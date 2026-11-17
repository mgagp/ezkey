import { describe, expect, it } from 'vitest';
import { resolveHelpTopicId } from './help-topics';
import {
  adminDetailHref,
  buildListDetailNavState,
  resolveListDetailNavigation,
} from './list-detail-navigation';

describe('adminDetailHref (#1399)', () => {
  it('points at the record page', () => {
    expect(adminDetailHref(27)).toBe('/admins/27');
  });

  it('keeps the admins help topic on the record page', () => {
    expect(resolveHelpTopicId('/admins')).toBe('admins');
    expect(resolveHelpTopicId(adminDetailHref(27))).toBe('admins');
  });
});

describe('list → record prev/next state', () => {
  const rows = [{ adminId: 3 }, { adminId: 9 }, { adminId: 12 }];

  it('resolves the neighbours of the clicked row', () => {
    const state = buildListDetailNavState(rows, (r) => r.adminId, rows[1], true);
    const nav = resolveListDetailNavigation(state, 9);
    expect(nav).toMatchObject({ prevId: 3, nextId: 12, listHasMorePages: true });
  });

  it('ignores state that does not match the opened record', () => {
    const state = buildListDetailNavState(rows, (r) => r.adminId, rows[1]);
    expect(resolveListDetailNavigation(state, 12)).toBeNull();
    expect(resolveListDetailNavigation(undefined, 9)).toBeNull();
  });
});
