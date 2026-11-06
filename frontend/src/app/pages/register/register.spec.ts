import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { Register } from './register';

describe('Register', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      imports: [Register],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  function fill(el: HTMLElement, email: string, password: string, confirm: string): void {
    for (const [sel, value] of [['#register-email', email], ['#register-password', password], ['#register-confirm', confirm]]) {
      const input = el.querySelector<HTMLInputElement>(sel)!;
      input.value = value;
      input.dispatchEvent(new Event('input'));
    }
    el.querySelector<HTMLButtonElement>('button[type=submit]')!.click();
  }

  it('vérifie que les deux mots de passe sont identiques', () => {
    const fixture = TestBed.createComponent(Register);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    fill(el, 'bob@example.com', 'motdepasse', 'autrechose');
    fixture.detectChanges();
    expect(el.textContent).toContain('Les deux mots de passe ne sont pas identiques.');
    http.expectNone('/api/auth/register');
  });

  it('crée le compte puis connecte automatiquement', () => {
    const fixture = TestBed.createComponent(Register);
    fixture.detectChanges();
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    fill(fixture.nativeElement, 'bob@example.com', 'motdepasse', 'motdepasse');
    http.expectOne('/api/auth/register').flush({ id: '1', email: 'bob@example.com', createdAt: '' });
    http.expectOne('/api/auth/login').flush({
      accessToken: 't', tokenType: 'Bearer', expiresIn: 3600, user: { id: '1', email: 'bob@example.com', createdAt: '' },
    });
    expect(navigate).toHaveBeenCalledWith('/mon-espace');
  });

  it('signale un e-mail déjà utilisé', () => {
    const fixture = TestBed.createComponent(Register);
    fixture.detectChanges();
    fill(fixture.nativeElement, 'bob@example.com', 'motdepasse', 'motdepasse');
    http.expectOne('/api/auth/register').flush(
      { detail: 'Un compte existe déjà avec cet e-mail.', code: 'EMAIL_ALREADY_USED' },
      { status: 409, statusText: 'Conflict' });
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Un compte existe déjà');
  });
});
