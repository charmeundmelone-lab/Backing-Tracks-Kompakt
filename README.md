# Backing-Tracks Kompakt

Backing-Track-Player für das Mudita Kompakt (E-Ink, Android 12, 480×800).
Nachfolger der App [Backing-Tracks](https://github.com/charmeundmelone-lab/Backing-Tracks),
deren bewährtes Verhalten (Gigs, Sets, Show-Automatik, Lyrics) übernommen wird. Die Oberfläche
ist neu und für E-Ink gebaut: nur Schwarz und Weiß, keine Animationen, Seiten statt Scrollen.

Stand: Planung und Entwürfe fertig, App-Code folgt. Details in [PLAN.md](PLAN.md).

## Ordner

- `PLAN.md` – Entscheidungen, Gerätefakten, Ton-Test, Design, Bedienkonzept
- `entwuerfe/canvas/` – Entwurfs-Bildschirme (Quellen des Design-Canvas)
- `entwuerfe/betrachter/` – Mini-App "Entwürfe", zeigt die Entwürfe pixelgenau auf dem Kompakt
- `entwuerfe/tontest/` – Mini-App für den Stereo-Ton-Test (links Band, rechts Click)
- `entwuerfe/werkzeug/` – `bau.sh` (Bau ohne Gradle), `expand.py` (Vorschau-Bilder)
