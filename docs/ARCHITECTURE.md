# OpenAirTV Architecture

OpenAirTV separates protocol handling from Android rendering and lifecycle code.

## Layers

### Discovery
Publishes receiver services over Bonjour/mDNS, owns multicast resources and reacts to network changes.

### Session
A deterministic state machine owns connection setup, active sessions, teardown, stale-session cleanup and recovery.

### AirPlay
Protocol-facing mirroring and media negotiation. Protocol parsing must not directly control Android UI components.

### RAOP
Audio transport, decoding, clock synchronization and latency management.

### Media
Android playback layer. Hardware MediaCodec is preferred for mirroring; Media3 handles appropriate URL/HLS media paths. Audio output is isolated behind a renderer interface.

### Compatibility
Sender capabilities and runtime failures feed explicit compatibility profiles. Fallbacks such as HEVC to H.264 or decoder recreation belong here rather than being scattered through the app.

### Diagnostics
Structured session events, codec statistics, network state, A/V timing and recovery reasons. Diagnostics must remain usable without cloud services.

## Always Ready

The receiver is designed to remain discoverable independently of the foreground Activity. Android service lifecycle, boot startup, multicast locks and network callbacks are treated as core receiver infrastructure.

## Development rule

A feature is not marked functional until it is compiled and validated on a real receiver/sender path. DRM/protected playback and protocols not implemented by the project must not be advertised as supported.
