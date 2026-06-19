# Falconer

A Chucker-style HTTP inspector for the [Dio](https://pub.dev/packages/dio)
client. Add one interceptor and inspect every request/response in a native
on-device UI — method, URL, headers, bodies, timing, status — with search,
JSON pretty-printing, image preview and cURL/text export.

> **v1 is Android-only**, with an iOS-ready architecture (all capture and
> redaction logic is pure Dart behind a platform interface). See
> [`doc/PLAN.md`](doc/PLAN.md) for the phased roadmap.

## Status

🚧 **Phase 0 — toolchain & scaffold.** The plugin registers its platform
channel and answers a `ping`; capture, storage and the inspection UI land in
later phases.

## Requirements

- Flutter 3.35.4+ / Dart 3.9+
- Android `minSdk 21`, `compileSdk 36`, Kotlin 2.1.x, Jetpack Compose

## Usage (target API — not yet wired)

```dart
import 'package:dio/dio.dart';
import 'package:falconer/falconer.dart';

void main() {
  // Configure once at startup. Disabled in release builds by default.
  Falconer.configure(const FalconerConfig());

  final dio = Dio()..interceptors.add(FalconerInterceptor());
}
```

## Security & data privacy

Falconer persists captured HTTP data **on the device**.

- **Release builds are inert by default.** Capture requires an explicit opt-in
  (`enabled: true` **and** `enableInReleaseBuilds: true`).
- **Sensitive headers are redacted in Dart before they cross the channel** —
  `Authorization`, `Cookie`, `Set-Cookie`, `Proxy-Authorization`, `X-Api-Key`,
  `X-Auth-Token` by default; secrets never reach native logs or the database.
- **Do not capture cardholder data (PAN/CVV) or other regulated PII.** In
  payment/PCI-DSS contexts, exclude such endpoints from capture and keep
  release capture disabled. Body-content redaction patterns are planned (see
  `doc/PLAN.md`, "Out of scope").

## Example

See [`example/`](example/) — a minimal app that pings the platform channel.
A full multi-Dio demo is added in Phase 9.

## License

[MIT](LICENSE).
