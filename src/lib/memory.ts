// AI Context & Memory Management System (Optimized & Token-Safe)

export type UserMemory = {
  persona: {
    name?: string;
    dialect?: string; // ရခိုင်၊ ထားဝယ်၊ ရှမ်း၊ အညာ စသည်
    preferences: string[];
  };
  topics: {
    title: string;
    summary: string;
    date: string;
  }[];
  jargonAndTerms: Record<string, string>;
  customNotes: string[];
};

const MEMORY_STORAGE_KEY = "ai_angle_long_term_memory_v2";

/** Deep-freeze so accidental direct mutation of the shared default fails loudly. */
function deepFreeze<T>(obj: T): T {
  if (obj && typeof obj === "object" && !Object.isFrozen(obj)) {
    Object.values(obj).forEach((v) => deepFreeze(v));
    Object.freeze(obj);
  }
  return obj;
}

export const defaultMemory: UserMemory = deepFreeze<UserMemory>({
  persona: {
    preferences: [],
  },
  topics: [],
  jargonAndTerms: {},
  customNotes: [],
});

/**
 * FIX (shared-mutable side-effect): never hand out the `defaultMemory`
 * singleton itself. Callers may mutate the returned object freely —
 * each call gets a fresh deep copy.
 */
function freshMemory(): UserMemory {
  return {
    persona: {
      name: defaultMemory.persona.name,
      dialect: defaultMemory.persona.dialect,
      preferences: [...defaultMemory.persona.preferences],
    },
    topics: defaultMemory.topics.map((t) => ({ ...t })),
    jargonAndTerms: { ...defaultMemory.jargonAndTerms },
    customNotes: [...defaultMemory.customNotes],
  };
}

// Memory ကို LocalStorage မှ ရယူခြင်း (immutable — shared reference မပေးပါ)
//
// DATA NOTE (#7): storage is device-local localStorage only — no backend,
// no sync, no backup. Clearing browser data wipes long-term memory.
// A server-side store is a future architectural step, not a code fix here.
export function loadUserMemory(): UserMemory {
  try {
    const data = localStorage.getItem(MEMORY_STORAGE_KEY);
    if (!data) return freshMemory();
    const parsed = JSON.parse(data);
    const strArr = (v: unknown): string[] =>
      Array.isArray(v) ? v.filter((x): x is string => typeof x === "string") : [];
    return {
      persona: {
        name: typeof parsed?.persona?.name === "string" ? parsed.persona.name : undefined,
        dialect: typeof parsed?.persona?.dialect === "string" ? parsed.persona.dialect : undefined,
        preferences: strArr(parsed?.persona?.preferences),
      },
      // FIX #6: validate each topic's shape — a hand-edited or corrupt entry
      // must not crash buildMemoryContext (t.summary.slice on non-string).
      topics: Array.isArray(parsed?.topics)
        ? parsed.topics
            .filter((t) => t && typeof t.title === "string" && typeof t.summary === "string")
            .map((t) => ({
              title: t.title as string,
              summary: t.summary as string,
              date: typeof t.date === "string" ? (t.date as string) : "",
            }))
        : [],
      jargonAndTerms:
        typeof parsed?.jargonAndTerms === "object" && parsed.jargonAndTerms !== null
          ? Object.fromEntries(
              Object.entries(parsed.jargonAndTerms).filter(
                ([k, v]) => typeof k === "string" && typeof v === "string"
              )
            )
          : {},
      customNotes: strArr(parsed?.customNotes),
    };
  } catch {
    return freshMemory();
  }
}

// Memory သိမ်းဆည်းခြင်း (input ကို mutate မလုပ်ပါ)
export function saveUserMemory(memory: UserMemory): void {
  try {
    localStorage.setItem(MEMORY_STORAGE_KEY, JSON.stringify(memory));
  } catch (e) {
    console.error("Memory saving error:", e);
  }
}

