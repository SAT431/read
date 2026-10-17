import { describe, it, expect } from "vitest";
import {
  execute,
  initial,
  seedPage,
  fixtureResponse,
  solveLayout,
  restoreLayout,
} from "./core";
describe("validated orchestration", () => {
  it("layout undo retains answers and retrieval observations", () => {
    const previous = initial();
    const current = execute(
      previous,
      fixtureResponse("quiz", "cell-p2").commands,
      [seedPage],
    );
    current.activities[1].answers[0] = 1;
    current.attempts.push({ source: "cell-p2", correct: false, at: 1, due: 2 });
    const restored = restoreLayout(current, previous);
    expect(restored.activities[1].answers[0]).toBe(1);
    expect(restored.activities[1].visible).toBe(false);
    expect(restored.attempts).toHaveLength(1);
  });
  it("rejects a whole transaction when any command is invalid", () => {
    const s = initial();
    expect(() =>
      execute(
        s,
        [
          { type: "HIDE_COMPONENT", id: "reader-1" },
          { type: "FOCUS_SOURCE", source: "missing" },
        ],
        [seedPage],
      ),
    ).toThrow();
    expect(s.activities[0].visible).toBe(true);
  });
  it("rejects executable properties and unknown components", () => {
    expect(() =>
      execute(
        initial(),
        [
          {
            type: "CREATE_ACTIVITY",
            kind: "script",
            source: "cell-p1",
            content: { text: "alert(1)" },
          },
        ],
        [seedPage],
      ),
    ).toThrow();
    expect(() =>
      execute(
        initial(),
        [{ type: "FOCUS_SOURCE", source: "cell-p1", javascript: "bad" }],
        [seedPage],
      ),
    ).toThrow();
  });
  it("preserves selected answers through pause, source review, resume and serialization", () => {
    let s = execute(initial(), fixtureResponse("quiz", "cell-p2").commands, [
      seedPage,
    ]);
    const q = s.activities[1];
    q.answers[0] = 2;
    s = execute(
      s,
      [
        { type: "SUSPEND_ACTIVITY", id: q.id },
        { type: "FOCUS_SOURCE", source: "cell-p2" },
      ],
      [seedPage],
    );
    s = JSON.parse(JSON.stringify(s));
    s = execute(s, [{ type: "RESUME_ACTIVITY", id: q.id }], [seedPage]);
    expect(s.activities[1].answers[0]).toBe(2);
    expect(s.activities[1].visible).toBe(true);
    expect(s.source).toBe("cell-p2");
  });
  it("protects pins from AI while permitting user control", () => {
    const s = initial();
    s.activities[0].pinned = true;
    expect(() =>
      execute(s, [{ type: "HIDE_COMPONENT", id: "reader-1" }], [seedPage]),
    ).toThrow("Pinned");
    expect(
      execute(
        s,
        [{ type: "HIDE_COMPONENT", id: "reader-1" }],
        [seedPage],
        "user",
      ).activities[0].visible,
    ).toBe(false);
  });
  it("supports unique instances without modifying sources", () => {
    const original = JSON.stringify(seedPage);
    let s = initial();
    for (let i = 0; i < 3; i++)
      s = execute(s, fixtureResponse("table", "cell-p2").commands, [seedPage]);
    expect(new Set(s.activities.map((a) => a.id)).size).toBe(4);
    expect(JSON.stringify(seedPage)).toBe(original);
  });
  it("rejects malformed tables and figure references", () => {
    expect(() =>
      execute(
        initial(),
        [
          {
            type: "CREATE_ACTIVITY",
            kind: "table",
            source: "cell-p1",
            content: { headers: ["A"], rows: [["1", "2"]] },
          },
        ],
        [seedPage],
      ),
    ).toThrow();
    expect(() =>
      execute(
        initial(),
        [
          {
            type: "CREATE_ACTIVITY",
            kind: "diagram",
            source: "cell-p1",
            content: {},
          },
        ],
        [seedPage],
      ),
    ).toThrow();
  });
  it("composes arbitrary combinations according to available width", () => {
    let s = initial();
    for (const req of ["quiz", "table", "explain"])
      s = execute(s, fixtureResponse(req, "cell-p2").commands, [seedPage]);
    const narrow = solveLayout(s.activities, 360, s.focus);
    const wide = solveLayout(s.activities, 1300, s.focus);
    expect(narrow.length).toBe(1);
    expect(wide.length).toBeGreaterThan(1);
    expect(wide.flat().length).toBe(4);
    expect(wide[0][0].id).toBe(s.focus);
  });
  it("hides independently of lifecycle and rejects invalid priority", () => {
    const s = initial();
    const hidden = execute(
      s,
      [{ type: "HIDE_COMPONENT", id: "reader-1" }],
      [seedPage],
    );
    expect(hidden.activities[0].status).toBe("active");
    expect(() =>
      execute(
        s,
        [{ type: "SET_COMPONENT_PRIORITY", id: "reader-1", priority: 900 }],
        [seedPage],
      ),
    ).toThrow();
  });
});
