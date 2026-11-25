# Interface: Font

Changes the font Bitwig draws its whole interface with.

## How to use

- Open **Settings > Mods > Font**.
- **Interface font**: normal text everywhere (track names, menus, labels, the browser).
- **Bold font**: the heavier text Bitwig uses for headings and selected items. Leave it on *Same as interface font* to use one font for everything.
- **Numbers font**: the narrow font Bitwig uses for values, time and BPM readouts.
- Click a dropdown to open the **Fonts** list: every font's name is drawn in that font, so you can see what it looks like. Scroll with the wheel or trackpad, drag the list, or drag the scrollbar on the right. Type in the search field to narrow the list, click a font to choose it, or ✕ to close. The font in use is marked in orange.
- **Move text down (px)**: your fonts' letters may sit higher (or lower) than Bitwig's own. This moves text drawn in your fonts down by that many pixels (negative moves it up); Bitwig's icons stay put. Default 2.
- **Restart Bitwig** after changing a font: Bitwig loads its fonts once, at start.

## Which fonts

- Every .ttf, .otf and .ttc font installed on your Mac (your Fonts folder, the shared Fonts folder and macOS's own fonts).
- Plus any font you put in this mod's **fonts** folder: `~/Library/Application Support/BML/mods/ui-font/fonts`. Restart Bitwig to see a new font in the list.

## Good to know

- Fonts with wider letters than Bitwig's own (Lato) can get cut off in tight places, such as device titles and knob labels. Narrow or medium-width fonts work best.
- Characters your font doesn't have may show as empty boxes. Bitwig's icons and its fonts for other languages are never replaced.
- If a font can't be loaded, Bitwig's own font is used for it and the mod notes it in Bitwig's log.
- Set every choice back to *Bitwig default* to turn the mod off.
