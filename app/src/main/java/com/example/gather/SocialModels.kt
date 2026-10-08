package com.example.gather

data class MemberProfile(
    val id: String = "",
    val name: String = "Gather member",
    val status: String = "available",
    val customStatus: String = ""
) {
    @get:com.google.firebase.firestore.Exclude
    val statusLabel: String
        get() = if (status == "custom") {
            customStatus
        } else {
            status.replaceFirstChar { it.uppercase() }
        }
}

data class SearchArea(
    val label: String,
    val latitude: Double,
    val longitude: Double
)

data class SocialPlan(
    val id: String = "",
    val ownerId: String = "",
    val title: String = "",
    val startsAt: Long = 0,
    val notes: String = "",
    val memberIds: List<String> = emptyList(),
    val responses: Map<String, String> = emptyMap(),
    val cancelled: Boolean = false,
    val hasPlace: Boolean = false,
    val placeName: String = "",
    val address: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val lastInvited: String = ""
) {
    fun response(uid: String): String {
        return responses[uid] ?: "pending"
    }

    fun canContribute(uid: String): Boolean {
        return !cancelled &&
                (uid == ownerId || response(uid) == "accepted")
    }
}

data class FriendLink(
    val id: String = "",
    val members: List<String> = emptyList(),
    val sender: String = "",
    val state: String = "pending"
) {
    fun other(uid: String): String {
        return members.firstOrNull { it != uid }.orEmpty()
    }
}

data class MapPin(
    val id: String = "",
    val authorId: String = "",
    val title: String = "",
    val note: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0
)