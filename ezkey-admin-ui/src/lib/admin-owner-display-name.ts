import type { AdminResponseDto } from '@/generated/admin-api/model';

/** Name shown for the owning administrator: full name, else username, else null. */
export function adminOwnerDisplayName(
  admin: Pick<AdminResponseDto, 'firstName' | 'lastName' | 'username'> | null | undefined,
): string | null {
  if (admin == null) return null;
  const fullName = [admin.firstName, admin.lastName]
    .map((part) => part?.trim())
    .filter(Boolean)
    .join(' ');
  if (fullName !== '') return fullName;
  const username = admin.username?.trim();
  return username != null && username !== '' ? username : null;
}
