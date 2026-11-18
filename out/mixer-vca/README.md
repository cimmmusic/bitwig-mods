# Mixer: VCA

VCA faders for Bitwig: one fader that moves a set of channels' faders together, as on a console.

## Making a VCA

- Select the tracks, right-click one of them and choose Group as VCA.
- A new track named VCA 1 (2, 3 …) appears after them, in a lighter grey. It carries no audio.
- It is half the width of a normal mixer strip.
- Its comment reads "VCA: " and the member names, followed by a tag with their IDs: that is the link, saved with the project. Leave the tag in place.

## Using it

- Move the VCA's fader: every member's fader moves by the same number of dB.
- Each member keeps its own level relative to the VCA: move a member by hand and that becomes its new place.
- A member pushed to the top or to silence comes back to exactly where it was when the VCA returns.
- Undo undoes the VCA move, and the members follow it back.

## Notes

- Deleting the VCA track ends the VCA; the members keep their current levels.
- A member's own volume automation wins over the VCA while it plays.
- Built for Bitwig Studio 6.1.4 on macOS.
