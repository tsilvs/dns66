/* Copyright (C) 2016-2019 Julian Andres Klode <jak@jak-linux.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.jak_linux.dns66

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.annotation.Keep
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.io.IOException
import java.io.Reader
import java.io.Writer

/**
 * Configuration class. This is serialized as JSON using read() and write() methods.
 *
 * @author Julian Andres Klode
 */
class Configuration {
    companion object {
        private const val TAG = "Configuration"

        val GSON = Gson()

        private const val VERSION = 2

        /* Default tweak level */
        const val MINOR_VERSION = 3

        @Throws(IOException::class)
        fun read(reader: Reader): Configuration {
            val config = GSON.fromJson(reader, Configuration::class.java)
            if (config.version > VERSION) {
                throw IOException("Unhandled file format version")
            }

            for (i in config.minorVersion + 1..MINOR_VERSION) {
                config.runUpdate(i)
            }

            config.updateURL(
                "http://someonewhocares.org/hosts/hosts",
                "https://someonewhocares.org/hosts/hosts",
                0
            )

            return config
        }
    }

    var version = 1
    var minorVersion = 0
    var autoStart = false
    var hosts = Hosts()
    var dnsServers = DnsServers()

    // Apologies for the legacy alternate
    @Keep
    @SerializedName(value = "allowlist", alternate = ["whitelist"])
    var allowlist = Allowlist()
    var showNotification = true
    var nightMode = false
    var watchDog = false
    var ipV6Support = true

    fun runUpdate(level: Int) {
        when (level) {
            1 -> {
                /* Switch someonewhocares to https */
                updateURL(
                    "http://someonewhocares.org/hosts/hosts",
                    "https://someonewhocares.org/hosts/hosts",
                    -1
                )

                /* Switch to StevenBlack's host file */
                addURL(
                    0, "StevenBlack's hosts file (includes all others)",
                    "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts",
                    Item.STATE_DENY
                )
                updateURL("https://someonewhocares.org/hosts/hosts", null, Item.STATE_IGNORE)
                updateURL("https://adaway.org/hosts.txt", null, Item.STATE_IGNORE)
                updateURL(
                    "https://www.malwaredomainlist.com/hostslist/hosts.txt",
                    null,
                    Item.STATE_IGNORE
                )
                updateURL(
                    "https://pgl.yoyo.org/adservers/serverlist.php?hostformat=hosts&showintro=1&mimetype=plaintext",
                    null,
                    Item.STATE_IGNORE
                )

                /* Remove broken host */
                removeURL("http://winhelp2002.mvps.org/hosts.txt")

                /* Update digitalcourage dns and add cloudflare */
                updateDNS("85.214.20.141", "46.182.19.48")
                addDNS("CloudFlare DNS (1)", "1.1.1.1", false)
                addDNS("CloudFlare DNS (2)", "1.0.0.1", false)
            }

            2 -> removeURL("https://hosts-file.net/ad_servers.txt")
            3 -> disableURL("https://blokada.org/blocklists/ddgtrackerradar/standard/hosts.txt")
        }
        minorVersion = level
    }

    fun updateURL(oldURL: String, newURL: String?, newState: Int) {
        for (host in hosts.items) {
            if (host.location == oldURL) {
                if (newURL != null) {
                    host.location = newURL
                }

                if (newState >= 0) {
                    host.state = newState
                }
            }
        }
    }

    fun updateDNS(oldIP: String, newIP: String) {
        for (host in dnsServers.items) {
            if (host.location == oldIP) {
                host.location = newIP
            }
        }
    }

    fun addDNS(title: String, location: String, isEnabled: Boolean) {
        val item = Item().apply {
            this.title = title
            this.location = location
            state = if (isEnabled) 1 else 0
        }
        dnsServers.items.add(item)
    }

    fun addURL(index: Int, title: String, location: String, state: Int) {
        val item = Item().apply {
            this.title = title
            this.location = location
            this.state = state
        }
        hosts.items.add(index, item)
    }

    fun removeURL(oldURL: String) {
        val itr = hosts.items.iterator()
        while (itr.hasNext()) {
            val host = itr.next()
            if (host.location == oldURL) {
                itr.remove()
            }
        }
    }

