package com.example.gather

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.widget.ScrollView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object LegacyPlanActionDialog {
    fun show(
        context: Context,
        plan: Hangout,
        onOpen: () -> Unit,
        onEdit: () -> Unit,
        onDelete: () -> Unit
    ) {
        val content = GatherUi.form(context)
        GatherUi.heading(content, plan.title)
        GatherUi.text(content, "Area · ${plan.city.ifBlank { "Not set" }}")
        GatherUi.text(content, "When · ${plan.whenText.ifBlank { "Time to be decided" }}")

        if (plan.notes.isNotBlank()) {
            GatherUi.text(content, "Notes", 13f, GatherUi.green)
            GatherUi.text(content, plan.notes)
        }

        lateinit var dialog: AlertDialog

        GatherUi.button(content, "Open itinerary", primary = true) {
            dialog.dismiss()
            onOpen()
        }

        GatherUi.button(content, "Edit plan") {
            dialog.dismiss()
            onEdit()
        }

        GatherUi.button(content, "Delete plan") {
            dialog.dismiss()
            onDelete()
        }.apply {
            setTextColor(Color.rgb(151, 45, 45))
            backgroundTintList = ColorStateList.valueOf(Color.rgb(251, 235, 234))
        }

        dialog = MaterialAlertDialogBuilder(context)
            .setTitle("Plan options")
            .setView(ScrollView(context).apply { addView(content) })
            .setNegativeButton("Close", null)
            .create()

        dialog.show()
    }
}