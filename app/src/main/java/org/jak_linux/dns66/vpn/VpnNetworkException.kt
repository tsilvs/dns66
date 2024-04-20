package org.jak_linux.dns66.vpn

class VpnNetworkException : Exception {
    constructor(s: String?) : super(s)
    constructor(s: String?, t: Throwable?) : super(s, t)
}
