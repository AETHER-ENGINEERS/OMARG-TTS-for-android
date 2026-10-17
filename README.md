# ONARG Narrator

A free, private text-to-speech narrator for Android with studio-style voice effects. No subscription, no ads, no
accounts, no analytics — the app doesn't even have the internet permission. Built and tested for the Galaxy S22
(Android 12–15); runs on Android 8.0+.

## Features

- **Any TTS engine on the phone**: Samsung TTS, Google Speech Services, or free offline neural engines such as
  SherpaTTS (Piper/Kokoro voices) and RHVoice. Searchable voice picker, per-voice speed and pitch.
- **42 built-in effects**, all free and editable. Categories: narration (movie trailer, documentary, radio host,
  storyteller, deep voice, stadium announcer), characters, sci-fi (robot, cyborg, alien, 8-bit…), horror (monster,
  demon, ghost, possessed…), places (cave, cathedral, underwater…), devices (telephone, walkie-talkie,
  megaphone…) and music (choir, crowd, harmony).
- **Effect editor**: chain any of 21 processors (pitch, tempo, tape speed, reverb, echo, chorus, flanger,
  vibrato, tremolo, ring modulator, metallic resonance, distortion, bitcrusher, band filter, 3-band EQ,
  compressor, static, voice layer, reverse, volume, fade), tune every parameter, preview, and save your own presets.
- **Dialogue mode**: up to 4 voices in one script, each with its own engine, voice and effect. Switch with `[1]`–`[4]`.
- **No length limit**: long texts are split at sentence boundaries automatically. `[pause 1s]` inserts silence.
- **Background music** from your own files, with automatic ducking under the voice, looping and fade-out.
- **Export** as M4A (AAC) or lossless WAV; share straight to WhatsApp and other apps, or save to `Music/ONARG`.
- **Library** of everything you've rendered: play, rename, share, re-edit.
- Share text into the app or select text anywhere → **Narrate with ONARG**.
- Material You colours and dark mode.

## Install on your phone

1. Open the **Actions** tab on GitHub → latest **Build APK** run → download the `ONARG-Narrator-apk` artifact.
2. Unzip it, copy `ONARG-Narrator.apk` to the phone and open it (allow "Install unknown apps" for your file manager).

Tagging a commit `v*` (e.g. `v1.0.0`) also publishes the APK as a GitHub release.

### Signing so updates install over each other

Without signing secrets, CI signs with a throwaway debug key, so each build may need uninstalling the previous one.
To keep one key, create a keystore once and add these repository secrets:

```sh
keytool -genkeypair -v -keystore onarg.jks -alias onarg -keyalg RSA -keysize 4096 -validity 10000
base64 -w0 onarg.jks   # → ONARG_KEYSTORE_BASE64
```

`ONARG_KEYSTORE_BASE64`, `ONARG_KEYSTORE_PASSWORD`, `ONARG_KEY_ALIAS` (`onarg`), `ONARG_KEY_PASSWORD`.

## Build locally

Android Studio (Ladybug or newer) or `./gradlew assembleDebug` with JDK 17 and the Android SDK.

## How it works

Text → Android `TextToSpeech.synthesizeToFile` (per chunk / per voice) → silence trimming → per-voice effects →
master effect chain → normalisation → background-music mix with ducking → soft limiter. All DSP is plain Kotlin
in `app/src/main/java/com/aetherengineers/onarg/audio/dsp` (WSOLA time-stretch and pitch-shift, Freeverb reverb,
RBJ biquad filters, modulated delay lines) and is unit-tested on the JVM.
