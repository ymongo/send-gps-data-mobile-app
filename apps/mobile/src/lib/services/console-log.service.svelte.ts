import { NativeService } from '../plugins/native-service';

/**
 * Console log service (Svelte 5 module store, `.service.svelte.ts`).
 *
 * Centralizes the in-app log stream from the native GpsTrackingService so the
 * Settings "Console Logs" block can render the last N lines. Encapsulated here
 * as a dedicated module (not inlined in +page.svelte) so it stays reusable and
 * testable without coupling to the page component.
 *
 * IMPORTANT: the parent page calls NativeService.removeAllListeners() on stop /
 * error, which also removes this module's 'log' listener. To stay correct across
 * start/stop cycles we must be able to re-attach: initConsoleLog() first detaches
 * any previous listener (if still active) then subscribes again. It is idempotent
 * per call — safe to invoke every time the menu opens.
 */

const MAX_LINES = 50;

/**
 * Current log lines, oldest → newest, capped at MAX_LINES.
 * Exported as a state object (mutate `.lines`) — Svelte 5 forbids exporting a
 * reassigned bare `$state` binding, but mutating an object property is allowed.
 */
export const consoleLog = $state({ lines: [] as string[] });

/** Handle for the currently-attached native listener, if any. */
let removeListener: (() => void) | null = null;

/**
 * Attaches the native log listener and seeds with the buffered recent lines.
 * Detaches any previous listener first so it can be re-called safely after
 * NativeService.removeAllListeners() has wiped the listeners (e.g. on stop).
 */
export function initConsoleLog(): void {
	// Detach previous listener if one is still attached (avoids duplicates).
	if (removeListener) {
		try {
			removeListener();
		} catch {
			// ignore — listener may already be gone
		}
		removeListener = null;
	}

	// Seed from the native ring buffer (best-effort).
	NativeService.getRecentLogs()
		.then(({ logs }) => {
			if (Array.isArray(logs) && logs.length > 0) {
				consoleLog.lines = logs.slice(-MAX_LINES);
			}
		})
		.catch(() => {
			// Plugin not available (e.g. plain web build) — ignore.
		});

	// Subscribe for new lines, keeping the remove handle for later re-init.
	NativeService.addListener('log', (event) => {
		const line = formatLine(event.timestamp, event.tag ?? '?', event.message ?? '');
		if (!line) return;
		consoleLog.lines = [...consoleLog.lines, line].slice(-MAX_LINES);
	})
		.then(({ remove }) => {
			removeListener = remove;
		})
		.catch(() => {
			// Listener registration failed — log stream simply won't update.
		});
}

/** Formats a log line with a HH:mm:ss.SSS timestamp prefix. */
function formatLine(timestamp: number, tag: string, message: string): string {
	if (!message) return '';
	const t = timestamp ? new Date(timestamp) : new Date();
	const hh = String(t.getHours()).padStart(2, '0');
	const mm = String(t.getMinutes()).padStart(2, '0');
	const ss = String(t.getSeconds()).padStart(2, '0');
	const ms = String(t.getMilliseconds()).padStart(3, '0');
	return `[${hh}:${mm}:${ss}.${ms}] [${tag}] ${message}`;
}

/** Clears the in-memory console lines (both the local store and the native buffer). */
export function clearConsoleLog(): void {
	consoleLog.lines = [];
	NativeService.clearLogs().catch(() => {});
}
