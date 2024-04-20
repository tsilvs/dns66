/* Copyright (C) 2016-2019 Julian Andres Klode <jak@jak-linux.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.jak_linux.dns66.main

import android.content.Context
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import org.jak_linux.dns66.R

/**
 * Adapter for the pager that holds the fragments of the main activity.
 */
class MainFragmentPagerAdapter(
    val context: Context,
    fm: FragmentManager
) : FragmentPagerAdapter(fm) {
    override fun getItem(position: Int): Fragment =
        when (position) {
            0 -> StartFragment()
            1 -> HostsFragment()
            2 -> AllowlistFragment()
            else -> DNSFragment()
        }

    override fun getPageTitle(position: Int): CharSequence? =
        when (position) {
            0 -> context.getString(R.string.start_tab)
            1 -> context.getString(R.string.hosts_tab)
            2 -> context.getString(R.string.allowlist_tab)
            3 -> context.getString(R.string.dns_tab)
            else -> null
        }

    override fun getCount(): Int = 4
}
