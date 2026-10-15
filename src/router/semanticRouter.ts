/**
 * Semantic/Cognitive Router skeleton.
 * Accepts NLU result & session context and decides: execute / clarify / escalate.
 */

import type { NluResult } from '../nlu/localNlu.ts';

export type RouterDecision =
  | { action: 'execute'; handler: string; reason?: string }
  | { action: 'clarify'; question: string }
  | { action: 'fallback'; reason: string };

export function route(nlu: NluResult, session: any): RouterDecision {
  // High confidence -> execute
  if (nlu.confidence >= 0.85 && nlu.intent !== 'unknown') {
    return { action: 'execute', handler: nlu.intent, reason: 'high_confidence_local' };
  }

  // Ambiguous -> clarification prompt
  if (nlu.confidence >= 0.4 && nlu.confidence < 0.85) {
    return { action: 'clarify', question: 'Did you mean to control EcoFlow or make a payment?' };
  }

  // Unknown -> fallback to Gemini or ask clarifying question
  return { action: 'fallback', reason: 'low_confidence' };
}
