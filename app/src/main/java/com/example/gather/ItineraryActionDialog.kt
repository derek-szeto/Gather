package com.example.gather

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.widget.ScrollView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object ItineraryActionDialog {

    enum class Action {
        SET_TIME,
        CLEAR_TIME,
        MOVE_UP,
        MOVE_DOWN,
        REMOVE
    }

    fun show(
        context: Context,
        stop: Stop,
        index: Int,
        count: Int,
        onAction: (Action) -> Unit
    ) {
        val form = GatherUi.form(context)

        GatherUi.heading(form, stop.name)
        GatherUi.text(form, stop.address)
        GatherUi.text(
            form,
            "Arrival: ${
                ItineraryStopAdapter.displayTime(
                    context,
                    stop.plannedTime
                )
            }"
        )

        lateinit var dialog: AlertDialog

        fun option(
            title: String,
            action: Action,
            destructive: Boolean = false
        ) {
            GatherUi.button(form, title) {
                dialog.dismiss()
                onAction(action)
            }.apply {
                if (destructive) {
                    setTextColor(Color.rgb(151, 45, 45))
                    backgroundTintList = ColorStateList.valueOf(
                        Color.rgb(251, 235, 234)
                    )
                }
            }
        }

        option("Set arrival time", Action.SET_TIME)

        if (stop.plannedTime.isNotBlank()) {
            option("Clear arrival time", Action.CLEAR_TIME)
        }

        if (index > 0) {
            option("Move earlier", Action.MOVE_UP)
        }

        if (index < count - 1) {
            option("Move later", Action.MOVE_DOWN)
        }

        option(
            "Remove this stop",
            Action.REMOVE,
            destructive = true
        )

        dialog = MaterialAlertDialogBuilder(context)
            .setTitle("Stop ${index + 1} of $count")
            .setView(
                ScrollView(context).apply {
                    addView(form)
                }
            )
            .setNegativeButton("Done", null)
            .create()

        dialog.show()
    }
}