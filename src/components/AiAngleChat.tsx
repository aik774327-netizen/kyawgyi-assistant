import { useEffect, useRef, useState } from "react";
import ReactMarkdown from "react-markdown";
import { toast } from "sonner";
import { Mic, Square, Send, Volume2, VolumeX, Trash2, Feather, Loader2, Play, Image as ImageIcon, Bookmark, Brain, Settings as SettingsIcon, Eye, EyeOff } from "lucide-react";
import { Sheet, SheetContent, SheetHeader, SheetTitle } from "@/components/ui/sheet";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { loadUserMemory, saveUserMemory, buildMemoryContext, withTopic, withPreference, withCustomNote, withJargon, withPersona, removePreference, removeCustomNote, removeJargon, removeTopic, UserMemory } from "@/lib/memory";

type Msg = { id: string; role: "user" | "assistant"; content: string; images?: string[] };
const CHAT_KEY = "ai-angle-chat-v2";
// Matches server TTS_VOICES (openai/gpt-4o-mini-tts voices — the old Gemini
// names Kore/Aoede/... would fail on the OpenAI model).
const VOICES = ["alloy", "echo", "fable", "onyx", "nova", "shimmer"];
const DEFAULT_VOICE = "nova";

// FIX (recording safety): upper bounds protect against oversized payloads
// that trigger 400/413 server errors. The old code only had a MINIMUM check.
const MAX_REC_MS = 120_000; // 2 minutes — auto-stop
const MAX_VOICE_BYTES = 10 * 1024 * 1024; // 10 MB
const MIN_VOICE_BYTES = 1500;

// API Key Settings (BYOK): user's own Lovable key, stored device-local only.
const API_KEY_STORAGE = "custom_lovable_api_key";

