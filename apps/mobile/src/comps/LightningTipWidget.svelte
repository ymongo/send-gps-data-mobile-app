<script lang="ts">
	import QRCode from 'qrcode';
	import {
		fetchInvoice,
		fetchLnurlParams,
		fetchBtcPriceUsd,
		validateAmount,
		usdToSats,
		checkPayment,
		type LnurlParams,
		type LightningInvoice
	} from '$lib/services/lightning-tip';

	/**
	 * Standalone Lightning tip widget — direct Alby calls, local QR.
	 *
	 * Replaces the old embedded widget bundle (third-party proxy + Google
	 * Fonts) with plain Svelte. Flow: start → amount → note → pay → qr →
	 * thankyou | error. Params (min/max sats) come from Alby's public LNURL
	 * endpoint; the Next button BLOCKS amounts below Alby Hub's minSendable
	 * with an inline error. QR codes are generated locally with `qrcode` —
	 * no network call for images.
	 */

	type Step = 'start' | 'amount' | 'note' | 'pay' | 'qr' | 'thankyou' | 'cancelled' | 'error';

	// Identity of the widget — same values the old embedded widget props used.
	const ADDRESS = 'greenpond2749@getalby.com';
	const NAME = 'send gps data';
	const BUTTON_TEXT = '⚡ Tip sats!';
	const AMOUNTS_USD = [3, 5, 7] as const;
	const LABELS = ['~3$', '~5$', '~7$'] as const;
	/** Fallback sats for the pills when the BTC price fetch fails (bc.info unreachable). */
	const DEFAULT_SATS = [47000, 78000, 109000] as const;

	let step = $state<Step>('start');
	let params = $state<LnurlParams | null>(null);
	let currentAmount = $state<number | null>(null);
	let comment = $state('');
	let invoice = $state<LightningInvoice | null>(null);
	let qrDataUrl = $state<string | null>(null);
	let priceUsd = $state(0);
	let loading = $state(false);
	let qrTimeoutElapsed = $state(false);
	let errorTitle = $state('');
	let errorMessage = $state('');
	let amountError = $state('');
	let amountList = $state<{ label: string; amount: number }[]>(
		AMOUNTS_USD.map((usd, i) => ({ label: LABELS[i], amount: DEFAULT_SATS[i] }))
	);

	const minSats = $derived(params?.minSendable ?? 0);
	const maxSats = $derived(params?.maxSendable ?? 0);

	// BTC price (non-blocking; on failure the pills keep DEFAULT_SATS).
	$effect(() => {
		void fetchBtcPriceUsd().then((p) => {
			if (p > 0) priceUsd = p;
		});
	});

	// Live conversion of the ~$ pills once the price is known.
	$effect(() => {
		if (priceUsd <= 0) return;
		amountList = AMOUNTS_USD.map((usd, i) => ({
			label: LABELS[i],
			amount: usdToSats(usd, priceUsd)
		}));
	});

	function messageFor(e: unknown): string {
		return e instanceof Error ? e.message : 'Unknown error';
	}

	function showError(title: string, message: string) {
		errorTitle = title;
		errorMessage = message;
		loading = false;
		step = 'error';
	}

	function reset() {
		comment = '';
		currentAmount = null;
		invoice = null;
		qrDataUrl = null;
		amountError = '';
		errorTitle = '';
		errorMessage = '';
		qrTimeoutElapsed = false;
		step = 'start';
	}

	// start → load Alby params (min/max) → amount
	async function showAmount() {
		loading = true;
		try {
			params = await fetchLnurlParams(ADDRESS);
			step = 'amount';
		} catch (e) {
			showError('Configuration error', messageFor(e));
		} finally {
			loading = false;
		}
	}

	function setAmount(raw: string) {
		const n = Number(raw);
		currentAmount = Number.isFinite(n) && raw.trim() !== '' ? n : null;
		amountError = '';
	}

	function pickAmount(sats: number) {
		currentAmount = sats;
		amountError = '';
	}

	// amount → note (or straight to pay when no comment allowed).
	// BLOCKING: amounts below Alby Hub's minSendable stay on this step.
	function next() {
		const invalid = validateAmount(currentAmount, minSats);
		if (invalid) {
			amountError = invalid;
			return;
		}
		amountError = '';
		if ((params?.commentAllowed ?? 0) > 0) {
			step = 'note';
		} else {
			step = 'pay';
			void pay();
		}
	}

	// note → pay → invoice (Alby callback) → qr + auto-open wallet
	async function pay() {
		const sats = currentAmount;
		if (!params || !sats) return;
		loading = true;
		try {
			const inv = await fetchInvoice(params.callback, sats, comment, params.commentAllowed);
			invoice = inv;
			qrDataUrl = await QRCode.toDataURL(`lightning:${inv.pr}`, {
				width: 160,
				margin: 2,
				errorCorrectionLevel: 'M'
			}).catch(() => null);
			showQr();
			setTimeout(openWallet, 400); // auto-open after the QR is painted
		} catch (e) {
			showError('Payment failed', messageFor(e));
		} finally {
			loading = false;
		}
	}

	function showQr() {
		qrTimeoutElapsed = false;
		step = 'qr';
		setTimeout(() => {
			qrTimeoutElapsed = true;
		}, 3000);
	}

	function openWallet() {
		if (!invoice) return;
		const uri = `lightning:${invoice.pr}`;
		try {
			if (!window.open(uri, '_system')) window.location.href = uri;
		} catch {
			window.location.href = uri;
		}
	}

	// "Done?" click → confirm the payment via Alby's verify URL. Paid → thankyou,
	// otherwise → a "Tip cancelled" step with a restart button.
	async function finish() {
		loading = true;
		try {
			const paid = await checkPayment(invoice?.verify ?? '');
			step = paid ? 'thankyou' : 'cancelled';
		} catch {
			step = 'cancelled';
		} finally {
			loading = false;
		}
	}

	function cancelNote() {
		step = 'amount';
	}

	// Mirrors the original widget: pay/qr always go back to note, note→amount, amount→start.
	function back() {
		const order: Step[] = ['start', 'amount', 'note', 'pay', 'qr'];
		const idx = order.indexOf(step);
		step = idx >= 3 ? 'note' : order[Math.max(idx - 1, 0)];
	}
