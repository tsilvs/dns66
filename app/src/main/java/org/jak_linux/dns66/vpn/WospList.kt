package org.jak_linux.dns66.vpn

import android.util.Log
import java.util.LinkedList

/**
 * Queue of WaitingOnSocketPacket, bound on time and space.
 */
class WospList {
    companion object {
        private const val TAG = "WospList"
    }

    private val list: LinkedList<WaitingOnSocketPacket> = LinkedList<WaitingOnSocketPacket>()

    fun add(wosp: WaitingOnSocketPacket) {
        if (list.size > AdVpnThread.DNS_MAXIMUM_WAITING) {
            Log.d(TAG, "Dropping socket due to space constraints: ${list.element().socket}")
            list.element().socket.close()
            list.remove()
        }

        while (!list.isEmpty() && list.element().ageSeconds() > AdVpnThread.DNS_TIMEOUT_SEC) {
            Log.d(TAG, "Timeout on socket " + list.element().socket)
            list.element().socket.close()
            list.remove()
        }

        list.add(wosp)
    }

    operator fun iterator(): MutableIterator<WaitingOnSocketPacket> = list.iterator()

    fun size(): Int = list.size
}
