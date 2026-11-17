package com.cimm.labelmodulator;

import bitwig.obf.dyC;  // something that can be a widget's content
import bitwig.obf.g1C;  // a modulator
import bml.api.Arg;
import bml.api.At;
import bml.api.Bml;
import bml.api.Hook;
import com.bitwig.flt.widget.core.device.wQh; // a modulator's panel (wQh.MRY(modulator) builds it)

/**
 * Label Modulator: a modulator whose name starts with "Label" shows just its text: its panel's content is a text view
 * of the rest of the name (wrapped), instead of the modulator's controls. Rename the modulator to change the text.
 */
public final class LabelModulator {
    static final Bml.Mod MOD = Bml.mod("label-modulator");
    private static final ThreadLocal<g1C> building = new ThreadLocal<>();
    private LabelModulator() {}

    @Hook(id = "panel-modulator", target = wQh.class, method = "MRY(Lg1C;)V", at = At.ENTRY)
    public static void panelModulator(@Arg(0) g1C modulator) { building.set(modulator); }

    /** wQh.MRY builds the content, then sets it: for a Label modulator, set the text view instead. */
    @Hook(id = "panel-content", target = wQh.class, method = "MRY(Lg1C;)V", at = At.REPLACE_CALL,
          callOwner = wQh.class, call = "cfM(LdyC;)V", occurrence = 1)
    public static void panelContent(wQh panel, dyC content) {
        g1C modulator = building.get();
        building.remove();
        dyC result = content;
        try {
            if (Label.isLabel(modulator)) {
                LabelText text = new LabelText(modulator);
                text.G_();
                text.a_(bitwig.obf.kn.cfM());
                text.MRY(bitwig.obf.kn.cfM());
                result = text;
                MOD.log("label: panel shows text '" + Label.text(modulator) + "'");
            }
        } catch (Throwable t) {
            MOD.log("label panel failed", t);
            result = content;
        }
        panel.cfM(result); // what Bitwig did: set the panel's content
    }
}
