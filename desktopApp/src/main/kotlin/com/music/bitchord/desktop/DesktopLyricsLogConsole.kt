package com.music.bitchord.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState

/**
 * Live view of [DesktopLyricsLog], shown under the lyrics panel.
 *
 * A null [maxHeight] lets the list fill what it is given; a value caps it, for the inline view.
 */
@Composable
internal fun DesktopLyricsLogConsole(modifier: Modifier = Modifier, maxHeight: Int? = null) {
    val entries by DesktopLyricsLog.entries.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(entries.size) {
        if (entries.isNotEmpty()) listState.animateScrollToItem(entries.lastIndex)
    }

    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.History,
                    contentDescription = null,
                    tint = Color(0xFFFFD54F),
                    modifier = Modifier.size(15.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    DesktopStrings["d_lyrics_logs", "Lyrics Logs"],
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White.copy(alpha = 0.85f),
                )
                Spacer(Modifier.width(6.dp))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.White.copy(alpha = 0.12f))
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                ) {
                    Text(
                        "${entries.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.65f),
                    )
                }
            }
            if (entries.isNotEmpty()) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { DesktopLyricsLog.clear() }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.Delete,
                        DesktopStrings["d_clear_logs", "Clear logs"],
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(13.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        DesktopStrings["clear", "Clear"],
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.5f),
                    )
                }
            }
        }

        if (entries.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(72.dp), contentAlignment = Alignment.Center) {
                Text(
                    DesktopStrings["d_no_logs_yet_play_a_song_to_see_api_calls_and_scraper_act", "No logs yet — play a song to see API calls and scraper activity."],
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.4f),
                )
            }
            return@Column
        }
        LazyColumn(
            state = listState,
            modifier = if (maxHeight != null) {
                Modifier.fillMaxWidth().heightIn(max = maxHeight.dp)
            } else {
                Modifier.fillMaxWidth()
            },
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(entries) { entry ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Text(
                        entry.formattedTime,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                        ),
                        color = Color.White.copy(alpha = 0.35f),
                        modifier = Modifier.padding(end = 6.dp),
                    )
                    Text(
                        "[${entry.tag}]",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = tagColour(entry.tag),
                        modifier = Modifier.padding(end = 6.dp),
                    )
                    Text(
                        entry.message,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.5.sp,
                            lineHeight = 14.sp,
                        ),
                        color = levelColour(entry.level),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

private fun tagColour(tag: String): Color = when (tag.lowercase()) {
    "genius" -> Color(0xFFFFEB3B)
    "lrclib" -> Color(0xFF00E5FF)
    "musixmatch" -> Color(0xFFFF5252)
    "kugou" -> Color(0xFF69F0AE)
    "repository" -> Color(0xFFE040FB)
    else -> Color(0xFFB0BEC5)
}

private fun levelColour(level: DesktopLyricsLog.Level): Color = when (level) {
    DesktopLyricsLog.Level.SUCCESS -> Color(0xFF69F0AE)
    DesktopLyricsLog.Level.WARN -> Color(0xFFFFD54F)
    DesktopLyricsLog.Level.ERROR -> Color(0xFFFF5252)
    DesktopLyricsLog.Level.INFO -> Color.White.copy(alpha = 0.80f)
}
