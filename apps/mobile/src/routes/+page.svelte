<script lang="ts">
	import { onMount } from 'svelte';
	import InputUrl from '../comps/InputUrl.svelte';
	import SettingsButton from '../comps/SettingsButton.svelte';
	import SettingsMenu from '../comps/SettingsMenu.svelte';
	import { NativeService } from '$lib/plugins/native-service';
	import { Device } from '@capacitor/device';
	import { Geolocation } from '@capacitor/geolocation';
	import { App } from '@capacitor/app';
	import { initConsoleLog } from '$lib/services/console-log.service.svelte';
	import { appUiState } from '$lib/services/favorites.service.svelte';
	import { qrScannerState } from '$lib/services/qrScanner.service.svelte';
	import {
		dotsToShow,
		startDotAnimation,
		stopDotAnimation
	} from '../comps/dots.animation';

	let appState = $state<'idle' | 'starting' | 'running' | 'reconnecting' | 'error'>('idle');
	let connectionState = $state<'connected' | 'disconnected' | 'reconnecting'>('disconnected');
	let gpsStatus = $state<'active' | 'unavailable' | 'permissionDenied' | 'unknown'>('unknown');
	let logMessage = $state('');
	let connectionMessage = $state('');

	// Handles for connection listeners so we can remove them individually on stop
	// WITHOUT wiping the log console listener (removeAllListeners() removes all).
	let connListener: (() => void) | null = null;
	let gpsListener: (() => void) | null = null;
	let errListener: (() => void) | null = null;

	// Attach the log console listener once, at app load, so logs accumulate from
	// the very first Start (not only when the settings menu is opened).
	onMount(() => {
		initConsoleLog();

		// Android back button: close the settings menu if it's open, otherwise
		// let the OS handle it (minimize/exit). Protects for web where the plugin
		// is absent.
		let removeBackListener: (() => void) | null = null;
		try {
			App.addListener('backButton', () => {
				// If a QR/scan modal is open, don't also close the menu — the modal's
				// own back handler (or the native scanner's back) handles it.
				if (qrScannerState.open) return;
				if (appUiState.settingsOpen) {
					appUiState.settingsOpen = false;
				}
			})
				.then(({ remove }) => { removeBackListener = remove; })
				.catch(() => { /* plugin not available on web */ });
		} catch {
			// ignore
		}
		return () => { removeBackListener?.(); };
	});

	function removeConnectionListeners() {
		connListener?.();
		gpsListener?.();
		errListener?.();
		connListener = null;
		gpsListener = null;
		errListener = null;
	}

	async function requestLocationPermission(): Promise<{ granted: boolean; error?: string }> {
		try {
			// First check current state — distinguishes "services disabled" from "permission denied"
			const current = await Geolocation.checkPermissions();
			if (current.location === 'granted') {
				return { granted: true };
			}
			const status = await Geolocation.requestPermissions({ permissions: ['location'] });
			return { granted: status.location === 'granted' };
		} catch (e: any) {
			// Plugin rejects with LOCATION_DISABLED (code 7) when system location services are off
			const msg = (e?.message || '') as string;
			const code = (e?.code || '') as string;
			if (code === 'OS-PLUG-GLOC-0007' || /location services are not enabled/i.test(msg)) {
				return { granted: false, error: 'Please enable location on your smartphone.' };
			}
			return { granted: false, error: 'Location permission denied.' };
		}
	}

	async function startTracking() {
		appState = 'starting';
		logMessage = 'Requesting permissions...';
		startDotAnimation();

		const perm = await requestLocationPermission();
		if (!perm.granted) {
			stopDotAnimation();
			if (perm.error === 'Please enable location on your smartphone.') {
				alert(perm.error);
				logMessage = 'Location services are disabled';
			} else {
				logMessage = perm.error ?? 'Location permission denied.';
			}
			appState = 'error';
			return;
		}

		logMessage = 'Starting...';
		const deviceInfo = await Device.getId();
		const deviceId = deviceInfo.identifier;

		connListener?.();
		connListener = (await NativeService.addListener('connectionState', (event) => {
			connectionState = event.state;
			connectionMessage = event.message || '';

			if (event.state === 'connected') {
				appState = 'running';
				logMessage = 'Sending in progress';
				startDotAnimation();
			} else if (event.state === 'disconnected' || event.state === 'reconnecting') {
				if (appState === 'starting') {
					stopDotAnimation();
					logMessage = event.message || 'Unable to connect to server';
					appState = 'error';
					NativeService.stop();
					removeConnectionListeners();
				} else if (appState === 'running' || appState === 'reconnecting') {
					appState = 'reconnecting';
					connectionMessage = event.message || 'Reconnecting...';
					startDotAnimation();
				}
			}
		})).remove;

		gpsListener?.();
		gpsListener = (await NativeService.addListener('gpsStatus', (event) => {
			gpsStatus = event.status;
		})).remove;

		errListener?.();
		errListener = (await NativeService.addListener('error', (event) => {
			logMessage = event.message;
			if (appState === 'starting') {
				stopDotAnimation();
				appState = 'error';
				NativeService.stop();
				removeConnectionListeners();
			}
			if (event.message === 'Max reconnection attempts reached') {
				stopDotAnimation();
				appState = 'idle';
				connectionMessage = '';
				NativeService.stop();
				removeConnectionListeners();
			}
		})).remove;

		try {
			await NativeService.start({ url: `wss://${appUiState.serverUrl}`, deviceId });
		} catch (error) {
			stopDotAnimation();
			logMessage = `Failed to start: ${error}`;
			appState = 'error';
		}
	}

	async function stopTracking() {
		await NativeService.stop();
		removeConnectionListeners();
		stopDotAnimation();
		appState = 'idle';
		connectionState = 'disconnected';
		gpsStatus = 'unknown';
		logMessage = 'Stopped';
		connectionMessage = '';
		setTimeout(() => { logMessage = ''; }, 2000);
	}

	let dots = $derived($dotsToShow);
	let canStart = $derived(appUiState.serverUrl.trim() !== '' && (appState === 'idle' || appState === 'error'));
	let canStop = $derived(appState === 'running' || appState === 'starting' || appState === 'reconnecting');
