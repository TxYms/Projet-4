import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../core/auth.service';

/**
 * Cadre commun des pages publiques : fond dégradé, logo, bouton de compte en haut à droite,
 * mention de copyright en bas. Le contenu de la page est projeté au centre.
 */
@Component({
  selector: 'app-gradient-shell',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  styleUrl: './gradient-shell.scss',
  template: `
    <header class="shell__header">
      <a routerLink="/" class="shell__logo">DataShare</a>
      @if (auth.isLoggedIn()) {
        <a routerLink="/mon-espace" class="btn btn--dark btn--sm">Mon espace</a>
      } @else {
        <a routerLink="/connexion" class="btn btn--dark btn--sm">Se connecter</a>
      }
    </header>
    <main class="shell__main" id="contenu">
      <ng-content />
    </main>
    <footer class="shell__footer">Copyright DataShare® 2025</footer>
  `,
})
export class GradientShell {
  protected readonly auth = inject(AuthService);
}
