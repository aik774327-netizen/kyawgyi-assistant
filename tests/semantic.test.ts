/**
 * Router tests
 */
import { route } from '../src/router/semanticRouter';
import { parseUtterance } from '../src/nlu/localNlu';

describe('Semantic Router', () => {
  it('routes high confidence local intent to execute', () => {
    const nlu = parseUtterance('EcoFlow ကို ဖွင့်ပါ');
    const decision = route(nlu, {});
    expect(decision.action).toBe('execute');
  });

  it('asks clarification on mid confidence', () => {
    const nlu = { intent: 'unknown', confidence: 0.5, entities: [] };
    const decision = route(nlu as any, {});
    expect(decision.action).toBe('clarify');
  });

  it('fallbacks on very low confidence', () => {
    const nlu = { intent: 'unknown', confidence: 0.0, entities: [] };
    const decision = route(nlu as any, {});
    expect(decision.action).toBe('fallback');
  });
});
