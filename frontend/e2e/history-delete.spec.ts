import { expect, test } from '@playwright/test';
import { register, uniqueEmail, uploadFile } from './helpers';

test('le propriétaire supprime un fichier : il disparaît de l’historique et le lien ne marche plus', async ({ page }) => {
  await register(page, uniqueEmail('suppression'));
  const link = await uploadFile(page, 'brouillon.txt');

  await page.goto('/mon-espace');
  const row = page.locator('.file-row', { hasText: 'brouillon.txt' });
  await expect(row).toBeVisible();

  await row.getByRole('button', { name: 'Supprimer brouillon.txt' }).click();
  const dialog = page.getByRole('dialog', { name: 'Supprimer ce fichier ?' });
  await expect(dialog).toBeVisible();
  await dialog.getByRole('button', { name: 'Supprimer définitivement' }).click();

  await expect(page.getByText('« brouillon.txt » a été supprimé.')).toBeVisible();
  await expect(row).toHaveCount(0);

  await page.goto(new URL(link).pathname);
  await expect(page.getByRole('alert')).toContainText("n'existe pas");
});
