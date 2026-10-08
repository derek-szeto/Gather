package com.example.gather

import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.text.format.DateFormat
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.Executors

class ItineraryActivity : AppCompatActivity() {

    private lateinit var database: DatabaseHelper
    private lateinit var placeSearchHelper: PlaceSearchHelper
    private lateinit var stopAdapter: ItineraryStopAdapter

    private val databaseThread = Executors.newSingleThreadExecutor()

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
            val safe = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                        WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom)
            insets
        }

        hangoutId = intent.getLongExtra("hangout_id", -1L)
        city = intent.getStringExtra("hangout_city").orEmpty()

        if (hangoutId <= 0L) {
            Toast.makeText(
                this,
                "Could not open this itinerary.",
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

        findViewById<TextView>(R.id.itineraryCity).text =
            city.ifBlank { "Plan your stops, times, and order." }

        stopAdapter = ItineraryStopAdapter(this)
        stopsList.adapter = stopAdapter

        placeSearchHelper = PlaceSearchHelper(this) { place ->
            changeStops {
                database.insertStop(hangoutId, place)
            }
        }

        findViewById<Button>(R.id.backButton).setOnClickListener {
            finish()
        }

        findViewById<Button>(R.id.homeButton).setOnClickListener {
            startActivity(
                Intent(this, MainActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            )
            finish()
        }

        addStopButton.setOnClickListener {
            placeSearchHelper.startSearch(city)
        }

        stopsList.setOnItemClickListener { _, _, position, _ ->
            if (!busy) {
                stops.getOrNull(position)?.let(::showStopActions)
            }
        }

        changeStops { }
    }

    private fun changeStops(action: () -> Unit) {
        if (busy) return

        setBusy(true)
        statusText.text = "Updating itinerary…"

        databaseThread.execute {
            val result = runCatching {
                action()
                database.getStops(hangoutId)
            }

            runOnUiThread {
                if (isDestroyed || isFinishing) {
                    return@runOnUiThread
                }

                setBusy(false)

                result.fold(
                    onSuccess = ::renderStops,
                    onFailure = { error ->
                        Log.e(
                            "Gather",
                            "Itinerary operation failed",
                            error
                        )
                        statusText.text =
                            "Could not update the itinerary. Please try again."

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

    private fun renderStops(savedStops: List<Stop>) {
        stops = savedStops
        stopAdapter.submit(savedStops)

        statusText.text = if (savedStops.isEmpty()) {
            "No stops yet. Find a place to start your itinerary."
        } else {
            val count = savedStops.size
            "$count ${if (count == 1) "stop" else "stops"} saved on this device · tap a card to change it"
        }
    }

    private fun showStopActions(stop: Stop) {
        val index = stops.indexOfFirst {
            it.id == stop.id
        }
        if (index < 0) return

        ItineraryActionDialog.show(
            this,
            stop,
            index,
            stops.size
        ) { action ->
            when (action) {
                ItineraryActionDialog.Action.SET_TIME ->
                    chooseStopTime(stop)

                ItineraryActionDialog.Action.CLEAR_TIME ->
                    changeStops {
                        check(
                            database.updateStopTime(
                                hangoutId,
                                stop.id,
                                ""
                            ) == 1
                        )
                    }

                ItineraryActionDialog.Action.MOVE_UP ->
                    changeStops {
                        database.moveStop(
                            hangoutId,
                            stop.id,
                            -1
                        )
                    }

                ItineraryActionDialog.Action.MOVE_DOWN ->
                    changeStops {
                        database.moveStop(
                            hangoutId,
                            stop.id,
                            1
                        )
                    }

                ItineraryActionDialog.Action.REMOVE ->
                    confirmDelete(stop)
            }
        }
    }

    private fun chooseStopTime(stop: Stop) {
        val now = Calendar.getInstance()
        val existing = stop.plannedTime.split(":")

        val hour = existing.getOrNull(0)?.toIntOrNull()
            ?: now.get(Calendar.HOUR_OF_DAY)

        val minute = existing.getOrNull(1)?.toIntOrNull()
            ?: now.get(Calendar.MINUTE)

        TimePickerDialog(
            this,
            { _, chosenHour, chosenMinute ->
                // store a consistent 24-hour value so the device can format it for display.
                val time = String.format(
                    Locale.US,
                    "%02d:%02d",
                    chosenHour,
                    chosenMinute
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
        MaterialAlertDialogBuilder(this)
            .setTitle("Remove ${stop.name}?")
            .setMessage(
                "This removes the stop and its planned time from this itinerary."
            )
            .setNegativeButton("Keep stop", null)
            .setPositiveButton("Remove stop") { _, _ ->
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