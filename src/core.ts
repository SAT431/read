import { z } from "zod";
export type Element = {
  id: string;
  type: "paragraph" | "heading" | "figure";
  text: string;
  bbox: number[];
  media?: string;
  parent?: string;
};
export type Page = {
  id: string;
  chapter: string;
  number: number;
  title: string;
  elements: Element[];
  original?: string;
  approved: boolean;
  fixture?: boolean;
  version: number;
  corrections: Record<string, string>;
};
export const kinds = [
  "reader",
  "quiz",
  "diagram",
  "explanation",
  "table",
] as const;
export type Kind = (typeof kinds)[number];
export const registry: Record<
  Kind,
  {
    title: string;
    minWidth: number;
    preferredWidth: number;
    gestures: string[];
    variants: string[];
    state: string;
    actions: string[];
  }
> = {
  reader: {
    title: "Reading canvas",
    minWidth: 340,
    preferredWidth: 720,
    gestures: ["tap-focus", "native-selection", "scroll"],
    variants: ["surface", "dominant"],
    state: "Source anchor, scroll, notes, checkpoints",
    actions: ["focus", "pin", "hide"],
  },
  quiz: {
    title: "Active recall",
    minWidth: 320,
    preferredWidth: 540,
    gestures: ["tap-answer"],
    variants: ["surface", "dominant"],
    state: "Questions, answers, submissions, index",
    actions: ["focus", "suspend", "resume", "pin"],
  },
  diagram: {
    title: "Source figure",
    minWidth: 300,
    preferredWidth: 520,
    gestures: ["native-pan", "zoom-control"],
    variants: ["surface", "dominant"],
    state: "Figure source, zoom",
    actions: ["focus", "pin", "hide"],
  },
  explanation: {
    title: "Mentor notes",
    minWidth: 300,
    preferredWidth: 420,
    gestures: ["scroll"],
    variants: ["surface", "inline"],
    state: "Text and source anchor",
    actions: ["focus", "pin", "hide"],
  },
  table: {
    title: "Comparison",
    minWidth: 320,
    preferredWidth: 520,
    gestures: ["horizontal-scroll"],
    variants: ["surface", "inline"],
    state: "Rows and source anchor",
    actions: ["focus", "pin", "hide"],
  },
};
export const animations = {
  fade: { duration: 180 },
  expand: { duration: 240 },
  focus: { duration: 120 },
};
export const questionSchema = z
  .object({
    prompt: z.string().min(1).max(2000),
    options: z.array(z.string().max(1000)).length(4),
    answer: z.number().int().min(0).max(3),
    explanation: z.string().max(4000),
  })
  .strict();
export type Question = z.infer<typeof questionSchema>;
const contentSchema = z
  .object({
    text: z.string().max(12000).optional(),
    headers: z.array(z.string().max(200)).max(6).optional(),
    rows: z
      .array(z.array(z.string().max(1000)).max(6))
      .max(30)
      .optional(),
    questions: z.array(questionSchema).min(1).max(10).optional(),
  })
  .strict();
export const commandSchema = z.discriminatedUnion("type", [
  z
    .object({
      type: z.literal("CREATE_ACTIVITY"),
      kind: z.enum(kinds),
      source: z.string(),
      content: contentSchema,
    })
    .strict(),
  z
    .object({
      type: z.enum([
        "FOCUS_ACTIVITY",
        "SUSPEND_ACTIVITY",
        "RESUME_ACTIVITY",
        "HIDE_COMPONENT",
      ]),
      id: z.string(),
    })
    .strict(),
  z.object({ type: z.literal("FOCUS_SOURCE"), source: z.string() }).strict(),
  z
    .object({
      type: z.literal("SET_COMPONENT_PRIORITY"),
      id: z.string(),
      priority: z.number().int().min(0).max(10),
    })
    .strict(),
  z
    .object({
      type: z.literal("SET_PRESENTATION"),
      id: z.string(),
      presentation: z.enum(["surface", "dominant", "inline"]),
    })
    .strict(),
]);
export const responseSchema = z
  .object({
    message: z.string().max(12000),
    commands: z.array(commandSchema).max(12),
  })
  .strict();
