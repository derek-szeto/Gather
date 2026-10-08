package com.example.gather

import android.content.Intent
import android.graphics.Color
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.firestore.ListenerRegistration
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import java.util.UUID

class SocialMapActivity : SignedInActivity() {
    private lateinit var mapView: MapView
    private var map: MapLibreMap? = null
    private lateinit var heading: TextView
    private lateinit var hint: TextView
    private lateinit var api: PlaceRepository
    private lateinit var location: LocationPicker
    private lateinit var search: PlaceSearchHelper
    private var planId = ""
    private var plan: SocialPlan? = null
    private var pins = emptyList<MapPin>()
    private var plans = emptyList<SocialPlan>()
    private val listeners = mutableListOf<ListenerRegistration>()
    private var generation = 0
    private var centered = false

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        if (!ready) return
        planId = intent.getStringExtra("plan_id").orEmpty()
        centered = state != null
        MapLibre.getInstance(this)
        api = PlaceRepository(this)
        location = LocationPicker(this, api) { area ->
            AreaStore(this, uid).save(area)
            map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(area.latitude, area.longitude), 13.0))
        }
        search = PlaceSearchHelper(this) {
            editPin(MapPin(authorId = uid, title = it.name, note = it.address,
                latitude = it.latitude, longitude = it.longitude))
        }
        val root = GatherUi.column(this)
        val top = GatherUi.column(this).apply {
            setPadding(GatherUi.dp(context, 18), GatherUi.dp(context, 12),
                GatherUi.dp(context, 18), GatherUi.dp(context, 14))
        }
        root.addView(top)
        GatherUi.eyebrow(top, "EXPLORE TOGETHER")
        heading = GatherUi.heading(top, if (planId.isBlank()) "Your map" else "Hangout map")
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        top.addView(actions)
        GatherUi.button(actions, "Home") {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)); finish()
        }.apply {
            setIconResource(R.drawable.ic_home)
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply {
                marginEnd = GatherUi.dp(this@SocialMapActivity, 8)
            }
        }
        GatherUi.button(actions, "Update location") { location.show() }
            .layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        mapView = MapView(this)
        root.addView(mapView, LinearLayout.LayoutParams(-1, 0, 1f))
        val bottom = GatherUi.column(this).apply {
            setPadding(GatherUi.dp(context, 18), GatherUi.dp(context, 10),
                GatherUi.dp(context, 18), GatherUi.dp(context, 12))
        }
        root.addView(bottom)
        hint = GatherUi.text(bottom, "Loading map…", 13f)
        val tools = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        bottom.addView(tools)
        GatherUi.button(tools, "Find a place") {
            val area = AreaStore(this, uid).read()?.label.orEmpty()
            search.startSearch(if (area == "Device location") "" else area)
        }.layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply {
            marginEnd = GatherUi.dp(this@SocialMapActivity, 8)
        }
        GatherUi.button(tools, "Saved places") { showList() }
            .layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        GatherUi.text(bottom, "© Geoapify · © OpenStreetMap contributors", 12f).apply {
            setOnClickListener { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.openstreetmap.org/copyright"))) }
        }
        install(root)
        mapView.onCreate(state)
        mapView.addOnDidFailLoadingMapListener { hint.text = "Map unavailable. Check your connection and Geoapify key." }
        mapView.getMapAsync { loadedMap ->
            if (isDestroyed || isFinishing) return@getMapAsync
            map = loadedMap
            val area = AreaStore(this, uid).read()
            if (state == null) loadedMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
                area?.let { LatLng(it.latitude, it.longitude) } ?: LatLng(39.8, -98.5),
                if (area == null) 3.0 else 12.0))
            // map tiles use the existing geoapify key; pins are loaded separately from firestore.
            val style = Uri.parse("https://maps.geoapify.com/v1/styles/osm-bright/style.json")
                .buildUpon().appendQueryParameter("apiKey", ApiConfig.GEOAPIFY_KEY).build().toString()
            loadedMap.setStyle(style) { loadedStyle ->
                if (isDestroyed || isFinishing) return@setStyle
                loadedStyle.addSource(GeoJsonSource("gather-pins", geoJson()))
                loadedStyle.addLayer(CircleLayer("gather-pins-layer", "gather-pins").withProperties(
                    PropertyFactory.circleRadius(9f),
                    PropertyFactory.circleColor(Expression.get("color")),
                    PropertyFactory.circleStrokeColor(Color.WHITE), PropertyFactory.circleStrokeWidth(3f)))
                hint.text = if (planId.isBlank()) "Green: private pins · Purple: your hangouts. Long-press to pin."
                else "Shared hangout pins. Long-press to add; tap a pin for notes."
                draw()
            }
            loadedMap.addOnMapLongClickListener { point ->
                editPin(MapPin(authorId = uid, latitude = point.latitude, longitude = point.longitude)); true
            }
            loadedMap.addOnMapClickListener { point ->
                val p = loadedMap.projection.toScreenLocation(point)
                val radius = GatherUi.dp(this, 20).toFloat()
                val feature = loadedMap.queryRenderedFeatures(RectF(p.x - radius, p.y - radius,
                    p.x + radius, p.y + radius), "gather-pins-layer").firstOrNull()
                val key = feature?.getStringProperty("key")
                if (key != null) { openMarker(key); true } else false
            }
        }
    }
    override fun onStart() {
        super.onStart(); if (!ready || !::mapView.isInitialized) return
        mapView.onStart()
        val token = ++generation
        listeners.add(repository.watchPins(planId, ::message) {
            if (token == generation) { pins = it; draw() }
        })
        if (planId.isBlank()) listeners.add(repository.watchPlans(::message) { values ->
            if (token == generation) {
                plans = values.filter { !it.cancelled && it.hasPlace && it.response(uid) == "accepted" }
                draw()
            }
        }) else listeners.add(repository.watchPlan(planId, { message(it); finish() }) {
            if (token == generation) {
                plan = it; plans = if (it.hasPlace) listOf(it) else emptyList()
                heading.text = it.title; draw()
            }
        })
    }
    private fun geoJson(): String {
        val features = JSONArray()
        fun add(key: String, lat: Double, lon: Double, color: String) {
            features.put(JSONObject().put("type", "Feature")
                .put("properties", JSONObject().put("key", key).put("color", color))
                .put("geometry", JSONObject().put("type", "Point")
                    .put("coordinates", JSONArray().put(lon).put(lat))))
        }
        pins.forEach { add("pin:${it.id}", it.latitude, it.longitude, "#176C5B") }
        plans.forEach { add("plan:${it.id}", it.latitude, it.longitude, "#7450A2") }
        return JSONObject().put("type", "FeatureCollection").put("features", features).toString()
    }
    private fun draw() {
        map?.style?.getSourceAs<GeoJsonSource>("gather-pins")?.setGeoJson(geoJson())
        if (!centered && planId.isNotBlank() && map?.style != null) {
            val center = plan?.takeIf { it.hasPlace }?.let { LatLng(it.latitude, it.longitude) }
                ?: pins.firstOrNull()?.let { LatLng(it.latitude, it.longitude) }
            if (center != null) { map?.moveCamera(CameraUpdateFactory.newLatLngZoom(center, 14.0)); centered = true }
        }
    }
    private fun openMarker(key: String) {
        if (key.startsWith("plan:")) startActivity(Intent(this, HangoutActivity::class.java)
            .putExtra("plan_id", key.removePrefix("plan:")))
        else pins.firstOrNull { it.id == key.removePrefix("pin:") }?.let(::editPin)
    }
    private fun showList() {
        val keys = pins.map { "pin:${it.id}" } + plans.map { "plan:${it.id}" }
        val names = pins.map { "Pin · ${it.title}" } + plans.map { "Hangout · ${it.title}" }
        if (keys.isEmpty()) { message("No saved pins yet. Long-press the map to add one."); return }
        MaterialAlertDialogBuilder(this).setTitle("On this map").setItems(names.toTypedArray()) { _, index ->
            openMarker(keys[index])
        }.setNegativeButton("Close", null).show()
    }
    private fun editPin(original: MapPin) {
        val allowed = planId.isBlank() || (plan?.canContribute(uid) == true &&
                (original.id.isBlank() || original.authorId == uid || plan?.ownerId == uid))
        if (!allowed) {
            MaterialAlertDialogBuilder(this).setTitle(original.title.ifBlank { "Hangout map" })
                .setMessage(original.note.ifBlank { "Accept the invitation to add your own pins. Only the author or host can edit an existing pin." })
                .setPositiveButton("Close", null).show(); return
        }
        val pin = if (original.id.isBlank()) original.copy(id = UUID.randomUUID().toString()) else original
        val form = GatherUi.form(this)
        val title = GatherUi.field(form, "Pin name", pin.title)
        val note = GatherUi.field(form, "Notes", pin.note, true)
        GatherUi.text(form, if (planId.isBlank()) "Visible only in your account."
        else "Shared with this hangout's members.", 12f)
        val dialog = MaterialAlertDialogBuilder(this).setTitle("Save a place")
            .setView(android.widget.ScrollView(this).apply { addView(form) })
            .setNegativeButton("Cancel", null).setPositiveButton("Save", null)
            .apply { if (original.id.isNotBlank()) setNeutralButton("Delete", null) }.create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = title.text.toString().trim(); val notes = note.text.toString().trim()
                if (name.length !in 1..100) title.error = "Use 1–100 characters."
                else if (notes.length > 2000) note.error = "Use up to 2000 characters."
                else perform({ repository.savePin(planId, pin.copy(title = name, note = notes)) }) {
                    dialog.dismiss(); message("Pin saved.")
                }
            }
            if (original.id.isNotBlank()) dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                confirm("Delete this pin?") { perform({ repository.deletePin(planId, pin.id) }) { dialog.dismiss() } }
            }
        }
        dialog.show()
    }
    override fun onResume() { super.onResume(); if (::mapView.isInitialized) mapView.onResume() }
    override fun onPause() { if (::mapView.isInitialized) mapView.onPause(); super.onPause() }
    override fun onStop() {
        generation++; listeners.forEach { it.remove() }; listeners.clear()
        if (::mapView.isInitialized) mapView.onStop(); super.onStop()
    }
    override fun onSaveInstanceState(out: Bundle) {
        super.onSaveInstanceState(out); if (::mapView.isInitialized) mapView.onSaveInstanceState(out)
    }
    override fun onLowMemory() { super.onLowMemory(); if (::mapView.isInitialized) mapView.onLowMemory() }
    override fun onDestroy() {
        if (::location.isInitialized) location.close()
        if (::search.isInitialized) search.close()
        if (::api.isInitialized) api.close()
        if (::mapView.isInitialized) mapView.onDestroy()
        super.onDestroy()
    }
}