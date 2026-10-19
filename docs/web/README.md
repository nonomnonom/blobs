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

The build writes a static website to `out/`, including a client-side search index at `/api/search/`. Preview serves the exported files. Deploy `out/` to a static host at a domain root, with directory-index routing and `404.html` as its error page. No production Next.js server, API keys, external fonts, or analytics are required. `next start` is not used for a static export.

The `postbuild` step normalizes Windows RSC segment filenames for [Next.js issue #85374](https://github.com/vercel/next.js/issues/85374). It leaves already-flat Linux/macOS exports unchanged and refuses to overwrite a conflicting file. Run `npm run build` rather than calling `next build` directly so this step is included.

Run commands from this directory. The website intentionally reuses assets and content elsewhere in the repository; copying only `docs/web` into another checkout omits those inputs.

`node_modules/`, `.next/`, `.source/`, and `out/` are generated and ignored. Commit `package-lock.json` with dependency changes. Use `npm run format` to apply the website’s Prettier configuration.

## License

Code and original documentation follow the repository’s Apache-2.0 license. Blobs artwork and branding remain excluded from that grant; see [NOTICE](../../NOTICE). Model and library metadata preserve the respective third-party identities and licenses.
