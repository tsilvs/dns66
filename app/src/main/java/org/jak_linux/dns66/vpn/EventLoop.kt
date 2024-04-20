package org.jak_linux.dns66.vpn

import org.pcap4j.packet.IpPacket
import java.net.DatagramPacket

/**
 * Interface abstracting away [AdVpnThread].
 */
interface EventLoop {
    /**
     * Called to send a packet to a remote location
     *
     * @param packet        The packet to send
     * @param requestPacket If specified, the event loop must wait for a response, and then
     * call [DnsPacketProxy.handleDnsResponse] for the data
     * of the response, with this packet as the first argument.
     */
    @Throws(VpnNetworkException::class)
    fun forwardPacket(packet: DatagramPacket?, requestPacket: IpPacket?)

    /**
     * Write an IP packet to the local TUN device
     *
     * @param packet The packet to write (a response to a DNS request)
     */
    fun queueDeviceWrite(packet: IpPacket?)
}
