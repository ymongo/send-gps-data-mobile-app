import { defineConfig } from '@playwright/test';

export default defineConfig({
	testDir: './e2e',
	timeout: 30000,
	// Serial to avoid localStorage/state collisions between UI tests.
	fullyParallel: false,
	workers: 1,
	retries: 1,
	use: {
		baseURL: 'http://127.0.0.1:5174',
		headless: true,
		viewport: { width: 390, height: 844 }, // mobile-ish viewport
		screenshot: 'only-on-failure',
		trace: 'retain-on-failure'
	},
	webServer: {
		command: 'npm run dev -- --port 5174 --host 127.0.0.1',
		url: 'http://127.0.0.1:5174',
		reuseExistingServer: false,
		timeout: 60000
	},
	projects: [{ name: 'chromium', use: { browserName: 'chromium' } }]
});