    fun disableURL(oldURL: String) {
        Log.d(TAG, String.format("disableURL: Disabling %s", oldURL))
        val itr: Iterator<*> = hosts.items.iterator()
        while (itr.hasNext()) {
            val host = itr.next() as Item
            if (host.location == oldURL) {
                host.state = Item.STATE_IGNORE
            }
        }
    }

    @Throws(IOException::class)
    fun write(writer: Writer?) = GSON.toJson(this, writer)

    class Item {
        companion object {
            const val STATE_IGNORE = 2
            const val STATE_DENY = 0
            const val STATE_ALLOW = 1
        }

        var title: String = ""
        var location: String = ""
        var state = 0

        fun isDownloadable(): Boolean {
            return location.startsWith("https://") || location.startsWith("http://")
        }
    }

    @Keep
    inner class Hosts {
        var enabled = false
        var automaticRefresh = false
        var items: MutableList<Item> = ArrayList()
    }

    @Keep
    inner class DnsServers {
        var enabled = false
        var items: MutableList<Item> = ArrayList()
    }

    @Keep
    class Allowlist {
        companion object {
            /**
             * All apps use the VPN.
             */
            const val DEFAULT_MODE_ON_VPN = 0

            /**
             * No apps use the VPN.
             */
            const val DEFAULT_MODE_NOT_ON_VPN = 1

            /**
             * System apps (excluding browsers) do not use the VPN.
             */
            const val DEFAULT_MODE_INTELLIGENT = 2
        }

        var showSystemApps = false

        /**
         * The default mode to put apps in, that are not listed in the lists.
         */
        var defaultMode = DEFAULT_MODE_ON_VPN

        /**
         * Apps that should not be allowed on the VPN
         */
        var items: MutableList<String> = ArrayList()

        /**
         * Apps that should be on the VPN
         */
        var itemsOnVpn: MutableList<String> = ArrayList()

        /**
         * Categorizes all packages in the system into "on vpn" or
         * "not on vpn".
         *
         * @param pm       A {@link PackageManager}
         * @param onVpn    names of packages to use the VPN
         * @param notOnVpn Names of packages not to use the VPN
         */
        fun resolve(pm: PackageManager, onVpn: MutableSet<String>, notOnVpn: MutableSet<String>) {
            val webBrowserPackageNames: MutableSet<String> = HashSet()
            val resolveInfoList = pm.queryIntentActivities(newBrowserIntent(), 0)
            for (resolveInfo in resolveInfoList) {
                webBrowserPackageNames.add(resolveInfo.activityInfo.packageName)
            }

            webBrowserPackageNames.apply {
                add("com.google.android.webview")
                add("com.android.htmlviewer")
                add("com.google.android.backuptransport")
                add("com.google.android.gms")
                add("com.google.android.gsf")
            }

            for (applicationInfo in pm.getInstalledApplications(0)) {
                // We need to always keep ourselves using the VPN, otherwise our
                // watchdog does not work.
                if (applicationInfo.packageName == BuildConfig.APPLICATION_ID) {
                    onVpn.add(applicationInfo.packageName)
                } else if (itemsOnVpn.contains(applicationInfo.packageName)) {
                    onVpn.add(applicationInfo.packageName)
                } else if (items.contains(applicationInfo.packageName)) {
                    notOnVpn.add(applicationInfo.packageName)
                } else if (defaultMode == DEFAULT_MODE_ON_VPN) {
                    onVpn.add(applicationInfo.packageName)
                } else if (defaultMode == DEFAULT_MODE_NOT_ON_VPN) {
                    notOnVpn.add(applicationInfo.packageName)
                } else if (defaultMode == DEFAULT_MODE_INTELLIGENT) {
                    if (webBrowserPackageNames.contains(applicationInfo.packageName)) {
                        onVpn.add(applicationInfo.packageName)
                    } else if (applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0) {
                        notOnVpn.add(applicationInfo.packageName)
                    } else {
                        onVpn.add(applicationInfo.packageName)
                    }
                }
            }
        }

        /**
         * Returns an intent for opening a website, used for finding
         * web browsers. Extracted method for mocking.
         */
        fun newBrowserIntent(): Intent =
            Intent(Intent.ACTION_VIEW).setData(Uri.parse("https://isabrowser.dns66.jak-linux.org/"))
    }
}
