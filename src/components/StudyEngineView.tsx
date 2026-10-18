import React, { useEffect, useRef, useState } from "react";
import {
  ArrowLeft,
  BookOpen,
  Check,
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  Eye,
  FileText,
  Highlighter,
  Image as ImageIcon,
  Maximize2,
  MousePointerClick,
  PenTool,
  RefreshCw,
  Sparkles,
  Trash2,
  X,
} from "lucide-react";
import {
  ExtractedFigure,
  LibraryPdfItem,
  NcertCardBlock,
  NcertSectionBlock,
} from "../data/neetCatalog";
import { DoubtMarkContext } from "../services/aiHarness";

interface StudyEngineViewProps {
  pdf: LibraryPdfItem;
  mode: "topics" | "study";
  scanningPdf: boolean;
  selectedTopicIds: string[];
  compiledSections: NcertSectionBlock[];
  doubtMarkerMode: boolean;
  activeDoubtMark: DoubtMarkContext | null;
  onBack: () => void;
  onOpenNativePdf: () => void;
  onSwitchToTopics: () => void;
  onToggleTopicSelection: (topicId: string) => void;
  onSelectAllTopics: () => void;
  onStartStudyingSelected: () => void;
  onSelectSingleTopic?: (topicId: string) => void;
  onMarkDoubt: (mark: DoubtMarkContext, viewportYRatio: number) => void;
  onClearDoubtMode: () => void;
  onDeleteAiBlock: (sectionId: string, blockId: string) => void;
  onRequestModifyAiBlock: (sectionId: string, block: NcertCardBlock) => void;
  onFocusElementYRatio: (ratio: number) => void;
}

/** Converts **bold** markdown tokens into <strong> elements while preserving original NCERT text */
function renderRichText(text: string): React.ReactNode {
  const parts = text.split(/(\*\*[^*]+\*\*)/g);
  return parts.map((part, idx) => {
    if (part.startsWith("**") && part.endsWith("**")) {
      return <strong key={idx}>{part.slice(2, -2)}</strong>;
    }
    return <React.Fragment key={idx}>{part}</React.Fragment>;
  });
}

