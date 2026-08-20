/**
 * Lightning tip helpers — direct Alby calls (no third-party proxy).
 *
 * All network logic for the LightningTipWidget lives here as plain functions
 * so the min-amount validation and URL/amount math are unit-testable without
 * a component harness. The Svelte component only wires state to the UI.
 */

/** LNURL-pay params as returned by Alby (min/max normalized to SATS). */
export interface LnurlParams {
  tag: string;
  callback: string;
  minSendable: number;
  maxSendable: number;
  commentAllowed: number;
  metadata: string;
}

/** Invoice response from the Alby callback endpoint. */
export interface LightningInvoice {
  pr: string;
  verify?: string;
  successAction?: unknown;
}

/**
 * Builds the public LNURL-pay params URL for a Lightning address
 * (e.g. `greenpond2749@getalby.com` → `https://getalby.com/.well-known/lnurlp/greenpond2749`).
 */
export function lnurlAddressToParamsUrl(address: string): string {
  const at = address.lastIndexOf("@");
  if (at <= 0 || at === address.length - 1) {
    throw new Error(`Invalid Lightning address: ${address}`);
  }
  return `https://${address.slice(at + 1)}/.well-known/lnurlp/${encodeURIComponent(address.slice(0, at))}`;
}

/**
 * Fetches the LNURL-pay params from Alby and normalizes min/max from
 * **millisats** (as returned by the endpoint) to **sats** (what the UI uses).
 */
export async function fetchLnurlParams(address: string): Promise<LnurlParams> {
  const res = await fetch(lnurlAddressToParamsUrl(address), {
    headers: { Accept: "application/json" },
  });
  const data: Record<string, unknown> = await res.json().catch(() => ({}));
  if (!res.ok || data.status === "ERROR" || data.error) {
    throw new Error(
      String(data.message || data.reason || `Request failed (${res.status})`),
    );
  }
  return {
    ...data,
    minSendable: Math.ceil(((data.minSendable as number) || 0) / 1000),
    maxSendable: Math.floor(((data.maxSendable as number) || 0) / 1000),
  } as LnurlParams;
}

/**
 * Amount validation for the "Next" click on the amount step.
 * Blocks with a user-facing message instead of silently rounding up:
 * Alby Hub refuses amounts below `minSendable` with a wallet error, so we
 * check first — the caller must NOT advance when a message is returned.
 */
export function validateAmount(
  sats: number | null,
  minSats: number,
): string | null {
  if (sats === null || !Number.isFinite(sats)) {
    return "Enter an amount in sats";
  }
  if (sats <= 0) {
    return "Enter an amount in sats";
  }
  if (Math.floor(sats) < minSats) {
    return `Minimum is ${minSats} sats`;
  }
  return null;
}

/**
 * Converts a USD amount to sats (rounded UP to the nearest 1000, like the
 * original widget) using the live BTC price. Returns 0 when no price known.
 */
export function usdToSats(usd: number, priceUsd: number): number {
  if (!priceUsd || priceUsd <= 0) return 0;
  return 1000 * Math.ceil(((usd / priceUsd) * 1e8) / 1000);
}

/**
 * Fetches a Bolt11 invoice from the Alby callback endpoint.
 * `sats` are expected pre-validated (>= minSendable) by the caller;
 * the amount is always sent in millisats = sats * 1000.
 */
export async function fetchInvoice(
  callback: string,
  sats: number,
  comment: string,
  commentAllowed: number,
): Promise<LightningInvoice> {
  const url = new URL(callback);
  url.searchParams.set("amount", String(Math.round(sats) * 1000));
  if (commentAllowed > 0 && comment) {
    url.searchParams.set("comment", comment);
  }
  const res = await fetch(url, { headers: { Accept: "application/json" } });
  const data: Record<string, unknown> = await res.json().catch(() => ({}));
  if (!res.ok || data.status === "ERROR" || data.error) {
    throw new Error(
      String(data.message || data.reason || `Request failed (${res.status})`),
    );
  }
  return data as unknown as LightningInvoice;
}

/**
 * Polls Alby's LNURL-pay `verify` URL to check whether the invoice was paid.
 * Returns `true` when paid, `false` when still pending/unpaid. Any network or
 * unknown status is treated as "not confirmed" (false) — we never block the
 * flow on a failed check. `timeoutMs` bounds a single attempt.
 */
export async function checkPayment(
  verifyUrl: string,
  timeoutMs = 6000,
): Promise<boolean> {
  if (!verifyUrl) return false;
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const res = await fetch(verifyUrl, {
      headers: { Accept: "application/json" },
      signal: controller.signal,
    });
    const data: Record<string, unknown> = await res.json().catch(() => ({}));
    const status = String(data.status || "").toUpperCase();
    // LNURL-pay verify: "PAID" (settled true) → paid; "UNPAID" → not yet.
    return status === "PAID" || data.settled === true;
  } catch {
    return false;
  } finally {
    clearTimeout(timer);
  }
}

/**
 * Fetches the current BTC price in USD from blockchain.info (non-blocking:
 * on any failure the caller keeps its fallback defaults). Timeout 6s.
 */
export async function fetchBtcPriceUsd(timeoutMs = 6000): Promise<number> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const res = await fetch("https://blockchain.info/ticker", {
      signal: controller.signal,
    });
    const data: { USD?: { last?: number } } = await res
      .json()
      .catch(() => ({}));
    return Number(data?.USD?.last) || 0;
  } catch {
    return 0;
  } finally {
    clearTimeout(timer);
  }
}
