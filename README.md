# Cimm's Bitwig mods

Mods for Bitwig Studio's interface, loaded with BML and managed in
Patchbay. Built against Bitwig Studio 6.1.4 on macOS.

## Mods

- **[Mixer: Device Mini Panels](https://github.com/cimmmusic/bitwig-mods/tree/perf/mixer-device-mini-panels)**:
  a small graphical view under each device in the mixer's Devices section (EQs, dynamics,
  filters, Tool and more) that you can drag to adjust, plus live meters, including a mini
  [VU Meter](https://github.com/cimmmusic/bitwig-devices/tree/main/devices/cimm-vu).
- **[Mixer: Pre Gain](mixer-pre-gain/)**: an input-gain knob at the top of every mixer
  channel, trimming the channel before its effects.
- **[Mixer: VCA](https://github.com/cimmmusic/bitwig-mods/tree/vca/mixer-vca)**: VCA faders. One fader moves a set of channels'
  faders together, with mute and solo for the group.
- **[Pin Channels](https://github.com/cimmmusic/bitwig-mods/tree/pin/pin-channels)**: pin channels (VCAs, buses, a reference track)
  so they stay in view in a fixed bar in the arranger and mixer.
- **[Label Modulator](label-modulator/)**: a modulator that's just text, for notes in a
  device's modulator area.
- **[Missing Device Guard](https://github.com/cimmmusic/bitwig-mods/tree/perf/missing-device-guard)**: opening a project that uses a
  native device you don't have (like [VU Meter](https://github.com/cimmmusic/bitwig-devices/tree/main/devices/cimm-vu))
  shows "Device missing" instead of crashing Bitwig.

Device Mini Panels (graphical version), Mixer: VCA, Pin Channels and Missing Device Guard are still in development on their own
branches (`vca`, `pin`, `perf`).

Native devices live in [bitwig-devices](https://github.com/cimmmusic/bitwig-devices).
