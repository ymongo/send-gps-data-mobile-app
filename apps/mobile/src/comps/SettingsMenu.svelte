<script lang="ts">
	import { consoleLog } from '$lib/services/console-log.service.svelte';
	import { favoritesState, selectFavorite, addFavorite, removeFavorite } from '$lib/services/favorites.service.svelte';
	import QrScannerButton from './QrScannerButton.svelte';
	import LightningTipWidget from './LightningTipWidget.svelte';

	// Settings menu. Open/close is bound from the parent; favorite selection and
	// the console log store come from their dedicated state services. Clicking a
	// favorite calls selectFavorite() which fills the main URL input and closes
	// the menu.
	let { open = $bindable(false) }: { open?: boolean } = $props();
	let newFavorite = $state('');

	// When the menu closes, collapse every accordion block (details) so it reopens
	// with all sections folded. Uses a tick delay so it runs after the DOM update.
	$effect(() => {
		if (open) return;
		const menu = document.getElementById('menu');
		if (!menu) return;
		queueMicrotask(() => {
			menu.querySelectorAll('details[open]').forEach((d) => d.removeAttribute('open'));
		});
	});

	// Example of the JSON payload actually sent to the WebSocket server
	// (see WebSocketManager.buildGpsJson in the native Android service).
	const JSON_SAMPLE = `{
  "latitude": 43.5210,          // double - decimal degrees
  "longitude": -5.6100,         // double - decimal degrees
  "accuracy": 5.2,              // float (m) - horizontal accuracy
  "speed": 12.5,                // float (m/s) - reliable speed (haversine)
  "altitude": 34.8,             // double | null (m) - 0 if unavailable
  "altitudeAccuracy": 3.1,      // float | null (m)
  "heading": 180.0,             // float | null (deg, 0-360)
  "timestamp": 1755000000000,   // long (ms) - epoch UTC, GPS fix time
  "deviceId": "device-uuid"     // string - unique device identifier
}`;

	/**
	 * Builds a display string for a URL that wraps on its separators (`.`, `:`,
	 * `/`, `-`, `_`) using <wbr> — the line only breaks at these points when
	 * needed, and each segment (hostname part, :port, /path) stays fully
	 * readable. No ellipsis: the user can always read the whole URL.
	 */
	function wrapUrl(url: string): string {
		const esc = url
			.replace(/&/g, '&amp;')
			.replace(/</g, '&lt;')
			.replace(/>/g, '&gt;')
			.replace(/"/g, '&quot;')
			.replace(/'/g, '&#39;');
		return esc.replace(/([.:/\-_])/g, '$1<wbr>');
	}
</script>

<div
	class="absolute top-0 right-0 z-40 h-full w-full transform bg-base-300 transition-transform duration-300 overflow-auto"
	class:translate-x-0={open}
	class:translate-x-full={!open}
	id="menu"
>
	<div class="flex flex-col gap-4 bg-base-300 p-6">
		<h1 class="text-3xl font-bold text-primary">Settings</h1>

		<!-- Favorite Servers — first block -->
		<details class="collapse collapse-arrow border border-base-300 bg-base-100">
			<summary class="collapse-title font-semibold">Favorite Servers</summary>
			<div class="collapse-content">
				<div class="flex flex-col gap-2">
					{#each favoritesState.items as favorite, index}
						<div class="flex items-center justify-between gap-1 rounded border border-base-200 bg-base-100 px-2 py-1">
							<button
								class="favorite-url min-w-0 flex-1 text-left text-[11px] font-mono hover:text-primary"
								title={favorite}
								onclick={() => selectFavorite(favorite)}
							>
								{@html wrapUrl(favorite)}
							</button>
							<div class="flex shrink-0 items-center gap-1">
								<button class="btn btn-ghost btn-xs p-0 w-6 h-6" title="Delete" onclick={() => removeFavorite(index)}>
									<svg class="w-3.5 h-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path stroke-linecap="round" stroke-linejoin="round" d="M6 18L18 6M6 6l12 12"/></svg>
								</button>
							</div>
						</div>
					{/each}

					{#if favoritesState.items.length === 0}
						<p class="text-[11px] text-base-content/60 text-center">No favorites yet. Add one below.</p>
					{/if}

					<div class="flex items-center gap-1">
						<div class="input-bordered input input-xs input-primary flex w-full items-center gap-0 font-mono text-[11px]">
							<span class="select-none">wss://</span>
							<input
								type="search"
								placeholder="hostname:port"
								class="grow bg-transparent outline-none"
								autocapitalize="none"
								autocorrect="off"
								spellcheck="false"
								bind:value={newFavorite}
								onkeydown={(e) => { if (e.key === 'Enter') { addFavorite(newFavorite); newFavorite = ''; } }}
							/>
							<QrScannerButton onScan={(url) => { newFavorite = url.replace(/^wss:\/\//i, '').replace(/^ws:\/\//i, '').replace(/^https:\/\//i, '').replace(/^http:\/\//i, ''); }} />
						</div>
						<button class="btn btn-primary btn-xs px-2" title="Add" onclick={() => { addFavorite(newFavorite); newFavorite = ''; }}>
							<svg class="w-3.5 h-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path stroke-linecap="round" stroke-linejoin="round" d="M12 4v16m8-8H4"/></svg>
						</button>
					</div>
				</div>
			</div>
		</details>

		<!-- Console Logs -->
		<details class="collapse collapse-arrow border border-base-300 bg-base-100">
			<summary class="collapse-title font-semibold">Console Logs</summary>
			<div class="collapse-content">
				<pre class="terminal-logs rounded bg-base-300 p-2 text-[10px] leading-relaxed overflow-y-auto h-60 whitespace-pre-wrap break-all">{#if consoleLog.lines.length === 0}No logs yet{/if}{consoleLog.lines.join('\n')}</pre>
			</div>
		</details>

		<!-- Data Format -->
		<details class="collapse collapse-arrow border border-base-300 bg-base-100">
			<summary class="collapse-title font-semibold">Data Format</summary>
			<div class="collapse-content">
				<pre class="data-format rounded bg-base-300 p-2 text-[10px] leading-relaxed overflow-x-auto whitespace-pre">{JSON_SAMPLE}</pre>
			</div>
		</details>

			<!-- Support & Donate -->
		<details class="collapse collapse-arrow border border-base-300 bg-base-100">
			<summary class="collapse-title font-semibold">Support & Donate</summary>
			<div class="collapse-content">
				<p class="text-xs text-base-content/70 mb-2">Send a Bitcoin (Lightning) tip. Pick an amount and pay with any wallet.</p>

				<!-- In-app Lightning tip widget (Svelte, Alby direct, QR local).
				     {#key open} remounts it at step 0 each time the menu reopens. -->
				{#key open}
					<LightningTipWidget />
				{/key}
			</div>
		</details>

	</div>
</div>

<style>
	.terminal-logs {
		font-family: 'Courier New', Courier, monospace;
		letter-spacing: 0.03em;
	}

	.data-format {
		font-family: 'Courier New', Courier, monospace;
		letter-spacing: 0.03em;
	}

	.favorite-url {
		overflow-wrap: break-word;
	}
</style>
