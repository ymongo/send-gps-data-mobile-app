import { createServer, IncomingMessage, ServerResponse } from 'http';

const PORT = 3000;

type GpsData = {
	latitude: number;
	longitude: number;
	accuracy: number;
	speed: number | null;
	altitude: number | null;
	altitudeAccuracy: number | null;
	heading: number | null;
	timestamp: number;
};

function handleRequest(req: IncomingMessage, res: ServerResponse) {
	// CORS headers
	res.setHeader('Access-Control-Allow-Origin', '*');
	res.setHeader('Access-Control-Allow-Methods', 'POST, HEAD, OPTIONS');
	res.setHeader('Access-Control-Allow-Headers', 'Content-Type');

	// Handle preflight
	if (req.method === 'OPTIONS') {
		res.writeHead(200);
		res.end();
		return;
	}

	// Handle HEAD (test connection)
	if (req.method === 'HEAD') {
		res.writeHead(200);
		res.end();
		return;
	}

	// Handle POST on /receiveData
	if (req.method === 'POST' && req.url === '/receiveData') {
		let body = '';

		req.on('data', (chunk) => {
			body += chunk.toString();
		});

		req.on('end', () => {
			try {
				const data: GpsData = JSON.parse(body);
				
				console.log('📍 GPS Data received:');
				console.log(`  Lat: ${data.latitude}, Lon: ${data.longitude}`);
				console.log(`  Speed: ${data.speed ? `${data.speed.toFixed(2)} m/s` : 'N/A'}`);
				console.log(`  Heading: ${data.heading ? `${data.heading}°` : 'N/A'}`);
				console.log(`  Accuracy: ${data.accuracy.toFixed(2)}m`);
				console.log(`  Timestamp: ${new Date(data.timestamp).toISOString()}`);
				console.log('---');

				res.writeHead(200, { 'Content-Type': 'application/json' });
				res.end(JSON.stringify({ success: true, received: data }));
			} catch (error) {
				console.error('Error parsing data:', error);
				res.writeHead(400, { 'Content-Type': 'application/json' });
				res.end(JSON.stringify({ success: false, error: 'Invalid JSON' }));
			}
		});
	} else {
		res.writeHead(404);
		res.end('Not Found');
	}
}

const server = createServer(handleRequest);

server.listen(PORT, () => {
	console.log(`🚀 GPS Test Server running on http://localhost:${PORT}`);
	console.log(`📡 Send GPS data to: http://localhost:${PORT}/receiveData`);
});
