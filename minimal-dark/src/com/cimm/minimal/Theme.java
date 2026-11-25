package com.cimm.minimal;

import bitwig.obf.iok;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The theme's colours and resources, applied by the mod itself (no Framework: Themes needed), the way that framework
 * does it: each named colour Bitwig registers gets the theme's value for its name (theme/minimal-dark.bte, Bitwig
 * Theme Editor format), "Gradient: false" stops Bitwig deriving lighter/darker shades, and files under
 * theme/overlays/icons and images replace Bitwig's of the same path. Read once at start: restart after editing.
 *
 * If Framework: Themes is on with a theme of its own selected, this part stands back (logged) so the two don't fight;
 * the rest of Minimal Dark (font, highlight, background, menus) still applies.
 */
final class Theme {
    private Theme() {}

    private static final Pattern COLOUR = Pattern.compile("^\\s*([^/:][^:]*)\\s*:\\s*(#[0-9A-Fa-f]{3,8})(?:\\s*//.*)?$");
    private static final Pattern GRADIENT = Pattern.compile("^\\s*Gradient\\s*:\\s*(true|false)\\s*(?://.*)?$", Pattern.CASE_INSENSITIVE);

    static final boolean ON = !otherThemeSelected() && Minimal.STORE.getBoolean("theme_on", true);
    private static final Map<String, iok> COLOURS = ON ? load() : Map.of();
    private static boolean noGradient;
    private static final Map<String, byte[]> OVERLAYS = ON ? overlays() : Map.of();

    static boolean any() { return !COLOURS.isEmpty(); }
    static iok colour(String name) { return name == null ? null : COLOURS.get(name); }
    static boolean gradientOff() { return noGradient; }

    /** Framework: Themes enabled with a theme chosen in its Themes tab (from BML's component bindings). */
    private static boolean otherThemeSelected() {
        try {
            String env = System.getenv("BML_MOD_CONFIG");
            if (env == null || env.isBlank()) return false;
            Properties p = new Properties();
            try (var r = Files.newBufferedReader(Path.of(env), StandardCharsets.UTF_8)) { p.load(r); }
            String id = p.getProperty("component.bte-theme-loader.group.themes.selected.0.id", "");
            if (id.isBlank()) return false;
            Minimal.MOD.log("Framework: Themes has the theme '" + id + "' selected: Minimal Dark leaves the colours to it."
                    + " To use Minimal Dark's colours, turn Framework: Themes off.");
            return true;
        } catch (Throwable t) { return false; }
    }

