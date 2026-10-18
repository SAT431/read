export type SubjectId = "botany" | "zoology" | "physics" | "chemistry";

export interface SubjectInfo {
  id: SubjectId;
  name: string;
  tagline: string;
  accent: string;
  softBg: string;
  borderColor: string;
  iconName: string;
}

export interface StudyChapter {
  id: string;
  subjectId: SubjectId;
  title: string;
  unitNumber: number;
  chapterNumber: number;
  classLevel: 11 | 12;
  highYield?: boolean;
}

export interface NcertTopic {
  id: string;
  number: string;
  title: string;
  pageStart: number;
  pageEnd: number;
  summary: string;
  subtopics: string[];
  rawText: string;
  figures: ExtractedFigure[];
  precompiledSections?: NcertSectionBlock[];
}

export interface ExtractedFigure {
  id: string;
  label: string;
  caption: string;
  pageNumber: number;
  svgDataUrl: string;
  bbox?: [number, number, number, number];
}

export type NcertCardVariant =
  | "purple-card"
  | "teal-card"
  | "timeline-group"
  | "theory-card"
  | "figure-card"
  | "ai-table"
  | "ai-explanation"
  | "ai-quiz";

export interface TimelineItem {
  id: string;
  theme: "schleiden" | "schwann" | "collaboration" | "virchow";
  scientist: string;
  year?: string;
  descriptor?: string;
  bullets?: string[];
  paragraphs?: string[];
  statement?: string;
  latinQuote?: string;
}

export interface NcertCardBlock {
  id: string;
  variant: NcertCardVariant;
  title?: string;
  paragraphs?: string[];
  capabilities?: { roman: string; text: string }[];
  importantGreen?: string;
  scientists?: { name: string; contribution: string }[];
  footerNote?: string;
  timelineItems?: TimelineItem[];
  theoryRows?: { roman: string; text: string }[];
  figure?: ExtractedFigure;
  tableData?: {
    headers: string[];
    rows: string[][];
    caption?: string;
  };
  quizData?: {
    question: string;
    options: string[];
    answerIndex: number;
    explanation: string;
  };
  aiGenerated?: boolean;
  sourceTopicId?: string;
}

export interface NcertSectionBlock {
  id: string;
  sectionNo: string;
  title: string;
  topicId: string;
  cards: NcertCardBlock[];
}

export interface LibraryPdfItem {
  id: string;
  chapterId: string;
  subjectId: SubjectId;
  title: string;
  fileName: string;
  pageCount: number;
  addedAt: number;
  fileSizeLabel: string;
  isBuiltInNcert?: boolean;
  dataUrl?: string;
  topics: NcertTopic[];
  scanned: boolean;
}

export const SUBJECTS: SubjectInfo[] = [
  {
    id: "botany",
    name: "Botany",
    tagline: "Plants, cells, inheritance and ecosystems",
    accent: "#147d5b",
    softBg: "#eaf9f2",
    borderColor: "#bcead4",
    iconName: "Leaf",
  },
  {
    id: "zoology",
    name: "Zoology",
    tagline: "Animals, human systems and evolution",
    accent: "#7047d8",
    softBg: "#f2ebff",
    borderColor: "#dccafa",
    iconName: "Dna",
  },
  {
    id: "physics",
    name: "Physics",
    tagline: "Motion, energy and the laws of nature",
    accent: "#2c63cc",
    softBg: "#eaf2ff",
    borderColor: "#cbdffc",
    iconName: "Atom",
  },
  {
    id: "chemistry",
    name: "Chemistry",
    tagline: "Matter, reactions and molecular connections",
    accent: "#b75b0e",
    softBg: "#fff4e6",
    borderColor: "#f5d9b8",
    iconName: "FlaskConical",
  },
];

