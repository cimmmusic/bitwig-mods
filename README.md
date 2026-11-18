# Cimm's Bitwig mods

Mods for Bitwig Studio's interface, loaded with BML (Bitwig Mod Loader) and managed in
Patchbay. Built against Bitwig Studio 6.1.4 on macOS.

## Mods

- **[Mixer: Device Mini Panels](mixer-device-mini-panels/)**: each device's remote-control
  knobs under its name in the mixer's Devices section, so EQ+, Compressor and other devices
  can be adjusted from every channel.
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

Mixer: VCA, Pin Channels and Missing Device Guard are still in development on their own
branches (`vca`, `pin`, `perf`).

Native devices live in [bitwig-devices](https://github.com/cimmmusic/bitwig-devices).
