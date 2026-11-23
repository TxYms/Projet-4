import { expect, test } from '@playwright/test';
import { register, uniqueEmail, uploadFile } from './helpers';

test('le destinataire télécharge un fichier protégé avec le bon mot de passe', async ({ browser, page }) => {
  await register(page, uniqueEmail('protege'));
  const link = await uploadFile(page, 'contrat.txt', { password: 'sesame42' });

  // Le destinataire n'a pas de compte : nouveau contexte de navigation, sans session
  const visitor = await (await browser.newContext()).newPage();
  await visitor.goto(new URL(link).pathname);
  await expect(visitor.getByRole('heading', { name: 'Télécharger un fichier' })).toBeVisible();
  await expect(visitor.getByText('contrat.txt')).toBeVisible();
  await expect(visitor.getByText('Ce fichier expirera dans 7 jours.')).toBeVisible();

  const button = visitor.getByRole('button', { name: 'Télécharger' });
  await expect(button).toBeDisabled();

  await visitor.getByLabel('Mot de passe').fill('mauvais');
  await button.click();
  await expect(visitor.getByRole('alert')).toContainText('Mot de passe incorrect.');

  await visitor.getByLabel('Mot de passe').fill('sesame42');
  const downloadPromise = visitor.waitForEvent('download');
  await button.click();
  const download = await downloadPromise;
  expect(download.suggestedFilename()).toBe('contrat.txt');
});

test('un lien inconnu affiche une erreur explicite', async ({ page }) => {
  await page.goto('/d/lien-qui-n-existe-pas');
  await expect(page.getByRole('alert')).toContainText("Ce lien de téléchargement n'existe pas.");
});
