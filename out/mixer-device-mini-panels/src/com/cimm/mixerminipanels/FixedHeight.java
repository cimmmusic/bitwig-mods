package com.cimm.mixerminipanels;

import bitwig.obf.ruA; // a widget's size context
import bitwig.obf.svm; // a size policy: min / preferred / max

/**
 * Exactly this height, in the same raw units MiniGraph's iG_() sizes use, with no clamping to the widget's own
 * minimum. (Bitwig's Sxr(n) instead scales n by the UI scale (dve.eLY) and then clamps to the widget's natural
 * min/max, so Bitwig's own taller panels never matched our 110.)
 */
final class FixedHeight implements svm {
    private final long units;
    FixedHeight(long units) { this.units = units; }
    @Override public long Hnt(ruA r) { return units; }
    @Override public long MRY(ruA r) { return units; }
    @Override public long cfM(ruA r) { return units; }
}
