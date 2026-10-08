package com.example.gather

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.os.CancellationSignal

class LocationPicker(private val activity: GatherActivity, private val api: PlaceRepository,
                     private val selected: (SearchArea) -> Unit) {
    private val handler = Handler(Looper.getMainLooper())
    private var cancellation: CancellationSignal? = null
    private var dialog: AlertDialog? = null
    private var generation = 0
    private val tag = Any()
    private var active = false
    private val permissions = activity.registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { if (active) locate() }

    fun show() {
        closeWork()
        active = true
        val form = GatherUi.form(activity)
        val input = GatherUi.field(form, "U.S. address, city, or ZIP")
        val status = GatherUi.text(form, "Type 3 characters, then select an address suggestion.")
        val results = GatherUi.column(activity).also { form.addView(it) }
        GatherUi.button(form, "Use my device location") {
            if (allowed(Manifest.permission.ACCESS_COARSE_LOCATION) ||
                allowed(Manifest.permission.ACCESS_FINE_LOCATION)) locate()
            else permissions.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION))
        }
        GatherUi.text(form, "Address text and requested device coordinates are sent to Geoapify.", 12f)
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                generation++
                val token = generation
                handler.removeCallbacksAndMessages(null)
                cancellation?.cancel(); cancellation = null
                api.cancel(tag); results.removeAllViews()
                val query = s.toString().trim()
                if (query.length < 3) { status.text = "Type at least 3 characters."; return }
                handler.postDelayed({
                    if (!active || token != generation) return@postDelayed
                    status.text = "Finding suggestions…"
                    api.addresses(query, tag, { if (active && token == generation) status.text = it }) { areas ->
                        if (active && token == generation) {
                            status.text = if (areas.isEmpty()) "No match. Add a city or ZIP." else "Choose your search area"
                            areas.forEach { area -> GatherUi.button(results, area.label) { choose(area) } }
                        }
                    }
                }, 500)
            }
        })
        dialog = MaterialAlertDialogBuilder(activity).setTitle("Update location")
            .setView(android.widget.ScrollView(activity).apply { addView(form) })
            .setNegativeButton("Cancel", null).create().apply {
                setOnDismissListener { closeWork() }; show()
            }
    }
    private fun allowed(permission: String) = ContextCompat.checkSelfPermission(activity, permission) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private fun locate() {
        if (!allowed(Manifest.permission.ACCESS_COARSE_LOCATION) &&
            !allowed(Manifest.permission.ACCESS_FINE_LOCATION)) {
            activity.message("Location permission was declined. Enter an address instead."); return
        }
        val manager = activity.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (!LocationManagerCompat.isLocationEnabled(manager)) {
            activity.message("Turn on device location or enter an address."); return
        }
        val provider = when {
            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            allowed(Manifest.permission.ACCESS_FINE_LOCATION) &&
                    manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            else -> { activity.message("No location provider. Enter an address instead."); return }
        }
        generation++; val token = generation
        handler.removeCallbacksAndMessages(null); api.cancel(tag); cancellation?.cancel()
        val signal = CancellationSignal(); cancellation = signal
        activity.message("Getting your location…")
        handler.postDelayed({
            if (active && generation == token) {
                generation++; signal.cancel(); activity.message("Location timed out. Try an address.")
            }
        }, 20000)
        try {
            // a one-time location request stops when the picker closes or times out.
            LocationManagerCompat.getCurrentLocation(manager, provider, signal,
                ContextCompat.getMainExecutor(activity)) { location ->
                if (active && token == generation) {
                    handler.removeCallbacksAndMessages(null)
                    if (location == null) activity.message("Location unavailable. Try an address.")
                    else choose(SearchArea("Device location", location.latitude, location.longitude))
                }
            }
        } catch (_: Exception) { activity.message("Location unavailable. Try an address.") }
    }
    private fun choose(area: SearchArea) { if (active) { dialog?.dismiss(); selected(area) } }
    private fun closeWork() {
        active = false; generation++; handler.removeCallbacksAndMessages(null)
        cancellation?.cancel(); cancellation = null; api.cancel(tag)
    }
    fun close() { dialog?.dismiss(); closeWork(); permissions.unregister() }
}