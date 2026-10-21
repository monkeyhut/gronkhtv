package org.streamarchiv.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.streamarchiv.tv.data.WatchSummary
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/** Rückblick auf den laufenden Monat ganz unten auf der Startseite; dunkel und violett wie die Live-Karte. */
@Composable
fun MonthReviewCard(month: WatchSummary) {
    val name = YearMonth.now().month.getDisplayName(TextStyle.FULL_STANDALONE, Locale.GERMANY)
    // Ohne onClick: am Fernseher trotzdem fokussierbar, sonst scrollt das Steuerkreuz nie bis hierher; OK tut bewusst nichts.
    Tile(onClick = null, modifier = Modifier.padding(horizontal = ScreenPadding).fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.85f), MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))))
                .padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Dein $name", style = MaterialTheme.typography.headlineSmall, color = Color.White)
            Row(horizontalArrangement = Arrangement.spacedBy(56.dp)) {
                Figure(formatDuration(month.seconds), "geschaut")
                Figure("${month.videos.size}", if (month.videos.size == 1) "Stream" else "Streams")
            }
            month.videos.firstOrNull()?.title?.takeIf { it.isNotBlank() }?.let {
                Text(
                    "Am meisten: $it",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun Figure(value: String, label: String) = Column {
    Text(value, style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
    Text(label, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.6f))
}
