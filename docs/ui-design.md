# Blobs UI direction

## North star

Blobs is a quiet research desk that happens to fit in a pocket. The question,
the local evidence behind an answer, and the next useful action should be easy
to find without making the on-device model the visual subject.

Design dials: energy 1, rhythm 2, motion 1. Research asks for calm reading,
occasional visual breaks for decisions, and motion only when it confirms a
state change.

## Decisions

- Material 3 color roles, type roles, shapes, and tonal surfaces are the shared
  visual language. The warm paper palette and illustrated worlds remain part of
  Blobs' existing identity.
- Blue is reserved for choice, focus, and the one primary action in a context.
  It is not used as decoration.
- Cards group decisions that need context: a model, library, import, or saved
  source. Ordinary navigation stays as readable rows, so settings and history
  do not turn into a dashboard.
- The composer keeps model and source scope adjacent to the question. A focus
  outline is a state signal, not a permanent accent.
- Citations are compact numbered controls in the answer. On touch devices they
  open the saved passage directly; the expandable source group provides the
  surrounding metadata and passage preview when the reader needs it.
- Grounding is a chain, not a badge. A finished turn offers one expandable
  "How this answer was checked" line whose steps are only things the runtime
  recorded for that turn: the scope searched, passages found, elapsed time, how
  many passages carry a marker, the support claim the model made, and the
  structural verdict. The shape borrows the AI Elements chain-of-thought
  pattern; Blobs shows no model reasoning, because a single local completion
  produces none to show.
- A question uses an asymmetric bubble to distinguish the user's words from a
  model answer. World panels use one-sided lower corners only for the bounded
  illustrated scene, not as a generic card treatment.
- The empty chat is centered on wide screens, while a finished conversation
  keeps long-form answers at a comfortable reading measure.

## Copy discipline

Every screen answers one question first: what is happening right now. That answer
is a state line with a number in it ("42% · 120 MB of 300 MB", "Sourced · 3
cited", "4096-token context · 1.2 GB · runs here"), followed by at most one
control. Explanation does not sit on the first screen; it lives behind a single
disclosure primitive, `DetailDisclosure`, whose summary is the state line and
whose body is the sentences that used to be paragraphs.

- `TraceCollapsible` is a `DetailDisclosure` with a trace gutter, so a turn's
  research stage, evidence list, and grounding chain all expand the same way.
- A string revealed by a disclosure is named `*_detail`. The name is the
  contract: it keeps explanation out of the visible copy budget.
- The budget is enforced by `CopyDensityTest`, not by review: no shipped string
  over 25 words, headings at most 4 words, primary and icon labels at most 3
  words, and at most 8 visible words per control on every source file that
  renders a control.
- Labels are verbs and sentences are not: "Start chatting", not "Get started
  with your first question".

## Information hierarchy

1. **Chat** starts with one question field. Once a conversation exists, the
   user's question, the answer, its current state, and its evidence appear in
   that order. The model choice and source scope stay in the composer because
   they change the next answer, not the answer being read.
2. **Evidence** stays progressive. A numbered citation opens its passage;
   cited and related passages can then be expanded without forcing every
   answer to carry a source list.
3. **Library** separates saved collections from available downloads. A saved
   collection exposes browsing and answer inclusion before secondary options.
4. **Reader** gives the passage priority, keeps provenance after the text, and
   places the contextual action, use this library in chat, at the end.
5. **History and data** make continuation the primary action and reserve the
   error color plus confirmation dialog for removal.
6. **Settings** group appearance, phone storage, and product information so a
   configuration action never competes with research work.

## Journey states

- Opening data shows a clear loading or recovery state without clearing user
  content.
- Setup makes the model decision first; a library remains optional, because
  model-only chat is a valid offline path.
- Streaming shows the current local research stage and preserves partial text
  when stopped or interrupted.
- A retrieval miss offers library choice or wider search while retaining a
  usable answer path.
- Imports and downloads expose progress, pause, resume, discard, and an
  explanation that existing data remains safe after failure.
- Search, history, and library screens include empty states and a next action.

## Motion and accessibility

Motion is limited to short feedback and page changes, and respects the existing
reduced-motion preference. Every prominent action keeps at least a 48dp target.
The theme provides light and dark color schemes, and source, model, and
library state is communicated with text or selection in addition to color.

Failures use the Material error container rather than the normal informational
container. Streaming stages and notices are polite accessibility announcements,
so assistive technology hears a meaningful update without being interrupted by
every generated token. Text scaling is never reduced to force a screen to fit.

## Reference patterns

- Material 3 provides adaptive layout, semantic color, type, and shape roles.
- The AI Elements prompt-input pattern informed the composer grouping.
- The AI Elements inline-citation and sources patterns informed progressive
  evidence disclosure. Blobs uses a touch-first direct-reader interaction in
  place of hover.
- The AI Elements chat and message-scroller patterns inform the transcript in
  `ui/chat`: a shared step shape (`Trace`, `TraceLine`, `TraceCollapsible`) for
  anything a turn did, and a scroll policy that moves the transcript only when
  the reader asked to move. Blobs keeps the step shape to stages the runtime
  actually reports, so no step history is invented for a finished turn.
