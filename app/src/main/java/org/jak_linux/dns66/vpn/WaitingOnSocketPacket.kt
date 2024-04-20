package org.jak_linux.dns66.vpn

import org.pcap4j.packet.IpPacket
import java.net.DatagramSocket

/**
 * Helper class holding a socket, the packet we are waiting the answer for, and a time
 */
class WaitingOnSocketPacket(val socket: DatagramSocket, val packet: IpPacket) {
    private val time = System.currentTimeMillis()

    fun ageSeconds(): Long {
        return (System.currentTimeMillis() - time) / 1000
    }
}
