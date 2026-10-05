package com.example.gather

import android.app.TimePickerDialog
import android.os.Bundle
import android.text.format.DateFormat
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.Executors

class ItineraryActivity : AppCompatActivity() {

    private lateinit var database: DatabaseHelper
    private lateinit var placeSearchHelper: PlaceSearchHelper

    private val databaseThread =
        Executors.newSingleThreadExecutor()

    private lateinit var addStopButton: Button
    private lateinit var statusText: TextView
    private lateinit var stopsList: ListView

    private var hangoutId = -1L
    private var city = ""
    private var stops = emptyList<Stop>()
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_Gather)
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContentView(R.layout.activity_itinerary)

        val root = findViewById<View>(R.id.itineraryRoot)

        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val safeArea = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                        WindowInsetsCompat.Type.displayCutout()
            )

            view.setPadding(
                safeArea.left,
                safeArea.top,
                safeArea.right,
                safeArea.bottom
            )

            insets
        }

        hangoutId = intent.getLongExtra("hangout_id", -1L)
        city = intent.getStringExtra("hangout_city").orEmpty()

        if (hangoutId <= 0L) {
            Toast.makeText(
                this,
                "Could not open this hangout.",
                Toast.LENGTH_LONG
            ).show()

            finish()
            return
        }

        database = DatabaseHelper(applicationContext)

        addStopButton = findViewById(R.id.addStopButton)
        statusText = findViewById(R.id.itineraryStatus)
        stopsList = findViewById(R.id.stopsList)

        findViewById<TextView>(R.id.itineraryTitle).text =
            intent.getStringExtra("hangout_title") ?: "Itinerary"

        findViewById<TextView>(R.id.itineraryCity).text = city

        placeSearchHelper = PlaceSearchHelper(this) { place ->
            // Save the API result directly into the stops table.
            changeStops {
                database.insertStop(hangoutId, place)
            }
        }

        findViewById<Button>(R.id.backButton).setOnClickListener {
            finish()
        }

        addStopButton.setOnClickListener {
            placeSearchHelper.startSearch(city)
        }

        stopsList.setOnItemClickListener { _, _, position, _ ->
            if (!busy) {
                showStopActions(stops[position])
            }
        }

        // Read saved data. Opening this screen makes no API request.
        changeStops { }
    }

    // Perform a database action, then load the updated itinerary.
    private fun changeStops(action: () -> Unit) {
        setBusy(true)
        statusText.text = "Updating itinerary…"

        databaseThread.execute {
            val result = runCatching {
                action()
                database.getStops(hangoutId)
            }

            runOnUiThread {
                if (!isDestroyed && !isFinishing) {
                    setBusy(false)

                    result.fold(
                        onSuccess = { savedStops ->
                            renderStops(savedStops)
                        },
                        onFailure = { error ->
                            Log.e(
                                "SimplePlans",
                                "Itinerary operation failed",
                                error
                            )

                            statusText.text =
                                "Could not update the itinerary. Reopen it to retry."

                            Toast.makeText(
                                this,
                                "The database operation failed.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    )
                }
            }
        }
    }

    private fun renderStops(savedStops: List<Stop>) {
        stops = savedStops

        val rows = savedStops.mapIndexed { index, stop ->
            val time = stop.plannedTime.ifBlank {
                "Time not set"
            }

            "${index + 1}. ${stop.name}\n$time\n${stop.address}"
        }

        stopsList.adapter = ArrayAdapter(
            this,
            R.layout.item_hangout,
            R.id.planText,
            rows
        )

        statusText.text = if (savedStops.isEmpty()) {
            "No stops yet. Find a place to start your itinerary."
        } else {
            "Saved on this device. Tap a stop to edit it."
        }
    }

    private fun showStopActions(stop: Stop) {
        val index = stops.indexOfFirst { it.id == stop.id }

        val options = mutableListOf(
            "Set time",
            "Clear time"
        )

        if (index > 0) {
            options.add("Move up")
        }

        if (index >= 0 && index < stops.lastIndex) {
            options.add("Move down")
        }

        options.add("Delete stop")

        AlertDialog.Builder(this)
            .setTitle(stop.name)
            .setItems(options.toTypedArray()) { _, selected ->
                when (options[selected]) {
                    "Set time" -> chooseStopTime(stop)

                    "Clear time" -> changeStops {
                        check(
                            database.updateStopTime(
                                hangoutId,
                                stop.id,
                                ""
                            ) == 1
                        )
                    }

                    "Move up" -> changeStops {
                        database.moveStop(
                            hangoutId,
                            stop.id,
                            -1
                        )
                    }

                    "Move down" -> changeStops {
                        database.moveStop(
                            hangoutId,
                            stop.id,
                            1
                        )
                    }

                    "Delete stop" -> confirmDelete(stop)
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun chooseStopTime(stop: Stop) {
        val now = Calendar.getInstance()

        val existingTime = stop.plannedTime.split(":")

        val hour = existingTime.getOrNull(0)?.toIntOrNull()
            ?: now.get(Calendar.HOUR_OF_DAY)

        val minute = existingTime.getOrNull(1)?.toIntOrNull()
            ?: now.get(Calendar.MINUTE)

        TimePickerDialog(
            this,
            { _, selectedHour, selectedMinute ->
                // Store a consistent 24-hour value, such as 14:30.
                val time = String.format(
                    Locale.US,
                    "%02d:%02d",
                    selectedHour,
                    selectedMinute
                )

                changeStops {
                    check(
                        database.updateStopTime(
                            hangoutId,
                            stop.id,
                            time
                        ) == 1
                    )
                }
            },
            hour,
            minute,
            DateFormat.is24HourFormat(this)
        ).show()
    }

    private fun confirmDelete(stop: Stop) {
        AlertDialog.Builder(this)
            .setTitle("Remove this stop?")
            .setMessage(stop.name)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Remove") { _, _ ->
                changeStops {
                    check(
                        database.deleteStop(
                            hangoutId,
                            stop.id
                        ) == 1
                    )
                }
            }
            .show()
    }

    private fun setBusy(value: Boolean) {
        busy = value
        addStopButton.isEnabled = !value
        stopsList.isEnabled = !value
    }

    override fun onDestroy() {
        if (::placeSearchHelper.isInitialized) {
            placeSearchHelper.close()
        }

        if (::database.isInitialized) {
            databaseThread.execute {
                database.close()
            }
        }

        databaseThread.shutdown()

        super.onDestroy()
    }
}