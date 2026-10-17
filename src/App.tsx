import { useEffect, useRef, useState } from "react";
import {
  ArrowUp,
  BookOpen,
  Bookmark,
  Check,
  ChevronRight,
  FlaskConical,
  Layers,
  Leaf,
  Maximize2,
  Mic,
  Minus,
  Moon,
  Pause,
  Pin,
  Plus,
  RotateCcw,
  Settings2,
  Sparkles,
  Square,
  Sun,
  Upload,
  Volume2,
  X,
  Code2,
  Image,
  Table2,
} from "lucide-react";
import {
  Activity,
  Page,
  Workspace,
  animations,
  execute,
  fixtureResponse,
  initial,
  kinds,
  registry,
  responseSchema,
  seedPage,
  solveLayout,
  restoreLayout,
} from "./core";

async function api<T>(path: string, options?: RequestInit): Promise<T> {
  const r = await fetch(path, options);
  if (!r.ok) {
    const b = await r.json().catch(() => ({ detail: r.statusText }));
    throw Error(b.detail || "Request failed");
  }
  return r.json();
}
function recover(): Workspace {
  try {
    const raw = JSON.parse(localStorage.getItem("folio.workspace") || "null");
    if (
      raw?.version === 1 &&
      Array.isArray(raw.activities) &&
      raw.notes &&
      raw.scroll &&
      raw.attempts
    )
      return raw;
  } catch {}
  return initial();
}
type VoiceRecognition = {
  start: () => void;
  stop: () => void;
  interimResults: boolean;
  lang: string;
  onresult: (e: {
    results: ArrayLike<ArrayLike<{ transcript: string }>>;
  }) => void;
  onend: () => void;
  onerror: () => void;
};

