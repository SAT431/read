import * as pdfjsLib from "pdfjs-dist";
import { ExtractedFigure, NCERT_CELL_FIGURES } from "../data/neetCatalog";

// Use locally bundled Vite worker so PDF parsing never fails due to external CDN issues
pdfjsLib.GlobalWorkerOptions.workerSrc = new URL(
  "pdfjs-dist/build/pdf.worker.min.mjs",
  import.meta.url,
).toString();

export interface ExtractedPdfPage {
  pageNumber: number;
  text: string;
  lines: string[];
  pageImageDataUrl?: string;
  extractedFigures: ExtractedFigure[];
}

export interface PdfPageContent {
  pageNumber: number;
  text: string;
}

export interface LoadedPdfSummary {
  pageCount: number;
}

export interface BuiltInNcertSheetPage {
  pageNumber: number;
  headerLeft: string;
  headerRight: string;
  chapterBanner?: {
    unitInfo: string;
    chapterNo: string;
    title: string;
  };
  columns: {
    heading?: string;
    paragraphs: string[];
  }[];
  figure?: ExtractedFigure;
}

export const BUILTIN_NCERT_CELL_PAGES: BuiltInNcertSheetPage[] = [
  {
    pageNumber: 1,
    headerLeft: "BIOLOGY · CLASS XI",
    headerRight: "CHAPTER 8 · CELL: THE UNIT OF LIFE",
    chapterBanner: {
      unitInfo: "UNIT 3 · CELL: STRUCTURE AND FUNCTIONS",
      chapterNo: "CHAPTER 8",
      title: "CELL: THE UNIT OF LIFE",
    },
    columns: [
      {
        heading: "8.1 WHAT IS A CELL?",
        paragraphs: [
          "Unicellular organisms are capable of (i) independent existence and (ii) performing the essential functions of life. Anything less than a complete structure of a cell does not ensure independent living. Hence, cell is the fundamental structural and functional unit of all living organisms.",
          "Antonie Von Leeuwenhoek first saw and described a live cell. Robert Brown later discovered the nucleus. The invention of the microscope and its improvement leading to the electron microscope revealed all the structural details of the cell.",
        ],
      },
      {
        heading: "8.2 CELL THEORY",
        paragraphs: [
          "In 1838, Matthias Schleiden, a German botanist, examined a large number of plants and observed that all plants are composed of different kinds of cells which form the tissues of the plant. At about the same time, Theodore Schwann (1839), a German Zoologist, studied different types of animal cells and reported that cells had a thin outer layer which is today known as the 'plasma membrane'. He also concluded, based on his studies on plant tissues, that the presence of cell wall is a unique character of the plant cells. On the basis of this, Schwann proposed the hypothesis that the bodies of animals and plants are composed of cells and products of cells.",
          "Schleiden and Schwann together formulated the cell theory. This theory however, did not explain as to how new cells were formed. Rudolf Virchow (1855) first explained that cells divided and new cells are formed from pre-existing cells (Omnis cellula-e cellula). He modified the hypothesis of Schleiden and Schwann to give the cell theory a final shape. Cell theory as understood today is: (i) all living organisms are composed of cells and products of cells. (ii) all cells arise from pre-existing cells.",
        ],
      },
    ],
  },
  {
    pageNumber: 2,
    headerLeft: "CELL: THE UNIT OF LIFE",
    headerRight: "NCERT PAGE 126",
    columns: [
      {
        heading: "8.3 AN OVERVIEW OF CELL",
        paragraphs: [
          "You have earlier observed cells in an onion peel and/or human cheek cells under the microscope. Let us recollect their structure. The onion cell which is a typical plant cell, has a distinct cell wall as its outer boundary and just within it is the cell membrane. Inside each cell is a dense membrane bound structure called nucleus. This nucleus contains the chromosomes which in turn contain the genetic material, DNA.",
          "In both prokaryotic and eukaryotic cells, a semi-fluid matrix called cytoplasm occupies the volume of the cell. The cytoplasm is the main arena of cellular activities in both the plant and animal cells. Various chemical reactions occur in it to keep the cell in the 'living state'.",
        ],
      },
      {
        paragraphs: [
          "Ribosomes are non-membrane bound organelles found in all cells – both eukaryotic as well as prokaryotic. Within the cell, ribosomes are found not only in the cytoplasm but also within the two organelles – chloroplasts (in plants) and mitochondria and on rough ER.",
          "Animal cells contain another non-membrane bound organelle called centrosome which helps in cell division. Cells differ greatly in size, shape and activities (Figure 8.1). Mycoplasmas, the smallest cells, are only 0.3 µm in length while bacteria could be 3 to 5 µm. Human red blood cells are about 7.0 µm in diameter.",
        ],
      },
    ],
    figure: NCERT_CELL_FIGURES.fig_8_1,
  },
  {
    pageNumber: 3,
    headerLeft: "BIOLOGY · CLASS XI",
    headerRight: "NCERT PAGE 127–128",
    columns: [
      {
        heading: "8.4 PROKARYOTIC CELLS",
        paragraphs: [
          "The prokaryotic cells are represented by bacteria, blue-green algae, mycoplasma and PPLO (Pleuro Pneumonia Like Organisms). They are generally smaller and multiply more rapidly than the eukaryotic cells (Figure 8.2). The four basic shapes of bacteria are bacillus (rod like), coccus (spherical), vibrio (comma shaped) and spirillum (spiral).",
          "All prokaryotes have a cell wall surrounding the cell membrane except in mycoplasma. In addition to the genomic DNA, many bacteria have small circular DNA outside the genomic DNA called plasmids.",
        ],
      },
      {
        paragraphs: [
          "The cell envelope consists of a tightly bound three layered structure i.e., the outermost glycocalyx followed by the cell wall and then the plasma membrane. A special membranous structure is the mesosome which is formed by the extensions of plasma membrane into the cell in the form of vesicles, tubules and lamellae.",
          "In prokaryotes, ribosomes are about 15 nm by 20 nm in size and are made of two subunits - 50S and 30S units which when present together form 70S prokaryotic ribosomes.",
        ],
      },
    ],
    figure: NCERT_CELL_FIGURES.fig_8_2,
  },
  {
    pageNumber: 4,
    headerLeft: "CELL: THE UNIT OF LIFE",
    headerRight: "NCERT PAGE 129–131",
    columns: [
      {
        heading: "8.5 EUKARYOTIC CELLS",
        paragraphs: [
          "The eukaryotes include all the protists, plants, animals and fungi. In eukaryotic cells there is an extensive compartmentalisation of cytoplasm through the presence of membrane bound organelles. Plant and animal cells are different as the former possess cell walls, plastids and a large central vacuole which are absent in animal cells. On the other hand, animal cells have centrioles which are absent in almost all plant cells (Figure 8.3).",
        ],
      },
      {
        heading: "8.5.1 CELL MEMBRANE",
        paragraphs: [
          "In human beings, the membrane of the erythrocyte has approximately 52 per cent protein and 40 per cent lipids. Depending on the ease of extraction, membrane proteins can be classified as integral and peripheral.",
          "An improved model of the structure of cell membrane was proposed by Singer and Nicolson (1972) widely accepted as fluid mosaic model (Figure 8.4). According to this, the quasi-fluid nature of lipid enables lateral movement of proteins within the overall bilayer.",
        ],
      },
    ],
    figure: NCERT_CELL_FIGURES.fig_8_3,
  },
  {
    pageNumber: 5,
    headerLeft: "BIOLOGY · CLASS XI",
    headerRight: "NCERT PAGE 137–138",
    columns: [
      {
        heading: "8.5.8 CILIA AND FLAGELLA",
        paragraphs: [
          "Cilia and flagella are hair-like outgrowths of the cell membrane. Cilia are small structures which work like oars, causing the movement of either the cell or the surrounding fluid. Flagella are comparatively longer and responsible for cell movement. Their core called the axoneme possesses a number of microtubules running parallel to the long axis (9+2 array of nine doublets of radially arranged peripheral microtubules and a pair of centrally located microtubules).",
        ],
      },
      {
        heading: "8.5.9 CENTROSOME AND CENTRIOLES",
        paragraphs: [
          "Centrosome is an organelle usually containing two cylindrical structures called centrioles. They are surrounded by amorphous pericentriolar materials. Both the centrioles in a centrosome lie perpendicular to each other in which each has an organisation like the cartwheel.",
          "They are made up of nine evenly spaced peripheral fibrils of tubulin protein. Each of the peripheral fibril is a triplet. The adjacent triplets are also linked. The central part of the proximal region of the centriole is also proteinaceous and called the hub, which is connected with tubules of the peripheral triplets by radial spokes made of protein. The centrioles form the basal body of cilia or flagella, and spindle fibres that give rise to spindle apparatus during cell division in animal cells.",
        ],
      },
    ],
    figure: NCERT_CELL_FIGURES.fig_8_4,
  },
];

