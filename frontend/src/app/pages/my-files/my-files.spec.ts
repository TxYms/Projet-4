import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { MyFiles } from './my-files';
import { StoredFile } from '../../core/models';

function file(overrides: Partial<StoredFile>): StoredFile {
  return {
    id: '1', originalName: 'photo.jpg', contentType: 'image/jpeg', sizeBytes: 1024,
    createdAt: new Date().toISOString(), expiresAt: new Date(Date.now() + 3 * 86400000).toISOString(),
    expired: false, passwordProtected: false, downloadToken: 'tok', downloadUrl: 'http://localhost:4200/d/tok',
    ...overrides,
  };
}

describe('MyFiles', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    // jsdom n'implémente pas <dialog>
    HTMLDialogElement.prototype.showModal = vi.fn();
    HTMLDialogElement.prototype.close = vi.fn();
    TestBed.configureTestingModule({
      imports: [MyFiles],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  function render() {
    const fixture = TestBed.createComponent(MyFiles);
    fixture.detectChanges();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  it('liste les fichiers avec leur état', () => {
    const { fixture, el } = render();
    http.expectOne('/api/files?status=ALL').flush([
      file({ id: '1', originalName: 'actif.jpg', passwordProtected: true }),
      file({ id: '2', originalName: 'vieux.mp4', expired: true, expiresAt: '2026-01-01T00:00:00Z' }),
    ]);
    fixture.detectChanges();
    const rows = el.querySelectorAll('.file-row');
    expect(rows.length).toBe(2);
    expect(rows[0].textContent).toContain('Protégé par mot de passe');
    expect(rows[0].querySelector('a')?.getAttribute('href')).toBe('http://localhost:4200/d/tok');
    expect(rows[1].textContent).toContain("il n'est plus stocké chez nous");
    expect(rows[1].querySelector('button')).toBeNull();
  });

  it('filtre par statut', () => {
    const { fixture, el } = render();
    http.expectOne('/api/files?status=ALL').flush([]);
    fixture.detectChanges();
    expect(el.textContent).toContain("Tu n'as encore envoyé aucun fichier.");
    const tabs = el.querySelectorAll<HTMLButtonElement>('.tabs__tab');
    tabs[1].click();
    http.expectOne('/api/files?status=ACTIVE').flush([]);
    fixture.detectChanges();
    expect(tabs[1].getAttribute('aria-pressed')).toBe('true');
    expect(el.textContent).toContain('Aucun fichier en cours de partage.');
    tabs[2].click();
    http.expectOne('/api/files?status=EXPIRED').flush([]);
    fixture.detectChanges();
    expect(el.textContent).toContain('Aucun fichier expiré.');
  });

  it('supprime un fichier après confirmation', () => {
    const { fixture, el } = render();
    http.expectOne('/api/files?status=ALL').flush([file({ id: '42', originalName: 'a-supprimer.txt' })]);
    fixture.detectChanges();
    el.querySelector<HTMLButtonElement>('.file-row button')!.click();
    fixture.detectChanges();
    expect(el.querySelector('dialog')?.textContent).toContain('a-supprimer.txt');
    el.querySelector<HTMLButtonElement>('.btn--danger')!.click();
    http.expectOne({ method: 'DELETE', url: '/api/files/42' }).flush(null, { status: 204, statusText: 'No Content' });
    fixture.detectChanges();
    expect(el.querySelectorAll('.file-row').length).toBe(0);
    expect(el.textContent).toContain('« a-supprimer.txt » a été supprimé.');
  });

  it('affiche une erreur si le chargement échoue', () => {
    const { fixture, el } = render();
    http.expectOne('/api/files?status=ALL').flush({}, { status: 500, statusText: 'Error' });
    fixture.detectChanges();
    expect(el.querySelector('[role=alert]')).not.toBeNull();
  });
});
