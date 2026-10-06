import { describe, expect, it } from "vitest";
import {
  isDeviceDisconnectedMessage,
  isFatalDisconnect,
  isFatalTrackingError,
} from "../../../src/lib/services/tracking-lifecycle";

describe("isFatalDisconnect — perte de connexion", () => {
  it("arrête le suivi quand le pair ferme volontairement (code 1000)", () => {
    expect(isFatalDisconnect({ code: 1000, reason: "No heartbeat" })).toBe(
      true,
    );
    expect(
      isFatalDisconnect({
        code: 1000,
        reason: "New connection from same device",
      }),
    ).toBe(true);
  });

  it("reconnaît aussi la raison textuelle renvoyée par la couche native", () => {
    expect(isFatalDisconnect({ reason: "Connection closed (code=1000)" })).toBe(
      true,
    );
    expect(
      isFatalDisconnect({
        reason: "Connection closed (code=1000, reason=device-disconnected)",
      }),
    ).toBe(true);
  });

  it("laisse la reconnexion gérer une coupure réseau transitoire", () => {
    expect(isFatalDisconnect({ code: 1006, reason: "Abnormal closure" })).toBe(
      false,
    );
    expect(isFatalDisconnect({ code: 1001 })).toBe(false);
    expect(isFatalDisconnect({})).toBe(false);
  });
});

describe("isDeviceDisconnectedMessage — événement desktop", () => {
  it("reconnaît le message device-disconnected", () => {
    expect(
      isDeviceDisconnectedMessage(
        '{"type":"device-disconnected","deviceId":"abc"}',
      ),
    ).toBe(true);
  });

  it("ignore les autres messages et le JSON invalide", () => {
    expect(isDeviceDisconnectedMessage('{"type":"server-ping"}')).toBe(false);
    expect(isDeviceDisconnectedMessage("not json")).toBe(false);
    expect(isDeviceDisconnectedMessage("")).toBe(false);
  });
});

describe("isFatalTrackingError — erreurs qui tuent le suivi", () => {
  it("exige l’arrêt après l’épuisement des tentatives de reconnexion", () => {
    expect(isFatalTrackingError("Max reconnection attempts reached")).toBe(
      true,
    );
  });

  it("exige l’arrêt quand la localisation devient impossible", () => {
    expect(isFatalTrackingError("Location permission denied")).toBe(true);
    expect(
      isFatalTrackingError("GPS provider not available on this device"),
    ).toBe(true);
    expect(isFatalTrackingError("No location providers available")).toBe(true);
    expect(isFatalTrackingError("LocationManager not available")).toBe(true);
    expect(isFatalTrackingError("Missing url or deviceId")).toBe(true);
  });

  it("ne coupe pas pour une erreur transitoire", () => {
    expect(isFatalTrackingError("WebSocket error: timeout")).toBe(false);
    expect(isFatalTrackingError("Failed to send ping: closed")).toBe(false);
  });
});
