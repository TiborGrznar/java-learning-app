import type { UserRole } from '../api/types';

export const ROLE_LABELS: Record<UserRole, string> = {
  STUDENT: 'Študent',
  TEACHER: 'Učiteľ',
  ADMIN: 'Administrátor',
};