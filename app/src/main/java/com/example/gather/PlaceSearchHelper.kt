package com.example.gather

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.os.CancellationSignal
import com.android.volley.DefaultRetryPolicy
import com.android.volley.Request
import com.android.volley.VolleyError
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import org.json.JSONException
import org.json.JSONObject
import java.util.Locale
import kotlin.math.roundToInt

class PlaceSearchHelper(
    private val activity: AppCompatActivity,
    private val onPlaceSelected: (ApiPlace) -> Unit
) {

    private data class SearchOptions(
        val category: String,
        val businessName: String,
        val radiusMeters: Int
    )

    private data class SearchLocation(
        val name: String,
        val latitude: Double,
        val longitude: Double
    )

    private data class NearbyPlace(
        val place: ApiPlace,
        val distanceMeters: Float,
        val categoryLabel: String
    )

    private var lastQuery = ""
    private var lastLocationText = ""
    private var lastRadiusIndex = 2

    private val radiusMiles = listOf(1, 3, 5, 10, 25)

    private val requestQueue =
        Volley.newRequestQueue(activity.applicationContext)

    private val requestTag = Any()
    private val handler = Handler(Looper.getMainLooper())

    private var currentDialog: AlertDialog? = null
    private var locationCancellation: CancellationSignal? = null
    private var locationTimeout: Runnable? = null
    private var pendingPermissionAction: (() -> Unit)? = null

    private var closed = false
    private var searching = false
    private var session = 0

    private val permissionLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        val action = pendingPermissionAction
        pendingPermissionAction = null

        if (!closed && searching && action != null) {
            if (
                hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ||
                hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
            ) {
                action()
            } else {
                finishWithMessage(
                    "Location permission was not granted. " +
                            "You can still search using an address."
                )
            }
        }
    }

    fun startSearch(city: String) {
        if (closed || searching) return

        if (
            ApiConfig.GEOAPIFY_KEY.isBlank() ||
            ApiConfig.GEOAPIFY_KEY == "YOUR_GEOAPIFY_API_KEY"
        ) {
            showMessage("Add your Geoapify key to ApiConfig.kt.")
            return
        }

        searching = true
        session++

        lastLocationText = city
        showSearchForm(city)
    }

    private fun showSearchForm(city: String) {
        val content = activity.layoutInflater.inflate(
            R.layout.dialog_place_search,
            null
        )

        val queryInput = content.findViewById<AutoCompleteTextView>(
            R.id.searchCategoryInput
        )

        val locationInput = content.findViewById<EditText>(
            R.id.searchLocationInput
        )

        val radiusSpinner = content.findViewById<Spinner>(
            R.id.searchRadiusSpinner
        )

        val suggestionsContainer = content.findViewById<LinearLayout>(
            R.id.addressSuggestions
        )

        queryInput.setAdapter(
            ArrayAdapter(
                activity,
                android.R.layout.simple_dropdown_item_1line,
                PlaceSearchTerms.suggestions
            )
        )

        queryInput.setOnClickListener {
            queryInput.showDropDown()
        }

        queryInput.setText(lastQuery, false)

        val interpretation = content.findViewById<TextView>(
            R.id.searchInterpretation
        )

        fun updateInterpretation() {
            val query = queryInput.text.toString().trim()
            val match = PlaceSearchTerms.resolve(query)

            interpretation.text = when {
                query.isBlank() ->
                    "Type to see suggestions, or enter a business name."

                match.businessName.isBlank() ->
                    "Searching category: ${match.label}"

                else ->
                    "Searching business name: $query. " +
                            "For a type of place, choose a suggestion."
            }
        }

        queryInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(
                s: CharSequence?,
                start: Int,
                count: Int,
                after: Int
            ) {
            }

            override fun onTextChanged(
                s: CharSequence?,
                start: Int,
                before: Int,
                count: Int
            ) {
                updateInterpretation()
            }

            override fun afterTextChanged(s: Editable?) {
            }
        })

        updateInterpretation()
        locationInput.setText(city)

        radiusSpinner.adapter = ArrayAdapter(
            activity,
            android.R.layout.simple_spinner_item,
            radiusMiles.map {
                if (it == 1) "1 mile" else "$it miles"
            }
        ).apply {
            setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
            )
        }

        radiusSpinner.setSelection(lastRadiusIndex)

        var selectedLocation: SearchLocation? = null
        var selectingSuggestion = false
        var pendingSuggestion: Runnable? = null
        var suggestionVersion = 0

        val suggestionTag = Any()
        val token = session

        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle("Find a place")
            .setView(content)
            .setNegativeButton("Cancel") { _, _ ->
                cancelSearch()
            }
            .setOnCancelListener {
                cancelSearch()
            }
            .create()

        currentDialog = dialog

        fun clearSuggestionWork() {
            pendingSuggestion?.let {
                handler.removeCallbacks(it)
            }

            pendingSuggestion = null
            requestQueue.cancelAll(suggestionTag)
        }

        fun showSuggestionMessage(message: String) {
            suggestionsContainer.removeAllViews()

            suggestionsContainer.addView(
                TextView(activity).apply {
                    text = message
                    setPadding(0, 12, 0, 12)
                }
            )
        }

        locationInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(
                text: CharSequence?,
                start: Int,
                count: Int,
                after: Int
            ) {
            }

            override fun onTextChanged(
                text: CharSequence?,
                start: Int,
                before: Int,
                count: Int
            ) {
            }

            override fun afterTextChanged(text: Editable?) {
                if (selectingSuggestion) return

                selectedLocation = null
                suggestionVersion++

                clearSuggestionWork()
                suggestionsContainer.removeAllViews()

                val query = text.toString().trim()
                val version = suggestionVersion

                if (query.length < 3) return

                val task = Runnable {
                    if (!isActive(token) || !dialog.isShowing) {
                        return@Runnable
                    }

                    showSuggestionMessage(
                        "Finding address suggestions…"
                    )

                    val url = Uri.parse(
                        "https://api.geoapify.com/v1/geocode/autocomplete"
                    ).buildUpon()
                        .appendQueryParameter("text", query)
                        .appendQueryParameter("filter", "countrycode:us")
                        .appendQueryParameter("format", "json")
                        .appendQueryParameter("limit", "5")
                        .appendQueryParameter(
                            "apiKey",
                            ApiConfig.GEOAPIFY_KEY
                        )
                        .build()
                        .toString()

                    val request = JsonObjectRequest(
                        Request.Method.GET,
                        url,
                        null,
                        { response ->
                            if (
                                isActive(token) &&
                                dialog.isShowing &&
                                version == suggestionVersion
                            ) {
                                suggestionsContainer.removeAllViews()

                                val results = response.optJSONArray("results")

                                if (results != null) {
                                    for (index in 0 until results.length()) {
                                        val item = results.optJSONObject(index)
                                            ?: continue

                                        val latitude = item.optDouble(
                                            "lat",
                                            Double.NaN
                                        )

                                        val longitude = item.optDouble(
                                            "lon",
                                            Double.NaN
                                        )

                                        if (
                                            !validCoordinates(
                                                latitude,
                                                longitude
                                            )
                                        ) {
                                            continue
                                        }

                                        val suggestion = SearchLocation(
                                            name = readText(
                                                item,
                                                "formatted",
                                                "Matching address"
                                            ),
                                            latitude = latitude,
                                            longitude = longitude
                                        )

                                        val button = MaterialButton(
                                            activity
                                        ).apply {
                                            this.text = suggestion.name
                                            isAllCaps = false
                                            cornerRadius = GatherUi.dp(context, 15)
                                            backgroundTintList = android.content.res.ColorStateList.valueOf(GatherUi.soft)
                                            setTextColor(GatherUi.green)

                                            gravity =
                                                android.view.Gravity.START or
                                                        android.view.Gravity.CENTER_VERTICAL

                                            minHeight = (
                                                    48 *
                                                            activity.resources
                                                                .displayMetrics.density
                                                    ).roundToInt()

                                            setOnClickListener {
                                                suggestionVersion++
                                                clearSuggestionWork()

                                                selectingSuggestion = true

                                                locationInput.setText(
                                                    suggestion.name
                                                )

                                                locationInput.setSelection(
                                                    locationInput.text.length
                                                )

                                                selectingSuggestion = false
                                                selectedLocation = suggestion

                                                suggestionsContainer
                                                    .removeAllViews()
                                            }
                                        }

                                        suggestionsContainer.addView(
                                            button,
                                            LinearLayout.LayoutParams(
                                                LinearLayout.LayoutParams.MATCH_PARENT,
                                                LinearLayout.LayoutParams.WRAP_CONTENT
                                            )
                                        )
                                    }
                                }

                                if (suggestionsContainer.childCount == 0) {
                                    showSuggestionMessage(
                                        "No suggestions yet. " +
                                                "Add a city or ZIP code, " +
                                                "or tap Search this area."
                                    )
                                }
                            }
                        },
                        {
                            if (
                                isActive(token) &&
                                dialog.isShowing &&
                                version == suggestionVersion
                            ) {
                                showSuggestionMessage(
                                    "Suggestions are unavailable. " +
                                            "You can still tap Search this area."
                                )
                            }
                        }
                    )

                    request.tag = suggestionTag

                    request.retryPolicy = DefaultRetryPolicy(
                        10_000,
                        0,
                        1f
                    )

                    requestQueue.add(request)
                }

                pendingSuggestion = task
                handler.postDelayed(task, 500)
            }
        })

        dialog.setOnDismissListener {
            suggestionVersion++
            clearSuggestionWork()
        }

        dialog.show()

        fun submit(useDeviceLocation: Boolean) {
            val query = queryInput.text.toString().trim()

            if (query.isBlank()) {
                queryInput.error = "Enter a place type or business name."
                queryInput.requestFocus()
                return
            }

            val locationText = locationInput.text.toString().trim()

            if (!useDeviceLocation && locationText.isBlank()) {
                locationInput.error =
                    "Enter a location or choose Use my location."

                locationInput.requestFocus()
                return
            }

            lastQuery = query
            lastLocationText = locationText
            lastRadiusIndex = radiusSpinner.selectedItemPosition

            val match = PlaceSearchTerms.resolve(query)

            val options = SearchOptions(
                category = match.categories,
                businessName = match.businessName,
                radiusMeters = (
                        radiusMiles[radiusSpinner.selectedItemPosition] *
                                1609.344
                        ).roundToInt()
            )

            val chosenLocation = selectedLocation

            dialog.dismiss()

            when {
                useDeviceLocation ->
                    requestDeviceLocation(options)

                chosenLocation != null ->
                    findPlaces(chosenLocation, options)

                else ->
                    findLocation(locationText, options)
            }
        }

        content.findViewById<MaterialButton>(
            R.id.useDeviceLocationButton
        ).setOnClickListener {
            submit(true)
        }

        content.findViewById<MaterialButton>(
            R.id.searchAreaButton
        ).setOnClickListener {
            submit(false)
        }
    }

    private fun findLocation(
        text: String,
        options: SearchOptions
    ) {
        showLoading("Finding matching U.S. locations…")

        val url = Uri.parse(
            "https://api.geoapify.com/v1/geocode/search"
        ).buildUpon()
            .appendQueryParameter("text", text)
            .appendQueryParameter("filter", "countrycode:us")
            .appendQueryParameter("format", "json")
            .appendQueryParameter("limit", "5")
            .appendQueryParameter("apiKey", ApiConfig.GEOAPIFY_KEY)
            .build()
            .toString()

        requestJson(url) { response ->
            val results = response.getJSONArray("results")
            val locations = mutableListOf<SearchLocation>()

            for (index in 0 until results.length()) {
                val item = results.getJSONObject(index)

                val latitude = item.optDouble("lat", Double.NaN)
                val longitude = item.optDouble("lon", Double.NaN)

                if (validCoordinates(latitude, longitude)) {
                    locations.add(
                        SearchLocation(
                            name = readText(
                                item,
                                "formatted",
                                "Matching location"
                            ),
                            latitude = latitude,
                            longitude = longitude
                        )
                    )
                }
            }

            if (locations.isEmpty()) {
                finishWithMessage(
                    "No matching U.S. location. " +
                            "Try adding the state or ZIP code."
                )
            } else {
                chooseLocation(locations, options)
            }
        }
    }

    private fun chooseLocation(
        locations: List<SearchLocation>,
        options: SearchOptions
    ) {
        currentDialog?.dismiss()

        currentDialog = MaterialAlertDialogBuilder(activity)
            .setTitle("Choose the search center")
            .setItems(
                locations.map { it.name }.toTypedArray()
            ) { _, position ->
                findPlaces(locations[position], options)
            }
            .setNegativeButton("Cancel") { _, _ ->
                cancelSearch()
            }
            .setOnCancelListener {
                cancelSearch()
            }
            .show()
    }

    private fun requestDeviceLocation(options: SearchOptions) {
        if (
            hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ||
            hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        ) {
            locateDevice(options)
        } else {
            showLoading("Waiting for location permission…")

            pendingPermissionAction = {
                locateDevice(options)
            }

            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun locateDevice(options: SearchOptions) {
        val hasFine =
            hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)

        val hasCoarse =
            hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)

        if (!hasFine && !hasCoarse) {
            finishWithMessage(
                "Location permission is unavailable. Search an address instead."
            )
            return
        }

        val manager = activity.getSystemService(
            Context.LOCATION_SERVICE
        ) as LocationManager

        if (!LocationManagerCompat.isLocationEnabled(manager)) {
            finishWithMessage(
                "Device location is turned off. " +
                        "Enable it or search using an address."
            )
            return
        }

        val provider = when {
            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ->
                LocationManager.NETWORK_PROVIDER

            hasFine &&
                    manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ->
                LocationManager.GPS_PROVIDER

            else -> null
        }

        if (provider == null) {
            finishWithMessage(
                "No suitable location provider is available. " +
                        "Search using an address."
            )
            return
        }

        showLoading("Getting your location…")

        val token = session
        val cancellation = CancellationSignal()

        locationCancellation = cancellation

        val timeout = Runnable {
            if (isActive(token)) {
                finishWithMessage(
                    "Could not get your location in time. " +
                            "Try again or enter an address."
                )
            }
        }

        locationTimeout = timeout
        handler.postDelayed(timeout, 20_000)

        try {
            LocationManagerCompat.getCurrentLocation(
                manager,
                provider,
                cancellation,
                ContextCompat.getMainExecutor(activity)
            ) { location ->
                if (isActive(token)) {
                    handler.removeCallbacks(timeout)

                    locationTimeout = null
                    locationCancellation = null

                    if (
                        location == null ||
                        !validCoordinates(
                            location.latitude,
                            location.longitude
                        )
                    ) {
                        finishWithMessage(
                            "Your location was unavailable. " +
                                    "Try again or enter an address."
                        )
                    } else {
                        val accuracy = if (location.hasAccuracy()) {
                            " (accuracy about ${location.accuracy.roundToInt()} m)"
                        } else {
                            ""
                        }

                        findPlaces(
                            SearchLocation(
                                name = "Device location$accuracy",
                                latitude = location.latitude,
                                longitude = location.longitude
                            ),
                            options
                        )
                    }
                }
            }
        } catch (_: SecurityException) {
            finishWithMessage(
                "Location access was denied. Search using an address."
            )
        } catch (_: IllegalArgumentException) {
            finishWithMessage(
                "The location provider is unavailable. Search using an address."
            )
        }
    }

    private fun findPlaces(
        center: SearchLocation,
        options: SearchOptions
    ) {
        showLoading("Finding matching places nearby…")

        val coordinates = "${center.longitude},${center.latitude}"

        val builder = Uri.parse(
            "https://api.geoapify.com/v2/places"
        ).buildUpon()
            .appendQueryParameter("categories", options.category)
            .appendQueryParameter(
                "filter",
                "circle:$coordinates,${options.radiusMeters}"
            )
            .appendQueryParameter(
                "bias",
                "proximity:$coordinates"
            )
            .appendQueryParameter("limit", "100")
            .appendQueryParameter("apiKey", ApiConfig.GEOAPIFY_KEY)

        if (options.businessName.isNotBlank()) {
            builder.appendQueryParameter("name", options.businessName)
        }

        requestJson(builder.build().toString()) { response ->
            val features = response.getJSONArray("features")
            val results = mutableListOf<NearbyPlace>()

            for (index in 0 until features.length()) {
                val properties = features
                    .getJSONObject(index)
                    .getJSONObject("properties")

                val country = readText(properties, "country_code", "")
                val placeId = readText(properties, "place_id", "")
                val latitude = properties.optDouble("lat", Double.NaN)
                val longitude = properties.optDouble("lon", Double.NaN)

                if (
                    country.equals("us", ignoreCase = true) &&
                    placeId.isNotBlank() &&
                    validCoordinates(latitude, longitude)
                ) {
                    val distance = FloatArray(1)

                    Location.distanceBetween(
                        center.latitude,
                        center.longitude,
                        latitude,
                        longitude,
                        distance
                    )

                    if (distance[0] <= options.radiusMeters) {
                        results.add(
                            NearbyPlace(
                                place = ApiPlace(
                                    placeId = placeId,
                                    name = readText(
                                        properties,
                                        "name",
                                        "Unnamed place"
                                    ),
                                    address = readText(
                                        properties,
                                        "formatted",
                                        "Address unavailable"
                                    ),
                                    latitude = latitude,
                                    longitude = longitude
                                ),
                                distanceMeters = distance[0],
                                categoryLabel = categoryLabel(properties)
                            )
                        )
                    }
                }
            }

            val sortedResults = results
                .distinctBy { it.place.placeId }
                .sortedBy { it.distanceMeters }

            showPlaces(sortedResults, center, options)
        }
    }

    private fun categoryLabel(properties: JSONObject): String {
        val categories = properties.optJSONArray("categories")
            ?: return "Place"

        val keys = (0 until categories.length()).map {
            categories.optString(it)
        }

        val key = keys
            .filter { it.isNotBlank() }
            .maxByOrNull { it.count { c -> c == '.' } }
            ?: return "Place"

        return key
            .substringAfterLast('.')
            .replace('_', ' ')
            .replaceFirstChar {
                it.titlecase(Locale.getDefault())
            }
    }

    private fun showPlaces(
        results: List<NearbyPlace>,
        center: SearchLocation,
        options: SearchOptions
    ) {
        currentDialog?.dismiss()

        val content = activity.layoutInflater.inflate(
            R.layout.dialog_place_results,
            null
        )

        val radius = String.format(
            Locale.getDefault(),
            "%.0f",
            options.radiusMeters / 1609.344
        )

        content.findViewById<TextView>(R.id.resultsSummary).text =
            "\"$lastQuery\" near ${center.name}\n" +
                    "Within $radius mi · Straight-line distances\n" +
                    if (results.isEmpty()) {
                        "No matches found."
                    } else {
                        "${results.size} results shown · Nearest first"
                    }

        val empty = content.findViewById<TextView>(
            R.id.resultsEmpty
        )

        empty.visibility = if (results.isEmpty()) {
            View.VISIBLE
        } else {
            View.GONE
        }

        val list = content.findViewById<ListView>(
            R.id.placeResultsList
        )

        list.visibility = if (results.isEmpty()) {
            View.GONE
        } else {
            View.VISIBLE
        }

        list.adapter = object : BaseAdapter() {
            override fun getCount() = results.size

            override fun getItem(position: Int) = results[position]

            override fun getItemId(position: Int) = position.toLong()

            override fun getView(
                position: Int,
                convertView: View?,
                parent: ViewGroup
            ): View {
                val row = convertView ?: activity.layoutInflater.inflate(
                    R.layout.item_place_result,
                    parent,
                    false
                )

                val result = results[position]
                val place = result.place
                val miles = result.distanceMeters / 1609.344

                val distance = if (miles < 0.1) {
                    "Less than 0.1 mi away"
                } else {
                    String.format(
                        Locale.getDefault(),
                        "%.1f mi away",
                        miles
                    )
                }

                row.findViewById<TextView>(R.id.placeName).text =
                    place.name

                row.findViewById<TextView>(R.id.placeMetadata).text =
                    "${result.categoryLabel} · $distance"

                row.findViewById<TextView>(R.id.placeAddress).text =
                    place.address.removePrefix("${place.name}, ")

                row.findViewById<MaterialButton>(
                    R.id.selectPlaceButton
                ).apply {
                    contentDescription =
                        "Select ${place.name}, ${place.address}"

                    setOnClickListener {
                        // The calling activity owns saving.
                        if (searching && !closed) {
                            cancelSearch()
                            onPlaceSelected(place)
                        }
                    }
                }

                return row
            }
        }

        content.findViewById<MaterialButton>(
            R.id.editSearchButton
        ).setOnClickListener {
            currentDialog?.dismiss()
            showSearchForm(lastLocationText)
        }

        currentDialog = MaterialAlertDialogBuilder(activity)
            .setTitle("Choose a place")
            .setView(content)
            .setNegativeButton("Close") { _, _ ->
                cancelSearch()
            }
            .setOnCancelListener {
                cancelSearch()
            }
            .show()

        currentDialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            (
                    activity.resources.displayMetrics.heightPixels * 0.85f
                    ).roundToInt()
        )
    }

    private fun requestJson(
        url: String,
        onSuccess: (JSONObject) -> Unit
    ) {
        val token = session

        val request = JsonObjectRequest(
            Request.Method.GET,
            url,
            null,
            { response ->
                if (isActive(token)) {
                    try {
                        onSuccess(response)
                    } catch (_: JSONException) {
                        finishWithMessage(
                            "The server returned an unexpected response."
                        )
                    }
                }
            },
            { error ->
                if (isActive(token)) {
                    finishWithMessage(errorMessage(error))
                }
            }
        )

        request.tag = requestTag
        request.retryPolicy = DefaultRetryPolicy(15_000, 0, 1f)

        requestQueue.add(request)
    }

    private fun showLoading(message: String) {
        currentDialog?.dismiss()

        currentDialog = MaterialAlertDialogBuilder(activity)
            .setTitle("Place search")
            .setMessage(message)
            .setCancelable(false)
            .setNegativeButton("Cancel") { _, _ ->
                cancelSearch()
            }
            .show()
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            activity,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun validCoordinates(
        latitude: Double,
        longitude: Double
    ): Boolean {
        return latitude.isFinite() &&
                longitude.isFinite() &&
                latitude in -90.0..90.0 &&
                longitude in -180.0..180.0
    }

    private fun isActive(token: Int): Boolean {
        return !closed &&
                searching &&
                token == session &&
                !activity.isFinishing &&
                !activity.isDestroyed
    }

    private fun readText(
        item: JSONObject,
        key: String,
        fallback: String
    ): String {
        if (item.isNull(key)) return fallback

        return item.optString(key).trim().ifBlank {
            fallback
        }
    }

    private fun errorMessage(error: VolleyError): String {
        return when (error.networkResponse?.statusCode) {
            400 ->
                "The search was rejected. Check the search parameters."

            401, 403 ->
                "The API key was rejected. Check its restrictions."

            429 ->
                "The API limit was reached. Try again later."

            null ->
                "Could not connect. Check your internet connection."

            else ->
                "The place search failed. Please try again."
        }
    }

    private fun finishWithMessage(message: String) {
        cancelSearch()
        showMessage(message)
    }

    private fun showMessage(message: String) {
        Toast.makeText(
            activity,
            message,
            Toast.LENGTH_LONG
        ).show()
    }

    private fun cancelSearch() {
        searching = false
        session++

        pendingPermissionAction = null

        requestQueue.cancelAll(requestTag)

        locationCancellation?.cancel()
        locationCancellation = null

        locationTimeout?.let {
            handler.removeCallbacks(it)
        }

        locationTimeout = null

        currentDialog?.dismiss()
        currentDialog = null
    }

    fun close() {
        closed = true
        cancelSearch()
        requestQueue.stop()
        permissionLauncher.unregister()
    }
}