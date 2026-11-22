// Demo Login — one log line per event on stdout. Never pass secrets, cookies or challenge codes.

type Level = 'INFO' | 'WARN' | 'ERROR';
type Fields = Record<string, string | number | boolean | null | undefined>;

function write(level: Level, event: string, fields: Fields): void {
  const parts = Object.entries(fields)
    .filter(([, v]) => v !== undefined)
    .map(([k, v]) => `${k}=${typeof v === 'string' && /\s/.test(v) ? JSON.stringify(v) : String(v)}`);
  const line = `${new Date().toISOString()} ${level} ${event}${parts.length ? ' ' + parts.join(' ') : ''}`;
  if (level === 'INFO') console.log(line);
  else console.error(line);
}

export const log = {
  info: (event: string, fields: Fields = {}) => write('INFO', event, fields),
  warn: (event: string, fields: Fields = {}) => write('WARN', event, fields),
  error: (event: string, fields: Fields = {}) => write('ERROR', event, fields),
};
