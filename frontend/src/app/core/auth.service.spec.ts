import { provideHttpClient, withInterceptors, HttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { AuthService } from './auth.service';
import { authInterceptor } from './auth.interceptor';
import { authGuard, guestGuard } from './auth.guard';
import { ActivatedRouteSnapshot, RouterStateSnapshot, UrlTree } from '@angular/router';

const loginResponse = {
  accessToken: 'jwt-123',
  tokenType: 'Bearer',
  expiresIn: 3600,
  user: { id: 'u1', email: 'alice@example.com', createdAt: '2026-11-06T10:00:00Z' },
};

describe('AuthService, intercepteur et gardes', () => {
  let auth: AuthService;
  let http: HttpTestingController;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    auth = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function login(): void {
    auth.login('alice@example.com', 'motdepasse').subscribe();
    http.expectOne('/api/auth/login').flush(loginResponse);
  }

  it('enregistre la session après connexion', () => {
    expect(auth.isLoggedIn()).toBe(false);
    login();
    expect(auth.isLoggedIn()).toBe(true);
    expect(auth.user()?.email).toBe('alice@example.com');
    expect(auth.token()).toBe('jwt-123');
    expect(sessionStorage.getItem('datashare.session')).toContain('jwt-123');
  });

  it('ajoute le jeton aux routes protégées mais pas aux routes publiques', () => {
    login();
    const client = TestBed.inject(HttpClient);
    client.get('/api/files').subscribe();
    expect(http.expectOne('/api/files').request.headers.get('Authorization')).toBe('Bearer jwt-123');
    client.get('/api/download/abc').subscribe();
    expect(http.expectOne('/api/download/abc').request.headers.has('Authorization')).toBe(false);
  });

  it('déconnecte et renvoie vers la connexion quand l’API répond 401', () => {
    login();
    const router = TestBed.inject(Router);
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    TestBed.inject(HttpClient).get('/api/files').subscribe({ error: () => undefined });
    http.expectOne('/api/files').flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(auth.isLoggedIn()).toBe(false);
    expect(navigate).toHaveBeenCalledWith(['/connexion'], expect.objectContaining({ queryParams: expect.objectContaining({ expired: 1 }) }));
  });

  it('considère une session expirée comme absente', () => {
    login();
    vi.spyOn(Date, 'now').mockReturnValue(Date.now() + 2 * 3600 * 1000);
    expect(auth.token()).toBeNull();
    expect(auth.isLoggedIn()).toBe(false);
    vi.restoreAllMocks();
  });

  it('logout vide la session', () => {
    login();
    vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    auth.logout();
    expect(sessionStorage.getItem('datashare.session')).toBeNull();
  });

  it('les gardes protègent mon espace et la page de connexion', () => {
    const run = (guard: typeof authGuard) => TestBed.runInInjectionContext(
      () => guard({} as ActivatedRouteSnapshot, { url: '/mon-espace' } as RouterStateSnapshot));
    expect(run(authGuard)).toBeInstanceOf(UrlTree);
    expect(run(guestGuard)).toBe(true);
    login();
    expect(run(authGuard)).toBe(true);
    expect(run(guestGuard)).toBeInstanceOf(UrlTree);
  });
});
