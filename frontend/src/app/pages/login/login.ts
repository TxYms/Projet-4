import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { toProblem } from '../../core/api-error';
import { GradientShell } from '../../layout/gradient-shell';

/** US04 : connexion par e-mail et mot de passe. */
@Component({
  selector: 'app-login',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, RouterLink, GradientShell],
  templateUrl: './login.html',
})
export class Login {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  protected readonly form = inject(NonNullableFormBuilder).group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required]],
  });
  protected readonly submitted = signal(false);
  protected readonly pending = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly info = signal<string | null>(this.initialInfo());

  /**
   * Valide le formulaire puis appelle l'API. Après connexion, renvoie vers la page demandée au départ
   * (`returnUrl`) ; seules les adresses internes, commençant par « / », sont acceptées pour éviter
   * une redirection vers un site extérieur.
   */
  protected submit(): void {
    this.submitted.set(true);
    this.error.set(null);
    if (this.form.invalid) {
      return;
    }
    const { email, password } = this.form.getRawValue();
    this.pending.set(true);
    this.auth.login(email, password).subscribe({
      next: () => {
        const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl');
        void this.router.navigateByUrl(returnUrl?.startsWith('/') ? returnUrl : '/mon-espace');
      },
      error: (err: unknown) => {
        this.pending.set(false);
        this.error.set(toProblem(err).detail);
      },
    });
  }

  /** Une erreur ne s'affiche qu'après que l'utilisateur a quitté le champ ou tenté de valider. */
  protected invalid(name: 'email' | 'password'): boolean {
    const control = this.form.controls[name];
    return control.invalid && (control.touched || this.submitted());
  }

  /** Message d'information selon la provenance : session expirée ou compte tout juste créé. */
  private initialInfo(): string | null {
    const params = this.route.snapshot.queryParamMap;
    if (params.get('expired')) {
      return 'Ta session a expiré, reconnecte-toi pour continuer.';
    }
    if (params.get('registered')) {
      return 'Ton compte est créé. Tu peux te connecter.';
    }
    return null;
  }
}
