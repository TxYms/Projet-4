/** Contrats de l'API DataShare (voir docs/openapi.json). */

export interface User {
  id: string;
  email: string;
  createdAt: string;
}

export interface AuthResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  user: User;
}

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

export type FileStatusFilter = 'ALL' | 'ACTIVE' | 'EXPIRED';

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
