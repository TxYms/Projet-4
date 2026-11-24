/** Contrats de l'API DataShare (voir docs/openapi.json). */

/** Profil public d'un utilisateur. */
export interface User {
  id: string;
  email: string;
  createdAt: string;
}

/** Réponse de connexion : `expiresIn` est en secondes. */
export interface AuthResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  user: User;
}

/**
 * Fichier de l'historique. Les dates sont au format ISO 8601 (UTC).
 * `downloadUrl` est le lien complet à partager ; `expired` est calculé par l'API au moment de la requête.
 */
export interface StoredFile {
  id: string;
  originalName: string;
  contentType: string;
  sizeBytes: number;
  createdAt: string;
  expiresAt: string;
  expired: boolean;
  passwordProtected: boolean;
  downloadToken: string;
  downloadUrl: string;
}

/** Filtres de l'historique, alignés sur les onglets des maquettes. */
export type FileStatusFilter = 'ALL' | 'ACTIVE' | 'EXPIRED';

/** Métadonnées affichées au destinataire avant le téléchargement. */
export interface DownloadInfo {
  originalName: string;
  contentType: string;
  sizeBytes: number;
  expiresAt: string;
  passwordProtected: boolean;
}

/** Corps d'erreur renvoyé par l'API (Problem Details, RFC 9457). */
export interface ApiProblem {
  status: number;
  detail: string;
  code: string;
  errors?: Record<string, string>;
}
