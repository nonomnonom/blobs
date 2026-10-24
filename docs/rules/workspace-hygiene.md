# Workspace hygiene

Source, tests, configuration, and documentation belong with their module owner.
Large models, packs, secrets, ad-hoc reports, and build output are not source.
Store evaluation output under the `corpus/evaluation` owner or an output path
recorded by the command.

Do not create catch-all directories such as `tmp`, `scratch`, `backup`, or
`misc`. Name temporary files, checkpoints, generated files, and fixtures so
they cannot be mistaken for production input.

Do not delete a running process, data preparation job, checkpoint, partial
file, or report that may be needed for reproduction. Establish its owner,
process status, and recovery value before cleaning an artifact.

Within one change, remove documentation, tests, or helpers that were genuinely
replaced. Do not add a second status document for a decision already owned by
architecture, roadmap, or the evaluation system. Run the relevant formatting or
diff inspection before handoff.
