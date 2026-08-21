import { test, expect } from '@playwright/test';

/**
 * Basic E2E smoke tests for the mobile frontend.
 * Run against the dev server (configured in playwright.config.ts webServer).
 *
 * These catch the regressions that occurred:
 *  1. Start button not enabling after typing a server URL (URL binding broken)
 *  2. Settings menu not opening (gear toggle broken)
 *  3. Favorites servers block rendered first in the menu
 */

test('typing a server URL enables the Start button', async ({ page }) => {
	await page.goto('/');

	// Wait for the app to actually render (first load compiles Vite modules).
	await expect(page.getByRole('heading', { name: 'Send GPS Data' })).toBeVisible({ timeout: 15000 });

	// Start should be disabled initially (empty URL)
	const startButton = page.getByRole('button', { name: 'Start' });
	await expect(startButton).toBeDisabled();

	// Type a server URL (the wss:// prefix is pre-pended in the input)
	const urlInput = page.locator('input[name="url_input"]');
	await urlInput.fill('host.example.com:8443');

	// Start must become enabled now that a URL is present
	await expect(startButton).toBeEnabled();
});

test('settings gear toggles the menu open and closed', async ({ page }) => {
	await page.goto('/');

	// The menu is rendered but hidden (translate-x-full) when closed.
	const menu = page.locator('#menu');
	await expect(menu).toHaveClass(/translate-x-full/);

	// Click the gear to open the menu (ssr=false → handler attached immediately)
	const gear = page.getByRole('button', { name: 'Settings' });
	await gear.click();
	await expect(menu).toHaveClass(/translate-x-0/);

	// The menu blocks should be present
	await expect(page.getByText('Favorite Servers')).toBeVisible();
	await expect(page.getByText('Console Logs')).toBeVisible();
	await expect(page.getByText('Data Format')).toBeVisible();

	// Click the gear again to close the menu
	await gear.click();
	await expect(menu).toHaveClass(/translate-x-full/);
});

test('favorite servers: add a favorite, then clicking it fills URL + closes menu', async ({ page }) => {
	await page.goto('/');

	// Open the settings menu
	const gear = page.getByRole('button', { name: 'Settings' });
	await gear.click();

	// Expand the Favorite Servers block (collapsed by default)
	await page.getByText('Favorite Servers', { exact: true }).click();

	// Add a favorite (target the input inside the menu)
	const addInput = page.locator('#menu input[placeholder="hostname:port"]');
	await addInput.fill('my-server.example.com:8443');
	await page.getByTitle('Add').click();

	// The favorite row appears
	await expect(page.getByText('my-server.example.com:8443')).toBeVisible();

	// Clicking it fills the main URL input and closes the menu
	await page.getByText('my-server.example.com:8443').click();

	// Menu closed
	await expect(page.locator('#menu')).toHaveClass(/translate-x-full/);
	// Main URL input now contains the selected favorite
	await expect(page.locator('input[name="url_input"]')).toHaveValue('my-server.example.com:8443');
});

test('console logs block shows a terminal area when expanded', async ({ page }) => {
	await page.goto('/');

	const gear = page.getByRole('button', { name: 'Settings' });
	await gear.click();
	await page.getByText('Console Logs', { exact: true }).click();

	// A terminal-style <pre> should be visible inside the Console Logs content
	await expect(page.locator('.terminal-logs')).toBeVisible();
});
