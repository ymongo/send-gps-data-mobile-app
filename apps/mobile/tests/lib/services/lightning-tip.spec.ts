import { describe, expect, it, vi, afterEach } from "vitest";
import {
  fetchBtcPriceUsd,
  fetchInvoice,
  fetchLnurlParams,
  lnurlAddressToParamsUrl,
  usdToSats,
  validateAmount,
} from "../../../src/lib/services/lightning-tip";

afterEach(() => {
  vi.unstubAllGlobals();
});

function stubFetch(response: unknown, ok = true, status = 200) {
  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue({
      ok,
      status,
      json: () => Promise.resolve(response),
    }),
  );
}

describe("validateAmount — Alby Hub min-amount blocking", () => {
  it("blocks a positive amount below the min with a clear message", () => {
    expect(validateAmount(0.5, 1)).toBe("Minimum is 1 sats");
    expect(validateAmount(999, 1000)).toBe("Minimum is 1000 sats");
  });

  it("accepts an amount equal to or above the min", () => {
    expect(validateAmount(1, 1)).toBeNull();
    expect(validateAmount(5000, 1000)).toBeNull();
  });

  it("treats missing/invalid input as an error (Never advances silently)", () => {
    expect(validateAmount(null, 1)).toBe("Enter an amount in sats");
    expect(validateAmount(Number.NaN, 1)).toBe("Enter an amount in sats");
    expect(validateAmount(-5, 1)).toBe("Enter an amount in sats");
  });

  it("does not clamp silently when the min is unknown (0)", () => {
    expect(validateAmount(1, 0)).toBeNull();
  });
});

describe("usdToSats", () => {
  it("converts USD to sats rounded up to the nearest 1000", () => {
    expect(usdToSats(3, 100000)).toBe(3000);
    expect(usdToSats(3, 63000)).toBe(5000); // 4761.9 → ceil to 5000
  });

  it("returns 0 without a valid price", () => {
    expect(usdToSats(3, 0)).toBe(0);
  });
});

describe("lnurlAddressToParamsUrl", () => {
  it("builds the public params URL from a Lightning address", () => {
    expect(lnurlAddressToParamsUrl("greenpond2749@getalby.com")).toBe(
      "https://getalby.com/.well-known/lnurlp/greenpond2749",
    );
  });

  it("rejects malformed addresses", () => {
    expect(() => lnurlAddressToParamsUrl("no-at-sign")).toThrow();
    expect(() => lnurlAddressToParamsUrl("@getalby.com")).toThrow();
  });
});

describe("fetchLnurlParams", () => {
  it("normalizes min/max from millisats to sats (ceil/floor)", async () => {
    stubFetch({
      tag: "payRequest",
      callback: "https://getalby.com/lnurlp/greenpond2749/callback",
      minSendable: 1000,
      maxSendable: 109_000_000,
      commentAllowed: 200,
      metadata: '[["text/plain","send gps data"]]',
    });
    const params = await fetchLnurlParams("greenpond2749@getalby.com");
    expect(params.minSendable).toBe(1);
    expect(params.maxSendable).toBe(109_000);
  });

  it("throws on an error payload", async () => {
    stubFetch({ status: "ERROR", reason: "Address not found" }, false, 404);
    await expect(fetchLnurlParams("nobody@getalby.com")).rejects.toThrow(
      "Address not found",
    );
  });
});

describe("fetchInvoice", () => {
  it("calls the callback with amount in millisats and the comment", async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: () =>
        Promise.resolve({
          pr: "lnbc1",
          verify: "https://getalby.com/verify/x",
          successAction: {},
        }),
    });
    vi.stubGlobal("fetch", fetchMock);

    const invoice = await fetchInvoice(
      "https://getalby.com/lnurlp/greenpond2749/callback",
      5000,
      "thanks!",
      200,
    );

    expect(invoice.pr).toBe("lnbc1");
    const calledUrl = String(fetchMock.mock.calls[0][0]);
    expect(calledUrl).toContain("amount=5000000");
    expect(calledUrl).toContain("comment=thanks%21");
  });

  it("omits the comment when commentAllowed is 0", async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: () => Promise.resolve({ pr: "lnbc2" }),
    });
    vi.stubGlobal("fetch", fetchMock);

    await fetchInvoice(
      "https://getalby.com/lnurlp/greenpond2749/callback",
      2000,
      "ignored",
      0,
    );
    const calledUrl = String(fetchMock.mock.calls[0][0]);
    expect(calledUrl).not.toContain("comment=");
  });

  it("throws on a recipient wallet error", async () => {
    stubFetch(
      { status: "ERROR", reason: "Recipient wallet error" },
      false,
      500,
    );
    await expect(
      fetchInvoice(
        "https://getalby.com/lnurlp/greenpond2749/callback",
        1,
        "",
        0,
      ),
    ).rejects.toThrow("Recipient wallet error");
  });
});

describe("fetchBtcPriceUsd", () => {
  it("returns the USD last price", async () => {
    stubFetch({ USD: { last: 105_000 } });
    await expect(fetchBtcPriceUsd()).resolves.toBe(105_000);
  });

  it("falls back to 0 on failure (non-blocking)", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockRejectedValue(new Error("network down")),
    );
    await expect(fetchBtcPriceUsd()).resolves.toBe(0);
  });
});
