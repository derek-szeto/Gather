package com.example.gather

import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings

object CloudStore {
    val auth: FirebaseAuth by lazy {
        FirebaseAuth.getInstance()
    }

    val db: FirebaseFirestore by lazy {
        FirebaseFirestore.getInstance().apply {
            // cloud data persists on the server; account data uses memory-only local caching.
            firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(
                    MemoryCacheSettings.newBuilder().build()
                )
                .build()
        }
    }
}

class AccountRepository {
    val uid: String?
        get() = CloudStore.auth.currentUser?.uid

    fun signIn(
        email: String,
        password: String
    ): Task<Void> {
        return CloudStore.auth
            .signInWithEmailAndPassword(email, password)
            .continueWithTask { task ->
                val user = task.result.user
                    ?: error("Sign-in failed.")

                ensureProfile(user.uid, "Gather member")
            }
    }

    fun register(
        name: String,
        email: String,
        password: String
    ): Task<Void> {
        return CloudStore.auth
            .createUserWithEmailAndPassword(email, password)
            .continueWithTask { task ->
                val user = task.result.user
                    ?: error("Account creation failed.")

                ensureProfile(user.uid, name)
            }
    }

    fun ensureProfile(
        uid: String,
        name: String
    ): Task<Void> {
        val ref = CloudStore.db
            .collection("users")
            .document(uid)

        // a transaction repairs interrupted registration without replacing an existing profile.
        return CloudStore.db.runTransaction<Void> { transaction ->
            if (!transaction.get(ref).exists()) {
                transaction.set(ref, MemberProfile(uid, name))
            }

            null
        }
    }

    fun resetPassword(email: String): Task<Void> {
        return CloudStore.auth.sendPasswordResetEmail(email)
    }

    fun signOut() {
        CloudStore.auth.signOut()
    }
}

class SocialRepository(private val uid: String) {
    private val db
        get() = CloudStore.db

    private fun plan(id: String) =
        db.collection("hangouts").document(id)

    private fun linkId(other: String): String {
        return listOf(uid, other)
            .sorted()
            .joinToString("_")
    }

    fun newPlanId(): String {
        return db.collection("hangouts").document().id
    }

    fun loadPlan(id: String): Task<SocialPlan> {
        return plan(id).get().continueWith {
            it.result.toObject(SocialPlan::class.java)
                ?: error("This hangout no longer exists.")
        }
    }

    fun watchProfile(
        id: String,
        error: (String) -> Unit,
        next: (MemberProfile) -> Unit
    ) = db.collection("users")
        .document(id)
        .addSnapshotListener { snapshot, failure ->
            if (failure != null) {
                error(failure.message ?: "Profile unavailable.")
            } else {
                snapshot
                    ?.toObject(MemberProfile::class.java)
                    ?.let(next)
            }
        }

    fun watchPlans(
        error: (String) -> Unit,
        next: (List<SocialPlan>) -> Unit
    ) = db.collection("hangouts")
        .whereArrayContains("memberIds", uid)
        .addSnapshotListener { snapshot, failure ->
            if (failure != null) {
                error(failure.message ?: "Plans unavailable.")
            } else {
                next(
                    snapshot
                        ?.toObjects(SocialPlan::class.java)
                        .orEmpty()
                        .sortedBy { it.startsAt }
                )
            }
        }

    fun watchPlan(
        id: String,
        error: (String) -> Unit,
        next: (SocialPlan) -> Unit
    ) = plan(id).addSnapshotListener { snapshot, failure ->
        if (failure != null) {
            error(failure.message ?: "Hangout unavailable.")
        } else {
            snapshot
                ?.toObject(SocialPlan::class.java)
                ?.let(next)
        }
    }

    fun watchFriends(
        error: (String) -> Unit,
        next: (List<FriendLink>) -> Unit
    ) = db.collection("friendships")
        .whereArrayContains("members", uid)
        .addSnapshotListener { snapshot, failure ->
            if (failure != null) {
                error(failure.message ?: "Friends unavailable.")
            } else {
                next(
                    snapshot
                        ?.toObjects(FriendLink::class.java)
                        .orEmpty()
                )
            }
        }

    fun setStatus(
        status: String,
        custom: String
    ): Task<Void> {
        return db.collection("users")
            .document(uid)
            .update(
                mapOf(
                    "status" to status,
                    "customStatus" to custom
                )
            )
    }

    fun requestFriend(other: String): Task<Void> {
        val ref = db.collection("friendships")
            .document(linkId(other))

        return db.runTransaction<Void> { transaction ->
            require(other.isNotBlank() && other != uid) {
                "Enter another person's friend code."
            }

            val target = db.collection("users").document(other)

            check(transaction.get(target).exists()) {
                "User not found."
            }

            check(!transaction.get(ref).exists()) {
                "You are already friends or have a pending request."
            }

            transaction.set(
                ref,
                FriendLink(
                    id = ref.id,
                    members = listOf(uid, other).sorted(),
                    sender = uid
                )
            )

            null
        }
    }

    fun acceptFriend(link: FriendLink): Task<Void> {
        return db.collection("friendships")
            .document(link.id)
            .update("state", "accepted")
    }

    fun removeFriend(link: FriendLink): Task<Void> {
        return db.collection("friendships")
            .document(link.id)
            .delete()
    }

    fun savePlan(
        value: SocialPlan,
        creating: Boolean
    ): Task<Void> {
        val ref = plan(value.id)

        // retaining the document id prevents duplicate plans when a create is retried.
        return if (creating) {
            db.runTransaction<Void> { transaction ->
                if (!transaction.get(ref).exists()) {
                    transaction.set(ref, value)
                }

                null
            }
        } else {
            ref.update(
                mapOf(
                    "title" to value.title,
                    "startsAt" to value.startsAt,
                    "notes" to value.notes,
                    "hasPlace" to value.hasPlace,
                    "placeName" to value.placeName,
                    "address" to value.address,
                    "latitude" to value.latitude,
                    "longitude" to value.longitude
                )
            )
        }
    }

    fun invite(
        id: String,
        other: String
    ): Task<Void> {
        return plan(id).update(
            mapOf(
                "memberIds" to FieldValue.arrayUnion(other),
                "lastInvited" to other
            )
        )
    }

    fun respond(
        id: String,
        response: String
    ): Task<Void> {
        return plan(id).update(
            FieldPath.of("responses", uid),
            response
        )
    }

    fun cancel(id: String): Task<Void> {
        return plan(id).update("cancelled", true)
    }

    private fun pins(hangoutId: String) =
        if (hangoutId.isBlank()) {
            db.collection("users")
                .document(uid)
                .collection("pins")
        } else {
            plan(hangoutId).collection("pins")
        }

    fun watchPins(
        hangoutId: String,
        error: (String) -> Unit,
        next: (List<MapPin>) -> Unit
    ) = pins(hangoutId)
        .addSnapshotListener { snapshot, failure ->
            if (failure != null) {
                error(failure.message ?: "Pins unavailable.")
            } else {
                next(
                    snapshot
                        ?.toObjects(MapPin::class.java)
                        .orEmpty()
                )
            }
        }

    fun savePin(
        hangoutId: String,
        pin: MapPin
    ): Task<Void> {
        val ref = if (pin.id.isBlank()) {
            pins(hangoutId).document()
        } else {
            pins(hangoutId).document(pin.id)
        }

        return ref.set(pin.copy(id = ref.id))
    }

    fun deletePin(
        hangoutId: String,
        id: String
    ): Task<Void> {
        return pins(hangoutId).document(id).delete()
    }
}