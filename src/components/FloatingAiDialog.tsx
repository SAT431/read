import React, { useEffect, useRef, useState } from "react";
import {
  Compass,
  FileText,
  Highlighter,
  MessageSquareText,
  Mic,
  MicOff,
  Minimize2,
  Send,
  Sparkles,
  Table2,
  Trash2,
  Undo2,
  Volume2,
  VolumeX,
  X,
} from "lucide-react";
import {
  DoubtMarkContext,
  getGroqKey,
  transcribeAudioWithGroq,
} from "../services/aiHarness";

export type PopupQuadrant =
  | "bottom-right"
  | "top-right"
  | "bottom-left"
  | "top-left";

type RadialActionId = "mic" | "doubt" | "table" | "dialogue" | "pdf_or_undo";

interface RadialItem {
  id: RadialActionId;
  label: string;
  shortLabel: string;
  dx: number;
  dy: number;
  color: string;
}

interface FloatingAiDialogProps {
  visible: boolean;
  aiMessage: string;
  busy: boolean;
  suggestedFollowUps: string[];
  avoidYRatio?: number | null;
  doubtMarkerMode: boolean;
  activeDoubtMark: DoubtMarkContext | null;
  canUndo: boolean;
  onSendPrompt: (text: string) => void;
  onToggleDoubtMarker: () => void;
  onClearDoubtMark: () => void;
  onUndoLastAction: () => void;
  onOpenNativePdf?: () => void;
}

type SpeechRecInstance = {
  start: () => void;
  stop: () => void;
  interimResults: boolean;
  lang: string;
  continuous: boolean;
  onresult: (e: {
    results: ArrayLike<ArrayLike<{ transcript: string }>>;
  }) => void;
  onend: () => void;
  onerror: () => void;
};

const WORKING_STATUS_STEPS = [
  "Transcribing & parsing your prompt…",
  "Scanning verbatim NCERT source lines…",
  "Engineering structured study cards…",
  "Updating full-screen Study Engine…",
];

