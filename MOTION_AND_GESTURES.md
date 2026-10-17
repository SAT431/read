# Motion and gesture contracts

The registry declares fade (180ms), expand (240ms), and focus (120ms). The current implementation wires surface entry to a 180ms opacity/5px translation and control hover feedback to a tokenized 180ms transition. Coordinated movement, shared-element motion and the other declared presets are extension points, not shipped animations. Reduced-motion disables animation and transitions globally.

Reader: native selection and scrolling take precedence over sentence focus. A nonempty selection suppresses the click-focus handler. Enter activates keyboard focus. Diagram: native overflow scroll/pan and explicit bounded 0.5–4x zoom controls. Quiz: button activation selects an answer, and submission locks choices. No cloud call occurs in these paths.

No custom pointer capture, long-press, drag/resize, pinch, stylus, waveform animation or swipe recognizer is installed. This intentionally leaves native scrolling and selection available until conflict-tested gestures are implemented. Attention protection currently rejects a delayed AI response when the source changed; a general writing/dragging interaction lock is still planned.
