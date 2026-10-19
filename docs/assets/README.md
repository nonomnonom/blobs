# Repository artwork

`banner-light.png` and `banner-dark.png` are 1200 × 480 exports of `banner.html`. They reuse the Android app's Nunito and DM Sans fonts and the original illustrations recorded in [the website asset notes](../web/public/media/README.md).

To render again, serve a temporary directory containing only this HTML and its referenced fonts and illustrations. Capture a 1200 × 480 viewport after fonts and images finish loading. Set `document.documentElement.dataset.theme = 'dark'` for the dark export. Do not serve the full checkout: it can contain private local signing files.

Original Blobs artwork and branding remain excluded from the Apache-2.0 grant; see [NOTICE](../../NOTICE). Font licenses are recorded in [Android third-party notices](../../android/THIRD_PARTY_NOTICES.md).
