package com.cimm.uifont;

import bitwig.obf.IdX; // a resource name (here: a font file name in Bitwig's fonts folder)
import bitwig.obf.dpK; // Bitwig's font cache: loads each font file once, by name
import bml.api.At;
import bml.api.Hook;
import com.bitwig.graphics.sQv; // a loaded font face
import com.bitwig.graphics.sal; // the graphics system (makes font faces from files)

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Interface: Font.
 *
 * Bitwig draws text with font families (efs: Lato for the UI, CP Mono for numbers) whose files it loads
 * by name through one cache, dpK.Hnt(IdX): it finds fonts/&lt;name&gt; and asks the graphics system for a face,
 * from the File when there is one, else from a stream. Both calls are replaced here: when the user chose a
 * font for that file, the face is made from the user's file instead. Fallback fonts (other scripts) and
 * Bitwig's icon font are left alone.
 */
public final class UiFont {
    private UiFont() {}

    /** Bitwig's graphics system, seen when it loads its fonts: the font list makes its previews with it. */
    static volatile sal graphics;

    /**
     * A dropdown fills its popup with dBX.cfM(popup) when it opens. For this mod's Settings dropdowns the popup gets
     * the font list instead (names drawn in their own fonts, a search field); every other dropdown is left alone.
     */
    @Hook(id = "font-dropdown", target = bitwig.obf.dBX.class, method = "JJY()LdKW;",
          at = At.REPLACE_CALL, callOwner = bitwig.obf.dBX.class, call = "cfM(LdKW;)V")
    public static void fillDropdown(bitwig.obf.dBX dropdown, bitwig.obf.dKW popup) {
        Browser.Target t = null;
        try { t = Browser.target(dropdown); } catch (Throwable ignored) {}
        if (t != null) {
            try { Browser.fill(dropdown, popup, t); return; }
            catch (Throwable e) { Fonts.MOD.log("font list failed, showing the plain list", e); }
        }
        dropdown.cfM(popup);
    }

    @Hook(id = "font-from-file", target = dpK.class, method = "Hnt(LIdX;)Lcom/bitwig/graphics/sQv;",
          at = At.REPLACE_CALL, callOwner = sal.class, call = "cfM(Ljava/io/File;)Lcom/bitwig/graphics/sQv;")
    public static sQv fontFromFile(sal graphics, File file) throws IOException {
        UiFont.graphics = graphics;
        File mine = Fonts.replacement(file == null ? null : file.getName());
        if (mine != null) {
            try { return Shift.mark(graphics.cfM(mine)); }
            catch (Throwable t) { Fonts.MOD.log("couldn't load " + mine + ", using Bitwig's " + file.getName(), t); }
        }
        return graphics.cfM(file);
    }

    @Hook(id = "font-from-stream", target = dpK.class, method = "Hnt(LIdX;)Lcom/bitwig/graphics/sQv;",
          at = At.REPLACE_CALL, callOwner = sal.class,
          call = "cfM(Ljava/lang/String;Ljava/io/InputStream;)Lcom/bitwig/graphics/sQv;")
    public static sQv fontFromStream(sal graphics, String name, InputStream in) throws IOException {
        UiFont.graphics = graphics;
        File mine = Fonts.replacement(name);
        if (mine != null) {
            try (InputStream own = new FileInputStream(mine)) { return Shift.mark(graphics.cfM(name, own)); }
            catch (Throwable t) { Fonts.MOD.log("couldn't load " + mine + ", using Bitwig's " + name, t); }
        }
        return graphics.cfM(name, in);
    }

    // ------------------------------------------------------------------------------------------- text size

    /**
     * Bitwig builds each font family (efs) by adding its files with a size factor: efs.cfM(family, file, factor) calls
     * family.cfM(file, coverage, factor); the renderer draws and measures each file at size x factor (Bitwig uses it to
     * shrink fallback fonts to match). For the family's own main file (Lato for the interface and bold, CP Mono for
     * numbers) the factor is multiplied by the user's size for that role. Fallbacks and the icon font keep theirs.
     */
    @Hook(id = "text-size", target = bitwig.obf.efs.class, method = "cfM(Lcom/bitwig/graphics/OLR;Ljava/lang/String;D)V",
          at = At.REPLACE_CALL, callOwner = com.bitwig.graphics.OLR.class,
          call = "cfM(Ljava/lang/String;Ljava/util/function/IntPredicate;D)V")
    public static void textSize(com.bitwig.graphics.OLR family, String file, java.util.function.IntPredicate coverage, double factor) {
        double f = factor;
        try {
            if (file != null && file.equals(family.cfM())) { // the family's main file, not a fallback
                double k = Fonts.sizeFactor(file);
                if (k != 1) { f = factor * k; Fonts.MOD.log(file + " size " + Math.round(k * 100) + "%"); }
            }
        } catch (Throwable t) { Fonts.MOD.log("text size failed for " + file, t); }
        family.cfM(file, coverage, f);
    }

