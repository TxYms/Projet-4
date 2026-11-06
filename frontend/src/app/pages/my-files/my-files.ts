import { ChangeDetectionStrategy, Component, ElementRef, OnInit, inject, signal, viewChild } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { FilesService } from '../../core/files.service';
import { toProblem } from '../../core/api-error';
import { expiryLabel, formatSize } from '../../core/format';
import { FileStatusFilter, StoredFile } from '../../core/models';
import { Icon } from '../../shared/icon';

/** US05 (historique) et US06 (suppression). */
@Component({
  selector: 'app-my-files',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Icon],
  templateUrl: './my-files.html',
  styleUrl: './my-files.scss',
})
export class MyFiles implements OnInit {
  protected readonly auth = inject(AuthService);
  private readonly filesService = inject(FilesService);
  private readonly confirmDialog = viewChild.required<ElementRef<HTMLDialogElement>>('confirm');

  protected readonly filters: ReadonlyArray<{ value: FileStatusFilter; label: string }> = [
    { value: 'ALL', label: 'Tous' },
    { value: 'ACTIVE', label: 'Actifs' },
    { value: 'EXPIRED', label: 'Expiré' },
  ];
  protected readonly formatSize = formatSize;
  protected readonly expiryLabel = expiryLabel;

  protected readonly filter = signal<FileStatusFilter>('ALL');
  protected readonly files = signal<StoredFile[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly message = signal<string | null>(null);
  protected readonly toDelete = signal<StoredFile | null>(null);
  protected readonly deleting = signal(false);
  protected readonly menuOpen = signal(false);

  ngOnInit(): void {
    this.load();
  }

  protected select(filter: FileStatusFilter): void {
    this.filter.set(filter);
    this.load();
  }

  protected askDelete(file: StoredFile): void {
    this.toDelete.set(file);
    this.confirmDialog().nativeElement.showModal();
  }

  protected closeDialog(): void {
    this.confirmDialog().nativeElement.close();
    this.toDelete.set(null);
  }

  protected confirmDelete(): void {
    const file = this.toDelete();
    if (!file) {
      return;
    }
    this.deleting.set(true);
    this.filesService.delete(file.id).subscribe({
      next: () => {
        this.deleting.set(false);
        this.files.update((list) => list.filter((f) => f.id !== file.id));
        this.message.set(`« ${file.originalName} » a été supprimé.`);
        this.closeDialog();
      },
      error: (err: unknown) => {
        this.deleting.set(false);
        this.error.set(toProblem(err).detail);
        this.closeDialog();
      },
    });
  }

  private load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.filesService.list(this.filter()).subscribe({
      next: (files) => {
        this.files.set(files);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.error.set(toProblem(err).detail);
        this.loading.set(false);
      },
    });
  }
}
