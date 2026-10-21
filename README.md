# GronkhTV

Inoffizielle App für das öffentliche Stream-Archiv von [gronkh.tv](https://gronkh.tv) – für Android TV, Google TV und Android-Handys.

> **Inoffiziell.** Die App stammt nicht von Gronkh und nicht von gronkh.tv. Alle Videos, Bilder und Namen gehören ihren Rechteinhabern; die App zeigt nur, was gronkh.tv öffentlich bereitstellt. Keine Anmeldung, keine Werbung, keine Datensammlung.

## Herunterladen

Die fertige APK samt Anleitung liegt unter **[Releases](../../releases/latest)**: `GronkhTV-<Version>.zip` herunterladen, entpacken, `ANLEITUNG.txt` lesen.

Kurz: Auf dem Fernseher per USB-Stick und Dateimanager oder mit „Send Files to TV“ vom Handy installieren, auf dem Handy die APK antippen. Beim ersten Mal muss die Installation aus unbekannten Quellen erlaubt werden. Updates einfach drüber installieren.

## Funktionen

- Neueste Streams, Gerade beliebt, Alle Streams, Meistgesehen, Zufallsfund
- **Weiterschauen** an der gespeicherten Stelle und **Merkliste** (langes OK bzw. lange drücken)
- **Spiele und Formate:** Reihe mit Box-Arts, Spielseite mit allen Streams eines Spiels, Start direkt am Kapitelanfang; Reihen für HORDE, Friendly Fire, #FREiAB18
- **Höhepunkte:** aus dem Chat-Replay berechnet, als Markierungen auf der Zeitleiste; am Fernseher springt „hoch“ zum nächsten
- **Kapitel** im Player, Vorschaubilder beim Spulen, Einschlaf-Timer, nächster Teil automatisch
- Suche mit Sprachsuche, Hinweis, wenn Gronkh live geht (öffnet die Twitch-App)
- **Handy:** Touch-Bedienung, Player im Hoch- und Querformat, **Bild im Bild**
- Immer die beste Bildstufe (bis 1080p mit 60 Bildern pro Sekunde)
- Bildschirmschoner „GronkhTV Galerie“ und ein kleiner Monatsrückblick

Bedienung im Detail: siehe `ANLEITUNG.txt` im Release.

## Selbst bauen

Voraussetzungen: JDK 21, Android SDK (compileSdk 37).

```
./gradlew testDebugUnitTest assembleRelease
```

Die APK liegt danach unter `app/build/outputs/apk/release/`. Signiert wird mit dem lokalen Debug-Schlüssel; eine selbst gebaute APK lässt sich deshalb nicht über die Release-APK installieren (und umgekehrt), ohne die App vorher zu entfernen.

## Datenschutz

Alles, was die App sich merkt (Stellen, Merkliste, eigene Statistik), bleibt lokal auf dem Gerät. Abgerufen werden nur die öffentlichen Daten und Bilder von gronkh.tv sowie Box-Arts und Live-Vorschaubilder vom Twitch-CDN.
