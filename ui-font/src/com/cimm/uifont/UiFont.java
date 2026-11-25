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
        g.MRY(x, y + Shift.offset(Shift.current.get()), text, colour);
    }

    /** Wheel / trackpad scrolls, before Bitwig handles them: the open font list scrolls. An ENTRY hook, so other mods'
     *  hooks on the same dispatch (the mixer mini views' scroll) are unaffected. */
    @Hook(id = "list-wheel", target = bitwig.obf.dpl.class, method = "cfM(Leph;)V", at = At.ENTRY)
    public static void wheel(@bml.api.Arg(0) bitwig.obf.eph e) { Browser.wheel(e); }
}
