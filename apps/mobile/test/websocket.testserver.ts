import { createServer } from 'http';
import { WebSocketServer } from 'ws';

const PORT = 3001;

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

type PingMessage = {
	type: 'ping';
	timestamp: number;
};

type PongMessage = {
	type: 'pong';
	timestamp: number;
};

// Créer le serveur HTTP
const server = createServer((req, res) => {
	// Gérer les requêtes HTTP normales
	if (req.url === '/ws') {
		res.writeHead(200, { 'Content-Type': 'text/plain' });
		res.end('WebSocket endpoint ready');
	} else {
		res.writeHead(404, { 'Content-Type': 'text/plain' });
		res.end('Not found');
	}
});

// Créer le serveur WebSocket sans path spécifique
const wss = new WebSocketServer({ 
	server
	// Pas de path, accepte toutes les connexions WebSocket
});

// Tracker les connexions actives par device ID
const activeConnections = new Map<any, { lastPing: number; interval: NodeJS.Timeout; deviceId?: string }>();
const deviceConnections = new Map<string, any>(); // deviceId -> websocket

wss.on('connection', (ws) => {
	console.log('📡 New WebSocket connection established');

	// Initialiser le heartbeat pour cette connexion
	const heartbeatInterval = setInterval(() => {
		if (ws.readyState === ws.OPEN) {
			// Vérifier si on a reçu un ping récemment
			const connection = activeConnections.get(ws);
			if (connection && Date.now() - connection.lastPing > 60000) { // 60s sans ping
				console.log('⚠️  No ping received for 60s, closing connection');
				ws.close(1000, 'No heartbeat');
				return;
			}
			
			// Envoyer un ping du serveur vers le client (optionnel)
			ws.send(JSON.stringify({ 
				type: 'server-ping', 
				timestamp: Date.now() 
			}));
		}
	}, 45000); // Vérifier toutes les 45 secondes

	// Enregistrer la connexion
	activeConnections.set(ws, { 
		lastPing: Date.now(), 
		interval: heartbeatInterval 
	});

	ws.on('message', (message) => {
		try {
			const rawData = JSON.parse(message.toString());
			
			// Gérer les messages de heartbeat
			if (rawData.type === 'ping') {
				const pingData = rawData as PingMessage & { deviceId?: string };
				console.log(`💓 Ping received from device: ${pingData.deviceId || 'unknown'}`);
				
				// Mettre à jour le timestamp du dernier ping
				const connection = activeConnections.get(ws);
				if (connection) {
					connection.lastPing = Date.now();
					// Enregistrer le deviceId seulement s'il n'est pas déjà défini
					if (pingData.deviceId && !connection.deviceId) {
						connection.deviceId = pingData.deviceId;
						// Fermer TOUTES les anciennes connexions de ce device
						const oldWs = deviceConnections.get(pingData.deviceId);
						if (oldWs && oldWs !== ws) {
							console.log(`🔄 Closing old connection for device: ${pingData.deviceId}`);
							if (oldWs.readyState === oldWs.OPEN) {
								oldWs.close(1000, 'New connection from same device');
							}
							// Nettoyer immédiatement l'ancienne connexion
							const oldConnection = activeConnections.get(oldWs);
							if (oldConnection) {
								clearInterval(oldConnection.interval);
								activeConnections.delete(oldWs);
							}
						}
						deviceConnections.set(pingData.deviceId, ws);
						console.log(`✅ Device ${pingData.deviceId} registered to new connection`);
					}
				}
				
				// Répondre avec un pong
				const pongResponse: PongMessage = {
					type: 'pong',
					timestamp: Date.now()
				};
				ws.send(JSON.stringify(pongResponse));
				return;
			}
			
			// Gérer les données GPS normales
			const data: GpsData & { deviceId?: string } = rawData;
			
			// Vérifier que c'est bien des données GPS
			if (typeof data.latitude === 'number' && typeof data.longitude === 'number') {
				// Mettre à jour le timestamp - les données GPS comptent comme activité
				const connection = activeConnections.get(ws);
				if (connection) {
					connection.lastPing = Date.now();
					// Enregistrer le deviceId seulement s'il n'est pas déjà défini
					if (data.deviceId && !connection.deviceId) {
						connection.deviceId = data.deviceId;
						// Fermer TOUTES les anciennes connexions de ce device
						const oldWs = deviceConnections.get(data.deviceId);
						if (oldWs && oldWs !== ws) {
							console.log(`🔄 Closing old connection for device: ${data.deviceId}`);
							if (oldWs.readyState === oldWs.OPEN) {
								oldWs.close(1000, 'New connection from same device');
							}
							// Nettoyer immédiatement l'ancienne connexion
							const oldConnection = activeConnections.get(oldWs);
							if (oldConnection) {
								clearInterval(oldConnection.interval);
								activeConnections.delete(oldWs);
							}
						}
						deviceConnections.set(data.deviceId, ws);
						console.log(`✅ Device ${data.deviceId} registered to new connection`);
					}
				}
				
				console.log(`📍 GPS Data from device: ${data.deviceId || 'unknown'}`);
				console.log(`  Lat: ${data.latitude}, Lon: ${data.longitude}`);
				console.log(`  Speed: ${data.speed !== null ? `${data.speed.toFixed(2)} m/s` : 'N/A (stationary)'}`);
				console.log(`  Heading: ${data.heading !== null ? `${data.heading.toFixed(1)}°` : 'N/A (no movement)'}`);
				console.log(`  Accuracy: ${data.accuracy.toFixed(2)}m`);
				console.log(`  Timestamp: ${new Date(data.timestamp).toISOString()}`);
				console.log('---');

				// Répondre avec un accusé de réception
				ws.send(JSON.stringify({ 
					success: true, 
					received: data,
					timestamp: Date.now()
				}));
			} else {
				console.log('📨 Non-GPS message received:', rawData);
			}
		} catch (error) {
			console.error('Error parsing message:', error);
			ws.send(JSON.stringify({ 
				success: false, 
				error: 'Invalid JSON' 
			}));
		}
	});

	ws.on('close', (code, reason) => {
		const connection = activeConnections.get(ws);
		const deviceId = connection?.deviceId;
		
		console.log(`📡 WebSocket connection closed for device: ${deviceId || 'unknown'} (${code}: ${reason})`);
		
		// Nettoyer les ressources
		if (connection) {
			clearInterval(connection.interval);
			activeConnections.delete(ws);
		}
		
		// Nettoyer le mapping device -> websocket
		if (deviceId && deviceConnections.get(deviceId) === ws) {
			deviceConnections.delete(deviceId);
		}
	});

	ws.on('error', (error) => {
		console.error('WebSocket error:', error);
	});

	// Gérer les pongs du client (réponse à nos server-pings)
	ws.on('pong', (data) => {
		console.log('🏓 Pong received from client');
		const connection = activeConnections.get(ws);
		if (connection) {
			connection.lastPing = Date.now();
		}
	});

	// Envoyer un message de bienvenue
	ws.send(JSON.stringify({ 
		type: 'welcome', 
		message: 'GPS WebSocket server ready with heartbeat support',
		heartbeat: {
			clientPingInterval: '30s',
			serverTimeoutCheck: '45s',
			connectionTimeout: '60s'
		}
	}));
});

