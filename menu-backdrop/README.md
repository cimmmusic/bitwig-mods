# Interface: Menu Backdrop

While a menu or popup is open, the Bitwig window behind it blurs or dims with a short fade, so the menu stands out.

Settings > Mods > Menu Backdrop:
- **Behind open menus:** Dim (Bitwig's own popup overlay colour, the theme's "Popup overlay background color", faded in and out), Blur (not available yet: falls back to Dim) or Off.

Notes:
- Blur freezes the picture behind the menu while it is open (meters and the playhead behind it pause visually and catch up when the menu closes). Dim stays live.
- If the blur can't be made on your system, Dim is used instead (the log says why).
