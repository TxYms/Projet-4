import { provideHttpClient } from '@angular/common/http';
import { HttpEventType } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { Home } from './home';
import { AuthService } from '../../core/auth.service';

describe('Home (téléversement)', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [Home],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  function render(loggedIn: boolean) {
    vi.spyOn(TestBed.inject(AuthService), 'token').mockReturnValue(loggedIn ? 'jwt' : null);
    const fixture = TestBed.createComponent(Home);
    fixture.detectChanges();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  function pick(el: HTMLElement, f: File): void {
    const input = el.querySelector<HTMLInputElement>('input[type=file]')!;
    Object.defineProperty(input, 'files', { value: [f], configurable: true });
    input.dispatchEvent(new Event('change'));
  }

  it('envoie un visiteur non connecté vers la connexion', () => {
    const { el } = render(false);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    el.querySelector<HTMLButtonElement>('.hero__button')!.click();
    expect(navigate).toHaveBeenCalledWith(['/connexion'], { queryParams: { returnUrl: '/' } });
  });

  it('refuse un type de fichier interdit avant l’envoi', () => {
    const { fixture, el } = render(true);
    pick(el, new File(['x'], 'setup.exe'));
    fixture.detectChanges();
    expect(el.textContent).toContain('Les fichiers .exe ne sont pas autorisés.');
    expect(el.querySelector<HTMLButtonElement>('button[type=submit]')!.disabled).toBe(true);
  });

  it('téléverse, suit la progression puis affiche le lien', () => {
    const { fixture, el } = render(true);
    pick(el, new File(['bonjour'], 'rapport.txt'));
    fixture.detectChanges();
    expect(el.textContent).toContain('rapport.txt');

    const select = el.querySelector<HTMLSelectElement>('#upload-expiration')!;
    select.selectedIndex = 2; // Trois jours
    select.dispatchEvent(new Event('change'));
    el.querySelector<HTMLButtonElement>('button[type=submit]')!.click();
    fixture.detectChanges();

    const req = http.expectOne('/api/files');
    const body = req.request.body as FormData;
    expect(body.get('expirationDays')).toBe('3');
    expect(body.get('password')).toBeNull();
    req.event({ type: HttpEventType.UploadProgress, loaded: 50, total: 100 });
    fixture.detectChanges();
    expect(el.textContent).toContain('50 %');

    req.flush({
      id: '1', originalName: 'rapport.txt', contentType: 'text/plain', sizeBytes: 7,
      createdAt: '', expiresAt: '', expired: false, passwordProtected: false,
      downloadToken: 'tok', downloadUrl: 'http://localhost:4200/d/tok',
    });
    fixture.detectChanges();
    expect(el.textContent).toContain('pendant trois jours');
    expect(el.querySelector('.link-box')?.textContent).toContain('/d/tok');
  });

  it('affiche l’erreur renvoyée par le serveur', () => {
    const { fixture, el } = render(true);
    pick(el, new File(['x'], 'a.txt'));
    fixture.detectChanges();
    el.querySelector<HTMLButtonElement>('button[type=submit]')!.click();
    http.expectOne('/api/files').flush(
      { detail: 'Le fichier dépasse la taille maximale de 1 Go.', code: 'FILE_TOO_LARGE' },
      { status: 413, statusText: 'Too Large' });
    fixture.detectChanges();
    expect(el.querySelector('[role=alert]')?.textContent).toContain('1 Go');
  });
});