export default function App() {
  const [state, setState] = useState<Workspace>(recover),
    [pages, setPages] = useState<Page[]>([seedPage]);
  const [panel, setPanel] = useState<
      "library" | "upload" | "settings" | "explorer" | "revision" | null
    >(null),
    [verify, setVerify] = useState<Page | null>(null);
  const [prompt, setPrompt] = useState(""),
    [message, setMessage] = useState(""),
    [error, setError] = useState(""),
    [busy, setBusy] = useState(false),
    [provider, setProvider] = useState("fixture");
  const [saved, setSaved] = useState("Loading storage…"),
    [ready, setReady] = useState(false),
    [width, setWidth] = useState(window.innerWidth - 120),
    [voice, setVoice] = useState("idle"),
    [transcript, setTranscript] = useState("");
  const [trace, setTrace] = useState<unknown[]>([]),
    [sentence, setSentence] = useState("");
  const undo = useRef<Workspace[]>([]),
    latest = useRef(state),
    revision = useRef(0),
    saveQueue = useRef(Promise.resolve()),
    recognition = useRef<VoiceRecognition | null>(null),
    requestId = useRef(0),
    workspaceRef = useRef<HTMLDivElement>(null);
  latest.current = state;
  useEffect(() => {
    let live = true;
    Promise.all([
      api<Page[]>("/api/pages"),
      api<{ provider: string }>("/api/status"),
      api<{ revision: number; state: Workspace | null }>("/api/workspace"),
    ])
      .then(([p, s, w]) => {
        if (!live) return;
        setPages([seedPage, ...p]);
        setProvider(s.provider);
        revision.current = w.revision;
        if (
          w.state?.version === 1 &&
          localStorage.getItem("folio.pending") !== "yes"
        )
          setState(w.state);
        setSaved("Saved on this device");
        setReady(true);
      })
      .catch((e) => {
        if (live) {
          setError(e.message + " · Local cache remains available.");
          setSaved("Browser cache only");
          setReady(true);
        }
      });
    return () => {
      live = false;
      recognition.current?.stop();
      window.speechSynthesis?.cancel();
    };
  }, []);
  useEffect(() => {
    if (!ready) return;
    try {
      localStorage.setItem("folio.workspace", JSON.stringify(state));
      localStorage.setItem("folio.pending", "yes");
    } catch {
      setError(
        "Browser storage is full. Keep this tab open until the server saves.",
      );
    }
    if (!ready) return;
    const timer = setTimeout(() => {
      const snapshot = state;
      saveQueue.current = saveQueue.current.then(async () => {
        try {
          setSaved("Saving…");
          const result = await api<{ revision: number }>("/api/workspace", {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
              revision: revision.current,
              state: snapshot,
            }),
          });
          revision.current = result.revision;
          if (JSON.stringify(latest.current) === JSON.stringify(snapshot))
            localStorage.removeItem("folio.pending");
          setSaved("Saved on this device");
        } catch (e) {
          setSaved("Browser cache only");
          setError((e as Error).message);
        }
      });
    }, 350);
    return () => clearTimeout(timer);
  }, [state, ready]);
  useEffect(() => {
    const el = workspaceRef.current;
    if (!el) return;
    const observer = new ResizeObserver((entries) =>
      setWidth(entries[0].contentRect.width),
    );
    observer.observe(el);
    return () => observer.disconnect();
  }, []);
  useEffect(() => {
    const target = document.querySelector(
      '[data-source=\"' + state.source + '\"]',
    );
    if (target) {
      const parent = target.closest(".reader-body");
      if (parent) {
        const r = target.getBoundingClientRect(),
          pr = parent.getBoundingClientRect();
        if (r.top < pr.top || r.bottom > pr.bottom)
          parent.scrollTop += r.top - pr.top - 20;
      }
    }
  }, [state.source]);
  useEffect(() => {
    document.documentElement.dataset.theme = state.dark ? "dark" : "light";
  }, [state.dark]);
  const page =
    pages.find((p) => p.elements.some((e) => e.id === state.source)) ||
    seedPage;
  const selected = page.elements.find((e) => e.id === state.source);
  const columns = solveLayout(state.activities, width, state.focus);
  const update = (fn: (s: Workspace) => void) =>
    setState((s) => {
      const next = structuredClone(s);
      fn(next);
      return next;
    });
  const patch = (id: string, fn: (a: Activity) => void) =>
    update((s) => {
      const a = s.activities.find((a) => a.id === id);
      if (a) fn(a);
    });
  const run = (commands: unknown[], actor: "user" | "ai" = "user") => {
    try {
      const before = latest.current;
      const next = execute(before, commands, pages, actor);
      undo.current.push(before);
      if (undo.current.length > 20) undo.current.shift();
      latest.current = next;
      setState(next);
      setTrace((t) =>
        [
          { at: new Date().toISOString(), commands, result: "accepted" },
          ...t,
        ].slice(0, 30),
      );
    } catch (e) {
      setError((e as Error).message);
      setTrace((t) => [{ commands, result: (e as Error).message }, ...t]);
    }
  };
  function openPage(p: Page) {
    if (!p.approved) {
      setVerify(p);
      return;
    }
    const source =
      p.elements.find((e) => e.type === "paragraph")?.id || p.elements[0]?.id;
    if (source) run([{ type: "FOCUS_SOURCE", source }]);
    setPanel(null);
  }
  async function ask(text = prompt) {
    if (!text.trim() || busy) return;
    setError("");
    setPrompt("");
    const lower = text.toLowerCase();
    const quiz = state.activities.find(
      (a) => a.kind === "quiz" && a.status === "suspended",
    );
    if (/^(continue|resume)/.test(lower) && quiz) {
      run([{ type: "RESUME_ACTIVITY", id: quiz.id }]);
      return;
    }
    if (lower.includes("pause")) {
      const q = state.activities.find(
        (a) => a.kind === "quiz" && a.status === "active",
      );
      if (q) {
        run([
          { type: "SUSPEND_ACTIVITY", id: q.id },
          { type: "FOCUS_SOURCE", source: q.source },
        ]);
        return;
      }
    }
    const id = ++requestId.current;
    setBusy(true);
    const contextSource = state.source;
    try {
      const result =
        provider === "fixture"
          ? fixtureResponse(text, contextSource)
          : await api<unknown>("/api/mentor", {
              method: "POST",
              headers: { "Content-Type": "application/json" },
              body: JSON.stringify({
                request: text,
                source: contextSource,
                context: {
                  elements: page.elements.map((e) => ({
                    ...e,
                    text: page.corrections[e.id] ?? e.text,
                  })),
                  selectedSentence: sentence,
                  activities: state.activities.map((a) => ({
                    id: a.id,
                    kind: a.kind,
                    status: a.status,
                    pinned: a.pinned,
                  })),
                  recentAttempts: state.attempts.slice(-8),
                },
                capabilities: registry,
              }),
            });
      if (id !== requestId.current) return;
      if (latest.current.source !== contextSource)
        throw Error(
          "Your source changed while the mentor was working. Please ask again.",
        );
      const valid = responseSchema.parse(result);
      run(valid.commands, "ai");
      setMessage(valid.message);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      if (id === requestId.current) setBusy(false);
    }
  }
  function speak(text: string) {
    window.speechSynthesis?.cancel();
    if (!window.speechSynthesis) {
      setError("Speech output is unavailable in this browser.");
      return;
    }
    const speech = new SpeechSynthesisUtterance(text);
    speech.rate = 0.92;
    speech.onend = () => setVoice("idle");
    speech.onerror = () => setVoice("idle");
    setVoice("speaking");
    window.speechSynthesis.speak(speech);
  }
  function microphone() {
    if (voice !== "idle") {
      recognition.current?.stop();
      window.speechSynthesis?.cancel();
      setVoice("idle");
      return;
    }
    const w = window as unknown as {
      SpeechRecognition?: new () => VoiceRecognition;
      webkitSpeechRecognition?: new () => VoiceRecognition;
    };
    const Constructor = w.SpeechRecognition || w.webkitSpeechRecognition;
    if (!Constructor) {
      setError(
        "Speech recognition is unavailable here. Use the text field below.",
      );
      return;
    }
    const r = new Constructor();
    recognition.current = r;
    r.interimResults = true;
    r.lang = "en-IN";
    r.onresult = (e) => {
      const text = Array.from(e.results)
        .map((x) => x[0].transcript)
        .join(" ");
      setTranscript(text);
      setPrompt(text);
    };
    r.onend = () => setVoice("idle");
    r.onerror = () => {
      setVoice("idle");
      setError(
        "Microphone unavailable or permission denied. Text input remains available.",
      );
    };
    try {
      r.start();
      setVoice("listening");
    } catch {
      setVoice("idle");
      setError("Could not start microphone.");
    }
  }
  function renderActivity(a: Activity) {
    const p =
      pages.find((p) => p.elements.some((e) => e.id === a.source)) || page;
    const source = p.elements.find((e) => e.id === a.source);
    const origin = p.fixture
      ? "Sample study content"
      : "Source-linked · page " + p.number;
    return (
      <section
        className={
          "surface " + a.kind + (a.id === state.focus ? " focused" : "")
        }
        key={a.id}
        aria-label={registry[a.kind].title}
      >
        <div className="surface-bar">
          <span className="eyebrow">
            {a.kind === "reader" ? (
              <BookOpen size={15} />
            ) : a.kind === "quiz" ? (
              <FlaskConical size={15} />
            ) : (
              <Sparkles size={15} />
            )}{" "}
            {registry[a.kind].title}
          </span>
          <div className="tools">
            <button
              aria-label={"Pin " + a.kind}
              className={a.pinned ? "active" : ""}
              onClick={() =>
                patch(a.id, (x) => {
                  x.pinned = !x.pinned;
                })
              }
            >
              <Pin size={15} />
            </button>
            <button
              aria-label={"Focus " + a.kind}
              onClick={() =>
                run([
                  ...state.activities
                    .filter((x) => x.id !== a.id && !x.pinned && x.visible)
                    .map((x) => ({ type: "HIDE_COMPONENT", id: x.id })),
                  { type: "FOCUS_ACTIVITY", id: a.id },
                ])
              }
            >
              <Maximize2 size={15} />
            </button>
            {a.kind !== "reader" && (
              <button
                aria-label={"Hide " + a.kind}
                onClick={() =>
                  run([
                    {
                      type:
                        a.kind === "quiz"
                          ? "SUSPEND_ACTIVITY"
                          : "HIDE_COMPONENT",
                      id: a.id,
                    },
                  ])
                }
              >
                <Minus size={17} />
              </button>
            )}
          </div>
        </div>
        {a.kind === "reader" ? (
          <div
            className="reader-body"
            style={{ fontSize: state.font }}
            ref={(el) => {
              if (el && Math.abs(el.scrollTop - (state.scroll[p.id] || 0)) > 5)
                el.scrollTop = state.scroll[p.id] || 0;
            }}
            onScroll={(e) => {
              const top = e.currentTarget.scrollTop;
              update((s) => {
                s.scroll[p.id] = top;
              });
            }}
          >
            <div className="chapter-kicker">
              BIOLOGY /{" "}
              {p.fixture ? "DEVELOPMENT SAMPLE" : "YOUR NCERT LIBRARY"}
            </div>
            <h1>{p.title}</h1>
            <div className="source-line">
              <span className="dot" />
              {p.fixture
                ? "Original sample · not NCERT text"
                : `Page ${p.number} · verified reconstruction`}
              <button onClick={() => setVerify(p)}>
                View source <ChevronRight size={13} />
              </button>
            </div>
            {p.elements.map((e, index) =>
              e.type === "heading" ? (
                <h2 key={e.id}>{e.text}</h2>
              ) : e.type === "figure" ? (
                <button
                  key={e.id}
                  className="figure-link"
                  onClick={() =>
                    run([
                      {
                        type: "CREATE_ACTIVITY",
                        kind: "diagram",
                        source: e.id,
                        content: {},
                      },
                    ])
                  }
                >
                  <Image size={18} /> Inspect extracted figure{" "}
                  <ChevronRight size={16} />
                </button>
              ) : (
                <div
                  key={e.id}
                  data-source={e.id}
                  className={
                    "passage " + (state.source === e.id ? "selected" : "")
                  }
                >
                  <button
                    className="paragraph-index"
                    aria-label={"Focus paragraph " + index}
                    onClick={() =>
                      run([{ type: "FOCUS_SOURCE", source: e.id }])
                    }
                  >
                    {String(index).padStart(2, "0")}
                  </button>
                  <p>
                    {(p.corrections[e.id] ?? e.text)
                      .split(/(?<=[.!?])\s+/)
                      .map((text, i) => (
                        <span
                          key={i}
                          tabIndex={0}
                          role="button"
                          className={
                            sentence === e.id + ":" + i
                              ? "sentence-selected"
                              : ""
                          }
                          onKeyDown={(ev) => {
                            if (ev.key === "Enter") {
                              setSentence(e.id + ":" + i);
                              run([{ type: "FOCUS_SOURCE", source: e.id }]);
                            }
                          }}
                          onClick={() => {
                            if (window.getSelection()?.toString()) return;
                            setSentence(e.id + ":" + i);
                            run([{ type: "FOCUS_SOURCE", source: e.id }]);
                          }}
                        >
                          {text}{" "}
                        </span>
                      ))}
                  </p>
                  {state.source === e.id && (
                    <div className="passage-actions">
                      <button
                        onClick={() =>
                          ask(
                            "Explain " +
                              (sentence
                                ? "the selected sentence"
                                : "this paragraph"),
                          )
                        }
                      >
                        <Sparkles size={14} /> Explain
                      </button>
                      <button
                        onClick={() =>
                          update((s) => {
                            s.done = s.done.includes(e.id)
                              ? s.done.filter((x) => x !== e.id)
                              : [...s.done, e.id];
                          })
                        }
                      >
                        <Check size={14} />
                        {state.done.includes(e.id) ? "Read" : "Mark as read"}
                      </button>
                      <button
                        onClick={() =>
                          update((s) => {
                            s.notes[e.id] = s.notes[e.id] ?? "";
                          })
                        }
                      >
                        <Bookmark size={14} /> Note
                      </button>
                    </div>
                  )}
                  {Object.hasOwn(state.notes, e.id) && (
                    <textarea
                      className="note"
                      placeholder="Your note, kept separate from the source…"
                      value={state.notes[e.id]}
                      onChange={(ev) =>
                        update((s) => {
                          s.notes[e.id] = ev.target.value;
                        })
                      }
                    />
                  )}
                </div>
              ),
            )}
            <div className="reading-end">
              <Leaf size={23} />
              <div>
                <strong>Make a connection.</strong>
                <p>Turn this passage into something you remember.</p>
              </div>
              <button
                onClick={() => ask("Ask me a question about this passage")}
              >
                Try active recall <ChevronRight size={16} />
              </button>
            </div>
          </div>
        ) : a.kind === "quiz" ? (
          (() => {
            const questions = a.content.questions || [];
            const q = questions[a.index];
            if (!q) return <p>No question available.</p>;
            const answered = a.submitted[a.index];
            return (
              <div className="content quiz-content">
                <span className="tag">{origin}</span>
                <div className="quiz-progress">
                  <span>
                    QUESTION {a.index + 1} OF {questions.length}
                  </span>
                  <span>{Object.keys(a.submitted).length} answered</span>
                </div>
                <h2>{q.prompt}</h2>
                <div className="answers">
                  {q.options.map((option, i) => (
                    <button
                      key={i}
                      disabled={answered}
                      className={
                        (a.answers[a.index] === i ? "chosen " : "") +
                        (answered && i === q.answer ? "correct" : "")
                      }
                      onClick={() =>
                        patch(a.id, (x) => {
                          x.answers[x.index] = i;
                        })
                      }
                    >
                      <span>{String.fromCharCode(65 + i)}</span>
                      {option}
                      {answered && i === q.answer && <Check size={18} />}
                    </button>
                  ))}
                </div>
                {answered ? (
                  <div className="feedback">
                    <strong>
                      {a.answers[a.index] === q.answer
                        ? "That’s right."
                        : "A useful thing to revisit."}
                    </strong>
                    <p>{q.explanation}</p>
                    {a.index < questions.length - 1 && (
                      <button
                        onClick={() =>
                          patch(a.id, (x) => {
                            x.index++;
                          })
                        }
                      >
                        Next question <ChevronRight size={16} />
                      </button>
                    )}
                  </div>
                ) : (
                  <button
                    className="primary"
                    disabled={a.answers[a.index] === undefined}
                    onClick={() =>
                      update((s) => {
                        const x = s.activities.find((x) => x.id === a.id)!;
                        x.submitted[x.index] = true;
                        const correct = x.answers[x.index] === q.answer;
                        s.attempts.push({
                          source: x.source,
                          correct,
                          at: Date.now(),
                          due: Date.now() + (correct ? 3 : 1) * 86400000,
                        });
                      })
                    }
                  >
                    Check answer <Check size={16} />
                  </button>
                )}
                <button
                  className="text-button"
                  onClick={() =>
                    run([
                      { type: "SUSPEND_ACTIVITY", id: a.id },
                      { type: "FOCUS_SOURCE", source: a.source },
                    ])
                  }
                >
                  <Pause size={15} /> Pause & review source
                </button>
              </div>
            );
          })()
        ) : a.kind === "table" ? (
          <div className="content">
            <span className="tag">{origin} · enrichment</span>
            <h2>See the differences.</h2>
            <div className="table-scroll">
              <table>
                <thead>
                  <tr>
                    {a.content.headers?.map((h, i) => (
                      <th key={i}>{h}</th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {a.content.rows?.map((row, i) => (
                    <tr key={i}>
                      {row.map((v, j) => (
                        <td key={j}>{v}</td>
                      ))}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <button
              className="text-button"
              onClick={() => run([{ type: "FOCUS_SOURCE", source: a.source }])}
            >
              <BookOpen size={15} /> Return to source
            </button>
          </div>
        ) : a.kind === "diagram" ? (
          <div className="content">
            <span className="tag">
              Original extracted image · page {p.number}
            </span>
            <div className="zoom-tools">
              <button
                aria-label="Zoom out"
                onClick={() =>
                  patch(a.id, (x) => {
                    x.zoom = Math.max(0.5, x.zoom - 0.25);
                  })
                }
              >
                <Minus size={16} />
              </button>
              <span>{Math.round(a.zoom * 100)}%</span>
              <button
                aria-label="Zoom in"
                onClick={() =>
                  patch(a.id, (x) => {
                    x.zoom = Math.min(4, x.zoom + 0.25);
                  })
                }
              >
                <Plus size={16} />
              </button>
            </div>
            <div className="diagram-scroll">
              {source?.media ? (
                <img
                  src={source.media}
                  alt={source.text}
                  style={{ width: `${a.zoom * 100}%`, maxWidth: "none" }}
                />
              ) : (
                <p>No source figure attached.</p>
              )}
            </div>
            <p className="muted">{source?.text}</p>
          </div>
        ) : (
          <div className="content">
            <span className="tag">
              {origin} ·{" "}
              {p.fixture
                ? "fixture explanation"
                : "AI-generated · verify with source"}
            </span>
            <h2>A closer look.</h2>
            <p className="explanation-text">{a.content.text}</p>
            <div className="row">
              <button onClick={() => speak(a.content.text || "")}>
                <Volume2 size={16} /> Listen
              </button>
              <button
                onClick={() =>
                  run([{ type: "FOCUS_SOURCE", source: a.source }])
                }
              >
                <BookOpen size={16} /> Source
              </button>
            </div>
          </div>
        )}
      </section>
    );
  }
  return (
    <div className="app">
      <aside className="rail">
        <a
          className="brand"
          href="#"
          aria-label="Folio home"
          onClick={(e) => {
            e.preventDefault();
            setPanel("library");
          }}
        >
          <Leaf size={25} />
        </a>
        <div className="rail-links">
          <button
            title="Library"
            aria-label="Library"
            onClick={() => setPanel("library")}
          >
            <BookOpen size={21} />
          </button>
          <button
            title="Learning activities"
            aria-label="Learning activities"
            onClick={() => setPanel(panel === "revision" ? null : "revision")}
          >
            <Layers size={21} />
          </button>
          <button
            title="Upload page"
            aria-label="Upload page"
            onClick={() => setPanel("upload")}
          >
            <Plus size={23} />
          </button>
        </div>
        <div className="rail-bottom">
          <button
            title="UI explorer"
            aria-label="UI explorer"
            onClick={() => setPanel("explorer")}
          >
            <Code2 size={20} />
          </button>
          <button
            title="Preferences"
            aria-label="Preferences"
            onClick={() => setPanel("settings")}
          >
            <Settings2 size={20} />
          </button>
        </div>
      </aside>
      <div className="main">
        <header>
          <div className="wordmark">
            folio<span> / </span>
            <small>your biology workspace</small>
          </div>
          <div className="header-right">
            <span className="save-status">
              <span className="dot" />
              {saved}
            </span>
            <button
              className="avatar"
              aria-label="Open preferences"
              onClick={() => setPanel("settings")}
            >
              Y
            </button>
          </div>
        </header>
        <div className="workspace-heading">
          <div>
            <div className="breadcrumb">
              <button onClick={() => setPanel("library")}>My library</button>
              <ChevronRight size={13} />
              <span>{page.chapter}</span>
            </div>
            <div className="session-label">
              <span className="live-dot" /> A little curiosity goes a long way.
            </div>
          </div>
          <div className="heading-actions">
            <span className="mode-badge">
              {provider === "fixture" ? "FIXTURE MODE" : "GEMINI CONNECTED"}
            </span>
            <button
              className="subtle"
              onClick={() => {
                const s = undo.current.pop();
                if (s) setState((current) => restoreLayout(current, s));
              }}
              disabled={!undo.current.length}
            >
              <RotateCcw size={16} /> Undo layout
            </button>
          </div>
        </div>
        <div className="activity-strip">
          <button
            className="activity active"
            onClick={() =>
              run([{ type: "FOCUS_SOURCE", source: state.source }])
            }
          >
            <BookOpen size={15} /> Reading{" "}
            <span>{page.number.toString().padStart(2, "0")}</span>
          </button>
          {state.activities
            .filter((a) => a.kind !== "reader")
            .map((a) => (
              <button
                key={a.id}
                className={
                  "activity " +
                  (state.focus === a.id && a.visible ? "active" : "")
                }
                onClick={() => run([{ type: "RESUME_ACTIVITY", id: a.id }])}
              >
                {a.kind === "quiz" ? (
                  <FlaskConical size={14} />
                ) : (
                  <Layers size={14} />
                )}{" "}
                {registry[a.kind].title}
                {a.status === "suspended" && <Pause size={12} />}
              </button>
            ))}
          <span className="strip-spacer" />
          <button className="text-button" onClick={() => setPanel("upload")}>
            <Upload size={15} /> Add a page
          </button>
        </div>
        {error && (
          <div className="notice error" role="alert">
            {error}
            <button aria-label="Dismiss error" onClick={() => setError("")}>
              <X size={17} />
            </button>
          </div>
        )}
        <div
          className="workspace"
          ref={workspaceRef}
          style={{
            gridTemplateColumns: columns
              .map(
                (col, i) =>
                  `minmax(${Math.min(width, Math.max(...col.map((a) => registry[a.kind].minWidth)))}px,${i === 0 && columns.length > 1 ? 1.3 : 1}fr)`,
              )
              .join(" "),
          }}
        >
          {columns.map((col, i) => (
            <div className="workspace-column" key={i}>
              {col.map(renderActivity)}
            </div>
          ))}
          {!columns.length && (
            <button
              onClick={() =>
                run([{ type: "FOCUS_SOURCE", source: state.source }])
              }
            >
              Return to reading
            </button>
          )}
        </div>
        <div className="composer-area">
          {message && (
            <div className="mentor-message">
              <Sparkles size={14} />
              <span>{message}</span>
              <button
                aria-label="Dismiss message"
                onClick={() => setMessage("")}
              >
                <X size={14} />
              </button>
            </div>
          )}
          {voice !== "idle" && (
            <div className="voice-caption" role="status">
              <span className="live-dot" />
              {voice === "listening" ? "Listening · " + transcript : "Speaking"}
              <button onClick={microphone}>
                <Square size={13} /> Stop
              </button>
            </div>
          )}
          <div className="suggestions">
            <span>EXPLORE THIS PASSAGE</span>
            <button onClick={() => ask("Explain the selected sentence")}>
              <Sparkles size={14} /> Explain it simply
            </button>
            <button onClick={() => ask("Make a comparison table")}>
              <Table2 size={14} /> Make a comparison
            </button>
            <button onClick={() => ask("Ask me a question")}>
              <FlaskConical size={14} /> Quiz me
            </button>
          </div>
          <form
            className="composer"
            onSubmit={(e) => {
              e.preventDefault();
              ask();
            }}
          >
            <Sparkles size={20} />
            <input
              aria-label="Ask your mentor"
              placeholder="Ask, explore, or say what you need…"
              value={prompt}
              onChange={(e) => setPrompt(e.target.value)}
            />
            <span className="context-pill">
              {selected ? "Passage selected" : "Study context"}
            </span>
            <button
              type="button"
              aria-label={voice === "idle" ? "Start microphone" : "Stop voice"}
              onClick={microphone}
            >
              <Mic size={20} />
            </button>
            {busy ? (
              <button
                type="button"
                aria-label="Cancel mentor request"
                onClick={() => {
                  requestId.current++;
                  setBusy(false);
                }}
              >
                <Square size={19} />
              </button>
            ) : (
              <button
                className="send"
                aria-label="Send to mentor"
                disabled={!prompt.trim()}
              >
                <ArrowUp size={20} />
              </button>
            )}
          </form>
          <div className="composer-foot">
            <span>
              {provider === "fixture"
                ? "Sample resources are deterministic. Configure Gemini to study your own pages with AI."
                : "AI enrichments are separate from your source. Check scientific claims against NCERT."}
            </span>
            <span>NEET BIOLOGY</span>
          </div>
        </div>
      </div>
      {panel && (
        <div
          className="modal-backdrop"
          onMouseDown={(e) => {
            if (e.target === e.currentTarget) setPanel(null);
          }}
        >
          <section
            className={"modal " + (panel === "explorer" ? "wide" : "")}
            role="dialog"
            aria-modal="true"
            aria-label={panel}
          >
            <div className="modal-head">
              <div>
                <span className="eyebrow">YOUR WORKSPACE</span>
                <h2>
                  {panel === "library"
                    ? "A growing body of knowledge."
                    : panel === "upload"
                      ? "One page at a time."
                      : panel === "settings"
                        ? "Make room for your mind."
                        : panel === "explorer"
                          ? "Interface system explorer"
                          : "Pick up where you left off."}
                </h2>
              </div>
              <button aria-label="Close dialog" onClick={() => setPanel(null)}>
                <X />
              </button>
            </div>
            {panel === "library" && (
              <>
                <p className="muted">
                  Build your chapters gradually. Every verified page is ready to
                  study.
                </p>
                <div className="library-list">
                  {pages.map((p) => (
                    <button key={p.id} onClick={() => openPage(p)}>
                      <div className="book-cover">
                        <Leaf size={27} />
                      </div>
                      <div>
                        <strong>{p.chapter}</strong>
                        <p>
                          {p.fixture
                            ? "Development sample"
                            : `Page ${p.number} · ${p.approved ? "Verified" : "Needs verification"}`}
                        </p>
                      </div>
                      <ChevronRight size={18} />
                    </button>
                  ))}
                </div>
                <button className="primary" onClick={() => setPanel("upload")}>
                  <Plus size={16} /> Add a page
                </button>
              </>
            )}
            {panel === "upload" && (
              <UploadForm
                onComplete={(p) => {
                  setPages((all) => [...all.filter((x) => x.id !== p.id), p]);
                  setPanel(null);
                  setVerify(p);
                }}
              />
            )}
            {panel === "settings" && (
              <div className="settings">
                <label>
                  Reading size <span>{state.font}px</span>
                  <input
                    aria-label="Reading size"
                    type="range"
                    min="16"
                    max="28"
                    value={state.font}
                    onChange={(e) =>
                      update((s) => {
                        s.font = Number(e.target.value);
                      })
                    }
                  />
                </label>
                <button
                  onClick={() =>
                    update((s) => {
                      s.dark = !s.dark;
                    })
                  }
                >
                  {state.dark ? <Sun size={18} /> : <Moon size={18} />} Switch
                  to {state.dark ? "light" : "dark"} theme
                </button>
                <button
                  onClick={() => {
                    localStorage.setItem(
                      "folio.snapshot",
                      JSON.stringify(state),
                    );
                    setMessage("Workspace snapshot saved.");
                    setPanel(null);
                  }}
                >
                  Save workspace snapshot
                </button>
                <button
                  onClick={() => {
                    try {
                      const s = JSON.parse(
                        localStorage.getItem("folio.snapshot") || "null",
                      );
                      if (s?.version !== 1)
                        throw Error("No saved snapshot yet");
                      undo.current.push(state);
                      setState(s);
                      setPanel(null);
                    } catch (e) {
                      setError((e as Error).message);
                      setPanel(null);
                    }
                  }}
                >
                  Restore snapshot
                </button>
                <p className="muted">
                  Provider: {provider}. API keys stay on the Python server. Set
                  GEMINI_API_KEY and GEMINI_MODEL, then restart it and reload
                  this page.
                </p>
              </div>
            )}
            {panel === "revision" && (
              <>
                <h3>Activities</h3>
                {state.activities.map((a) => (
                  <button
                    className="list-row"
                    key={a.id}
                    onClick={() => {
                      run([{ type: "RESUME_ACTIVITY", id: a.id }]);
                      setPanel(null);
                    }}
                  >
                    <span>{registry[a.kind].title}</span>
                    <span>
                      {a.status} <ChevronRight size={14} />
                    </span>
                  </button>
                ))}
                <h3>Retrieval history</h3>
                {!state.attempts.length ? (
                  <p className="muted">
                    Complete a question to start your revision history.
                  </p>
                ) : (
                  state.attempts.map((x, i) => (
                    <button
                      className="list-row"
                      key={i}
                      onClick={() => {
                        run([{ type: "FOCUS_SOURCE", source: x.source }]);
                        setPanel(null);
                      }}
                    >
                      <span>
                        {x.correct ? "Correct retrieval" : "Review recommended"}
                      </span>
                      <span>Review {new Date(x.due).toLocaleDateString()}</span>
                    </button>
                  ))
                )}
              </>
            )}
            {panel === "explorer" && (
              <>
                <p className="muted">
                  Live registry · {kinds.length} components ·{" "}
                  {Object.keys(animations).length} motion presets. Adding a
                  component uses the same validated runtime as the mentor.
                </p>
                <div className="catalog">
                  {kinds.map((kind) => (
                    <article key={kind}>
                      <span className="eyebrow">{kind}</span>
                      <h3>{registry[kind].title}</h3>
                      <p>{registry[kind].state}</p>
                      <small>
                        Minimum {registry[kind].minWidth}px ·{" "}
                        {registry[kind].variants.join(", ")}
                      </small>
                      <p className="muted">
                        {registry[kind].gestures.join(" / ")}
                      </p>
                      <button
                        onClick={() => {
                          if (kind === "diagram") {
                            const e = page.elements.find(
                              (e) => e.type === "figure",
                            );
                            if (!e) {
                              setError(
                                "Upload a page containing a source image to inspect the diagram component.",
                              );
                              return;
                            }
                            run([
                              {
                                type: "CREATE_ACTIVITY",
                                kind,
                                source: e.id,
                                content: {},
                              },
                            ]);
                          } else if (kind === "reader")
                            run([
                              {
                                type: "CREATE_ACTIVITY",
                                kind,
                                source: state.source,
                                content: {},
                              },
                            ]);
                          else if (kind === "explanation")
                            run([
                              {
                                type: "CREATE_ACTIVITY",
                                kind,
                                source: state.source,
                                content: {
                                  text: "Explorer fixture. This is a registered component with independent persistent state.",
                                },
                              },
                            ]);
                          else
                            run(
                              fixtureResponse(
                                kind === "quiz" ? "quiz" : "table",
                                "cell-p2",
                              ).commands,
                            );
                          setPanel(null);
                        }}
                      >
                        <Plus size={15} /> Add to composition
                      </button>
                    </article>
                  ))}
                </div>
                <h3>Command inspector</h3>
                <pre>
                  {JSON.stringify(
                    {
                      viewport: Math.round(width),
                      columns: columns.map((c) => c.map((a) => a.kind)),
                      trace,
                    },
                    null,
                    2,
                  )}
                </pre>
              </>
            )}
          </section>
        </div>
      )}
      {verify && (
        <Verification
          page={verify}
          onClose={() => setVerify(null)}
          onSave={(p) => {
            setPages((all) => all.map((x) => (x.id === p.id ? p : x)));
            setVerify(null);
            openPage(p);
          }}
        />
      )}
    </div>
  );
}

function UploadForm({ onComplete }: { onComplete: (p: Page) => void }) {
  const [file, setFile] = useState<File | null>(null),
    [chapter, setChapter] = useState(""),
    [number, setNumber] = useState(1),
    [busy, setBusy] = useState(false),
    [error, setError] = useState("");
  return (
    <form
      className="upload-form"
      onSubmit={async (e) => {
        e.preventDefault();
        if (!file) return;
        setBusy(true);
        setError("");
        try {
          const data = new FormData();
          data.append("file", file);
          data.append("chapter", chapter);
          data.append("number", String(number));
          onComplete(
            await api<Page>("/api/upload", { method: "POST", body: data }),
          );
        } catch (e) {
          setError((e as Error).message);
        } finally {
          setBusy(false);
        }
      }}
    >
      <label className="upload-zone">
        <Upload size={30} />
        <strong>{file ? file.name : "Bring a page into your workspace"}</strong>
        <span>PDF, PNG or JPEG · up to 25 MB</span>
        <input
          required
          type="file"
          accept="application/pdf,image/png,image/jpeg"
          onChange={(e) => setFile(e.target.files?.[0] || null)}
        />
      </label>
      <label>
        Chapter name
        <input
          required
          maxLength={200}
          placeholder="e.g. Cell: the unit of life"
          value={chapter}
          onChange={(e) => setChapter(e.target.value)}
        />
      </label>
      <label>
        Page to extract from this file
        <input
          type="number"
          min="1"
          required
          value={number}
          onChange={(e) => setNumber(Number(e.target.value))}
        />
      </label>
      <p className="muted">
        Text and embedded images are extracted locally. Scanned pages need
        manual transcription in this version. You’ll verify everything before
        studying.
      </p>
      {error && (
        <p role="alert" className="error">
          {error}
        </p>
      )}
      <button className="primary" disabled={busy}>
        {busy ? "Extracting and storing source…" : "Extract page"}
        <ChevronRight size={16} />
      </button>
    </form>
  );
}
function Verification({
  page,
  onClose,
  onSave,
}: {
  page: Page;
  onClose: () => void;
  onSave: (p: Page) => void;
}) {
  const [corrections, setCorrections] = useState(page.corrections),
    [busy, setBusy] = useState(false),
    [error, setError] = useState("");
  return (
    <div className="modal-backdrop">
      <section
        className="modal verification wide"
        role="dialog"
        aria-modal="true"
        aria-label="Verify page"
      >
        <div className="modal-head">
          <div>
            <span className="eyebrow">SOURCE VERIFICATION</span>
            <h2>{page.chapter}</h2>
          </div>
          <button aria-label="Close verification" onClick={onClose}>
            <X />
          </button>
        </div>
        {page.fixture ? (
          <p>
            This is original development sample text, not an uploaded NCERT
            page. Upload your textbook page to compare it with its
            reconstruction.
          </p>
        ) : (
          <>
            <p className="muted">
              Check reading order, scientific spelling and figure boundaries.
              Corrections are versioned separately; extracted source text stays
              immutable.
            </p>
            <div className="verify-columns">
              <div>
                <img src={page.original} alt="Original uploaded page" />
              </div>
              <div>
                {page.elements.map((e) =>
                  e.type === "figure" ? (
                    <figure key={e.id}>
                      <img src={e.media} alt={e.text} />
                      <figcaption>{e.text}</figcaption>
                    </figure>
                  ) : (
                    <label key={e.id}>
                      <span>
                        Source region [
                        {e.bbox.map((n) => Math.round(n)).join(", ")}]
                      </span>
                      <textarea
                        aria-label={"Correct region " + e.id}
                        value={corrections[e.id] ?? e.text}
                        onChange={(ev) =>
                          setCorrections({
                            ...corrections,
                            [e.id]: ev.target.value,
                          })
                        }
                      />
                    </label>
                  ),
                )}
              </div>
            </div>
            {error && (
              <p role="alert" className="error">
                {error}
              </p>
            )}
            <button
              className="primary"
              disabled={
                busy ||
                !page.elements.some(
                  (e) =>
                    e.type === "paragraph" &&
                    (corrections[e.id] ?? e.text).trim(),
                )
              }
              onClick={async () => {
                setBusy(true);
                try {
                  onSave(
                    await api<Page>(`/api/pages/${page.id}/verify`, {
                      method: "POST",
                      headers: { "Content-Type": "application/json" },
                      body: JSON.stringify({
                        version: page.version,
                        corrections,
                        approved: true,
                      }),
                    }),
                  );
                } catch (e) {
                  setError((e as Error).message);
                } finally {
                  setBusy(false);
                }
              }}
            >
              <Check size={16} />
              {busy ? "Saving…" : "Approve reconstruction & study"}
            </button>
          </>
        )}
      </section>
    </div>
  );
}
