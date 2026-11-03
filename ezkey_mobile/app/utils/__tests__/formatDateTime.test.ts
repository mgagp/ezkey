import {formatDateTime, localeForFormatting, setFormattingDeviceLocales} from '../formatDateTime';

const SAMPLE = new Date('2026-11-03T20:34:00Z');

describe('localeForFormatting', () => {
  it('keeps the phone regional variant when its language matches the app', () => {
    expect(localeForFormatting('fr', ['fr-CA', 'en-US'])).toBe('fr-CA');
    expect(localeForFormatting('en', ['en-GB'])).toBe('en-GB');
  });

  it('ignores a phone language that the app is not showing', () => {
    expect(localeForFormatting('en', ['fr-CA'])).toBe('en');
    expect(localeForFormatting('fr', ['en-US', 'es-ES'])).toBe('fr');
  });
});

describe('formatDateTime', () => {
  afterEach(() => {
    setFormattingDeviceLocales([]);
  });

  it('uses the generic app language when the phone speaks another language', () => {
    setFormattingDeviceLocales(['en-US']);

    expect(formatDateTime(SAMPLE, 'fr')).toBe(
      SAMPLE.toLocaleString('fr', {dateStyle: 'medium', timeStyle: 'short'}),
    );
  });

  it('keeps French (Canada) hour spacing on a fr-CA phone shown in French', () => {
    setFormattingDeviceLocales(['fr-CA']);

    const formatted = formatDateTime(SAMPLE, 'fr');
    expect(formatted).toBe(
      SAMPLE.toLocaleString('fr-CA', {dateStyle: 'medium', timeStyle: 'short'}),
    );
    expect(formatted).not.toBe(
      SAMPLE.toLocaleString('fr', {dateStyle: 'medium', timeStyle: 'short'}),
    );
    expect(formatted).toContain('h');
  });

  it('returns undefined for a missing or invalid value', () => {
    expect(formatDateTime(undefined, 'en')).toBeUndefined();
    expect(formatDateTime('not-a-date', 'en')).toBeUndefined();
  });
});
