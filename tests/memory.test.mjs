// Logic tests for src/lib/memory.ts (immutable helpers, token-safe caps).
import { execFileSync } from "node:child_process";
import { mkdtempSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { createRequire } from "node:module";
import { test, assert, assertEq, report } from "./run.mjs";

const require = createRequire(import.meta.url);
const ESBUILD = "/home/hatch/workspace/myanvoice-aistudio-src/node_modules/.bin/esbuild";
const SRC = "/home/hatch/workspace/ai-angle-fixes/src/lib/memory.ts";

const outDir = mkdtempSync(join(tmpdir(), "mem-test-"));
const outFile = join(outDir, "memory.cjs");
execFileSync(ESBUILD, [SRC, "--format=cjs", `--outfile=${outFile}`, "--log-level=error"]);
const mem = require(outFile);

// ---- localStorage stub (memory.ts touches it directly) ----
function makeStorage() {
  const m = new Map();
  return {
    getItem: (k) => (m.has(k) ? m.get(k) : null),
    setItem: (k, v) => void m.set(String(k), String(v)),
    removeItem: (k) => void m.delete(k),
    clear: () => void m.clear(),
  };
}

test("defaultMemory is deep-frozen", () => {
  assert(Object.isFrozen(mem.defaultMemory), "top-level frozen");
  assert(Object.isFrozen(mem.defaultMemory.persona), "persona frozen");
});

test("loadUserMemory returns an independent copy", () => {
  globalThis.localStorage = makeStorage();
  const a = mem.loadUserMemory();
  a.persona.preferences.push("MUT");
  a.topics.push({ title: "MUT", summary: "x", date: "d" });
  const b = mem.loadUserMemory();
  assert(!b.persona.preferences.includes("MUT"), "prefs not shared");
  assert(!b.topics.some((t) => t.title === "MUT"), "topics not shared");
});

test("corrupt stored JSON falls back to default shape", () => {
  globalThis.localStorage = makeStorage();
  localStorage.setItem("ai_angle_long_term_memory_v2", "{not json");
  const m = mem.loadUserMemory();
  assert(Array.isArray(m.topics) && Array.isArray(m.persona.preferences), "valid shape");
});

test("withTopic is immutable, appends, caps at 50", () => {
  const base = mem.loadUserMemory();
  const t1 = mem.withTopic(base, { title: "T1", summary: "s", date: "d" });
  assertEq(base.topics.length, 0, "base untouched");
  assertEq(t1.topics.length, 1, "appended");
  assertEq(t1.topics[0].title, "T1", "order kept");
  let m = base;
  for (let i = 0; i < 60; i++) m = mem.withTopic(m, { title: `T${i}`, summary: "s", date: "d" });
  assertEq(m.topics.length, 50, "capped at 50");
  assertEq(m.topics[49].title, "T59", "keeps newest");
});

test("withPreference dedupes and is immutable", () => {
  const base = mem.loadUserMemory();
  const m1 = mem.withPreference(base, "tea");
  const m2 = mem.withPreference(m1, "tea");
  assertEq(m1.persona.preferences.length, 1, "added once");
  assert(m2 === m1, "duplicate returns same ref");
  assertEq(base.persona.preferences.length, 0, "base untouched");
});

test("buildMemoryContext applies token-safe caps", () => {
  let m = mem.loadUserMemory();
  for (let i = 0; i < 8; i++)
    m = mem.withTopic(m, { title: `Topic${i}`, summary: "x".repeat(200), date: `d${i}` });
  for (let i = 0; i < 12; i++) m = mem.withPreference(m, `pref${i}`);
  for (let i = 0; i < 20; i++) m = mem.withJargon(m, `term${i}`, "mean");
  for (let i = 0; i < 12; i++) m = mem.withCustomNote(m, `note${i}`);

  const ctx = mem.buildMemoryContext(m);
  assert(!ctx.includes("Topic0") && !ctx.includes("Topic1"), "topics sliced to last 6");
  assert(ctx.includes("Topic7"), "newest topic kept");
  const longLine = ctx.split("\n").find((l) => l.includes("Topic7"));
  assert(longLine.length - "  * [d7] Topic7:".length <= 150, "summary truncated to 150");
  assert(!ctx.includes("pref0") && ctx.includes("pref11"), "prefs sliced to last 10");
  assert(!ctx.includes("term0") && ctx.includes("term19"), "jargon sliced to last 15");
  assert(!ctx.includes("note0") && ctx.includes("note11"), "notes sliced to last 10");
});

test("remove helpers drop exactly the target", () => {
  let m = mem.loadUserMemory();
  m = mem.withPreference(m, "a");
  m = mem.withJargon(m, "t", "v");
  m = mem.withCustomNote(m, "n");
  m = mem.withTopic(m, { title: "T", summary: "s", date: "d" });
  m = mem.removePreference(m, "a");
  m = mem.removeJargon(m, "t");
  m = mem.removeCustomNote(m, "n");
  m = mem.removeTopic(m, "T", "d");
  assertEq(m.persona.preferences.length, 0, "pref removed");
  assertEq(Object.keys(m.jargonAndTerms).length, 0, "jargon removed");
  assertEq(m.customNotes.length, 0, "note removed");
  assertEq(m.topics.length, 0, "topic removed");
});

test("empty memory renders the empty-state string", () => {
  const ctx = mem.buildMemoryContext(mem.loadUserMemory());
  assert(ctx.includes("မရှိသေးပါ"), "empty state in Burmese");
});

export { report };