</script>

<div class="flex h-screen w-full flex-col items-center justify-center bg-base-200">
	<div
		class="relative flex w-full max-w-md flex-1 flex-col overflow-hidden bg-base-100 p-4 md:m-24 md:rounded-4xl"
	>
		<div class="absolute top-2 right-2 z-50">
			<SettingsButton onclick={() => appUiState.settingsOpen = !appUiState.settingsOpen} />
		</div>

		<SettingsMenu bind:open={appUiState.settingsOpen} />

		<!-- Main content -->
		<div class="flex flex-1 flex-col items-center justify-center gap-6 px-2" id="content">
			<h1 class="text-4xl font-bold text-primary">Send GPS Data</h1>

			<InputUrl
				bind:value={appUiState.serverUrl}
				disabled={appState === 'starting' || appState === 'running' || appState === 'reconnecting'}
				hasError={appState === 'error'}
				onEnter={canStart ? startTracking : undefined}
			/>

			<button class="btn w-full btn-primary" disabled={!canStart} onclick={startTracking}>
				Start
			</button>

			<button class="btn w-full btn-secondary" disabled={!canStop} onclick={stopTracking}>
				Stop
			</button>

			<!-- Log terminal -->
			{#if logMessage || connectionMessage}
				<div class="flex w-full justify-center">
					<div
						class="terminal-log rounded bg-base-300 px-3 py-1 text-xs"
						class:text-error={appState === 'error'}
						class:text-warning={appState === 'reconnecting'}
					>
						{#if appState === 'running' && connectionState === 'connected'}
							<span class="text-success">Connected</span>
							<br />
							<span>{logMessage}{dots}</span>
						{:else if appState === 'reconnecting'}
							{connectionMessage}{dots}
						{:else}
							{logMessage}{dots}
						{/if}

					{#if appState === 'running' && connectionState === 'connected'}
						{#if gpsStatus === 'unavailable'}
							<br /><span class="text-warning">GPS unavailable</span>
						{:else if gpsStatus === 'permissionDenied'}
							<br /><span class="text-error">GPS permission denied</span>
						{/if}
					{/if}
					</div>
				</div>
			{/if}
		</div>
	</div>
</div>

<style>
	.terminal-log {
		font-family: 'Courier New', Courier, monospace;
		letter-spacing: 0.05em;
		text-shadow: 0 0 5px currentColor;
	}
</style>
