package com.example.gather

import android.content.Intent
import android.os.Bundle
import android.widget.FrameLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.firestore.ListenerRegistration

class HangoutActivity : SignedInActivity() {
    private lateinit var frame: FrameLayout
    private var id = ""
    private var plan: SocialPlan? = null
    private var friends = emptyList<FriendLink>()
    private val profiles = mutableMapOf<String, MemberProfile>()
    private val listeners = mutableListOf<ListenerRegistration>()
    private val people = mutableMapOf<String, ListenerRegistration>()
    private var generation = 0
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        if (!ready) return
        id = intent.getStringExtra("plan_id").orEmpty()
        if (id.isBlank()) { finish(); return }
        frame = FrameLayout(this); install(frame)
        GatherUi.text(GatherUi.page(frame), "Loading hangout…")
    }
    override fun onStart() {
        super.onStart(); if (!ready || id.isBlank()) return
        val token = ++generation
        fun watchPerson(other: String) {
            if (!people.containsKey(other)) people[other] = repository.watchProfile(other, ::message) {
                if (token == generation) { profiles[other] = it; render() }
            }
        }
        listeners.add(repository.watchPlan(id, { message(it); finish() }) {
            if (token == generation) {
                plan = it; it.memberIds.forEach(::watchPerson); render()
            }
        })
        listeners.add(repository.watchFriends(::message) {
            if (token == generation) {
                friends = it.filter { link -> link.state == "accepted" }
                friends.forEach { link -> watchPerson(link.other(uid)) }; render()
            }
        })
    }
    private fun render() {
        val value = plan ?: return
        val page = GatherUi.page(frame)
        GatherUi.eyebrow(page, "THE HANGOUT")
        GatherUi.title(page, value.title)
        GatherUi.text(page, GatherUi.date(value.startsAt))
        GatherUi.chip(page, if (value.cancelled) "Cancelled" else
            "Your response · ${value.response(uid).replaceFirstChar { it.uppercase() }}")
        if (value.hasPlace) {
            val card = GatherUi.card(page)
            GatherUi.eyebrow(card, "MEETING POINT")
            GatherUi.heading(card, value.placeName); GatherUi.text(card, value.address)
        }
        if (value.notes.isNotBlank()) {
            val notes = GatherUi.card(page)
            GatherUi.eyebrow(notes, "PLAN NOTES")
            GatherUi.text(notes, value.notes)
        }
        GatherUi.button(page, "Open hangout map", true) {
            startActivity(Intent(this, SocialMapActivity::class.java).putExtra("plan_id", id))
        }
        if (!value.cancelled && value.ownerId != uid) {
            GatherUi.button(page, "Accept invitation") { perform({ repository.respond(id, "accepted") }) }
            GatherUi.button(page, "Decline invitation") { perform({ repository.respond(id, "declined") }) }
        }
        GatherUi.section(page, "THE GROUP", "Who's coming")
        value.memberIds.forEach { member ->
            val person = GatherUi.card(page)
            GatherUi.heading(person, profiles[member]?.name ?: "Loading…")
            GatherUi.chip(person, value.response(member).replaceFirstChar { it.uppercase() })
        }
        if (value.ownerId == uid && !value.cancelled) {
            GatherUi.section(page, "HOST TOOLS", "Manage this hangout")
            GatherUi.button(page, "Invite a friend") {
                val options = friends.map { it.other(uid) }.distinct().filter { it !in value.memberIds }
                if (options.isEmpty()) message("Add a friend first, or everyone is already invited.")
                else MaterialAlertDialogBuilder(this).setTitle("Invite a friend")
                    .setItems(options.map { "${profiles[it]?.name ?: "Friend"} · ${it.take(6)}" }.toTypedArray()) { _, index ->
                        perform({ repository.invite(id, options[index]) }) { message("Invitation sent.") }
                    }.setNegativeButton("Cancel", null).show()
            }
            GatherUi.button(page, "Edit hangout") {
                startActivity(Intent(this, PlanEditorActivity::class.java).putExtra("plan_id", id))
            }
            GatherUi.button(page, "Cancel hangout") {
                confirm("Cancel this hangout for everyone?") { perform({ repository.cancel(id) }) }
            }
        }
        GatherUi.button(page, "Home") {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
            finish()
        }.setIconResource(R.drawable.ic_home)
    }
    override fun onStop() {
        generation++; listeners.forEach { it.remove() }; listeners.clear()
        people.values.forEach { it.remove() }; people.clear(); super.onStop()
    }
}