const docCache = new Map<string, Promise<pdfjsLib.PDFDocumentProxy>>();

async function loadDocument(dataUrl: string): Promise<pdfjsLib.PDFDocumentProxy> {
  if (!dataUrl) throw new Error("PDF source data is empty.");
  const existing = docCache.get(dataUrl);
  if (existing) return existing;

  const promise = (async () => {
    const base64Marker = ";base64,";
    const base64Index = dataUrl.indexOf(base64Marker);
    if (base64Index !== -1) {
      const base64 = dataUrl.substring(base64Index + base64Marker.length).trim();
      const raw = atob(base64);
      const bytes = new Uint8Array(raw.length);
      for (let i = 0; i < raw.length; i++) {
        bytes[i] = raw.charCodeAt(i);
      }
      return await pdfjsLib.getDocument({ data: bytes }).promise;
    }
    return await pdfjsLib.getDocument({ url: dataUrl }).promise;
  })();

  promise.catch(() => {
    docCache.delete(dataUrl);
  });

  docCache.set(dataUrl, promise);
  return promise;
}

/**
 * Reconstructs text from a PDF page while preserving line breaks, two-column reading order,
 * and section headings (e.g., "8.5.9 Centrosome and Centrioles") so topic boundaries remain intact.
 */
function reconstructStructuredPageText(items: any[], pageWidth: number): {
  text: string;
  lines: string[];
} {
  interface PositionedRun {
    str: string;
    x: number;
    y: number;
    width: number;
    height: number;
    hasEOL: boolean;
  }

  const runs: PositionedRun[] = [];
  for (const item of items) {
    if (!item || typeof item.str !== "string") continue;
    const raw = item.str;
    if (!raw.trim() && !item.hasEOL) continue;
    const tx = Array.isArray(item.transform) ? item.transform : [1, 0, 0, 1, 0, 0];
    runs.push({
      str: raw,
      x: Number(tx[4]) || 0,
      y: Number(tx[5]) || 0,
      width: Number(item.width) || 0,
      height: Math.abs(Number(tx[3]) || Number(item.height) || 10),
      hasEOL: Boolean(item.hasEOL),
    });
  }

  if (runs.length === 0) return { text: "", lines: [] };

  const midX = pageWidth * 0.5;
  const leftRuns = runs.filter((r) => r.x + r.width <= midX + 12 && r.x < midX * 0.85);
  const rightRuns = runs.filter((r) => r.x >= midX - 12);
  const isTwoColumn =
    pageWidth > 400 &&
    leftRuns.length > 15 &&
    rightRuns.length > 15 &&
    leftRuns.length + rightRuns.length >= runs.length * 0.75;

  const assembleColumnLines = (colRuns: PositionedRun[]): string[] => {
    if (colRuns.length === 0) return [];
    const sorted = [...colRuns].sort((a, b) => {
      if (Math.abs(a.y - b.y) > 3.5) return b.y - a.y;
      return a.x - b.x;
    });

    const lines: string[] = [];
    let currentLine = "";
    let prevY: number | null = null;
    let prevEndX: number | null = null;

    for (const run of sorted) {
      const piece = run.str;
      if (prevY === null) {
        currentLine = piece;
      } else {
        const dy = Math.abs(prevY - run.y);
        const lineThreshold = Math.max(3.5, run.height * 0.45);
        if (dy > lineThreshold) {
          if (currentLine.trim()) lines.push(currentLine.replace(/\s+/g, " ").trim());
          currentLine = piece;
        } else {
          const gap = prevEndX !== null ? run.x - prevEndX : 0;
          const needsSpace =
            currentLine.length > 0 &&
            !currentLine.endsWith(" ") &&
            !piece.startsWith(" ") &&
            (gap > 1.5 || /[A-Za-z0-9.,;:)\]]$/.test(currentLine));
          currentLine += (needsSpace ? " " : "") + piece;
        }
      }
      prevY = run.y;
      prevEndX = run.x + run.width;
      if (run.hasEOL && currentLine.trim()) {
        lines.push(currentLine.replace(/\s+/g, " ").trim());
        currentLine = "";
        prevY = null;
        prevEndX = null;
      }
    }
    if (currentLine.trim()) {
      lines.push(currentLine.replace(/\s+/g, " ").trim());
    }
    return lines;
  };

  let allLines: string[] = [];
  if (isTwoColumn) {
    const headerRuns: PositionedRun[] = [];
    const colLeft: PositionedRun[] = [];
    const colRight: PositionedRun[] = [];
    for (const r of runs) {
      if (r.x < midX - 20 && r.x + r.width > midX + 40) {
        headerRuns.push(r);
      } else if (r.x < midX - 8) {
        colLeft.push(r);
      } else {
        colRight.push(r);
      }
    }
    allLines = [
      ...assembleColumnLines(headerRuns),
      ...assembleColumnLines(colLeft),
      ...assembleColumnLines(colRight),
    ];
  } else {
    allLines = assembleColumnLines(runs);
  }

  return {
    text: allLines.join("\n"),
    lines: allLines,
  };
}

