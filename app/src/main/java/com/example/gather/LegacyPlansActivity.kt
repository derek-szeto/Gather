package com.example.gather

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.text.format.DateFormat
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors

class LegacyPlansActivity : AppCompatActivity() {

    private lateinit var database: DatabaseHelper
    private lateinit var placeSearchHelper: PlaceSearchHelper

    private val databaseThread = Executors.newSingleThreadExecutor()

    private val preferences by lazy {
        getSharedPreferences("user_preferences", MODE_PRIVATE)
    }

    private lateinit var homePanel: View
    private lateinit var formPanel: View
    private lateinit var formHeading: TextView
    private lateinit var titleInput: EditText
    private lateinit var cityInput: EditText
    private lateinit var whenInput: EditText
    private lateinit var notesInput: EditText
    private lateinit var newButton: Button
    private lateinit var saveButton: Button
    private lateinit var cancelButton: Button
    private lateinit var chooseTimeButton: Button
    private lateinit var findPlacesButton: Button
    private lateinit var hangoutList: ListView
    private lateinit var planAdapter: LegacyPlanAdapter
    private lateinit var statusText: TextView
    private lateinit var selectedPlacesStatus: TextView
    private lateinit var selectedPlacesContainer: LinearLayout

    private val selectedPlaces = mutableListOf<ApiPlace>()
    private var hangouts = emptyList<Hangout>()
    private var editingId = 0L
    private var formOpen = false
    private var busy = false
    private var saveToken = UUID.randomUUID().toString()

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_Gather)
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        database = DatabaseHelper(applicationContext)

        connectViews()
        configureInsets()
        planAdapter = LegacyPlanAdapter(this)
        hangoutList.adapter = planAdapter

        placeSearchHelper = PlaceSearchHelper(this) { place ->
            if (formOpen && !busy) {
                when {
                    selectedPlaces.any { it.placeId == place.placeId } ->
                        showMessage("This place is already selected.")

                    selectedPlaces.size >= 30 ->
                        showMessage("Save this plan before adding more places.")

                    else -> {
                        selectedPlaces.add(place)
                        renderSelectedPlaces()
                    }
                }
            }
        }

        setUpButtons()

        editingId = savedInstanceState?.getLong("editing_id") ?: 0L
        saveToken = savedInstanceState?.getString("save_token")
            ?: UUID.randomUUID().toString()

        restorePlaces(savedInstanceState?.getString("selected_places"))

        showForm(savedInstanceState?.getBoolean("form_open") ?: false)
        renderSelectedPlaces()
        refreshHangouts()
    }

    private fun connectViews() {
        homePanel = findViewById(R.id.homePanel)
        formPanel = findViewById(R.id.formPanel)
        formHeading = findViewById(R.id.formHeading)
        titleInput = findViewById(R.id.titleInput)
        cityInput = findViewById(R.id.cityInput)
        whenInput = findViewById(R.id.whenInput)
        notesInput = findViewById(R.id.notesInput)
        newButton = findViewById(R.id.newButton)
        saveButton = findViewById(R.id.saveButton)
        cancelButton = findViewById(R.id.cancelButton)
        chooseTimeButton = findViewById(R.id.chooseTimeButton)
        findPlacesButton = findViewById(R.id.findPlacesButton)
        hangoutList = findViewById(R.id.hangoutList)
        statusText = findViewById(R.id.statusText)
        selectedPlacesStatus = findViewById(R.id.selectedPlacesStatus)
        selectedPlacesContainer = findViewById(R.id.selectedPlacesContainer)
    }

    private fun configureInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(
            findViewById(R.id.main)
        ) { view, insets ->
            val safe = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                        WindowInsetsCompat.Type.displayCutout() or
                        WindowInsetsCompat.Type.ime()
            )

            view.setPadding(safe.left, safe.top, safe.right, safe.bottom)
            insets
        }
    }

    private fun setUpButtons() {
        findViewById<Button>(R.id.legacyHomeButton).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            })
            finish()
        }

        newButton.setOnClickListener {
            openForm(null)
        }

        cancelButton.setOnClickListener {
            selectedPlaces.clear()
            showForm(false)
        }

        saveButton.setOnClickListener {
            saveHangout()
        }

        chooseTimeButton.setOnClickListener {
            chooseDateAndTime()
        }

        findPlacesButton.setOnClickListener {
            if (!busy) {
                placeSearchHelper.startSearch(
                    cityInput.text.toString().trim()
                )
            }
        }

        hangoutList.setOnItemClickListener { _, _, position, _ ->
            if (!busy) {
                hangouts.getOrNull(position)?.let {
                    showActions(it)
                }
            }
        }
    }

    private fun showForm(show: Boolean) {
        formOpen = show
        homePanel.visibility = if (show) View.GONE else View.VISIBLE
        formPanel.visibility = if (show) View.VISIBLE else View.GONE

        formHeading.setText(
            if (editingId == 0L) R.string.hp_new_hangout
            else R.string.hp_edit_hangout
        )
    }

    private fun openForm(hangout: Hangout?) {
        editingId = hangout?.id ?: 0L
        saveToken = UUID.randomUUID().toString()
        selectedPlaces.clear()

        titleInput.setText(hangout?.title.orEmpty())
        cityInput.setText(
            hangout?.city ?: preferences.getString("default_city", "")
        )
        whenInput.setText(hangout?.whenText.orEmpty())
        notesInput.setText(hangout?.notes.orEmpty())

        titleInput.error = null
        cityInput.error = null

        renderSelectedPlaces()
        showForm(true)
    }

    private fun renderSelectedPlaces() {
        selectedPlacesContainer.removeAllViews()

        selectedPlacesStatus.text = when {
            selectedPlaces.isNotEmpty() ->
                "${selectedPlaces.size} places ready to save. Tap one to remove it."

            editingId != 0L ->
                "Add more places here. Manage existing stops in Open itinerary."

            else ->
                "No places selected yet. You can save a plan without places."
        }

        selectedPlaces.forEach { place ->
            val button = MaterialButton(this).apply {
                text = "${place.name}\n${place.address}"
                isAllCaps = false
                cornerRadius = GatherUi.dp(context, 14)
                backgroundTintList = ColorStateList.valueOf(GatherUi.soft)
                setTextColor(GatherUi.green)
                isEnabled = !busy
                isSaveEnabled = false

                setOnClickListener {
                    if (!busy) {
                        MaterialAlertDialogBuilder(this@LegacyPlansActivity)
                            .setTitle("Remove selected place?")
                            .setMessage(place.name)
                            .setNegativeButton("Keep", null)
                            .setPositiveButton("Remove") { _, _ ->
                                selectedPlaces.remove(place)
                                renderSelectedPlaces()
                            }
                            .show()
                    }
                }
            }

            selectedPlacesContainer.addView(
                button,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }
    }

    private fun saveHangout() {
        if (busy) return

        val title = titleInput.text.toString().trim()
        val city = cityInput.text.toString().trim()

        if (title.isBlank()) {
            titleInput.error = getString(R.string.hp_required)
            titleInput.requestFocus()
            return
        }

        val hangout = Hangout(
            id = editingId,
            title = title,
            city = city,
            whenText = whenInput.text.toString().trim(),
            notes = notesInput.text.toString().trim()
        )

        val placesToSave = selectedPlaces.toList()
        val token = saveToken

        runDatabase(
            work = {
                database.saveHangoutWithPlaces(
                    hangout,
                    placesToSave,
                    token
                )
            },
            onSuccess = { savedId ->
                editingId = savedId
                selectedPlaces.clear()
                saveToken = UUID.randomUUID().toString()

                preferences.edit()
                    .putString("default_city", city)
                    .apply()

                showForm(false)
                showMessage("Plan saved on this device.")
                refreshHangouts()
            }
        )
    }

    private fun chooseDateAndTime() {
        val selected = Calendar.getInstance()

        val formatter = SimpleDateFormat(
            "MMM d, yyyy 'at' h:mm a",
            Locale.getDefault()
        ).apply {
            isLenient = false
        }

        runCatching {
            formatter.parse(whenInput.text.toString().trim())
        }.getOrNull()?.let {
            selected.time = it
        }

        DatePickerDialog(
            this,
            { _, year, month, day ->
                selected.set(year, month, day)

                TimePickerDialog(
                    this,
                    { _, hour, minute ->
                        selected.set(Calendar.HOUR_OF_DAY, hour)
                        selected.set(Calendar.MINUTE, minute)
                        selected.set(Calendar.SECOND, 0)
                        selected.set(Calendar.MILLISECOND, 0)
                        whenInput.setText(formatter.format(selected.time))
                    },
                    selected.get(Calendar.HOUR_OF_DAY),
                    selected.get(Calendar.MINUTE),
                    DateFormat.is24HourFormat(this)
                ).show()
            },
            selected.get(Calendar.YEAR),
            selected.get(Calendar.MONTH),
            selected.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun showActions(hangout: Hangout) {
        LegacyPlanActionDialog.show(this, hangout,
            onOpen = {
                startActivity(
                    Intent(this, ItineraryActivity::class.java).apply {
                        putExtra("hangout_id", hangout.id)
                        putExtra("hangout_title", hangout.title)
                        putExtra("hangout_city", hangout.city)
                    }
                )
            },
            onEdit = { openForm(hangout) },
            onDelete = { confirmDelete(hangout) }
        )
    }

    private fun confirmDelete(hangout: Hangout) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete this plan?")
            .setMessage("${hangout.title} and all its itinerary stops will be removed from this device.")
            .setNegativeButton("Keep plan", null)
            .setPositiveButton("Delete plan") { _, _ ->
                runDatabase(
                    work = {
                        check(database.deleteHangout(hangout.id) == 1)
                        database.getAllHangouts()
                    },
                    onSuccess = {
                        renderHangouts(it)
                    }
                )
            }
            .show()
            .getButton(AlertDialog.BUTTON_POSITIVE)
            .setTextColor(Color.rgb(151, 45, 45))
    }

    private fun refreshHangouts() {
        statusText.setText(R.string.hp_loading)

        runDatabase(
            work = { database.getAllHangouts() },
            onSuccess = { renderHangouts(it) }
        )
    }

    private fun renderHangouts(saved: List<Hangout>) {
        hangouts = saved
        planAdapter.submit(saved)

        statusText.setText(
            if (saved.isEmpty()) R.string.hp_empty
            else R.string.hp_tap_plan
        )
    }

    private fun setBusy(value: Boolean) {
        busy = value

        listOf(
            newButton, saveButton, cancelButton,
            chooseTimeButton, findPlacesButton,
            titleInput, cityInput, whenInput,
            notesInput, hangoutList
        ).forEach {
            it.isEnabled = !value
        }

        for (index in 0 until selectedPlacesContainer.childCount) {
            selectedPlacesContainer.getChildAt(index).isEnabled = !value
        }
    }

    private fun <T> runDatabase(
        work: () -> T,
        onSuccess: (T) -> Unit
    ) {
        if (busy) return
        setBusy(true)

        databaseThread.execute {
            val result = runCatching(work)

            runOnUiThread {
                if (!isDestroyed && !isFinishing) {
                    setBusy(false)

                    result.fold(
                        onSuccess = onSuccess,
                        onFailure = {
                            Log.e("Gather", "Database operation failed", it)
                            statusText.setText(R.string.hp_database_error)
                            showMessage(getString(R.string.hp_database_error))
                        }
                    )
                }
            }
        }
    }

    private fun encodePlaces(): String {
        val array = JSONArray()

        selectedPlaces.forEach { place ->
            array.put(
                JSONObject().apply {
                    put("id", place.placeId)
                    put("name", place.name)
                    put("address", place.address)
                    put("lat", place.latitude)
                    put("lon", place.longitude)
                }
            )
        }

        return array.toString()
    }

    private fun restorePlaces(json: String?) {
        if (json == null) return

        runCatching {
            val array = JSONArray(json)

            List(array.length()) { index ->
                val item = array.getJSONObject(index)

                ApiPlace(
                    placeId = item.getString("id"),
                    name = item.getString("name"),
                    address = item.getString("address"),
                    latitude = item.getDouble("lat"),
                    longitude = item.getDouble("lon")
                )
            }
        }.onSuccess {
            selectedPlaces.addAll(it)
        }
    }

    private fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong("editing_id", editingId)
        outState.putBoolean("form_open", formOpen)
        outState.putString("save_token", saveToken)
        outState.putString("selected_places", encodePlaces())
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