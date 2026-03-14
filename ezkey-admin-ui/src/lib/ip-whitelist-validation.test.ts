import { describe, expect, it } from 'vitest';
import {
  parseAndValidateIpWhitelist,
  parseIpWhitelistLines,
} from './ip-whitelist-validation';

describe('parseAndValidateIpWhitelist', () => {
  it('returns empty entries for empty or blank string', () => {
    expect(parseAndValidateIpWhitelist('')).toMatchObject({ success: true, entries: [] });
    expect(parseAndValidateIpWhitelist('   \n\n  ')).toMatchObject({
      success: true,
      entries: [],
    });
    expect(parseAndValidateIpWhitelist(undefined)).toMatchObject({
      success: true,
      entries: [],
    });
    expect(parseAndValidateIpWhitelist(null)).toMatchObject({
      success: true,
      entries: [],
    });
  });

  it('accepts valid IPv4 addresses', () => {
    expect(parseAndValidateIpWhitelist('192.168.1.1')).toMatchObject({
      success: true,
      entries: ['192.168.1.1'],
    });
    expect(parseAndValidateIpWhitelist('10.0.0.1')).toMatchObject({
      success: true,
      entries: ['10.0.0.1'],
    });
    expect(parseAndValidateIpWhitelist('0.0.0.0')).toMatchObject({
      success: true,
      entries: ['0.0.0.0'],
    });
    expect(parseAndValidateIpWhitelist('255.255.255.255')).toMatchObject({
      success: true,
      entries: ['255.255.255.255'],
    });
  });

  it('accepts valid IPv4 CIDR', () => {
    expect(parseAndValidateIpWhitelist('192.168.1.0/24')).toMatchObject({
      success: true,
      entries: ['192.168.1.0/24'],
    });
    expect(parseAndValidateIpWhitelist('10.0.0.0/8')).toMatchObject({
      success: true,
      entries: ['10.0.0.0/8'],
    });
    expect(parseAndValidateIpWhitelist('172.16.0.0/12')).toMatchObject({
      success: true,
      entries: ['172.16.0.0/12'],
    });
    expect(parseAndValidateIpWhitelist('192.168.1.100/32')).toMatchObject({
      success: true,
      entries: ['192.168.1.100/32'],
    });
  });

  it('accepts valid IPv6 addresses', () => {
    expect(parseAndValidateIpWhitelist('::1')).toMatchObject({
      success: true,
      entries: ['::1'],
    });
    expect(parseAndValidateIpWhitelist('2001:db8::1')).toMatchObject({
      success: true,
      entries: ['2001:db8::1'],
    });
  });

  it('accepts valid IPv6 CIDR', () => {
    expect(parseAndValidateIpWhitelist('2001:db8::/32')).toMatchObject({
      success: true,
      entries: ['2001:db8::/32'],
    });
  });

  it('trims and splits multiple lines', () => {
    const result = parseAndValidateIpWhitelist(
      '  192.168.1.0/24  \n  10.0.0.1  \n\n  172.16.0.0/12  ',
    );
    expect(result).toMatchObject({
      success: true,
      entries: ['192.168.1.0/24', '10.0.0.1', '172.16.0.0/12'],
    });
  });

  it('rejects invalid IP or CIDR and returns first error with line number', () => {
    const result = parseAndValidateIpWhitelist('192.168.1.0/24\ninvalid-ip\n10.0.0.1');
    expect(result.success).toBe(false);
    if (!result.success) {
      expect(result.lineNumber).toBe(2);
      expect(result.value).toBe('invalid-ip');
      expect(result.message).toContain('line 2');
      expect(result.message).toContain('invalid-ip');
    }
  });

  it('rejects invalid IPv4 octet', () => {
    const result = parseAndValidateIpWhitelist('192.168.1.256');
    expect(result.success).toBe(false);
    if (!result.success) {
      expect(result.lineNumber).toBe(1);
      expect(result.value).toBe('192.168.1.256');
    }
  });

  it('rejects invalid CIDR prefix length', () => {
    const result = parseAndValidateIpWhitelist('192.168.1.0/33');
    expect(result.success).toBe(false);
    if (!result.success) {
      expect(result.lineNumber).toBe(1);
    }
  });

  it('rejects hostnames', () => {
    const result = parseAndValidateIpWhitelist('example.com');
    expect(result.success).toBe(false);
  });

  it('rejects empty line content after trim', () => {
    const result = parseAndValidateIpWhitelist('192.168.1.1\n\n10.0.0.1');
    expect(result).toMatchObject({
      success: true,
      entries: ['192.168.1.1', '10.0.0.1'],
    });
  });
});

describe('parseIpWhitelistLines', () => {
  it('returns entries without validation', () => {
    expect(parseIpWhitelistLines('192.168.1.0/24\n10.0.0.1')).toEqual([
      '192.168.1.0/24',
      '10.0.0.1',
    ]);
  });

  it('returns empty array for empty input', () => {
    expect(parseIpWhitelistLines('')).toEqual([]);
    expect(parseIpWhitelistLines(undefined)).toEqual([]);
  });

  it('trims and filters blank lines', () => {
    expect(parseIpWhitelistLines('  a  \n\n  b  ')).toEqual(['a', 'b']);
  });

  it('returns all lines without validation (including invalid)', () => {
    expect(
      parseIpWhitelistLines('192.168.1.0/24\nnot-an-ip\n10.0.0.1'),
    ).toEqual(['192.168.1.0/24', 'not-an-ip', '10.0.0.1']);
  });
});
