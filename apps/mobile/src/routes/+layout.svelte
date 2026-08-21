<script>
	import "../app.css";
	import QrScannerModal from "../comps/QrScannerModal.svelte";
	import { qrScannerState } from "$lib/services/qrScanner.service.svelte";
	let { children } = $props();
</script>

{@render children?.()}

{#if qrScannerState.open}
	<QrScannerModal />
{/if}
