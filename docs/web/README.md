# Blobs documentation website

Next.js + Fumadocs, with English guides for users and contributors. Uses the app’s local fonts and original artwork. Model and library tables read `android/app/src/main/assets/package-catalog.json`; the architecture page includes `docs/architecture.md` at build time.

## Develop

Requires Node.js 24 and npm. From this directory in a full Blobs checkout:

```sh
npm ci
npm run dev
```

Open <http://localhost:3000>. Edit guides in `content/docs/`; `meta.json` sets navigation order. Shared layout options live in `lib/layout.shared.tsx`, and brand tokens in `app/global.css`.

## Check and export

```sh
npm run lint
npm run typecheck
npm test
npm run format:check
npm run build
npm run preview
```

The build writes a static website to `out/`, including a client-side search index at `/api/search` (an extensionless JSON file, without a trailing slash). Preview serves the exported files. Deploy `out/` to a static host with directory-index routing and `404.html` as its error page. No production Next.js server, API keys, external fonts, or analytics are required. `next start` is not used for a static export.

The [documentation workflow](../../.github/workflows/docs.yml) checks and publishes the site to <https://nonomnonom.github.io/blobs/> on changes to its inputs on `main`. Pull requests run the checks without deploying. GitHub Pages must use **GitHub Actions** as its publishing source. The workflow sets `NEXT_PUBLIC_BASE_PATH=/blobs` at build time; leave it unset for a domain-root deployment or local development. Changing this path requires rebuilding. When previewing a subpath export locally, serve `out/` at that same subpath.

The `postbuild` step normalizes Windows RSC segment filenames for [Next.js issue #85374](https://github.com/vercel/next.js/issues/85374). It leaves already-flat Linux/macOS exports unchanged and refuses to overwrite a conflicting file. Run `npm run build` rather than calling `next build` directly so this step is included.

Run commands from this directory. The website intentionally reuses assets and content elsewhere in the repository; copying only `docs/web` into another checkout omits those inputs.

The `/docs/tour` guide shows native Android screenshots with the Fumadocs image viewer. Capture versions and attribution are recorded in `public/screens/README.md`. Compressed copies of original app illustrations live in `public/media/`, with their source paths recorded alongside them.

To refresh the tour screens, build the debug app and test APKs, install both, place a model package and the starter library package where the app can read them, and run the manual capture:

```sh
adb shell am instrument -w -r \
  -e reviewModel /data/local/tmp/<model>.blobs \
  -e reviewLibrary /data/local/tmp/<library>.blobs \
  -e class dev.blobs.app.DesignCaptureTest \
  dev.blobs.app.debug.test/androidx.test.runner.AndroidJUnitRunner
```

Pull the `ui-review` folder from the app's external files directory, choose the ten site screens, and convert those PNGs to lossless WebP.

Mermaid code fences and `<Mermaid chart="…" title="…" />` render to SVG during the build through the Fumadocs remark plugin and Beautiful Mermaid. No diagram renderer or remote font request is needed in the browser. Diagrams use the same CSS colors and local body font as the rest of the site, respond to appearance changes, and keep a keyboard-focusable scrolling area for wide layouts. Invalid diagram syntax fails the build. Use the Mermaid flowchart, sequence, state, class, ER, or XY syntax supported by the pinned renderer.

`node_modules/`, `.next/`, `.source/`, and `out/` are generated and ignored. Commit `package-lock.json` with dependency changes. Use `npm run format` to apply the website’s Prettier configuration.

## License

Code and original documentation follow the repository’s Apache-2.0 license. Blobs artwork and branding remain excluded from that grant; see [NOTICE](../../NOTICE). Model and library metadata preserve the respective third-party identities and licenses.
