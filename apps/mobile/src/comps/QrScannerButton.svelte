<script lang="ts">
	import { openQrScanner } from '$lib/services/qrScanner.service.svelte';

	// Reusable ZXing scan button. Renders a small icon that opens the QR scanner
	// modal (mounted at the app root via +layout.svelte). The scanned value
	// is normalized by the caller via onScan.
	let { onScan, id }: { onScan: (value: string) => void; id?: string } = $props();
</script>

<button
	class="btn btn-ghost btn-xs p-0 w-6 h-6 shrink-0"
	title="Scan QR code"
	onclick={() => openQrScanner(onScan, id)}
>
	<svg class="w-3.5 h-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
		<path stroke-linecap="round" stroke-linejoin="round" d="M7 3H4a1 1 0 00-1 1v3M17 3h3a1 1 0 011 1v3M7 21H4a1 1 0 01-1-1v-3M17 21h3a1 1 0 001-1v-3M3 12h18" />
	</svg>
</button>