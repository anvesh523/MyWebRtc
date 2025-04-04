const WebSocket = require('ws');
const wss = new WebSocket.Server({ port: 8080 });

const rooms = {};

wss.on('connection', (ws, req) => {
    console.log(`New connection: ${req.url}`);
    const roomId = req.url.split('/')[2];

    if (!rooms[roomId]) {
        rooms[roomId] = [];
    }

    rooms[roomId].push(ws);

    ws.on('message', (message) => {
        console.log(`Received: ${message}`);
        rooms[roomId].forEach(client => {
            console.log(`Sending to client in room ${roomId}`);
            if (client !== ws && client.readyState === WebSocket.OPEN) {
                client.send(message);
            }
        });
    });

    ws.on('close', () => {
        rooms[roomId] = rooms[roomId].filter(client => client !== ws);
        if (rooms[roomId].length === 0) {
            delete rooms[roomId];
        }
    });
});

console.log('Signaling server running on ws://localhost:8080');