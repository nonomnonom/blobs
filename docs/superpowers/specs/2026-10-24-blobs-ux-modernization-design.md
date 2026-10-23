# Blobs UX modernization — the app, not the manual

Date: 2026-10-24
Status: approved by owner delegation ("decide for me / lanjut")

## Problem

Measured on the shipped copy (329 string resources, 2,038 words):

| Surface | Words | Controls | Words/control |
|---|---:|---:|---:|
| InfoScreens | 399 | 6 | 66.5 |
| ImportScreen | 266 | 16 | 16.6 |
| TurnOutcome | 106 | 8 | 13.3 |
| SetupScreen | 156 | 17 | 9.2 |

Two resources are paragraph blobs (`help_body` 221 words, `privacy_body` 103
words). The UI explains the system before it shows the state. A user opening
Privacy reads a policy; a user importing a file reads an essay; a user choosing
a model reads a spec sheet. That is documentation chrome, not app chrome.

## The one rule

Every surface answers three questions, in this order:

1. **State line** — what is true right now. One line, at most 12 words, and a
   number whenever one exists (`3 sources · 2 cited`, `42% · 120 MB`).
2. **One primary control** — what happens next. At most 3 words
   (`Download`, `Continue`, `Keep answers`).
3. **Detail** — everything that explains *why*, behind a disclosure whose
   summary is at most 5 words.

Rules that follow from it:

- Copy longer than 25 words must live in a resource named `*_detail` and be
  rendered inside `DetailDisclosure`.
- No surface may exceed 8 words per control above the fold.
- Numbers carry the state. Sentences carry the explanation, never both at once.
- Accessibility labels are not exempt from length, but they are not visible
  copy; they still follow the 25-word ceiling.

This rule is the whole design. There is no second pattern.

## Primitive

`ui/DetailDisclosure.kt`:

```kotlin
@Composable
internal fun DetailDisclosure(
    open: Boolean,
    onOpenChange: (Boolean) -> Unit,
    summary: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
)
```

- Material 3 `Surface` row, minimum 56 dp, `stateDescription` = expanded /
  collapsed, chevron rotates 180° (down → up).
- Motion honours `LocalLessMotion` (snap + no size animation).
- `TraceCollapsible` becomes a thin wrapper over it, keeping its test tag, so
  chat traces and settings details share one implementation.
- The summary takes a `RowScope` because the trace line, the settings row, and
  the import state line all end with a number or a measure.

## Surfaces

### InfoScreens (Help, Privacy, About)

- Help: heading + one-line promise, then seven disclosure rows, each title at
  most 3 words and each body at most 25 words. `help_body` is removed.
- Privacy: a state line (`On this phone · no account · no cloud AI`), then
  three disclosures: what stays here, when it connects, backups and removal.
  `privacy_body` is removed.
- About: identity block (mark, tagline, version), then `Credits` and
  `Licenses` disclosures. `about_description` moves behind `*_detail`.

### ImportScreen

- State line computed from existing work state:
  `42% · 120 MB of 300 MB`, `Checking file`, `Saved and verified`,
  `Interrupted · files kept`, `Paused`.
- Title card keeps name, kind, size, progress.
- The explanatory `body` copy renders once, inside `Details`. The duplicate
  `Body(work.notice)` renderings collapse to the state line plus the detail.
- Secondary actions stay, but labels shrink to `Later` and `Cancel`.

### SetupScreen

- Step line stays (`Step 1 of 3`).
- Model step: heading at most 3 words, state line
  `4,096-token context · 1.2 GB · runs here`, one primary action.
  `setup_model_intro`, the device line, and the "you can change this later"
  hint move into `Why this model`.
- Library step: heading, state line `Fresh · 80 MB · optional`, one primary
  action, `What's inside` disclosure.
- Ready step: state line `Model and library ready`, one primary action.

### TurnOutcome and GroundingChain

- The outcome is already one `TraceLine`; keep it as the only visible line.
- `GroundingChain` becomes a summary line (`Sourced · 3 cited`) inside
  `DetailDisclosure`; the five steps and the verdict open on demand.
- No step claims reasoning the engine did not perform. Steps report retrieval,
  citations, and support only.

## Enforcement

`android/app/src/test/kotlin/dev/blobs/app/CopyDensityTest.kt` runs in the
existing JVM suite:

1. Every string resource is at most 25 words.
2. Every `Heading(R.string.x)` is at most 4 words.
3. Every `PrimaryAction(R.string.x)` / `GlyphButton(..., R.string.x)` is at
   most 3 words.
4. Words per control, excluding `*_detail` resources, is at most 8 for
   `InfoScreens.kt`, `ImportScreen.kt`, `SetupScreen.kt`, `TurnOutcome.kt`,
   and `GroundingChain.kt`.

The test reads the real resource and source files, so a regression fails
`./gradlew test` on the next change.

## Non-goals

- No new navigation, no new feature, no change to the retrieval or completion
  contract.
- No fabricated chain-of-thought; the chain stays a proof, not a monologue.
- No re-theming: Material 3 tokens and existing type scale stay.
- Corpus work in the working tree is untouched.

## Acceptance

- The four target surfaces meet the 8 words/control ceiling.
- `help_body` and `privacy_body` no longer exist.
- `./gradlew test`, `ktlintCheck`, `compileDebugKotlin`, `lintDebug`, and
  `assembleDebug` pass.
- Device acceptance remains blocked until an AVD exists; this spec does not
  claim runtime verification.