</script>

<div class="tip-card" style="--accent: #e879f9; --color: #fff; --button-color: #e879f9">
	{#if step !== 'start' && step !== 'error' && step !== 'thankyou' && step !== 'cancelled'}
		<button class="back" title="Back" aria-label="Back" onclick={back}>
			<svg width="20" height="20" viewBox="0 0 20 20" fill="none" xmlns="http://www.w3.org/2000/svg"><path d="M13.9 0 4.7 9.2a1.4 1.4 0 0 0 0 2.1l9.2 9.3" stroke="white" stroke-width="2" stroke-linecap="round" /></svg>
		</button>
	{/if}

	{#if step === 'start'}
		<button class="button" onclick={showAmount} disabled={loading}>{BUTTON_TEXT}</button>

	{:else if step === 'amount'}
		<h3>How many sats?</h3>
		<div class="pill-container">
			{#each amountList as pill}
				<button class="pill" type="button" onclick={() => pickAmount(pill.amount)}>{pill.label}</button>
			{/each}
		</div>
		<div class="amount-field">
				<input
					type="number"
					class="amount-input"
					name="amount"
					placeholder="Enter amount in sats"
					value={currentAmount ?? ''}
					style="width: {(currentAmount ? String(currentAmount).length : 22) + 'ch'}"
					oninput={(e) => setAmount(e.currentTarget.value)}
				/>
				{#if currentAmount !== null}
					<span class="amount-unit">sats</span>
					<button class="amount-clear" type="button" aria-label="Clear amount" onclick={() => setAmount('')}>✕</button>
				{/if}
			</div>
		{#if amountError}
			<p class="amount-error" role="alert">{amountError}</p>
		{/if}
		<button class="button button-next" type="button" onclick={next}>Next</button>

	{:else if step === 'note'}
		<h3>Want to add a note?</h3>
		<textarea
			class="note-text"
			name="comment"
			placeholder="Enter your note"
			rows="4"
			maxlength={params?.commentAllowed ?? 0}
			value={comment}
			oninput={(e) => (comment = e.currentTarget.value)}
		></textarea>
		<div class="note-actions">
			<button class="button" type="button" onclick={() => { step = 'pay'; void pay(); }}>Next</button>
		</div>

	{:else if step === 'pay'}
		<svg class="spinner" width="100" height="100" viewBox="0 0 38 38" xmlns="http://www.w3.org/2000/svg">
			<defs>
				<linearGradient x1="8.042%" y1="0%" x2="65.682%" y2="23.865%" id="a">
					<stop stop-color="#fff" stop-opacity="0" offset="0%" />
					<stop stop-color="#fff" stop-opacity=".631" offset="63.146%" />
					<stop stop-color="#fff" offset="100%" />
				</linearGradient>
			</defs>
			<g fill="none" fill-rule="evenodd">
				<g transform="translate(1 1)">
					<path d="M36 18c0-9.94-8.06-18-18-18" stroke="url(#a)" stroke-width="2" />
					<circle fill="#fff" cx="36" cy="18" r="1">
						<animateTransform attributeName="transform" type="rotate" from="0 18 18" to="360 18 18" dur="0.9s" repeatCount="indefinite" />
					</circle>
				</g>
			</g>
		</svg>
		<h4 class="mb-2">Waiting for payment...</h4>
		{#if invoice}
			<button class="text-link" type="button" onclick={showQr}> Use QR code </button>
		{/if}

	{:else if step === 'qr'}
		<div class="mb-2">
			{#if qrDataUrl}
				<img class="qr" width="150" height="150" src={qrDataUrl} alt="Lightning invoice QR" />
			{:else}
				<p class="qr-fallback">Could not generate the QR code.</p>
			{/if}
		</div>
		{#if qrTimeoutElapsed}
			<button class="button" type="button" onclick={finish} disabled={loading}>{loading ? 'Checking...' : 'Done?'}</button>
		{:else}
			<h4 class="qr-heading">Scan the QR if your wallet didn't open</h4>
		{/if}

	{:else if step === 'thankyou'}
		<div>
			<h3>Thank you</h3>
		</div>
		<button class="button" type="button" onclick={reset}>Start over</button>

	{:else if step === 'cancelled'}
		<div>
			<h3>Tip cancelled 😪</h3>
		</div>
		<button class="button" type="button" onclick={reset}>Restart</button>

	{:else if step === 'error'}
		<h3 style="margin-bottom: 0">{errorTitle}</h3>
		<p class="mb-2">{errorMessage}</p>
		<button class="button" type="button" onclick={reset}>Retry</button>
	{/if}
</div>

<style>
	/* Same look as the old embedded widget: pink accent card, white text,
	   dark rose amount pill, no Google Fonts (system/DaisyUI font). */
	.tip-card {
		--accent: #e879f9;
		--color: #fff;
		--button-color: #e879f9;
		position: relative;
		width: 100%;
		min-height: 300px;
		box-sizing: border-box;
		display: flex;
		flex-direction: column;
		justify-content: center;
		align-items: center;
		text-align: center;
		padding: 28px 20px;
		border-radius: 16px;
		background-color: var(--accent);
		color: var(--color);
		font-family: system-ui, -apple-system, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
	}

	.back {
		position: absolute;
		top: 15px;
		left: 15px;
		z-index: 10;
		background: none;
		border: none;
		padding: 0;
		cursor: pointer;
		line-height: 0;
		opacity: 0.9;
	}
	.back:hover {
		opacity: 1;
	}

	h3 {
		font-size: 24px;
		margin: 0 0 22px;
		line-height: 29px;
	}
	h4 {
		font-size: 17px;
		margin: 1em;
		line-height: 21px;
	}
	p {
		margin: 1em 0;
		line-height: 1.5em;
	}
	.mb-2 {
		margin-bottom: 2em;
	}

	.button {
		font-size: 17px;
		background: #fff;
		border: 1px solid transparent;
		border-radius: 50px;
		padding: 10px 30px;
		flex: none;
		color: var(--button-color);
		font-weight: bold;
		box-shadow:
			0 32px 32px rgba(0, 0, 0, 0.07),
			0 16px 16px rgba(0, 0, 0, 0.07),
			0 8px 8px rgba(0, 0, 0, 0.07),
			0 4px 4px rgba(0, 0, 0, 0.07),
			0 2px 2px rgba(0, 0, 0, 0.07);
		transition: all 0.1s ease-in;
	}
	.button:hover:not(:disabled) {
		cursor: pointer;
		background: var(--accent);
		border-color: #fff;
		color: var(--color);
	}
	.button:disabled {
		opacity: 0.6;
	}

	.pill-container {
		display: flex;
		flex-direction: row;
		justify-content: center;
		width: 260px;
		max-width: 100%;
		margin: 0 auto 18px;
	}
	.pill {
		font-size: 17px;
		border: 1px solid var(--color);
		color: var(--color);
		background: transparent;
		padding: 0.5em 0;
		border-radius: 50px;
		flex: 1;
		text-align: center;
		margin: 0 0.25em;
		cursor: pointer;
	}
	.pill:hover {
		color: var(--accent);
		background: var(--color);
	}

	/* Dark rose pill: the editable field is clearly not one of the white pills. */
	.amount-field {
		display: flex;
		align-items: center;
		justify-content: center;
		gap: 0.5em;
		margin: 0 auto;
		width: 260px;
		max-width: 100%;
		height: 44px;
		box-sizing: border-box;
		border: 1px solid rgba(255, 255, 255, 0.35);
		border-radius: 50px;
		padding: 0 14px;
		background: rgba(120, 40, 60, 0.55);
	}
	.amount-error {
		color: #ff6b6b;
		font-size: 13px;
		margin: 8px 0 0;
	}
	.button-next {
		margin-top: 22px;
	}
	.amount-input {
		flex: none;
		text-align: center;
		font-size: 16px;
		font-weight: bold;
		line-height: 1;
		padding: 0;
		border: none;
		outline: none;
		background: none;
		color: var(--color);
		max-width: none;
		height: 100%;
		/* normal caret (not hidden) */
		caret-color: var(--color);
	}
	.amount-input::placeholder {
		color: var(--color);
		opacity: 0.5;
	}
	/* hide number input spinners */
	.amount-input::-webkit-outer-spin-button,
	.amount-input::-webkit-inner-spin-button {
		-webkit-appearance: none;
	}
	.amount-input {
		-moz-appearance: textfield;
		appearance: textfield;
	}
	.amount-unit {
		font-size: 16px;
		font-weight: bold;
		color: var(--color);
		white-space: nowrap;
		margin-left: 0.1em;
		pointer-events: none;
	}
	.amount-clear {
		display: inline-flex;
		align-items: center;
		justify-content: center;
		width: 20px;
		height: 20px;
		border-radius: 50%;
		border: none;
		background: transparent;
		color: var(--color);
		opacity: 0.7;
		font-size: 16px;
		line-height: 1;
		cursor: pointer;
		padding: 0;
	}
	.amount-clear:hover {
		opacity: 1;
	}

	.note-actions {
		display: flex;
		gap: 0.75em;
		justify-content: center;
	}

	textarea.note-text {
		width: 260px;
		max-width: 100%;
		background: none;
		border: 1px solid rgba(255, 255, 255, 0.35);
		border-radius: 16px;
		outline: none;
		resize: none;
		text-align: center;
		color: var(--color);
		font-size: 17px;
		font-family: inherit;
		padding: 12px;
		margin-bottom: 22px;
	}
	textarea.note-text::placeholder {
		color: var(--color);
		opacity: 0.5;
	}

	.spinner {
		display: block;
	}
	.text-link {
		color: var(--color);
		text-decoration: underline;
		font-weight: 500;
		background: none;
		border: none;
		padding: 0;
		cursor: pointer;
		font-size: inherit;
		font-family: inherit;
	}

	.qr {
		background: #fff;
		border-radius: 5px;
		padding: 8px;
		box-shadow:
			0 32px 32px rgba(0, 0, 0, 0.07),
			0 16px 16px rgba(0, 0, 0, 0.07),
			0 8px 8px rgba(0, 0, 0, 0.07),
			0 4px 4px rgba(0, 0, 0, 0.07),
			0 2px 2px rgba(0, 0, 0, 0.07);
	}
	.qr-fallback {
		color: var(--color);
	}
	.qr-heading {
		text-align: center;
		margin: 0;
	}
</style>