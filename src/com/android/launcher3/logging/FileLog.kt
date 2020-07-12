package com.android.launcher3.logging

import android.os.Handler
import android.os.HandlerThread
import android.os.Message
import android.util.Log
import android.util.Pair
import com.android.launcher3.Utilities
import com.android.launcher3.config.FeatureFlags
import java.io.*
import java.text.DateFormat
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Wrapper around [Log] to allow writing to a file.
 * This class can safely be called from main thread.
 *
 * Note: This should only be used for logging errors which have a persistent effect on user's data,
 * but whose effect may not be visible immediately.
 */
object FileLog {
    @JvmField
    val ENABLED = FeatureFlags.IS_DOGFOOD_BUILD || Utilities.IS_DEBUG_DEVICE
    private const val FILE_NAME_PREFIX = "log-"
    private val DATE_FORMAT = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
    private const val MAX_LOG_FILE_SIZE = (4 shl 20 // 4 mb
            .toLong().toInt()).toLong()
    private var sHandler: Handler? = null
    private var sLogsDirectory: File? = null
    @JvmStatic
    fun setDir(logsDir: File) {
        if (ENABLED) {
            synchronized(DATE_FORMAT) {
                // If the target directory changes, stop any active thread.
                if (sHandler != null && logsDir != sLogsDirectory) {
                    (sHandler!!.looper.thread as HandlerThread).quit()
                    sHandler = null
                }
            }
        }
        sLogsDirectory = logsDir
    }

    fun d(tag: String, msg: String, e: Exception) {
        Log.d(tag, msg, e)
        print(tag, msg, e)
    }

    @JvmStatic
    fun d(tag: String, msg: String) {
        Log.d(tag, msg)
        print(tag, msg)
    }

    @JvmStatic
    fun e(tag: String, msg: String, e: Exception) {
        Log.e(tag, msg, e)
        print(tag, msg, e)
    }

    @JvmStatic
    fun e(tag: String, msg: String) {
        Log.e(tag, msg)
        print(tag, msg)
    }

    @JvmStatic
    @JvmOverloads
    fun print(tag: String, msg: String, e: Exception? = null) {
        if (!ENABLED) {
            return
        }
        var out = String.format("%s %s %s", DATE_FORMAT.format(Date()), tag, msg)
        if (e != null) {
            out += "\n${Log.getStackTraceString(e)}"
        }
        Message.obtain(handler, LogWriterCallback.MSG_WRITE, out).sendToTarget()
    }

    private val handler: Handler?
        get() {
            synchronized(DATE_FORMAT) {
                if (sHandler == null) {
                    val thread = HandlerThread("file-logger")
                    thread.start()
                    sHandler = Handler(thread.looper, LogWriterCallback())
                }
            }
            return sHandler
        }

    /**
     * Blocks until all the pending logs are written to the disk
     * @param out if not null, all the persisted logs are copied to the writer.
     */
    @JvmStatic
    @Throws(InterruptedException::class)
    fun flushAll(out: PrintWriter) {
        if (!ENABLED) {
            return
        }
        val latch = CountDownLatch(1)
        Message.obtain(handler, LogWriterCallback.MSG_FLUSH,
                Pair.create(out, latch)).sendToTarget()
        latch.await(2, TimeUnit.SECONDS)
    }

    private fun dumpFile(out: PrintWriter?, fileName: String) {
        val logFile = File(sLogsDirectory, fileName)
        if (logFile.exists()) {
            var `in`: BufferedReader? = null
            try {
                `in` = BufferedReader(FileReader(logFile))
                out!!.println()
                out.println("--- logfile: $fileName ---")
                var line: String?
                while (`in`.readLine().also { line = it } != null) {
                    out.println(line)
                }
            } catch (e: Exception) {
                // ignore
            } finally {
                Utilities.closeSilently(`in`)
            }
        }
    }

    /**
     * Writes logs to the file.
     * Log files are named log-0 for even days of the year and log-1 for odd days of the year.
     * Logs older than 36 hours are purged.
     */
    private class LogWriterCallback : Handler.Callback {
        private var currentFileName: String? = null
        private var currentWriter: PrintWriter? = null
        private fun closeWriter() {
            Utilities.closeSilently(currentWriter)
            currentWriter = null
        }

        override fun handleMessage(msg: Message): Boolean {
            if (sLogsDirectory == null || !ENABLED) {
                return true
            }
            when (msg.what) {
                MSG_WRITE -> {
                    val cal = Calendar.getInstance()
                    // suffix with 0 or 1 based on the day of the year.
                    val fileName = FILE_NAME_PREFIX + (cal[Calendar.DAY_OF_YEAR] and 1)
                    if (fileName != currentFileName) {
                        closeWriter()
                    }
                    try {
                        if (currentWriter == null) {
                            currentFileName = fileName
                            var append = false
                            val logFile = File(sLogsDirectory, fileName)
                            if (logFile.exists()) {
                                val modifiedTime = Calendar.getInstance()
                                modifiedTime.timeInMillis = logFile.lastModified()

                                // If the file was modified more that 36 hours ago, purge the file.
                                // We use instead of 24 to account for day-365 followed by day-1
                                modifiedTime.add(Calendar.HOUR, 36)
                                append = (cal.before(modifiedTime)
                                        && logFile.length() < MAX_LOG_FILE_SIZE)
                            }
                            currentWriter = PrintWriter(FileWriter(logFile, append))
                        }
                        currentWriter!!.println(msg.obj as String)
                        currentWriter!!.flush()

                        // Auto close file stream after some time.
                        sHandler!!.removeMessages(MSG_CLOSE)
                        sHandler!!.sendEmptyMessageDelayed(MSG_CLOSE, CLOSE_DELAY)
                    } catch (e: Exception) {
                        Log.e("FileLog", "Error writing logs to file", e)
                        // Close stream, will try reopening during next log
                        closeWriter()
                    }
                    return true
                }
                MSG_CLOSE -> {
                    closeWriter()
                    return true
                }
                MSG_FLUSH -> {
                    closeWriter()
                    val p = msg.obj as Pair<PrintWriter?, CountDownLatch>
                    if (p.first != null) {
                        dumpFile(p.first, FILE_NAME_PREFIX + 0)
                        dumpFile(p.first, FILE_NAME_PREFIX + 1)
                    }
                    p.second.countDown()
                    return true
                }
            }
            return true
        }

        companion object {
            private const val CLOSE_DELAY = 5000L // 5 seconds
            const val MSG_WRITE = 1
            private const val MSG_CLOSE = 2
            const val MSG_FLUSH = 3
        }
    }
}