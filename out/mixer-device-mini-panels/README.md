# Mixer: Device Mini Panels

Adds a small graphical view under each supported device in the mixer's **Devices** section, so you can see and adjust EQs, dynamics, filters and more on every channel without opening the device.

## Getting started

- Turn on the mixer's **Devices** section (the section toggles at the bottom of the mixer).
- Supported devices get a mini view under their name. Other devices keep Bitwig's normal row.
- Views are open in the mixer and collapsed in the track Inspector by default.

## Working with the views

- **Drag a node** to change the device's settings. Every drag is one undo step.
- **Modulation** shows as a see-through "ghost" of the curve and nodes that follows the modulation live. The solid curve and nodes stay at the knob's own value.
- **Sticky 0 dB line**: in the EQ and filter views the centre line catches a node within a few pixels and only lets go when you pull it clearly away.
- **Right-click a view** for *Collapse Mini View*. A collapsed view becomes a thin bar; click it to open it again.
- **Right-click a device** (its row or panel) for *Mini View in Mixer / Track Inspector: Collapse / Expand*.
- Devices renamed by a preset are still recognised for most views.

## Supported devices

| Device | What you see | What you can do |
|---|---|---|
| **EQ+, EQ-5, EQ-2** | Frequency response, one node per band | Drag nodes for frequency and gain. Click empty space to add a band; double-click a node to remove it. Scroll or middle-drag over a node for Q. Right-click a node for its band type. |
| **Compressor+, Compressor** | Transfer curve with a dashed 1:1 line | Drag the threshold and knee nodes. |
| **Dynamics** | Transfer curve with both thresholds | Drag the low and high threshold nodes. |
| **Gate** | Transfer curve that drops below the threshold | Drag the node: left/right for Threshold, up/down for Depth. |
| **Peak Limiter** | 1:1 line bending into the ceiling | Drag the node: up/down for Ceiling, left/right for Input. |
| **Over** | Scrolling waveform of the channel with the Clip line; nothing is drawn above the line | Drag the line up/down for Clip. |
| **Saturator** | The device's curves, Quiet and Loud nodes, yellow Drive line | Drag the nodes for threshold and amount; drag the yellow node for Drive. |
| **Transient Control** | A drum hit before (dashed) and after (yellow attack, purple sustain) | Attack node: up/down Gain, left/right Time. Sustain node: the same for Sustain. |
| **Filter** | Filter response | Drag the node: left/right for Cutoff, up/down for Resonance. Right-click the node for the filter type. |
| **Filter+** | Filter response with a yellow Drive line | As Filter. Right-click the cutoff node for the filter module, or the yellow node for the waveshaper module. |
| **De-Esser** | The cut above the frequency | Drag the node: left/right for Frequency, up/down for Amount. Right-click it for the mode. |
| **Tool** | Stereo image bar | Drag the centre for pan and gain (centre pan is sticky); drag the ends for width. |
| **VU Meter** (Cimm) | Live needles | — |
| **Oscilloscope, Spectrum, Tuner** | Bitwig's own live display | — |

## Notes

- Curves follow the knobs, not the audio passing through. Dynamic devices (De-Esser, Transient Control) show what they can do rather than what they're doing at that moment. Over's waveform is the channel's output level.
- Collapse choices last for the current session.
- Built for Bitwig Studio 6.1.4 on macOS.
