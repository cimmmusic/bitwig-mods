# Mixer: Pre Gain

A gain knob at the top of every mixer channel, above its devices, like an input gain.

- Drag up/down to change it (0.1 dB per pixel); double-click resets to 0 dB. Changes are undoable.
- Behind the knob is Bitwig's Tool, added to each track automatically and hidden from the device panel and the mixer's Devices section. On instrument tracks it sits right after the instrument (it trims the instrument's output before the effects); on audio, group and effect tracks it's first in the chain.
- A track that already has a Tool anywhere in its chain doesn't get another one.
- The knob shows when the mixer's Devices section is shown.
