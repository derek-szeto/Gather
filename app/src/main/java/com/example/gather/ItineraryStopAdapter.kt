package com.example.gather

import android.content.Context
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import java.util.Calendar

class ItineraryStopAdapter(
    private val context: Context
) : BaseAdapter() {

    private var stops = emptyList<Stop>()

    fun submit(values: List<Stop>) {
        stops = values
        notifyDataSetChanged()
    }

    override fun getCount(): Int = stops.size

    override fun getItem(position: Int): Stop = stops[position]

    override fun getItemId(position: Int): Long = stops[position].id

    override fun getView(
        position: Int,
        convertView: View?,
        parent: ViewGroup
    ): View {
        val view = convertView
            ?: LayoutInflater.from(context).inflate(
                R.layout.item_itinerary_stop,
                parent,
                false
            )

        val stop = stops[position]

        view.findViewById<TextView>(R.id.stopNumber).text =
            (position + 1).toString()

        view.findViewById<TextView>(R.id.stopName).text =
            stop.name

        view.findViewById<TextView>(R.id.stopAddress).text =
            stop.address

        view.findViewById<TextView>(R.id.stopTime).text =
            displayTime(context, stop.plannedTime)

        return view
    }

    companion object {
        fun displayTime(context: Context, value: String): String {
            if (value.isBlank()) return "Time not set"

            val parts = value.split(":")
            val hour = parts.getOrNull(0)?.toIntOrNull()
            val minute = parts.getOrNull(1)?.toIntOrNull()

            if (
                hour == null ||
                minute == null ||
                hour !in 0..23 ||
                minute !in 0..59
            ) {
                return value
            }

            val time = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
            }

            return DateFormat.getTimeFormat(context).format(time.time)
        }
    }
}