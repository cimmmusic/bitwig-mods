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
            try { return graphics.cfM(mine); }
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
            try (InputStream own = new FileInputStream(mine)) { return graphics.cfM(name, own); }
            catch (Throwable t) { Fonts.MOD.log("couldn't load " + mine + ", using Bitwig's " + name, t); }
        }
        return graphics.cfM(name, in);
    }
}
