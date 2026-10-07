# Roadmap

## M0 - Foundation
- Repository, licensing and architecture
- Android TV project skeleton
- Reproducible CI builds
- Diagnostics contract

## M1 - AirPlay receiver core
- Bonjour/mDNS advertisement
- AirPlay session lifecycle
- H.264 mirroring to MediaCodec
- Audio receive/playback
- A/V synchronization
- Disconnect/reconnect handling
- Always Ready service and boot recovery

## M2 - Compatibility
- Legacy iOS/iPad validation
- Current iPhone/iPad validation
- HEVC capability negotiation
- decoder fallback/recovery
- network-change recovery
- sender compatibility profiles
- PIN/pairing where supported

## M3 - Media receiver
- video URL/HLS
- photos
- music and metadata
- artwork
- MediaSession/TV controls

## M4 - Receiver UX
- Android TV home screen
- connection status
- device naming
- diagnostics overlay
- overscan/aspect controls
- setup and troubleshooting

## M5 - Multi-protocol expansion
Evaluate DLNA/UPnP and other open receiver paths. Google Cast and Miracast are separate protocols and are not assumed to be available through the AirPlay implementation.

## Release gate

No release is labeled stable solely because it compiles. A stable milestone requires real-device tests for discovery, connect, video, audio, disconnect, reconnect, UI exit, network recovery and TV reboot.
