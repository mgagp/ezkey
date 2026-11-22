// Demo Login — administration from the command line (no admin screen).
//
//   user list
//   user add <username> [--name "Display Name"] [--ezkey-id <userIdentifier>]
//   user remove <username>
//   user disable <username> | user enable <username>
//
// In Docker: docker compose exec demo-login node dist/server/cli.js user list

import { loadConfig } from './config.ts';
import { DemoLoginDb } from './db.ts';

const USAGE = `Usage:
  user list
  user add <username> [--name "Display Name"] [--ezkey-id <userIdentifier>]
  user remove <username>
  user disable <username>
  user enable <username>

--ezkey-id is the User ID of the enrollment in Ezkey (defaults to the username).`;

function option(args: string[], name: string): string | null {
  const i = args.indexOf(name);
  if (i < 0) return null;
  const value = args[i + 1];
  if (!value || value.startsWith('--')) throw new Error(`${name} needs a value`);
  return value;
}

function main(argv: string[]): number {
  const [group, command, ...rest] = argv;
  if (group !== 'user' || !command) {
    console.error(USAGE);
    return 2;
  }
  const db = new DemoLoginDb(loadConfig().dbPath);
  try {
    switch (command) {
      case 'list': {
        const users = db.listUsers();
        if (users.length === 0) {
          console.log('No users.');
          return 0;
        }
        console.table(
          users.map((u) => ({
            username: u.username,
            name: u.displayName ?? '',
            'ezkey user id': u.ezkeyUserIdentifier,
            active: u.active,
            'last sign-in': u.lastLoginAt ?? '',
          })),
        );
        return 0;
      }
      case 'add': {
        const username = rest[0]?.trim();
        if (!username || username.startsWith('--')) throw new Error('username is required');
        if (username.length > 128) throw new Error('username is too long (128 max)');
        if (db.findUser(username)) throw new Error(`user "${username}" already exists`);
        const user = db.addUser(username, option(rest, '--name'), option(rest, '--ezkey-id') ?? username);
        console.log(`Added "${user.username}" (Ezkey user id "${user.ezkeyUserIdentifier}").`);
        return 0;
      }
      case 'remove':
      case 'disable':
      case 'enable': {
        const username = rest[0]?.trim();
        if (!username) throw new Error('username is required');
        const done =
          command === 'remove' ? db.removeUser(username) : db.setUserActive(username, command === 'enable');
        if (!done) throw new Error(`no user "${username}"`);
        console.log(`${command === 'remove' ? 'Removed' : command === 'enable' ? 'Enabled' : 'Disabled'} "${username}".`);
        return 0;
      }
      default:
        console.error(USAGE);
        return 2;
    }
  } catch (err) {
    console.error(`Error: ${(err as Error).message}`);
    return 1;
  } finally {
    db.close();
  }
}

process.exit(main(process.argv.slice(2)));
