import { createFileRoute } from "@tanstack/react-router";
import {
  resolveApiKey,
  burmeseError,
  clientIp,
  createRateLimiter,
} from "../../lib/gateway";

const MODEL = "openai/gpt-6-astra";
const GATEWAY = "https://ai.gateway.lovable.dev/v1";

// FIX #8: per-IP token bucket (30/min) — the route spends API credits,
// so a public endpoint needs a local throttle (best-effort, per instance).
const chatRateLimit = createRateLimiter();

const SYSTEM = `သင်သည် "AI Angle" ဖြစ်သည် — မြန်မာ့ပညာရှင် ပါရဂူတစ်ဦးကဲ့သို့ လေးနက်ဉာဏ်ရည်ထက်မြက်ပြီး နွေးထွေးဖော်ရွေသော မြန်မာ AI အဖော်။

[Context & Memory Management]
- အသုံးပြုသူ ယခင်က ပြောကြားခဲ့သော ကိုယ်ရေးအချက်အလက်များ၊ အကြိုက်နှစ်သက်မှုများ၊ နယ်ပယ်အလိုက် သုံးစွဲခဲ့သော အထူးအသုံးအနှုန်းများ (Jargon)၊ ယခင်ရက်များက ဆွေးနွေးခဲ့သည့် အဓိက အကြောင်းအရာများကို အစဉ်သဖြင့် သတိရ မှတ်သားထားပြီး ရှေ့နောက်ဆက်စပ်အသုံးပြုပါ။

[Advanced Reasoning & Myanmar Persona]
- မြန်မာ့ပညာရှင်တစ်ဦးကဲ့သို့ ရှုပ်ထွေးသော မေးခွန်းများနှင့် အခြေအနေမျိုးစုံကို စေ့စေ့စပ်စပ် ခွဲခြမ်းစိတ်ဖြာ စဉ်းစားတွေးခေါ်ပြီးမှ တိကျသေချာသော အဖြေကို ထုတ်ပေးပါ။
- အသုံးပြုသူ၏ ဒေသသုံးစကား (ရခိုင်၊ ထားဝယ်၊ မြိတ်၊ ရှမ်း၊ အညာ စသည်)၊ အသံထွက်ကွဲလွဲမှု၊ စာလုံးပေါင်းမှားဝင်မှုများကို ရှေ့နောက်ဆက်စပ်မှု (Context) ဖြင့် အသိဉာဏ်ရှိစွာ ရိပ်မိနားလည်ပါ။

[Speech & Natural Voice Output Optimization]
- အဖြေကို စက်ရုပ်မဆန်ဘဲ လူသားဆန်ဆန် သဘာဝကျကျ အသံဖြင့် ဖတ်ပြမည်ဖြစ်၍ မြန်မာစကားပြောဟန်ဖြင့် ပီပီသသ၊ နွေးနွေးထွေးထွေး၊ သဘာဝကျကျ ဖြေကြားပါ။
- အဖြေကို စကားပြောရလွယ်ကူပြီး လိုရင်းတိုရှင်းဖြစ်အောင် ပုံမှန် ၂ ကြောင်းမှ ၅ ကြောင်းအတွင်း ဖြေပါ။ လိုအပ်မှသာ စာရင်းသုံးပါ။`;

export type ChatRequestMsg = {
  role: "user" | "assistant";
  content: string;
  images?: string[];
};

