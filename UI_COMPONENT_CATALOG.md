# Component contracts

All components use paper surfaces, semantic color tokens, labelled controls and persistent activity IDs. Their React lifetimes are disposable; state belongs to the workspace. Unsupported content is rejected before creation. All support the registered fade entrance and reduced-motion override. User-controlled pinning protects against AI hide, suspension, presentation and priority changes.

| Registry ID | Purpose / appearance | Data and durable state | Interactions / gestures | Width / variants | Failure |
|---|---|---|---|---|---|
| reader | Source text, natural paragraph flow, focus margin | Source ID, scroll by page, notes, confirmed checkpoints | Tap / keyboard sentence focus, native text selection, scroll, notes, source inspection | 340 minimum, 720 preferred; surface, dominant | Missing page falls back to sample while imports load |
| quiz | Question with four answer controls and feedback | Source, questions, index, selected and submitted answers | Tap answer, submit, pause/review, resume | 320 minimum, 540 preferred; surface, dominant | Empty questions rejected |
| diagram | Original extracted image with source caption | Figure ID and zoom | Zoom controls, native overflow pan | 300 minimum, 520 preferred; surface, dominant | Non-figure anchors rejected |
| explanation | Mentor prose and source provenance | Text and source ID | Scroll, listen, return to source | 300 minimum, 420 preferred; surface, inline intent | Empty text rejected; speech failure has text fallback |
| table | Header and row comparison, horizontally scrollable | Headers, rows, source ID | Scroll, return to source | 320 minimum, 520 preferred; surface, inline intent | Ragged / empty tables rejected |

Inline and dominant presentation values are validated intent fields; renderer-specific inline insertion is not implemented yet. Focus controls currently implement expansion through visibility changes. Registry entries include discovery metadata; not all target accessibility/lifecycle declarations from the full specification have dedicated schema fields yet.

Supporting application controls: chapter/page library, upload form, original/reconstructed verification modal, activity capsule strip, voice controls, settings, workspace snapshots, revision history, registry explorer and command trace. These are local controls, not yet all individually AI-instantiable registry entries.

Accessibility: semantic buttons and inputs, keyboard sentence activation, descriptive labels, focus-visible outlines, feedback alerts, reduced-motion styles. Native selection remains intact. A complete modal focus trap, automated accessibility audit and screen-reader review remain required. Some compact toolbar controls still fall below the target 44px touch height and require refinement.
