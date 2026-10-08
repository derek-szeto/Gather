package com.example.gather

import android.text.InputType
import android.widget.LinearLayout
import android.content.res.ColorStateList

object SocialScreens {
    fun auth(page: LinearLayout, login: (String, String) -> Unit,
             register: (String, String, String) -> Unit, reset: (String) -> Unit, legacy: () -> Unit) {
        GatherUi.title(page, "gather.")
        GatherUi.hero(page, "PLAN THE MOMENT", "Good plans.\nBetter company.",
            "A place for the people and moments that matter.")
        GatherUi.section(page, "YOUR ACCOUNT", "Welcome to Gather")
        val account = GatherUi.card(page)
        val name = GatherUi.field(account, "Display name · for new accounts")
        val email = GatherUi.field(account, "Email").apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        }
        val password = GatherUi.field(account, "Password").apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        GatherUi.button(account, "Sign in", true) { login(email.text.toString().trim(), password.text.toString()) }
        GatherUi.button(account, "Create account") {
            if (name.text.toString().trim().length !in 1..40) name.error = "Use 1–40 characters."
            else if (password.text.length < 8) password.error = "Use at least 8 characters."
            else register(name.text.toString().trim(), email.text.toString().trim(), password.text.toString())
        }
        GatherUi.button(account, "Reset password") { reset(email.text.toString().trim()) }
        GatherUi.button(page, "Open old on-device plans") { legacy() }
        GatherUi.text(page, "On-device plans are stored separately from shared accounts.", 12f)
    }

    fun home(page: LinearLayout, uid: String, area: SearchArea?, plans: List<SocialPlan>,
             places: List<ApiPlace>, recommendationStatus: String, all: Boolean,
             location: () -> Unit, create: (ApiPlace?) -> Unit, open: (SocialPlan) -> Unit,
             toggle: () -> Unit, refresh: () -> Unit, localPlans: () -> Unit) {
        GatherUi.title(page, "gather.")
        GatherUi.button(page, area?.label ?: "Choose your location") { location() }
        val hero = GatherUi.hero(page, "YOUR NEXT MOMENT", "Make room for\nsomething good.",
            "Bring everyone together, one plan at a time.")
        GatherUi.button(hero, "Create a hangout", true) { create(null) }.apply {
            backgroundTintList = ColorStateList.valueOf(GatherUi.accent)
            setTextColor(GatherUi.ink)
        }
        GatherUi.section(page, "YOUR PLANS", if (all) "All hangouts" else "Coming up")
        val now = System.currentTimeMillis()
        val upcoming = plans.filter { !it.cancelled && it.startsAt >= now && it.response(uid) == "accepted" }
        val invitations = plans.filter { !it.cancelled && it.startsAt >= now && it.response(uid) == "pending" }
        fun card(plan: SocialPlan) {
            val card = GatherUi.card(page)
            GatherUi.eyebrow(card, "HANGOUT")
            GatherUi.heading(card, plan.title)
            GatherUi.text(card, GatherUi.date(plan.startsAt))
            GatherUi.text(card, plan.placeName.ifBlank { "Meeting point not chosen" })
            GatherUi.chip(card, if (plan.cancelled) "Cancelled" else
                plan.response(uid).replaceFirstChar { it.uppercase() })
            GatherUi.button(card, "Open hangout") { open(plan) }
        }
        val displayed = if (all) plans else upcoming
        if (displayed.isEmpty()) GatherUi.text(page, "No hangouts here yet.")
        displayed.forEach(::card)
        if (!all && invitations.isNotEmpty()) {
            GatherUi.section(page, "YOUR PEOPLE", "Invitations")
            invitations.forEach(::card)
        }
        GatherUi.button(page, if (all) "Show upcoming" else "See all hangouts") { toggle() }
        GatherUi.section(page, "NEAR YOU", "Places you might like")
        GatherUi.text(page, recommendationStatus)
        places.forEach { place ->
            val card = GatherUi.card(page)
            GatherUi.eyebrow(card, "DISCOVER")
            GatherUi.heading(card, place.name)
            GatherUi.text(card, place.address)
            GatherUi.button(card, "Plan a hangout here") { create(place) }
        }
        GatherUi.button(page, "Refresh nearby places") { refresh() }
        GatherUi.section(page, "ON THIS DEVICE", "Local plans")
        val local = GatherUi.card(page)
        GatherUi.text(local, "Keep a personal itinerary with places and stops saved on this device.")
        GatherUi.button(local, "Open on-device plans") { localPlans() }
        GatherUi.text(page, "Powered by Geoapify · © OpenStreetMap contributors", 12f)
    }

    fun friends(page: LinearLayout, uid: String, links: List<FriendLink>, profiles: Map<String, MemberProfile>,
                add: () -> Unit, accept: (FriendLink) -> Unit, remove: (FriendLink) -> Unit) {
        GatherUi.eyebrow(page, "YOUR CIRCLE")
        GatherUi.title(page, "Your people")
        GatherUi.text(page, "Make plans with the people you know.")
        GatherUi.button(page, "Add a friend by code", true) { add() }
        if (links.isEmpty()) GatherUi.text(page, "Share your friend code from Profile to connect.")
        links.sortedBy { it.state }.forEach { link ->
            val card = GatherUi.card(page)
            val profile = profiles[link.other(uid)]
            GatherUi.eyebrow(card, "FRIEND")
            GatherUi.heading(card, profile?.name ?: "Loading friend…")
            GatherUi.text(card, "Code · ${link.other(uid).take(6)}…", 12f)
            if (link.state == "accepted") {
                GatherUi.chip(card, profile?.statusLabel.orEmpty())
                GatherUi.button(card, "Remove friend") { remove(link) }
            } else if (link.sender == uid) {
                GatherUi.chip(card, "Request sent")
                GatherUi.button(card, "Cancel request") { remove(link) }
            } else {
                GatherUi.chip(card, "Wants to connect")
                GatherUi.button(card, "Accept", true) { accept(link) }
                GatherUi.button(card, "Decline") { remove(link) }
            }
        }
    }

    fun profile(page: LinearLayout, value: MemberProfile?, uid: String,
                copy: () -> Unit, status: () -> Unit, location: () -> Unit, logout: () -> Unit) {
        GatherUi.eyebrow(page, "YOUR SPACE")
        GatherUi.title(page, "Your profile")
        val identity = GatherUi.hero(page, "SIGNED IN AS",
            value?.name ?: "Loading profile…", "Let friends know when you're free.")
        GatherUi.chip(identity, value?.statusLabel.orEmpty())
        GatherUi.button(page, "Set status", true) { status() }
        val card = GatherUi.card(page)
        GatherUi.eyebrow(card, "SHARE TO CONNECT")
        GatherUi.heading(card, "Your friend code")
        GatherUi.text(card, uid).setTextIsSelectable(true)
        GatherUi.button(card, "Copy friend code") { copy() }
        GatherUi.section(page, "PREFERENCES", "Your location")
        GatherUi.button(page, "Update location") { location() }
        GatherUi.button(page, "Sign out") { logout() }
    }
}