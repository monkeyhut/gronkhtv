package org.streamarchiv.tv.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.tv.material3.Text

/**
 * Knopf neben dem Suchfeld: startet die Spracherkennung des Systems und gibt den erkannten Text weiter. Abbruch liefert
 * nichts. Fehlt eine Spracherkennung, gibt es nur einen Hinweis; ohne Ziel wirft launch, ein Vorab-Check bräuchte <queries>.
 */
@Composable
fun VoiceSearchButton(onResult: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let(onResult)
    }
    AppButton(
        onClick = {
            try {
                launcher.launch(
                    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "de-DE"),
                )
            } catch (e: ActivityNotFoundException) {
                Log.w("GronkhSearch", "Keine Spracherkennung installiert", e)
                Toast.makeText(context, "Keine Spracherkennung auf diesem Gerät", Toast.LENGTH_LONG).show()
            }
        },
        modifier = modifier,
    ) { Text("Sprechen") }
}
