package com.example.gather

import android.content.Context
import android.net.Uri
import com.android.volley.DefaultRetryPolicy
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import org.json.JSONObject

class PlaceRepository(context: Context) {
    private val queue =
        Volley.newRequestQueue(context.applicationContext)

    private fun request(
        path: String,
        params: Map<String, String>,
        tag: Any,
        error: (String) -> Unit,
        next: (JSONObject) -> Unit
    ) {
        if (
            ApiConfig.GEOAPIFY_KEY.isBlank() ||
            ApiConfig.GEOAPIFY_KEY.startsWith("YOUR_")
        ) {
            error("Add your Geoapify key to ApiConfig.kt.")
            return
        }

        // uri encoding keeps typed addresses safe as query parameters.
        val url = Uri.parse(
            "https://api.geoapify.com/$path"
        ).buildUpon()

        params.forEach { (key, value) ->
            url.appendQueryParameter(key, value)
        }

        url.appendQueryParameter(
            "apiKey",
            ApiConfig.GEOAPIFY_KEY
        )

        val request = JsonObjectRequest(
            Request.Method.GET,
            url.build().toString(),
            null,
            { data ->
                runCatching {
                    next(data)
                }.onFailure {
                    error("Unexpected place response.")
                }
            },
            {
                error(
                    if (it.networkResponse?.statusCode == 429) {
                        "Geoapify limit reached. Try later."
                    } else {
                        "Place service unavailable. Check the connection and API key."
                    }
                )
            }
        )

        request.tag = tag
        request.retryPolicy = DefaultRetryPolicy(12000, 0, 1f)

        queue.add(request)
    }

    fun addresses(
        text: String,
        tag: Any,
        error: (String) -> Unit,
        next: (List<SearchArea>) -> Unit
    ) {
        request(
            "v1/geocode/autocomplete",
            mapOf(
                "text" to text,
                "filter" to "countrycode:us",
                "format" to "json",
                "limit" to "5"
            ),
            tag,
            error
        ) { data ->
            val items = data.optJSONArray("results")

            val areas = (0 until (items?.length() ?: 0))
                .mapNotNull { index ->
                    val item = items!!.getJSONObject(index)
                    val lat = item.optDouble("lat", Double.NaN)
                    val lon = item.optDouble("lon", Double.NaN)

                    if (!coordinatesValid(lat, lon)) {
                        null
                    } else {
                        SearchArea(
                            item.optString("formatted", text),
                            lat,
                            lon
                        )
                    }
                }

            next(areas)
        }
    }

    fun recommendations(
        area: SearchArea,
        tag: Any,
        error: (String) -> Unit,
        next: (List<ApiPlace>) -> Unit
    ) {
        val center = "${area.longitude},${area.latitude}"

        request(
            "v2/places",
            mapOf(
                "categories" to
                        "catering.cafe,catering.restaurant,leisure.park",
                "filter" to "circle:$center,8000",
                "bias" to "proximity:$center",
                "limit" to "12"
            ),
            tag,
            error
        ) { data ->
            val items = data.optJSONArray("features")

            val places = (0 until (items?.length() ?: 0))
                .mapNotNull { index ->
                    val properties = items!!
                        .getJSONObject(index)
                        .getJSONObject("properties")

                    val lat = properties.optDouble("lat", Double.NaN)
                    val lon = properties.optDouble("lon", Double.NaN)

                    if (
                        !coordinatesValid(lat, lon) ||
                        properties.optString("place_id").isBlank()
                    ) {
                        null
                    } else {
                        ApiPlace(
                            placeId = properties.getString("place_id"),
                            name = properties.optString("name")
                                .ifBlank { "Nearby place" },
                            address = properties.optString("formatted")
                                .ifBlank { "Address unavailable" },
                            latitude = lat,
                            longitude = lon
                        )
                    }
                }
                .distinctBy { it.placeId }

            next(places)
        }
    }

    fun cancel(tag: Any) {
        queue.cancelAll(tag)
    }

    fun close() {
        queue.cancelAll { true }
        queue.stop()
    }
}

fun coordinatesValid(
    lat: Double,
    lon: Double
): Boolean {
    return lat.isFinite() &&
            lon.isFinite() &&
            lat in -90.0..90.0 &&
            lon in -180.0..180.0
}

class AreaStore(
    context: Context,
    uid: String
) {
    private val prefs = context.getSharedPreferences(
        "area_$uid",
        Context.MODE_PRIVATE
    )

    fun read(): SearchArea? {
        if (!prefs.contains("lat")) return null

        return SearchArea(
            label = prefs.getString(
                "label",
                "Selected area"
            ).orEmpty(),
            latitude = Double.fromBits(prefs.getLong("lat", 0)),
            longitude = Double.fromBits(prefs.getLong("lon", 0))
        )
    }

    fun save(area: SearchArea) {
        // search locations stay on this device under the current account.
        prefs.edit()
            .putString("label", area.label)
            .putLong("lat", area.latitude.toBits())
            .putLong("lon", area.longitude.toBits())
            .apply()
    }
}