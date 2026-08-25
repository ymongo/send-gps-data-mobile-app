<script lang="ts">
	import { onDestroy, onMount, tick } from 'svelte';
	import { App } from '@capacitor/app';
	import { Html5Qrcode } from 'html5-qrcode';
	import { qrScannerState, closeQrScanner } from '$lib/services/qrScanner.service.svelte';

	// Web QR scanner modal using `html5-qrcode` (pure ZXing running in the
	// WebView via getUserMedia) — NO native Google ML Kit / Play Services.
	// The camera preview renders into a stable <div id="qr-reader"> element,
	// and a continuous scan callback decodes QR codes in real time.
	//
	// Flow:
	//   - open       -> phase 'scanning', start the camera via html5QrCode.start()
	//   - decode     -> scannedUrl = decodedText -> phase 'result' (OK/Cancel)
	//   - permission/camera errors -> phase 'error' with Retry
	//   - close/unmount -> html5QrCode.stop() + clear() and release the camera.

	type Phase = 'scanning' | 'result' | 'error';
	let phase = $state<Phase>('scanning');
	let error = $state('');
	let scannedUrl = $state('');

	let disposed = false;
	let scanner: Html5Qrcode | null = null;

	// Stable DOM id the html5-qrcode library mounts its video preview into.
	const READER_ID = 'qr-reader';

	// Square scan-area size (px) — matches both the qrbox and the reticle.
	const QR_BOX_SIZE = 280;

	// Guard so we only ever start one scanning session at a time.
	let startPromise: Promise<null> | null = null;

	async function startScan() {
		phase = 'scanning';
		error = '';

		try {
			// Make sure the #qr-reader container is rendered before instantiating
			// the scanner (html5-qrcode throws if the element is missing).
			await tick();

			// Create a fresh scanner instance bound to the reader element.
			scanner = new Html5Qrcode(READER_ID, { verbose: false });

			startPromise = scanner.start(
				{ facingMode: 'environment' },
				{
					fps: 10,
					// Scan area matches the square reticle (QR_BOX_SIZE). Slightly
					// smaller than the container so the QR fits within the frame.
					qrbox: { width: QR_BOX_SIZE, height: QR_BOX_SIZE },
				},
				(decodedText) => {
					// A QR code was decoded — stop scanning and show the result.
					if (disposed || phase !== 'scanning') return;
					void stopScanner();
					scannedUrl = decodedText;
					phase = 'result';
				},
				() => {
					// Per-frame decode failure (no QR in view). Ignored; the
					// library keeps scanning until a QR is found.
				}
			);

			await startPromise;
		} catch (e: any) {
			if (disposed) return;
			const msg = String(e?.message || e?.name || e || '');
			const isPermission = /notallowed|permission/i.test(msg) || e?.name === 'NotAllowedError';
			if (isPermission) {
				error = 'Camera permission denied. Enable camera access and try again.';
			} else {
				// Could be camera unavailable, insecure context, no camera, ...
				error = msg || 'Unable to start the camera.';
			}
			phase = 'error';
		}
	}

	// Stop the camera stream and tear down the scanner instance (idempotent).
	async function stopScanner() {
		if (scanner) {
			try {
				// clear() also stops the camera stream if it is still running.
				await scanner.stop();
				scanner.clear();
			} catch {
				// stop()/clear() throw if nothing was started — safe to ignore.
			}
			scanner = null;
		}
	}

	function confirm() {
		if (scannedUrl) qrScannerState.onScan(scannedUrl);
		closeQrScanner();
	}

	function cancel() {
		closeQrScanner();
	}

	// Start scanning only AFTER the DOM is mounted so the #qr-reader element
	// exists before html5-qrcode tries to render the camera into it.
	onMount(async () => {
		// Ensure the reader container is in the DOM before starting the camera.
		await tick();
		await startScan();
	});

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
		// Ensure the camera is released when the modal unmounts.
		void stopScanner();
	});
</script>

<!-- Full-screen QR scan overlay. During 'scanning' the WebView camera preview
     (from html5-qrcode) is shown live inside the #qr-reader container. After a
     decode or an error the overlay switches to the result/error card. -->
<div
	class="fixed inset-0 z-[9999] flex flex-col items-center justify-between p-6 py-10 {phase === 'scanning' ? 'bg-black' : 'bg-black/85'}"
	role="presentation"
	onclick={(e) => { if (e.target === e.currentTarget) cancel(); }}
>
	<!-- Top: title -->
	<h2 class="text-xl font-bold text-primary text-center bg-black/50 px-4 py-1 rounded-lg">Scan QR Code</h2>

	<!-- Center: camera preview or result/error card -->
	<div class="flex flex-col items-center gap-3 w-full">
		{#if phase === 'scanning'}
			<!-- html5-qrcode injects the <video> preview into this container. -->
			<div class="relative overflow-hidden rounded-2xl bg-black">
				<div id={READER_ID} class="w-[min(90vw,320px)] h-[min(90vw,320px)]"></div>
				<!-- Single pink square reticle (the library's default corners are
				     hidden via .qr-shaded-region in app.css). The qrbox is sized to
				     QR_BOX_SIZE, so place the frame to match it. -->
				<div class="pointer-events-none absolute inset-0 flex items-center justify-center">
					<div
						class="rounded-lg border-2 border-primary/80"
						style="width: {QR_BOX_SIZE}px; height: {QR_BOX_SIZE}px"
					></div>
				</div>
			</div>
			<p class="text-xs text-base-content/60 text-center">Point the camera at a QR code</p>
		{:else if phase === 'result'}
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

	<!-- Bottom: action buttons -->
	<div class="flex flex-col gap-2 w-full max-w-sm">
		{#if phase === 'result'}
			<button class="btn btn-primary btn-sm w-full" onclick={confirm}>OK</button>
		{:else if phase === 'error'}
			<button class="btn btn-primary btn-sm w-full" onclick={startScan}>Retry</button>
		{/if}
		<button class="btn btn-outline btn-primary btn-sm w-full" onclick={cancel}>Cancel</button>
	</div>
</div>