export const CHAPTERS: StudyChapter[] = [
  // Botany
  { id: "botany-01", subjectId: "botany", title: "The Living World", unitNumber: 1, chapterNumber: 1, classLevel: 11, highYield: true },
  { id: "botany-02", subjectId: "botany", title: "Biological Classification", unitNumber: 1, chapterNumber: 2, classLevel: 11, highYield: true },
  { id: "botany-03", subjectId: "botany", title: "Plant Kingdom", unitNumber: 1, chapterNumber: 3, classLevel: 11 },
  { id: "botany-04", subjectId: "botany", title: "Morphology of Flowering Plants", unitNumber: 2, chapterNumber: 4, classLevel: 11 },
  { id: "botany-05", subjectId: "botany", title: "Anatomy of Flowering Plants", unitNumber: 2, chapterNumber: 5, classLevel: 11 },
  { id: "botany-06", subjectId: "botany", title: "Cell: The Unit of Life", unitNumber: 3, chapterNumber: 8, classLevel: 11, highYield: true },
  { id: "botany-07", subjectId: "botany", title: "Biomolecules", unitNumber: 3, chapterNumber: 9, classLevel: 11, highYield: true },
  { id: "botany-08", subjectId: "botany", title: "Cell Cycle and Cell Division", unitNumber: 3, chapterNumber: 10, classLevel: 11, highYield: true },
  { id: "botany-09", subjectId: "botany", title: "Photosynthesis in Higher Plants", unitNumber: 4, chapterNumber: 11, classLevel: 11 },
  { id: "botany-10", subjectId: "botany", title: "Respiration in Plants", unitNumber: 4, chapterNumber: 12, classLevel: 11 },
  { id: "botany-11", subjectId: "botany", title: "Plant Growth and Development", unitNumber: 4, chapterNumber: 13, classLevel: 11 },
  { id: "botany-12", subjectId: "botany", title: "Sexual Reproduction in Flowering Plants", unitNumber: 6, chapterNumber: 1, classLevel: 12 },
  { id: "botany-13", subjectId: "botany", title: "Principles of Inheritance and Variation", unitNumber: 7, chapterNumber: 4, classLevel: 12, highYield: true },
  { id: "botany-14", subjectId: "botany", title: "Molecular Basis of Inheritance", unitNumber: 7, chapterNumber: 5, classLevel: 12, highYield: true },
  { id: "botany-15", subjectId: "botany", title: "Biotechnology: Principles and Processes", unitNumber: 9, chapterNumber: 9, classLevel: 12, highYield: true },
  { id: "botany-16", subjectId: "botany", title: "Biotechnology and its Applications", unitNumber: 9, chapterNumber: 10, classLevel: 12, highYield: true },
  { id: "botany-17", subjectId: "botany", title: "Organisms and Populations", unitNumber: 10, chapterNumber: 11, classLevel: 12 },
  { id: "botany-18", subjectId: "botany", title: "Ecosystem", unitNumber: 10, chapterNumber: 12, classLevel: 12 },
  { id: "botany-19", subjectId: "botany", title: "Biodiversity and Conservation", unitNumber: 10, chapterNumber: 13, classLevel: 12 },

  // Zoology
  { id: "zoology-01", subjectId: "zoology", title: "Animal Kingdom", unitNumber: 1, chapterNumber: 4, classLevel: 11, highYield: true },
  { id: "zoology-02", subjectId: "zoology", title: "Structural Organisation in Animals", unitNumber: 2, chapterNumber: 7, classLevel: 11 },
  { id: "zoology-03", subjectId: "zoology", title: "Breathing and Exchange of Gases", unitNumber: 5, chapterNumber: 14, classLevel: 11 },
  { id: "zoology-04", subjectId: "zoology", title: "Body Fluids and Circulation", unitNumber: 5, chapterNumber: 15, classLevel: 11, highYield: true },
  { id: "zoology-05", subjectId: "zoology", title: "Excretory Products and their Elimination", unitNumber: 5, chapterNumber: 16, classLevel: 11 },
  { id: "zoology-06", subjectId: "zoology", title: "Locomotion and Movement", unitNumber: 5, chapterNumber: 17, classLevel: 11 },
  { id: "zoology-07", subjectId: "zoology", title: "Neural Control and Coordination", unitNumber: 5, chapterNumber: 18, classLevel: 11 },
  { id: "zoology-08", subjectId: "zoology", title: "Chemical Coordination and Integration", unitNumber: 5, chapterNumber: 19, classLevel: 11 },
  { id: "zoology-09", subjectId: "zoology", title: "Human Reproduction", unitNumber: 6, chapterNumber: 2, classLevel: 12, highYield: true },
  { id: "zoology-10", subjectId: "zoology", title: "Reproductive Health", unitNumber: 6, chapterNumber: 3, classLevel: 12 },
  { id: "zoology-11", subjectId: "zoology", title: "Evolution", unitNumber: 7, chapterNumber: 6, classLevel: 12, highYield: true },
  { id: "zoology-12", subjectId: "zoology", title: "Human Health and Disease", unitNumber: 8, chapterNumber: 7, classLevel: 12, highYield: true },
  { id: "zoology-13", subjectId: "zoology", title: "Microbes in Human Welfare", unitNumber: 8, chapterNumber: 8, classLevel: 12 },

  // Physics
  { id: "physics-01", subjectId: "physics", title: "Units and Measurements", unitNumber: 1, chapterNumber: 1, classLevel: 11 },
  { id: "physics-02", subjectId: "physics", title: "Motion in a Straight Line & Plane", unitNumber: 2, chapterNumber: 2, classLevel: 11 },
  { id: "physics-03", subjectId: "physics", title: "Laws of Motion", unitNumber: 3, chapterNumber: 3, classLevel: 11, highYield: true },
  { id: "physics-04", subjectId: "physics", title: "Work, Energy and Power", unitNumber: 4, chapterNumber: 4, classLevel: 11 },
  { id: "physics-05", subjectId: "physics", title: "System of Particles and Rotational Motion", unitNumber: 5, chapterNumber: 5, classLevel: 11, highYield: true },
  { id: "physics-06", subjectId: "physics", title: "Gravitation", unitNumber: 6, chapterNumber: 6, classLevel: 11 },
  { id: "physics-07", subjectId: "physics", title: "Mechanical Properties of Solids & Fluids", unitNumber: 7, chapterNumber: 7, classLevel: 11 },
  { id: "physics-08", subjectId: "physics", title: "Thermodynamics", unitNumber: 8, chapterNumber: 8, classLevel: 11, highYield: true },
  { id: "physics-09", subjectId: "physics", title: "Oscillations and Waves", unitNumber: 10, chapterNumber: 10, classLevel: 11 },
  { id: "physics-10", subjectId: "physics", title: "Electrostatics", unitNumber: 11, chapterNumber: 1, classLevel: 12, highYield: true },
  { id: "physics-11", subjectId: "physics", title: "Current Electricity", unitNumber: 12, chapterNumber: 2, classLevel: 12, highYield: true },
  { id: "physics-12", subjectId: "physics", title: "Moving Charges and Magnetism", unitNumber: 13, chapterNumber: 3, classLevel: 12 },
  { id: "physics-13", subjectId: "physics", title: "Ray and Wave Optics", unitNumber: 16, chapterNumber: 6, classLevel: 12, highYield: true },
  { id: "physics-14", subjectId: "physics", title: "Dual Nature of Radiation and Matter", unitNumber: 17, chapterNumber: 7, classLevel: 12 },
  { id: "physics-15", subjectId: "physics", title: "Semiconductor Electronics", unitNumber: 19, chapterNumber: 9, classLevel: 12, highYield: true },

  // Chemistry
  { id: "chemistry-01", subjectId: "chemistry", title: "Some Basic Concepts of Chemistry", unitNumber: 1, chapterNumber: 1, classLevel: 11 },
  { id: "chemistry-02", subjectId: "chemistry", title: "Structure of Atom", unitNumber: 2, chapterNumber: 2, classLevel: 11, highYield: true },
  { id: "chemistry-03", subjectId: "chemistry", title: "Chemical Bonding and Molecular Structure", unitNumber: 3, chapterNumber: 4, classLevel: 11, highYield: true },
  { id: "chemistry-04", subjectId: "chemistry", title: "Chemical Thermodynamics", unitNumber: 4, chapterNumber: 5, classLevel: 11, highYield: true },
  { id: "chemistry-05", subjectId: "chemistry", title: "Equilibrium", unitNumber: 6, chapterNumber: 6, classLevel: 11, highYield: true },
  { id: "chemistry-06", subjectId: "chemistry", title: "Redox Reactions and Electrochemistry", unitNumber: 7, chapterNumber: 7, classLevel: 11 },
  { id: "chemistry-07", subjectId: "chemistry", title: "Some Basic Principles of Organic Chemistry", unitNumber: 14, chapterNumber: 8, classLevel: 11, highYield: true },
  { id: "chemistry-08", subjectId: "chemistry", title: "Hydrocarbons", unitNumber: 15, chapterNumber: 9, classLevel: 11 },
  { id: "chemistry-09", subjectId: "chemistry", title: "Solutions & Chemical Kinetics", unitNumber: 5, chapterNumber: 1, classLevel: 12, highYield: true },
  { id: "chemistry-10", subjectId: "chemistry", title: "Coordination Compounds", unitNumber: 12, chapterNumber: 5, classLevel: 12, highYield: true },
  { id: "chemistry-11", subjectId: "chemistry", title: "Aldehydes, Ketones and Carboxylic Acids", unitNumber: 17, chapterNumber: 8, classLevel: 12, highYield: true },
  { id: "chemistry-12", subjectId: "chemistry", title: "Biomolecules", unitNumber: 19, chapterNumber: 10, classLevel: 12 },
];

function makeSvgDataUrl(svg: string): string {
  return `data:image/svg+xml;utf8,${encodeURIComponent(svg.trim())}`;
}

