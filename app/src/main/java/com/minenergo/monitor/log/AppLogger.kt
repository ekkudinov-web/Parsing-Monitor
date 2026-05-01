package com.minenergo.monitor.log

import android.content.Context
import android.util.Log
import com.minenergo.monitor.Config
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Простой логгер с записью в файл `<filesDir>/logs/minenergo.log`.
 *
 * Раздел 16.9 ТЗ: в лог запрещено писать секреты. У нативного приложения
 * секретов нет (бот-токена больше нет), поэтому логгер пишет только
 * технические сведения.
 */
object AppLogger {

    private const val TAG_APP = "MinenergoMonitor"

    @Volatile
    private var logFile: File? = null

    private val ts: SimpleDateFormat =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT)

    fun init(context: Context) {
        val dir = File(context.filesDir, Config.LOGS_SUBDIR)
        if (!dir.exists()) dir.mkdirs()
        logFile = File(dir, Config.LOG_FILE)
    }

    fun logFile(): File? = logFile

    fun i(tag: String, message: String) {
        Log.i(TAG_APP, "[$tag] $message")
        write("INFO ", tag, message, null)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        Log.w(TAG_APP, "[$tag] $message", throwable)
        write("WARN ", tag, message, throwable)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        Log.e(TAG_APP, "[$tag] $message", throwable)
        write("ERROR", tag, message, throwable)
    }

    @Synchronized
    private fun write(level: String, tag: String, message: String, throwable: Throwable?) {
        val file = logFile ?: return
        try {
            // Простейшая ротация: если файл превысил лимит — переименовываем.
            if (file.exists() && file.length() > Config.LOG_MAX_BYTES) {
                val backup = File(file.parentFile, file.name + ".1")
                if (backup.exists()) backup.delete()
                file.renameTo(backup)
            }
            file.appendText(
                "${ts.format(Date())} | $level | $tag | $message\n"
            )
            if (throwable != null) {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                file.appendText(sw.toString())
            }
        } catch (_: Throwable) {
            // Не должны падать из-за лога
        }
    }
}
