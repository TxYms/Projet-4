import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthService } from './auth.service';

/** Ajoute le jeton aux appels de l'API et renvoie vers la connexion si la session a expiré. */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const token = auth.token();
  const isApi = req.url.startsWith('/api/');
  const isPublic = req.url.startsWith('/api/auth/login') || req.url.startsWith('/api/auth/register')
    || req.url.startsWith('/api/download/');

  const request = token && isApi && !isPublic
    ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
    : req;

  return next(request).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse && error.status === 401 && isApi && !isPublic) {
        auth.expire(router.url);
      }
      return throwError(() => error);
    }),
  );
};