export const Route = createFileRoute("/api/angle-chat")({
  server: {
    handlers: {
      POST: async ({ request }) => {
        // BYOK: user's own key (x-custom-api-key header) wins over env.
        const key = resolveApiKey(request);
        if (!key) return Response.json({ error: "AI key မရှိပါ" }, { status: 500 });

        if (chatRateLimit(clientIp(request), 30, 60_000)) {
          return Response.json(
            { error: "တောင်းဆိုမှု များနေပါသည်၊ ခဏနေမှ ပြန်ကြိုးစားပါ" },
            { status: 429 }
          );
        }

        let body: { messages?: ChatRequestMsg[]; context?: string };
        try {
          body = await request.json();
        } catch {
          return Response.json({ error: "Invalid JSON format" }, { status: 400 });
        }

        // Token သက်သာစေရန် မက်ဆေ့ခ်ျ နောက်ဆုံး ၂၀ ခုသာ ပို့မည်
        const msgs = (body.messages ?? []).filter((m) => m && typeof m.content === "string").slice(-20);
        if (!msgs.length) return Response.json({ error: "No messages provided" }, { status: 400 });

        const lastUserIdx = msgs.map((m) => m.role).lastIndexOf("user");

        const input = msgs.map((m, i) =>
          m.role === "assistant"
            ? { role: "assistant", content: [{ type: "output_text", text: m.content }] }
            : {
                role: "user",
                content: [
                  { type: "input_text", text: m.content || "(ပူးတွဲဖိုင်ကို ကြည့်ပါ)" },
                  ...(i === lastUserIdx ? (m.images ?? []).slice(0, 8).map((u) => ({ type: "input_image", image_url: u })) : []),
                ],
              }
        );

        const instructions = body.context ? `${SYSTEM}\n\n=== အသုံးပြုသူ၏ မှတ်ဉာဏ်/Persona & Context ===\n${body.context.slice(0, 4000)}` : SYSTEM;

        const upstream = await fetch(`${GATEWAY}/responses`, {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            Authorization: `Bearer ${key}`,
            "Lovable-API-Key": key,
            "X-Lovable-AIG-SDK": "fetch",
          },
          body: JSON.stringify({
            model: MODEL,
            instructions,
            input,
            reasoning: { effort: "medium", summary: "auto" },
            store: false,
            stream: true,
          }),
          signal: request.signal,
        });

        if (!upstream.ok || !upstream.body) {
          const t = await upstream.text().catch(() => "");
          console.error("angle-chat upstream error:", upstream.status, t.slice(0, 500));
          return Response.json(
            { error: burmeseError(upstream.status) },
            { status: upstream.status }
          );
        }

        const enc = new TextEncoder();
        const dec = new TextDecoder();
        let buffer = "";

        // SSE chunks များကို Clean Plain Text Stream အဖြစ် ပြောင်းလဲပေးခြင်း
        const stream = upstream.body.pipeThrough(
          new TransformStream<Uint8Array, Uint8Array>({
            transform(chunk, ctrl) {
              buffer += dec.decode(chunk, { stream: true });
              const lines = buffer.split("\n");
              buffer = lines.pop() ?? "";

              for (const line of lines) {
                const trimmed = line.trim();
                if (!trimmed.startsWith("data:")) continue;
                const data = trimmed.slice(5).trim();
                if (!data || data === "[DONE]") continue;

                try {
                  const ev = JSON.parse(data);
                  if (ev.type === "response.output_text.delta" && ev.delta) {
                    ctrl.enqueue(enc.encode(ev.delta));
                  } else if (ev.type === "response.failed" || ev.type === "error") {
                    ctrl.enqueue(enc.encode("\n\n[AI error: တုံ့ပြန်မှု မအောင်မြင်ပါ]"));
                  }
                } catch {
                  /* partial JSON pass */
                }
              }
            },
            flush(ctrl) {
              if (buffer.trim().startsWith("data:")) {
                const data = buffer.trim().slice(5).trim();
                if (data && data !== "[DONE]") {
                  try {
                    const ev = JSON.parse(data);
                    if (ev.type === "response.output_text.delta" && ev.delta) {
                      ctrl.enqueue(enc.encode(ev.delta));
                    }
                  } catch {
                    /* ignore */
                  }
                }
              }
            },
          })
        );

        return new Response(stream, {
          headers: {
            "Content-Type": "text/plain; charset=utf-8",
            "Cache-Control": "no-cache, no-transform",
          },
        });
      },
    },
  },
});