export const NCERT_CELL_FIGURES: Record<string, ExtractedFigure> = {
  fig_8_1: {
    id: "fig-8-1",
    label: "Figure 8.1",
    caption: "Diagram showing different shapes of the cells: (a) Red blood cells (round and biconcave), (b) White blood cells (amoeboid), (c) Columnar epithelial cells (long and narrow), (d) Nerve cell (branched and long), (e) A tracheid (elongated), (f) Mesophyll cells (round and oval)",
    pageNumber: 2,
    svgDataUrl: makeSvgDataUrl(`
      <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 820 340" width="820" height="340">
        <defs>
          <linearGradient id="bg" x1="0" y1="0" x2="1" y2="1">
            <stop offset="0%" stop-color="#fcfbff"/>
            <stop offset="100%" stop-color="#f3f7fc"/>
          </linearGradient>
          <radialGradient id="rbc" cx="50%" cy="50%" r="50%">
            <stop offset="0%" stop-color="#ff9b9b"/>
            <stop offset="45%" stop-color="#e34242"/>
            <stop offset="85%" stop-color="#b91c1c"/>
            <stop offset="100%" stop-color="#7f1d1d"/>
          </radialGradient>
        </defs>
        <rect width="820" height="340" rx="18" fill="url(#bg)" stroke="#dfe7f2" stroke-width="2"/>
        <text x="26" y="34" font-family="Space Grotesk, sans-serif" font-size="13" font-weight="700" fill="#5b36bc">NCERT EXTRACTED FIGURE 8.1 · DIVERSITY IN CELL SHAPES &amp; SIZES</text>
        
        <g transform="translate(45, 65)">
          <rect width="210" height="115" rx="12" fill="#fff" stroke="#e4e8f1"/>
          <circle cx="65" cy="52" r="26" fill="url(#rbc)"/>
          <circle cx="65" cy="52" r="11" fill="#ff9b9b" opacity="0.55"/>
          <ellipse cx="135" cy="55" rx="30" ry="13" fill="url(#rbc)" transform="rotate(-12 135 55)"/>
          <text x="105" y="100" text-anchor="middle" font-family="DM Sans, sans-serif" font-size="12" font-weight="700" fill="#1f2441">Red blood cells (round &amp; biconcave)</text>
        </g>

        <g transform="translate(285, 65)">
          <rect width="210" height="115" rx="12" fill="#fff" stroke="#e4e8f1"/>
          <path d="M75,45 C65,25 95,18 110,30 C130,20 150,35 140,55 C155,70 130,85 110,75 C90,88 65,75 72,58 C55,52 65,45 75,45 Z" fill="#e9defe" stroke="#7047d8" stroke-width="2"/>
          <path d="M95,48 C95,38 112,38 115,48 C118,58 100,64 95,55 Z" fill="#5b36bc"/>
          <text x="105" y="100" text-anchor="middle" font-family="DM Sans, sans-serif" font-size="12" font-weight="700" fill="#1f2441">White blood cells (amoeboid)</text>
        </g>

        <g transform="translate(525, 65)">
          <rect width="250" height="115" rx="12" fill="#fff" stroke="#e4e8f1"/>
          <g transform="translate(68, 16)">
            <rect x="0" y="0" width="26" height="64" rx="4" fill="#ffe8d6" stroke="#b75b0e" stroke-width="1.6"/>
            <ellipse cx="13" cy="46" rx="6" ry="10" fill="#b75b0e"/>
            <rect x="26" y="0" width="26" height="64" rx="4" fill="#ffe8d6" stroke="#b75b0e" stroke-width="1.6"/>
            <ellipse cx="39" cy="46" rx="6" ry="10" fill="#b75b0e"/>
            <rect x="52" y="0" width="26" height="64" rx="4" fill="#ffe8d6" stroke="#b75b0e" stroke-width="1.6"/>
            <ellipse cx="65" cy="46" rx="6" ry="10" fill="#b75b0e"/>
            <rect x="78" y="0" width="26" height="64" rx="4" fill="#ffe8d6" stroke="#b75b0e" stroke-width="1.6"/>
            <ellipse cx="91" cy="46" rx="6" ry="10" fill="#b75b0e"/>
          </g>
          <text x="125" y="100" text-anchor="middle" font-family="DM Sans, sans-serif" font-size="12" font-weight="700" fill="#1f2441">Columnar epithelial cells (long &amp; narrow)</text>
        </g>

        <g transform="translate(45, 200)">
          <rect width="340" height="115" rx="12" fill="#fff" stroke="#e4e8f1"/>
          <circle cx="65" cy="52" r="20" fill="#fff1dc" stroke="#d97706" stroke-width="2"/>
          <circle cx="65" cy="52" r="7" fill="#b45309"/>
          <path d="M45,52 L25,36 M45,52 L22,52 M45,52 L25,68 M65,32 L52,15 M65,72 L52,88" stroke="#d97706" stroke-width="2" stroke-linecap="round"/>
          <path d="M85,52 Q170,48 275,52" stroke="#d97706" stroke-width="3.5" fill="none"/>
          <path d="M275,52 L302,35 M275,52 L306,52 M275,52 L302,69" stroke="#d97706" stroke-width="2" stroke-linecap="round"/>
          <text x="170" y="102" text-anchor="middle" font-family="DM Sans, sans-serif" font-size="12" font-weight="700" fill="#1f2441">Nerve cell (branched and long)</text>
        </g>

        <g transform="translate(410, 200)">
          <rect width="365" height="115" rx="12" fill="#fff" stroke="#e4e8f1"/>
          <path d="M28,55 L95,38 L162,55 L95,72 Z" fill="#e0f2fe" stroke="#087c86" stroke-width="2"/>
          <circle cx="75" cy="55" r="3" fill="#087c86"/>
          <circle cx="95" cy="55" r="3" fill="#087c86"/>
          <circle cx="115" cy="55" r="3" fill="#087c86"/>
          <text x="95" y="102" text-anchor="middle" font-family="DM Sans, sans-serif" font-size="12" font-weight="700" fill="#1f2441">A tracheid (elongated)</text>

          <g transform="translate(225, 22)">
            <ellipse cx="32" cy="24" rx="22" ry="15" fill="#dcfce7" stroke="#147d5b" stroke-width="2"/>
            <ellipse cx="68" cy="28" rx="22" ry="15" fill="#dcfce7" stroke="#147d5b" stroke-width="2"/>
            <ellipse cx="48" cy="48" rx="22" ry="15" fill="#dcfce7" stroke="#147d5b" stroke-width="2"/>
            <circle cx="28" cy="22" r="3" fill="#147d5b"/>
            <circle cx="65" cy="26" r="3" fill="#147d5b"/>
            <circle cx="48" cy="48" r="3" fill="#147d5b"/>
          </g>
          <text x="275" y="102" text-anchor="middle" font-family="DM Sans, sans-serif" font-size="12" font-weight="700" fill="#1f2441">Mesophyll cells (round &amp; oval)</text>
        </g>
      </svg>
    `),
  },
  fig_8_2: {
    id: "fig-8-2",
    label: "Figure 8.2",
    caption: "Comparison of Eukaryotic cell (10–20 µm), Typical bacteria (1–2 µm), PPLO (about 0.1 µm), and Viruses (0.02–0.2 µm)",
    pageNumber: 3,
    svgDataUrl: makeSvgDataUrl(`
      <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 820 290" width="820" height="290">
        <rect width="820" height="290" rx="18" fill="#f9fbfd" stroke="#dfe7f2" stroke-width="2"/>
        <text x="26" y="34" font-family="Space Grotesk, sans-serif" font-size="13" font-weight="700" fill="#087c86">NCERT EXTRACTED FIGURE 8.2 · PROKARYOTIC &amp; EUKARYOTIC CELL SIZE COMPARISON</text>
        <g transform="translate(45, 58)">
          <rect width="230" height="195" rx="14" fill="#fff" stroke="#d2eef0"/>
          <circle cx="115" cy="88" r="58" fill="#eaf9f2" stroke="#147d5b" stroke-width="2.5"/>
          <circle cx="115" cy="88" r="22" fill="#bbf7d0" stroke="#147d5b" stroke-width="2"/>
          <text x="115" y="92" text-anchor="middle" font-family="DM Sans, sans-serif" font-size="11" font-weight="700" fill="#14532d">Nucleus</text>
          <text x="115" y="166" text-anchor="middle" font-family="DM Sans, sans-serif" font-size="13" font-weight="700" fill="#1f2441">Typical Eukaryotic Cell</text>
          <text x="115" y="183" text-anchor="middle" font-family="Space Grotesk, sans-serif" font-size="12" font-weight="700" fill="#147d5b">(10 – 20 µm)</text>
        </g>
        <g transform="translate(300, 58)">
          <rect width="160" height="195" rx="14" fill="#fff" stroke="#d2eef0"/>
          <rect x="48" y="68" width="64" height="34" rx="17" fill="#fef3c7" stroke="#b75b0e" stroke-width="2.5"/>
          <path d="M62,85 Q80,75 98,85" stroke="#b75b0e" stroke-width="1.8" fill="none"/>
          <text x="80" y="166" text-anchor="middle" font-family="DM Sans, sans-serif" font-size="13" font-weight="700" fill="#1f2441">Typical Bacteria</text>
          <text x="80" y="183" text-anchor="middle" font-family="Space Grotesk, sans-serif" font-size="12" font-weight="700" fill="#b75b0e">(1 – 2 µm)</text>
        </g>
        <g transform="translate(485, 58)">
          <rect width="140" height="195" rx="14" fill="#fff" stroke="#d2eef0"/>
          <circle cx="70" cy="85" r="14" fill="#f3e8ff" stroke="#7047d8" stroke-width="2.5"/>
          <text x="70" y="166" text-anchor="middle" font-family="DM Sans, sans-serif" font-size="13" font-weight="700" fill="#1f2441">PPLO</text>
          <text x="70" y="183" text-anchor="middle" font-family="Space Grotesk, sans-serif" font-size="12" font-weight="700" fill="#7047d8">(~0.1 µm)</text>
        </g>
        <g transform="translate(650, 58)">
          <rect width="135" height="195" rx="14" fill="#fff" stroke="#d2eef0"/>
          <polygon points="68,68 82,78 82,94 68,104 54,94 54,78" fill="#e0f2fe" stroke="#2c63cc" stroke-width="2"/>
          <text x="68" y="166" text-anchor="middle" font-family="DM Sans, sans-serif" font-size="13" font-weight="700" fill="#1f2441">Viruses</text>
          <text x="68" y="183" text-anchor="middle" font-family="Space Grotesk, sans-serif" font-size="12" font-weight="700" fill="#2c63cc">(0.02 – 0.2 µm)</text>
        </g>
      </svg>
    `),
  },
  fig_8_3: {
    id: "fig-8-3",
    label: "Figure 8.3",
    caption: "Diagrammatic representation of (a) Plant cell and (b) Animal cell showing Plasma membrane, Cell wall, Nucleus, Endoplasmic reticulum, Mitochondria, Chloroplast, Golgi apparatus, Peroxisome, and Centriole",
    pageNumber: 4,
    svgDataUrl: makeSvgDataUrl(`
      <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 820 350" width="820" height="350">
        <rect width="820" height="350" rx="18" fill="#f8faff" stroke="#dfe7f2" stroke-width="2"/>
        <text x="26" y="34" font-family="Space Grotesk, sans-serif" font-size="13" font-weight="700" fill="#5b36bc">NCERT EXTRACTED FIGURE 8.3 · ULTRASTRUCTURE OF PLANT CELL VS ANIMAL CELL</text>
        <g transform="translate(36, 52)">
          <rect width="360" height="270" rx="18" fill="#f0fdf4" stroke="#147d5b" stroke-width="3"/>
          <rect x="10" y="10" width="340" height="250" rx="12" fill="#fff" stroke="#24a778" stroke-width="1.5" stroke-dasharray="5,3"/>
          <rect x="34" y="36" width="165" height="165" rx="34" fill="#e0f2fe" stroke="#0284c7" stroke-width="2"/>
          <text x="116" y="122" text-anchor="middle" font-family="DM Sans, sans-serif" font-size="13" font-weight="700" fill="#0369a1">Large Central Vacuole</text>
          <circle cx="268" cy="86" r="42" fill="#f3e8ff" stroke="#7047d8" stroke-width="2.5"/>
          <circle cx="268" cy="86" r="14" fill="#7047d8"/>
          <text x="268" y="142" text-anchor="middle" font-family="DM Sans, sans-serif" font-size="11" font-weight="700" fill="#5b36bc">Nucleus &amp; Nucleolus</text>
          <ellipse cx="265" cy="192" rx="46" ry="22" fill="#bbf7d0" stroke="#15803d" stroke-width="2"/>
          <text x="265" y="196" text-anchor="middle" font-family="DM Sans, sans-serif" font-size="11" font-weight="700" fill="#14532d">Chloroplast</text>
          <text x="180" y="246" text-anchor="middle" font-family="Fraunces, serif" font-size="15" font-weight="700" fill="#147d5b">(a) Plant Cell (Rigid Cell Wall + Plastids)</text>
        </g>
        <g transform="translate(424, 52)">
          <ellipse cx="180" cy="135" rx="170" ry="126" fill="#fff7ed" stroke="#dc4295" stroke-width="3"/>
          <circle cx="180" cy="118" r="46" fill="#fde9f3" stroke="#dc4295" stroke-width="2.5"/>
          <circle cx="180" cy="118" r="15" fill="#a32b6f"/>
          <text x="180" y="178" text-anchor="middle" font-family="DM Sans, sans-serif" font-size="11" font-weight="700" fill="#a32b6f">Nucleus &amp; ER</text>
          <rect x="85" y="75" width="28" height="12" rx="4" fill="#dbeafe" stroke="#2c63cc" stroke-width="2"/>
          <rect x="95" y="65" width="12" height="28" rx="4" fill="#dbeafe" stroke="#2c63cc" stroke-width="2"/>
          <text x="98" y="108" text-anchor="middle" font-family="DM Sans, sans-serif" font-size="11" font-weight="700" fill="#1e40af">Centrosome</text>
          <ellipse cx="278" cy="105" rx="34" ry="16" fill="#ffedd5" stroke="#ea580c" stroke-width="2"/>
          <path d="M254,105 L264,98 L272,112 L282,98 L292,112 L300,105" stroke="#ea580c" stroke-width="1.8" fill="none"/>
          <text x="278" y="135" text-anchor="middle" font-family="DM Sans, sans-serif" font-size="11" font-weight="700" fill="#9a3412">Mitochondrion</text>
          <text x="180" y="232" text-anchor="middle" font-family="Fraunces, serif" font-size="15" font-weight="700" fill="#a32b6f">(b) Animal Cell (Centrioles · No Cell Wall)</text>
        </g>
      </svg>
    `),
  },
  fig_8_4: {
    id: "fig-8-4",
    label: "Figure 8.4",
    caption: "Fluid mosaic model of plasma membrane (Singer and Nicolson, 1972) showing lipid bilayer, integral protein, peripheral protein, sugar chains, and cholesterol",
    pageNumber: 5,
    svgDataUrl: makeSvgDataUrl(`
      <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 820 300" width="820" height="300">
        <rect width="820" height="300" rx="18" fill="#f8faff" stroke="#dfe7f2" stroke-width="2"/>
        <text x="26" y="34" font-family="Space Grotesk, sans-serif" font-size="13" font-weight="700" fill="#2c63cc">NCERT EXTRACTED FIGURE 8.4 · FLUID MOSAIC MODEL OF PLASMA MEMBRANE (1972)</text>
        <g transform="translate(60, 85)">
          <rect x="0" y="24" width="700" height="90" rx="12" fill="#fef9c3" opacity="0.7"/>
          <circle cx="40" cy="24" r="14" fill="#f97316"/><circle cx="72" cy="24" r="14" fill="#f97316"/><circle cx="104" cy="24" r="14" fill="#f97316"/>
          <circle cx="220" cy="24" r="14" fill="#f97316"/><circle cx="252" cy="24" r="14" fill="#f97316"/><circle cx="284" cy="24" r="14" fill="#f97316"/><circle cx="316" cy="24" r="14" fill="#f97316"/>
          <circle cx="440" cy="24" r="14" fill="#f97316"/><circle cx="472" cy="24" r="14" fill="#f97316"/><circle cx="504" cy="24" r="14" fill="#f97316"/><circle cx="630" cy="24" r="14" fill="#f97316"/><circle cx="662" cy="24" r="14" fill="#f97316"/>
          <circle cx="40" cy="114" r="14" fill="#f97316"/><circle cx="72" cy="114" r="14" fill="#f97316"/><circle cx="104" cy="114" r="14" fill="#f97316"/>
          <circle cx="220" cy="114" r="14" fill="#f97316"/><circle cx="252" cy="114" r="14" fill="#f97316"/><circle cx="284" cy="114" r="14" fill="#f97316"/><circle cx="316" cy="114" r="14" fill="#f97316"/>
          <circle cx="440" cy="114" r="14" fill="#f97316"/><circle cx="472" cy="114" r="14" fill="#f97316"/><circle cx="504" cy="114" r="14" fill="#f97316"/><circle cx="630" cy="114" r="14" fill="#f97316"/><circle cx="662" cy="114" r="14" fill="#f97316"/>
          <rect x="130" y="-6" width="66" height="150" rx="28" fill="#815ddf" stroke="#4c1d95" stroke-width="2.5"/>
          <text x="163" y="72" text-anchor="middle" font-family="DM Sans, sans-serif" font-size="11" font-weight="700" fill="#fff">Integral</text>
          <text x="163" y="86" text-anchor="middle" font-family="DM Sans, sans-serif" font-size="11" font-weight="700" fill="#fff">Protein</text>
          <rect x="530" y="-4" width="76" height="146" rx="30" fill="#087c86" stroke="#115e59" stroke-width="2.5"/>
          <text x="568" y="72" text-anchor="middle" font-family="DM Sans, sans-serif" font-size="11" font-weight="700" fill="#fff">Channel</text>
          <text x="568" y="86" text-anchor="middle" font-family="DM Sans, sans-serif" font-size="11" font-weight="700" fill="#fff">Protein</text>
          <ellipse cx="378" cy="12" rx="44" ry="20" fill="#ec4899" stroke="#9d174d" stroke-width="2"/>
          <text x="378" y="16" text-anchor="middle" font-family="DM Sans, sans-serif" font-size="11" font-weight="700" fill="#fff">Peripheral</text>
          <text x="350" y="175" text-anchor="middle" font-family="DM Sans, sans-serif" font-size="13" font-weight="700" fill="#1f2441">Quasi-fluid lipid bilayer enables lateral movement of proteins within the overall bilayer (Fluidity)</text>
        </g>
      </svg>
    `),
  },
};

