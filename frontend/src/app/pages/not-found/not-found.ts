import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { GradientShell } from '../../layout/gradient-shell';

@Component({
  selector: 'app-not-found',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, GradientShell],
  template: `
    <app-gradient-shell>
      <section class="card" aria-labelledby="nf-title">
        <h1 id="nf-title" class="card__title">Page introuvable</h1>
        <p>Cette adresse ne correspond à aucune page de DataShare.</p>
        <a routerLink="/" class="btn btn--soft btn--block">Revenir à l'accueil</a>
      </section>
    </app-gradient-shell>
  `,
})
export class NotFound {}
