package org.streamarchiv.tv.ui

import android.content.res.Configuration
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.MaterialTheme

/** Gleichbedeutend mit UiModeManager.currentModeType == UI_MODE_TYPE_TELEVISION; die Konfiguration trägt denselben Wert. */
fun Configuration.isTelevision() = uiMode and Configuration.UI_MODE_TYPE_MASK == Configuration.UI_MODE_TYPE_TELEVISION

/**
 * Handy, Tablet oder Chromebook statt Fernseher. Dieselben Bildschirme, nur die Klickflächen und Maße wechseln:
 * tv-material reagiert nicht auf Berührung (tvClickable kennt nur OK am Steuerkreuz und die Bedienungshilfe).
 */
val TouchUi: Boolean @Composable @ReadOnlyComposable get() = !LocalConfiguration.current.isTelevision()

private val TileShape = RoundedCornerShape(8.dp)

/**
 * Antippbare Fläche einer Karte. Am Fernseher die tv-Card mit Fokusrahmen und Zoom; auf dem Handy Tippen, langes
 * Drücken für [onLongClick] und ein kurzes Einsinken als Rückmeldung. Ohne [onClick] ist sie am Fernseher nur
 * fokussierbar (damit das Steuerkreuz bis dahin scrollt) und auf dem Handy ein ruhiges Feld.
 */
@Composable
fun Tile(onClick: (() -> Unit)?, modifier: Modifier = Modifier, onLongClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    if (!TouchUi) return Card(onClick = onClick ?: {}, onLongClick = onLongClick, modifier = modifier) { content() }
    val interaction = remember { MutableInteractionSource() }
    val scale by animateFloatAsState(if (interaction.collectIsPressedAsState().value) 0.96f else 1f, label = "tile")
    val tap = if (onClick == null) Modifier else Modifier.combinedClickable(
        interactionSource = interaction,
        indication = LocalIndication.current,
        onLongClick = onLongClick,
        onClick = onClick,
    )
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(TileShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(tap),
    ) { content() }
}

/** Knopf mit Text: am Fernseher der tv-Button, auf dem Handy eine Pille in Fingergröße (48 dp hoch). */
@Composable
fun AppButton(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    if (!TouchUi) return Button(onClick = onClick, modifier = modifier) { content() }
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** Im Raster füllt eine Karte auf dem Handy ihre Spalte; am Fernseher bleibt sie so breit wie in den Reihen. */
@Composable
fun Modifier.gridCell(): Modifier = if (TouchUi) fillMaxWidth() else this
