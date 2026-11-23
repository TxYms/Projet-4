import { Page, expect } from '@playwright/test';

export function uniqueEmail(prefix: string): string {
  return `${prefix}-${Date.now()}-${Math.floor(Math.random() * 1e6)}@e2e.datashare.fr`;
}

export async function register(page: Page, email: string, password = 'motdepasse'): Promise<void> {
  await page.goto('/inscription');
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Mot de passe', { exact: true }).fill(password);
  await page.getByLabel('Vérification du mot de passe').fill(password);
  await page.getByRole('button', { name: 'Créer mon compte' }).click();
  await expect(page).toHaveURL(/\/mon-espace$/);
}

/** Téléverse un petit fichier texte depuis l'accueil et renvoie le lien obtenu. */
export async function uploadFile(page: Page, name: string, options: { password?: string; expiration?: string } = {})
  : Promise<string> {
  await page.goto('/');
  await page.locator('input[type=file]').setInputFiles({
    name, mimeType: 'text/plain', buffer: Buffer.from(`Contenu de ${name}`),
  });
  await expect(page.getByRole('heading', { name: 'Ajouter un fichier' })).toBeVisible();
  if (options.password) {
    await page.getByLabel('Mot de passe').fill(options.password);
  }
  if (options.expiration) {
    await page.getByLabel('Expiration').selectOption({ label: options.expiration });
  }
  await page.getByRole('button', { name: 'Téléverser' }).click();
  await expect(page.getByText('Félicitations')).toBeVisible();
  return (await page.locator('.link-box').textContent())!.trim();
}
