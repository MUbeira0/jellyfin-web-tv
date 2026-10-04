# Jellyfin Web TV

A minimal Android TV app that shows the Jellyfin web client full screen with its TV layout (`layout=tv`).
The official Android TV app is native and cannot render web plugins such as Home Screen Sections or Media Bar;
this app loads your server's web client instead, so everything the web shows appears on the TV and is navigated
with the remote's D-pad.

- Enter your server address on first launch.
- Menu button: reload, change server, exit.
- Back button: navigates back in the web client, leaves fullscreen video, then exits.

Not affiliated with the Jellyfin project. Playback uses the Android WebView's decoders; the server transcodes
when the WebView cannot play a stream directly.

## Build

GitHub Actions builds and signs the APK (`.github/workflows/build.yml`). Push a `v*` tag to publish it as a release.
Required repository secrets: `KEYSTORE_B64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.