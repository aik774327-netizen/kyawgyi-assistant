// Logic tests for src/lib/gateway.ts (BYOK, error mapping, rate limiter).
import { execFileSync } from "node:child_process";
import { mkdtempSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { createRequire } from "node:module";
import { test, assert, assertEq } from "./run.mjs";

const require = createRequire(import.meta.url);
const ESBUILD = "/home/hatch/workspace/myanvoice-aistudio-src/node_modules/.bin/esbuild";
const SRC = "/home/hatch/workspace/ai-angle-fixes/src/lib/gateway.ts";

const outDir = mkdtempSync(join(tmpdir(), "gw-test-"));
const outFile = join(outDir, "gateway.cjs");
execFileSync(ESBUILD, [SRC, "--format=cjs", `--outfile=${outFile}`, "--log-level=error"]);
const gw = require(outFile);

const mkReq = (headerVal) => ({
  headers: { get: (k) => (k === "x-custom-api-key" ? headerVal : null) },
});

test("burmeseError maps 401/402/429, falls back otherwise", () => {
  assertEq(gw.burmeseError(401), "API Key မမှန်ကန်ပါ", "401");
  assertEq(gw.burmeseError(402), "AI credits ကုန်သွားပါပြီ", "402");
  assertEq(gw.burmeseError(429), "တောင်းဆိုမှု များနေပါသည်၊ ခဏနေမှ ပြန်ကြိုးစားပါ", "429");
  assertEq(gw.burmeseError(500), "AI error (500)", "fallback");
});

test("resolveApiKey prefers a valid custom header", () => {
  process.env.LOVABLE_API_KEY = "env-key";
  assertEq(gw.resolveApiKey(mkReq("user-key")), "user-key", "header wins");
  assertEq(gw.resolveApiKey(mkReq("  user-key  ")), "user-key", "trimmed");
});

test("resolveApiKey rejects header injection + oversize", () => {
  process.env.LOVABLE_API_KEY = "env-key";
  assertEq(gw.resolveApiKey(mkReq("bad\r\nkey: x")), "env-key", "CRLF rejected");
  assertEq(gw.resolveApiKey(mkReq("x".repeat(201))), "env-key", "oversize rejected");
  assertEq(gw.resolveApiKey(mkReq("   ")), "env-key", "blank rejected");
});

test("resolveApiKey falls back to env, then null", () => {
  process.env.LOVABLE_API_KEY = "env-key";
  assertEq(gw.resolveApiKey(mkReq(null)), "env-key", "env fallback");
  delete process.env.LOVABLE_API_KEY;
  assertEq(gw.resolveApiKey(mkReq(null)), null, "null when nothing");
});

test("createRateLimiter blocks over the limit per IP", () => {
  const rl = gw.createRateLimiter();
  let blocked = 0;
  for (let i = 0; i < 12; i++) if (rl("1.2.3.4", 10, 60_000)) blocked++;
  assertEq(blocked, 2, "2 over 10 blocked");
  assert(!rl("9.9.9.9", 10, 60_000), "other IP unaffected");
});

test("rate limiter resets after the window", () => {
  const rl = gw.createRateLimiter();
  for (let i = 0; i < 10; i++) assert(!rl("ip", 10, 1), "within limit");
  assert(rl("ip", 10, 1), "11th blocked");
  const start = Date.now();
  while (Date.now() - start < 5) {} // wait out the 1ms window
  assert(!rl("ip", 10, 1), "window reset");
});
