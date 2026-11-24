import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

/**
 * Composant racine : affiche la page de la route courante.
 * Le lien « Aller au contenu » permet aux utilisateurs au clavier de sauter directement au contenu principal.
 */
@Component({
  selector: 'app-root',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet],
  template: `
    <a class="skip-link" href="#contenu">Aller au contenu</a>
    <router-outlet />
  `,
})
export class App {}