    private static Map<String, iok> load() {
        Path file;
        try { Path dir = Minimal.MOD.resource("theme"); file = dir == null ? null : dir.resolve("minimal-dark.bte"); }
        catch (Throwable t) { file = null; }
        if (file == null || !Files.isRegularFile(file)) { Minimal.MOD.log("theme file missing; Bitwig's colours stay"); return Map.of(); }
        HashMap<String, iok> found = new HashMap<>();
        try (BufferedReader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = r.readLine()) != null) {
                Matcher g = GRADIENT.matcher(line);
                if (g.matches()) { noGradient = "false".equalsIgnoreCase(g.group(1)); continue; }
                Matcher m = COLOUR.matcher(line);
                if (!m.matches()) continue;
                try { found.put(m.group(1).trim(), new iok(argb(m.group(2).trim()))); }
                catch (RuntimeException e) { Minimal.MOD.log("skipping colour " + m.group(1).trim() + " = " + m.group(2)); }
            }
        } catch (Throwable t) { Minimal.MOD.log("reading " + file + " failed", t); return Map.of(); }
        Minimal.MOD.log("theme: " + found.size() + " colours" + (noGradient ? ", no gradients" : ""));
        return Collections.unmodifiableMap(found);
    }

    // ------------------------------------------------------------------------------------------- hooks' work

    /** The name of the colour being registered on this thread (between the entry and return of its registration). */
    static final ThreadLocal<String> registering = new ThreadLocal<>();
    private static int applied;

    /** A named colour has stored Bitwig's value: store the theme's instead (efb.Hnt: the colour's own value). */
    static void registered(bitwig.obf.eeY colour) {
        String name = registering.get();
        registering.remove();
        iok themed = colour(name);
        if (themed == null) return;
        try {
            ((bitwig.obf.efb) colour).Hnt(themed);
            if (applied++ == 0) Minimal.MOD.log("theme colours applied (first: " + name + ")");
        } catch (Throwable t) { if (applied++ < 3) Minimal.MOD.log("theme colour " + name + " failed", t); }
    }

    /** A named colour resolved for a widget's style: the theme's value for its name (as Framework: Themes does). */
    static iok relative(bitwig.obf.eeY named, iok value) {
        if (value == null || COLOURS.isEmpty()) return value;
        String name;
        try { name = named.toString(); } catch (Throwable t) { return value; }
        if (name == null || name.equals("Same as background") || name.equals("Transparent")
                || name.equals("Image Source") || name.equals("Inherited")) return value;
        iok themed = COLOURS.get(name);
        return themed == null ? value : themed;
    }

    /** With "Gradient: false": the colour a derivation started from, not the derived shade. */
    static iok gradient(iok derived, iok original) {
        return noGradient && original != null ? original : derived;
    }

    // ------------------------------------------------------------------------------------------- overlays

    private static Map<String, byte[]> overlays() {
        Path root;
        try { Path dir = Minimal.MOD.resource("theme"); root = dir == null ? null : dir.resolve("overlays"); }
        catch (Throwable t) { return Map.of(); }
        if (root == null) return Map.of();
        HashMap<String, byte[]> found = new HashMap<>();
        for (String sub : new String[]{"icons", "images"}) {
            Path dir = root.resolve(sub);
            if (!Files.isDirectory(dir)) continue;
            try (Stream<Path> files = Files.walk(dir)) {
                files.filter(Files::isRegularFile).forEach(f -> {
                    try { found.putIfAbsent(dir.relativize(f).toString().replace('\\', '/'), Files.readAllBytes(f)); }
                    catch (Throwable ignored) {}
                });
            } catch (Throwable ignored) {}
        }
        if (!found.isEmpty()) Minimal.MOD.log("theme: " + found.size() + " icon/image overlays");
        return Collections.unmodifiableMap(found);
    }

    /** A resource file opened for reading: the overlay with the same path end replaces it. */
    static InputStream resource(InputStream stream, Object file) {
        if (stream == null || file == null || OVERLAYS.isEmpty()) return stream;
        String path = String.valueOf(file).replace('\\', '/');
        byte[] bytes = OVERLAYS.get(path);
        for (int s = path.indexOf('/'); bytes == null && s >= 0; s = path.indexOf('/', s + 1)) bytes = OVERLAYS.get(path.substring(s + 1));
        if (bytes == null) return stream;
        try { stream.close(); } catch (Throwable ignored) {}
        return new ByteArrayInputStream(bytes);
    }

    private static int argb(String value) {
        String h = value.substring(1);
        int r, g, b, a = 255;
        if (h.length() == 3 || h.length() == 4) {
            r = Character.digit(h.charAt(0), 16) * 17; g = Character.digit(h.charAt(1), 16) * 17; b = Character.digit(h.charAt(2), 16) * 17;
            if (h.length() == 4) a = Character.digit(h.charAt(3), 16) * 17;
        } else if (h.length() == 6 || h.length() == 8) {
            long v = Long.parseLong(h, 16);
            if (h.length() == 6) { r = (int) (v >>> 16 & 255); g = (int) (v >>> 8 & 255); b = (int) (v & 255); }
            else { r = (int) (v >>> 24 & 255); g = (int) (v >>> 16 & 255); b = (int) (v >>> 8 & 255); a = (int) (v & 255); }
        } else throw new IllegalArgumentException(value);
        if (r < 0 || g < 0 || b < 0 || a < 0) throw new IllegalArgumentException(value);
        return a << 24 | r << 16 | g << 8 | b;
    }
}
