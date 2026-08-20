/**
 * Shared QR scanner modal state (Svelte 5 module store, `.service.svelte.ts`).
 *
 * Modal state for the primary QR scanner using the official
 * `@capacitor/barcode-scanner` plugin (ZXing forced on Android). Mounted at the app root
 * (+layout.svelte). The scan buttons just open/close this store.
 */
export const qrScannerState = $state<{
	open: boolean;
	id?: string;
	onScan: (value: string) => void;
}>({
	open: false,
	id: undefined,
	onScan: () => {}
});

/** Opens the QR scanner modal, wiring the callback that receives the scanned value. */
export function openQrScanner(onScan: (value: string) => void, id?: string): void {
	qrScannerState.onScan = onScan;
	qrScannerState.id = id;
	qrScannerState.open = true;
}

/** Closes the QR scanner modal without scanning. */
export function closeQrScanner(): void {
	qrScannerState.open = false;
}