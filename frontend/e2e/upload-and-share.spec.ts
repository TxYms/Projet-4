import { expect, test } from '@playwright/test';
import { register, uniqueEmail, uploadFile } from './helpers';

test('un nouvel utilisateur crée son compte, téléverse un fichier et récupère son lien', async ({ page }) => {
  await register(page, uniqueEmail('partage'));
  await expect(page.getByRole('heading', { name: 'Mes fichiers' })).toBeVisible();
  await expect(page.getByText("Tu n'as encore envoyé aucun fichier.")).toBeVisible();

  const link = await uploadFile(page, 'compte-rendu.txt', { expiration: 'Trois jours' });
  expect(link).toMatch(/\/d\/[A-Za-z0-9_-]{22}$/);
  await expect(page.getByText('pendant trois jours')).toBeVisible();

  await page.getByRole('button', { name: 'Copier le lien' }).click();
  await expect(page.getByRole('button', { name: 'Lien copié' })).toBeVisible();

  await page.getByRole('link', { name: 'Mon espace' }).click();
  const row = page.locator('.file-row', { hasText: 'compte-rendu.txt' });
  await expect(row).toContainText('Expire dans 3 jours');
});

test('un visiteur non connecté est invité à se connecter avant de téléverser', async ({ page }) => {
  await page.goto('/');
  await page.getByRole('button', { name: 'Choisir un fichier à partager' }).click();
  await expect(page).toHaveURL(/\/connexion\?returnUrl=%2F/);
  await expect(page.getByRole('heading', { name: 'Connexion' })).toBeVisible();
});
