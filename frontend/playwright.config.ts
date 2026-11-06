import { defineConfig, devices } from '@playwright/test';

/**
 * Tests de bout en bout : le front (ng serve, port 4200) et l'API (port 8080) doivent tourner,
 * avec une base PostgreSQL initialisée. Voir TESTING.md.
 * Par défaut, on utilise le Chrome installé sur le poste (aucun navigateur à télécharger).
 */
export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  retries: 0,
  reporter: [['list'], ['html', { open: 'never', outputFolder: 'e2e-report' }]],
  use: {
    baseURL: process.env['E2E_BASE_URL'] ?? 'http://localhost:4200',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    permissions: ['clipboard-read', 'clipboard-write'],
  },
  projects: [
    { name: 'desktop-chrome', use: { ...devices['Desktop Chrome'], channel: process.env['E2E_CHANNEL'] ?? 'chrome' } },
    { name: 'mobile', use: { ...devices['Pixel 7'], channel: process.env['E2E_CHANNEL'] ?? 'chrome' } },
  ],
});
