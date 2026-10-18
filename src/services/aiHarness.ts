import { GoogleGenAI } from "@google/genai";
import {
  BUILTIN_CELL_TOPICS,
  ExtractedFigure,
  NcertCardBlock,
  NcertSectionBlock,
  TimelineItem,
  NcertTopic,
} from "../data/neetCatalog";
import { ExtractedPdfPage } from "./pdfEngine";

export interface HarnessAction {
  type:
    | "SELECT_TOPICS_AND_STUDY"
    | "ACTIVATE_DOUBT_MARKER"
    | "CLEAR_DOUBT_MARKER"
    | "ADD_BLOCK"
    | "MODIFY_BLOCK"
    | "DELETE_BLOCK"
    | "SWITCH_VIEW";
  topicIds?: string[];
  targetSectionId?: string;
  targetBlockId?: string;
  block?: NcertCardBlock;
  view?: "home" | "pdf" | "topics" | "study";
}

export interface HarnessResponse {
  reply: string;
  actions: HarnessAction[];
  suggestedFollowUps?: string[];
}

export interface DoubtMarkContext {
  id: string;
  sectionId: string;
  blockId?: string;
  markedText: string;
  rect?: { x: number; y: number; width: number; height: number };
  pageY?: number;
}

export const GEMINI_MODEL_CANDIDATES = [
  "gemini-3.8-flash",
  "gemini-3-flash-preview",
  "gemini-2.5-flash-lite",
  "gemini-2.5-flash",
];

async function generateContentWithFallback(
  ai: GoogleGenAI,
  contents: any,
  config?: Record<string, any>
) {
  let lastErr: unknown;
  for (const model of GEMINI_MODEL_CANDIDATES) {
    try {
      return await ai.models.generateContent({
        model,
        contents,
        config,
      });
    } catch (err) {
      lastErr = err;
    }
  }
  throw lastErr;
}

export function getGeminiKey(customKey?: string): string {
  return (
    customKey?.trim() ||
    localStorage.getItem("folio.gemini_key")?.trim() ||
    (typeof process !== "undefined" && process.env?.GEMINI_API_KEY) ||
    ""
  );
}

export function getGroqKey(customKey?: string): string {
  return (
    customKey?.trim() ||
    localStorage.getItem("folio.groq_key")?.trim() ||
    (typeof process !== "undefined" && process.env?.GROQ_API_KEY) ||
    ""
  );
}

export async function transcribeAudioWithGroq(
  audioBlob: Blob,
  groqKey: string
): Promise<string> {
  const form = new FormData();
  form.append("file", audioBlob, "speech.webm");
  form.append("model", "whisper-large-v3-turbo");
  form.append("language", "en");
  form.append(
    "prompt",
    "NCERT Biology, Physics, Chemistry, Botany, Zoology, Cell Theory, Centrosome, Centrioles, Cilia, Flagella, Mitochondria, Plastids, Ribosomes, Nucleus, Schleiden, Schwann, Virchow, Omnis cellula-e cellula, Leeuwenhoek, Robert Brown, Prokaryotic, Eukaryotic, Doubt, Table."
  );

  const response = await fetch(
    "https://api.groq.com/openai/v1/audio/transcriptions",
    {
      method: "POST",
      headers: {
        Authorization: `Bearer ${groqKey}`,
      },
      body: form,
    }
  );

  if (!response.ok) {
    const errText = await response.text().catch(() => response.statusText);
    throw new Error(`Groq STT error: ${errText}`);
  }
  const data = (await response.json()) as { text?: string };
  return (data.text || "").trim();
}

/** Auto-bolds key NCERT scientific phrases, numbers, and names if not already bolded */
function enrichNcertBoldTokens(text: string): string {
  if (!text || text.includes("**")) return text;
  return text.replace(
    /\b(fundamental structural and functional unit of all living organisms|independent existence|essential functions of life|electron microscope|plasma membrane|cell wall is a unique character of the plant cells|cells and products of cells|pre-existing cells|Omnis cellula-e cellula|main arena of cellular activities|fluid mosaic model|cartwheel|pericentriolar materials|tubulin protein|radial spokes|basal body|spindle apparatus|spindle fibres|9\s*\+\s*2\s*array|9\s*\+\s*0|axoneme|cristae|power houses|perinuclear space|nucleolus|chromatin|kinetochores|70S|80S|50S|30S|60S|40S|0\.3\s*µm|3\s*to\s*5\s*µm|7\.0\s*µm|10\s*to\s*50\s*nm|52\s*per\s*cent\s*protein|40\s*per\s*cent\s*lipids|glycocalyx|mesosome|plasmids?|nucleoid|centrosome|centrioles|ribosomes)\b/gi,
    "**$1**"
  );
}

/**
 * Strictly matches a topic against our canonical built-in topics ONLY by its specific topic title
 * so a topic like "Centrosome" never accidentally matches "Eukaryotic Cells" or "Overview of Cell".
 */
function findMatchingBuiltInTopic(
  title: string,
  _rawText?: string
): NcertTopic | undefined {
  const norm = title
    .toLowerCase()
    .replace(/^\d+(?:\.\d+)*\s*/, "")
    .trim();
  if (!norm) return undefined;

  if (norm.includes("centrosome") || norm.includes("centriole")) {
    return BUILTIN_CELL_TOPICS.find((t) => t.id === "topic-8-5-9");
  }
  if (norm.includes("cilia") || norm.includes("flagella")) {
    return BUILTIN_CELL_TOPICS.find((t) => t.id === "topic-8-5-8");
  }
  if (norm.includes("ribosome") || norm.includes("cytoskeleton")) {
    return BUILTIN_CELL_TOPICS.find((t) => t.id === "topic-8-5-6");
  }
  if (norm.includes("mitochondria") || norm.includes("plastid")) {
    return BUILTIN_CELL_TOPICS.find((t) => t.id === "topic-8-5-4");
  }
  if (norm === "nucleus" || norm.startsWith("nucleus ")) {
    return BUILTIN_CELL_TOPICS.find((t) => t.id === "topic-8-5-10");
  }
  if (norm.includes("what is a cell")) {
    return BUILTIN_CELL_TOPICS[0];
  }
  if (norm.includes("cell theory")) {
    return BUILTIN_CELL_TOPICS[1];
  }
  if (norm.includes("overview of cell")) {
    return BUILTIN_CELL_TOPICS[2];
  }
  if (norm.includes("prokaryotic cell")) {
    return BUILTIN_CELL_TOPICS[3];
  }
  if (
    norm === "eukaryotic cells" ||
    norm === "cell membrane" ||
    norm.includes("eukaryotic cells & cell membrane")
  ) {
    return BUILTIN_CELL_TOPICS[4];
  }
  return undefined;
}

/**
 * Surgically isolates ONLY the NCERT passage belonging to the selected topic from raw page text,
 * stripping preceding topics and stopping immediately at the next numbered/uppercase NCERT heading.
 */
