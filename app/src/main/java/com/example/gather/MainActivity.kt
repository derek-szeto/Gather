package com.example.gather

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.ListenerRegistration

class MainActivity : GatherActivity() {
    private val accounts = AccountRepository()
    private lateinit var frame: FrameLayout
    private lateinit var api: PlaceRepository
    private lateinit var location: LocationPicker
    private var repository: SocialRepository? = null
    private var currentUid: String? = null
    private var tab = 1
    private var all = false
    private var profile: MemberProfile? = null
    private var plans = emptyList<SocialPlan>()
    private var friends = emptyList<FriendLink>()
    private val profiles = mutableMapOf<String, MemberProfile>()
    private var places = emptyList<ApiPlace>()
    private var recommendationStatus = "Choose a location for recommendations."
    private var listeners = mutableListOf<ListenerRegistration>()
    private var friendListeners = mutableListOf<ListenerRegistration>()
    private var generation = 0
    private val recommendationTag = Any()
    private var recommendationVersion = 0
    private var firebaseReady = false
    private var lastArea: SearchArea? = null

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        tab = state?.getInt("tab") ?: 1
        api = PlaceRepository(this)
        location = LocationPicker(this, api) { area ->
            currentUid?.let { AreaStore(this, it).save(area) }
            recommend(); render()
        }
        firebaseReady = FirebaseApp.initializeApp(this) != null
        shell()
    }
    private fun shell() {
        val root = GatherUi.column(this)
        frame = FrameLayout(this)
        root.addView(frame, LinearLayout.LayoutParams(-1, 0, 1f))
        if (firebaseReady && accounts.uid != null) {
            val nav = BottomNavigationView(this)
            listOf(Triple(1, "Home", R.drawable.ic_home), Triple(2, "Map", R.drawable.ic_map),
                Triple(3, "Friends", R.drawable.ic_friends), Triple(4, "Profile", R.drawable.ic_profile))
                .forEach { (id, title, icon) -> nav.menu.add(0, id, id, title).setIcon(icon) }
            val navigationColors = ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(GatherUi.green, GatherUi.muted)
            )
            nav.itemIconTintList = navigationColors
            nav.itemTextColor = navigationColors
            nav.itemActiveIndicatorColor = ColorStateList.valueOf(GatherUi.soft)
            nav.setBackgroundColor(android.graphics.Color.WHITE)
            nav.elevation = GatherUi.dp(this, 8).toFloat()
            nav.selectedItemId = tab
            nav.setOnItemSelectedListener {
                if (it.itemId == 2) { startActivity(Intent(this, SocialMapActivity::class.java)); false }
                else { tab = it.itemId; render(); true }
            }
            root.addView(nav)
        }
        install(root)
        render()
    }
    override fun onStart() {
        super.onStart()
        if (firebaseReady && accounts.uid != null) {
            enter()
            currentUid?.let { if (AreaStore(this, it).read() != lastArea) recommend() }
        }
    }
    private fun enter() {
        val id = accounts.uid ?: return
        if (currentUid != id) {
            currentUid = id; profile = null; plans = emptyList(); friends = emptyList()
            profiles.clear(); places = emptyList(); repository = SocialRepository(id); shell(); recommend()
        }
        stopListening()
        val token = generation
        val repo = repository ?: return
        fun valid() = token == generation && currentUid == id
        listeners.add(repo.watchProfile(id, ::message) { if (valid()) { profile = it; render() } })
        listeners.add(repo.watchPlans(::message) { if (valid()) { plans = it; render() } })
        listeners.add(repo.watchFriends(::message) { values ->
            if (valid()) {
                friends = values
                friendListeners.forEach { it.remove() }; friendListeners.clear(); profiles.clear()
                values.map { it.other(id) }.distinct().forEach { other ->
                    friendListeners.add(repo.watchProfile(other, ::message) { person ->
                        if (valid()) { profiles[other] = person; if (tab == 3) render() }
                    })
                }
                render()
            }
        })
    }
    private fun render() {
        if (!::frame.isInitialized) return
        val page = GatherUi.page(frame)
        if (!firebaseReady) {
            GatherUi.title(page, "Connect Gather to Firebase")
            GatherUi.text(page, "Add app/google-services.json, enable Email/Password sign-in, create Firestore, and publish the supplied rules. Then rebuild.")
            GatherUi.button(page, "Open old on-device plans") { legacy() }; return
        }
        val id = currentUid
        if (id == null || accounts.uid == null) {
            SocialScreens.auth(page,
                { email, password -> perform({ accounts.signIn(email, password) }) { enter() } },
                { name, email, password -> perform({ accounts.register(name, email, password) }) { enter() } },
                { email -> perform({ accounts.resetPassword(email) }) { message("If the account exists, check your email.") } },
                ::legacy)
            return
        }
        val repo = repository ?: return
        when (tab) {
            3 -> SocialScreens.friends(page, id, friends, profiles,
                { input("Add friend", "Paste their friend code") { other ->
                    if (!other.matches(Regex("[A-Za-z0-9_-]{1,128}"))) message("Invalid friend code.")
                    else perform({ repo.requestFriend(other) }) { message("Friend request sent.") }
                } },
                { link -> perform({ repo.acceptFriend(link) }) },
                { link -> confirm("Remove this friendship or request?") { perform({ repo.removeFriend(link) }) } })
            4 -> SocialScreens.profile(page, profile, id, {
                (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager)
                    .setPrimaryClip(ClipData.newPlainText("Gather friend code", id))
                message("Friend code copied.")
            }, ::chooseStatus, { location.show() }, {
                stopListening(); recommendationVersion++; api.cancel(recommendationTag)
                accounts.signOut(); currentUid = null; repository = null
                profile = null; plans = emptyList(); friends = emptyList(); profiles.clear(); places = emptyList()
                tab = 1; shell()
            })
            else -> SocialScreens.home(page, id, AreaStore(this, id).read(), plans, places,
                recommendationStatus, all, { location.show() }, { place -> edit(place) },
                { plan -> startActivity(Intent(this, HangoutActivity::class.java).putExtra("plan_id", plan.id)) },
                { all = !all; render() }, ::recommend, ::legacy)
        }
    }
    private fun chooseStatus() {
        MaterialAlertDialogBuilder(this).setTitle("Your status")
            .setItems(arrayOf("Available", "Busy", "Custom")) { _, index ->
                val status = listOf("available", "busy", "custom")[index]
                if (status == "custom") input("Custom status", "Up to 80 characters") { value ->
                    if (value.length > 80) message("Use up to 80 characters.")
                    else repository?.let { repo -> perform({ repo.setStatus(status, value) }) }
                } else repository?.let { repo -> perform({ repo.setStatus(status, "") }) }
            }.show()
    }
    private fun recommend() {
        val id = currentUid ?: return
        recommendationVersion++; val version = recommendationVersion
        api.cancel(recommendationTag); places = emptyList()
        val area = AreaStore(this, id).read()
        lastArea = area
        if (area == null) { recommendationStatus = "Choose a location for recommendations."; render(); return }
        recommendationStatus = "Finding nearby cafés, restaurants, and parks…"; render()
        api.recommendations(area, recommendationTag, {
            if (currentUid == id && version == recommendationVersion) { recommendationStatus = it; render() }
        }) {
            if (currentUid == id && version == recommendationVersion) {
                places = it
                recommendationStatus = if (it.isEmpty()) "No places found. Try another area."
                else "Nearby cafés, restaurants, and parks · within 8 km"
                render()
            }
        }
    }
    private fun edit(place: ApiPlace?) {
        val intent = Intent(this, PlanEditorActivity::class.java)
        place?.let { intent.putExtra("place_name", it.name).putExtra("address", it.address)
            .putExtra("lat", it.latitude).putExtra("lon", it.longitude) }
        startActivity(intent)
    }
    private fun legacy() = startActivity(Intent(this, LegacyPlansActivity::class.java))
    private fun stopListening() {
        generation++; listeners.forEach { it.remove() }; listeners.clear()
        friendListeners.forEach { it.remove() }; friendListeners.clear()
    }
    override fun onStop() { stopListening(); super.onStop() }
    override fun onSaveInstanceState(outState: Bundle) { outState.putInt("tab", tab); super.onSaveInstanceState(outState) }
    override fun onDestroy() { location.close(); api.close(); super.onDestroy() }
}