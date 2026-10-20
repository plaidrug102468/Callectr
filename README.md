# Callectr

**Collect your calls.**

Callectr is a Samsung-first Android call recorder built as a GPL-compliant modified fork of
[CallVault](https://github.com/madkongo/CallVault), which itself is based on
[ShizuCallRecorder](https://github.com/kitsumed/ShizuCallRecorder).

The first beta focuses on reliable carrier and WhatsApp call capture using the proven
embedded-ADB privileged recording architecture, without requiring root.

## Beta defaults

- Package: `app.callectr.recorder`
- Carrier recording: manual / Ask each time
- WhatsApp recording: enabled, manual / Ask each time
- Default codec: AAC in M4A
- App lock: enabled where the device has a PIN/password/strong biometric
- Upstream CallVault updater: disabled
- No analytics or telemetry added

## Important

Modern Android deliberately restricts call-audio capture. Callectr uses elevated local
Android shell/ADB capabilities and hidden platform APIs inherited from the upstream projects.
Behavior can vary by Android/OEM build and must be verified on real hardware.

Callectr does not provide a stealth recording mode. Users are responsible for complying with
call-recording laws applicable to them.

## Licence and attribution

GPL-3.0-or-later with the applicable additional terms in `LICENSE`. See `NOTICE.md`.