export type Command = z.infer<typeof commandSchema>;
export type Activity = {
  id: string;
  kind: Kind;
  source: string;
  status: "active" | "suspended";
  visible: boolean;
  pinned: boolean;
  priority: number;
  presentation: "surface" | "dominant" | "inline";
  content: z.infer<typeof contentSchema>;
  answers: Record<number, number>;
  submitted: Record<number, boolean>;
  index: number;
  zoom: number;
};
export type Workspace = {
  version: 1;
  activities: Activity[];
  focus: string;
  source: string;
  notes: Record<string, string>;
  done: string[];
  font: number;
  dark: boolean;
  scroll: Record<string, number>;
  attempts: { source: string; correct: boolean; at: number; due: number }[];
};
export const seedPage: Page = {
  id: "fixture-cell",
  chapter: "Cell: the unit of life",
  number: 1,
  title: "A small world. A remarkable system.",
  approved: true,
  fixture: true,
  version: 1,
  corrections: {},
  elements: [
    {
      id: "cell-heading",
      type: "heading",
      text: "Inside the eukaryotic cell",
      bbox: [],
    },
    {
      id: "cell-p1",
      type: "paragraph",
      text: "Eukaryotic cells contain a membrane-bound nucleus and specialised organelles. These compartments allow different cellular processes to take place in distinct environments.",
      bbox: [],
    },
    {
      id: "cell-p2",
      type: "paragraph",
      text: "The rough endoplasmic reticulum carries ribosomes on its surface and participates in protein synthesis. The smooth endoplasmic reticulum lacks ribosomes and is involved in lipid synthesis.",
      bbox: [],
    },
    {
      id: "cell-p3",
      type: "paragraph",
      text: "The Golgi apparatus modifies, sorts and packages cellular products. Transport vesicles connect the endoplasmic reticulum with the Golgi apparatus and other destinations in the cell.",
      bbox: [],
    },
  ],
};
export function initial(): Workspace {
  return {
    version: 1,
    activities: [
      {
        id: "reader-1",
        kind: "reader",
        source: "cell-p1",
        status: "active",
        visible: true,
        pinned: false,
        priority: 8,
        presentation: "surface",
        content: {},
        answers: {},
        submitted: {},
        index: 0,
        zoom: 1,
      },
    ],
    focus: "reader-1",
    source: "cell-p1",
    notes: {},
    done: [],
    font: 20,
    dark: false,
    scroll: {},
    attempts: [],
  };
}
export function execute(
  state: Workspace,
  raw: unknown[],
  pages: Page[],
  actor: "user" | "ai" = "ai",
): Workspace {
  const commands = z.array(commandSchema).max(12).parse(raw);
  const next = structuredClone(state);
  const exists = (s: string) =>
    pages.some((p) => p.elements.some((e) => e.id === s));
  for (const c of commands) {
    if ("source" in c && !exists(c.source))
      throw Error("Unknown source anchor");
    if (c.type === "CREATE_ACTIVITY") {
      if (c.kind === "quiz" && !c.content.questions?.length)
        throw Error("A quiz requires questions");
      if (
        c.kind === "table" &&
        (!c.content.headers?.length ||
          !c.content.rows?.length ||
          c.content.rows.some((r) => r.length !== c.content.headers!.length))
      )
        throw Error("Invalid table shape");
      if (c.kind === "explanation" && !c.content.text)
        throw Error("Explanation is empty");
      if (
        c.kind === "diagram" &&
        !pages.some((p) =>
          p.elements.some((e) => e.id === c.source && e.type === "figure"),
        )
      )
        throw Error("Diagram requires a figure anchor");
      const a: Activity = {
        id: crypto.randomUUID(),
        kind: c.kind,
        source: c.source,
        status: "active",
        visible: true,
        pinned: false,
        priority: 7,
        presentation: "surface",
        content: c.content,
        answers: {},
        submitted: {},
        index: 0,
        zoom: 1,
      };
      next.activities.push(a);
      next.focus = a.id;
    } else if (c.type === "FOCUS_SOURCE") {
      next.source = c.source;
      const r = next.activities.find((a) => a.kind === "reader");
      if (r) {
        r.source = c.source;
        r.visible = true;
        r.status = "active";
        next.focus = r.id;
      }
    } else if ("id" in c) {
      const a = next.activities.find((a) => a.id === c.id);
      if (!a) throw Error("Unknown activity");
      if (
        actor === "ai" &&
        a.pinned &&
        [
          "SUSPEND_ACTIVITY",
          "HIDE_COMPONENT",
          "SET_COMPONENT_PRIORITY",
          "SET_PRESENTATION",
        ].includes(c.type)
      )
        throw Error("Pinned component protected");
      if (c.type === "HIDE_COMPONENT") a.visible = false;
      if (c.type === "SUSPEND_ACTIVITY") {
        a.status = "suspended";
        a.visible = false;
      }
      if (c.type === "RESUME_ACTIVITY" || c.type === "FOCUS_ACTIVITY") {
        a.visible = true;
        a.status = "active";
        next.focus = a.id;
      }
      if (c.type === "SET_COMPONENT_PRIORITY") a.priority = c.priority;
      if (c.type === "SET_PRESENTATION") {
        if (!registry[a.kind].variants.includes(c.presentation))
          throw Error("Unsupported presentation");
        a.presentation = c.presentation;
      }
    }
  }
  return next;
}
export function solveLayout(
  activities: Activity[],
  width: number,
  focus: string,
) {
  const visible = activities
    .filter((a) => a.visible && a.status === "active")
    .sort(
      (a, b) =>
        Number(b.pinned) - Number(a.pinned) ||
        (b.id === focus ? 1 : 0) - (a.id === focus ? 1 : 0) ||
        b.priority - a.priority,
    );
  const columns: Activity[][] = [];
  for (const a of visible) {
    const min = registry[a.kind].minWidth;
    const used = columns.reduce(
      (n, col) =>
        n + Math.max(...col.map((x) => registry[x.kind].minWidth)) + 20,
      0,
    );
    if (!columns.length || used + min <= width) columns.push([a]);
    else columns[columns.length - 1].push(a);
  }
  return columns;
}
export function fixtureResponse(request: string, source: string) {
  if (!source.startsWith("cell-"))
    throw Error(
      "Fixture mode only generates content for the sample. Configure Gemini for uploaded pages.",
    );
  const q: Question = {
    prompt: "Which feature distinguishes rough ER from smooth ER?",
    options: [
      "Ribosomes attached to its surface",
      "A separate nucleus",
      "The absence of a membrane",
      "Photosynthetic pigments",
    ],
    answer: 0,
    explanation:
      "The sample passage identifies surface ribosomes as the distinguishing feature of rough ER.",
  };
  const kind =
    request.toLowerCase().includes("question") ||
    request.toLowerCase().includes("quiz")
      ? "quiz"
      : request.toLowerCase().includes("table") ||
          request.toLowerCase().includes("compare")
        ? "table"
        : "explanation";
  return {
    message: "Deterministic sample resource · not an AI response",
    commands: [
      {
        type: "CREATE_ACTIVITY",
        kind,
        source: "cell-p2",
        content:
          kind === "quiz"
            ? { questions: [q] }
            : kind === "table"
              ? {
                  headers: ["Feature", "Rough ER", "Smooth ER"],
                  rows: [
                    ["Surface", "Ribosomes present", "Ribosomes absent"],
                    [
                      "Role in this passage",
                      "Protein synthesis",
                      "Lipid synthesis",
                    ],
                  ],
                }
              : {
                  text: "Ribosomes assemble proteins. Rough ER has ribosomes attached to its surface, whereas smooth ER does not. Compare the two structures by their surface and the type of molecule they help produce.",
                },
      },
    ],
  };
}
// Layout undo must never roll back a learner's answers, notes or retrieval history.
export function restoreLayout(
  current: Workspace,
  previous: Workspace,
): Workspace {
  const next = structuredClone(current);
  next.focus = previous.focus;
  next.source = previous.source;
  next.activities = next.activities.map((a) => {
    const old = previous.activities.find((x) => x.id === a.id);
    if (!old) return { ...a, visible: false };
    return {
      ...a,
      visible: old.visible,
      status: old.status,
      priority: old.priority,
      presentation: old.presentation,
      pinned: old.pinned,
      ...(a.kind === "reader" ? { source: old.source } : {}),
    };
  });
  return next;
}
