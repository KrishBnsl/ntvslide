# ntvslide

App-specific volume sliders for Nothing phones, built into the look of the Nothing OS volume panel.

Nothing OS, like stock Android, only has system-wide volume streams (media, ring, alarm…). ntvslide adds a
slider **per app**, shown beside the system panel when you press a volume key, drawn in the same
black / off-white / red-dot, dot-matrix style.

```
web/       Runnable HTML/CSS/JS preview of the panel (tested; plays real per-app audio so you can hear it)
android/   The Android app (Kotlin) — accessibility overlay + Shizuku-backed per-app volume
```

## Web preview

No build step:

```sh
cd web && python3 -m http.server 8000   # then open http://localhost:8000
```

Drag a slider, scroll over it, or focus it and use ↑/↓ (Shift = ×5, `M` = mute). Tap an app icon to mute it,
press ▶ to start that app's test tone. Heard loudness = **master × app**, which is also what the Android
service does. Levels persist in `localStorage`.

## Android app

### How it works

| Piece | What it does | API used |
| --- | --- | --- |
| `VolumeKeyService` | Accessibility service. On a volume key press it shows the panel as an overlay just left of the system panel, auto-hides after 4 s. Never consumes the key, so system volume works as normal. | public (`AccessibilityService`, `TYPE_ACCESSIBILITY_OVERLAY`) |
| `ui/` (`PanelView`, `DotSliderView`, …) | The Nothing-style UI: dot-matrix pill sliders, dot-matrix readout, monochrome app icons, red dot for mute, haptic ticks every 10 %, TalkBack actions. | public |
| `AppVolumeStore` | Per-app level + mute, saved by package name. | public |
| `backend/` | Finds apps with active players and scales them. | **hidden / privileged** |

**The honest limitation:** Android has no public API to change another app's volume. `backend/` therefore runs a
small helper process through [Shizuku](https://shizuku.rikka.app/) (shell privileges, no root) that calls the
framework's player-proxy `setVolume`, the same mechanism the system uses to duck audio. Only apps you have
adjusted are ever touched, and the service re-applies levels every 2 s so newly started players are caught.

### Build & install

Needs Android Studio (or JDK 17 + Android SDK 34):

```sh
cd android
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

Then open ntvslide and:

1. Install and start Shizuku (wireless debugging is enough on Android 11+), then tap **Grant Shizuku access**.
2. Tap **Enable accessibility service** and turn on ntvslide.
3. Play something, press a volume key: the app sliders appear beside the system panel.

### Status

> ⚠️ **The Android code has not been compiled or run.** It was written without an Android SDK available, so expect
> to fix small compile errors on first build. The hidden-API calls in `PrivilegedVolumeService`
> (`IAudioService.getActivePlaybackConfigurations`, `AudioPlaybackConfiguration.getPlayerProxy().setVolume`)
> are based on AOSP 12–14 and have not been verified on a Nothing device; some apps (e.g. native/Oboe players that
> ignore the proxy volume) may not respond. The overlay offset (`88dp`) is an estimate of the system panel's width.
> The web preview is the tested reference for look and behaviour.

## License

MIT, see [LICENSE](LICENSE).