export const StudyEngineView: React.FC<StudyEngineViewProps> = ({
  pdf,
  mode,
  scanningPdf,
  selectedTopicIds,
  compiledSections,
  doubtMarkerMode,
  activeDoubtMark,
  onBack,
  onOpenNativePdf,
  onSwitchToTopics,
  onToggleTopicSelection,
  onSelectAllTopics,
  onStartStudyingSelected,
  onSelectSingleTopic,
  onMarkDoubt,
  onClearDoubtMode,
  onDeleteAiBlock,
  onRequestModifyAiBlock,
  onFocusElementYRatio,
}) => {
  const [enlargedFigure, setEnlargedFigure] = useState<ExtractedFigure | null>(
    null
  );
  const [quizSelections, setQuizSelections] = useState<Record<string, number>>(
    {}
  );
  const [drawBoxMode, setDrawBoxMode] = useState(false);
  const [dragBox, setDragBox] = useState<{
    startX: number;
    startY: number;
    currX: number;
    currY: number;
  } | null>(null);

  const studyContainerRef = useRef<HTMLDivElement>(null);

  // Page / Topic navigation state for minimal bottom-left micro-pill
  const currentTopicIndex = Math.max(
    0,
    pdf.topics.findIndex((t) => selectedTopicIds.includes(t.id))
  );
  const activeTopic = pdf.topics[currentTopicIndex] || pdf.topics[0];
  const totalTopicsCount = Math.max(1, pdf.topics.length);
  const canGoPrev = currentTopicIndex > 0;
  const canGoNext = currentTopicIndex < totalTopicsCount - 1;

  const handlePrevPageOrTopic = () => {
    if (!canGoPrev) return;
    const prevTopic = pdf.topics[currentTopicIndex - 1];
    if (prevTopic && onSelectSingleTopic) {
      onSelectSingleTopic(prevTopic.id);
      window.scrollTo({ top: 0, behavior: "smooth" });
    }
  };

  const handleNextPageOrTopic = () => {
    if (!canGoNext) return;
    const nextTopic = pdf.topics[currentTopicIndex + 1];
    if (nextTopic && onSelectSingleTopic) {
      onSelectSingleTopic(nextTopic.id);
      window.scrollTo({ top: 0, behavior: "smooth" });
    }
  };

  // Keyboard Left/Right navigation in study mode
  useEffect(() => {
    if (mode !== "study") return;
    const onKeyDown = (e: KeyboardEvent) => {
      const tag = (e.target as HTMLElement | null)?.tagName;
      if (tag === "INPUT" || tag === "TEXTAREA") return;
      if (e.key === "ArrowLeft" && canGoPrev) {
        handlePrevPageOrTopic();
      } else if (e.key === "ArrowRight" && canGoNext) {
        handleNextPageOrTopic();
      }
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  });

  const handleCardOrTextClick = (
    e: React.MouseEvent,
    sectionId: string,
    blockId: string,
    summaryText: string
  ) => {
    const ratio = Math.max(
      0.1,
      Math.min(0.9, e.clientY / Math.max(1, window.innerHeight))
    );
    onFocusElementYRatio(ratio);

    if (!doubtMarkerMode) return;
    e.stopPropagation();

    const selectedText = window.getSelection()?.toString()?.trim();
    const markedText = selectedText || summaryText;

    onMarkDoubt(
      {
        id: `doubt-${Date.now()}`,
        sectionId,
        blockId,
        markedText,
      },
      ratio
    );
  };

  const handleDrawPointerDown = (e: React.PointerEvent) => {
    if (!doubtMarkerMode || !drawBoxMode) return;
    setDragBox({
      startX: e.clientX,
      startY: e.clientY,
      currX: e.clientX,
      currY: e.clientY,
    });
  };

  const handleDrawPointerMove = (e: React.PointerEvent) => {
    if (!dragBox) return;
    setDragBox((prev) =>
      prev ? { ...prev, currX: e.clientX, currY: e.clientY } : null
    );
  };

  const handleDrawPointerUp = (e: React.PointerEvent) => {
    if (!dragBox) return;
    const x = Math.min(dragBox.startX, dragBox.currX);
    const y = Math.min(dragBox.startY, dragBox.currY);
    const width = Math.abs(dragBox.currX - dragBox.startX);
    const height = Math.abs(dragBox.currY - dragBox.startY);
    setDragBox(null);
    setDrawBoxMode(false);

    if (width < 16 && height < 16) return;

    const centerX = x + width / 2;
    const centerY = y + height / 2;
    const el = document.elementFromPoint(centerX, centerY);
    const cardEl = el?.closest("[data-section-id]") as HTMLElement | null;
    const sectionId =
      cardEl?.getAttribute("data-section-id") ||
      compiledSections[0]?.id ||
      "sec-what-is-cell";
    const blockId = cardEl?.getAttribute("data-block-id") || undefined;
    const textContent =
      cardEl?.innerText?.replace(/\s+/g, " ")?.trim()?.slice(0, 220) ||
      "Marked region on NCERT study page";

    const ratio = Math.max(
      0.1,
      Math.min(0.9, e.clientY / Math.max(1, window.innerHeight))
    );

    onMarkDoubt(
      {
        id: `doubt-box-${Date.now()}`,
        sectionId,
        blockId,
        markedText: textContent,
        rect: { x, y, width, height },
      },
      ratio
    );
  };

  const renderNcertCard = (sectionId: string, card: NcertCardBlock) => {
    const isMarked = activeDoubtMark?.blockId === card.id;
    const commonProps = {
      "data-section-id": sectionId,
      "data-block-id": card.id,
      onClick: (e: React.MouseEvent) =>
        handleCardOrTextClick(
          e,
          sectionId,
          card.id,
          [
            card.title,
            ...(card.paragraphs || []),
            card.importantGreen,
            ...(card.scientists?.map((s) => `${s.name}: ${s.contribution}`) ||
              []),
          ]
            .filter(Boolean)
            .join(" ")
        ),
    };

    if (card.variant === "purple-card") {
      return (
        <div
          key={card.id}
          {...commonProps}
          className={`ncert-card purple-card ${
            doubtMarkerMode ? "markable-card" : ""
          } ${isMarked ? "doubt-marked-highlight" : ""}`}
        >
          {isMarked && (
            <div className="doubt-pin-tag">
              <Highlighter size={12} /> Marked for Doubt Clarification
            </div>
          )}
          {card.title && <h3>{card.title}</h3>}
          {card.paragraphs?.[0] && (
            <p>{renderRichText(card.paragraphs[0])}</p>
          )}
          {card.capabilities && card.capabilities.length > 0 && (
            <ol className="capability-list">
              {card.capabilities.map((cap, idx) => (
                <li key={idx}>
                  <span className="roman">{cap.roman}</span>
                  <span>{renderRichText(cap.text)}</span>
                </li>
              ))}
            </ol>
          )}
          {card.paragraphs?.slice(1).map((p, idx) => (
            <p key={idx}>{renderRichText(p)}</p>
          ))}
          {card.importantGreen && (
            <div className="important important-green">
              <p>{renderRichText(card.importantGreen)}</p>
            </div>
          )}
        </div>
      );
    }

    if (card.variant === "teal-card") {
      return (
        <div
          key={card.id}
          {...commonProps}
          className={`ncert-card teal-card ${
            doubtMarkerMode ? "markable-card" : ""
          } ${isMarked ? "doubt-marked-highlight" : ""}`}
        >
          {isMarked && (
            <div className="doubt-pin-tag">
              <Highlighter size={12} /> Marked for Doubt Clarification
            </div>
          )}
          {card.title && <h3>{card.title}</h3>}
          {card.scientists && card.scientists.length > 0 && (
            <div className="scientist-list">
              {card.scientists.map((sc, idx) => (
                <div key={idx} className="scientist">
                  <p>
                    <strong>{sc.name}</strong> {renderRichText(sc.contribution)}
                  </p>
                </div>
              ))}
            </div>
          )}
          {card.paragraphs?.map((p, idx) => (
            <p key={idx} style={{ marginTop: 12 }}>
              {renderRichText(p)}
            </p>
          ))}
          {card.footerNote && (
            <p style={{ marginTop: 19 }}>{renderRichText(card.footerNote)}</p>
          )}
        </div>
      );
    }

    if (card.variant === "timeline-group" && card.timelineItems) {
      return (
        <div key={card.id} className="timeline">
          {card.timelineItems.map((item) => {
            const itemMarked = activeDoubtMark?.blockId === item.id;
            return (
              <article
                key={item.id}
                data-section-id={sectionId}
                data-block-id={item.id}
                onClick={(e) =>
                  handleCardOrTextClick(
                    e,
                    sectionId,
                    item.id,
                    [
                      item.scientist,
                      item.year,
                      ...(item.bullets || []),
                      ...(item.paragraphs || []),
                      item.statement,
                      item.latinQuote,
                    ]
                      .filter(Boolean)
                      .join(" ")
                  )
                }
                className={`ncert-card timeline-card ${item.theme} ${
                  doubtMarkerMode ? "markable-card" : ""
                } ${itemMarked ? "doubt-marked-highlight" : ""}`}
              >
                {itemMarked && (
                  <div className="doubt-pin-tag">
                    <Highlighter size={12} /> Marked for Doubt Clarification
                  </div>
                )}
                <div className="scientist-heading">
                  <h3>{item.scientist}</h3>
                  {item.year && <span className="year">{item.year}</span>}
                </div>
                {item.descriptor && (
                  <div className="descriptor">{item.descriptor}</div>
                )}
                {item.bullets && item.bullets.length > 0 && (
                  <ul className="bullet-list">
                    {item.bullets.map((b, idx) => (
                      <li key={idx}>{renderRichText(b)}</li>
                    ))}
                  </ul>
                )}
                {item.paragraphs?.map((p, idx) => (
                  <p key={idx}>
                    {renderRichText(p)}
                    {idx === 0 && item.latinQuote && (
                      <span className="latin">{item.latinQuote}</span>
                    )}
                  </p>
                ))}
                {item.statement && (
                  <div className="statement">
                    <p>{renderRichText(item.statement)}</p>
                  </div>
                )}
              </article>
            );
          })}
        </div>
      );
    }

    if (card.variant === "theory-card" && card.theoryRows) {
      return (
        <div
          key={card.id}
          {...commonProps}
          className={`ncert-card theory ${
            doubtMarkerMode ? "markable-card" : ""
          } ${isMarked ? "doubt-marked-highlight" : ""}`}
        >
          {isMarked && (
            <div className="doubt-pin-tag">
              <Highlighter size={12} /> Marked for Doubt Clarification
            </div>
          )}
          {card.theoryRows.map((row, idx) => (
            <div key={idx} className="theory-row">
              <span className="theory-no">{row.roman}</span>
              <p>{renderRichText(row.text)}</p>
            </div>
          ))}
        </div>
      );
    }

    if (card.variant === "figure-card" && card.figure) {
      const fig = card.figure;
      return (
        <div
          key={card.id}
          {...commonProps}
          className={`ncert-card figure-extracted-card ${
            doubtMarkerMode ? "markable-card" : ""
          } ${isMarked ? "doubt-marked-highlight" : ""}`}
        >
          <div className="figure-card-header">
            <div className="figure-badge-pill">
              <ImageIcon size={14} />
              <span>{fig.label} · Extracted from PDF Page {fig.pageNumber}</span>
            </div>
            <div className="figure-card-actions">
              <button
                type="button"
                className="figure-action-btn"
                onClick={(e) => {
                  e.stopPropagation();
                  setEnlargedFigure(fig);
                }}
              >
                <Maximize2 size={13} /> Zoom Figure
              </button>
              <button
                type="button"
                className="figure-action-btn"
                onClick={(e) => {
                  e.stopPropagation();
                  onOpenNativePdf();
                }}
              >
                <Eye size={13} /> Source PDF
              </button>
            </div>
          </div>

          <div
            className="figure-image-frame"
            onClick={(e) => {
              if (!doubtMarkerMode) {
                e.stopPropagation();
                setEnlargedFigure(fig);
              }
            }}
          >
            <img src={fig.svgDataUrl} alt={fig.caption} />
          </div>

          <p className="figure-caption-text">
            <strong>{fig.label}:</strong> {fig.caption}
          </p>
        </div>
      );
    }

    if (card.variant === "ai-table" && card.tableData) {
      return (
        <div
          key={card.id}
          {...commonProps}
          className="ncert-card ai-harness-card ai-table-card"
        >
          {card.aiGenerated && (
            <div className="ai-harness-card-bar">
              <span className="ai-generated-badge">
                <Sparkles size={13} /> Added by AI Study Harness
              </span>
              <div className="ai-harness-controls">
                <button
                  type="button"
                  onClick={(e) => {
                    e.stopPropagation();
                    onRequestModifyAiBlock(sectionId, card);
                  }}
                  title="Modify this table with AI"
                >
                  <RefreshCw size={13} /> Modify
                </button>
                <button
                  type="button"
                  className="danger"
                  onClick={(e) => {
                    e.stopPropagation();
                    onDeleteAiBlock(sectionId, card.id);
                  }}
                  title="Delete this table"
                >
                  <Trash2 size={13} /> Delete
                </button>
              </div>
            </div>
          )}
          {card.title && <h3>{card.title}</h3>}
          <div className="ncert-table-wrap">
            <table className="ncert-comparison-table">
              <thead>
                <tr>
                  {card.tableData.headers.map((h, i) => (
                    <th key={i}>{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {card.tableData.rows.map((row, rIdx) => (
                  <tr key={rIdx}>
                    {row.map((cell, cIdx) => (
                      <td key={cIdx}>{renderRichText(cell)}</td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      );
    }

    if (card.variant === "ai-quiz" && card.quizData) {
      const chosen = quizSelections[card.id];
      const q = card.quizData;
      return (
        <div
          key={card.id}
          {...commonProps}
          className="ncert-card ai-harness-card ai-quiz-card"
        >
          <div className="ai-harness-card-bar">
            <span className="ai-generated-badge">
              <Sparkles size={13} /> AI Active Recall Check
            </span>
            <div className="ai-harness-controls">
              <button
                type="button"
                className="danger"
                onClick={(e) => {
                  e.stopPropagation();
                  onDeleteAiBlock(sectionId, card.id);
                }}
              >
                <Trash2 size={13} /> Delete
              </button>
            </div>
          </div>
          {card.title && <h3>{card.title}</h3>}
          <p className="ai-quiz-question">{q.question}</p>
          <div className="ai-quiz-options">
            {q.options.map((opt, idx) => {
              const isSelected = chosen === idx;
              const isCorrect = idx === q.answerIndex;
              let statusClass = "";
              if (chosen !== undefined) {
                if (isCorrect) statusClass = "correct";
                else if (isSelected) statusClass = "wrong";
              }
              return (
                <button
                  key={idx}
                  type="button"
                  className={`ai-quiz-opt ${statusClass}`}
                  onClick={(e) => {
                    e.stopPropagation();
                    setQuizSelections((prev) => ({ ...prev, [card.id]: idx }));
                  }}
                >
                  <span className="opt-letter">
                    {String.fromCharCode(65 + idx)}
                  </span>
                  <span>{opt}</span>
                </button>
              );
            })}
          </div>
          {chosen !== undefined && (
            <div className="important important-green">
              <p>
                <strong>
                  {chosen === q.answerIndex ? "✓ Correct!" : "✗ Review NCERT:"}
                </strong>{" "}
                {q.explanation}
              </p>
            </div>
          )}
        </div>
      );
    }

    return (
      <div
        key={card.id}
        {...commonProps}
        className={`ncert-card ai-harness-card ${
          doubtMarkerMode ? "markable-card" : ""
        } ${isMarked ? "doubt-marked-highlight" : ""}`}
      >
        <div className="ai-harness-card-bar">
          <span className="ai-generated-badge">
            <Sparkles size={13} /> AI Doubt Clarification Note
          </span>
          <div className="ai-harness-controls">
            <button
              type="button"
              onClick={(e) => {
                e.stopPropagation();
                onRequestModifyAiBlock(sectionId, card);
              }}
            >
              <RefreshCw size={13} /> Modify
            </button>
            <button
              type="button"
              className="danger"
              onClick={(e) => {
                e.stopPropagation();
                onDeleteAiBlock(sectionId, card.id);
              }}
            >
              <Trash2 size={13} /> Delete
            </button>
          </div>
        </div>
        {card.title && <h3>{card.title}</h3>}
        {card.paragraphs?.map((p, idx) => (
          <p key={idx}>{renderRichText(p)}</p>
        ))}
      </div>
    );
  };

  return (
    <div
      className="study-engine-fullscreen"
      ref={studyContainerRef}
      data-testid="study-engine-view"
    >
      {/* ZERO TOP BAR: Reclaimed 100% of top screen space for study content!
          Compact, intelligent page & topic navigation micro-pill sits unobtrusively at bottom-left. */}
      <nav
        className="study-bottom-page-dock"
        aria-label="Minimal page and topic navigation"
      >
        <button
          type="button"
          className="study-dock-pill-btn"
          onClick={mode === "study" ? onSwitchToTopics : onBack}
          data-testid="study-back-button"
          title={mode === "study" ? "Back to Topic Map" : "Back to Library"}
        >
          <ArrowLeft size={13} />
          <span>{mode === "study" ? "Topics" : "Library"}</span>
        </button>

        {mode === "study" && (
          <>
            <span className="study-dock-divider" />
            <button
              type="button"
              className="study-dock-page-arrow"
              disabled={!canGoPrev}
              onClick={handlePrevPageOrTopic}
              data-testid="study-prev-page"
              title="Previous Page / Topic (←)"
            >
              <ChevronLeft size={15} />
            </button>
            <span className="study-dock-page-indicator" title={activeTopic?.title}>
              p.{activeTopic?.pageStart || currentTopicIndex + 1} ·{" "}
              {currentTopicIndex + 1}/{totalTopicsCount}
            </span>
            <button
              type="button"
              className="study-dock-page-arrow"
              disabled={!canGoNext}
              onClick={handleNextPageOrTopic}
              data-testid="study-next-page"
              title="Next Page / Topic (→)"
            >
              <ChevronRight size={15} />
            </button>
          </>
        )}

        <span className="study-dock-divider" />
        <button
          type="button"
          className="study-dock-pill-btn subtle"
          onClick={onOpenNativePdf}
          data-testid="switch-to-native-pdf"
          title="Open Native PDF Viewer"
        >
          <FileText size={12} />
          <span>PDF</span>
        </button>
      </nav>

      {/* On-Demand Doubt Marker Floating Bar (ONLY shown when user activates Doubt Marker) */}
      {doubtMarkerMode && (
        <div
          className="doubt-marker-floating-toolbar"
          data-testid="doubt-marker-toolbar"
        >
          <div className="doubt-toolbar-badge">
            <Highlighter size={15} />
            <span>Doubt Marker Active</span>
          </div>
          <span className="doubt-toolbar-hint">
            Tap any sentence/card or draw a box to mark your doubt
          </span>
          <div className="doubt-toolbar-actions">
            <button
              type="button"
              className={`doubt-mode-btn ${!drawBoxMode ? "active" : ""}`}
              onClick={() => setDrawBoxMode(false)}
            >
              <MousePointerClick size={14} /> Tap Sentence / Card
            </button>
            <button
              type="button"
              className={`doubt-mode-btn ${drawBoxMode ? "active" : ""}`}
              onClick={() => setDrawBoxMode((d) => !d)}
            >
              <PenTool size={14} /> Draw Region Box
            </button>
            <button
              type="button"
              className="doubt-mode-close"
              onClick={onClearDoubtMode}
            >
              <X size={14} /> Exit Marker
            </button>
          </div>
        </div>
      )}

      {doubtMarkerMode && drawBoxMode && (
        <div
          className="doubt-draw-surface"
          onPointerDown={handleDrawPointerDown}
          onPointerMove={handleDrawPointerMove}
          onPointerUp={handleDrawPointerUp}
        >
          {dragBox && (
            <div
              className="doubt-drawn-rect"
              style={{
                left: Math.min(dragBox.startX, dragBox.currX),
                top: Math.min(dragBox.startY, dragBox.currY),
                width: Math.abs(dragBox.currX - dragBox.startX),
                height: Math.abs(dragBox.currY - dragBox.startY),
              }}
            />
          )}
        </div>
      )}

      {/* MODE 1: AI Scanned Chapter Topic Map */}
      {mode === "topics" ? (
        <main className="topic-map-container" data-testid="topic-map-screen">
          <div className="topic-map-hero">
            <div className="topic-map-eyebrow">
              <Sparkles size={14} /> AI PDF TOPIC SCANNER · NCERT STRUCTURE
            </div>
            <h1>Select Topic to Reconstruct Study Notes</h1>
            <p>
              {pdf.topics.length} NCERT topics &amp; sub-topics identified. Tap
              any topic below (such as <strong>8.5.9 Centrosome and Centrioles</strong>)
              to immediately extract and organize only its relevant NCERT content!
            </p>
            <div className="topic-map-bulk-bar">
              <button
                type="button"
                className="topic-secondary-btn"
                onClick={onSelectAllTopics}
              >
                <CheckCircle2 size={15} /> Select All ({pdf.topics.length} Topics)
              </button>
              <button
                type="button"
                className="topic-primary-cta"
                disabled={selectedTopicIds.length === 0}
                onClick={onStartStudyingSelected}
                data-testid="launch-study-notes-btn"
              >
                <BookOpen size={16} />
                <span>
                  Open Selected in Study Engine ({selectedTopicIds.length}{" "}
                  selected)
                </span>
              </button>
            </div>
          </div>

          {scanningPdf ? (
            <div className="topic-scanning-card">
              <div className="ai-spinner large" />
              <h3>Scanning PDF &amp; Extracting NCERT Topics + Figures…</h3>
              <p>
                Analyzing headings, verbatim paragraphs, and diagram bounding
                boxes…
              </p>
            </div>
          ) : (
            <div className="topic-cards-grid">
              {pdf.topics.map((topic) => {
                const isSelected = selectedTopicIds.includes(topic.id);
                return (
                  <div
                    key={topic.id}
                    onClick={() => {
                      if (onSelectSingleTopic) {
                        onSelectSingleTopic(topic.id);
                      } else {
                        onToggleTopicSelection(topic.id);
                      }
                    }}
                    className={`topic-map-card ${
                      isSelected ? "selected" : ""
                    }`}
                    data-testid={`topic-card-${topic.id}`}
                  >
                    <div className="topic-card-top">
                      <span className="topic-num-badge">{topic.number}</span>
                      <span className="topic-page-pill">
                        Page{" "}
                        {topic.pageStart === topic.pageEnd
                          ? topic.pageStart
                          : `${topic.pageStart}–${topic.pageEnd}`}
                      </span>
                      {topic.figures.length > 0 && (
                        <span className="topic-fig-pill">
                          <ImageIcon size={12} /> {topic.figures.length} Figure
                          {topic.figures.length > 1 ? "s" : ""}
                        </span>
                      )}
                      <button
                        type="button"
                        onClick={(e) => {
                          e.stopPropagation();
                          onToggleTopicSelection(topic.id);
                        }}
                        className={`topic-check-circle ${
                          isSelected ? "checked" : ""
                        }`}
                        title="Toggle multi-topic selection"
                      >
                        {isSelected && <Check size={14} />}
                      </button>
                    </div>

                    <h3 className="topic-card-title">{topic.title}</h3>
                    <p className="topic-card-summary">{topic.summary}</p>

                    <div className="topic-subtopics-wrap">
                      {topic.subtopics.map((sub, i) => (
                        <span key={i} className="topic-sub-chip">
                          {sub}
                        </span>
                      ))}
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </main>
      ) : (
        /* MODE 2: Full-Screen Rich HTML-Formatted NCERT Study Notes (Starts immediately at top of viewport) */
        <main className="ncert-notes-shell" data-testid="ncert-notes-canvas">
          {compiledSections.map((section, secIdx) => (
            <section
              key={section.id}
              className={`ncert-section ${
                secIdx === compiledSections.length - 1 ? "last-section" : ""
              }`}
              aria-labelledby={`title-${section.id}`}
            >
              <div className="ncert-section-title">
                <span className="ncert-section-no">{section.sectionNo}</span>
                <h2 id={`title-${section.id}`}>{section.title}</h2>
              </div>

              <div className="ncert-cards-stack">
                {section.cards.map((card) => renderNcertCard(section.id, card))}
              </div>
            </section>
          ))}
        </main>
      )}

      {/* Lightbox Modal for Zooming Extracted Figures */}
      {enlargedFigure && (
        <div
          className="figure-lightbox-backdrop"
          onClick={() => setEnlargedFigure(null)}
        >
          <div
            className="figure-lightbox-modal"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="figure-lightbox-header">
              <div>
                <strong>{enlargedFigure.label}</strong> · Extracted from PDF
                Page {enlargedFigure.pageNumber}
              </div>
              <button
                type="button"
                className="study-minimal-btn"
                onClick={() => setEnlargedFigure(null)}
              >
                <X size={16} /> Close
              </button>
            </div>
            <div className="figure-lightbox-body">
              <img
                src={enlargedFigure.svgDataUrl}
                alt={enlargedFigure.caption}
              />
            </div>
            <p className="figure-lightbox-caption">{enlargedFigure.caption}</p>
          </div>
        </div>
      )}
    </div>
  );
};
