# Interface: Font

Changes the font Bitwig draws its whole interface with.

## How to use

- Open **Settings > Mods > Font**.
- **Interface font**: normal text everywhere (track names, menus, labels, the browser).
- **Bold font**: the heavier text Bitwig uses for headings and selected items. Leave it on *Same as interface font* to use one font for everything.
- **Numbers font**: the narrow font Bitwig uses for values, time and BPM readouts.
- **Restart Bitwig** after changing a font: Bitwig loads its fonts once, at start.

## Which fonts

- Every .ttf, .otf and .ttc font installed on your Mac (your Fonts folder, the shared Fonts folder and macOS's own fonts).
- Plus any font you put in this mod's **fonts** folder: `~/Library/Application Support/BML/mods/ui-font/fonts`. Restart Bitwig to see a new font in the list.

## Good to know

- Fonts with wider letters than Bitwig's own (Lato) can get cut off in tight places, such as device titles and knob labels. Narrow or medium-width fonts work best.
- Characters your font doesn't have may show as empty boxes. Bitwig's icons and its fonts for other languages are never replaced.
- If a font can't be loaded, Bitwig's own font is used for it and the mod notes it in Bitwig's log.
- Set every choice back to *Bitwig default* to turn the mod off.