// Token Bloat မဖြစ်စေရန် စစ်ထုတ်ထားသော Context စာသား တည်ဆောက်ခြင်း
export function buildMemoryContext(memory: UserMemory): string {
  const parts: string[] = [];

  if (memory.persona.name) {
    parts.push(`- အသုံးပြုသူအမည်: ${memory.persona.name}`);
  }
  if (memory.persona.dialect) {
    parts.push(`- သုံးစွဲသည့် ဒေသစကား/အသံထွက်: ${memory.persona.dialect}`);
  }
  if (memory.persona.preferences.length > 0) {
    const recentPrefs = memory.persona.preferences.slice(-10);
    parts.push(`- အသုံးပြုသူ၏ အကြိုက်နှင့် ဦးစားပေးချက်များ:\n  * ${recentPrefs.join("\n  * ")}`);
  }
  if (Object.keys(memory.jargonAndTerms).length > 0) {
    const jargonEntries = Object.entries(memory.jargonAndTerms).slice(-15);
    parts.push(`- စကားဝိုင်းသုံး အထူးအသုံးအနှုန်းများ (Jargon):\n${jargonEntries.map(([k, v]) => `  * ${k}:${v}`).join("\n")}`);
  }
  if (memory.topics.length > 0) {
    // Topic များကို နောက်ဆုံး ၆ ခုသာ ယူပြီး Summary ကို ဇာတ်ကောင် ၁၅၀ အထိ ကန့်သတ်သည်
    const recentTopics = memory.topics.slice(-6);
    parts.push(
      `- ယခင်ရက်များက ဆွေးနွေးခဲ့သည့် အဓိက အကြောင်းအရာများ:\n${recentTopics
        .map((t) => `  * [${t.date}] ${t.title}:${t.summary.slice(0, 150)}`)
        .join("\n")}`
    );
  }
  if (memory.customNotes.length > 0) {
    const recentNotes = memory.customNotes.slice(-10);
    parts.push(`- ထပ်ဆောင်း မှတ်သားချက်များ:\n  * ${recentNotes.join("\n  * ")}`);
  }

  return parts.length > 0 ? parts.join("\n\n") : "ယခင် မှတ်ဉာဏ်ဒေတာ မရှိသေးပါ။";
}

// ---------------------------------------------------------------------------
// Immutable update helpers — the WRITE path for long-term memory.
// Each returns a NEW UserMemory; the input is never mutated.
// Stored collections are capped so LocalStorage + token context stay bounded.
// ---------------------------------------------------------------------------

const MAX_STORED_TOPICS = 50;
const MAX_STORED_PREFS = 30;
const MAX_STORED_NOTES = 30;
const MAX_STORED_JARGON = 60;

export function withPreference(memory: UserMemory, pref: string): UserMemory {
  const p = pref.trim();
  if (!p || memory.persona.preferences.includes(p)) return memory;
  return {
    ...memory,
    persona: {
      ...memory.persona,
      preferences: [...memory.persona.preferences, p].slice(-MAX_STORED_PREFS),
    },
  };
}

export function withCustomNote(memory: UserMemory, note: string): UserMemory {
  const n = note.trim();
  if (!n) return memory;
  return {
    ...memory,
    customNotes: [...memory.customNotes, n].slice(-MAX_STORED_NOTES),
  };
}

export function withTopic(
  memory: UserMemory,
  topic: { title: string; summary: string; date: string }
): UserMemory {
  const title = topic.title.trim();
  if (!title) return memory;
  return {
    ...memory,
    topics: [...memory.topics, { ...topic, title }].slice(-MAX_STORED_TOPICS),
  };
}

export function withJargon(memory: UserMemory, term: string, meaning: string): UserMemory {
  const t = term.trim();
  if (!t) return memory;
  const entries = Object.entries({ ...memory.jargonAndTerms, [t]: meaning });
  return {
    ...memory,
    jargonAndTerms: Object.fromEntries(entries.slice(-MAX_STORED_JARGON)),
  };
}

export function withPersona(
  memory: UserMemory,
  patch: Partial<Pick<UserMemory["persona"], "name" | "dialect">>
): UserMemory {
  return {
    ...memory,
    persona: { ...memory.persona, ...patch },
  };
}

// ---------------------------------------------------------------------------
// Removal helpers — companions for the add helpers above (used by the
// memory panel UI). Immutable as well.
// ---------------------------------------------------------------------------

export function removePreference(memory: UserMemory, pref: string): UserMemory {
  return {
    ...memory,
    persona: {
      ...memory.persona,
      preferences: memory.persona.preferences.filter((p) => p !== pref),
    },
  };
}

export function removeCustomNote(memory: UserMemory, note: string): UserMemory {
  return {
    ...memory,
    customNotes: memory.customNotes.filter((n) => n !== note),
  };
}

export function removeJargon(memory: UserMemory, term: string): UserMemory {
  const { [term]: _dropped, ...rest } = memory.jargonAndTerms;
  return { ...memory, jargonAndTerms: rest };
}

export function removeTopic(memory: UserMemory, title: string, date: string): UserMemory {
  return {
    ...memory,
    topics: memory.topics.filter((t) => !(t.title === title && t.date === date)),
  };
}
