/** Fonctions d'affichage pures, testées dans format.spec.ts. */

const UNITS = ['octets', 'Ko', 'Mo', 'Go'];
const DAY_MS = 24 * 60 * 60 * 1000;

export function formatSize(bytes: number): string {
  if (bytes < 1024) {
    return `${bytes} ${bytes > 1 ? 'octets' : 'octet'}`;
  }
  let value = bytes;
  let unit = 0;
  while (value >= 1024 && unit < UNITS.length - 1) {
    value /= 1024;
    unit++;
  }
  const rounded = value >= 10 ? Math.round(value).toString() : value.toFixed(1).replace('.', ',');
  return `${rounded.replace(/,0$/, '')} ${UNITS[unit]}`;
}

/** Nombre de jours calendaires entre maintenant et l'expiration (0 = aujourd'hui). */
export function daysLeft(expiresAt: string, now: Date = new Date()): number {
  const end = new Date(expiresAt);
  const startOfToday = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime();
  const startOfEnd = new Date(end.getFullYear(), end.getMonth(), end.getDate()).getTime();
  return Math.round((startOfEnd - startOfToday) / DAY_MS);
}

/** « Expire dans 2 jours », « Expire demain », « Expire aujourd'hui », « Expiré ». */
export function expiryLabel(expiresAt: string, now: Date = new Date()): string {
  if (new Date(expiresAt).getTime() <= now.getTime()) {
    return 'Expiré';
  }
  const days = daysLeft(expiresAt, now);
  if (days <= 0) {
    return "Expire aujourd'hui";
  }
  if (days === 1) {
    return 'Expire demain';
  }
  return `Expire dans ${days} jours`;
}

/** Phrase de la page de téléchargement : « Ce fichier expirera dans 3 jours ». */
export function expirySentence(expiresAt: string, now: Date = new Date()): string {
  const days = daysLeft(expiresAt, now);
  if (days <= 0) {
    return "Ce fichier expirera aujourd'hui.";
  }
  if (days === 1) {
    return 'Ce fichier expirera demain.';
  }
  return `Ce fichier expirera dans ${days} jours.`;
}

export const EXPIRATION_OPTIONS: ReadonlyArray<{ days: number; label: string }> = [
  { days: 1, label: 'Une journée' },
  { days: 2, label: 'Deux jours' },
  { days: 3, label: 'Trois jours' },
  { days: 4, label: 'Quatre jours' },
  { days: 5, label: 'Cinq jours' },
  { days: 6, label: 'Six jours' },
  { days: 7, label: 'Une semaine' },
];

export function durationLabel(days: number): string {
  return EXPIRATION_OPTIONS.find((o) => o.days === days)?.label.toLowerCase() ?? `${days} jours`;
}
