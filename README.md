# Cute Pet Overlay ♡

A mascot pet that floats **over any app** on your Android screen.
Moves by itself, does cute hops/spins, talks in bubbles, reacts when you tap it, and reminds you to rest.

## Features
- 🐱 4 pets: Cat / Mochi / Bunny / Panda (drawn with code, no downloads)
- 🚶 Auto-wanders across screen + drag anywhere
- 💬 Bubble chatter every ~15s–3min (adjustable)
- ♡ Tap reactions: happy jump, hearts, spin, love burst
- 🌙 Rest reminder every 15–90 min: sleepy face + zzz + notification
- 🔛 ON/OFF switch + notification buttons (Turn OFF / Snooze rest)
- 🔁 Auto-restart after reboot if left ON

## How to get the APK (pick one)

### Option A — Build in Android Studio (easiest)
1. Open this folder `cute-pet-overlay` in Android Studio
2. Let Gradle sync, then Run ▶ on your phone
3. Allow `Display over other apps` when asked, flip ON

### Option B — Auto-build with GitHub (no PC needed, free)
1. Create a GitHub repo, upload this folder
2. Go to Actions tab → run `Build APK` workflow (already included)
3. Download `cute-pet-debug.apk` from Artifacts, install on phone
   (Allow install from unknown sources)

### Option C — Build in Termux on your phone
```bash
pkg install openjdk-17 -y
# install Android cmdline-tools + sdk (one-time, ~1GB)
bash scripts/setup-sdk-termux.sh
bash scripts/build-termux.sh
# APK at: app/build/outputs/apk/debug/app-debug.apk
```

## Permissions (one-time)
- Display over other apps → so pet shows on any app
- Notifications → for ON/OFF control + rest reminders
- Ignore battery optimization → so Android doesn't kill the pet

## Use
- ON/OFF: big switch in app, or notification "Turn OFF"
- Move: drag pet (it also walks alone if wander ON)
- Tap: hearts + cute line
- Long-press pet (hold 0.6s): opens settings
- Rest: every X min pet sleeps + bubble + notification. Tap pet when rested. Snooze = 10 min.

Made with ♡ — take breaks, drink water!
