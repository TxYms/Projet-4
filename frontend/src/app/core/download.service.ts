import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { DownloadInfo } from './models';

@Injectable({ providedIn: 'root' })
export class DownloadService {
  private readonly http = inject(HttpClient);

  info(token: string): Observable<DownloadInfo> {
    return this.http.get<DownloadInfo>(`/api/download/${encodeURIComponent(token)}`);
  }

  /** Vérifie le mot de passe et renvoie un ticket valable quelques minutes. */
  unlock(token: string, password: string): Observable<string> {
    return this.http
      .post<{ ticket: string }>(`/api/download/${encodeURIComponent(token)}/unlock`, { password })
      .pipe(map((res) => res.ticket));
  }

  /**
   * URL du contenu. Le navigateur télécharge directement le flux : un fichier de 1 Go n'est jamais
   * chargé en mémoire dans la page.
   */
  contentUrl(token: string, ticket?: string): string {
    const base = `/api/download/${encodeURIComponent(token)}/content`;
    return ticket ? `${base}?ticket=${encodeURIComponent(ticket)}` : base;
  }
}