    // ------------------------------------------------------------------------------------------- moving text down

    /*
     * Bitwig draws a line of text as runs, one per font face (the UI font, then fallbacks such as its icon font for
     * symbols): RKg sets the run's face on the graphics (JXO.cfM(hLr)), then draws the run (JXO.MRY(x, y, text, colour)).
     * The first call notes the face; the second moves the run down when the face is one of the user's fonts, whose
     * letters sit higher than Lato's. Icons and other fallbacks stay where they are.
     */

    private static final String RUN = "cfM(Lcom/bitwig/graphics/render_commands/JXO;[DDLiok;Lcom/bitwig/graphics/render_commands/hLr;Ljava/lang/String;)V";

    @Hook(id = "run-face", target = com.bitwig.graphics.render_commands.RKg.class, method = RUN, at = At.REPLACE_CALL,
          callOwner = com.bitwig.graphics.render_commands.JXO.class, call = "cfM(Lcom/bitwig/graphics/render_commands/hLr;)V")
    public static void runFace(com.bitwig.graphics.render_commands.JXO g, com.bitwig.graphics.render_commands.hLr face) {
        Shift.current.set(face);
        g.cfM(face);
    }

    @Hook(id = "run-draw", target = com.bitwig.graphics.render_commands.RKg.class, method = RUN, at = At.REPLACE_CALL,
          callOwner = com.bitwig.graphics.render_commands.JXO.class, call = "MRY(DDLjava/lang/String;Liok;)V")
    public static void runDraw(com.bitwig.graphics.render_commands.JXO g, double x, double y, String text, bitwig.obf.iok colour) {
        com.bitwig.graphics.render_commands.hLr run = Shift.current.get();
        double d = Shift.offset(run);
        if (d > 0) d = Shift.fit(g, run, y, d); // never past the bottom of the field / label: descenders stay whole
        g.MRY(x, y + d, text, colour);
        // dark text (black on bright tracks, clips, light backgrounds): a thin face leaves its strokes part-covered,
        // so black over orange reads brown; a second pass a fraction of a pixel across fills the strokes in
        if (Fonts.solidDark && colour != null && Shift.userFace(run) && dark(colour))
            g.MRY(x + 0.35, y + d, text, colour);
    }

    private static boolean dark(bitwig.obf.iok c) {
        return c.hUR() > 0.9f && c.FzB() < 0.2f && c.IwV() < 0.2f && c.UNj() < 0.2f;
    }

    /** Toolbar menus in sentence case (see Labels): YGP.cfM(files) merges the language's text files into one map,
     *  reading each with YGP.cfM(file). All of the mod's hooks live in this class: BML takes one helper class per mod. */
    @Hook(id = "sentence-case-toolbar", target = bitwig.obf.YGP.class, method = "cfM(Ljava/util/List;)Ljava/util/Map;",
          at = At.REPLACE_CALL, callOwner = bitwig.obf.YGP.class, call = "cfM(LYBi;)Ljava/util/Map;")
    @SuppressWarnings("rawtypes")
    public static java.util.Map textsLoaded(bitwig.obf.YBi file) throws IOException {
        return Labels.sentenceCase(bitwig.obf.YGP.cfM(file));
    }

    /** Wheel / trackpad scrolls, before Bitwig handles them: the open font list scrolls. An ENTRY hook, so other mods'
     *  hooks on the same dispatch (the mixer mini views' scroll) are unaffected. */
    @Hook(id = "list-wheel", target = bitwig.obf.dpl.class, method = "cfM(Leph;)V", at = At.ENTRY)
    public static void wheel(@bml.api.Arg(0) bitwig.obf.eph e) { Browser.wheel(e); }
}
