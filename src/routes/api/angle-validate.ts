import { createFileRoute } from "@tanstack/react-router";
import {
  resolveApiKey,
  clientIp,
  createRateLimiter,
} from "../../lib/gateway";

/**
 * GET /api/angle-validate — validates a Lovable API key with a minimal
 * upstream call (GET /v1/models — lists models, spends no credits).
 *
 * Key source: `x-custom-api-key` request header (BYOK — the user's own key
 * from browser localStorage) takes priority; falls back to LOVABLE_API_KEY
 * env when the header is absent.
 *
 * The key is sanitized (length + control-char check) and NEVER logged.
 * Always responds 200 with { ok, reason } so the client needs no
 * status-code branching.
 *
 * Compile status: NOT VERIFIED (no project deps here).
 */

const GATEWAY = "https://ai.gateway.lovable.dev/v1";

// Light throttle — validation hits the gateway on every click.
const validateRateLimit = createRateLimiter();

export const Route = createFileRoute("/api/angle-validate")({
  server: {
    handlers: {
      GET: async ({ request }) => {
        if (validateRateLimit(clientIp(request), 10, 60_000)) {
          return Response.json({ ok: false, reason: "rate-limit" });
        }

        const key = resolveApiKey(request);
        if (!key) {
          return Response.json({ ok: false, reason: "missing" });
        }

        let upstream: Response;
        try {
          upstream = await fetch(`${GATEWAY}/models`, {
            headers: {
              Authorization: `Bearer ${key}`,
              "Lovable-API-Key": key,
            },
            signal: request.signal,
          });
        } catch {
          return Response.json({ ok: false, reason: "network" });
        }

        if (upstream.ok) return Response.json({ ok: true });
        const reason =
          upstream.status === 402
            ? "credits"
            : upstream.status === 401 || upstream.status === 403
              ? "invalid"
              : "unknown";
        return Response.json({ ok: false, reason });
      },
    },
  },
});
