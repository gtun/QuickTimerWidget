# Quick Timer

A home-screen widget with four buttons — **+30s**, **+1m**, **+5m**, **+10m**.

- Tap a button with no timer running → starts a countdown for that duration.
- Tap a button while a timer is already running → adds that duration to the
  remaining time (a widget showing `4:59` and you tap `+5m` → it now shows
  `9:59`).
- A small **✕** appears on the widget while running to cancel early.
- When the countdown reaches zero, an alarm sound loops and the phone
  vibrates repeatedly until you dismiss it — either via the notification's
  **Dismiss** action or the widget's **✕**.
- While running, an ongoing low-priority notification also shows the time
  left (tapping it opens the app; it has its own Cancel action too).
- Survives the app/service being killed by the OS and even a device reboot —
  the countdown is derived from a stored end-timestamp, not a live counter.

## Project structure

- `TimerWidgetProvider` — draws the widget (`RemoteViews`) and wires up the
  button taps.
- `TimerService` — a foreground service that owns the actual countdown: it
  ticks once a second, updates the ongoing notification and every placed
  widget instance, and fires the completion notification.
- `TimerPrefs` — the single source of truth (`SharedPreferences`), storing
  only the absolute end-time in millis so remaining time can always be
  recomputed correctly.
- `BootReceiver` — resumes a still-running timer after a device reboot.
- `MainActivity` — just an "how to add the widget" screen + requests the
  notification permission on Android 13+.

## Opening the project

1. Open Android Studio → **Open** → select this `QuickTimerWidget` folder.
2. This project omits the binary `gradle-wrapper.jar` (can't be produced as
   text). Android Studio will either regenerate it automatically on open, or
   prompt **"Gradle wrapper is not found. Create Gradle wrapper?"** — accept
   that, or run `gradle wrapper` yourself if you have Gradle installed
   locally. After that, **Sync Project** and **Run**.
3. Requires a device/emulator on **Android 8.0 (API 26)** or newer.

## Adding the widget

Long-press the home screen → **Widgets** → find **Quick Timer** → drag it to
the home screen.

## Notes / possible follow-ups

- Currently there's one global timer shared by all widget instances (if you
  place the widget twice, both show/control the same countdown). Say the
  word if you'd rather have independent timers per widget instance.
- Tick precision is ~1s and, like any non-alarm background timer, can drift
  by a few seconds if the OS deep-sleeps the device for a long stretch —
  fine for short breaks/tasks, not lab-grade precision.
