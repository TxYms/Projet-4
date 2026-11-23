import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Download } from './download';

const inThreeDays = new Date(Date.now() + 3 * 24 * 3600 * 1000).toISOString();

describe('Download', () => {
  let http: HttpTestingController;
  let clicked: string[];

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [Download],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    clicked = [];
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
      clicked.push(this.getAttribute('href') ?? '');
    });
  });

  afterEach(() => vi.restoreAllMocks());

  function render(token = 'abc') {
    const fixture = TestBed.createComponent(Download);
    fixture.componentRef.setInput('token', token);
    fixture.detectChanges();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  it('affiche les métadonnées puis télécharge un fichier non protégé', () => {
    const { fixture, el } = render();
    http.expectOne('/api/download/abc').flush({
      originalName: 'rapport.pdf', contentType: 'application/pdf', sizeBytes: 2048,
      expiresAt: inThreeDays, passwordProtected: false,
    });
    fixture.detectChanges();
    expect(el.textContent).toContain('rapport.pdf');
    expect(el.textContent).toContain('2 Ko');
    expect(el.textContent).toContain('Ce fichier expirera dans 3 jours.');
    expect(el.querySelector('#download-password')).toBeNull();

    el.querySelector<HTMLButtonElement>('button[type=submit]')!.click();
    fixture.detectChanges();
    expect(clicked).toEqual(['/api/download/abc/content']);
    expect(el.textContent).toContain('Le téléchargement a démarré');
  });

  it('demande le mot de passe d’un fichier protégé et utilise le ticket', () => {
    const { fixture, el } = render();
    http.expectOne('/api/download/abc').flush({
      originalName: 'secret.zip', contentType: 'application/zip', sizeBytes: 10,
      expiresAt: inThreeDays, passwordProtected: true,
    });
    fixture.detectChanges();
    const input = el.querySelector<HTMLInputElement>('#download-password')!;
    input.value = 'sesame';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    el.querySelector<HTMLButtonElement>('button[type=submit]')!.click();

    const req = http.expectOne('/api/download/abc/unlock');
    expect(req.request.body).toEqual({ password: 'sesame' });
    req.flush({ ticket: 'tk.1' });
    expect(clicked).toEqual(['/api/download/abc/content?ticket=tk.1']);
  });

  it('affiche l’erreur d’un mauvais mot de passe', () => {
    const { fixture, el } = render();
    http.expectOne('/api/download/abc').flush({
      originalName: 's.zip', contentType: 'x', sizeBytes: 1, expiresAt: inThreeDays, passwordProtected: true,
    });
    fixture.detectChanges();
    const input = el.querySelector<HTMLInputElement>('#download-password')!;
    input.value = 'faux';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    el.querySelector<HTMLButtonElement>('button[type=submit]')!.click();
    http.expectOne('/api/download/abc/unlock').flush(
      { detail: 'Mot de passe incorrect.', code: 'WRONG_PASSWORD' }, { status: 401, statusText: 'Unauthorized' });
    fixture.detectChanges();
    expect(el.querySelector('[role=alert]')?.textContent).toContain('Mot de passe incorrect.');
    expect(clicked).toEqual([]);
  });

  it('explique qu’un lien a expiré', () => {
    const { fixture, el } = render();
    http.expectOne('/api/download/abc').flush(
      { detail: "Ce fichier n'est plus disponible en téléchargement car il a expiré.", code: 'FILE_EXPIRED' },
      { status: 410, statusText: 'Gone' });
    fixture.detectChanges();
    expect(el.querySelector('[role=alert]')?.textContent).toContain('car il a expiré');
  });

  it('explique qu’un lien est inconnu', () => {
    const { fixture, el } = render('zzz');
    http.expectOne('/api/download/zzz').flush({ detail: 'x', code: 'NOT_FOUND' }, { status: 404, statusText: 'Not Found' });
    fixture.detectChanges();
    expect(el.textContent).toContain("Ce lien de téléchargement n'existe pas.");
  });
});