export const BUILTIN_CELL_TOPICS: NcertTopic[] = [
  {
    id: "topic-8-1",
    number: "8.1",
    title: "What is a Cell?",
    pageStart: 1,
    pageEnd: 1,
    summary: "Independent existence, essential functions of life, fundamental unit of all living organisms, and milestone discoveries by Leeuwenhoek & Robert Brown.",
    subtopics: ["Unicellular organisms", "Independent existence", "Antonie Von Leeuwenhoek", "Robert Brown (Nucleus)", "Electron microscope"],
    rawText: `WHAT IS A CELL?
Unicellular organisms are capable of (i) independent existence and (ii) performing the essential functions of life. Anything less than a complete structure of a cell does not ensure independent living. Hence, cell is the fundamental structural and functional unit of all living organisms.
Antonie Von Leeuwenhoek first saw and described a live cell. Robert Brown later discovered the nucleus. The invention of the microscope and its improvement leading to the electron microscope revealed all the structural details of the cell.`,
    figures: [],
    precompiledSections: [
      {
        id: "sec-what-is-cell",
        sectionNo: "01",
        title: "What Is a Cell?",
        topicId: "topic-8-1",
        cards: [
          {
            id: "card-unicellular",
            variant: "purple-card",
            title: "Unicellular Organisms",
            paragraphs: [
              "Unicellular organisms are capable of:",
              "Anything less than a complete structure of a cell does not ensure independent living.",
            ],
            capabilities: [
              { roman: "(i)", text: "Independent existence" },
              { roman: "(ii)", text: "Performing the essential functions of life" },
            ],
            importantGreen: "Hence, **cell is the fundamental structural and functional unit of all living organisms.**",
          },
          {
            id: "card-discovery",
            variant: "teal-card",
            title: "Discovery of Cell Structures",
            scientists: [
              {
                name: "Antonie Von Leeuwenhoek",
                contribution: "first saw and described a live cell.",
              },
              {
                name: "Robert Brown",
                contribution: "later discovered the nucleus.",
              },
            ],
            footerNote: "The invention of the microscope and its improvement leading to the **electron microscope** revealed all the structural details of the cell.",
          },
        ],
      },
    ],
  },
  {
    id: "topic-8-2",
    number: "8.2",
    title: "Cell Theory",
    pageStart: 1,
    pageEnd: 2,
    summary: "Schleiden (1838), Schwann (1839), and Rudolf Virchow (1855 — Omnis cellula-e cellula) and the two cardinal principles of modern Cell Theory.",
    subtopics: ["Matthias Schleiden (1838)", "Theodore Schwann (1839)", "Plasma membrane & cell wall", "Rudolf Virchow (1855)", "Omnis cellula-e cellula"],
    rawText: `CELL THEORY
In 1838, Matthias Schleiden, a German botanist, examined a large number of plants and observed that all plants are composed of different kinds of cells which form the tissues of the plant. At about the same time, Theodore Schwann (1839), a German Zoologist, studied different types of animal cells and reported that cells had a thin outer layer which is today known as the ‘plasma membrane’. He also concluded, based on his studies on plant tissues, that the presence of cell wall is a unique character of the plant cells. On the basis of this, Schwann proposed the hypothesis that the bodies of animals and plants are composed of cells and products of cells.
Schleiden and Schwann together formulated the cell theory. This theory however, did not explain as to how new cells were formed. Rudolf Virchow (1855) first explained that cells divided and new cells are formed from pre-existing cells (Omnis cellula-e cellula). He modified the hypothesis of Schleiden and Schwann to give the cell theory a final shape. Cell theory as understood today is:
(i) all living organisms are composed of cells and products of cells.
(ii) all cells arise from pre-existing cells.`,
    figures: [],
    precompiledSections: [
      {
        id: "sec-cell-theory",
        sectionNo: "02",
        title: "Cell Theory",
        topicId: "topic-8-2",
        cards: [
          {
            id: "card-timeline-theory",
            variant: "timeline-group",
            timelineItems: [
              {
                id: "tl-schleiden",
                theme: "schleiden",
                scientist: "Matthias Schleiden",
                year: "1838",
                descriptor: "German botanist",
                bullets: [
                  "Examined a large number of plants.",
                  "Observed that **all plants are composed of different kinds of cells which form the tissues of the plant.**",
                ],
              },
              {
                id: "tl-schwann",
                theme: "schwann",
                scientist: "Theodore Schwann",
                year: "1839",
                descriptor: "German Zoologist",
                bullets: [
                  "Studied different types of animal cells.",
                  "Reported that cells had a thin outer layer which is today known as the **plasma membrane**.",
                  "Concluded, based on his studies on plant tissues, that the presence of **cell wall is a unique character of the plant cells**.",
                ],
                statement: "On the basis of this, Schwann proposed the hypothesis that the **bodies of animals and plants are composed of cells and products of cells.**",
              },
              {
                id: "tl-collab",
                theme: "collaboration",
                scientist: "Schleiden and Schwann",
                paragraphs: [
                  "Schleiden and Schwann together formulated the **cell theory**.",
                  "This theory however, **did not explain as to how new cells were formed**.",
                ],
              },
              {
                id: "tl-virchow",
                theme: "virchow",
                scientist: "Rudolf Virchow",
                year: "1855",
                paragraphs: [
                  "First explained that **cells divided and new cells are formed from pre-existing cells**.",
                  "He modified the hypothesis of Schleiden and Schwann to give the **cell theory a final shape**.",
                ],
                latinQuote: "Omnis cellula-e cellula",
              },
            ],
          },
        ],
      },
      {
        id: "sec-modern-theory",
        sectionNo: "03",
        title: "Cell Theory as Understood Today",
        topicId: "topic-8-2",
        cards: [
          {
            id: "card-modern-theory",
            variant: "theory-card",
            theoryRows: [
              {
                roman: "(i)",
                text: "All living organisms are composed of **cells and products of cells**.",
              },
              {
                roman: "(ii)",
                text: "All cells arise from **pre-existing cells**.",
              },
            ],
          },
        ],
      },
    ],
  },
  {
    id: "topic-8-3",
    number: "8.3",
    title: "An Overview of Cell",
    pageStart: 2,
    pageEnd: 3,
    summary: "Onion peel & human cheek cells, nucleus & chromosomes, cytoplasm as main arena of cellular activities, 70S/80S ribosomes, and diversity in cell size & shape (Mycoplasma, RBC, Nerve cells).",
    subtopics: ["Cytoplasm as main arena", "Membrane-bound vs non-membrane organelles", "Mycoplasma (0.3 µm)", "Bacteria (3–5 µm)", "Human RBC (7.0 µm)", "Cell shapes & Figure 8.1"],
    rawText: `8.3 AN OVERVIEW OF CELL
You have earlier observed cells in an onion peel and/or human cheek cells under the microscope. Let us recollect their structure. The onion cell which is a typical plant cell, has a distinct cell wall as its outer boundary and just within it is the cell membrane. The cells of the human cheek have an outer membrane as the delimiting structure of the cell. Inside each cell is a dense membrane bound structure called nucleus. This nucleus contains the chromosomes which in turn contain the genetic material, DNA. Cells that have membrane bound nuclei are called eukaryotic whereas cells that lack a membrane bound nucleus are prokaryotic. In both prokaryotic and eukaryotic cells, a semi-fluid matrix called cytoplasm occupies the volume of the cell. The cytoplasm is the main arena of cellular activities in both the plant and animal cells. Various chemical reactions occur in it to keep the cell in the ‘living state’.
Besides the nucleus, the eukaryotic cells have other membrane bound distinct structures called organelles like the endoplasmic reticulum (ER), the golgi complex, lysosomes, mitochondria, microbodies and vacuoles. The prokaryotic cells lack such membrane bound organelles.
Ribosomes are non-membrane bound organelles found in all cells – both eukaryotic as well as prokaryotic. Within the cell, ribosomes are found not only in the cytoplasm but also within the two organelles – chloroplasts (in plants) and mitochondria and on rough ER.
Animal cells contain another non-membrane bound organelle called centrosome which helps in cell division.
Cells differ greatly in size, shape and activities (Figure 8.1). For example, Mycoplasmas, the smallest cells, are only 0.3 µm in length while bacteria could be 3 to 5 µm. The largest isolated single cell is the egg of an ostrich. Among multicellular organisms, human red blood cells are about 7.0 µm in diameter. Nerve cells are some of the longest cells. Cells also vary greatly in their shape. They may be disc-like, polygonal, columnar, cuboid, thread like, or even irregular. The shape of the cell may vary with the function they perform.`,
    figures: [NCERT_CELL_FIGURES.fig_8_1],
    precompiledSections: [
      {
        id: "sec-overview",
        sectionNo: "04",
        title: "An Overview of Cell",
        topicId: "topic-8-3",
        cards: [
          {
            id: "card-overview-structure",
            variant: "purple-card",
            title: "Cell Boundaries, Nucleus & Cytoplasm",
            paragraphs: [
              "The **onion cell** (a typical plant cell) has a distinct **cell wall** as its outer boundary and just within it is the **cell membrane**. The cells of the **human cheek** have an outer membrane as the delimiting structure of the cell.",
              "Inside each cell is a dense membrane bound structure called **nucleus**, which contains the **chromosomes** that in turn contain the genetic material, **DNA**.",
            ],
            capabilities: [
              { roman: "(i)", text: "**Eukaryotic cells:** Have membrane-bound nuclei and membrane-bound organelles (ER, Golgi complex, lysosomes, mitochondria, microbodies, vacuoles)." },
              { roman: "(ii)", text: "**Prokaryotic cells:** Lack a membrane-bound nucleus and lack membrane-bound organelles." },
            ],
            importantGreen: "In both prokaryotic and eukaryotic cells, a semi-fluid matrix called **cytoplasm** occupies the volume of the cell. **The cytoplasm is the main arena of cellular activities in both the plant and animal cells.**",
          },
          {
            id: "card-non-membrane",
            variant: "teal-card",
            title: "Non-Membrane Bound Organelles & Cell Size Diversity",
            scientists: [
              {
                name: "Ribosomes (Non-membrane bound)",
                contribution: "Found in all cells (both eukaryotic and prokaryotic) — in cytoplasm, chloroplasts (in plants), mitochondria, and on rough ER.",
              },
              {
                name: "Centrosome (Non-membrane bound)",
                contribution: "Found in animal cells; helps in cell division.",
              },
              {
                name: "NCERT Cell Dimensions",
                contribution: "Mycoplasmas (smallest cells): 0.3 µm in length · Bacteria: 3 to 5 µm · Human RBC: about 7.0 µm in diameter · Largest isolated single cell: Egg of an ostrich · Longest cells: Nerve cells.",
              },
            ],
            footerNote: "Cells may be disc-like, polygonal, columnar, cuboid, thread-like, or irregular. **The shape of the cell may vary with the function they perform.**",
          },
          {
            id: "card-fig-8-1",
            variant: "figure-card",
            title: "Extracted NCERT Figure 8.1 · Diversity in Cell Shapes",
            figure: NCERT_CELL_FIGURES.fig_8_1,
          },
        ],
      },
    ],
  },
  {
    id: "topic-8-4",
    number: "8.4",
    title: "Prokaryotic Cells",
    pageStart: 3,
    pageEnd: 4,
    summary: "Bacteria, blue-green algae, mycoplasma and PPLO; four basic bacterial shapes (Bacillus, Coccus, Vibrio, Spirillum); glycocalyx, cell wall, mesosomes, plasmids, 70S ribosomes and inclusion bodies.",
    subtopics: ["Bacillus, Coccus, Vibrio, Spirillum", "Genomic DNA & Plasmid DNA", "Glycocalyx (Slime layer vs Capsule)", "Mesosome functions", "70S Ribosomes (50S + 30S) & Polysome", "Inclusion bodies"],
    rawText: `8.4 PROKARYOTIC CELLS
The prokaryotic cells are represented by bacteria, blue-green algae, mycoplasma and PPLO (Pleuro Pneumonia Like Organisms). They are generally smaller and multiply more rapidly than the eukaryotic cells (Figure 8.2). They may vary greatly in shape and size. The four basic shapes of bacteria are bacillus (rod like), coccus (spherical), vibrio (comma shaped) and spirillum (spiral).
All prokaryotes have a cell wall surrounding the cell membrane except in mycoplasma. The fluid matrix filling the cell is the cytoplasm. There is no well-defined nucleus. The genetic material is basically naked, not enveloped by a nuclear membrane. In addition to the genomic DNA (the single chromosome/circular DNA), many bacteria have small circular DNA outside the genomic DNA. These smaller DNA are called plasmids. The plasmid DNA confers certain unique phenotypic characters to such bacteria. One such character is resistance to antibiotics.
Most prokaryotic cells, particularly the bacterial cells, have a chemically complex cell envelope. The cell envelope consists of a tightly bound three layered structure i.e., the outermost glycocalyx followed by the cell wall and then the plasma membrane.
A special membranous structure is the mesosome which is formed by the extensions of plasma membrane into the cell. These extensions are in the form of vesicles, tubules and lamellae. They help in cell wall formation, DNA replication and distribution to daughter cells.
In prokaryotes, ribosomes are associated with the plasma membrane of the cell. They are about 15 nm by 20 nm in size and are made of two subunits - 50S and 30S units which when present together form 70S prokaryotic ribosomes. Several ribosomes may attach to a single mRNA and form a chain called polyribosomes or polysome.`,
    figures: [NCERT_CELL_FIGURES.fig_8_2],
    precompiledSections: [
      {
        id: "sec-prokaryotic",
        sectionNo: "05",
        title: "Prokaryotic Cells",
        topicId: "topic-8-4",
        cards: [
          {
            id: "card-prok-overview",
            variant: "purple-card",
            title: "Organization & Four Basic Shapes of Bacteria",
            paragraphs: [
              "Prokaryotic cells are represented by **bacteria, blue-green algae, mycoplasma and PPLO (Pleuro Pneumonia Like Organisms)**. They are smaller and multiply more rapidly than eukaryotic cells.",
              "The **four basic shapes of bacteria** are: **bacillus** (rod-like), **coccus** (spherical), **vibrio** (comma-shaped), and **spirillum** (spiral).",
            ],
            capabilities: [
              { roman: "(i)", text: "**Genomic DNA:** Single chromosome / circular naked DNA, not enveloped by a nuclear membrane." },
              { roman: "(ii)", text: "**Plasmids:** Small circular DNA outside genomic DNA; confers unique phenotypic characters such as **resistance to antibiotics**." },
            ],
            importantGreen: "All prokaryotes have a cell wall surrounding the cell membrane **except in Mycoplasma**.",
          },
          {
            id: "card-prok-envelope",
            variant: "teal-card",
            title: "Cell Envelope, Mesosomes & 70S Ribosomes",
            scientists: [
              {
                name: "Three-Layered Cell Envelope",
                contribution: "Outermost **glycocalyx** (loose sheath = slime layer; thick and tough = capsule) → **cell wall** → **plasma membrane**.",
              },
              {
                name: "Mesosome (Vesicles, Tubules & Lamellae)",
                contribution: "Extensions of plasma membrane into the cell; helps in cell wall formation, DNA replication, respiration, and secretion.",
              },
              {
                name: "70S Ribosomes & Polysomes",
                contribution: "15 nm × 20 nm; made of **50S and 30S subunits** (together 70S). Several ribosomes attach to a single mRNA to form a **polyribosome (polysome)**.",
              },
            ],
          },
          {
            id: "card-fig-8-2",
            variant: "figure-card",
            title: "Extracted NCERT Figure 8.2 · Cell Size Comparison",
            figure: NCERT_CELL_FIGURES.fig_8_2,
          },
        ],
      },
    ],
  },
  {
    id: "topic-8-5",
    number: "8.5",
    title: "Eukaryotic Cells & Cell Membrane",
    pageStart: 4,
    pageEnd: 5,
    summary: "Protists, plants, animals and fungi; Plant vs Animal cell organization (Figure 8.3); Singer & Nicolson (1972) Fluid Mosaic Model of plasma membrane (52% protein, 40% lipids) and active/passive transport.",
    subtopics: ["Plant vs Animal Cell (Figure 8.3)", "RBC Membrane (52% protein, 40% lipids)", "Fluid Mosaic Model (1972)", "Integral vs Peripheral proteins", "Passive, Osmosis & Active transport (Na+/K+ Pump)"],
    rawText: `8.5 EUKARYOTIC CELLS
The eukaryotes include all the protists, plants, animals and fungi. In eukaryotic cells there is an extensive compartmentalisation of cytoplasm through the presence of membrane bound organelles. Eukaryotic cells possess an organised nucleus with a nuclear envelope. Plant and animal cells are different as the former possess cell walls, plastids and a large central vacuole which are absent in animal cells. On the other hand, animal cells have centrioles which are absent in almost all plant cells (Figure 8.3).
8.5.1 Cell Membrane
Chemical studies on the cell membrane, especially in human red blood cells (RBCs), enabled the scientists to deduce the possible structure of plasma membrane. These studies showed that the cell membrane is mainly composed of lipids and proteins. The major lipids are phospholipids that are arranged in a bilayer. In human beings, the membrane of the erythrocyte has approximately 52 per cent protein and 40 per cent lipids.
Depending on the ease of extraction, membrane proteins can be classified as integral and peripheral. Peripheral proteins lie on the surface of membrane while the integral proteins are partially or totally buried in the membrane.
An improved model of the structure of cell membrane was proposed by Singer and Nicolson (1972) widely accepted as fluid mosaic model (Figure 8.4). According to this, the quasi-fluid nature of lipid enables lateral movement of proteins within the overall bilayer. This ability to move within the membrane is measured as its fluidity.`,
    figures: [NCERT_CELL_FIGURES.fig_8_3, NCERT_CELL_FIGURES.fig_8_4],
    precompiledSections: [
      {
        id: "sec-eukaryotic",
        sectionNo: "06",
        title: "Eukaryotic Cells & Fluid Mosaic Membrane",
        topicId: "topic-8-5",
        cards: [
          {
            id: "card-euk-compartment",
            variant: "purple-card",
            title: "Eukaryotic Compartmentalisation",
            paragraphs: [
              "The eukaryotes include all the **protists, plants, animals and fungi**. They possess extensive compartmentalisation of cytoplasm and an organised nucleus with a nuclear envelope.",
            ],
            capabilities: [
              { roman: "(i)", text: "**Plant cells possess:** Cell walls, plastids, and a large central vacuole (absent in animal cells)." },
              { roman: "(ii)", text: "**Animal cells possess:** Centrioles (absent in almost all plant cells)." },
            ],
            importantGreen: "In human beings, the membrane of the **erythrocyte (RBC)** has approximately **52 per cent protein and 40 per cent lipids**.",
          },
          {
            id: "card-fig-8-3",
            variant: "figure-card",
            title: "Extracted NCERT Figure 8.3 · Plant Cell vs Animal Cell",
            figure: NCERT_CELL_FIGURES.fig_8_3,
          },
          {
            id: "card-fluid-mosaic",
            variant: "teal-card",
            title: "Fluid Mosaic Model (Singer & Nicolson, 1972)",
            scientists: [
              {
                name: "Singer and Nicolson (1972)",
                contribution: "Proposed the widely accepted **Fluid Mosaic Model**: the quasi-fluid nature of lipid enables lateral movement of proteins within the overall bilayer (measured as fluidity).",
              },
              {
                name: "Integral vs Peripheral Proteins",
                contribution: "**Peripheral proteins** lie on the surface of membrane while **integral proteins** are partially or totally buried in the membrane.",
              },
            ],
            footerNote: "One of the most important functions of the plasma membrane is the **transport of the molecules across it** (selectively permeable).",
          },
          {
            id: "card-fig-8-4",
            variant: "figure-card",
            title: "Extracted NCERT Figure 8.4 · Fluid Mosaic Model of Plasma Membrane",
            figure: NCERT_CELL_FIGURES.fig_8_4,
          },
        ],
      },
    ],
  },
  {
    id: "topic-8-5-4",
    number: "8.5.4",
    title: "Mitochondria & Plastids",
    pageStart: 4,
    pageEnd: 5,
    summary: "Double membrane-bound organelles, cristae, matrix, 70S ribosomes, circular DNA, aerobic respiration ('power houses of the cell'), leucoplasts, chromoplasts and chloroplasts.",
    subtopics: ["Cristae & Inner Compartment (Matrix)", "Power house of the cell (ATP)", "Single circular DNA & 70S ribosomes", "Chloroplasts, Chromoplasts & Leucoplasts", "Thylakoids, Grana & Stroma lamellae"],
    rawText: `8.5.4 Mitochondria
Mitochondria (sing.: mitochondrion), unless specifically stained, are not easily visible under the microscope. Each mitochondrion is a double membrane-bound structure with the outer membrane and the inner membrane dividing its lumen distinctly into two aqueous compartments, i.e., the outer compartment and the inner compartment. The inner compartment is filled with a dense homogeneous substance called the matrix. The inner membrane forms a number of infoldings called the cristae (sing.: crista) towards the matrix. The cristae increase the surface area. Mitochondria are the sites of aerobic respiration. They produce cellular energy in the form of ATP, hence they are called 'power houses' of the cell. The matrix also possesses single circular DNA molecule, a few RNA molecules, ribosomes (70S) and the components required for the synthesis of proteins. The mitochondria divide by fission.
8.5.5 Plastids
Plastids are found in all plant cells and in euglenoides. Based on the type of pigments plastids can be classified into chloroplasts, chromoplasts and leucoplasts. The leucoplasts are the colourless plastids of varied shapes and sizes with stored nutrients: Amyloplasts store carbohydrates (starch), e.g., potato; elaioplasts store oils and fats whereas the aleuroplasts store proteins.`,
    figures: [],
  },
  {
    id: "topic-8-5-6",
    number: "8.5.6",
    title: "Ribosomes & Cytoskeleton",
    pageStart: 5,
    pageEnd: 5,
    summary: "George Palade (1953) granular structures composed of rRNA and proteins; 80S (60S + 40S) and 70S (50S + 30S) sedimentation coefficient; microtubules, microfilaments and intermediate filaments.",
    subtopics: ["George Palade (1953)", "Ribonucleoprotein (rRNA + Proteins)", "80S (60S + 40S) vs 70S (50S + 30S)", "Svedberg's Unit (S)", "Cytoskeleton (Mechanical support & motility)"],
    rawText: `8.5.6 Ribosomes
Ribosomes are the granular structures first observed under the electron microscope as dense particles by George Palade (1953). They are composed of ribonucleic acid (RNA) and proteins and are not surrounded by any membrane.
The eukaryotic ribosomes are 80S while the prokaryotic ribosomes are 70S. Each ribosome has two subunits, larger and smaller subunits. The two subunits of 80S ribosomes are 60S and 40S while that of 70S ribosomes are 50S and 30S. Here 'S' (Svedberg's Unit) stands for the sedimentation coefficient; it is indirectly a measure of density and size.
8.5.7 Cytoskeleton
An elaborate network of filamentous proteinaceous structures consisting of microtubules, microfilaments and intermediate filaments present in the cytoplasm is collectively referred to as the cytoskeleton. The cytoskeleton in a cell are involved in many functions such as mechanical support, motility, maintenance of the shape of the cell.`,
    figures: [],
  },
  {
    id: "topic-8-5-8",
    number: "8.5.8",
    title: "Cilia and Flagella",
    pageStart: 5,
    pageEnd: 5,
    summary: "Hair-like outgrowths of cell membrane; axoneme core with 9+2 array of microtubules (9 peripheral doublets + 2 central microtubules), central sheath, radial spokes, interdoublet bridges and basal body.",
    subtopics: ["Cilia (Oar-like) vs Flagella", "Axoneme Core", "9+2 Array of Microtubules", "9 Radial Spokes & Central Sheath", "Interdoublet Bridge & Basal Body"],
    rawText: `8.5.8 Cilia and Flagella
Cilia (sing.: cilium) and flagella (sing.: flagellum) are hair-like outgrowths of the cell membrane. Cilia are small structures which work like oars, causing the movement of either the cell or the surrounding fluid. Flagella are comparatively longer and responsible for cell movement. The prokaryotic bacteria also possess flagella but these are structurally different from that of the eukaryotic flagella.
The electron microscopic study of a cilium or the flagellum show that they are covered with plasma membrane. Their core called the axoneme, possesses a number of microtubules running parallel to the long axis. The axoneme usually has nine doublets of radially arranged peripheral microtubules, and a pair of centrally located microtubules. Such an arrangement of axonemal microtubules is referred to as the 9+2 array. The central tubules are connected by bridges and is also enclosed by a central sheath, which is connected to one of the tubules of each peripheral doublets by a radial spoke. Thus, there are nine radial spokes. The peripheral doublets are also interconnected by linkers. Both the cilium and flagellum emerge from centriole-like structure called the basal bodies.`,
    figures: [],
  },
  {
    id: "topic-8-5-9",
    number: "8.5.9",
    title: "Centrosome and Centrioles",
    pageStart: 5,
    pageEnd: 5,
    summary: "Non-membrane bound organelle containing two perpendicular cylindrical centrioles surrounded by amorphous pericentriolar materials; cartwheel organisation (9+0 peripheral triplet fibrils of tubulin, central proteinaceous hub & radial spokes); forms basal bodies and spindle fibres.",
    subtopics: [
      "Two perpendicular cylindrical centrioles",
      "Amorphous pericentriolar materials",
      "Cartwheel organisation (9+0 array)",
      "9 peripheral triplet fibrils of tubulin",
      "Central proteinaceous hub & radial spokes",
      "Basal bodies & Spindle apparatus",
    ],
    rawText: `8.5.9 Centrosome and Centrioles
Centrosome is an organelle usually containing two cylindrical structures called centrioles. They are surrounded by amorphous pericentriolar materials. Both the centrioles in a centrosome lie perpendicular to each other in which each has an organisation like the cartwheel. They are made up of nine evenly spaced peripheral fibrils of tubulin protein. Each of the peripheral fibril is a triplet. The adjacent triplets are also linked. The central part of the proximal region of the centriole is also proteinaceous and called the hub, which is connected with tubules of the peripheral triplets by radial spokes made of protein. The centrioles form the basal body of cilia or flagella, and spindle fibres that give rise to spindle apparatus during cell division in animal cells.`,
    figures: [],
    precompiledSections: [
      {
        id: "sec-centrosome",
        sectionNo: "01",
        title: "8.5.9 Centrosome and Centrioles",
        topicId: "topic-8-5-9",
        cards: [
          {
            id: "card-centrosome-overview",
            variant: "purple-card",
            title: "Occurrence & Core Organization of Centrosome",
            paragraphs: [
              "**Centrosome** is a non-membrane bound organelle usually containing **two cylindrical structures called centrioles**, surrounded by **amorphous pericentriolar materials**.",
            ],
            capabilities: [
              {
                roman: "(i)",
                text: "**Perpendicular Arrangement:** Both the centrioles in a centrosome lie **perpendicular to each other**.",
              },
              {
                roman: "(ii)",
                text: "**Cartwheel Organisation:** Each centriole exhibits a characteristic internal organisation like a **cartwheel** (**9 + 0 arrangement** — no central microtubules).",
              },
            ],
            importantGreen:
              "The centrioles form the **basal body of cilia or flagella**, and **spindle fibres** that give rise to **spindle apparatus during cell division in animal cells**.",
            sourceTopicId: "topic-8-5-9",
          },
          {
            id: "card-centrosome-ultrastructure",
            variant: "teal-card",
            title: "Ultrastructure of Centriole (Cartwheel Architecture)",
            scientists: [
              {
                name: "Peripheral Fibrils (9 Triplets of Tubulin)",
                contribution:
                  "Made up of **nine evenly spaced peripheral fibrils of tubulin protein**. Each of the peripheral fibril is a **triplet**, and the **adjacent triplets are also linked**.",
              },
              {
                name: "Central Hub (Proximal Region)",
                contribution:
                  "The central part of the proximal region of the centriole is **proteinaceous** and called the **hub**.",
              },
              {
                name: "Proteinaceous Radial Spokes",
                contribution:
                  "The central **hub** is connected with tubules of the peripheral triplets by **nine radial spokes made of protein**.",
              },
            ],
            footerNote:
              "**NCERT Key Distinction:** Centrioles have a **9 + 0 arrangement** (9 peripheral triplets, proteinaceous hub, 0 central microtubules), whereas eukaryotic cilia/flagella axonemes have a **9 + 2 array** (9 peripheral doublets + 2 central microtubules).",
            sourceTopicId: "topic-8-5-9",
          },
          {
            id: "card-centrosome-table",
            variant: "ai-table",
            title: "NCERT Structural Summary · Centrosome & Centrioles",
            tableData: {
              headers: [
                "Structural Component",
                "NCERT Organization & Composition",
                "Key Role / Feature",
              ],
              rows: [
                [
                  "**Centrosome**",
                  "Two cylindrical **centrioles** + surrounding **amorphous pericentriolar materials**",
                  "Non-membrane bound organelle in animal cells",
                ],
                [
                  "**Orientation of Centrioles**",
                  "Lie **perpendicular (90°) to each other**",
                  "Each shows a **cartwheel** organisation",
                ],
                [
                  "**Peripheral Fibrils**",
                  "**Nine evenly spaced peripheral fibrils** of **tubulin protein**",
                  "Each peripheral fibril is a **triplet**; adjacent triplets are linked",
                ],
                [
                  "**Hub & Radial Spokes**",
                  "Central proteinaceous **hub** (proximal region) + **radial spokes made of protein**",
                  "Connects the central hub to tubules of the peripheral triplets",
                ],
                [
                  "**Cellular Functions**",
                  "Forms **basal bodies** and **spindle fibres**",
                  "Gives rise to **cilia/flagella** and **spindle apparatus** during cell division in animal cells",
                ],
              ],
            },
            sourceTopicId: "topic-8-5-9",
          },
        ],
      },
    ],
  },
  {
    id: "topic-8-5-10",
    number: "8.5.10",
    title: "Nucleus & Chromosomes",
    pageStart: 5,
    pageEnd: 5,
    summary: "Robert Brown (1831), Flemming (chromatin), nuclear envelope with perinuclear space (10–50 nm), nucleoplasm, non-membranous nucleolus (rRNA synthesis), centromere, kinetochore and 4 chromosome types (Metacentric, Sub-metacentric, Acrocentric, Telocentric).",
    subtopics: ["Robert Brown (1831) & Flemming (Chromatin)", "Perinuclear space (10 to 50 nm)", "Nucleolus (Active rRNA synthesis)", "Centromere & Kinetochores", "Metacentric, Sub-metacentric, Acrocentric, Telocentric"],
    rawText: `8.5.10 Nucleus
Nucleus as a cell organelle was first described by Robert Brown as early as 1831. Later the material of the nucleus stained by the basic dyes was given the name chromatin by Flemming.
The interphase nucleus (nucleus of a cell when it is not dividing) has highly extended and elaborate nucleoprotein fibres called chromatin, nuclear matrix and one or more spherical bodies called nucleoli (sing.: nucleolus). Electron microscopy has revealed that the nuclear envelope, which consists of two parallel membranes with a space between (10 to 50 nm) called the perinuclear space, forms a barrier between the materials present inside the nucleus and that of the cytoplasm.
The nuclear matrix or the nucleoplasm contains nucleolus and chromatin. The content of nucleolus is continuous with the rest of the nucleoplasm as it is not a membrane bound structure. It is a site for active ribosomal RNA synthesis.
Based on the position of the centromere, the chromosomes can be classified into four types: the metacentric chromosome has middle centromere forming two equal arms of the chromosome. The sub-metacentric chromosome has centromere slightly away from the middle of the chromosome resulting into one shorter arm and one longer arm. In case of acrocentric chromosome the centromere is situated close to its end forming one extremely short and one very long arm, whereas the telocentric chromosome has a terminal centromere.`,
    figures: [],
  },
];

export const INITIAL_LIBRARY_PDFS: LibraryPdfItem[] = [
  {
    id: "ncert-botany-cell-pdf",
    chapterId: "botany-06",
    subjectId: "botany",
    title: "NCERT Class 11 Biology · Ch 8: Cell: The Unit of Life",
    fileName: "NCERT_Ch08_Cell_The_Unit_of_Life.pdf",
    pageCount: 5,
    addedAt: Date.now() - 3600_000,
    fileSizeLabel: "2.4 MB · Official NCERT",
    isBuiltInNcert: true,
    topics: BUILTIN_CELL_TOPICS,
    scanned: true,
  },
];

