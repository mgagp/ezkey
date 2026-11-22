// Demo Login — SQLite database (built-in node:sqlite): the application's users and sessions.

import { DatabaseSync } from 'node:sqlite';
import { mkdirSync } from 'node:fs';
import { dirname } from 'node:path';

export interface User {
  id: number;
  username: string;
  displayName: string | null;
  ezkeyUserIdentifier: string;
  active: boolean;
  createdAt: string;
  lastLoginAt: string | null;
}

export interface Session {
  userId: number;
  createdAt: string;
  expiresAt: string;
}

const SCHEMA = `
CREATE TABLE IF NOT EXISTS users (
  id INTEGER PRIMARY KEY,
  username TEXT NOT NULL UNIQUE COLLATE NOCASE,
  display_name TEXT,
  ezkey_user_identifier TEXT NOT NULL,
  active INTEGER NOT NULL DEFAULT 1,
  created_at TEXT NOT NULL,
  last_login_at TEXT
);
CREATE TABLE IF NOT EXISTS sessions (
  token_hash TEXT PRIMARY KEY,
  user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  created_at TEXT NOT NULL,
  expires_at TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_sessions_expires_at ON sessions(expires_at);
`;

interface UserRow {
  id: number;
  username: string;
  display_name: string | null;
  ezkey_user_identifier: string;
  active: number;
  created_at: string;
  last_login_at: string | null;
}

function toUser(row: UserRow): User {
  return {
    id: row.id,
    username: row.username,
    displayName: row.display_name,
    ezkeyUserIdentifier: row.ezkey_user_identifier,
    active: row.active === 1,
    createdAt: row.created_at,
    lastLoginAt: row.last_login_at,
  };
}

export class DemoLoginDb {
  private readonly db: DatabaseSync;

  constructor(path: string) {
    if (path !== ':memory:') mkdirSync(dirname(path), { recursive: true });
    this.db = new DatabaseSync(path);
    this.db.exec('PRAGMA journal_mode = WAL; PRAGMA foreign_keys = ON; PRAGMA busy_timeout = 5000;');
    this.db.exec(SCHEMA);
  }

  close(): void {
    this.db.close();
  }

  findUser(username: string): User | null {
    const row = this.db.prepare('SELECT * FROM users WHERE username = ?').get(username) as UserRow | undefined;
    return row ? toUser(row) : null;
  }

  findUserById(id: number): User | null {
    const row = this.db.prepare('SELECT * FROM users WHERE id = ?').get(id) as UserRow | undefined;
    return row ? toUser(row) : null;
  }

  listUsers(): User[] {
    return (this.db.prepare('SELECT * FROM users ORDER BY username').all() as unknown as UserRow[]).map(toUser);
  }

  addUser(username: string, displayName: string | null, ezkeyUserIdentifier: string): User {
    this.db
      .prepare('INSERT INTO users (username, display_name, ezkey_user_identifier, created_at) VALUES (?, ?, ?, ?)')
      .run(username, displayName, ezkeyUserIdentifier, new Date().toISOString());
    return this.findUser(username)!;
  }

  removeUser(username: string): boolean {
    return Number(this.db.prepare('DELETE FROM users WHERE username = ?').run(username).changes) > 0;
  }

  setUserActive(username: string, active: boolean): boolean {
    return Number(this.db.prepare('UPDATE users SET active = ? WHERE username = ?').run(active ? 1 : 0, username).changes) > 0;
  }

  recordLogin(userId: number): void {
    this.db.prepare('UPDATE users SET last_login_at = ? WHERE id = ?').run(new Date().toISOString(), userId);
  }

  createSession(tokenHash: string, userId: number, expiresAt: Date): Session {
    const createdAt = new Date().toISOString();
    this.db
      .prepare('INSERT INTO sessions (token_hash, user_id, created_at, expires_at) VALUES (?, ?, ?, ?)')
      .run(tokenHash, userId, createdAt, expiresAt.toISOString());
    return { userId, createdAt, expiresAt: expiresAt.toISOString() };
  }

  /** Returns the session when it exists and has not expired. */
  findSession(tokenHash: string): Session | null {
    const row = this.db
      .prepare('SELECT user_id, created_at, expires_at FROM sessions WHERE token_hash = ? AND expires_at > ?')
      .get(tokenHash, new Date().toISOString()) as { user_id: number; created_at: string; expires_at: string } | undefined;
    return row ? { userId: row.user_id, createdAt: row.created_at, expiresAt: row.expires_at } : null;
  }

  deleteSession(tokenHash: string): void {
    this.db.prepare('DELETE FROM sessions WHERE token_hash = ?').run(tokenHash);
  }

  deleteExpiredSessions(): number {
    return Number(this.db.prepare('DELETE FROM sessions WHERE expires_at <= ?').run(new Date().toISOString()).changes);
  }
}
