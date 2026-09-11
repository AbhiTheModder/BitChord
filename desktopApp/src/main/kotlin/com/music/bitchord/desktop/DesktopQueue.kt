package com.music.bitchord.desktop

import com.music.bitchord.data.model.Song

/** The live queue: what is playing, what played before it, and what is next. */
internal data class DesktopQueue(
    val songs: List<Song> = emptyList(),
    val index: Int = 0,
) {
    val current: Song? get() = songs.getOrNull(index)

    val hasNext: Boolean get() = index < songs.lastIndex

    val hasPrevious: Boolean get() = index > 0

    /** What is still to come, which is what "Up Next" means. */
    val upcoming: List<Song> get() = if (index >= songs.lastIndex) emptyList() else songs.drop(index + 1)

    /**
     * Where AutoPlay's section begins, and so where a track queued by hand belongs — above the mix,
     * below everything the listener picked.
     */
    val autoplaySectionStart: Int
        get() {
            val after = (index + 1).coerceIn(0, songs.size)
            return (after until songs.size).firstOrNull { songs[it].fromAutoplay } ?: songs.size
        }

    /** Moves to [target], dropping anything a forward jump skipped past. */
    fun jumpTo(target: Int): DesktopQueue {
        if (target !in songs.indices) return this
        val skipped = skippedByJump(index, target)
            ?: return copy(index = target).trimmed()
        return copy(
            songs = songs.filterIndexed { at, _ -> at !in skipped },
            index = target - skipped.count(),
        ).trimmed()
    }

    fun next(): DesktopQueue = if (hasNext) jumpTo(index + 1) else this

    fun previous(): DesktopQueue = if (hasPrevious) copy(index = index - 1) else this

    /** Adds tracks at the end — where AutoPlay's own additions go. */
    /** Slots one track into the running order at [position]. */
    fun insert(position: Int, song: Song): DesktopQueue = copy(
        songs = songs.toMutableList().apply { add(position.coerceIn(0, size), song) },
    )

    fun append(more: List<Song>): DesktopQueue =
        if (more.isEmpty()) this else copy(songs = songs + more)

    /**
     * Everything AutoPlay put after the current track, removed.
     *
     * Switching AutoPlay off is the listener saying they do not want those tracks — Android's
     * `dropAutoplayTracksFromQueue`. What they queued by hand stays where it is.
     */
    fun withoutAutoplay(): DesktopQueue {
        val from = index + 1
        if (from >= songs.size) return this
        val kept = songs.take(from) + songs.drop(from).filterNot { it.fromAutoplay }
        return if (kept.size == songs.size) this else copy(songs = kept)
    }

    /**
     * Drops completed entries older than the history window, keeping [index] pointing at the same
     * song.
     */
    fun trimmed(): DesktopQueue {
        val trim = historyTrimCount(index)
        if (trim <= 0) return this
        return copy(songs = songs.drop(trim), index = index - trim)
    }

    /** Shuffle as an edit to the queue rather than a playback mode. */
    fun shuffledAhead(): DesktopQueue {
        val from = index + 1
        if (from >= songs.size) return this
        val (mix, own) = songs.drop(from).partition { it.fromAutoplay }
        return copy(songs = songs.take(from) + own.shuffled() + mix.shuffled())
    }

    /** Puts the upcoming tracks back in [originalOrder], by id. */
    fun inOrderOf(originalOrder: List<String>): DesktopQueue {
        val from = index + 1
        if (from >= songs.size) return this
        val upcoming = songs.drop(from).toMutableList()
        val restored = buildList {
            originalOrder.forEach { id ->
                val at = upcoming.indexOfFirst { it.videoId == id }
                if (at >= 0) add(upcoming.removeAt(at))
            }
            addAll(upcoming)
        }
        // Sections are preserved on the way back too.
        val (mix, own) = restored.partition { it.fromAutoplay }
        return copy(songs = songs.take(from) + own + mix)
    }

    companion object {
        /** Completed entries retained behind the current song. */
        const val MAX_HISTORY = 25

        /** Starts a new queue at the row the listener picked, dropping the rows above it. */
        fun startingAt(songs: List<Song>, startIndex: Int): DesktopQueue {
            if (songs.isEmpty()) return DesktopQueue()
            return DesktopQueue(songs.drop(startIndex.coerceIn(songs.indices)), index = 0)
        }

        /**
         * The order a queue goes in when it is started while shuffle is on: the track the listener
         * picked leads, the rest follow at random.
         */
        fun shuffledStartingAt(songs: List<Song>, startIndex: Int): DesktopQueue {
            if (songs.isEmpty()) return DesktopQueue()
            val at = startIndex.coerceIn(songs.indices)
            return DesktopQueue(listOf(songs[at]) + songs.filterIndexed { i, _ -> i != at }.shuffled())
        }

        /** One song played on its own is a queue of one, not an addition to the last one. */
        fun of(song: Song): DesktopQueue = DesktopQueue(listOf(song), index = 0)

        /** Restores a saved queue around the item that was current. */
        fun restored(songs: List<Song>, index: Int): DesktopQueue {
            if (songs.isEmpty()) return DesktopQueue()
            return DesktopQueue(songs, index.coerceIn(songs.indices)).trimmed()
        }

        internal fun historyTrimCount(currentIndex: Int): Int =
            (currentIndex - MAX_HISTORY).coerceAtLeast(0)

        /** The entries a forward jump passes over. */
        internal fun skippedByJump(currentIndex: Int, target: Int): IntRange? =
            if (target > currentIndex + 1) (currentIndex + 1) until target else null
    }
}
