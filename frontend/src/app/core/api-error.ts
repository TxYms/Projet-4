import { HttpErrorResponse } from '@angular/common/http';
import { ApiProblem } from './models';

/**
 * Transforme une erreur HTTP en message affichable.
 * Le serveur renvoie déjà des messages en français ; on prévoit un repli si le serveur est injoignable.
 */
export function toProblem(error: unknown): ApiProblem {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 0) {
      return { status: 0, code: 'NETWORK', detail: 'Le serveur est injoignable. Vérifie ta connexion puis réessaie.' };
    }
    const body = error.error as Partial<ApiProblem> | null;
    if (body && typeof body === 'object' && body.detail) {
      return { status: error.status, code: body.code ?? 'ERROR', detail: body.detail, errors: body.errors };
    }
    if (error.status === 413) {
      return { status: 413, code: 'FILE_TOO_LARGE', detail: 'Le fichier dépasse la taille maximale de 1 Go.' };
    }
    return { status: error.status, code: 'ERROR', detail: 'Une erreur est survenue. Réessaie dans un instant.' };
  }
  return { status: -1, code: 'ERROR', detail: 'Une erreur est survenue. Réessaie dans un instant.' };
}
