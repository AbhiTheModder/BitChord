package com.music.bitchord.desktop

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Live log of lyrics lookups, shown in the lyrics panel when Settings asks for it.
 *
 * Deliberately separate from [DesktopTrackLog], which answers "why did this song sound wrong" and
 * stays readable by holding only the playback path.
 */
internal object DesktopLyricsLog {

    enum class Level { INFO, SUCCESS, WARN, ERROR }

    data class Entry(
        val timestamp: Long,
        val tag: String,
        val message: String,
        val level: Level = Level.INFO,
    ) {
        val formattedTime: String
            get() = LocalTime.ofInstant(
                java.time.Instant.ofEpochMilli(timestamp),
                java.time.ZoneId.systemDefault(),
            ).format(CLOCK)
    }

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())

    val entries: StateFlow<List<Entry>> = _entries

    fun i(tag: String, message: String) = add(tag, message, Level.INFO)

    fun s(tag: String, message: String) = add(tag, message, Level.SUCCESS)

    fun w(tag: String, message: String) = add(tag, message, Level.WARN)

    fun e(tag: String, message: String) = add(tag, message, Level.ERROR)

    fun clear() {
        _entries.value = emptyList()
    }

    private fun add(tag: String, message: String, level: Level) {
        val entry = Entry(System.currentTimeMillis(), tag, message, level)
        val current = _entries.value
        _entries.value = if (current.size >= MAX_ENTRIES) {
            current.drop(current.size - MAX_ENTRIES + 1) + entry
        } else {
            current + entry
        }
    }

    private const val MAX_ENTRIES = 120

    private val CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss")
}
