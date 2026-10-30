# Documentation artwork

These WebP copies reuse original Blobs Android artwork without changing its design:

- `welcome.webp`: `android/app/src/main/res/drawable-nodpi/welcome_world.png`, resized to 1,000 pixels wide.
- `welcome-night.webp`: `android/app/src/main/res/drawable-nodpi/mascot_garden_night.png`, retained at its original width.
- `reading.webp`: `android/app/src/main/res/drawable-nodpi/mascot_reading.png`, resized to 700 pixels wide.

Converted with Sharp 0.35.5 to WebP at quality 88, effort 6, without enlarging inputs. Original artwork and branding are excluded from the repository’s Apache-2.0 source-code grant; see the root `NOTICE`.

## Demo film

- `blobs-demo.mp4`: scenes 0 to 6 of the storyboard v2 cut, rendered by the pipeline in the git-ignored `demo-video/` directory. H.264 High, 1920×1080, 60 fps, AAC 96 kb/s mono, CRF 20. The paper plates are generated clips; the phone screens are emulator captures of this app.

The file is published here so the film has a stable address on the documentation site. Replacing it at the same path keeps existing links working.
