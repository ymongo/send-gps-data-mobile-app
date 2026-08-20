<script lang="ts">
	import { onDestroy } from 'svelte';
	import { App } from '@capacitor/app';
	import {
		CapacitorBarcodeScanner,
		CapacitorBarcodeScannerTypeHint,
		CapacitorBarcodeScannerCameraDirection,
		CapacitorBarcodeScannerScanOrientation,
		CapacitorBarcodeScannerAndroidScanningLibrary
	} from '@capacitor/barcode-scanner';
	import { qrScannerState, closeQrScanner } from '$lib/services/qrScanner.service.svelte';

	// ZXing scan modal — the primary QR scanner for the app. The ZXing scanner
	// runs in its own native full-screen Activity (ScannerActivity, CameraX +
	// ZXing): the WebView is covered while scanning and the plugin
	// resolves/rejects once the scan ends. UI: title, 4-corner scan frame,
	// result/error cards, and OK/Cancel buttons. Required by the ZXing flow:
	//   - success   -> result.ScanResult  -> "result" phase
	//   - native cancel -> promise rejects "Scanner cancelled" -> close
	//   - permission/camera errors -> "error" phase with Retry

	type Phase = 'scanning' | 'result' | 'error';
	let phase = $state<Phase>('scanning');
	let error = $state('');
	let scannedUrl = $state('');

	let disposed = false;

	async function startScan() {
		phase = 'scanning';
		error = '';

		try {
			const result = await CapacitorBarcodeScanner.scanBarcode({
				hint: CapacitorBarcodeScannerTypeHint.QR_CODE,
				cameraDirection: CapacitorBarcodeScannerCameraDirection.BACK,
				scanOrientation: CapacitorBarcodeScannerScanOrientation.PORTRAIT,
				android: { scanningLibrary: CapacitorBarcodeScannerAndroidScanningLibrary.ZXING }
			});
			if (disposed) return;
			if (result?.ScanResult) {
				scannedUrl = result.ScanResult;
				phase = 'result';
			} else {
				error = 'No scan result';
				phase = 'error';
			}
		} catch (e: any) {
			if (disposed) return;
			const msg = String(e?.message || e || '');
			if (/cancel/i.test(msg)) {
				// Native scanner dismissed -> close.
				closeQrScanner();
			} else if (/denied/i.test(msg)) {
				// Camera permission refused by the user.
				error = 'Camera permission denied. Enable camera access and try again.';
				phase = 'error';
			} else {
				// Other failures: plugin missing on web, camera error, ...
				error = msg;
				phase = 'error';
			}
		}
	}

	function confirm() {
		if (scannedUrl) qrScannerState.onScan(scannedUrl);
		closeQrScanner();
	}

	function cancel() {
		closeQrScanner();
	}

	startScan();

	// Close on Android hardware/gesture back button; clean up on unmount.
	let removeBackListener: (() => void) | null = null;
	try {
		App.addListener('backButton', () => cancel())
			.then(({ remove }) => { removeBackListener = remove; })
			.catch(() => { /* plugin not available on web */ });
	} catch {
		// ignore
	}

	onDestroy(() => {
		disposed = true;
		removeBackListener?.();
	});
</script>

<!-- The ZXing scanner runs in its own native full-screen Activity (ScannerActivity,
     CameraX + ZXing): it covers the WebView while scanning. So during 'scanning'
     we render nothing (the native scanner is on top) — no pink scan-frame flash.
     The Svelte overlay only appears after the scan resolves (result/error). -->
<div
	class="fixed inset-0 z-[9999] flex flex-col items-center justify-between p-6 py-10 {phase !== 'scanning' ? 'bg-black/85' : 'bg-transparent'}"
	role="presentation"
	onclick={(e) => { if (e.target === e.currentTarget) cancel(); }}
>
	{#if phase !== 'scanning'}
		<!-- Top: title -->
		<h2 class="text-xl font-bold text-primary text-center bg-black/50 px-4 py-1 rounded-lg">Scan QR Code</h2>
	{/if}

	<!-- Center: only shown after the native scan resolves -->
	<div class="flex flex-col items-center gap-3">
		{#if phase === 'result'}
			<div class="w-full max-w-sm rounded-2xl bg-base-300 p-5 flex flex-col gap-4">
				<p class="text-xs text-base-content/70 text-center">Scanned URL:</p>
				<p class="rounded bg-base-100 p-2 text-[11px] font-mono break-all text-center text-success">{scannedUrl}</p>
			</div>
		{:else if phase === 'error'}
			<div class="w-full max-w-sm rounded-2xl bg-base-300 p-5">
				<p class="text-center text-sm text-error">{error}</p>
			</div>
		{/if}
	</div>

	<!-- Bottom: action buttons (only after scan resolves) -->
	{#if phase !== 'scanning'}
		<div class="flex flex-col gap-2 w-full max-w-sm">
			{#if phase === 'result'}
				<button class="btn btn-primary btn-sm w-full" onclick={confirm}>OK</button>
			{:else if phase === 'error'}
				<button class="btn btn-primary btn-sm w-full" onclick={startScan}>Retry</button>
			{/if}
			<button class="btn btn-outline btn-primary btn-sm w-full" onclick={cancel}>Cancel</button>
		</div>
	{/if}
</div>