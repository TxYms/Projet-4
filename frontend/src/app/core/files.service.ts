import { HttpClient, HttpEvent, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { FileStatusFilter, StoredFile } from './models';

@Injectable({ providedIn: 'root' })
export class FilesService {
  private readonly http = inject(HttpClient);

  /** Envoie le fichier en signalant la progression, utile pour les fichiers volumineux. */
  upload(file: File, expirationDays: number, password?: string): Observable<HttpEvent<StoredFile>> {
    const form = new FormData();
    form.append('file', file, file.name);
    form.append('expirationDays', String(expirationDays));
    if (password) {
      form.append('password', password);
    }
    return this.http.post<StoredFile>('/api/files', form, { reportProgress: true, observe: 'events' });
  }

  list(status: FileStatusFilter): Observable<StoredFile[]> {
    return this.http.get<StoredFile[]>('/api/files', { params: new HttpParams().set('status', status) });
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`/api/files/${encodeURIComponent(id)}`);
  }
}
