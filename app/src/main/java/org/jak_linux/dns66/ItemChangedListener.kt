/* Copyright (C) 2016-2019 Julian Andres Klode <jak@jak-linux.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.jak_linux.dns66

/**
 * A callback for returns from the [ItemActivity].
 * The method [onItemChanged] will be called with a new item as
 * returned by the ItemActivity.
 */
interface ItemChangedListener {
    fun onItemChanged(item: Configuration.Item?)
}
