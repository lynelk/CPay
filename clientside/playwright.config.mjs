import { defineConfig } from '@playwright/test';

const desktop = (browserName, width, height) => ({
  browserName,
  viewport: { width, height },
  deviceScaleFactor: 1,
});

const mobile = (browserName, width, height, deviceScaleFactor) => ({
  browserName,
  viewport: { width, height },
  deviceScaleFactor,
  isMobile: true,
  hasTouch: true,
});

export default defineConfig({
  testDir: './e2e',
  testMatch: '**/*.pw.mjs',
  outputDir: 'test-results/browser-matrix',
  timeout: 45_000,
  expect: { timeout: 10_000 },
  fullyParallel: true,
  forbidOnly: Boolean(process.env.CI),
  retries: process.env.CI ? 1 : 0,
  workers: process.env.CI ? 4 : undefined,
  reporter: [
    ['list'],
    ['html', { outputFolder: 'playwright-report', open: 'never' }],
  ],
  use: {
    baseURL: 'http://127.0.0.1:4173',
    colorScheme: 'light',
    locale: 'en-GB',
    screenshot: 'only-on-failure',
    trace: 'retain-on-failure',
    video: 'retain-on-failure',
  },
  webServer: {
    command: 'npm run preview -- --host 127.0.0.1 --port 4173',
    url: 'http://127.0.0.1:4173',
    reuseExistingServer: !process.env.CI,
    timeout: 120_000,
  },
  projects: [
    { name: 'chrome-edge-1366', use: desktop('chromium', 1366, 768) },
    { name: 'chrome-edge-1440', use: desktop('chromium', 1440, 900) },
    { name: 'chrome-edge-1920', use: desktop('chromium', 1920, 1080) },
    { name: 'firefox-desktop', use: desktop('firefox', 1440, 900) },
    { name: 'safari-webkit-desktop', use: desktop('webkit', 1440, 900) },
    { name: 'android-chrome', use: mobile('chromium', 412, 915, 2.625) },
    { name: 'iphone-safari', use: mobile('webkit', 390, 844, 3) },
    { name: 'ipad-safari', use: mobile('webkit', 820, 1180, 2) },
  ],
});
