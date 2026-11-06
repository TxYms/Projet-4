import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';
import { AuthResponse, User } from './models';

const STORAGE_KEY = 'datashare.session';

interface Session {
  token: string;
  expiresAt: number;
  user: User;
}

/**
 * Session de l'utilisateur. Le jeton JWT est gardé dans le sessionStorage : il disparaît à la fermeture
 * de l'onglet et n'est jamais envoyé automatiquement (pas de cookie), ce qui écarte les attaques CSRF.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly session = signal<Session | null>(readSession());

  readonly user = computed(() => this.session()?.user ?? null);
  readonly isLoggedIn = computed(() => this.session() !== null);

  token(): string | null {
    const current = this.session();
    if (!current) {
      return null;
    }
    if (Date.now() >= current.expiresAt) {
      this.clear();
      return null;
    }
    return current.token;
  }

  register(email: string, password: string): Observable<User> {
    return this.http.post<User>('/api/auth/register', { email, password });
  }

  login(email: string, password: string): Observable<AuthResponse> {
    return this.http.post<AuthResponse>('/api/auth/login', { email, password }).pipe(
      tap((res) => {
        const session: Session = {
          token: res.accessToken,
          expiresAt: Date.now() + res.expiresIn * 1000,
          user: res.user,
        };
        sessionStorage.setItem(STORAGE_KEY, JSON.stringify(session));
        this.session.set(session);
      }),
    );
  }

  logout(): void {
    this.clear();
    void this.router.navigateByUrl('/');
  }

  /** Appelé quand l'API répond 401 : la session n'est plus valable. */
  expire(returnUrl: string): void {
    this.clear();
    void this.router.navigate(['/connexion'], { queryParams: { returnUrl, expired: 1 } });
  }

  private clear(): void {
    sessionStorage.removeItem(STORAGE_KEY);
    this.session.set(null);
  }
}

function readSession(): Session | null {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY);
    const session = raw ? (JSON.parse(raw) as Session) : null;
    return session && session.expiresAt > Date.now() ? session : null;
  } catch {
    return null;
  }
}