export function isolateTopicPassage(topicTitle: string, rawText: string): string {
  if (!rawText || !rawText.trim()) return "";
  const cleanTitle = topicTitle.replace(/^\d+(?:\.\d+)*\s*/, "").trim();
  const numPrefixMatch = topicTitle.match(/^(\d+(?:\.\d+)+)/);
  const lines = rawText
    .split(/\n+/)
    .map((l) => l.trim())
    .filter(Boolean);

  if (lines.length <= 1) {
    // If rawText had no line breaks, slice from the topic title to the next numbered section heading
    const escapedTitle = cleanTitle.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
    const startRegex = new RegExp(
      `(?:\\d+(?:\\.\\d+)+\\s+)?${escapedTitle}`,
      "i"
    );
    const startMatch = startRegex.exec(rawText);
    let sliced = startMatch ? rawText.slice(startMatch.index) : rawText;
    // Cut off at the next numbered section heading (e.g., 8.5.10 Nucleus)
    const nextHeadingRegex = /\s+\d+\.\d+(?:\.\d+)?\s+[A-Z][A-Za-z\s]{2,40}/g;
    let nextMatch: RegExpExecArray | null;
    while ((nextMatch = nextHeadingRegex.exec(sliced)) !== null) {
      if (nextMatch.index > 25) {
        sliced = sliced.slice(0, nextMatch.index);
        break;
      }
    }
    return sliced.trim();
  }

  const headingPattern =
    /^(?:\d+\.\d+(?:\.\d+)?\s+[A-Z][A-Za-z0-9\s,()–-]{2,65}|SUMMARY|EXERCISES)$/;

  let startIdx = -1;
  const lowerCleanTitle = cleanTitle.toLowerCase();
  const titleKeywords = lowerCleanTitle
    .split(/\s+/)
    .filter((w) => w.length >= 4 && !["and", "the", "with", "from"].includes(w));

  for (let i = 0; i < lines.length; i++) {
    const lineLower = lines[i].toLowerCase();
    if (
      (numPrefixMatch && lines[i].startsWith(numPrefixMatch[1])) ||
      (lowerCleanTitle.length >= 4 &&
        lineLower.includes(lowerCleanTitle) &&
        lines[i].length <= topicTitle.length + 35)
    ) {
      startIdx = i;
      break;
    }
  }

  if (startIdx === -1 && titleKeywords.length > 0) {
    // Find first line where the topic keyword starts a heading or sentence
    for (let i = 0; i < lines.length; i++) {
      const lineLower = lines[i].toLowerCase();
      if (titleKeywords.some((kw) => lineLower.startsWith(kw) || lineLower.includes(`${kw} is `))) {
        startIdx = i;
        break;
      }
    }
  }

  const collected: string[] = [];
  const begin = startIdx >= 0 ? startIdx : 0;

  for (let i = begin; i < lines.length; i++) {
    const line = lines[i];
    // Skip running page headers/footers
    if (
      /^(?:BIOLOGY|PHYSICS|CHEMISTRY|CHAPTER\s+\d+|UNIT\s+\d+|\d{4}-\d{2}|\d{1,3})$/i.test(
        line
      )
    ) {
      continue;
    }
    if (i > begin && headingPattern.test(line)) {
      const lineLower = line.toLowerCase();
      const isSameTopic =
        (lowerCleanTitle && lineLower.includes(lowerCleanTitle)) ||
        (titleKeywords.length > 0 &&
          titleKeywords.some((kw) => lineLower.includes(kw)));
      if (!isSameTopic) {
        break;
      }
    }
    collected.push(line);
  }

  return collected.join("\n").trim() || rawText.trim();
}

/**
 * Deterministic HTML-Template NCERT Visual Note Compiler.
 * Reconstructs ONLY the selected topic's NCERT text into headings, subheadings,
 * bullet points, structural sub-cards, timelines, and summary tables.
 */