function fileToDataUrl(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result || ""));
    reader.onerror = () => reject(reader.error || new Error("Failed to read PDF file"));
    reader.readAsDataURL(file);
  });
}

/**
 * Extracts all pages, preserves two-column NCERT line structure, renders page previews,
 * and crops figure regions when "Figure X.Y" captions are present on a page.
 */
export async function extractPdfPagesAndFigures(file: File): Promise<{
  pageCount: number;
  pages: ExtractedPdfPage[];
  dataUrl: string;
}> {
  const dataUrl = await fileToDataUrl(file);
  const arrayBuffer = await file.arrayBuffer();
  const pdf = await pdfjsLib.getDocument({ data: new Uint8Array(arrayBuffer) }).promise;
  const pageCount = pdf.numPages;
  const maxPagesToScan = Math.min(pageCount, 30);
  const pages: ExtractedPdfPage[] = [];

  for (let p = 1; p <= maxPagesToScan; p++) {
    const page = await pdf.getPage(p);
    const viewport1x = page.getViewport({ scale: 1.0 });
    const textContent = await page.getTextContent();
    const { text, lines } = reconstructStructuredPageText(
      textContent.items as any[],
      viewport1x.width
    );

    let pageImageDataUrl: string | undefined;
    const extractedFigures: ExtractedFigure[] = [];

    try {
      const renderViewport = page.getViewport({ scale: 1.35 });
      const canvas = document.createElement("canvas");
      canvas.width = Math.floor(renderViewport.width);
      canvas.height = Math.floor(renderViewport.height);
      const ctx = canvas.getContext("2d");
      if (ctx) {
        await page.render({
          canvasContext: ctx,
          viewport: renderViewport,
          canvas,
        } as any).promise;
        pageImageDataUrl = canvas.toDataURL("image/jpeg", 0.84);

        // Detect Figure captions on this page and crop around the figure region
        const figRegex = /\b(Fig(?:ure|\.)?\s*\d+(?:\.\d+)?)\s*[:.-]?\s*([^\n]{4,140})/gi;
        let match: RegExpExecArray | null;
        let figIdx = 0;
        while ((match = figRegex.exec(text)) !== null && figIdx < 3) {
          figIdx++;
          const figLabel = match[1].replace(/^Fig\.?/i, "Figure").trim();
          const figCaption = match[2].trim();
          extractedFigures.push({
            id: `pdf-p${p}-fig-${figIdx}`,
            label: figLabel,
            caption: figCaption,
            pageNumber: p,
            svgDataUrl: pageImageDataUrl,
          });
        }
      }
    } catch {
      // Continue with extracted text if canvas rendering is unavailable
    }

    pages.push({
      pageNumber: p,
      text,
      lines,
      pageImageDataUrl,
      extractedFigures,
    });
  }

  return { pageCount, pages, dataUrl };
}

export async function loadPdfSummary(dataUrl: string): Promise<LoadedPdfSummary> {
  const doc = await loadDocument(dataUrl);
  return { pageCount: doc.numPages };
}
