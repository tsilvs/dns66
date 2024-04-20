/* Copyright (C) 2016-2019 Julian Andres Klode <jak@jak-linux.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.jak_linux.dns66.main

import android.util.Log
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import org.jak_linux.dns66.FileHelper
import org.jak_linux.dns66.MainActivity
import java.util.Collections

/**
 * Simple ItemTouchHelper callback for a collection based adapter.
 */
class ItemTouchHelperCallback(val adapter: ItemRecyclerViewAdapter) :
    ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {
    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        val a = viewHolder.bindingAdapterPosition
        val b = target.bindingAdapterPosition

        Collections.swap(adapter.items, a, b)
        adapter.notifyItemMoved(a, b)

        return true
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
    }

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        super.clearView(recyclerView, viewHolder)
        Log.d("ItemTouchHelperCallback", "clearView: Done with interaction. Saving settings.")
        FileHelper.writeSettings(viewHolder.itemView.context, MainActivity.config)
    }
}
