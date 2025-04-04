const WebSocket = require('ws');
const wss = new WebSocket.Server({ port: 8080 });

const rooms = new Map();

wss.on('connection', (ws, req) => {
  // Extract room ID from URL (e.g., ws://localhost:8080?room=123)
  const url = new URL(req.url, `ws://${req.headers.host}`);
  const roomId = url.searchParams.get('room');
  const clientType = url.searchParams.get('type'); // 'android' or 'web'

  if (!roomId) {
    ws.close(1008, 'Room ID required');
    return;
  }

  console.log(`New connection: ${clientType} in room ${roomId}`);

  if (!rooms.has(roomId)) {
    rooms.set(roomId, {
      android: null,
      web: null
    });
  }

  const room = rooms.get(roomId);

  // Store the connection based on client type
  if (clientType === 'android') {
    room.android = ws;
  } else if (clientType === 'web') {
    room.web = ws;
  }

  ws.on('message', (message) => {
    console.log(`Relaying message in room ${roomId}: ${message}`);

    // Relay messages to the other client
    const target = clientType === 'android' ? room.web : room.android;
    if (target && target.readyState === WebSocket.OPEN) {
      target.send(message);
    }
  });

  ws.on('close', () => {
    console.log(`Client ${clientType} disconnected from room ${roomId}`);
    if (clientType === 'android') {
      room.android = null;
    } else {
      room.web = null;
    }

    // Clean up empty rooms
    if (!room.android && !room.web) {
      rooms.delete(roomId);
    }
  });
});

console.log('Signaling server running on ws://localhost:8080');