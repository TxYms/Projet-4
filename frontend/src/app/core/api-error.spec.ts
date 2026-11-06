import { HttpErrorResponse } from '@angular/common/http';
import { toProblem } from './api-error';

describe('toProblem', () => {
  it('reprend le message renvoyé par l’API', () => {
    const err = new HttpErrorResponse({
      status: 410,
      error: { detail: 'Ce fichier a expiré.', code: 'FILE_EXPIRED' },
    });
    expect(toProblem(err)).toEqual({ status: 410, code: 'FILE_EXPIRED', detail: 'Ce fichier a expiré.', errors: undefined });
  });

  it('explique une coupure réseau', () => {
    expect(toProblem(new HttpErrorResponse({ status: 0 })).code).toBe('NETWORK');
  });

  it('gère un 413 renvoyé par un proxy sans corps JSON', () => {
    expect(toProblem(new HttpErrorResponse({ status: 413, error: 'Too large' })).code).toBe('FILE_TOO_LARGE');
  });

  it('a un message par défaut', () => {
    expect(toProblem(new Error('x')).detail).toContain('Une erreur est survenue');
    expect(toProblem(new HttpErrorResponse({ status: 500 })).status).toBe(500);
  });
});
