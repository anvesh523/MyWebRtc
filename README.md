# MyWebRTC

How to Use This App

    Set up the local server:
        Install Node.js
        Run node server.js on your computer
        Note your computer's local IP (e.g., 192.168.1.x)
    Update the Android app:
        Change ws://10.0.2.2:8080 to your computer's IP in SignalingClient
    Run the app on two devices:
        Both devices must be on the same local network
        Enter the same room ID on both devices
        Click "Connect" on both
        Start chatting!

Key Features

    - Uses WebRTC DataChannel for direct P2P communication
    - Works without internet (only local network required)
    - Unique room ID system for pairing devices
    - Simple text chat functionality
    - Local WebSocket signaling server
