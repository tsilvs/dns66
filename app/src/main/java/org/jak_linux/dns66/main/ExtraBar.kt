/* Copyright (C) 2016-2019 Julian Andres Klode <jak@jak-linux.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.jak_linux.dns66.main

import android.content.Context
import android.view.View
import android.widget.ImageView
import androidx.appcompat.content.res.AppCompatResources
import org.jak_linux.dns66.R

/**
 * Helper to set-up the on/off extra bar toggle.
 */
object ExtraBar {
    fun setup(view: View, name: String) {
        val preferences = view.context.getSharedPreferences("state", Context.MODE_PRIVATE)

        val expand = view.findViewById<ImageView>(R.id.extra_bar_toggle)
        val extra = view.findViewById<View>(R.id.extra_bar_extra)
        val l = View.OnClickListener {
            if (extra.visibility == View.GONE) {
                if (preferences.getBoolean("extraBarClosed:$name", false)) {
                    preferences.edit()
                        .putBoolean("extraBarClosed:$name", false)
                        .apply()
                }

                view.announceForAccessibility(view.context.getString(R.string.expand_bar_expanded))
                expand.setImageDrawable(
                    AppCompatResources.getDrawable(
                        view.context,
                        R.drawable.ic_expand_less_black_24dp
                    )
                )
                expand.setContentDescription(view.context.getString(R.string.expand_bar_toggle_close))
                extra.visibility = View.VISIBLE
            } else {
                if (!preferences.getBoolean("extraBarClosed:$name", false)) {
                    preferences.edit()
                        .putBoolean("extraBarClosed:$name", true)
                        .apply()
                }
                view.announceForAccessibility(view.context.getString(R.string.expand_bar_closed))
                expand.setImageDrawable(
                    AppCompatResources.getDrawable(
                        view.context,
                        R.drawable.ic_expand_more_black_24dp
                    )
                )
                expand.setContentDescription(view.context.getString(R.string.expand_bar_toggle_expand))
                extra.visibility = View.GONE
            }
        }
        expand.setOnClickListener(l)
        view.setOnClickListener(l)

        if (!preferences.getBoolean("extraBarClosed:$name", false)) {
            expand.callOnClick()
        }
    }
}
