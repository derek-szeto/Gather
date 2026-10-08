package com.example.gather

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.text.format.DateFormat
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import java.util.Calendar

class PlanEditorActivity : SignedInActivity() {
    private lateinit var titleInput: EditText
    private lateinit var notesInput: EditText
    private lateinit var dateLabel: TextView
    private lateinit var placeLabel: TextView
    private lateinit var search: PlaceSearchHelper
    private var id = ""
    private var creating = true
    private var loaded = false
    private var startsAt = System.currentTimeMillis() + 3600000
    private var place: ApiPlace? = null

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        if (!ready) return
        creating = intent.getStringExtra("plan_id").isNullOrBlank()
        id = state?.getString("id") ?: intent.getStringExtra("plan_id") ?: repository.newPlanId()
        val frame = FrameLayout(this); install(frame)
        val page = GatherUi.page(frame)
        GatherUi.eyebrow(page, "CREATE TOGETHER")
        GatherUi.title(page, if (creating) "Make a plan" else "Edit hangout")
        GatherUi.text(page, "Set a time, choose a place, and bring your people in.")
        val details = GatherUi.card(page)
        GatherUi.eyebrow(details, "THE DETAILS")
        titleInput = GatherUi.field(details, "Hangout name")
        dateLabel = GatherUi.text(details, "")
        GatherUi.button(details, "Choose date and time") { chooseTime() }
        placeLabel = GatherUi.text(details, "No meeting point yet.")
        search = PlaceSearchHelper(this) { place = it; labels() }
        GatherUi.button(details, "Find a meeting place") {
            val area = AreaStore(this, uid).read()?.label.orEmpty()
            search.startSearch(if (area == "Device location") "" else area)
        }
        notesInput = GatherUi.field(details, "Hangout notes", multiline = true)
        GatherUi.button(page, "Save hangout", true) { save() }
        GatherUi.button(page, "Cancel") { finish() }
        if (state?.getBoolean("loaded") == true) {
            loaded = true
            titleInput.setText(state.getString("title")); notesInput.setText(state.getString("notes"))
            startsAt = state.getLong("time")
            if (state.getBoolean("has_place")) place = ApiPlace("", state.getString("name").orEmpty(),
                state.getString("address").orEmpty(), state.getDouble("lat"), state.getDouble("lon"))
        } else if (creating) {
            loaded = true
            intent.getStringExtra("place_name")?.let { name ->
                place = ApiPlace("", name, intent.getStringExtra("address").orEmpty(),
                    intent.getDoubleExtra("lat", 0.0), intent.getDoubleExtra("lon", 0.0))
            }
        } else {
            repository.loadPlan(id)
                .addOnSuccessListener { value ->
                    if (isDestroyed || isFinishing) return@addOnSuccessListener
                    if (value.ownerId != uid) { message("Only the host can edit."); finish() }
                    else {
                        titleInput.setText(value.title); notesInput.setText(value.notes); startsAt = value.startsAt
                        if (value.hasPlace) place = ApiPlace("", value.placeName, value.address, value.latitude, value.longitude)
                        loaded = true; labels()
                    }
                }.addOnFailureListener { if (!isDestroyed && !isFinishing) message(it.message ?: "Could not load this plan.") }
        }
        labels()
    }
    private fun labels() {
        dateLabel.text = GatherUi.date(startsAt)
        placeLabel.text = place?.let { "${it.name}\n${it.address}" } ?: "No meeting point yet. You can add map pins after saving."
    }
    private fun chooseTime() {
        val time = Calendar.getInstance().apply { timeInMillis = startsAt }
        DatePickerDialog(this, { _, year, month, day ->
            time.set(year, month, day)
            TimePickerDialog(this, { _, hour, minute ->
                time.set(Calendar.HOUR_OF_DAY, hour); time.set(Calendar.MINUTE, minute)
                time.set(Calendar.SECOND, 0); time.set(Calendar.MILLISECOND, 0)
                startsAt = time.timeInMillis; labels()
            }, time.get(Calendar.HOUR_OF_DAY), time.get(Calendar.MINUTE), DateFormat.is24HourFormat(this)).show()
        }, time.get(Calendar.YEAR), time.get(Calendar.MONTH), time.get(Calendar.DAY_OF_MONTH)).show()
    }
    private fun save() {
        if (!loaded) { message("Wait for the plan to load."); return }
        val title = titleInput.text.toString().trim()
        if (title.length !in 1..100) { titleInput.error = "Use 1–100 characters."; return }
        if (startsAt <= System.currentTimeMillis()) { message("Choose a future date and time."); return }
        val notes = notesInput.text.toString().trim()
        if (notes.length > 2000) { notesInput.error = "Use up to 2000 characters."; return }
        val value = SocialPlan(id, uid, title, startsAt, notes, listOf(uid), mapOf(uid to "accepted"),
            hasPlace = place != null, placeName = place?.name.orEmpty(), address = place?.address.orEmpty(),
            latitude = place?.latitude ?: 0.0, longitude = place?.longitude ?: 0.0)
        perform({ repository.savePlan(value, creating) }) {
            if (creating) startActivity(Intent(this, HangoutActivity::class.java).putExtra("plan_id", id))
            finish()
        }
    }
    override fun onSaveInstanceState(out: Bundle) {
        if (ready) {
            out.putString("id", id); out.putBoolean("loaded", loaded)
            out.putString("title", titleInput.text.toString()); out.putString("notes", notesInput.text.toString())
            out.putLong("time", startsAt); out.putBoolean("has_place", place != null)
            place?.let { out.putString("name", it.name); out.putString("address", it.address)
                out.putDouble("lat", it.latitude); out.putDouble("lon", it.longitude) }
        }
        super.onSaveInstanceState(out)
    }
    override fun onDestroy() { if (::search.isInitialized) search.close(); super.onDestroy() }
}