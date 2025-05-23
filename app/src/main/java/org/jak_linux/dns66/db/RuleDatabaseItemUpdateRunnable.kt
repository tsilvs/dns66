/* Copyright (C) 2017 Julian Andres Klode <jak@jak-linux.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.jak_linux.dns66.db

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Process
import android.util.Log
import org.jak_linux.dns66.Configuration
import org.jak_linux.dns66.FileHelper
import org.jak_linux.dns66.R
import org.jak_linux.dns66.SingleWriterMultipleReaderFile
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.SocketTimeoutException
import java.net.URL
import java.util.Date
import javax.net.ssl.HttpsURLConnection

/**
 * Updates a single item.
 */
class RuleDatabaseItemUpdateRunnable(
    private val parentTask: RuleDatabaseUpdateTask,
    private val context: Context,
    private val item: Configuration.Item,
) : Runnable {
    companion object {
        private const val CONNECT_TIMEOUT_MILLIS = 3000
        private const val READ_TIMEOUT_MILLIS = 10000
        private const val TAG = "RuleDbItemUpdate"
    }

    private lateinit var url: URL
    private var file: File? = null

    fun shouldDownload(): Boolean {
        if (item.state == Configuration.Item.STATE_IGNORE) {
            return false
        }

        // Not sure if that is slow or not
        if (item.location.startsWith("content://")) {
            return true
        }

        file = FileHelper.getItemFile(context, item)
        if (file == null || !item.isDownloadable()) {
            return false
        }

        try {
            url = URL(item.location)
        } catch (e: MalformedURLException) {
            parentTask.addError(item, context.getString(R.string.invalid_url_s, item.location))
            return false
        }

        return true
    }

    /**
     * Runs the item download, and marks it as done when finished.getLocalizedMessage
     */
    override fun run() {
        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
        } catch (_: UnsatisfiedLinkError) {
        } catch (e: RuntimeException) {
            if (!e.toString().contains("not mocked")) {
                throw e
            }
        }

        if (item.location.startsWith("content://")) {
            try {
                val uri = parseUri(item.location)
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )

                context.contentResolver.openInputStream(uri)?.close()
                Log.d(TAG, "run: Permission requested for ${item.location}")
            } catch (e: SecurityException) {
                Log.d(TAG, "doInBackground: Error taking permission: $e")
                parentTask.addError(item, context.getString(R.string.permission_denied))
            } catch (e: FileNotFoundException) {
                parentTask.addError(item, context.getString(R.string.file_not_found))
            } catch (e: IOException) {
                parentTask.addError(
                    item,
                    context.getString(R.string.unknown_error_s, e.getLocalizedMessage())
                )
            }
            return
        }

        val singleWriterMultipleReaderFile = SingleWriterMultipleReaderFile(file!!)
        var connection: HttpURLConnection? = null
        parentTask.addBegin(item)
        try {
            connection = getHttpURLConnection(file!!, singleWriterMultipleReaderFile, url)

            if (validateResponse(connection)) {
                return
            }
            downloadFile(file!!, singleWriterMultipleReaderFile, connection)
        } catch (e: SocketTimeoutException) {
            parentTask.addError(item, context.getString(R.string.requested_timed_out))
        } catch (e: IOException) {
            parentTask.addError(item, context.getString(R.string.unknown_error_s, e.toString()))
        } catch (e: NullPointerException) {
            parentTask.addError(item, context.getString(R.string.unknown_error_s, e.toString()))
        } finally {
            parentTask.addDone(item)
            connection?.disconnect()
        }
    }

    /**
     * Internal helper for testing
     */
    fun parseUri(uri: String): Uri = Uri.parse(uri)

    /**
     * Opens a new HTTP connection.
     *
     * @param file                           Target file
     * @param singleWriterMultipleReaderFile Target file
     * @param url                            URL to download from
     * @return An initialized HTTP connection.
     * @throws IOException
     */
    @Throws(IOException::class)
    fun getHttpURLConnection(
        file: File,
        singleWriterMultipleReaderFile: SingleWriterMultipleReaderFile,
        url: URL
    ): HttpURLConnection {
        val connection = internalOpenHttpConnection(url)
        connection.apply {
            instanceFollowRedirects = true
            connectTimeout = CONNECT_TIMEOUT_MILLIS
            readTimeout = READ_TIMEOUT_MILLIS
        }

        try {
            singleWriterMultipleReaderFile.openRead().close()
            connection.ifModifiedSince = file.lastModified()
        } catch (_: IOException) {
        }

        connection.connect()
        return connection
    }

    // Internal helper for testing.
    @Throws(IOException::class)
    fun internalOpenHttpConnection(url: URL): HttpURLConnection =
        url.openConnection() as HttpsURLConnection

    /**
     * Checks if we should read from the URL.
     *
     * @param connection The connection that was established.
     * @return true if there was no problem.
     * @throws IOException If an I/O Exception occured.
     */
    @Throws(IOException::class)
    fun validateResponse(connection: HttpURLConnection): Boolean {
        Log.d(
            TAG,
            "validateResponse: ${item.title}: local = ${Date(connection.ifModifiedSince)} remote = ${
                Date(connection.lastModified)
            }"
        )
        if (connection.responseCode != 200) {
            Log.d(
                TAG,
                "validateResponse: ${item.title}: Skipping: Server responded with ${connection.responseCode} for ${item.location}"
            )

            if (connection.responseCode == 404) {
                parentTask.addError(item, context.getString(R.string.file_not_found))
            } else if (connection.responseCode != 304) {
                context.resources.getString(R.string.host_update_error_item)
                parentTask.addError(
                    item,
                    context.resources.getString(
                        R.string.host_update_error_item,
                        connection.getResponseCode(),
                        connection.getResponseMessage()
                    )
                )
            }
            return false
        }
        return true
    }

    /**
     * Downloads a file from a connection to an singleWriterMultipleReaderFile.
     *
     * @param file                           The file to write to
     * @param singleWriterMultipleReaderFile The atomic file for the destination file
     * @param connection                     The connection to read from
     * @throws IOException I/O exceptions.
     */
    @Throws(IOException::class)
    fun downloadFile(
        file: File,
        singleWriterMultipleReaderFile: SingleWriterMultipleReaderFile,
        connection: HttpURLConnection
    ) {
        val inStream = connection.inputStream
        var outStream: FileOutputStream? = singleWriterMultipleReaderFile.startWrite()
        try {
            copyStream(inStream, outStream!!)

            singleWriterMultipleReaderFile.finishWrite(outStream)
            outStream = null

            // Write has started, set modification time
            if (connection.lastModified == 0L || !file.setLastModified(connection.lastModified)) {
                Log.d(TAG, "downloadFile: Could not set last modified")
            }
        } finally {
            if (outStream != null) {
                singleWriterMultipleReaderFile.failWrite(outStream)
            }
        }
    }

    /**
     * Copies one stream to another.
     *
     * @param inStream  Input stream
     * @param outStream Output stream
     * @throws IOException If an exception occured.
     */
    @Throws(IOException::class)
    fun copyStream(inStream: InputStream, outStream: OutputStream) {
        val buffer = ByteArray(4096)
        var rc = inStream.read(buffer)
        while (rc != -1) {
            outStream.write(buffer, 0, rc)
            rc = inStream.read(buffer)
        }
    }
}
