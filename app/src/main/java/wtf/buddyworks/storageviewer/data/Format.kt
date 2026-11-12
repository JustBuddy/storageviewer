package wtf.buddyworks.storageviewer.data

import java.text.NumberFormat
import java.util.Locale

/** Human-readable sizes, using decimal units like Android and One UI do (1 GB = 1000³ bytes). */
object Format {
    private val units = arrayOf("B", "KB", "MB", "GB", "TB", "PB")

    /** NumberFormat is costly to create and not thread-safe: one set per thread and locale. */
    private class Formatters(val locale: Locale) {
        val integer: NumberFormat = NumberFormat.getIntegerInstance(locale)
        val percent: NumberFormat = NumberFormat.getPercentInstance(locale).apply { maximumFractionDigits = 0 }
    }

    private val formatters = ThreadLocal<Formatters>()

    private fun formatters(): Formatters {
        val locale = Locale.getDefault()
        formatters.get()?.takeIf { it.locale == locale }?.let { return it }
        return Formatters(locale).also { formatters.set(it) }
    }

    fun bytes(bytes: Long): String {
        val (number, unit) = bytesParts(bytes)
        return "$number $unit"
    }

    /** Splits a size into number and unit so the UI can style them separately. */
    fun bytesParts(bytes: Long): Pair<String, String> {
        if (bytes < 1000) return bytes.coerceAtLeast(0).toString() to units[0]
        var value = bytes.toDouble()
        var unit = 0
        while (value >= 999.5 && unit < units.lastIndex) {
            value /= 1000.0
            unit++
        }
        // Three significant digits: 4.97 MB, 13.7 MB, 175 GB.
        val decimals = when {
            value >= 99.95 -> 0
            value >= 9.995 -> 1
            else -> 2
        }
        return String.format(Locale.getDefault(), "%.${decimals}f", value) to units[unit]
    }

    fun count(n: Number): String = formatters().integer.format(n)

    enum class AgoUnit { Today, Days, Weeks, Months, Years }

    /** How long before [now] [time] was, in whole days, weeks, months or years (epoch ms). */
    fun ago(time: Long, now: Long): Pair<AgoUnit, Int> {
        val days = ((now - time) / DAY_MS).coerceAtLeast(0L).toInt()
        return when {
            days < 1 -> AgoUnit.Today to 0
            days < 14 -> AgoUnit.Days to days
            days < 60 -> AgoUnit.Weeks to days / 7
            days < 730 -> AgoUnit.Months to days / 30
            else -> AgoUnit.Years to days / 365
        }
    }

    private const val DAY_MS = 24L * 60 * 60 * 1000

    /** Percentage of a whole; tiny non-zero shares read as "<1 %" instead of a misleading 0. */
    fun percent(part: Long, whole: Long): String {
        val format = formatters().percent
        if (whole <= 0L) return format.format(0)
        val fraction = part.toDouble() / whole.toDouble()
        return if (fraction > 0.0 && fraction < 0.005) "<" + format.format(0.01) else format.format(fraction)
    }
}
