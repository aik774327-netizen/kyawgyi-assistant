// Minimal zero-dependency test runner for the ai-angle-fixes logic tests.
// Usage: node tests/all.mjs
let passed = 0;
let failed = 0;
const failures = [];

export function test(name, fn) {
  try {
    fn();
    passed++;
  } catch (e) {
    failed++;
    failures.push([name, e]);
  }
}

export function assert(cond, msg) {
  if (!cond) throw new Error(msg || "assertion failed");
}

export function assertEq(a, b, msg) {
  if (a !== b) throw new Error(`${msg || "not equal"}: ${JSON.stringify(a)} !== ${JSON.stringify(b)}`);
}

export function report() {
  console.log(`\n${passed} passed, ${failed} failed`);
  for (const [n, e] of failures) console.log(`  FAIL ${n}: ${e && e.message}`);
  process.exit(failed ? 1 : 0);
}
