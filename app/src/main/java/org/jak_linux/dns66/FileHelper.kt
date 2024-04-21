package org.jak_linux.dns66

import android.content.Context
import android.net.Uri
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.system.StructPollfd
import android.util.Log
import android.widget.Toast
import java.io.Closeable
import java.io.File
import java.io.FileDescriptor
import java.io.FileNotFoundException
import java.io.FileReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.UnsupportedEncodingException
import java.net.URLEncoder

/**
 * Utility object for working with files.
 */
object FileHelper {
    private const val TAG = "FileHelper"

    /**
     * Try open the file with [Context.openFileInput], falling back to a file of
     * the same name in the assets.
     */
    @Throws(IOException::class)
    fun openRead(context: Context, filename: String?): InputStream =
        try {
            context.openFileInput(filename)
        } catch (e: FileNotFoundException) {
            context.assets.open(filename!!)
        }

    /**
     * Write to the given file in the private files dir, first renaming an old one to .bak
     *
     * @param context  A context
     * @param filename A filename as for [Context.openFileOutput]
     * @return See [Context.openFileOutput]
     * @throws IOException See @{link {@link Context#openFileOutput(String, int)}}
     */
    @Throws(IOException::class)
    fun openWrite(context: Context, filename: String): OutputStream? {
        val out = context.getFileStreamPath(filename)

        // Create backup
        out.renameTo(context.getFileStreamPath("$filename.bak"))
        return context.openFileOutput(filename, Context.MODE_PRIVATE)
    }

    @Throws(IOException::class)
    private fun readConfigFile(
        context: Context,
        name: String,
        defaultsOnly: Boolean
    ): Configuration {
        val stream: InputStream = if (defaultsOnly) {
            context.assets.open(name)
        } else {
            openRead(context, name)
        }

        return Configuration.read(InputStreamReader(stream))
    }

    fun loadCurrentSettings(context: Context): Configuration =
        try {
            readConfigFile(context, "settings.json", false)
        } catch (e: Exception) {
            Toast.makeText(
                context,
                context.getString(R.string.cannot_read_config, e.localizedMessage),
                Toast.LENGTH_LONG
            ).show()
            loadPreviousSettings(context)
        }

    fun loadPreviousSettings(context: Context): Configuration =
        try {
            readConfigFile(context, "settings.json.bak", false)
        } catch (e: Exception) {
            Toast.makeText(
                context,
                context.getString(R.string.cannot_restore_previous_config, e.localizedMessage),
                Toast.LENGTH_LONG
            ).show()
            loadDefaultSettings(context)!!
        }

    fun loadDefaultSettings(context: Context): Configuration? =
        try {
            readConfigFile(context, "settings.json", true)
        } catch (e: Exception) {
            Toast.makeText(
                context,
                context.getString(R.string.cannot_load_default_config, e.localizedMessage),
                Toast.LENGTH_LONG
            ).show()
            null
        }

    fun writeSettings(context: Context, config: Configuration) {
        Log.d(TAG, "writeSettings: Writing the settings file")
        try {
            OutputStreamWriter(openWrite(context, "settings.json")).use {
                config.write(it)
            }
        } catch (e: IOException) {
            Toast.makeText(
                context,
                context.getString(R.string.cannot_write_config, e.localizedMessage),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    /**
     * Returns a file where the item should be downloaded to.
     *
     * @param context A context to work in
     * @param item    A configuration item.
     * @return File or null, if that item is not downloadable.
     */
    fun getItemFile(context: Context, item: Configuration.Item): File? =
        if (item.isDownloadable()) {
            try {
                File(context.getExternalFilesDir(null), URLEncoder.encode(item.location, "UTF-8"))
            } catch (e: UnsupportedEncodingException) {
                e.printStackTrace()
                null
            }
        } else {
            null
        }

    @Throws(FileNotFoundException::class)
    fun openItemFile(context: Context, item: Configuration.Item): InputStreamReader? {
        return if (item.location.startsWith("content://")) {
            try {
                InputStreamReader(context.contentResolver.openInputStream(Uri.parse(item.location)))
            } catch (e: SecurityException) {
                Log.d("FileHelper", "openItemFile: Cannot open", e)
                throw FileNotFoundException(e.message)
            }
        } else {
            getItemFile(context, item) ?: return null
            if (item.isDownloadable()) {
                InputStreamReader(
                    SingleWriterMultipleReaderFile(getItemFile(context, item)!!).openRead()
                )
            } else {
                FileReader(getItemFile(context, item))
            }
        }
    }

    /**
     * Wrapper around [Os.poll] that automatically restarts on EINTR
     * While post-Lollipop devices handle that themselves, we need to do this for Lollipop.
     *
     * @param fds     Descriptors and events to wait on
     * @param timeout Timeout. Should be -1 for infinite, as we do not lower the timeout when
     *                retrying due to an interrupt
     * @return The number of fds that have events
     * @throws ErrnoException See [Os.poll]
     */
    @Throws(ErrnoException::class, InterruptedException::class)
    fun poll(fds: Array<StructPollfd?>, timeout: Int): Int {
        while (true) {
            if (Thread.interrupted()) {
                throw InterruptedException()
            }

            return try {
                Os.poll(fds, timeout)
            } catch (e: ErrnoException) {
                if (e.errno == OsConstants.EINTR) {
                    continue
                }
                throw e
            }
        }
    }

    fun closeOrWarn(fd: FileDescriptor?, tag: String?, message: String): FileDescriptor? {
        try {
            if (fd != null) {
                Os.close(fd)
            }
        } catch (e: ErrnoException) {
            Log.e(tag, "closeOrWarn: $message", e)
        }

        // Always return null
        return null
    }

    fun <T : Closeable?> closeOrWarn(fd: T, tag: String?, message: String): FileDescriptor? {
        try {
            fd?.close()
        } catch (e: java.lang.Exception) {
            Log.e(tag, "closeOrWarn: $message", e)
        }

        // Always return null
        return null
    }
}
