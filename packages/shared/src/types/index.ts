// GPS Data Types
// This is the shared TYPE CONTRACT between the Android mobile app and the
// desktop app over WebSocket.
//   - Desktop imports these types (apps/desktop/...) to consume the data.
//   - The mobile app is Java: it cannot import TS types. The SAME contract is
//     guaranteed there by the serialization test:
//     apps/mobile/android/app/src/test/java/com/mongoutils/sendgpsdata/
//       WebSocketManagerGpsSerializationTest.java
//     (verify buildGpsJson() emits exactly these fields).
// Keep the fields below in sync with both the Java buildGpsJson() and that test.
export type GpsData = {
	latitude: number;
	longitude: number;
	accuracy: number;
	speed: number | null;      // m/s, null if stationary
	altitude: number | null;
	altitudeAccuracy: number | null;
	heading: number | null;    // degrees, null if no movement
	timestamp: number;         // Unix timestamp ms
	deviceId?: string;
};

/**
 * GPS data with required deviceId for WebSocket transmission
 * When sending GPS data over WebSocket, deviceId is required (MA-002 AC-6)
 */
export type GpsDataWithDeviceId = Omit<GpsData, 'deviceId'> & {
	deviceId: string;
};

// WebSocket Message Types
export type PingMessage = {
	type: 'ping';
	timestamp: number;
	deviceId?: string;
};

export type PongMessage = {
	type: 'pong';
	timestamp: number;
};

export type WelcomeMessage = {
	type: 'welcome';
	message: string;
	heartbeat: {
		clientPingInterval: string;
		serverTimeoutCheck: string;
		connectionTimeout: string;
	};
};

export type ServerPingMessage = {
	type: 'server-ping';
	timestamp: number;
};

export type SuccessResponse = {
	success: true;
	received: unknown;
	timestamp: number;
};

export type ErrorResponse = {
	success: false;
	error: string;
};

/**
 * Union type for all WebSocket messages from client to server
 */
export type ClientMessage = PingMessage | GpsDataWithDeviceId;

/**
 * Union type for all WebSocket messages from server to client
 */
export type ServerMessage = PongMessage | WelcomeMessage | ServerPingMessage | SuccessResponse | ErrorResponse;
