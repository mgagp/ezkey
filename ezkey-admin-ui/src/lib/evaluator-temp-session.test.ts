import { describe, expect, it } from 'vitest';
import { isEvaluatorTempSession } from '@/lib/auth';

describe('isEvaluatorTempSession', () => {
  it('is true only for EVALUATOR_TEMP purpose', () => {
    expect(isEvaluatorTempSession({ tokenPurpose: 'EVALUATOR_TEMP' })).toBe(true);
    expect(isEvaluatorTempSession({ tokenPurpose: 'SESSION' })).toBe(false);
    expect(isEvaluatorTempSession({})).toBe(false);
    expect(isEvaluatorTempSession(null)).toBe(false);
    expect(isEvaluatorTempSession(undefined)).toBe(false);
  });
});
