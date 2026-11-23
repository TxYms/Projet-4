import { Routes } from '@angular/router';
import { authGuard, guestGuard } from './core/auth.guard';

export const routes: Routes = [
  { path: '', title: 'DataShare', loadComponent: () => import('./pages/home/home').then((m) => m.Home) },
  {
    path: 'connexion',
    title: 'Connexion · DataShare',
    canActivate: [guestGuard],
    loadComponent: () => import('./pages/login/login').then((m) => m.Login),
  },
  {
    path: 'inscription',
    title: 'Créer un compte · DataShare',
    canActivate: [guestGuard],
    loadComponent: () => import('./pages/register/register').then((m) => m.Register),
  },
  {
    path: 'mon-espace',
    title: 'Mes fichiers · DataShare',
    canActivate: [authGuard],
    loadComponent: () => import('./pages/my-files/my-files').then((m) => m.MyFiles),
  },
  {
    path: 'd/:token',
    title: 'Télécharger un fichier · DataShare',
    loadComponent: () => import('./pages/download/download').then((m) => m.Download),
  },
  {
    path: '**',
    title: 'Page introuvable · DataShare',
    loadComponent: () => import('./pages/not-found/not-found').then((m) => m.NotFound),
  },
];
