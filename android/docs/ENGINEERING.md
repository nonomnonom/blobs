# Engineering rules

Place behavior with the component that owns its invariant. Screens render, ViewModels coordinate user intent, repositories own persistence, the package installer owns package validation, and the native runtime owns model handles. Add a new abstraction only when it creates a real dependency or reuse boundary.

Use immutable UI state, lifecycle-aware flow collection, structured concurrency, and explicit cancellation. Preserve drafts and partial user work on failure. Reject late generation writes after cancellation or conversation replacement.

English UI strings belong in resources. Respect system font scale, insets, TalkBack semantics, touch targets, dark mode, and reduced motion. Production screens must not contain fixture answers, dead controls, or invented device compatibility ratings.

Treat document URIs as streams, not filesystem paths. Validate lengths, schemas, hashes, entry names, available storage, and compatibility before activation. Parameterize SQL. Treat model output and retrieved text as untrusted data and never execute generated markup, code, or commands.

Pin build and runtime dependencies. Review lock-file changes. Do not use dynamic versions, download executables at app runtime, place credentials in source, or depend on developer-specific paths.

Run `gradlew verify` before handoff. Run affected connected tests for persistence, lifecycle, import, or UI behavior. Run `tools/verify_apk.py` for native/dependency/packaging changes. Report the exact checks performed and any behavior that remains unmeasured.

Release signing belongs to a protected environment. Never publish a debug APK as a release, and never commit `.local/signing`, keystores, passwords, generated packages, or release directories.
