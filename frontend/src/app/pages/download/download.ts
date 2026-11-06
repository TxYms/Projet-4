import { ChangeDetectionStrategy, Component, OnInit, inject, input, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { DownloadService } from '../../core/download.service';
import { toProblem } from '../../core/api-error';
import { daysLeft, expirySentence, formatSize } from '../../core/format';
import { DownloadInfo } from '../../core/models';
import { GradientShell } from '../../layout/gradient-shell';
import { Icon } from '../../shared/icon';

type State =
  | { kind: 'loading' }
  | { kind: 'ready'; info: DownloadInfo }
  | { kind: 'unavailable'; message: string };

/** US02 : page publique ouverte par le destinataire du lien. */
@Component({
  selector: 'app-download',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, GradientShell, Icon],
  templateUrl: './download.html',
})
export class Download implements OnInit {
  private readonly downloads = inject(DownloadService);

  /** Lien unique, lu dans l'URL /d/:token */
  readonly token = input.required<string>();

  protected readonly formatSize = formatSize;
  protected readonly state = signal<State>({ kind: 'loading' });
  protected readonly pending = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly started = signal(false);
  protected readonly form = inject(NonNullableFormBuilder).group({
    password: ['', [Validators.required]],
  });

  ngOnInit(): void {
    this.downloads.info(this.token()).subscribe({
      next: (info) => this.state.set({ kind: 'ready', info }),
      error: (err: unknown) => {
        const problem = toProblem(err);
        const message = problem.status === 404
          ? "Ce lien de téléchargement n'existe pas. Vérifie qu'il a été copié en entier."
          : problem.detail;
        this.state.set({ kind: 'unavailable', message });
      },
    });
  }

  protected expiry(info: DownloadInfo): string {
    return expirySentence(info.expiresAt);
  }

  protected soon(info: DownloadInfo): boolean {
    return daysLeft(info.expiresAt) <= 1;
  }

  protected download(info: DownloadInfo): void {
    this.error.set(null);
    if (!info.passwordProtected) {
      this.start(this.downloads.contentUrl(this.token()));
      return;
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.pending.set(true);
    this.downloads.unlock(this.token(), this.form.getRawValue().password).subscribe({
      next: (ticket) => {
        this.pending.set(false);
        this.start(this.downloads.contentUrl(this.token(), ticket));
      },
      error: (err: unknown) => {
        this.pending.set(false);
        this.error.set(toProblem(err).detail);
      },
    });
  }

  /** Le navigateur gère lui-même le téléchargement (barre de progression, reprise, mémoire). */
  private start(url: string): void {
    const link = document.createElement('a');
    link.href = url;
    link.rel = 'noopener';
    document.body.appendChild(link);
    link.click();
    link.remove();
    this.started.set(true);
  }
}
