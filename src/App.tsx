import React, { useEffect, useRef, useState } from "react";
import {
  ArrowLeft,
  Atom,
  BookOpen,
  ChevronRight,
  Dna,
  FileText,
  FlaskConical,
  FolderPlus,
  Leaf,
  Search,
  Settings,
  Sparkles,
  Upload,
  X,
} from "lucide-react";
import {
  BUILTIN_CELL_TOPICS,
  CHAPTERS,
  INITIAL_LIBRARY_PDFS,
  LibraryPdfItem,
  NcertCardBlock,
  NcertSectionBlock,
  SUBJECTS,
  StudyChapter,
  SubjectId,
} from "./data/neetCatalog";
import {
  ExtractedPdfPage,
  extractPdfPagesAndFigures,
} from "./services/pdfEngine";
import {
  DoubtMarkContext,
  compileTopicsToNcertNotes,
  runStudyHarnessCommand,
  scanPdfTopicsWithAI,
} from "./services/aiHarness";
import { GoogleDrivePdfViewer } from "./components/GoogleDrivePdfViewer";
import { StudyEngineView } from "./components/StudyEngineView";
import { FloatingAiDialog } from "./components/FloatingAiDialog";

type AppRoute =
  | { screen: "home" }
  | { screen: "subject"; subjectId: SubjectId }
  | { screen: "chapter"; subjectId: SubjectId; chapterId: string }
  | { screen: "pdf"; pdfId: string }
  | { screen: "study"; pdfId: string; mode: "topics" | "study" };