// Nettoyer les connexions fermées périodiquement
setInterval(() => {
	const now = Date.now();
	const connectionsToDelete = [];
	
	for (const [ws, connection] of activeConnections.entries()) {
		// Supprimer les connexions fermées ou très anciennes
		if (ws.readyState !== ws.OPEN || now - connection.lastPing > 90000) {
			console.log(`🧹 Cleaning up stale connection for device: ${connection.deviceId || 'unknown'}`);
			clearInterval(connection.interval);
			connectionsToDelete.push(ws);
			
			// Nettoyer aussi le mapping device si c'est cette connexion
			if (connection.deviceId && deviceConnections.get(connection.deviceId) === ws) {
				deviceConnections.delete(connection.deviceId);
			}
		}
	}
	
	// Supprimer les connexions identifiées
	connectionsToDelete.forEach(ws => activeConnections.delete(ws));
}, 60000); // Nettoyer toutes les minutes

server.listen(PORT, '0.0.0.0', () => {
	console.log(`🚀 GPS WebSocket Server running on ws://0.0.0.0:${PORT}/ws`);
	console.log(`📡 Connect your app to: ws://localhost:${PORT}/ws (local)`);
	console.log(`🌐 Or via Tailscale`);
	console.log(`💓 Heartbeat: Client pings every 30s, server timeout after 60s`);
	console.log(`📊 Active connections: ${activeConnections.size}`);
});

// Afficher le nombre de connexions actives périodiquement
setInterval(() => {
	if (activeConnections.size > 0) {
		console.log(`📊 Active connections: ${activeConnections.size}, Unique devices: ${deviceConnections.size}`);
		// Afficher les devices connectés
		const devices = Array.from(deviceConnections.keys());
		if (devices.length > 0) {
			console.log(`📱 Connected devices: ${devices.join(', ')}`);
		}
	}
}, 30000);