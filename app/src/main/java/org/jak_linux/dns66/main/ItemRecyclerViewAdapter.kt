/* Copyright (C) 2016-2019 Julian Andres Klode <jak@jak-linux.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.jak_linux.dns66.main

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.recyclerview.widget.RecyclerView
import org.jak_linux.dns66.Configuration
import org.jak_linux.dns66.FileHelper
import org.jak_linux.dns66.ItemChangedListener
import org.jak_linux.dns66.MainActivity
import org.jak_linux.dns66.R

class ItemRecyclerViewAdapter(
    val context: Context,
    val items: MutableList<Configuration.Item>,
    val stateChoices: Int,
) : RecyclerView.Adapter<ItemRecyclerViewAdapter.ViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        LayoutInflater.from(parent.context).inflate(R.layout.view_item, parent, false)
            .also { return ViewHolder(it) }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.apply {
            item = items[position]
            titleView.text = items[position].title
            subtitleView.text = items[position].location
            updateState()
        }
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(val view: View) : RecyclerView.ViewHolder(view), View.OnClickListener {
        val titleView: TextView = view.findViewById<View>(R.id.item_title) as TextView
        val subtitleView: TextView = view.findViewById<View>(R.id.item_subtitle) as TextView
        val iconView: ImageView = view.findViewById<View>(R.id.item_enabled) as ImageView

        lateinit var item: Configuration.Item

        init {
            view.setOnClickListener(this)
            iconView.setOnClickListener(this)
        }

        fun updateState() {
            iconView.imageAlpha = 255 * 87 / 100
            if (stateChoices == 2) {
                when (item.state) {
                    Configuration.Item.STATE_IGNORE,
                    Configuration.Item.STATE_DENY -> {
                        iconView.setImageDrawable(
                            AppCompatResources.getDrawable(
                                context,
                                R.drawable.ic_check_box_outline_blank_black_24dp
                            )
                        )
                        iconView.setContentDescription(context.getString(R.string.do_not_use_dns_server))
                    }

                    Configuration.Item.STATE_ALLOW -> {
                        iconView.setImageDrawable(
                            AppCompatResources.getDrawable(
                                context,
                                R.drawable.ic_check_box_black_24dp
                            )
                        )
                        iconView.setContentDescription(context.getString(R.string.use_dns_server))
                    }
                }
            } else {
                when (item.state) {
                    Configuration.Item.STATE_IGNORE -> {
                        iconView.setImageDrawable(
                            AppCompatResources.getDrawable(
                                context,
                                R.drawable.ic_state_ignore
                            )
                        )
                        iconView.imageAlpha = 255 * 38 / 100
                    }

                    Configuration.Item.STATE_DENY -> iconView.setImageDrawable(
                        AppCompatResources.getDrawable(
                            context,
                            R.drawable.ic_state_deny
                        )
                    )

                    Configuration.Item.STATE_ALLOW -> iconView.setImageDrawable(
                        AppCompatResources.getDrawable(context, R.drawable.ic_state_allow)
                    )
                }
                iconView.setContentDescription(
                    context.resources.getStringArray(R.array.item_states)[item.state]
                )
            }
        }

        override fun onClick(v: View?) {
            val position = bindingAdapterPosition
            if (v === iconView) {
                item.state = (item.state + 1) % stateChoices
                updateState()
                FileHelper.writeSettings(context, MainActivity.config)
            } else if (v === view) {
                // Start edit activity
                val main = v.context as MainActivity
                main.editItem(stateChoices, item, object : ItemChangedListener {
                    override fun onItemChanged(item: Configuration.Item?) {
                        if (item == null) {
                            items.removeAt(position)
                            notifyItemRemoved(position)
                        } else {
                            items[position] = item
                            this@ItemRecyclerViewAdapter.notifyItemChanged(position)
                        }
                        FileHelper.writeSettings(itemView.context, MainActivity.config)
                    }
                })
            }
        }
    }
}
