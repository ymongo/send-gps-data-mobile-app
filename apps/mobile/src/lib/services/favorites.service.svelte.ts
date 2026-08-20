/**
 * Favorites + app UI state service (Svelte 5 module store, `.service.svelte.ts`).
 *
 * Centralizes the server URL and the settings-menu open flag alongside the list
 * of favorite servers, so the "select a favorite" action can both fill the main
 * URL input and close the menu from one place. Components just read/mutate the
 * state object — no prop drilling, no callbacks to wire up.
 *
 * Exported as state objects (mutate properties) because Svelte 5 forbids
 * exporting a reassigned bare `$state` binding.
 */

const STORAGE_KEY = 'gps_favorites';
const MAX_FAVORITES = 3;

/** Favorite server URLs, oldest → newest, capped at MAX_FAVORITES. */
export const favoritesState = $state<{ items: string[] }>({
	items: loadFavorites()
});

/** Shared app UI state (server URL input + settings menu visibility). */
export const appUiState = $state<{ serverUrl: string; settingsOpen: boolean }>({
	serverUrl: '',
	settingsOpen: false
});

function loadFavorites(): string[] {
	try {
		const raw = localStorage.getItem(STORAGE_KEY);
		if (!raw) return [];
		const parsed = JSON.parse(raw);
		return Array.isArray(parsed) ? parsed.filter((f) => typeof f === 'string').slice(0, MAX_FAVORITES) : [];
	} catch {
		return [];
	}
}

function persist(): void {
	try {
		localStorage.setItem(STORAGE_KEY, JSON.stringify(favoritesState.items));
	} catch {
		// ignore — favorites persistence is best-effort
	}
}

/** Selects a favorite: fills the main URL input and closes the settings menu. */
export function selectFavorite(url: string): void {
	appUiState.serverUrl = url;
	appUiState.settingsOpen = false;
}

/** Adds a new favorite, replacing the oldest slot when at capacity. */
export function addFavorite(url: string): void {
	const value = url.trim();
	if (!value) return;
	const items = favoritesState.items;
	if (items.includes(value)) return;
	favoritesState.items = items.length >= MAX_FAVORITES ? [...items.slice(1), value] : [...items, value];
	persist();
}

/** Removes the favorite at the given index. */
export function removeFavorite(index: number): void {
	favoritesState.items = favoritesState.items.filter((_, i) => i !== index);
	persist();
}