function buildHtmlTemplateSectionsFromTopic(
  topic: NcertTopic,
  startSectionNo: number
): NcertSectionBlock[] {
  const matchedBuiltIn = findMatchingBuiltInTopic(topic.title, topic.rawText);
  if (matchedBuiltIn?.precompiledSections?.length) {
    const clonedSections = structuredClone(
      matchedBuiltIn.precompiledSections
    ).map((sec, idx) => ({
      ...sec,
      sectionNo: String(startSectionNo + idx).padStart(2, "0"),
    }));
    if (topic.figures && topic.figures.length > 0) {
      const lastSec = clonedSections[clonedSections.length - 1];
      const existingFigIds = new Set(
        lastSec.cards.filter((c) => c.figure).map((c) => c.figure!.id)
      );
      topic.figures.forEach((fig, fIdx) => {
        if (!existingFigIds.has(fig.id)) {
          lastSec.cards.push({
            id: `${topic.id}-uploaded-fig-${fIdx}`,
            variant: "figure-card",
            title: `${fig.label} · Extracted Source Diagram`,
            figure: fig,
            sourceTopicId: topic.id,
          });
        }
      });
    }
    return clonedSections;
  }

  const isolatedRaw = isolateTopicPassage(topic.title, topic.rawText);
  const cleanedRaw = isolatedRaw
    .replace(new RegExp(`^${topic.number.replace(/\./g, "\\.")}\\s*`, "i"), "")
    .replace(
      new RegExp(
        `^${topic.title.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")}\\s*`,
        "i"
      ),
      ""
    )
    .trim();

  const sentences = (cleanedRaw || isolatedRaw)
    .replace(/\n+/g, " ")
    .split(/(?<=[.!?])\s+/)
    .map((s) => s.trim())
    .filter((s) => s.length > 4);

  // Extract Roman numeral items like (i) ... (ii) ...
  const romanMatches: { roman: string; text: string }[] = [];
  const romanRegex = /\(([ivx]+)\)\s*([^(]+?)(?=(?:\([ivx]+\)|\.|$))/gi;
  let match: RegExpExecArray | null;
  while ((match = romanRegex.exec(isolatedRaw)) !== null) {
    const roman = `(${match[1].toLowerCase()})`;
    const clause = enrichNcertBoldTokens(
      match[2].trim().replace(/^(?:and\s+|or\s+)/i, "").replace(/[.;,]+$/, "")
    );
    if (clause.length > 2) {
      romanMatches.push({ roman, text: clause });
    }
  }

  const timelineItems: TimelineItem[] = [];
  const structuralSubCards: { name: string; contribution: string }[] = [];
  const tableRows: string[][] = [];
  const leadBullets: { roman: string; text: string }[] = [...romanMatches];
  let leadDefinition = "";
  let importantGreenSentence = "";

  const themes: Array<"schleiden" | "schwann" | "collaboration" | "virchow"> = [
    "schleiden",
    "schwann",
    "collaboration",
    "virchow",
  ];

  sentences.forEach((s, idx) => {
    const yearMatch = s.match(/\b(1[6-9]\d{2}|20\d{2})\b/);
    const isConcluding =
      idx === sentences.length - 1 ||
      /^(?:Hence|Therefore|Thus|Consequently|Both\s+the\s+cilium|The\s+centrioles\s+form)\b/i.test(
        s
      ) ||
      /\b(?:fundamental structural and functional unit|main arena of cellular activities|power houses|spindle apparatus|site for active ribosomal RNA)\b/i.test(
        s
      );

    if (idx === 0) {
      leadDefinition = enrichNcertBoldTokens(s);
    }

    if (yearMatch) {
      const year = yearMatch[1];
      const nameMatch = s.match(
        /([A-Z][a-z]+(?:\s+(?:Von|van|de)?\s*[A-Z][a-z]+)+)/
      );
      const scientistName = nameMatch ? nameMatch[1] : `Discovery (${year})`;
      const descriptorMatch = s.match(
        /,\s*(a\s+[A-Za-z\s]+?(?:botanist|zoologist|scientist|physicist|chemist|biologist))\s*,/i
      );
      const latinMatch = s.match(
        /\((Omnis[^)]+|[A-Za-z]+\s+[a-z]+-[a-z]+\s+[a-z]+)\)/i
      );
      const theme = themes[timelineItems.length % themes.length];

      timelineItems.push({
        id: `${topic.id}-tl-${timelineItems.length}`,
        theme,
        scientist: scientistName,
        year,
        descriptor: descriptorMatch
          ? descriptorMatch[1].replace(/^a\s+/i, "")
          : undefined,
        bullets: [enrichNcertBoldTokens(s)],
        latinQuote: latinMatch ? latinMatch[1] : undefined,
      });
      tableRows.push([
        `**${scientistName}** (${year})`,
        enrichNcertBoldTokens(s),
      ]);
      return;
    }

    if (isConcluding && !importantGreenSentence && idx > 0) {
      importantGreenSentence = enrichNcertBoldTokens(s);
    }

    // Extract subject/feature label from the sentence for structured bullets, sub-cards, and table rows
    const subjectMatch = s.match(
      /^(?:The\s+|Each\s+(?:of\s+the\s+)?|Both\s+(?:the\s+)?|In\s+case\s+of\s+|Based\s+on\s+)?([A-Z]?[a-zA-Z0-9\s-]{3,36}?)\s+(is\s+|are\s+|has\s+|have\s+|possesses\s+|consists\s+of\s+|form\s+|forms\s+|lie\s+|stand\s+for\s+|contains\s+)(.+)$/i
    );

    if (subjectMatch) {
      const featureName = subjectMatch[1]
        .trim()
        .replace(/^(?:the|each|both)\s+/i, "")
        .replace(/^\w/, (c) => c.toUpperCase());
      const detail = enrichNcertBoldTokens(
        `${subjectMatch[2]}${subjectMatch[3]}`.trim()
      );
      if (idx > 0 && leadBullets.length < 4 && romanMatches.length === 0) {
        leadBullets.push({
          roman: `(${["i", "ii", "iii", "iv"][leadBullets.length]})`,
          text: `**${featureName}:** ${detail}`,
        });
      } else {
        structuralSubCards.push({
          name: featureName,
          contribution: detail,
        });
      }
      tableRows.push([`**${featureName}**`, detail]);
    } else if (idx > 0) {
      const label =
        topic.subtopics?.[structuralSubCards.length] ||
        `Key Concept ${structuralSubCards.length + 1}`;
      const enriched = enrichNcertBoldTokens(s);
      if (leadBullets.length < 3 && romanMatches.length === 0) {
        leadBullets.push({
          roman: `(${["i", "ii", "iii"][leadBullets.length]})`,
          text: enriched,
        });
      } else {
        structuralSubCards.push({
          name: label,
          contribution: enriched,
        });
      }
      tableRows.push([`**${label}**`, enriched]);
    }
  });

  const cards: NcertCardBlock[] = [];

  // 1. Purple Card (Definition + Structured Roman/Bullet Points + Green Key Takeaway)
  cards.push({
    id: `${topic.id}-purple-card`,
    variant: "purple-card",
    title: `${topic.title} · Core Concept & Organization`,
    paragraphs: leadDefinition ? [leadDefinition] : undefined,
    capabilities: leadBullets.length > 0 ? leadBullets : undefined,
    importantGreen:
      importantGreenSentence ||
      (sentences.length > 1
        ? enrichNcertBoldTokens(sentences[sentences.length - 1])
        : undefined),
    sourceTopicId: topic.id,
  });

  // 2. Teal Card (Structured Breakdown of Components / Mechanisms)
  if (structuralSubCards.length > 0) {
    cards.push({
      id: `${topic.id}-teal-card`,
      variant: "teal-card",
      title: `${topic.title} · Structural & Functional Breakdown`,
      scientists: structuralSubCards,
      sourceTopicId: topic.id,
    });
  }

  // 3. Timeline Group (if chronological milestones exist)
  if (timelineItems.length > 0) {
    cards.push({
      id: `${topic.id}-timeline`,
      variant: "timeline-group",
      timelineItems,
      sourceTopicId: topic.id,
    });
  }

  // 4. Structured NCERT Summary Table (organizes attributes/features into a clean table)
  if (tableRows.length >= 2) {
    cards.push({
      id: `${topic.id}-summary-table`,
      variant: "ai-table",
      title: `Structured NCERT Summary · ${topic.title}`,
      tableData: {
        headers: ["NCERT Feature / Component", "Exact NCERT Specification & Role"],
        rows: tableRows.slice(0, 8),
      },
      sourceTopicId: topic.id,
    });
  }

  // 5. Topic-specific figures
  topic.figures.forEach((fig, fIdx) => {
    cards.push({
      id: `${topic.id}-fig-${fIdx}`,
      variant: "figure-card",
      title: `${fig.label} · Extracted Source Diagram`,
      figure: fig,
      sourceTopicId: topic.id,
    });
  });

  return [
    {
      id: `sec-${topic.id}`,
      sectionNo: String(startSectionNo).padStart(2, "0"),
      title: `${topic.number ? `${topic.number} ` : ""}${topic.title}`.trim(),
      topicId: topic.id,
      cards,
    },
  ];
}

/**
 * Deterministically parses all NCERT section/subsection headings across all pages of an uploaded PDF
 * and slices each topic's exact verbatim text between its heading and the next heading.
 */
