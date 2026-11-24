import { HttpClient, HttpEvent, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { FileStatusFilter, StoredFile } from './models';

/**
 * Appels de l'API pour l'espace personnel (US01, US05, US06).
 * Le jeton d'accès est ajouté automatiquement par `authInterceptor` : ce service n'a pas à s'en occuper.
 */
@Injectable({ providedIn: 'root' })
export class FilesService {
  private readonly http = inject(HttpClient);

  /**
   * Envoie le fichier en signalant la progression, utile pour les fichiers volumineux.
   *
   * @param file fichier choisi par l'utilisateur
   * @param expirationDays durée de validité du lien, de 1 à 7 jours
   * @param password mot de passe optionnel ; non envoyé s'il est vide
   * @returns les événements HTTP : progression de l'envoi, puis réponse avec le lien créé
   */
  upload(file: File, expirationDays: number, password?: string): Observable<HttpEvent<StoredFile>> {
    const form = new FormData();
    form.append('file', file, file.name);
    form.append('expirationDays', String(expirationDays));
    if (password) {
      form.append('password', password);
    }
    return this.http.post<StoredFile>('/api/files', form, { reportProgress: true, observe: 'events' });
  }

  /** Historique des fichiers, filtré selon l'onglet choisi (« Tous », « Actifs », « Expiré »). */
  list(status: FileStatusFilter): Observable<StoredFile[]> {
    return this.http.get<StoredFile[]>('/api/files', { params: new HttpParams().set('status', status) });
  }

  /** Supprime définitivement un fichier. L'identifiant est encodé pour ne jamais altérer l'URL. */
  delete(id: string): Observable<void> {
    return this.http.delete<void>(`/api/files/${encodeURIComponent(id)}`);
  }
}
