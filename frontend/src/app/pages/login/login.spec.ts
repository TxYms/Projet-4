import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { Login } from './login';

describe('Login', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      imports: [Login],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  function render() {
    const fixture = TestBed.createComponent(Login);
    fixture.detectChanges();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  function type(el: HTMLElement, selector: string, value: string): void {
    const input = el.querySelector<HTMLInputElement>(selector)!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
  }

  it('affiche les erreurs de saisie sans appeler l’API', () => {
    const { fixture, el } = render();
    el.querySelector<HTMLButtonElement>('button[type=submit]')!.click();
    fixture.detectChanges();
    expect(el.textContent).toContain('Saisis une adresse e-mail valide.');
    expect(el.textContent).toContain('Saisis ton mot de passe.');
    http.expectNone('/api/auth/login');
  });

  it('connecte l’utilisateur puis ouvre son espace', () => {
    const { fixture, el } = render();
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    type(el, '#login-email', 'alice@example.com');
    type(el, '#login-password', 'motdepasse');
    el.querySelector<HTMLButtonElement>('button[type=submit]')!.click();
    http.expectOne('/api/auth/login').flush({
      accessToken: 't', tokenType: 'Bearer', expiresIn: 3600,
      user: { id: '1', email: 'alice@example.com', createdAt: '' },
    });
    fixture.detectChanges();
    expect(navigate).toHaveBeenCalledWith('/mon-espace');
  });

  it('affiche le message de l’API en cas d’échec', () => {
    const { fixture, el } = render();
    type(el, '#login-email', 'alice@example.com');
    type(el, '#login-password', 'mauvais');
    el.querySelector<HTMLButtonElement>('button[type=submit]')!.click();
    http.expectOne('/api/auth/login').flush(
      { detail: 'E-mail ou mot de passe incorrect.', code: 'INVALID_CREDENTIALS' },
      { status: 401, statusText: 'Unauthorized' });
    fixture.detectChanges();
    expect(el.querySelector('[role=alert]')?.textContent).toContain('E-mail ou mot de passe incorrect.');
  });
});