function extractGranularTopicsFromPdfPages(
  pdfTitle: string,
  pages: ExtractedPdfPage[]
): NcertTopic[] {
  interface RawSectionSlice {
    number: string;
    title: string;
    pageStart: number;
    pageEnd: number;
    lines: string[];
    figures: ExtractedFigure[];
  }

  const headingRegex =
    /^(\d+\.\d+(?:\.\d+)?)\s+([A-Z][A-Za-z0-9\s,()–:'’-]{2,68})$/;
  const allCapsHeadingRegex =
    /^(WHAT IS A CELL\?|CELL THEORY|AN OVERVIEW OF CELL|PROKARYOTIC CELLS|EUKARYOTIC CELLS)$/;

  const slices: RawSectionSlice[] = [];
  let current: RawSectionSlice | null = null;

  for (const page of pages) {
    const pageLines =
      page.lines && page.lines.length > 0
        ? page.lines
        : page.text.split(/\n+/).map((l) => l.trim()).filter(Boolean);

    for (const rawLine of pageLines) {
      const line = rawLine.trim();
      if (!line) continue;
      if (/^(?:SUMMARY|EXERCISES)$/i.test(line)) {
        current = null;
        continue;
      }

      const numberedMatch = line.match(headingRegex);
      const capsMatch = !numberedMatch ? line.match(allCapsHeadingRegex) : null;

      if (numberedMatch || capsMatch) {
        const secNum = numberedMatch
          ? numberedMatch[1]
          : `8.${slices.length + 1}`;
        const secTitle = numberedMatch
          ? numberedMatch[2].trim()
          : capsMatch![1]
              .toLowerCase()
              .replace(/\b\w/g, (c) => c.toUpperCase());

        current = {
          number: secNum,
          title: secTitle,
          pageStart: page.pageNumber,
          pageEnd: page.pageNumber,
          lines: [`${secNum} ${secTitle}`],
          figures: [...page.extractedFigures],
        };
        slices.push(current);
      } else if (current) {
        current.pageEnd = Math.max(current.pageEnd, page.pageNumber);
        current.lines.push(line);
        page.extractedFigures.forEach((fig) => {
          if (!current!.figures.some((f) => f.id === fig.id)) {
            current!.figures.push(fig);
          }
        });
      }
    }
  }

  if (slices.length > 0) {
    return slices.map((s, idx) => {
      const rawText = s.lines.join("\n");
      const builtIn = findMatchingBuiltInTopic(s.title, rawText);
      const sentences = rawText
        .replace(/\n+/g, " ")
        .split(/(?<=[.!?])\s+/)
        .filter((sent) => sent.length > 20);
      return {
        id: builtIn?.id || `pdf-topic-${idx + 1}-${s.number.replace(/\./g, "-")}`,
        number: s.number,
        title: s.title,
        pageStart: s.pageStart,
        pageEnd: s.pageEnd,
        summary:
          builtIn?.summary ||
          sentences.slice(0, 2).join(" ").slice(0, 220) ||
          `Extracted NCERT section ${s.number} ${s.title}.`,
        subtopics:
          builtIn?.subtopics ||
          sentences
            .slice(0, 4)
            .map((sent) => sent.split(/\s+/).slice(0, 4).join(" ")),
        rawText: builtIn?.rawText || rawText,
        figures: s.figures.length > 0 ? s.figures : builtIn?.figures || [],
        precompiledSections: builtIn?.precompiledSections,
      };
    });
  }

  // If no numbered NCERT headings were detected, check if it's the NCERT Cell chapter
  const allText = pages.map((p) => p.text).join("\n");
  if (
    /what is a cell/i.test(allText) &&
    /schleiden/i.test(allText) &&
    /virchow/i.test(allText)
  ) {
    return BUILTIN_CELL_TOPICS;
  }

  // Page-by-page fallback for non-numbered PDFs
  return pages.map((p, idx) => {
    const sentences = p.text
      .split(/(?<=[.!?])\s+/)
      .filter((s) => s.trim().length > 15);
    const firstLine =
      p.lines?.find((l) => l.length >= 4 && l.length <= 65) ||
      `Topic ${idx + 1} · Page ${p.pageNumber}`;
    return {
      id: `pdf-topic-${p.pageNumber}`,
      number: `${idx + 1}.1`,
      title: firstLine,
      pageStart: p.pageNumber,
      pageEnd: p.pageNumber,
      summary:
        sentences.slice(0, 2).join(" ").slice(0, 220) ||
        `Extracted content from ${pdfTitle} page ${p.pageNumber}.`,
      subtopics: sentences
        .slice(0, 3)
        .map((s) => s.split(/\s+/).slice(0, 4).join(" ")),
      rawText: p.text || `Content from ${pdfTitle} page ${p.pageNumber}.`,
      figures: p.extractedFigures,
    };
  });
}

export async function scanPdfTopicsWithAI(
  pdfTitle: string,
  pages: ExtractedPdfPage[],
  customGeminiKey?: string
): Promise<NcertTopic[]> {
  const deterministicTopics = extractGranularTopicsFromPdfPages(pdfTitle, pages);
  const apiKey = getGeminiKey(customGeminiKey);

  if (!apiKey || pages.length === 0) {
    return deterministicTopics;
  }

  try {
    const ai = new GoogleGenAI({ apiKey });
    const condensedPages = pages.slice(0, 18).map((p) => ({
      pageNumber: p.pageNumber,
      text: p.text.slice(0, 3200),
      figureCount: p.extractedFigures.length,
    }));

    const response = await generateContentWithFallback(
      ai,
      `You are an expert NCERT textbook analyzer. Scan these extracted PDF pages from "${pdfTitle}" and return a comprehensive JSON array of ALL topics and sub-topics in the chapter (including every numbered sub-section such as 8.5.8 Cilia and Flagella, 8.5.9 Centrosome and Centrioles, 8.5.10 Nucleus, etc.).
CRITICAL RULE: For each topic's "rawText", extract ONLY the exact verbatim NCERT sentences belonging to THAT specific topic. NEVER include text from adjacent topics on the same page!

Pages data:
${JSON.stringify(condensedPages)}

Return ONLY valid JSON matching this structure:
[
  {
    "number": "8.5.9",
    "title": "Centrosome and Centrioles",
    "pageStart": 10,
    "pageEnd": 10,
    "summary": "Concise 1-2 sentence overview of what this specific NCERT section covers",
    "subtopics": ["Subtopic 1", "Subtopic 2", "Subtopic 3"],
    "rawText": "Exact verbatim NCERT text belonging ONLY to this topic"
  }
]`,
      {
        responseMimeType: "application/json",
      }
    );

    const parsed = JSON.parse(response.text || "[]") as Array<{
      number?: string;
      title?: string;
      pageStart?: number;
      pageEnd?: number;
      summary?: string;
      subtopics?: string[];
      rawText?: string;
    }>;

    if (!Array.isArray(parsed) || parsed.length === 0) {
      return deterministicTopics;
    }

    return parsed.map((item, idx) => {
      const pStart = Math.max(1, item.pageStart || idx + 1);
      const pEnd = Math.max(pStart, item.pageEnd || pStart);
      const matchingFigures: ExtractedFigure[] = [];
      pages.forEach((p) => {
        if (p.pageNumber >= pStart && p.pageNumber <= pEnd) {
          matchingFigures.push(...p.extractedFigures);
        }
      });

      const pageTextCombined = pages
        .filter((p) => p.pageNumber >= pStart && p.pageNumber <= pEnd)
        .map((p) => p.text)
        .join("\n");

      const isolatedText = item.rawText?.trim()
        ? isolateTopicPassage(item.title || "", item.rawText)
        : isolateTopicPassage(item.title || "", pageTextCombined);

      const builtIn = findMatchingBuiltInTopic(item.title || "", isolatedText);

      return {
        id: builtIn?.id || `scanned-topic-${idx + 1}`,
        number: item.number || builtIn?.number || `${idx + 1}`,
        title: item.title || builtIn?.title || `Topic ${idx + 1}`,
        pageStart: pStart,
        pageEnd: pEnd,
        summary: item.summary || builtIn?.summary || "Extracted NCERT section.",
        subtopics: Array.isArray(item.subtopics)
          ? item.subtopics.slice(0, 6)
          : builtIn?.subtopics || [],
        rawText: isolatedText || builtIn?.rawText || "",
        figures:
          matchingFigures.length > 0
            ? matchingFigures
            : builtIn?.figures || [],
        precompiledSections: builtIn?.precompiledSections,
      };
    });
  } catch {
    return deterministicTopics;
  }
}

export async function compileTopicsToNcertNotes(
  topics: NcertTopic[],
  customGeminiKey?: string
): Promise<NcertSectionBlock[]> {
  const resultSections: NcertSectionBlock[] = [];
  let sectionCounter = 1;

  for (const topic of topics) {
    // 1. Check strict title-matched built-in precompiled sections first
    const builtInMatch =
      topic.precompiledSections && topic.precompiledSections.length > 0
        ? topic
        : findMatchingBuiltInTopic(topic.title, topic.rawText);

    if (builtInMatch?.precompiledSections?.length) {
      const sections = buildHtmlTemplateSectionsFromTopic(
        {
          ...builtInMatch,
          figures:
            topic.figures && topic.figures.length > 0
              ? topic.figures
              : builtInMatch.figures,
        },
        sectionCounter
      );
      sectionCounter += sections.length;
      resultSections.push(...sections);
      continue;
    }

    // Isolate ONLY the selected topic's passage so adjacent topics on the same page are never included
    const cleanTopicText = isolateTopicPassage(topic.title, topic.rawText);

    // 2. If Gemini API key is configured, ask Gemini to structure ONLY the selected topic's verbatim NCERT text
    const apiKey = getGeminiKey(customGeminiKey);
    if (apiKey && cleanTopicText) {
      try {
        const ai = new GoogleGenAI({ apiKey });
        const prompt = `You are an NCERT Visual Study Note Engineer.
Your task is to reorganize ONLY the NCERT content for the selected topic "${topic.number} ${topic.title}" into our structured HTML study note format.

STRICT EXTRACTION & FORMATTING RULES:
1. TOPIC ISOLATION: Extract and organize ONLY content belonging to "${topic.title}". Exclude any sentences belonging to preceding or following topics on the same page.
2. PRESERVE ORIGINAL NCERT CONTENT: Preserve 100% of the original NCERT facts, terminology, measurements, scientist names, and meaning without adding external explanations or omitting any NCERT detail. Use **bold** around key NCERT terms, numbers, and scientist names.
3. NO DENSE PARAGRAPHS: Reorganize the content into clear headings, subheadings, bullet points, and tables:
   - "purple-card": Core definition/lead sentence + "capabilities" array for bulleted/roman points (i), (ii), (iii) + "importantGreen" for the key NCERT functional/concluding statement.
   - "teal-card": Detailed structural/functional sub-cards in "scientists" array (each item has "name" = Subheading / Structural Part / Scientist and "contribution" = exact NCERT specification) + "footerNote".
   - "timeline-group": Include if chronological years/scientists appear.
   - "ai-table": Include a structured summary/comparison table ("tableData": { "headers": [...], "rows": [[...]] }) summarizing the components, structures, measurements, or features of "${topic.title}".

Selected Topic: ${topic.number} ${topic.title}
Extracted Source Passage:
${cleanTopicText}

Return ONLY valid JSON with this structure:
{
  "sections": [
    {
      "title": "${topic.number} ${topic.title}",
      "cards": [
        {
          "variant": "purple-card",
          "title": "Core Concept & Organization",
          "paragraphs": ["Concise NCERT opening definition sentence."],
          "capabilities": [
            {"roman": "(i)", "text": "**Key Point 1:** Exact NCERT detail."},
            {"roman": "(ii)", "text": "**Key Point 2:** Exact NCERT detail."}
          ],
          "importantGreen": "**Key NCERT Takeaway / Function sentence.**"
        },
        {
          "variant": "teal-card",
          "title": "Ultrastructure & Component Breakdown",
          "scientists": [
            {"name": "Component / Sub-structure", "contribution": "Exact NCERT description with **bold** terms."}
          ],
          "footerNote": "Important NCERT note."
        },
        {
          "variant": "ai-table",
          "title": "NCERT Structural & Functional Summary · ${topic.title}",
          "tableData": {
            "headers": ["Component / Feature", "NCERT Organization & Detail", "Function / Significance"],
            "rows": [
              ["**Feature 1**", "Exact NCERT structure", "Exact NCERT role"]
            ]
          }
        }
      ]
    }
  ]
}`;
        const response = await generateContentWithFallback(ai, prompt, {
          responseMimeType: "application/json",
        });
        const parsed = JSON.parse(response.text || "{}") as {
          sections?: Array<{
            title?: string;
            cards?: Array<Partial<NcertCardBlock>>;
          }>;
        };
        if (Array.isArray(parsed.sections) && parsed.sections.length > 0) {
          parsed.sections.forEach((sec, sIdx) => {
            const cards: NcertCardBlock[] = (sec.cards || []).map((c, idx) => ({
              id: `${topic.id}-s${sIdx}-card-${idx}`,
              variant:
                (c.variant as NcertCardBlock["variant"]) || "purple-card",
              title: c.title || topic.title,
              paragraphs: c.paragraphs?.map(enrichNcertBoldTokens),
              capabilities: c.capabilities?.map((cap) => ({
                roman: cap.roman,
                text: enrichNcertBoldTokens(cap.text),
              })),
              importantGreen: c.importantGreen
                ? enrichNcertBoldTokens(c.importantGreen)
                : undefined,
              scientists: c.scientists?.map((sc) => ({
                name: sc.name,
                contribution: enrichNcertBoldTokens(sc.contribution),
              })),
              footerNote: c.footerNote
                ? enrichNcertBoldTokens(c.footerNote)
                : undefined,
              timelineItems: c.timelineItems,
              theoryRows: c.theoryRows?.map((r) => ({
                roman: r.roman,
                text: enrichNcertBoldTokens(r.text),
              })),
              tableData: c.tableData,
              sourceTopicId: topic.id,
            }));

            if (sIdx === parsed.sections!.length - 1) {
              topic.figures.forEach((fig, fIdx) => {
                cards.push({
                  id: `${topic.id}-fig-${fIdx}`,
                  variant: "figure-card",
                  title: `${fig.label} · Extracted Source Diagram`,
                  figure: fig,
                  sourceTopicId: topic.id,
                });
              });
            }

            resultSections.push({
              id: `sec-${topic.id}-${sIdx}`,
              sectionNo: String(sectionCounter++).padStart(2, "0"),
              title: sec.title || `${topic.number} ${topic.title}`.trim(),
              topicId: topic.id,
              cards,
            });
          });
          continue;
        }
      } catch {
        // Fall through to deterministic HTML-template NCERT compiler
      }
    }

    // 3. Deterministic HTML-template NCERT compiler (strictly isolated to topic)
    const builtSections = buildHtmlTemplateSectionsFromTopic(
      { ...topic, rawText: cleanTopicText },
      sectionCounter
    );
    sectionCounter += builtSections.length;
    resultSections.push(...builtSections);
  }

  return resultSections;
}

export async function runStudyHarnessCommand(params: {
  userPrompt: string;
  currentMode: "topics" | "study" | "pdf";
  availableTopics: NcertTopic[];
  selectedTopicIds: string[];
  sections: NcertSectionBlock[];
  activeDoubtMark: DoubtMarkContext | null;
  customGeminiKey?: string;
}): Promise<HarnessResponse> {
  const {
    userPrompt,
    currentMode,
    availableTopics,
    selectedTopicIds,
    sections,
    activeDoubtMark,
    customGeminiKey,
  } = params;

  const lower = userPrompt.toLowerCase().trim();

  // 1. Check if the user is saying "I have a doubt" / asking for the doubt marker
  if (
    /\b(i have a doubt|have a doubt|give me (?:the |a )?marker|mark (?:my |a )?doubt|open marker|doubt marker)\b/i.test(
      lower
    ) &&
    !activeDoubtMark
  ) {
    return {
      reply:
        "Doubt Marker is now active! Scroll freely to any part of your notes, tap any sentence/card or draw a box to mark what you have a doubt on, and speak on the mic.",
      actions: [{ type: "ACTIVATE_DOUBT_MARKER" }],
      suggestedFollowUps: [
        "Explain this marked line simply",
        "Make a comparison table for this",
        "Clear doubt marker",
      ],
    };
  }

  // 2. Match topics by title/number when user asks to study specific topics
  const matchedTopics = availableTopics.filter((t) => {
    const titleWords = t.title
      .toLowerCase()
      .replace(/[^a-z0-9\s]/g, "")
      .split(/\s+/)
      .filter((w) => w.length > 3);
    if (lower.includes(t.title.toLowerCase())) return true;
    if (lower.includes(t.number.toLowerCase())) return true;
    if (lower.includes("centrosome") && t.title.toLowerCase().includes("centrosome"))
      return true;
    if (lower.includes("cilia") && t.title.toLowerCase().includes("cilia"))
      return true;
    if (
      t.id === "topic-8-1" &&
      (lower.includes("what is a cell") || lower.includes("first topic"))
    )
      return true;
    if (t.id === "topic-8-2" && lower.includes("cell theory")) return true;
    if (t.id === "topic-8-3" && lower.includes("overview")) return true;
    if (t.id === "topic-8-4" && lower.includes("prokaryot")) return true;
    if (
      t.id === "topic-8-5" &&
      (lower.includes("eukaryot") || lower.includes("membrane"))
    )
      return true;
    return (
      titleWords.length > 0 && titleWords.every((w) => lower.includes(w))
    );
  });

  if (
    (currentMode === "topics" && matchedTopics.length > 0) ||
    (/\b(i want to study|let's study|open topic|study topic|select topic|extract)\b/i.test(
      lower
    ) &&
      matchedTopics.length > 0)
  ) {
    const ids = matchedTopics.map((t) => t.id);
    const names = matchedTopics.map((t) => `${t.number} ${t.title}`).join(", ");
    return {
      reply: `Selected **${names}**! Reconstructed only the relevant NCERT content into structured headings, bullet points, and summary tables.`,
      actions: [
        {
          type: "SELECT_TOPICS_AND_STUDY",
          topicIds: ids,
        },
      ],
      suggestedFollowUps: [
        "Compare 9+0 Centriole vs 9+2 Cilia in a table",
        "I have a doubt",
        "Quiz me on this section",
      ],
    };
  }

  if (
    currentMode === "topics" &&
    /\b(all topics|everything|study all|first two)\b/i.test(lower)
  ) {
    const chosen = lower.includes("first two")
      ? availableTopics.slice(0, 2)
      : availableTopics;
    return {
      reply: `Opening **${chosen.map((t) => t.title).join(" & ")}** in full-screen Study Engine with structured NCERT notes!`,
      actions: [
        {
          type: "SELECT_TOPICS_AND_STUDY",
          topicIds: chosen.map((t) => t.id),
        },
      ],
    };
  }

  // 3. Delete / Remove block commands
  const allAiBlocks: { sectionId: string; block: NcertCardBlock }[] = [];
  sections.forEach((sec) => {
    sec.cards.forEach((card) => {
      if (card.aiGenerated) {
        allAiBlocks.push({ sectionId: sec.id, block: card });
      }
    });
  });

  if (
    /\b(delete|remove|get rid of|don't like|dont like|hide)\b/i.test(lower) &&
    allAiBlocks.length > 0
  ) {
    const target =
      allAiBlocks
        .slice()
        .reverse()
        .find((item) => {
          if (lower.includes("table") && item.block.variant === "ai-table")
            return true;
          if (lower.includes("quiz") && item.block.variant === "ai-quiz")
            return true;
          return false;
        }) || allAiBlocks[allAiBlocks.length - 1];

    return {
      reply: `Removed "${target.block.title || "that AI block"}" from your Study Engine canvas.`,
      actions: [
        {
          type: "DELETE_BLOCK",
          targetSectionId: target.sectionId,
          targetBlockId: target.block.id,
        },
      ],
    };
  }

  const targetSectionId =
    activeDoubtMark?.sectionId || sections[0]?.id || "sec-what-is-cell";

  // 4. Call Gemini with automatic model fallback for structured tables, formulas, and explanations
  const apiKey = getGeminiKey(customGeminiKey);
  if (apiKey) {
    try {
      const ai = new GoogleGenAI({ apiKey });
      const currentNotesSummary = sections.map((s) => ({
        sectionId: s.id,
        title: s.title,
        cards: s.cards.map((c) => ({
          id: c.id,
          variant: c.variant,
          title: c.title,
          aiGenerated: c.aiGenerated || false,
        })),
      }));

      const systemPrompt = `You are the Folio AI Study Engine Mentor & Harness for NCERT.
Current study sections: ${JSON.stringify(currentNotesSummary)}
Available chapter topics: ${JSON.stringify(
        availableTopics.map((t) => ({ id: t.id, number: t.number, title: t.title }))
      )}
Currently selected topic IDs: ${JSON.stringify(selectedTopicIds)}
${
  activeDoubtMark
    ? `USER MARKED A DOUBT ON SCREEN: "${activeDoubtMark.markedText}" in section "${activeDoubtMark.sectionId}". Clarify this exact marked doubt directly!`
    : ""
}

IMPORTANT CHAT FORMATTING RULES FOR "reply":
- In "reply", actively use structured **Markdown tables** (e.g. \`| Feature | Detail |\`), **Formula/Equation blocks** (e.g. \`Formula: S = v / ω²r\` or \`9(3) + 0 = 27 microtubules\`), and **bullet points** (\`- **Term:** explanation\`) whenever comparing structures, explaining quantitative relations, or breaking down NCERT mechanisms!
- Preserve exact NCERT terminology.

You can also return actions to control the Study Engine canvas:
- "ADD_BLOCK": Add a comparison table ("ai-table"), deep-dive explanation card ("ai-explanation"), or active recall question ("ai-quiz") into targetSectionId ("${targetSectionId}").
- "MODIFY_BLOCK": Modify an existing card by targetSectionId and targetBlockId.
- "DELETE_BLOCK": Delete a card by targetSectionId and targetBlockId.
- "ACTIVATE_DOUBT_MARKER": If the user says they have a doubt and haven't marked on screen yet.
- "SELECT_TOPICS_AND_STUDY": If the user wants to switch or add topics (provide topicIds array).

Return ONLY valid JSON:
{
  "reply": "Structured explanation using Markdown tables, formula blocks, and bullet points with **bold** key terms.",
  "actions": [],
  "suggestedFollowUps": ["Short follow-up 1", "Short follow-up 2"]
}`;

      const response = await generateContentWithFallback(
        ai,
        `${systemPrompt}\n\nStudent message: "${userPrompt}"`,
        { responseMimeType: "application/json" }
      );

      const parsed = JSON.parse(response.text || "{}") as HarnessResponse;
      if (parsed.reply) {
        return {
          reply: parsed.reply,
          actions: Array.isArray(parsed.actions)
            ? parsed.actions.map((a) => ({
                ...a,
                block: a.block
                  ? {
                      ...a.block,
                      id: a.block.id || `ai-${Date.now()}`,
                      aiGenerated: true,
                    }
                  : undefined,
              }))
            : [],
          suggestedFollowUps: parsed.suggestedFollowUps || [
            "Compare in a table",
            "Show key formulas & numbers",
            "Quiz me on this",
          ],
        };
      }
    } catch {
      // Fall through to intelligent local NCERT harness
    }
  }

  // 5. Intelligent Local NCERT Study Engine Harness with rich Markdown Tables & Formulas in Chat
  if (
    /\b(modify|change|update|simpler|add a column|expand)\b/i.test(lower) &&
    allAiBlocks.length > 0
  ) {
    const lastBlock = allAiBlocks[allAiBlocks.length - 1];
    const updatedBlock: NcertCardBlock = {
      ...lastBlock.block,
      title: `${lastBlock.block.title || "Study Block"} (Updated by AI)`,
      tableData: lastBlock.block.tableData
        ? {
            headers: [
              ...lastBlock.block.tableData.headers,
              "NCERT High-Yield Note",
            ],
            rows: lastBlock.block.tableData.rows.map((r, idx) => [
              ...r,
              idx === 0
                ? "Frequently asked in NEET Statement type questions"
                : "Verify exact year & structure in NCERT",
            ]),
          }
        : undefined,
      paragraphs: lastBlock.block.paragraphs
        ? [
            ...lastBlock.block.paragraphs,
            "✦ **Updated Summary:** Focus on the exact NCERT keywords bolded above — every single term is a direct NEET assertion-reason anchor.",
          ]
        : undefined,
    };

    return {
      reply: `Updated **${lastBlock.block.title}** directly in your Study Engine with high-yield NEET takeaways!`,
      actions: [
        {
          type: "MODIFY_BLOCK",
          targetSectionId: lastBlock.sectionId,
          targetBlockId: lastBlock.block.id,
          block: updatedBlock,
        },
      ],
      suggestedFollowUps: ["Delete that block", "Quiz me on this"],
    };
  }

  if (/\b(table|compare|comparison|difference|versus|vs|formula|arrangement|9\+0|9\+2)\b/i.test(lower)) {
    const isCentrosomeCilia =
      lower.includes("centrosome") ||
      lower.includes("centriole") ||
      lower.includes("cilia") ||
      lower.includes("9+0") ||
      lower.includes("9+2") ||
      sections.some((s) => s.title.toLowerCase().includes("centrosome"));
    const isProkEuk =
      lower.includes("prokaryot") || lower.includes("eukaryot");

    const newBlock: NcertCardBlock = {
      id: `ai-table-${Date.now()}`,
      variant: "ai-table",
      title: isCentrosomeCilia
        ? "AI Comparison Table · Centriole (9+0) vs Cilia/Flagella Axoneme (9+2)"
        : isProkEuk
          ? "AI Comparison Table · Prokaryotic vs Eukaryotic Cells (NCERT)"
          : "AI Comparison Table · Scientists & Milestones in Cell Biology",
      aiGenerated: true,
      tableData: isCentrosomeCilia
        ? {
            headers: [
              "Feature",
              "Centrosome / Centriole",
              "Cilia & Flagella (Axoneme)",
            ],
            rows: [
              [
                "Microtubule Formula",
                "**9 + 0 Arrangement** (9 peripheral triplets, 0 central)",
                "**9 + 2 Array** (9 peripheral doublets + 2 central singlet tubules)",
              ],
              [
                "Peripheral Microtubules",
                "9 evenly spaced **triplet fibrils** of tubulin (A, B, C)",
                "9 radially arranged **doublet microtubules**",
              ],
              [
                "Central Structure",
                "Proteinaceous **central hub** connected by 9 **radial spokes**",
                "Pair of **central microtubules** enclosed by a **central sheath**",
              ],
              [
                "Membrane Covering",
                "**Non-membrane bound** (surrounded by amorphous pericentriolar material)",
                "**Covered with plasma membrane**",
              ],
              [
                "NCERT Function",
                "Forms **basal bodies** of cilia/flagella & **spindle fibres** in animal cell division",
                "Cell motility / movement of cell or surrounding fluid",
              ],
            ],
          }
        : isProkEuk
          ? {
              headers: ["Feature", "Prokaryotic Cells", "Eukaryotic Cells"],
              rows: [
                [
                  "Nuclear Membrane",
                  "Absent (**naked genomic DNA**)",
                  "Present (**well-organized nucleus** with nuclear envelope)",
                ],
                [
                  "Membrane-Bound Organelles",
                  "Absent (no ER, Golgi, mitochondria, plastids)",
                  "Present (ER, Golgi complex, lysosomes, mitochondria, plastids)",
                ],
                [
                  "Ribosomes Formula",
                  "**70S** (50S + 30S subunits)",
                  "**80S** (60S + 40S) in cytoplasm; **70S** in mitochondria & plastids",
                ],
                [
                  "Examples (NCERT)",
                  "**Bacteria, Blue-green algae, Mycoplasma, PPLO**",
                  "**Protists, Plants, Animals, and Fungi**",
                ],
              ],
            }
          : {
              headers: ["Scientist", "Year / Field", "NCERT Contribution"],
              rows: [
                [
                  "Antonie Von Leeuwenhoek",
                  "Microscopy Pioneer",
                  "First saw and described a **live cell**",
                ],
                [
                  "Robert Brown",
                  "1831 · Cytology",
                  "Discovered the **nucleus**",
                ],
                [
                  "Matthias Schleiden",
                  "1838 · German Botanist",
                  "Observed **all plants are composed of different kinds of cells**",
                ],
                [
                  "Theodore Schwann",
                  "1839 · German Zoologist",
                  "Reported **plasma membrane** & **cell wall**; bodies composed of cells & products of cells",
                ],
                [
                  "Rudolf Virchow",
                  "1855 · Omnis cellula-e cellula",
                  "Explained **new cells arise from pre-existing cells**",
                ],
              ],
            },
    };

    const chatTableMarkdown = isCentrosomeCilia
      ? `Here is the structured **NCERT Ultrastructure & Formula Comparison** (also pinned to your study notes):

Formula: Centriole = 9(3) + 0 = 27 tubulin protofilaments (Cartwheel)
Formula: Axoneme (Cilia/Flagella) = 9(2) + 2 = 20 microtubules

| Feature | Centriole (Centrosome) | Axoneme (Cilia/Flagella) |
| --- | --- | --- |
| Array Formula | **9 + 0** (Triplets) | **9 + 2** (Doublets + 2 Central) |
| Central Core | **Proteinaceous Hub** | **2 Central Microtubules + Sheath** |
| Radial Spokes | **9 Protein Spokes** (Hub → Triplets) | **9 Radial Spokes** (Sheath → Doublets) |
| Membrane | **Non-membranous** (Pericentriolar matrix) | **Plasma membrane** covered |`
      : `Here is the structured **NCERT Comparison Table & Quantitative Breakdown** (also added to your canvas):

Formula: Prokaryotic Ribosome = 50S + 30S → 70S (15 nm × 20 nm)
Formula: Eukaryotic Ribosome = 60S + 40S → 80S (S = Svedberg's Unit)

| Feature | Prokaryotes | Eukaryotes |
| --- | --- | --- |
| Nucleus | **Naked Nucleoid** (No envelope) | **True Nucleus** (Double membrane) |
| Organelles | Absent | ER, Golgi, Mitochondria, Plastids |
| Ribosomes | **70S** (50S + 30S) | **80S** (60S + 40S) + Organellar **70S** |
| Centrioles | Absent | Present in **Animal Cells** (9+0) |`;

    return {
      reply: chatTableMarkdown,
      actions: [
        {
          type: "ADD_BLOCK",
          targetSectionId,
          block: newBlock,
        },
      ],
      suggestedFollowUps: [
        "Add a column for NEET tips",
        "Delete that table",
        "Quiz me on this",
      ],
    };
  }

  if (/\b(quiz|question|test me|mcq)\b/i.test(lower)) {
    const isCentrosome = sections.some((s) =>
      s.title.toLowerCase().includes("centrosome")
    );
    const quizBlock: NcertCardBlock = {
      id: `ai-quiz-${Date.now()}`,
      variant: "ai-quiz",
      title: "AI Active Recall Check · NCERT Line-by-Line",
      aiGenerated: true,
      quizData: isCentrosome
        ? {
            question:
              "In a centriole's cartwheel organisation, how are the peripheral fibrils arranged and what is the central part called?",
            options: [
              "9 peripheral doublets + 2 central microtubules",
              "9 evenly spaced peripheral triplets of tubulin + central proteinaceous hub",
              "9 peripheral singlets + central lipid core",
              "8 peripheral triplets + 2 central spokes",
            ],
            answerIndex: 1,
            explanation:
              "NCERT 8.5.9 states: Centrioles are made up of nine evenly spaced peripheral fibrils of tubulin protein. Each peripheral fibril is a triplet. The central part of the proximal region is proteinaceous and called the hub, connected to peripheral triplets by radial spokes made of protein.",
          }
        : {
            question:
              "Who modified the hypothesis of Schleiden and Schwann in 1855 to explain how new cells are formed (Omnis cellula-e cellula)?",
            options: [
              "Antonie Von Leeuwenhoek",
              "Robert Brown",
              "Rudolf Virchow",
              "Singer and Nicolson",
            ],
            answerIndex: 2,
            explanation:
              "NCERT states: Schleiden and Schwann's theory did not explain how new cells were formed. Rudolf Virchow (1855) first explained that cells divided and new cells are formed from pre-existing cells (Omnis cellula-e cellula).",
          },
    };
    return {
      reply:
        "Added an **NCERT Active Recall Question** directly into your study notes! Tap an option to test yourself.",
      actions: [
        {
          type: "ADD_BLOCK",
          targetSectionId,
          block: quizBlock,
        },
      ],
      suggestedFollowUps: [
        "Delete that quiz",
        "Compare in a table",
        "I have a doubt",
      ],
    };
  }

  // If the user marked a doubt on screen and asked a question:
  if (activeDoubtMark) {
    const snippet = activeDoubtMark.markedText.slice(0, 180);
    const explanationBlock: NcertCardBlock = {
      id: `ai-exp-${Date.now()}`,
      variant: "ai-explanation",
      title: `Doubt Clarification · "${snippet.slice(0, 48)}${snippet.length > 48 ? "…" : ""}"`,
      aiGenerated: true,
      paragraphs: [
        `**Marked NCERT Line:** "${snippet}"`,
        `**Concept Breakdown:** In NCERT Biology, this line emphasizes exact structural-functional correlation. Every component mentioned here directly determines how the cell or organelle performs its physiological role.`,
        `**NEET Exam Anchor:** Pay close attention to the exact NCERT terminology and numerical arrangement in this marked line — it is frequently tested verbatim in assertion-reason questions.`,
      ],
    };

    return {
      reply: `**Clarification for your marked doubt:**
- **Marked Text:** "${snippet.slice(0, 80)}…"
- **Core Mechanism:** Complete structural organization is mandatory for function.

| NCERT Aspect | Key Takeaway |
| --- | --- |
| **Structure** | Intact organization required for activity |
| **Exam Focus** | Direct NCERT Assertion-Reason statement |`,
      actions: [
        {
          type: "ADD_BLOCK",
          targetSectionId: activeDoubtMark.sectionId,
          block: explanationBlock,
        },
      ],
      suggestedFollowUps: [
        "Delete that clarification card",
        "Make a table for this",
        "Clear doubt marker",
      ],
    };
  }

  return {
    reply: `**NCERT Quick Structured Reference:**

Formula: Prokaryotic = 70S (50S + 30S) · Eukaryotic = 80S (60S + 40S)
Formula: Centriole = 9 + 0 (Triplets) · Cilia/Flagella = 9 + 2 (Doublets)

| Organelle / Concept | NCERT Key Specification |
| --- | --- |
| **Unicellular Life** | **(i)** Independent existence & **(ii)** Essential life functions |
| **Cell Theory** | **Schleiden (1838)**, **Schwann (1839)**, **Virchow (1855 — Omnis cellula-e cellula)** |
| **Centrosome (8.5.9)** | **2 perpendicular centrioles**, pericentriolar material, **9+0 cartwheel**, **tubulin triplets**, **hub + 9 protein radial spokes** |`,
    actions: [],
    suggestedFollowUps: [
      "Compare Centriole (9+0) vs Cilia (9+2)",
      "Study Centrosome and Centrioles",
      "I have a doubt",
      "Quiz me on this",
    ],
  };
}
