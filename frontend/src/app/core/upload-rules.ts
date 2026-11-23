/**
 * Contrôles faits avant l'envoi, pour prévenir l'utilisateur tout de suite.
 * Le serveur refait ces contrôles : ils ne servent qu'au confort, pas à la sécurité.
 */
export const MAX_FILE_SIZE = 1024 * 1024 * 1024;
export const MIN_FILE_PASSWORD = 6;
export const FORBIDDEN_EXTENSIONS = ['exe', 'bat', 'cmd', 'com', 'msi', 'scr', 'ps1', 'vbs', 'jar', 'sh', 'dll'];

export function fileError(file: File): string | null {
  if (file.size === 0) {
    return 'Ce fichier est vide.';
  }
  if (file.size > MAX_FILE_SIZE) {
    return 'La taille des fichiers est limitée à 1 Go.';
  }
  const dot = file.name.lastIndexOf('.');
  const ext = dot < 0 ? '' : file.name.slice(dot + 1).toLowerCase();
  if (FORBIDDEN_EXTENSIONS.includes(ext)) {
    return `Les fichiers .${ext} ne sont pas autorisés.`;
  }
  return null;
}
