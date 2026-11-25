# Interface: Menu Backdrop

While a menu or popup is open, the Bitwig window behind it blurs or dims with a short fade, so the menu stands out.

Settings > Mods > Menu Backdrop:
- **Style:** Blur (a soft blurred copy of the window, slightly dimmed), Dim (a live darkening) or Off.
- **Darkness (%):** how dark the Dim style gets (Blur uses a quarter of it on top of the blur).

Notes:
- Blur freezes the picture behind the menu while it is open (meters and the playhead behind it pause visually and catch up when the menu closes). Dim stays live.
- If the blur can't be made on your system, Dim is used instead (the log says why).
