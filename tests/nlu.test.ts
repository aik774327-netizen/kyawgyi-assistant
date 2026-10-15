/**
 * Jest fixtures for 62 Myanmar utterances mapped to expected intents.
 * Uses src/nlu/localNlu.ts
 */
import { parseUtterance } from '../src/nlu/localNlu';

const fixtures: { utterance: string; expected: string }[] = [
  // 62 examples (device control, battery, app opens, calls, payments, etc.)
  { utterance: 'EcoFlow ကို ဖွင့်ပါ', expected: 'ecoflow.turn_on' },
  { utterance: 'မီးကို ဖွင့်줘', expected: 'ecoflow.turn_on' },
  { utterance: 'ပစ္စည်းကို ပိတ်ပါ', expected: 'ecoflow.turn_off' },
  { utterance: 'ဖုန်းခေါ်ပါ Aung ကို', expected: 'phone.call' },
  { utterance: 'KBZPay ဖြင့် ပေးဆောင်ပါ', expected: 'payment.kbzpay' },
  { utterance: 'ဘက်ထရီ အခြေအနေကို ပြပါ', expected: 'ecoflow.check_battery' },
  { utterance: 'Viber ဖွင့်ပါ', expected: 'app.open_viber' },
  { utterance: 'Telegram ဖွင့်ပါ', expected: 'app.open_telegram' },
  { utterance: 'Turn on the EcoFlow', expected: 'ecoflow.turn_on' },
  { utterance: 'Turn off the EcoFlow', expected: 'ecoflow.turn_off' },
  { utterance: 'Check battery level', expected: 'ecoflow.check_battery' },
  { utterance: 'Open Viber app', expected: 'app.open_viber' },
  { utterance: 'Open Telegram app', expected: 'app.open_telegram' },
  { utterance: 'Call Maung', expected: 'phone.call' },
  { utterance: 'KBZ pay ပေးပါ', expected: 'payment.kbzpay' },
  { utterance: 'ဘက်ထရီ စစ်ပါ', expected: 'ecoflow.check_battery' },
  { utterance: 'EcoFlow ပိတ်ပါ', expected: 'ecoflow.turn_off' },
  { utterance: 'EcoFlow ဖွင့်ပါ အခု', expected: 'ecoflow.turn_on' },
  { utterance: 'ဖုန်းကို ခေါ်ပါ', expected: 'phone.call' },
  { utterance: 'KBZPay link ပြပါ', expected: 'payment.kbzpay' },
  { utterance: 'ဗိုက်ဘာ ဖွင့်ပေး', expected: 'app.open_viber' },
  { utterance: 'တယ်လီဂရမ် ဖွင့်ပေး', expected: 'app.open_telegram' },
  { utterance: 'ဘက်ထရီဘယ်လောက်ရှိလေ', expected: 'ecoflow.check_battery' },
  { utterance: 'ပစ္စည်းကို ပိတ်ပါ', expected: 'ecoflow.turn_off' },
  { utterance: 'ဖွင့်ပါ EcoFlow', expected: 'ecoflow.turn_on' },
  { utterance: 'Call number 09xxxxxxx', expected: 'phone.call' },
  { utterance: 'Send payment via KBZ', expected: 'payment.kbzpay' },
  { utterance: 'Open Viber chat', expected: 'app.open_viber' },
  { utterance: 'Open Telegram chat', expected: 'app.open_telegram' },
  { utterance: 'ဘက်ထရီ စာရင်း', expected: 'ecoflow.check_battery' },
  { utterance: 'Power on EcoFlow', expected: 'ecoflow.turn_on' },
  { utterance: 'Power off EcoFlow', expected: 'ecoflow.turn_off' },
  { utterance: 'Call Aye Aye', expected: 'phone.call' },
  { utterance: 'KBZPay link ဖန်တီးပါ', expected: 'payment.kbzpay' },
  { utterance: 'ဗိုက်ဘာ သွင်းပါ', expected: 'app.open_viber' },
  { utterance: 'တယ်လီဂရမ် သွင်းပါ', expected: 'app.open_telegram' },
  { utterance: 'battery status ပြပါ', expected: 'ecoflow.check_battery' },
  { utterance: 'turn on', expected: 'ecoflow.turn_on' },
  { utterance: 'turn off', expected: 'ecoflow.turn_off' },
  { utterance: 'check battery', expected: 'ecoflow.check_battery' },
  { utterance: 'open viber', expected: 'app.open_viber' },
  { utterance: 'open telegram', expected: 'app.open_telegram' },
  { utterance: 'call my mom', expected: 'phone.call' },
  { utterance: 'pay with kbz', expected: 'payment.kbzpay' },
  { utterance: 'EcoFlow အားပေးပါ', expected: 'ecoflow.turn_on' },
  { utterance: 'EcoFlow power off', expected: 'ecoflow.turn_off' },
  { utterance: 'Battery check', expected: 'ecoflow.check_battery' },
  { utterance: 'Open messenger in Viber', expected: 'app.open_viber' },
  { utterance: 'Open telegram to chat', expected: 'app.open_telegram' },
  { utterance: 'Call number 0912345678', expected: 'phone.call' },
  { utterance: 'KBZ payment please', expected: 'payment.kbzpay' },
  { utterance: 'ဖွင့်ပေးပါ', expected: 'ecoflow.turn_on' },
  { utterance: 'ပိတ်ပေးပါ', expected: 'ecoflow.turn_off' },
  { utterance: 'ဘက်ထရီကို ကြည့်ပါ', expected: 'ecoflow.check_battery' },
  { utterance: 'ဗိုက်ဘာ ဖွင့်ပေးပါ', expected: 'app.open_viber' },
  { utterance: 'တယ်လီဂရမ် ကို ဖွင့်ပါ', expected: 'app.open_telegram' },
  { utterance: 'call phone', expected: 'phone.call' },
  { utterance: 'kbzpay open', expected: 'payment.kbzpay' },
  { utterance: 'ဗိုင်းဘာ ဖွင့်', expected: 'app.open_viber' },
  { utterance: 'KBZPay ဖွင့်ပါ', expected: 'payment.kbzpay' },
  { utterance: 'မေမေ့ဆီ ဖုန်းခေါ်', expected: 'phone.call' },
  { utterance: 'ပါဝါ ဖွင့်ပါ', expected: 'ecoflow.turn_on' },
];

describe('Local NLU fixtures', () => {
  for (const f of fixtures) {
    it(`"${f.utterance}" => ${f.expected}`, () => {
      const r = parseUtterance(f.utterance);
      expect(r.intent).toBe(f.expected);
      expect(typeof r.confidence).toBe('number');
    });
  }
});
