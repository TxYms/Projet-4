import { HttpEventType } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, ElementRef, inject, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { AuthService } from '../../core/auth.service';
import { FilesService } from '../../core/files.service';
import { toProblem } from '../../core/api-error';
import { EXPIRATION_OPTIONS, durationLabel, formatSize } from '../../core/format';
import { StoredFile } from '../../core/models';
import { MIN_FILE_PASSWORD, fileError } from '../../core/upload-rules';
import { GradientShell } from '../../layout/gradient-shell';
import { Icon } from '../../shared/icon';

type Step = 'idle' | 'form' | 'uploading' | 'done';

/** US01 : téléverser un fichier et obtenir son lien de téléchargement. */
@Component({
  selector: 'app-home',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, GradientShell, Icon],
  templateUrl: './home.html',
  styleUrl: './home.scss',
})
export class Home {
  private readonly auth = inject(AuthService);
  private readonly files = inject(FilesService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly picker = viewChild.required<ElementRef<HTMLInputElement>>('picker');
  private upload?: Subscription;

  protected readonly options = EXPIRATION_OPTIONS;
  protected readonly minPassword = MIN_FILE_PASSWORD;
  protected readonly formatSize = formatSize;

  protected readonly step = signal<Step>('idle');
  protected readonly file = signal<File | null>(null);
  protected readonly fileProblem = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly progress = signal(0);
  protected readonly result = signal<StoredFile | null>(null);
  protected readonly chosenDays = signal(7);
  protected readonly copied = signal(false);
  protected readonly dragging = signal(false);

  protected readonly form = inject(NonNullableFormBuilder).group({
    password: ['', [Validators.minLength(MIN_FILE_PASSWORD)]],
    expirationDays: [7],
  });

  /** Le bouton rond ouvre le sélecteur ; un visiteur non connecté est d'abord envoyé vers la connexion. */
  protected choose(): void {
    if (!this.auth.token()) {
      void this.router.navigate(['/connexion'], { queryParams: { returnUrl: '/' } });
      return;
    }
    this.picker().nativeElement.click();
  }

  protected onPicked(event: Event): void {
    const input = event.target as HTMLInputElement;
    const picked = input.files?.[0];
    input.value = '';
    if (picked) {
      this.select(picked);
    }
  }

  protected onDrop(event: DragEvent): void {
    event.preventDefault();
    this.dragging.set(false);
    const dropped = event.dataTransfer?.files?.[0];
    if (!dropped) {
      return;
    }
    if (!this.auth.token()) {
      void this.router.navigate(['/connexion'], { queryParams: { returnUrl: '/' } });
      return;
    }
    this.select(dropped);
  }

  protected onDragOver(event: DragEvent): void {
    event.preventDefault();
    this.dragging.set(true);
  }

  protected change(): void {
    this.picker().nativeElement.click();
  }

  protected submit(): void {
    const file = this.file();
    if (!file || this.fileProblem() || this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const { password, expirationDays } = this.form.getRawValue();
    const days = Number(expirationDays);
    this.chosenDays.set(days);
    this.error.set(null);
    this.progress.set(0);
    this.step.set('uploading');

    this.upload = this.files.upload(file, days, password || undefined)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (event) => {
          if (event.type === HttpEventType.UploadProgress && event.total) {
            this.progress.set(Math.round((event.loaded / event.total) * 100));
          } else if (event.type === HttpEventType.Response && event.body) {
            this.result.set(event.body);
            this.step.set('done');
          }
        },
        error: (err: unknown) => {
          this.error.set(toProblem(err).detail);
          this.step.set('form');
        },
      });
  }

  protected cancel(): void {
    this.upload?.unsubscribe();
    this.step.set('form');
  }

  protected async copy(link: string): Promise<void> {
    try {
      await navigator.clipboard.writeText(link);
      this.copied.set(true);
      setTimeout(() => this.copied.set(false), 2500);
    } catch {
      this.error.set('La copie a échoué. Sélectionne le lien et copie-le manuellement.');
    }
  }

  protected restart(): void {
    this.reset();
    this.step.set('idle');
  }

  protected keptFor(): string {
    return durationLabel(this.chosenDays());
  }

  protected passwordInvalid(): boolean {
    const control = this.form.controls.password;
    return control.invalid && control.touched;
  }

  private select(file: File): void {
    this.reset();
    this.file.set(file);
    this.fileProblem.set(fileError(file));
    this.step.set('form');
  }

  private reset(): void {
    this.error.set(null);
    this.result.set(null);
    this.copied.set(false);
    this.form.reset({ password: '', expirationDays: 7 });
  }
}