function renderInlineRich(text: string): React.ReactNode {
  const parts = text.split(/(\*\*[^*]+\*\*|`[^`]+`)/g);
  return parts.map((part, idx) => {
    if (part.startsWith("**") && part.endsWith("**")) {
      return <strong key={idx}>{part.slice(2, -2)}</strong>;
    }
    if (part.startsWith("`") && part.endsWith("`")) {
      return (
        <code key={idx} className="ai-chat-inline-formula">
          {part.slice(1, -1)}
        </code>
      );
    }
    return <React.Fragment key={idx}>{part}</React.Fragment>;
  });
}

/**
 * Renders structured chat replies including Markdown Tables, Formula / Equation boxes,
 * Bullet lists, and Bold NCERT terms inside the Floating AI Dialogue.
 */
function renderStructuredAiReply(text: string): React.ReactNode {
  if (!text) return null;
  const lines = text.split("\n");
  const blocks: React.ReactNode[] = [];
  let i = 0;

  while (i < lines.length) {
    const rawLine = lines[i].trim();
    if (!rawLine) {
      i++;
      continue;
    }

    // 1. Detect Markdown Table block (lines starting & ending with |)
    if (rawLine.startsWith("|") && rawLine.endsWith("|")) {
      const tableLines: string[] = [];
      while (
        i < lines.length &&
        lines[i].trim().startsWith("|") &&
        lines[i].trim().endsWith("|")
      ) {
        tableLines.push(lines[i].trim());
        i++;
      }
      const parsedRows = tableLines
        .filter((tl) => !/^\|[\s:-]+\|$/.test(tl.replace(/\|/g, "|")))
        .filter((tl) => !tl.split("|").slice(1, -1).every((c) => /^[\s:-]+$/.test(c)))
        .map((tl) =>
          tl
            .split("|")
            .slice(1, -1)
            .map((cell) => cell.trim())
        );

      if (parsedRows.length > 0) {
        const headers = parsedRows[0];
        const bodyRows = parsedRows.slice(1);
        blocks.push(
          <div key={`tbl-${i}`} className="ai-chat-table-wrap">
            <table className="ai-chat-table">
              <thead>
                <tr>
                  {headers.map((h, hIdx) => (
                    <th key={hIdx}>{renderInlineRich(h)}</th>
                  ))}
                </tr>
              </thead>
              {bodyRows.length > 0 && (
                <tbody>
                  {bodyRows.map((r, rIdx) => (
                    <tr key={rIdx}>
                      {r.map((c, cIdx) => (
                        <td key={cIdx}>{renderInlineRich(c)}</td>
                      ))}
                    </tr>
                  ))}
                </tbody>
              )}
            </table>
          </div>
        );
      }
      continue;
    }

    // 2. Detect Formula / Equation block ("Formula: ...", "Equation: ...", "$$...$$")
    if (
      /^(?:Formula|Equation|Array Formula)\s*:/i.test(rawLine) ||
      (rawLine.startsWith("$$") && rawLine.endsWith("$$"))
    ) {
      const formulaText = rawLine
        .replace(/^\$\$|\$\$/g, "")
        .replace(/^(?:Formula|Equation)\s*:\s*/i, "")
        .trim();
      blocks.push(
        <div key={`eq-${i}`} className="ai-chat-formula-box">
          <span className="ai-chat-formula-badge">FORMULA</span>
          <span className="ai-chat-formula-expr">
            {renderInlineRich(formulaText)}
          </span>
        </div>
      );
      i++;
      continue;
    }

    // 3. Detect Bullet List block (- ... or • ...)
    if (/^(?:[-•*])\s+/.test(rawLine)) {
      const items: string[] = [];
      while (i < lines.length && /^(?:[-•*])\s+/.test(lines[i].trim())) {
        items.push(lines[i].trim().replace(/^(?:[-•*])\s+/, ""));
        i++;
      }
      blocks.push(
        <ul key={`ul-${i}`} className="ai-chat-bullet-list">
          {items.map((item, idx) => (
            <li key={idx}>{renderInlineRich(item)}</li>
          ))}
        </ul>
      );
      continue;
    }

    // 4. Standard paragraph
    blocks.push(
      <p key={`p-${i}`} className="ai-practical-reply">
        {renderInlineRich(rawLine)}
      </p>
    );
    i++;
  }

  return <div className="ai-structured-reply-stack">{blocks}</div>;
}

export const FloatingAiDialog: React.FC<FloatingAiDialogProps> = ({
  visible,
  aiMessage,
  busy,
  suggestedFollowUps,
  avoidYRatio,
  doubtMarkerMode,
  activeDoubtMark,
  canUndo,
  onSendPrompt,
  onToggleDoubtMarker,
  onClearDoubtMark,
  onUndoLastAction,
  onOpenNativePdf,
}) => {
  const [dialogueOpen, setDialogueOpen] = useState(false);
  const [quadrant, setQuadrant] = useState<PopupQuadrant>("bottom-right");
  const [input, setInput] = useState("");
  const [listening, setListening] = useState(false);
  const [transcribing, setTranscribing] = useState(false);
  const [liveTranscript, setLiveTranscript] = useState("");
  const [ttsEnabled, setTtsEnabled] = useState(false);
  const [statusStepIndex, setStatusStepIndex] = useState(0);

  // Long-press & slide radial menu state
  const [radialOpen, setRadialOpen] = useState(false);
  const [hoveredRadialId, setHoveredRadialId] =
    useState<RadialActionId | null>(null);

  const longPressTimerRef = useRef<number | null>(null);
  const pointerDownTimeRef = useRef<number>(0);
  const pointerDownPosRef = useRef<{ x: number; y: number }>({ x: 0, y: 0 });
  const isHoldingOrbRef = useRef(false);
  const orbCenterRef = useRef<{ x: number; y: number }>({ x: 0, y: 0 });
  const recognitionRef = useRef<SpeechRecInstance | null>(null);
  const mediaRecorderRef = useRef<MediaRecorder | null>(null);
  const audioChunksRef = useRef<Blob[]>([]);
  const latestTranscriptRef = useRef("");
  const initialMountRef = useRef(true);

  const isRight = quadrant.endsWith("right");
  const isBottom = quadrant.startsWith("bottom");
  const xSign = isRight ? -1 : 1;
  const ySign = isBottom ? -1 : 1;

  const radialItems: RadialItem[] = [
    {
      id: "mic",
      label: "Speak Voice (Live STT)",
      shortLabel: "Speak",
      dx: xSign * 14,
      dy: ySign * 108,
      color: "#7c3aed",
    },
    {
      id: "doubt",
      label: doubtMarkerMode ? "Exit Doubt Marker" : "I Have a Doubt (Marker)",
      shortLabel: "Doubt",
      dx: xSign * 62,
      dy: ySign * 94,
      color: "#d97706",
    },
    {
      id: "table",
      label: "Make Comparison Table",
      shortLabel: "Table",
      dx: xSign * 96,
      dy: ySign * 60,
      color: "#0d9488",
    },
    {
      id: "dialogue",
      label: dialogueOpen ? "Hide AI Dialogue" : "Open AI Dialogue Box",
      shortLabel: "AI Chat",
      dx: xSign * 108,
      dy: ySign * 12,
      color: "#2563eb",
    },
    {
      id: "pdf_or_undo",
      label: canUndo ? "Undo Last AI Block" : "Open Native PDF View",
      shortLabel: canUndo ? "Undo" : "PDF",
      dx: xSign * 56,
      dy: ySign * 46,
      color: "#e11d48",
    },
  ];

  // Only reposition dialogue if it is currently open and blocking a marked doubt
  useEffect(() => {
    if (avoidYRatio == null || !dialogueOpen) return;
    if (avoidYRatio > 0.58) {
      setQuadrant((prev) => (prev.startsWith("bottom") ? "top-right" : prev));
    } else if (avoidYRatio < 0.38) {
      setQuadrant((prev) => (prev.startsWith("top") ? "bottom-right" : prev));
    }
  }, [avoidYRatio, dialogueOpen]);

  useEffect(() => {
    if (!busy && !transcribing) {
      setStatusStepIndex(0);
      return;
    }
    const interval = window.setInterval(() => {
      setStatusStepIndex((prev) => (prev + 1) % WORKING_STATUS_STEPS.length);
    }, 850);
    return () => clearInterval(interval);
  }, [busy, transcribing]);

  useEffect(() => {
    if (initialMountRef.current) {
      initialMountRef.current = false;
      return;
    }
    if (aiMessage && !busy) {
      setDialogueOpen(true);
      if (
        ttsEnabled &&
        typeof window !== "undefined" &&
        window.speechSynthesis
      ) {
        window.speechSynthesis.cancel();
        const utter = new SpeechSynthesisUtterance(
          aiMessage.replace(/\*\*/g, "").replace(/\|/g, " ")
        );
        utter.rate = 0.98;
        window.speechSynthesis.speak(utter);
      }
    }
  }, [aiMessage, busy, ttsEnabled]);

  const cycleQuadrant = () => {
    const order: PopupQuadrant[] = [
      "bottom-right",
      "top-right",
      "top-left",
      "bottom-left",
    ];
    setQuadrant(order[(order.indexOf(quadrant) + 1) % order.length]);
  };

  const stopVoiceCaptureAndMinimize = (sendSpoken = true) => {
    recognitionRef.current?.stop();
    if (
      mediaRecorderRef.current &&
      mediaRecorderRef.current.state !== "inactive"
    ) {
      mediaRecorderRef.current.stop();
    }
    setListening(false);
    const spoken = latestTranscriptRef.current.trim();
    if (sendSpoken && spoken) {
      latestTranscriptRef.current = "";
      window.setTimeout(() => {
        setLiveTranscript("");
        onSendPrompt(spoken);
      }, 220);
    } else {
      setLiveTranscript("");
    }
  };

  const startVoiceCaptureAndExpand = async () => {
    if (listening) {
      stopVoiceCaptureAndMinimize(true);
      return;
    }

    setRadialOpen(false);
    setLiveTranscript("");
    latestTranscriptRef.current = "";
    setListening(true);

    const groqKey = getGroqKey();
    const w = window as unknown as {
      SpeechRecognition?: new () => SpeechRecInstance;
      webkitSpeechRecognition?: new () => SpeechRecInstance;
    };
    const SpeechConstructor =
      w.SpeechRecognition || w.webkitSpeechRecognition;

    if (SpeechConstructor) {
      const rec = new SpeechConstructor();
      recognitionRef.current = rec;
      rec.interimResults = true;
      rec.continuous = false;
      rec.lang = "en-IN";
      rec.onresult = (event) => {
        const text = Array.from(event.results)
          .map((r) => r[0].transcript)
          .join(" ")
          .trim();
        latestTranscriptRef.current = text;
        setLiveTranscript(text);
      };
      rec.onend = () => {
        setListening(false);
        if (latestTranscriptRef.current.trim()) {
          const finalWords = latestTranscriptRef.current.trim();
          latestTranscriptRef.current = "";
          window.setTimeout(() => {
            setLiveTranscript("");
            onSendPrompt(finalWords);
          }, 260);
        }
      };
      rec.onerror = () => {
        // Keep capsule open for quick voice chip or typing
      };
      try {
        rec.start();
        return;
      } catch {
        // Fall through
      }
    }

    if (groqKey && navigator.mediaDevices?.getUserMedia) {
      try {
        const stream = await navigator.mediaDevices.getUserMedia({
          audio: true,
        });
        const recorder = new MediaRecorder(stream);
        audioChunksRef.current = [];
        mediaRecorderRef.current = recorder;
        recorder.ondataavailable = (ev) => {
          if (ev.data.size > 0) audioChunksRef.current.push(ev.data);
        };
        recorder.onstop = async () => {
          stream.getTracks().forEach((t) => t.stop());
          const blob = new Blob(audioChunksRef.current, { type: "audio/webm" });
          if (blob.size > 0) {
            setTranscribing(true);
            try {
              const text = await transcribeAudioWithGroq(blob, groqKey);
              if (text) {
                setLiveTranscript(text);
                window.setTimeout(() => {
                  setLiveTranscript("");
                  setTranscribing(false);
                  onSendPrompt(text);
                }, 360);
                return;
              }
            } catch {
              // ignore
            }
            setTranscribing(false);
          }
        };
        recorder.start();
      } catch {
        // Keep listening capsule open
      }
    }
  };

  const executeRadialAction = (actionId: RadialActionId) => {
    setRadialOpen(false);
    setHoveredRadialId(null);
    if (actionId === "mic") {
      startVoiceCaptureAndExpand();
    } else if (actionId === "doubt") {
      onToggleDoubtMarker();
    } else if (actionId === "table") {
      onSendPrompt("Make a comparison table for this section");
    } else if (actionId === "dialogue") {
      setDialogueOpen((prev) => !prev);
    } else if (actionId === "pdf_or_undo") {
      if (canUndo) {
        onUndoLastAction();
      } else if (onOpenNativePdf) {
        onOpenNativePdf();
      }
    }
  };

  const handleOrbPointerDown = (e: React.PointerEvent<HTMLButtonElement>) => {
    e.preventDefault();
    const rect = e.currentTarget.getBoundingClientRect();
    orbCenterRef.current = {
      x: rect.left + rect.width / 2,
      y: rect.top + rect.height / 2,
    };
    pointerDownPosRef.current = { x: e.clientX, y: e.clientY };
    pointerDownTimeRef.current = Date.now();
    isHoldingOrbRef.current = true;
    setHoveredRadialId(null);

    try {
      e.currentTarget.setPointerCapture(e.pointerId);
    } catch {}

    if (longPressTimerRef.current) {
      window.clearTimeout(longPressTimerRef.current);
    }
    longPressTimerRef.current = window.setTimeout(() => {
      if (isHoldingOrbRef.current) {
        setRadialOpen(true);
      }
    }, 240);
  };

  const handleOrbPointerMove = (e: React.PointerEvent<HTMLButtonElement>) => {
    if (!isHoldingOrbRef.current) return;
    const moveDist = Math.hypot(
      e.clientX - pointerDownPosRef.current.x,
      e.clientY - pointerDownPosRef.current.y
    );
    // If user begins sliding outward (> 16px) even slightly before 240ms, smoothly open radial menu
    if (!radialOpen && moveDist > 16) {
      if (longPressTimerRef.current) {
        window.clearTimeout(longPressTimerRef.current);
        longPressTimerRef.current = null;
      }
      setRadialOpen(true);
    }

    if (!radialOpen && moveDist <= 16) return;

    const relX = e.clientX - orbCenterRef.current.x;
    const relY = e.clientY - orbCenterRef.current.y;

    let closest: RadialActionId | null = null;
    let minDist = 46;
    for (const item of radialItems) {
      const dist = Math.hypot(relX - item.dx, relY - item.dy);
      if (dist < minDist) {
        minDist = dist;
        closest = item.id;
      }
    }
    if (closest !== hoveredRadialId) {
      setHoveredRadialId(closest);
    }
  };

  const handleOrbPointerUp = (e: React.PointerEvent<HTMLButtonElement>) => {
    if (longPressTimerRef.current) {
      window.clearTimeout(longPressTimerRef.current);
      longPressTimerRef.current = null;
    }
    try {
      e.currentTarget.releasePointerCapture(e.pointerId);
    } catch {}

    const elapsed = Date.now() - pointerDownTimeRef.current;
    const wasRadialOpen = radialOpen;
    const targetAction = hoveredRadialId;
    isHoldingOrbRef.current = false;

    if (wasRadialOpen) {
      if (targetAction) {
        executeRadialAction(targetAction);
      }
      return;
    }

    if (elapsed < 240) {
      startVoiceCaptureAndExpand();
    }
  };

  const handleSubmitText = (e: React.FormEvent) => {
    e.preventDefault();
    if (!input.trim() || busy) return;
    const text = input.trim();
    setInput("");
    onSendPrompt(text);
  };

  if (!visible) return null;

  // Keep the bottom orb strictly anchored at bottom-right so it never jumps across corners
  const orbAnchorStyle: React.CSSProperties = {
    right: "18px",
    bottom: "18px",
  };

  const activeHoveredItem = radialItems.find((i) => i.id === hoveredRadialId);

  return (
    <>
      {/* 1. PRACTICAL, HIGH-VISIBILITY FLOATING AI RESPONDING DIALOGUE CARD WITH TABLES & FORMULAS */}
      {dialogueOpen && (
        <aside
          style={
            quadrant.startsWith("bottom")
              ? {
                  right: isRight ? "18px" : undefined,
                  left: !isRight ? "18px" : undefined,
                  bottom: "82px",
                }
              : {
                  right: isRight ? "18px" : undefined,
                  left: !isRight ? "18px" : undefined,
                  top: "18px",
                }
          }
          className={`ai-practical-dialogue ${
            doubtMarkerMode ? "is-doubt-mode" : ""
          }`}
          data-testid="ai-floating-dialog"
          aria-label="Folio AI Responding Dialogue"
        >
          <div className="ai-practical-header">
            <div className="ai-practical-brand">
              <span
                className={`ai-orb-pulse ${busy || listening ? "active" : ""}`}
              />
              <strong>Folio AI Mentor</strong>
              <span className="ai-nonblocking-tag">Tables &amp; Formulas</span>
            </div>
            <div className="ai-practical-actions">
              <button
                type="button"
                className="ai-icon-mini-btn"
                onClick={() => setTtsEnabled((v) => !v)}
                title={ttsEnabled ? "Mute Voice" : "Read AI Reply Aloud"}
              >
                {ttsEnabled ? <Volume2 size={14} /> : <VolumeX size={14} />}
              </button>
              <button
                type="button"
                className="ai-icon-mini-btn"
                onClick={cycleQuadrant}
                title="Move dialogue to another open corner"
              >
                <Compass size={14} />
              </button>
              <button
                type="button"
                className="ai-icon-mini-btn"
                onClick={() => setDialogueOpen(false)}
                title="Minimize back to single AI button"
              >
                <Minimize2 size={14} />
              </button>
            </div>
          </div>

          {(doubtMarkerMode || activeDoubtMark) && (
            <div className="ai-doubt-status-strip">
              <Highlighter size={13} />
              <div className="ai-doubt-status-text">
                {activeDoubtMark ? (
                  <span>
                    <strong>Marked:</strong> "
                    {activeDoubtMark.markedText.slice(0, 60)}
                    {activeDoubtMark.markedText.length > 60 ? "…" : ""}"
                  </span>
                ) : (
                  <span>
                    <strong>Marker Active:</strong> Tap any sentence/card on
                    screen to mark your doubt!
                  </span>
                )}
              </div>
              <button
                type="button"
                className="ai-doubt-clear-btn"
                onClick={onClearDoubtMark}
              >
                Done
              </button>
            </div>
          )}

          {/* Structured AI Response Body (Supports Tables, Formulas, Bullets, and Bold Terms) */}
          <div className="ai-practical-body">
            {busy ? (
              <div className="ai-working-inline">
                <span className="ai-spinner" />
                <div>
                  <div className="ai-working-step-title">
                    {WORKING_STATUS_STEPS[statusStepIndex]}
                  </div>
                  <div className="ai-working-progress-bar">
                    <span
                      style={{ width: `${((statusStepIndex + 1) / 4) * 100}%` }}
                    />
                  </div>
                </div>
              </div>
            ) : (
              renderStructuredAiReply(aiMessage)
            )}
          </div>

          {/* Compact Action Chips */}
          <div className="ai-quick-chips">
            <button
              type="button"
              className={`ai-chip doubt-chip ${
                doubtMarkerMode ? "active" : ""
              }`}
              onClick={onToggleDoubtMarker}
              data-testid="doubt-marker-trigger"
            >
              <Highlighter size={12} />
              <span>{doubtMarkerMode ? "Marker Active" : "I have a doubt"}</span>
            </button>
            {suggestedFollowUps.slice(0, 2).map((chip, idx) => (
              <button
                key={idx}
                type="button"
                className="ai-chip"
                onClick={() => onSendPrompt(chip)}
              >
                {chip.toLowerCase().includes("table") ||
                chip.toLowerCase().includes("compare") ? (
                  <Table2 size={12} />
                ) : chip.toLowerCase().includes("delete") ? (
                  <Trash2 size={12} />
                ) : (
                  <Sparkles size={12} />
                )}
                <span>{chip}</span>
              </button>
            ))}
            {canUndo && (
              <button
                type="button"
                className="ai-chip undo-chip"
                onClick={onUndoLastAction}
              >
                <Undo2 size={12} />
                <span>Undo</span>
              </button>
            )}
          </div>

          {/* Compact Prompt Input */}
          <form className="ai-dialog-composer" onSubmit={handleSubmitText}>
            <button
              type="button"
              className={`ai-mic-button ${listening ? "listening" : ""}`}
              onClick={startVoiceCaptureAndExpand}
              title="Speak with Live Voice Transcription"
            >
              {listening ? <MicOff size={15} /> : <Mic size={15} />}
            </button>
            <input
              type="text"
              className="ai-composer-input"
              placeholder={
                activeDoubtMark
                  ? "Ask about your marked doubt…"
                  : "Ask AI for a table, formula, or doubt…"
              }
              value={input}
              onChange={(e) => setInput(e.target.value)}
              data-testid="ai-prompt-input"
            />
            <button
              type="submit"
              className="ai-send-button"
              disabled={!input.trim() || busy}
              data-testid="ai-send-button"
            >
              <Send size={14} />
            </button>
          </form>
        </aside>
      )}

      {/* 2. SMOOTH EXPANDING LIVE VOICE TRANSCRIPTION CAPSULE (Zero layout shift) */}
      <div
        style={{
          right: "80px",
          bottom: "16px",
          opacity: listening ? 1 : 0,
          transform: listening
            ? "translate3d(0, 0, 0) scale(1)"
            : "translate3d(14px, 6px, 0) scale(0.94)",
          pointerEvents: listening ? "auto" : "none",
        }}
        className="ai-expanding-transcript-capsule"
        data-testid={listening ? "ai-live-transcript-capsule" : undefined}
        aria-hidden={!listening}
      >
        <div className="ai-transcript-top">
          <div className="ai-waveform">
            <span />
            <span />
            <span />
            <span />
            <span />
          </div>
          <span className="ai-transcript-status">
            Listening · Live Voice Transcription
          </span>
          <button
            type="button"
            className="ai-transcript-close"
            onClick={() => stopVoiceCaptureAndMinimize(false)}
            title="Cancel voice"
          >
            <X size={13} />
          </button>
        </div>

        <div className="ai-transcript-live-text">
          <input
            type="text"
            value={liveTranscript}
            onChange={(e) => {
              latestTranscriptRef.current = e.target.value;
              setLiveTranscript(e.target.value);
            }}
            onKeyDown={(e) => {
              if (e.key === "Enter" && liveTranscript.trim()) {
                stopVoiceCaptureAndMinimize(true);
              }
            }}
            placeholder="Speak now (e.g., 'Study Centrosome', 'Compare 9+0 vs 9+2 in a table')…"
          />
          <button
            type="button"
            className="ai-transcript-send-pill"
            onClick={() => {
              if (!latestTranscriptRef.current.trim()) {
                latestTranscriptRef.current =
                  "Compare Centriole 9+0 vs Cilia 9+2 in a table";
              }
              stopVoiceCaptureAndMinimize(true);
            }}
          >
            Done
          </button>
        </div>

        <div className="ai-transcript-quick-voice">
          {[
            "Study Centrosome and Centrioles",
            "Compare 9+0 vs 9+2 in a table",
            "I have a doubt",
            "Quiz me on this",
          ].map((phrase, idx) => (
            <button
              key={idx}
              type="button"
              className="ai-voice-sim-chip"
              onClick={() => {
                latestTranscriptRef.current = phrase;
                setLiveTranscript(phrase);
                window.setTimeout(() => {
                  setListening(false);
                  setLiveTranscript("");
                  onSendPrompt(phrase);
                }, 260);
              }}
            >
              🎤 "{phrase}"
            </button>
          ))}
        </div>
      </div>

      {/* 3. SMOOTH ANIMATED WORKING STATUS PILL */}
      {(busy || transcribing) && !listening && !dialogueOpen && (
        <div
          style={{
            right: "80px",
            bottom: "20px",
          }}
          className="ai-live-status-pill"
          data-testid="ai-working-status-pill"
        >
          <span className="ai-spinner" />
          <div className="ai-status-pill-content">
            <span className="ai-status-step-label">
              {transcribing
                ? "Groq Whisper STT transcribing voice…"
                : WORKING_STATUS_STEPS[statusStepIndex]}
            </span>
            <div className="ai-status-mini-bar">
              <span
                style={{
                  width: `${
                    transcribing ? 40 : ((statusStepIndex + 1) / 4) * 100
                  }%`,
                }}
              />
            </div>
          </div>
        </div>
      )}

      {/* 4. SINGLE MINIMALIST AI BUTTON WITH SMOOTH GPU-ACCELERATED RADIAL MENU (Zero jumping) */}
      <div
        style={orbAnchorStyle}
        className={`ai-single-orb-container ${radialOpen ? "radial-open" : ""}`}
        data-testid="ai-single-orb-root"
      >
        {radialOpen && (
          <div
            className="ai-radial-slide-hint"
            style={{
              right: "0px",
              bottom: "152px",
            }}
          >
            {activeHoveredItem ? (
              <span>
                Release: <strong>{activeHoveredItem.label}</strong>
              </span>
            ) : (
              <span>Slide to an icon &amp; release</span>
            )}
          </div>
        )}

        {/* Always-mounted GPU-transformed Radial Icons so opening/closing never jumps */}
        {radialItems.map((item) => {
          const isHovered = radialOpen && hoveredRadialId === item.id;
          return (
            <button
              key={item.id}
              type="button"
              tabIndex={radialOpen ? 0 : -1}
              onClick={() => radialOpen && executeRadialAction(item.id)}
              className={`ai-radial-icon-btn ${isHovered ? "hovered" : ""}`}
              style={{
                opacity: radialOpen ? 1 : 0,
                pointerEvents: radialOpen ? "auto" : "none",
                transform: radialOpen
                  ? `translate3d(${item.dx}px, ${item.dy}px, 0) scale(${
                      isHovered ? 1.2 : 1
                    })`
                  : "translate3d(0px, 0px, 0) scale(0.35)",
                borderColor: isHovered ? item.color : undefined,
                background: isHovered ? item.color : undefined,
                color: isHovered ? "#ffffff" : undefined,
              }}
              title={item.label}
              data-testid={radialOpen ? `radial-action-${item.id}` : undefined}
            >
              {item.id === "mic" && <Mic size={17} />}
              {item.id === "doubt" && <Highlighter size={17} />}
              {item.id === "table" && <Table2 size={17} />}
              {item.id === "dialogue" && <MessageSquareText size={17} />}
              {item.id === "pdf_or_undo" &&
                (canUndo ? <Undo2 size={17} /> : <FileText size={17} />)}
              <span className="ai-radial-mini-tag">{item.shortLabel}</span>
            </button>
          );
        })}

        {radialOpen && (
          <button
            type="button"
            className="ai-radial-close-ring"
            onClick={() => setRadialOpen(false)}
            title="Close radial menu"
          />
        )}

        <button
          type="button"
          className={`ai-single-master-orb ${listening ? "is-listening" : ""} ${
            busy || transcribing ? "is-busy" : ""
          } ${doubtMarkerMode ? "is-doubt" : ""}`}
          onPointerDown={handleOrbPointerDown}
          onPointerMove={handleOrbPointerMove}
          onPointerUp={handleOrbPointerUp}
          onContextMenu={(e) => {
            e.preventDefault();
            setRadialOpen((prev) => !prev);
          }}
          title="Tap to Speak · Long-press & slide for Doubt Marker, Table, AI Dialogue, or PDF"
          data-testid="ai-voice-mic-button"
        >
          {busy || transcribing ? (
            <span className="ai-orb-ring-spinner" />
          ) : listening ? (
            <MicOff size={22} />
          ) : doubtMarkerMode ? (
            <Highlighter size={21} />
          ) : (
            <Mic size={22} />
          )}
          <span className="ai-master-orb-sparkle">
            <Sparkles size={10} />
          </span>
        </button>
      </div>
    </>
  );
};