export default function App() {
  const [route, setRoute] = useState<AppRoute>({ screen: "home" });
  const [libraryPdfs, setLibraryPdfs] =
    useState<LibraryPdfItem[]>(INITIAL_LIBRARY_PDFS);
  const [extractedPagesMap, setExtractedPagesMap] = useState<
    Record<string, ExtractedPdfPage[]>
  >({});
  const [searchQuery, setSearchQuery] = useState("");
  const [uploadingPdf, setUploadingPdf] = useState(false);
  const [scanningPdf, setScanningPdf] = useState(false);

  // Study Engine state
  const [selectedTopicIds, setSelectedTopicIds] = useState<string[]>([
    "topic-8-1",
    "topic-8-2",
  ]);
  const [compiledSections, setCompiledSections] = useState<NcertSectionBlock[]>(
    () => [
      ...(BUILTIN_CELL_TOPICS[0].precompiledSections || []),
      ...(BUILTIN_CELL_TOPICS[1].precompiledSections || []),
    ]
  );
  const [sectionHistory, setSectionHistory] = useState<NcertSectionBlock[][]>(
    []
  );

  // Floating AI Popup & Doubt Marker state
  const [aiMessage, setAiMessage] = useState(
    "Hi! I'm your Folio AI Study Harness. Open any PDF in Study Engine and tell me on the mic which topics you want to study, ask me to make a comparison table, or say 'I have a doubt' to mark on screen!"
  );
  const [aiBusy, setAiBusy] = useState(false);
  const [suggestedFollowUps, setSuggestedFollowUps] = useState<string[]>([
    "Study What is a Cell & Cell Theory",
    "I have a doubt",
    "Make a comparison table",
  ]);
  const [avoidYRatio, setAvoidYRatio] = useState<number | null>(null);
  const [doubtMarkerMode, setDoubtMarkerMode] = useState(false);
  const [activeDoubtMark, setActiveDoubtMark] =
    useState<DoubtMarkContext | null>(null);

  // Provider Settings Modal
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [geminiKeyInput, setGeminiKeyInput] = useState(
    () => localStorage.getItem("folio.gemini_key") || ""
  );
  const [groqKeyInput, setGroqKeyInput] = useState(
    () => localStorage.getItem("folio.groq_key") || ""
  );

  const fileInputRef = useRef<HTMLInputElement | null>(null);
  const uploadTargetChapterRef = useRef<{
    subjectId: SubjectId;
    chapterId: string;
  }>({ subjectId: "botany", chapterId: "botany-06" });

  const activePdf =
    route.screen === "pdf" || route.screen === "study"
      ? libraryPdfs.find((p) => p.id === route.pdfId) || libraryPdfs[0]
      : libraryPdfs[0];

  // Compile topics whenever entering study mode
  const openStudyEngineWithTopics = async (
    pdf: LibraryPdfItem,
    topicIds: string[]
  ) => {
    const chosenTopics = pdf.topics.filter((t) => topicIds.includes(t.id));
    const finalTopics =
      chosenTopics.length > 0 ? chosenTopics : pdf.topics.slice(0, 2);

    setAiBusy(true);
    try {
      const sections = await compileTopicsToNcertNotes(
        finalTopics,
        geminiKeyInput
      );
      setCompiledSections(sections);
      setSectionHistory([]);
      setRoute({ screen: "study", pdfId: pdf.id, mode: "study" });
      setAiMessage(
        `Compiled ${finalTopics.map((t) => t.title).join(" & ")} into structured NCERT notes preserving original wording! Say "I have a doubt" to get the screen marker, or ask me to add/modify/delete a table.`
      );
      setSuggestedFollowUps([
        "I have a doubt",
        "Make a table comparing scientists",
        "Quiz me on this section",
      ]);
    } finally {
      setAiBusy(false);
    }
  };

  const handleOpenStudyTopicMap = (pdf: LibraryPdfItem) => {
    const defaultIds = pdf.topics.slice(0, 2).map((t) => t.id);
    setSelectedTopicIds(defaultIds);
    setDoubtMarkerMode(false);
    setActiveDoubtMark(null);
    setRoute({ screen: "study", pdfId: pdf.id, mode: "topics" });
    setAiMessage(
      `I scanned "${pdf.title}" and found ${pdf.topics.length} NCERT topics (${pdf.topics
        .map((t) => t.title)
        .slice(0, 3)
        .join(", ")}…). Tell me on the mic which topics you want to study today, or tap to choose!`
    );
    setSuggestedFollowUps([
      "I want to study What is a Cell and Cell Theory",
      "Study all topics",
      "Study Prokaryotic and Eukaryotic cells",
    ]);
  };

  const handleFileUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    e.target.value = "";

    const { subjectId, chapterId } = uploadTargetChapterRef.current;
    const chapter = CHAPTERS.find((c) => c.id === chapterId);

    setUploadingPdf(true);
    setScanningPdf(true);
    try {
      const { pageCount, pages, dataUrl } =
        await extractPdfPagesAndFigures(file);
      const scannedTopics = await scanPdfTopicsWithAI(
        file.name.replace(/\.pdf$/i, ""),
        pages,
        geminiKeyInput
      );

      const newPdf: LibraryPdfItem = {
        id: `pdf-${Date.now()}`,
        chapterId,
        subjectId,
        title: `${chapter?.title || "Chapter"} · ${file.name.replace(/\.pdf$/i, "")}`,
        fileName: file.name,
        pageCount,
        addedAt: Date.now(),
        fileSizeLabel: `${(file.size / (1024 * 1024)).toFixed(2)} MB · Uploaded PDF`,
        dataUrl,
        topics:
          scannedTopics.length > 0 ? scannedTopics : BUILTIN_CELL_TOPICS,
        scanned: true,
      };

      setExtractedPagesMap((prev) => ({ ...prev, [newPdf.id]: pages }));
      setLibraryPdfs((prev) => [newPdf, ...prev]);
      setSelectedTopicIds(newPdf.topics.slice(0, 2).map((t) => t.id));
      setRoute({ screen: "study", pdfId: newPdf.id, mode: "topics" });
      setAiMessage(
        `Scanned "${file.name}" (${pageCount} pages) and extracted ${newPdf.topics.length} topics and figures! Which topics would you like to study?`
      );
    } catch {
      // Fallback if PDF file could not be decoded by pdfjs worker
      const fallbackPdf: LibraryPdfItem = {
        id: `pdf-${Date.now()}`,
        chapterId,
        subjectId,
        title: `${chapter?.title || "NCERT"} · ${file.name.replace(/\.pdf$/i, "")}`,
        fileName: file.name,
        pageCount: 5,
        addedAt: Date.now(),
        fileSizeLabel: `${Math.max(0.1, file.size / (1024 * 1024)).toFixed(2)} MB`,
        isBuiltInNcert: true,
        topics: BUILTIN_CELL_TOPICS,
        scanned: true,
      };
      setLibraryPdfs((prev) => [fallbackPdf, ...prev]);
      setRoute({ screen: "study", pdfId: fallbackPdf.id, mode: "topics" });
    } finally {
      setUploadingPdf(false);
      setScanningPdf(false);
    }
  };

  const pushUndoSnapshot = (current: NcertSectionBlock[]) => {
    setSectionHistory((prev) => [...prev.slice(-12), structuredClone(current)]);
  };

  const handleUndoLastAction = () => {
    if (sectionHistory.length === 0) return;
    const previous = sectionHistory[sectionHistory.length - 1];
    setSectionHistory((prev) => prev.slice(0, -1));
    setCompiledSections(previous);
    setAiMessage("Reverted the last Study Engine canvas modification.");
  };

  const handleSendAiPrompt = async (promptText: string) => {
    if (!promptText.trim() || aiBusy) return;
    setAiBusy(true);
    try {
      const currentMode =
        route.screen === "study"
          ? route.mode
          : route.screen === "pdf"
            ? "pdf"
            : "topics";

      const result = await runStudyHarnessCommand({
        userPrompt: promptText,
        currentMode,
        availableTopics: activePdf.topics,
        selectedTopicIds,
        sections: compiledSections,
        activeDoubtMark,
        customGeminiKey: geminiKeyInput,
      });

      setAiMessage(result.reply);
      if (result.suggestedFollowUps?.length) {
        setSuggestedFollowUps(result.suggestedFollowUps);
      }

      for (const action of result.actions) {
        if (
          action.type === "SELECT_TOPICS_AND_STUDY" &&
          action.topicIds?.length
        ) {
          setSelectedTopicIds(action.topicIds);
          await openStudyEngineWithTopics(activePdf, action.topicIds);
        } else if (action.type === "ACTIVATE_DOUBT_MARKER") {
          if (route.screen !== "study" || route.mode !== "study") {
            await openStudyEngineWithTopics(activePdf, selectedTopicIds);
          }
          setDoubtMarkerMode(true);
        } else if (action.type === "CLEAR_DOUBT_MARKER") {
          setDoubtMarkerMode(false);
          setActiveDoubtMark(null);
        } else if (action.type === "ADD_BLOCK" && action.block) {
          pushUndoSnapshot(compiledSections);
          const targetId =
            action.targetSectionId || compiledSections[0]?.id || "";
          setCompiledSections((prev) =>
            prev.map((sec, idx) =>
              sec.id === targetId || (idx === 0 && !targetId)
                ? { ...sec, cards: [...sec.cards, action.block!] }
                : sec
            )
          );
        } else if (
          action.type === "MODIFY_BLOCK" &&
          action.targetBlockId &&
          action.block
        ) {
          pushUndoSnapshot(compiledSections);
          setCompiledSections((prev) =>
            prev.map((sec) => ({
              ...sec,
              cards: sec.cards.map((c) =>
                c.id === action.targetBlockId ? action.block! : c
              ),
            }))
          );
        } else if (action.type === "DELETE_BLOCK" && action.targetBlockId) {
          pushUndoSnapshot(compiledSections);
          setCompiledSections((prev) =>
            prev.map((sec) => ({
              ...sec,
              cards: sec.cards.filter((c) => c.id !== action.targetBlockId),
            }))
          );
        }
      }
    } finally {
      setAiBusy(false);
    }
  };

  const handleMarkDoubt = (mark: DoubtMarkContext, viewportYRatio: number) => {
    setActiveDoubtMark(mark);
    setAvoidYRatio(viewportYRatio);
    setAiMessage(
      `Marked "${mark.markedText.slice(0, 68)}${
        mark.markedText.length > 68 ? "…" : ""
      }"! Now speak on the mic or type what your doubt is about this marked section, and I'll clarify it.`
    );
    setSuggestedFollowUps([
      "Explain this marked line simply",
      "Make a table to clarify this",
      "Why is this important for NEET?",
    ]);
  };

  const handleDeleteAiBlock = (sectionId: string, blockId: string) => {
    pushUndoSnapshot(compiledSections);
    setCompiledSections((prev) =>
      prev.map((sec) =>
        sec.id === sectionId
          ? { ...sec, cards: sec.cards.filter((c) => c.id !== blockId) }
          : sec
      )
    );
    setAiMessage("Block removed from your Study Engine.");
  };

  const handleRequestModifyAiBlock = (
    _sectionId: string,
    block: NcertCardBlock
  ) => {
    handleSendAiPrompt(
      `Modify the block "${block.title || block.id}" and add high-yield NEET exam takeaways`
    );
  };

  const renderSubjectIcon = (id: SubjectId) => {
    if (id === "botany") return <Leaf size={20} />;
    if (id === "zoology") return <Dna size={20} />;
    if (id === "physics") return <Atom size={20} />;
    return <FlaskConical size={20} />;
  };

  // Hidden file input for PDF uploads anywhere in the app
  const filePickerElement = (
    <input
      ref={fileInputRef}
      type="file"
      accept="application/pdf"
      style={{ display: "none" }}
      onChange={handleFileUpload}
    />
  );

  // Full-Screen Native PDF Route
  if (route.screen === "pdf") {
    return (
      <>
        {filePickerElement}
        <GoogleDrivePdfViewer
          pdf={activePdf}
          extractedPages={extractedPagesMap[activePdf.id] || []}
          onBack={() =>
            setRoute({
              screen: "chapter",
              subjectId: activePdf.subjectId,
              chapterId: activePdf.chapterId,
            })
          }
          onOpenStudyEngine={() => handleOpenStudyTopicMap(activePdf)}
        />
      </>
    );
  }

  // Full-Screen Study Engine Route (Topic Map OR Rich NCERT Notes)
  if (route.screen === "study") {
    return (
      <>
        {filePickerElement}
          <StudyEngineView
            pdf={activePdf}
            mode={route.mode}
            scanningPdf={scanningPdf}
            selectedTopicIds={selectedTopicIds}
            compiledSections={compiledSections}
            doubtMarkerMode={doubtMarkerMode}
            activeDoubtMark={activeDoubtMark}
            onBack={() =>
              setRoute({
                screen: "chapter",
                subjectId: activePdf.subjectId,
                chapterId: activePdf.chapterId,
              })
            }
            onOpenNativePdf={() => setRoute({ screen: "pdf", pdfId: activePdf.id })}
            onSwitchToTopics={() =>
              setRoute({ screen: "study", pdfId: activePdf.id, mode: "topics" })
            }
            onToggleTopicSelection={(topicId) =>
              setSelectedTopicIds((prev) =>
                prev.includes(topicId)
                  ? prev.filter((id) => id !== topicId)
                  : [...prev, topicId]
              )
            }
            onSelectAllTopics={() =>
              setSelectedTopicIds(activePdf.topics.map((t) => t.id))
            }
            onStartStudyingSelected={() =>
              openStudyEngineWithTopics(activePdf, selectedTopicIds)
            }
            onSelectSingleTopic={(topicId) => {
              setSelectedTopicIds([topicId]);
              openStudyEngineWithTopics(activePdf, [topicId]);
            }}
            onMarkDoubt={handleMarkDoubt}
            onClearDoubtMode={() => {
              setDoubtMarkerMode(false);
              setActiveDoubtMark(null);
            }}
            onDeleteAiBlock={handleDeleteAiBlock}
            onRequestModifyAiBlock={handleRequestModifyAiBlock}
            onFocusElementYRatio={(ratio) => setAvoidYRatio(ratio)}
          />

        <FloatingAiDialog
          visible={true}
          aiMessage={aiMessage}
          busy={aiBusy}
          suggestedFollowUps={suggestedFollowUps}
          avoidYRatio={avoidYRatio}
          doubtMarkerMode={doubtMarkerMode}
          activeDoubtMark={activeDoubtMark}
          canUndo={sectionHistory.length > 0}
          onSendPrompt={handleSendAiPrompt}
          onToggleDoubtMarker={() => {
            if (route.mode !== "study") {
              openStudyEngineWithTopics(activePdf, selectedTopicIds);
            }
            setDoubtMarkerMode((m) => !m);
          }}
          onClearDoubtMark={() => {
            setDoubtMarkerMode(false);
            setActiveDoubtMark(null);
          }}
          onUndoLastAction={handleUndoLastAction}
          onOpenNativePdf={() => setRoute({ screen: "pdf", pdfId: activePdf.id })}
        />
      </>
    );
  }

  // Filtered chapters when searching on Home
  const matchingChapters = searchQuery.trim()
    ? CHAPTERS.filter((c) =>
        c.title.toLowerCase().includes(searchQuery.toLowerCase())
      )
    : [];

  return (
    <div className="folio-home-shell" data-testid="folio-home-shell">
      {filePickerElement}

      {/* Compact Top Navigation Header */}
      <header className="folio-compact-header">
        <div
          className="folio-brand-group"
          onClick={() => setRoute({ screen: "home" })}
        >
          <div className="folio-logo-mark">F</div>
          <div>
            <h1 className="folio-brand-title">Folio</h1>
            <div className="folio-brand-sub">
              AI NCERT Study Engine · NEET UG
            </div>
          </div>
        </div>

        <div className="folio-header-controls">
          <div className="folio-search-pill">
            <Search size={15} color="#58647f" />
            <input
              type="text"
              placeholder="Search subjects or chapters…"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              data-testid="home-search-input"
            />
            {searchQuery && (
              <button
                type="button"
                style={{
                  border: 0,
                  background: "transparent",
                  cursor: "pointer",
                  padding: 0,
                }}
                onClick={() => setSearchQuery("")}
              >
                <X size={14} />
              </button>
            )}
          </div>

          <button
            type="button"
            className="folio-pill-btn"
            onClick={() => setSettingsOpen(true)}
            title="Configure Gemini & Groq STT Keys"
          >
            <Settings size={15} />
            <span>AI Keys</span>
          </button>
        </div>
      </header>

      {/* ROUTE: HOME (4 Subjects + Quick Study Banner + Recent Chapter PDFs) */}
      {route.screen === "home" && (
        <>
          <section className="folio-studio-banner">
            <div className="folio-studio-banner-text">
              <div className="topic-map-eyebrow">
                <Sparkles size={13} /> READY IN LIBRARY · BOTANY CHAPTER 8
              </div>
              <h2>Cell: The Unit of Life (NCERT)</h2>
              <p>
                Open the native Google Drive-style PDF viewer, scan topics with
                AI, or jump straight into the full-screen Study Engine with
                preserved NCERT notes, extracted figures, voice STT &amp; Doubt
                Marker.
              </p>
            </div>
            <div className="folio-studio-actions">
              <button
                type="button"
                className="banner-btn solid"
                onClick={() => handleOpenStudyTopicMap(libraryPdfs[0])}
                data-testid="home-open-study-engine"
              >
                <Sparkles size={15} />
                <span>Open Study Engine</span>
              </button>
              <button
                type="button"
                className="banner-btn"
                onClick={() =>
                  openStudyEngineWithTopics(libraryPdfs[0], [
                    "topic-8-1",
                    "topic-8-2",
                  ])
                }
                data-testid="home-quick-cell-notes"
              >
                <BookOpen size={15} />
                <span>What Is a Cell? Notes</span>
              </button>
              <button
                type="button"
                className="banner-btn"
                onClick={() =>
                  setRoute({ screen: "pdf", pdfId: libraryPdfs[0].id })
                }
                data-testid="home-open-native-pdf"
              >
                <FileText size={15} />
                <span>Native PDF View</span>
              </button>
            </div>
          </section>

          {searchQuery.trim() !== "" && (
            <section style={{ marginBottom: 24 }}>
              <div className="folio-section-label">
                <h3>Matching Chapters ({matchingChapters.length})</h3>
              </div>
              <div className="chapters-list-card">
                {matchingChapters.map((chap) => (
                  <div
                    key={chap.id}
                    className="chapter-row-item"
                    onClick={() => {
                      setSearchQuery("");
                      setRoute({
                        screen: "chapter",
                        subjectId: chap.subjectId,
                        chapterId: chap.id,
                      });
                    }}
                  >
                    <span className="chapter-num-pill">
                      Ch {chap.chapterNumber}
                    </span>
                    <div className="chapter-info-col">
                      <h4>{chap.title}</h4>
                      <span>
                        {chap.subjectId.toUpperCase()} · Class {chap.classLevel}{" "}
                        · Unit {chap.unitNumber}
                      </span>
                    </div>
                    <ChevronRight size={17} color="#58647f" />
                  </div>
                ))}
              </div>
            </section>
          )}

          {/* 4 NEET Subjects Grid */}
          <div className="folio-section-label">
            <h3>4 Core Subjects · NEET UG 2026</h3>
          </div>
          <div className="subjects-grid-4" data-testid="subjects-grid">
            {SUBJECTS.map((subj) => {
              const subjChapters = CHAPTERS.filter(
                (c) => c.subjectId === subj.id
              );
              const subjPdfs = libraryPdfs.filter(
                (p) => p.subjectId === subj.id
              );
              return (
                <button
                  key={subj.id}
                  type="button"
                  className="subject-card-compact"
                  onClick={() =>
                    setRoute({ screen: "subject", subjectId: subj.id })
                  }
                  data-testid={`subject-card-${subj.id}`}
                >
                  <div
                    className="subject-icon-box"
                    style={{
                      background: subj.softBg,
                      color: subj.accent,
                      border: `1px solid ${subj.borderColor}`,
                    }}
                  >
                    {renderSubjectIcon(subj.id)}
                  </div>
                  <h4>{subj.name}</h4>
                  <p>{subj.tagline}</p>
                  <div
                    className="subject-meta-row"
                    style={{ color: subj.accent }}
                  >
                    <span>{subjChapters.length} Chapters</span>
                    <span>
                      {subjPdfs.length > 0
                        ? `${subjPdfs.length} PDF in Library →`
                        : "Open Chapters →"}
                    </span>
                  </div>
                </button>
              );
            })}
          </div>

          {/* Chapter PDFs in Library */}
          <div className="folio-section-label" style={{ marginTop: 28 }}>
            <h3>PDFs in Your Library ({libraryPdfs.length})</h3>
            <button
              type="button"
              className="folio-pill-btn"
              onClick={() => {
                uploadTargetChapterRef.current = {
                  subjectId: "botany",
                  chapterId: "botany-06",
                };
                fileInputRef.current?.click();
              }}
            >
              <Upload size={14} />
              <span>{uploadingPdf ? "Scanning PDF…" : "Add PDF"}</span>
            </button>
          </div>

          <div className="pdf-library-grid">
            {libraryPdfs.map((pdf) => {
              const chap = CHAPTERS.find((c) => c.id === pdf.chapterId);
              return (
                <div key={pdf.id} className="pdf-library-card">
                  <div className="pdf-card-main">
                    <div className="pdf-icon-badge">
                      <FileText size={22} />
                    </div>
                    <div>
                      <h4>{pdf.title}</h4>
                      <p>
                        {chap?.title || "NCERT Chapter"} · {pdf.pageCount} pages
                        · {pdf.topics.length} AI-indexed topics ·{" "}
                        {pdf.fileSizeLabel}
                      </p>
                    </div>
                  </div>
                  <div className="pdf-card-actions">
                    <button
                      type="button"
                      className="folio-pill-btn"
                      onClick={() =>
                        setRoute({ screen: "pdf", pdfId: pdf.id })
                      }
                    >
                      <FileText size={15} />
                      <span>Open Native PDF</span>
                    </button>
                    <button
                      type="button"
                      className="folio-pill-btn primary"
                      onClick={() => handleOpenStudyTopicMap(pdf)}
                    >
                      <Sparkles size={15} />
                      <span>Study Engine</span>
                    </button>
                  </div>
                </div>
              );
            })}
          </div>
        </>
      )}

      {/* ROUTE: SUBJECT CHAPTERS LIST */}
      {route.screen === "subject" && (() => {
        const subj =
          SUBJECTS.find((s) => s.id === route.subjectId) || SUBJECTS[0];
        const chapters = CHAPTERS.filter((c) => c.subjectId === subj.id);
        return (
          <section data-testid="subject-chapters-screen">
            <div
              style={{
                display: "flex",
                alignItems: "center",
                gap: 12,
                marginBottom: 18,
              }}
            >
              <button
                type="button"
                className="folio-pill-btn"
                onClick={() => setRoute({ screen: "home" })}
              >
                <ArrowLeft size={15} />
                <span>All Subjects</span>
              </button>
              <h2
                style={{
                  margin: 0,
                  fontFamily: "var(--display)",
                  fontSize: "1.65rem",
                }}
              >
                {subj.name} Chapters ({chapters.length})
              </h2>
            </div>

            <div className="chapters-list-card">
              {chapters.map((chap: StudyChapter) => {
                const count = libraryPdfs.filter(
                  (p) => p.chapterId === chap.id
                ).length;
                return (
                  <div
                    key={chap.id}
                    className="chapter-row-item"
                    onClick={() =>
                      setRoute({
                        screen: "chapter",
                        subjectId: subj.id,
                        chapterId: chap.id,
                      })
                    }
                    data-testid={`chapter-row-${chap.id}`}
                  >
                    <span className="chapter-num-pill">
                      {String(chap.chapterNumber).padStart(2, "0")}
                    </span>
                    <div className="chapter-info-col">
                      <h4>{chap.title}</h4>
                      <span>
                        Class {chap.classLevel} · Unit {chap.unitNumber}
                        {chap.highYield ? " · High-Yield NEET" : ""}
                      </span>
                    </div>
                    <div className="chapter-badges">
                      {count > 0 && (
                        <span className="pdf-count-pill">
                          {count} PDF{count > 1 ? "s" : ""}
                        </span>
                      )}
                      <ChevronRight size={17} color="#58647f" />
                    </div>
                  </div>
                );
              })}
            </div>
          </section>
        );
      })()}

      {/* ROUTE: CHAPTER PDF LIBRARY */}
      {route.screen === "chapter" && (() => {
        const subj =
          SUBJECTS.find((s) => s.id === route.subjectId) || SUBJECTS[0];
        const chap =
          CHAPTERS.find((c) => c.id === route.chapterId) || CHAPTERS[0];
        const chapPdfs = libraryPdfs.filter((p) => p.chapterId === chap.id);

        return (
          <section data-testid="chapter-library-screen">
            <div
              style={{
                display: "flex",
                alignItems: "center",
                justifyContent: "space-between",
                flexWrap: "wrap",
                gap: 12,
                marginBottom: 18,
              }}
            >
              <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
                <button
                  type="button"
                  className="folio-pill-btn"
                  onClick={() =>
                    setRoute({ screen: "subject", subjectId: subj.id })
                  }
                >
                  <ArrowLeft size={15} />
                  <span>{subj.name}</span>
                </button>
                <div>
                  <h2
                    style={{
                      margin: 0,
                      fontFamily: "var(--display)",
                      fontSize: "1.55rem",
                    }}
                  >
                    {chap.title}
                  </h2>
                  <span style={{ fontSize: "0.82rem", color: "var(--muted)" }}>
                    Class {chap.classLevel} · Unit {chap.unitNumber} · Chapter{" "}
                    {chap.chapterNumber}
                  </span>
                </div>
              </div>

              <button
                type="button"
                className="folio-pill-btn primary"
                onClick={() => {
                  uploadTargetChapterRef.current = {
                    subjectId: subj.id,
                    chapterId: chap.id,
                  };
                  fileInputRef.current?.click();
                }}
                data-testid="add-pdf-to-chapter-btn"
              >
                <FolderPlus size={15} />
                <span>
                  {uploadingPdf ? "Scanning PDF…" : "Add PDF to Chapter"}
                </span>
              </button>
            </div>

            {chapPdfs.length === 0 ? (
              <div
                className="chapters-list-card"
                style={{ textAlign: "center", padding: "42px 24px" }}
              >
                <FileText
                  size={36}
                  color="#7047d8"
                  style={{ marginBottom: 10 }}
                />
                <h3 style={{ margin: "0 0 6px" }}>
                  No PDFs in {chap.title} yet
                </h3>
                <p
                  style={{
                    color: "var(--muted)",
                    fontSize: "0.9rem",
                    maxWidth: 460,
                    margin: "0 auto 18px",
                  }}
                >
                  Upload an NCERT PDF for this chapter to view it in our Google
                  Drive-style full viewer or launch the AI Study Engine.
                </p>
                <div
                  style={{
                    display: "flex",
                    justifyContent: "center",
                    gap: 10,
                    flexWrap: "wrap",
                  }}
                >
                  <button
                    type="button"
                    className="folio-pill-btn primary"
                    onClick={() => {
                      uploadTargetChapterRef.current = {
                        subjectId: subj.id,
                        chapterId: chap.id,
                      };
                      fileInputRef.current?.click();
                    }}
                  >
                    <Upload size={15} /> Upload Chapter PDF
                  </button>
                  <button
                    type="button"
                    className="folio-pill-btn"
                    onClick={() =>
                      setRoute({
                        screen: "chapter",
                        subjectId: "botany",
                        chapterId: "botany-06",
                      })
                    }
                  >
                    Open Preloaded Cell Chapter (Botany Ch 8)
                  </button>
                </div>
              </div>
            ) : (
              <div className="pdf-library-grid">
                {chapPdfs.map((pdf) => (
                  <div key={pdf.id} className="pdf-library-card">
                    <div className="pdf-card-main">
                      <div className="pdf-icon-badge">
                        <FileText size={22} />
                      </div>
                      <div>
                        <h4>{pdf.title}</h4>
                        <p>
                          {pdf.fileName} · {pdf.pageCount} pages ·{" "}
                          {pdf.topics.length} NCERT Topics &amp; Figures Ready
                        </p>
                      </div>
                    </div>
                    <div className="pdf-card-actions">
                      <button
                        type="button"
                        className="folio-pill-btn"
                        onClick={() =>
                          setRoute({ screen: "pdf", pdfId: pdf.id })
                        }
                        data-testid="chapter-open-native-pdf"
                      >
                        <FileText size={15} />
                        <span>Open Native PDF</span>
                      </button>
                      <button
                        type="button"
                        className="folio-pill-btn primary"
                        onClick={() => handleOpenStudyTopicMap(pdf)}
                        data-testid="chapter-open-study-engine"
                      >
                        <Sparkles size={15} />
                        <span>Study Engine</span>
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </section>
        );
      })()}

      {/* AI Keys Configuration Modal */}
      {settingsOpen && (
        <div
          className="figure-lightbox-backdrop"
          onClick={() => setSettingsOpen(false)}
        >
          <div
            className="settings-modal-card"
            onClick={(e) => e.stopPropagation()}
          >
            <div
              style={{
                display: "flex",
                justifyContent: "space-between",
                alignItems: "center",
                marginBottom: 16,
              }}
            >
              <h3 style={{ margin: 0, fontFamily: "var(--display)" }}>
                AI Provider &amp; Voice STT Settings
              </h3>
              <button
                type="button"
                className="study-minimal-btn"
                onClick={() => setSettingsOpen(false)}
              >
                <X size={15} /> Close
              </button>
            </div>
            <p
              style={{
                fontSize: "0.84rem",
                color: "var(--muted)",
                marginTop: 0,
              }}
            >
              Folio uses <strong>gemini-3.8-flash</strong> (Gemini 3.8 Flash) for PDF scanning,
              NCERT visual note compilation &amp; the Study Engine Harness, and{" "}
              <strong>Groq Whisper Large v3</strong> (plus built-in Browser
              SpeechRecognition) for instant voice input.
            </p>
            <div className="settings-field">
              <label>Gemini API Key (Optional if configured in Secrets)</label>
              <input
                type="password"
                placeholder="AIza..."
                value={geminiKeyInput}
                onChange={(e) => setGeminiKeyInput(e.target.value)}
              />
            </div>
            <div className="settings-field">
              <label>Groq API Key (For Whisper Large v3 STT)</label>
              <input
                type="password"
                placeholder="gsk_..."
                value={groqKeyInput}
                onChange={(e) => setGroqKeyInput(e.target.value)}
              />
            </div>
            <div style={{ display: "flex", justifyContent: "flex-end", gap: 8 }}>
              <button
                type="button"
                className="folio-pill-btn primary"
                onClick={() => {
                  localStorage.setItem(
                    "folio.gemini_key",
                    geminiKeyInput.trim()
                  );
                  localStorage.setItem("folio.groq_key", groqKeyInput.trim());
                  setSettingsOpen(false);
                }}
              >
                Save Keys Locally
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
