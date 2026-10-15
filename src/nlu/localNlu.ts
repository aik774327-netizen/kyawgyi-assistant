/**
 * Minimal local NLU rule engine for Myanmar utterances.
 * parseUtterance returns { intent, confidence, entities, raw }
 *
 * Config:
 *   LOCAL_CONF_THRESHOLD (default 0.85)
 *   GEMINI_CONF_THRESHOLD (default 0.75)
 *
 * For real usage: extend tokenization, morphological normalization, ML classifier.
 */

export type NluEntity = { name: string; value: string };
export type NluResult = {
  intent: string;
  confidence: number;
  entities: NluEntity[];
  raw?: any;
  usedFallback?: 'local' | 'gemini';
};

const LOCAL_CONF_THRESHOLD = Number(process.env.LOCAL_CONF_THRESHOLD ?? 0.85);
const GEMINI_CONF_THRESHOLD = Number(process.env.GEMINI_CONF_THRESHOLD ?? 0.75);

// Rules ordered with specific intents first to prevent generic keyword overrides
const rules: { intent: string; patterns: RegExp[] }[] = [
  { intent: 'app.open_viber', patterns: [/Viber|ဗိုက်ဘာ|ဗိုင်းဘာ/i] },
  { intent: 'app.open_telegram', patterns: [/Telegram|တယ်လီဂရမ်/i] },
  { intent: 'payment.kbzpay', patterns: [/KBZ|KBZPay|ကေဘဇ်/i] },
  { intent: 'phone.call', patterns: [/ခေါ်|call|တယ်လီဖုန်း/i] },
  { intent: 'ecoflow.check_battery', patterns: [/battery|ဘက်ထရီ|အား\b|အားအခြေအနေ|စစ်ပါ|စာရင်း|ကြည့်ပါ|ဘယ်လောက်/i] },
  { intent: 'ecoflow.turn_on', patterns: [/ဖွင့်/, /ဖွင့်/, /\bon\b|turn on|power on|အားပေးပါ/i] },
  { intent: 'ecoflow.turn_off', patterns: [/ပိတ်/, /ပိတ္/, /\boff\b|turn off|power off/i] },
];

export function parseUtterance(utterance: string): NluResult {
  const normalized = utterance.trim();
  for (const rule of rules) {
    for (const p of rule.patterns) {
      if (p.test(normalized)) {
        return {
          intent: rule.intent,
          confidence: 0.95,
          entities: [], // Extract entities in extended engine
          raw: { text: utterance },
          usedFallback: 'local',
        };
      }
    }
  }

  // No rule matched -> low confidence unknown
  return {
    intent: 'unknown',
    confidence: 0.0,
    entities: [],
    raw: { text: utterance },
    usedFallback: 'local',
  };
}
