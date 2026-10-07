# OpenAirTV

OpenAirTV is an open-source, local-first receiver for Android TV and Google TV.

The project aims to provide a robust receiver architecture for screen mirroring, audio, video, photos and local-network media, beginning with AirPlay compatibility and designed to grow into a multi-protocol receiver.

## Project status

**Early development / architecture phase.**

The first engineering milestone is a reliable Android TV 11 receiver path:

1. Bonjour / mDNS discovery
2. AirPlay session negotiation
3. H.264 mirroring
4. synchronized audio
5. clean disconnect and reconnect
6. network recovery
7. Always Ready operation after UI exit and device reboot

A known-working Android AirPlay implementation is kept as a golden reference while OpenAirTV is developed as a modular Android-first architecture.

## Architecture

- `app` - Android TV application and TV UI
- `core/discovery` - Bonjour / mDNS discovery and network recovery
- `core/session` - receiver session lifecycle and state machine
- `core/airplay` - AirPlay protocol integration
- `core/raop` - audio receiver and timing
- `core/media` - video, audio, photo and HLS playback
- `core/compat` - sender/device compatibility profiles and fallbacks
- `core/diagnostics` - telemetry, logs and receiver diagnostics

## Design principles

- Local-first: no account or cloud dependency for receiving media
- TV-first: remote-friendly UI and background receiver behavior
- Resilient: recover discovery and sessions after Wi-Fi or process interruptions
- Compatibility before novelty: old and new Apple devices are both first-class test targets
- Hardware acceleration first, graceful fallbacks where practical
- Clear protocol boundaries so additional receiver protocols can be added later

## Compatibility targets

Initial targets include Android TV 11+, iPad/iPhone senders using AirPlay-compatible mirroring and media sessions, H.264 video and synchronized audio.

AirPlay 2, protected/DRM media, Google Cast and Miracast are **not** claimed as implemented unless they pass real-device validation.

## Golden references

OpenAirTV studies established open-source implementations and real receiver behavior to understand protocol compatibility, recovery strategies, timing and Android integration. Important references include:

- UxPlay
- android-airplay-server by jqssun
- Shairport Sync
- FFmpeg
- libplist
- OpenSSL

Reference projects are not automatically part of OpenAirTV. Code incorporated from third-party projects must retain its applicable license and attribution.

## Builds

GitHub Actions is used for reproducible Android builds. APK artifacts will be published by CI once the Android application module lands.

## License

OpenAirTV is licensed under the GNU General Public License v3.0. See [LICENSE](LICENSE).

## Disclaimer

OpenAirTV is an independent open-source project. Apple, AirPlay, Google, Android and other marks belong to their respective owners. OpenAirTV is not affiliated with or endorsed by Apple or Google.
