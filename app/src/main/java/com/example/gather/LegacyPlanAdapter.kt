package com.example.gather

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView

class LegacyPlanAdapter(context: Context) : BaseAdapter() {
    private val inflater = LayoutInflater.from(context)
    private var plans = emptyList<Hangout>()

    fun submit(values: List<Hangout>) {
        plans = values.toList()
        notifyDataSetChanged()
    }

    override fun getCount() = plans.size

    override fun getItem(position: Int): Hangout = plans[position]

    override fun getItemId(position: Int) = plans[position].id

    override fun hasStableIds() = true

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: inflater.inflate(R.layout.item_legacy_plan, parent, false)
        val plan = getItem(position)

        view.findViewById<TextView>(R.id.legacyPlanTitle).text = plan.title
        view.findViewById<TextView>(R.id.legacyPlanArea).text =
            plan.city.ifBlank { "Area not set" }
        view.findViewById<TextView>(R.id.legacyPlanTime).text =
            plan.whenText.ifBlank { "Time to be decided" }

        return view
    }
}