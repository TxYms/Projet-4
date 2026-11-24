import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';
import { AuthResponse, User } from './models';

/** Clé sous laquelle la session est gardée dans le sessionStorage du navigateur. */
const STORAGE_KEY = 'datashare.session';

/** Session en cours : jeton, date d'expiration (en millisecondes) et profil de l'utilisateur. */
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

  /** Profil de l'utilisateur connecté, ou `null`. Les composants qui le lisent se mettent à jour seuls. */
  readonly user = computed(() => this.session()?.user ?? null);
  readonly isLoggedIn = computed(() => this.session() !== null);

  /**
   * Jeton d'accès à envoyer à l'API, ou `null` si personne n'est connecté.
   * Une session dont la date d'expiration est passée est effacée au passage.
   */
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

  /** Crée un compte (US03). Ne connecte pas : c'est la page d'inscription qui enchaîne avec `login`. */
  register(email: string, password: string): Observable<User> {
    return this.http.post<User>('/api/auth/register', { email, password });
  }

  /**
   * Connecte l'utilisateur (US04) et enregistre la session en cas de succès.
   * La date d'expiration est calculée côté navigateur à partir de la durée renvoyée par l'API.
   */
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

  /** Déconnecte l'utilisateur et le ramène à l'accueil. */
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

/**
 * Relit la session au chargement de l'application (après un rafraîchissement de la page, par exemple).
 * Une session expirée ou illisible est ignorée.
 */
function readSession(): Session | null {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY);
    const session = raw ? (JSON.parse(raw) as Session) : null;
    return session && session.expiresAt > Date.now() ? session : null;
  } catch {
    return null;
  }
}
