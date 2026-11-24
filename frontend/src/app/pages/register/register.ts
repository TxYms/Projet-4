import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import {
  AbstractControl,
  NonNullableFormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { toProblem } from '../../core/api-error';
import { GradientShell } from '../../layout/gradient-shell';

/** Longueur minimale du mot de passe d'un compte, identique à celle exigée par l'API (US03). */
export const MIN_ACCOUNT_PASSWORD = 8;

/** Validateur de groupe : signale une erreur `mismatch` si les deux mots de passe saisis diffèrent. */
function passwordsMatch(group: AbstractControl): ValidationErrors | null {
  const password = group.get('password')?.value as string;
  const confirm = group.get('confirm')?.value as string;
  return confirm && password !== confirm ? { mismatch: true } : null;
}

/** US03 : création de compte. */
@Component({
  selector: 'app-register',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, RouterLink, GradientShell],
  templateUrl: './register.html',
})
export class Register {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly minLength = MIN_ACCOUNT_PASSWORD;
  protected readonly form = inject(NonNullableFormBuilder).group(
    {
      email: ['', [Validators.required, Validators.email]],
      password: ['', [Validators.required, Validators.minLength(MIN_ACCOUNT_PASSWORD)]],
      confirm: ['', [Validators.required]],
    },
    { validators: passwordsMatch },
  );
  protected readonly submitted = signal(false);
  protected readonly pending = signal(false);
  protected readonly error = signal<string | null>(null);

  /** Crée le compte puis connecte automatiquement l'utilisateur, pour lui éviter une saisie de plus. */
  protected submit(): void {
    this.submitted.set(true);
    this.error.set(null);
    if (this.form.invalid) {
      return;
    }
    const { email, password } = this.form.getRawValue();
    this.pending.set(true);
    this.auth.register(email, password).subscribe({
      next: () => {
        // Connexion automatique juste après l'inscription
        this.auth.login(email, password).subscribe({
          next: () => void this.router.navigateByUrl('/mon-espace'),
          error: () => void this.router.navigate(['/connexion'], { queryParams: { registered: 1 } }),
        });
      },
      error: (err: unknown) => {
        this.pending.set(false);
        this.error.set(toProblem(err).detail);
      },
    });
  }

  protected show(name: 'email' | 'password' | 'confirm'): boolean {
    const control = this.form.controls[name];
    return control.touched || this.submitted();
  }

  protected mismatch(): boolean {
    return this.form.hasError('mismatch') && this.show('confirm');
  }
}