export function AiAngleChat() {
  const [open, setOpen] = useState(false);
  const [msgs, setMsgs] = useState<Msg[]>([]);
  const [input, setInput] = useState("");
  const [pending, setPending] = useState<string[]>([]);
  const [busy, setBusy] = useState(false);
  const [recording, setRecording] = useState(false);
  const [transcribing, setTranscribing] = useState(false);
  const [speak, setSpeak] = useState(true);
  const [voice, setVoice] = useState(DEFAULT_VOICE);
  const [speakingId, setSpeakingId] = useState<string | null>(null);
  const [memory, setMemory] = useState<UserMemory>(loadUserMemory);
  // FIX #5: memory panel state — every write helper now has a UI trigger.
  const [memOpen, setMemOpen] = useState(false);
  const [prefInput, setPrefInput] = useState("");
  const [noteInput, setNoteInput] = useState("");
  const [jargonTerm, setJargonTerm] = useState("");
  const [jargonMeaning, setJargonMeaning] = useState("");
  // API Key Settings state
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [apiKeyInput, setApiKeyInput] = useState("");
  const [showKey, setShowKey] = useState(false);
  const [validating, setValidating] = useState(false);
  const [keyValid, setKeyValid] = useState<"unknown" | "valid" | "invalid">("unknown");

  const recRef = useRef<MediaRecorder | null>(null);
  const recTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const mountedRef = useRef(true);
  // FIX #1: onstop closure captures stale `busy` — ref always holds the truth.
  const busyRef = useRef(false);
  busyRef.current = busy;
  // FIX #2: generation counter — a late-resolving fetch must not hijack newer playback.
  const ttsGenRef = useRef(0);
  const audioRef = useRef<HTMLAudioElement | null>(null);
  const audioUrlRef = useRef<string | null>(null);
  const endRef = useRef<HTMLDivElement>(null);
  const taRef = useRef<HTMLTextAreaElement>(null);
  const fileRef = useRef<HTMLInputElement>(null);

  // FIX #4: memory write path — persist every change (was read-only before).
  useEffect(() => {
    try {
      saveUserMemory(memory);
    } catch {
      /* ignore */
    }
  }, [memory]);

  /** FIX #4: user-controlled "remember this exchange" — the only write path. */
  function rememberExchange(userText: string, asstText: string) {
    const title = userText.trim().slice(0, 40) || "စကားဝိုင်း";
    setMemory((m) =>
      withTopic(m, {
        title,
        summary: asstText.slice(0, 300),
        date: new Date().toISOString().slice(0, 10),
      })
    );
    toast.success("မှတ်သားထားလိုက်ပါပြီ");
  }

  useEffect(() => {
    try {
      const raw = localStorage.getItem(CHAT_KEY);
      if (raw) {
        const p = JSON.parse(raw);
        setMsgs(p.msgs ?? []);
        setSpeak(p.speak ?? true);
        // Old installs may hold a Gemini voice name — fall back to default.
        setVoice(VOICES.includes(p.voice) ? p.voice : DEFAULT_VOICE);
      }
      // Restore the user's own API key (BYOK) if one was saved.
      const savedKey = localStorage.getItem(API_KEY_STORAGE);
      if (savedKey) {
        setApiKeyInput(savedKey);
        setKeyValid("unknown"); // re-validate on demand, never assume
      }
    } catch {
      /* ignore */
    }
  }, []);

  /** BYOK header — the server prefers this over its env key when present. */
  function customKeyHeaders(): Record<string, string> {
    try {
      const k = localStorage.getItem(API_KEY_STORAGE)?.trim();
      return k ? { "X-Custom-Api-Key": k } : {};
    } catch {
      return {};
    }
  }

  function saveKey() {
    const k = apiKeyInput.trim();
    try {
      if (k) localStorage.setItem(API_KEY_STORAGE, k);
      else localStorage.removeItem(API_KEY_STORAGE);
    } catch {
      /* storage unavailable */
    }
    setKeyValid("unknown");
    toast.success(k ? "API Key သိမ်းဆည်းပြီးပါပြီ" : "API Key ဖျက်လိုက်ပါပြီ");
  }

  async function validateKey() {
    const k = apiKeyInput.trim();
    if (!k) {
      toast.error("API Key ထည့်ပါ");
      return;
    }
    setValidating(true);
    try {
      const r = await fetch("/api/angle-validate", {
        headers: { "X-Custom-Api-Key": k },
      });
      const j = (await r.json().catch(() => ({}))) as { ok?: boolean };
      if (j.ok) {
        setKeyValid("valid");
        toast.success("API Key မှန်ကန်ပါသည် ✅");
      } else {
        setKeyValid("invalid");
        toast.error("API Key မမှန်ကန်ပါ သို့မဟုတ် Credit ကုန်နေပါသည် ❌");
      }
    } catch {
      setKeyValid("invalid");
      toast.error("API Key မမှန်ကန်ပါ သို့မဟုတ် Credit ကုန်နေပါသည် ❌");
    } finally {
      setValidating(false);
    }
  }

  useEffect(() => {
    try {
      const trimmedMsgs = msgs.slice(-20).map(({ images, ...m }) => ({
        ...m,
        images: images?.length ? [] : undefined,
      }));
      localStorage.setItem(CHAT_KEY, JSON.stringify({ msgs: trimmedMsgs, speak, voice }));
    } catch {
      /* quota full catch */
    }
  }, [msgs, speak, voice]);

  useEffect(() => {
    endRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [msgs, busy]);

  // Object URL Cleanup ဖြင့် Memory Leak ကို ရှင်းလင်းပေးခြင်း
  function clearAudioObjectUrl() {
    if (audioUrlRef.current) {
      URL.revokeObjectURL(audioUrlRef.current);
      audioUrlRef.current = null;
    }
  }

  function stopAudioElement() {
    if (audioRef.current) {
      try {
        audioRef.current.pause();
      } catch {
        /* already stopped */
      }
      audioRef.current = null;
    }
  }

  // FIX (unmount leak): recording/Timer/Mic/Audio အားလုံး unmount မှာ ရှင်းပေးခြင်း။
  // အရင် code မှာ recording လုပ်နေတုန်း sheet ပိတ်�/unmount ဖြစ်ရင် mic ပွင့်ကျန်ခဲ့မယ်။
  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
      if (recTimerRef.current) {
        clearTimeout(recTimerRef.current);
        recTimerRef.current = null;
      }
      try {
        recRef.current?.stop();
      } catch {
        /* already inactive */
      }
      recRef.current = null;
      streamRef.current?.getTracks().forEach((t) => {
        try {
          t.stop();
        } catch {
          /* ignore */
        }
      });
      streamRef.current = null;
      stopAudioElement();
      clearAudioObjectUrl();
    };
  }, []);

  // FIX #5: split long replies at sentence boundaries so a >4000-char reply
  // doesn't 413 on the TTS route — chunks play back-to-back.
  function splitForTts(text: string, max = 3500): string[] {
    if (text.length <= max) return [text];
    const sentences = text.match(/[^။.!?]+[။.!?]+["'"'"'”’)\]]?|\s*[^။.!?]+$/g) || [text];
    const parts: string[] = [];
    let cur = "";
    for (const s of sentences) {
      if (cur && (cur + s).length > max) {
        parts.push(cur);
        cur = s;
      } else {
        cur += s;
      }
    }
    if (cur.trim()) parts.push(cur);
    return parts.length ? parts : [text];
  }

  // FIX #2: generation counter — a superseded fetch/play must not hijack
  // newer playback or leak its object URL.
  async function playTts(id: string, text: string) {
    const gen = ++ttsGenRef.current;
    const alive = () => gen === ttsGenRef.current && mountedRef.current;
    try {
      stopAudioElement();
      clearAudioObjectUrl();
      if (!alive()) return;
      setSpeakingId(id);

      for (const chunk of splitForTts(text)) {
        if (!alive()) return;

        const r = await fetch("/api/angle-voice", {
          method: "POST",
          headers: { "Content-Type": "application/json", ...customKeyHeaders() },
          body: JSON.stringify({ text: chunk, voice }),
        });
        if (!alive()) return;
        if (!r.ok) throw new Error((await r.json().catch(() => ({}))).error || "အသံ ထုတ်မရပါ");

        const blob = await r.blob();
        if (!alive()) return;

        const url = URL.createObjectURL(blob);
        audioUrlRef.current = url;

        const a = new Audio(url);
        audioRef.current = a;

        const done = new Promise<void>((resolve) => {
          a.onended = () => {
            setSpeakingId(null);
            clearAudioObjectUrl();
            resolve();
          };
          a.onerror = () => {
            setSpeakingId(null);
            clearAudioObjectUrl();
            resolve();
          };
        });

        try {
          await a.play();
        } catch {
          setSpeakingId(null);
          clearAudioObjectUrl();
          throw new Error("အသံ ဖွင့်မရပါ");
        }
        await done;
      }
      setSpeakingId(null);
    } catch (e) {
      setSpeakingId(null);
      clearAudioObjectUrl();
      const err = e as Error;
      toast.error(err.message === "Failed to fetch" ? "အင်တာနက် ချိတ်ဆက်မှု မရပါ" : err.message);
    }
  }

  async function send(textArg?: string) {
    const text = (textArg ?? input).trim();
    if ((!text && !pending.length) || busy) return;

    const user: Msg = { id: crypto.randomUUID(), role: "user", content: text, images: pending.length ? pending : undefined };
    const asst: Msg = { id: crypto.randomUUID(), role: "assistant", content: "" };
    const history = [...msgs, user];

    // FIX #2: keep in-memory history bounded (storage already trims to 20).
    const next = [...history, asst];
    setMsgs(next.length > 60 ? next.slice(-60) : next);
    setInput("");
    setPending([]);
    setBusy(true);

    const contextStr = buildMemoryContext(memory);

    let full = "";
    try {
      const r = await fetch("/api/angle-chat", {
        method: "POST",
        headers: { "Content-Type": "application/json", ...customKeyHeaders() },
        body: JSON.stringify({
          // FIX #1: only the latest user message carries images — the server
          // ignores the rest, so sending them wastes megabytes of base64.
          messages: (() => {
            const win = history.slice(-20);
            const lastUser = win.map((m) => m.role).lastIndexOf("user");
            return win.map(({ role, content, images }, i) => ({
              role,
              content,
              images: i === lastUser ? images : undefined,
            }));
          })(),
          context: contextStr,
        }),
      });

      if (!r.ok || !r.body) throw new Error((await r.json().catch(() => ({}))).error || `Error ${r.status}`);

      const reader = r.body.getReader();
      const dec = new TextDecoder();

      for (;;) {
        const { done, value } = await reader.read();
        if (done) break;
        full += dec.decode(value, { stream: true });
        setMsgs((m) => m.map((x) => (x.id === asst.id ? { ...x, content: full } : x)));
      }

      if (!full.trim()) throw new Error("အဖြေ မရရှိပါ");
      if (speak) void playTts(asst.id, full);
    } catch (e) {
      const err = e as Error;
      const msg = err.message === "Failed to fetch" ? "အင်တာနက် ချိတ်ဆက်မှု မရပါ" : err.message;
      setMsgs((m) => m.map((x) => (x.id === asst.id ? { ...x, content: full || `⚠️ ${msg}` } : x)));
      toast.error(msg);
    } finally {
      setBusy(false);
      setTimeout(() => taRef.current?.focus(), 50);
    }
  }

  function clearRecTimer() {
    if (recTimerRef.current) {
      clearTimeout(recTimerRef.current);
      recTimerRef.current = null;
    }
  }

  async function toggleRec() {
    if (recording) {
      recRef.current?.stop();
      return;
    }

    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        audio: {
          echoCancellation: true,
          noiseSuppression: true,
          autoGainControl: true,
        },
      });
      streamRef.current = stream;

      const mime = MediaRecorder.isTypeSupported("audio/webm") ? "audio/webm" : "audio/mp4";
      const rec = new MediaRecorder(stream, { mimeType: mime });
      const chunks: Blob[] = [];

      rec.ondataavailable = (e) => e.data.size && chunks.push(e.data);
      rec.onstop = async () => {
        clearRecTimer();
        stream.getTracks().forEach((t) => t.stop());
        streamRef.current = null;
        // Unmount ဖြစ်ပြီးသားဆို tracks ရပ်ပြီးပြီ — UI/state အလုပ် ဆက်မလုပ်ပါ
        if (!mountedRef.current) return;
        setRecording(false);

        const blob = new Blob(chunks, { type: mime });
        if (blob.size < MIN_VOICE_BYTES) return toast.error("အသံ တိုလွန်းပါသည်");
        // FIX: အရင်က upper bound မရှိပါ — oversized payload → 400/413 ကာကွယ်ရန်
        if (blob.size > MAX_VOICE_BYTES) return toast.error("အသံဖိုင် ကြီးလွန်းပါသည် (10MB အထိသာ ရပါသည်)");

        setTranscribing(true);
        try {
          const fd = new FormData();
          fd.append("file", blob, mime === "audio/webm" ? "voice.webm" : "voice.m4a");
          const r = await fetch("/api/angle-voice", {
            method: "POST",
            headers: { ...customKeyHeaders() },
            body: fd,
          });
          const j = await r.json();

          if (!r.ok) throw new Error(j.error || "စာသားပြောင်းမရပါ");
          if (!j.text) throw new Error("စကားသံ မကြားရပါ");

          // FIX #1: recording finished while a send is in-flight — stash the
          // transcription in the input instead of silently dropping it.
          if (busyRef.current) {
            setInput(j.text);
            toast.info("စာသားရရှိပါပြီ — Send နှိပ်ပြီး ပို့ပါ");
          } else {
            await send(j.text);
          }
        } catch (e) {
          const err = e as Error;
          toast.error(err.message === "Failed to fetch" ? "အင်တာနက် ချိတ်ဆက်မှု မရပါ" : err.message);
        } finally {
          setTranscribing(false);
        }
      };

      rec.start();
      recRef.current = rec;
      // FIX: max duration auto-stop — recording အရှည်ကြီး → payload ကြီး → 400 ကာကွယ်ရန်
      recTimerRef.current = setTimeout(() => {
        recTimerRef.current = null;
        try {
          if (recRef.current?.state === "recording") recRef.current.stop();
        } catch {
          /* ignore */
        }
      }, MAX_REC_MS);
      setRecording(true);
    } catch {
      // FIX #6: MediaRecorder construction/start may throw AFTER getUserMedia
      // succeeded — release the mic so it doesn't stay open forever.
      streamRef.current?.getTracks().forEach((t) => {
        try {
          t.stop();
        } catch {
          /* ignore */
        }
      });
      streamRef.current = null;
      toast.error("မိုက်ခရိုဖုန်း ခွင့်ပြုချက် မရပါ");
    }
  }

  // FIX #7: image attach was dead (pending[] never populated) — wire it up.
  function readAsDataURL(f: File): Promise<string> {
    return new Promise((res, rej) => {
      const r = new FileReader();
      r.onload = () => res(r.result as string);
      r.onerror = () => rej(new Error("read failed"));
      r.readAsDataURL(f);
    });
  }

  async function onPickImages(files: FileList | null) {
    if (!files) return;
    // FIX #4: total payload guard — per-file cap exists, but 8 files could
    // still be ~40MB of base64 in one JSON body.
    const MAX_TOTAL_BYTES = 12 * 1024 * 1024;
    let total = pending.reduce((n, s) => n + Math.ceil(s.length * 0.75), 0); // ~base64 bytes
    const urls: string[] = [];
    for (const f of Array.from(files).slice(0, 8)) {
      if (!f.type.startsWith("image/")) continue;
      if (f.size > 5 * 1024 * 1024) {
        toast.error("ဓာတ်ပုံ ကြီးလွန်းပါသည် (5MB အထိ)");
        continue;
      }
      if (total + f.size > MAX_TOTAL_BYTES) {
        toast.error("ဓာတ်ပုံ စုစုပေါင်း ကြီးလွန်းပါသည် (12MB အထိ)");
        break;
      }
      try {
        urls.push(await readAsDataURL(f));
        total += f.size;
      } catch {
        /* skip unreadable file */
      }
    }
    if (urls.length) setPending((p) => [...p, ...urls].slice(0, 8));
    // reset so the same file can be picked again
    if (fileRef.current) fileRef.current.value = "";
  }

  return (
    <>
      <button
        onClick={() => setOpen(true)}
        className="fixed bottom-5 right-5 z-40 flex items-center gap-2 rounded-full bg-primary px-4 py-3 text-primary-foreground shadow-lg hover:opacity-90"
        aria-label="AI Angle"
      >
        <Feather className="h-5 w-5" />
        <span className="text-sm font-semibold">AI Angle</span>
      </button>

      <Sheet open={open} onOpenChange={setOpen}>
        <SheetContent side="right" className="flex w-full flex-col gap-0 p-0 sm:max-w-md">
          <SheetHeader className="border-b border-border p-4">
            <SheetTitle className="flex items-center gap-2">
              <Feather className="h-5 w-5 text-primary" /> AI Angle
            </SheetTitle>
            <div className="flex items-center gap-2 pt-1">
              <Button size="sm" variant="ghost" onClick={() => setSpeak((s) => !s)} aria-label="Voice Toggle">
                {speak ? <Volume2 className="h-4 w-4" /> : <VolumeX className="h-4 w-4" />}
              </Button>
              <select
                value={voice}
                onChange={(e) => setVoice(e.target.value)}
                className="h-8 rounded-md border border-border bg-background px-2 text-xs"
              >
                {VOICES.map((v) => (
                  <option key={v}>{v}</option>
                ))}
              </select>
              <Button size="sm" variant="ghost" onClick={() => playTts("test", "မင်္ဂလာပါ။ ကျွန်မ AI Angle ပါ။ ဘာကူညီပေးရမလဲရှင်။")}>
                <Play className="mr-1 h-3 w-3" /> စမ်းနားထောင်
              </Button>
              {/* FIX #5: memory panel toggle — every write helper has a UI trigger */}
              <Button size="sm" variant="ghost" onClick={() => setMemOpen((v) => !v)} aria-label="Memory">
                <Brain className="h-4 w-4" />
              </Button>
              {/* API Key Settings (BYOK) */}
              <Button size="sm" variant="ghost" onClick={() => setSettingsOpen((v) => !v)} aria-label="API Key Settings">
                <SettingsIcon className="h-4 w-4" />
              </Button>
              <Button
                size="sm"
                variant="ghost"
                className="ml-auto text-destructive hover:text-destructive"
                onClick={() => {
                  // FIX #3: clear-chat must also stop TTS playback.
                  ttsGenRef.current++;
                  stopAudioElement();
                  clearAudioObjectUrl();
                  setSpeakingId(null);
                  setMsgs([]);
                  toast.success("စကားဝိုင်း အသစ် စတင်လိုက်ပါပြီ");
                }}
                aria-label="Clear Chat"
              >
                <Trash2 className="h-4 w-4" />
              </Button>
            </div>
          </SheetHeader>

          {/* API Key Settings (BYOK) — user's own key, device-local only */}
          {settingsOpen && (
            <div className="space-y-2 border-b border-border p-3 text-xs">
              <p className="text-sm font-semibold">⚙️ API Key ဆက်တင်</p>
              <div className="relative">
                <input
                  type={showKey ? "text" : "password"}
                  value={apiKeyInput}
                  onChange={(e) => {
                    setApiKeyInput(e.target.value);
                    setKeyValid("unknown");
                  }}
                  placeholder="Lovable API Key"
                  autoComplete="off"
                  spellCheck={false}
                  className="h-8 w-full rounded-md border border-border bg-background px-2 pr-8"
                />
                <button
                  onClick={() => setShowKey((s) => !s)}
                  className="absolute right-2 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
                  aria-label={showKey ? "Hide key" : "Show key"}
                >
                  {showKey ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                </button>
              </div>
              {keyValid !== "unknown" && (
                <span
                  className={`inline-block rounded-full px-2 py-0.5 font-medium ${
                    keyValid === "valid"
                      ? "bg-green-500/15 text-green-600 dark:text-green-400"
                      : "bg-red-500/15 text-red-600 dark:text-red-400"
                  }`}
                >
                  {keyValid === "valid" ? "✅ မှန်ကန်ပါသည်" : "❌ မမှန်ကန်ပါ"}
                </span>
              )}
              <div className="flex gap-2">
                <Button size="sm" onClick={saveKey}>
                  သိမ်းဆည်းမည်
                </Button>
                <Button size="sm" variant="outline" onClick={validateKey} disabled={validating}>
                  {validating && <Loader2 className="mr-1 h-3 w-3 animate-spin" />}
                  အမှားအမှန် စစ်ဆေးမည်
                </Button>
              </div>
              <p className="text-muted-foreground">
                Key ကို ဒီ browser မှာပဲ သိမ်းထားပါတယ်။ ထည့်ထားရင် server env key အစား
                ဒီ key ကို သုံးပါမယ်။ မထည့်ထားရင် server default အတိုင်း အလုပ်လုပ်ပါတယ်။
              </p>
            </div>
          )}

          {/* FIX #5: memory manager — UI triggers for all write helpers */}
          {memOpen && (
            <div className="max-h-64 space-y-2 overflow-y-auto border-b border-border p-3 text-xs">
              <p className="text-sm font-semibold">🧠 မှတ်ဉာဏ် စီမံခန့်ခွဲမှု</p>
              <div className="flex gap-2">
                <input
                  value={memory.persona.name ?? ""}
                  onChange={(e) => setMemory((m) => withPersona(m, { name: e.target.value.trim() || undefined }))}
                  placeholder="အမည်"
                  className="h-8 flex-1 rounded-md border border-border bg-background px-2"
                />
                <input
                  value={memory.persona.dialect ?? ""}
                  onChange={(e) => setMemory((m) => withPersona(m, { dialect: e.target.value.trim() || undefined }))}
                  placeholder="ဒေသစကား"
                  className="h-8 flex-1 rounded-md border border-border bg-background px-2"
                />
              </div>
              <div className="flex gap-2">
                <input
                  value={prefInput}
                  onChange={(e) => setPrefInput(e.target.value)}
                  placeholder="အကြိုက်အသစ် ထည့်ပါ"
                  className="h-8 flex-1 rounded-md border border-border bg-background px-2"
                />
                <Button size="sm" onClick={() => { setMemory((m) => withPreference(m, prefInput)); setPrefInput(""); }}>ထည့်</Button>
              </div>
              {memory.persona.preferences.map((p) => (
                <div key={p} className="flex items-center justify-between rounded bg-muted px-2 py-1">
                  <span className="truncate">• {p}</span>
                  <button onClick={() => setMemory((m) => removePreference(m, p))} className="text-destructive" aria-label="Remove preference">✕</button>
                </div>
              ))}
              <div className="flex gap-2">
                <input
                  value={noteInput}
                  onChange={(e) => setNoteInput(e.target.value)}
                  placeholder="မှတ်သားချက်အသစ်"
                  className="h-8 flex-1 rounded-md border border-border bg-background px-2"
                />
                <Button size="sm" onClick={() => { setMemory((m) => withCustomNote(m, noteInput)); setNoteInput(""); }}>ထည့်</Button>
              </div>
              {memory.customNotes.map((n) => (
                <div key={n} className="flex items-center justify-between rounded bg-muted px-2 py-1">
                  <span className="truncate">• {n}</span>
                  <button onClick={() => setMemory((m) => removeCustomNote(m, n))} className="text-destructive" aria-label="Remove note">✕</button>
                </div>
              ))}
              <div className="flex gap-2">
                <input
                  value={jargonTerm}
                  onChange={(e) => setJargonTerm(e.target.value)}
                  placeholder="အသုံးအနှုန်း"
                  className="h-8 flex-1 rounded-md border border-border bg-background px-2"
                />
                <input
                  value={jargonMeaning}
                  onChange={(e) => setJargonMeaning(e.target.value)}
                  placeholder="အဓိပ္ပာယ်"
                  className="h-8 flex-1 rounded-md border border-border bg-background px-2"
                />
                <Button size="sm" onClick={() => { setMemory((m) => withJargon(m, jargonTerm, jargonMeaning)); setJargonTerm(""); setJargonMeaning(""); }}>ထည့်</Button>
              </div>
              {Object.entries(memory.jargonAndTerms).map(([k, v]) => (
                <div key={k} className="flex items-center justify-between rounded bg-muted px-2 py-1">
                  <span className="truncate">• {k}: {v}</span>
                  <button onClick={() => setMemory((m) => removeJargon(m, k))} className="text-destructive" aria-label="Remove term">✕</button>
                </div>
              ))}
              {memory.topics.length > 0 && <p className="pt-1 font-semibold">မှတ်သားထားသော အကြောင်းအရာများ</p>}
              {memory.topics.slice(-10).reverse().map((t) => (
                <div key={`${t.date}-${t.title}`} className="flex items-center justify-between rounded bg-muted px-2 py-1">
                  <span className="truncate">• [{t.date}] {t.title}</span>
                  <button onClick={() => setMemory((m) => removeTopic(m, t.title, t.date))} className="text-destructive" aria-label="Remove topic">✕</button>
                </div>
              ))}
            </div>
          )}

          <div className="flex-1 space-y-4 overflow-y-auto p-4">
            {!msgs.length && (
              <p className="pt-10 text-center text-sm text-muted-foreground">
                စာရိုက်ပါ၊ မိုက်နှိပ်ပြောပါ၊ ဓာတ်ပုံ/ဗီဒီယို ပို့ပါ — AI Angle က မြန်မာလို ပြန်ဖြေပေးပါမယ်။
              </p>
            )}
            {msgs.map((m, idx) =>
              m.role === "user" ? (
                <div key={m.id} className="ml-auto max-w-[85%] rounded-2xl bg-primary px-3 py-2 text-sm text-primary-foreground">
                  {m.images && m.images.length > 0 && (
                    <div className="mb-1 flex flex-wrap gap-1">
                      {m.images.map((s, i) =>
                        s ? <img key={i} src={s} alt="" className="h-16 w-16 rounded object-cover" /> : null
                      )}
                    </div>
                  )}
                  <div className="whitespace-pre-wrap">{m.content}</div>
                </div>
              ) : (
                <div key={m.id} className="max-w-[95%] text-sm text-foreground">
                  {m.content ? (
                    <div className="prose prose-sm dark:prose-invert max-w-none leading-relaxed">
                      <ReactMarkdown>{m.content}</ReactMarkdown>
                    </div>
                  ) : (
                    <span className="flex items-center gap-2 text-muted-foreground">
                      <Loader2 className="h-4 w-4 animate-spin" /> စဉ်းစားနေပါတယ်...
                    </span>
                  )}
                  {m.content && !busy && (
                    <div className="mt-1 flex items-center gap-3">
                      <button onClick={() => playTts(m.id, m.content)} className="text-xs text-muted-foreground hover:text-foreground">
                        {speakingId === m.id ? "🔊 ပြောနေသည်..." : "🔈 နားထောင်ရန်"}
                      </button>
                      <button
                        onClick={() => {
                          const prevUser = [...msgs.slice(0, idx)].reverse().find((x) => x.role === "user");
                          rememberExchange(prevUser?.content ?? "", m.content);
                        }}
                        className="flex items-center gap-1 text-xs text-muted-foreground hover:text-foreground"
                        aria-label="Remember this exchange"
                      >
                        <Bookmark className="h-3 w-3" /> မှတ်သားရန်
                      </button>
                    </div>
                  )}
                </div>
              )
            )}
            <div ref={endRef} />
          </div>

          <div className="border-t border-border p-3">
            {/* FIX #7: pending image thumbnails (was dead UI) */}
            {pending.length > 0 && (
              <div className="mb-2 flex flex-wrap gap-1">
                {pending.map((s, i) => (
                  <div key={i} className="relative">
                    <img src={s} alt="" className="h-14 w-14 rounded object-cover" />
                    <button
                      onClick={() => setPending((p) => p.filter((_, j) => j !== i))}
                      className="absolute -right-1 -top-1 rounded-full bg-destructive px-1 text-[10px] text-destructive-foreground"
                      aria-label="Remove image"
                    >
                      ✕
                    </button>
                  </div>
                ))}
              </div>
            )}
            <div className="flex items-end gap-2">
              <input
                ref={fileRef}
                type="file"
                accept="image/*"
                multiple
                className="hidden"
                onChange={(e) => void onPickImages(e.target.files)}
              />
              <Button
                size="icon"
                variant="outline"
                onClick={() => fileRef.current?.click()}
                disabled={busy || transcribing}
                aria-label="Attach images"
              >
                <ImageIcon className="h-4 w-4" />
              </Button>
              <Textarea
                ref={taRef}
                value={input}
                onChange={(e) => setInput(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === "Enter" && !e.shiftKey) {
                    e.preventDefault();
                    send();
                  }
                }}
                placeholder={transcribing ? "အသံကို စာသားပြောင်းနေသည်..." : "AI Angle ကို မေးပါ..."}
                rows={1}
                className="max-h-32 min-h-10 flex-1 resize-none"
              />
              <Button size="icon" variant={recording ? "destructive" : "outline"} onClick={toggleRec} disabled={busy || transcribing} aria-label="Mic">
                {transcribing ? <Loader2 className="h-4 w-4 animate-spin" /> : recording ? <Square className="h-4 w-4" /> : <Mic className="h-4 w-4" />}
              </Button>
              <Button size="icon" onClick={() => send()} disabled={busy || (!input.trim() && !pending.length)} aria-label="Send">
                {busy ? <Loader2 className="h-4 w-4 animate-spin" /> : <Send className="h-4 w-4" />}
              </Button>
            </div>
          </div>
        </SheetContent>
      </Sheet>
    </>
  );
}
