import { describe, expect, it } from 'vitest';
import { cx, fmtDate, fmtNum, fmtTime } from './utils';

describe('common utils', () => {
  it('joins truthy class names only', () => {
    expect(cx('base', false, 'active', null, undefined)).toBe('base active');
  });

  it('formats dates from backend ISO strings', () => {
    expect(fmtDate('2026-06-02T15:38:19')).toBe('2026-06-02 15:38:19');
    expect(fmtDate()).toBe('-');
  });

  it('formats finite numbers and hides invalid values', () => {
    expect(fmtNum(12.345, 2)).toBe('12.35');
    expect(fmtNum(Number.NaN)).toBe('-');
    expect(fmtNum(Number.POSITIVE_INFINITY)).toBe('-');
  });

  it('returns a placeholder for invalid times', () => {
    expect(fmtTime('not-a-date')).toBe('-');
  });
});
