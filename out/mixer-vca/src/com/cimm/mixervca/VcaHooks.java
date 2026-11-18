package com.cimm.mixervca;

import bitwig.obf.dWb; // a vertical or horizontal group of widgets
import bitwig.obf.dve; // a widget
import bml.api.At;
import bml.api.Bml;
import bml.api.Hook;
import bml.api.Return;

/**
 * Mixer: VCA — layout probe. Bitwig's mixer is rows (one per section: header, I/O, sends, devices, fader…) of cells,
 * one per channel; the master strip lives in the fixed area on the right (bDq), whose getters each return master's
 * cell for one section row (bDw lays them out, so they line up with the track rows). Each getter is wrapped here:
 * [probe cell][master's cell], to see which getter is which section and that the hook works. Logged once per getter.
 */
public final class VcaHooks {
    static final Bml.Mod MOD = Bml.mod("mixer-vca");
    private static final java.util.Set<String> logged = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private VcaHooks() {}

    @Hook(id = "master-cfM", target = bitwig.obf.bDq.class, method = "cfM()Ldve;", at = At.WRAP_RETURN)
    public static dve master_cfM(@Return dve cell) { return wrap("cfM", cell); }

    @Hook(id = "master-IwV", target = bitwig.obf.bDq.class, method = "IwV()Ldve;", at = At.WRAP_RETURN)
    public static dve master_IwV(@Return dve cell) { return wrap("IwV", cell); }

    @Hook(id = "master-UNj", target = bitwig.obf.bDq.class, method = "UNj()Ldve;", at = At.WRAP_RETURN)
    public static dve master_UNj(@Return dve cell) { return wrap("UNj", cell); }

    @Hook(id = "master-hUR", target = bitwig.obf.bDq.class, method = "hUR()Ldve;", at = At.WRAP_RETURN)
    public static dve master_hUR(@Return dve cell) { return wrap("hUR", cell); }

    @Hook(id = "master-TMF", target = bitwig.obf.bDq.class, method = "TMF()Ldve;", at = At.WRAP_RETURN)
    public static dve master_TMF(@Return dve cell) { return wrap("TMF", cell); }

    @Hook(id = "master-fJR", target = bitwig.obf.bDq.class, method = "fJR()Ldve;", at = At.WRAP_RETURN)
    public static dve master_fJR(@Return dve cell) { return wrap("fJR", cell); }

    @Hook(id = "master-Hnt", target = bitwig.obf.bDq.class, method = "Hnt()Ldve;", at = At.WRAP_RETURN)
    public static dve master_Hnt(@Return dve cell) { return wrap("Hnt", cell); }

    @Hook(id = "master-iHb", target = bitwig.obf.bDq.class, method = "iHb()Ldve;", at = At.WRAP_RETURN)
    public static dve master_iHb(@Return dve cell) { return wrap("iHb", cell); }

    @Hook(id = "master-nVd", target = bitwig.obf.bDq.class, method = "nVd()Ldve;", at = At.WRAP_RETURN)
    public static dve master_nVd(@Return dve cell) { return wrap("nVd", cell); }

    @Hook(id = "master-MRY", target = bitwig.obf.bDq.class, method = "MRY()Ldve;", at = At.WRAP_RETURN)
    public static dve master_MRY(@Return dve cell) { return wrap("MRY", cell); }

    @Hook(id = "master-wGc", target = bitwig.obf.bDq.class, method = "wGc()Ldve;", at = At.WRAP_RETURN)
    public static dve master_wGc(@Return dve cell) { return wrap("wGc", cell); }

    @Hook(id = "master-TtY", target = bitwig.obf.bDq.class, method = "TtY()Ldve;", at = At.WRAP_RETURN)
    public static dve master_TtY(@Return dve cell) { return wrap("TtY", cell); }

    @Hook(id = "master-eLY", target = bitwig.obf.bDq.class, method = "eLY()Ldve;", at = At.WRAP_RETURN)
    public static dve master_eLY(@Return dve cell) { return wrap("eLY", cell); }

    static dve wrap(String getter, dve cell) {
        if (cell == null) {
            if (logged.add(getter + "-null")) MOD.log("bDq." + getter + "() -> null");
            return null;
        }
        try {
            if (logged.add(getter)) {
                String size = "";
                try { com.bitwig.base.geom.SYo p = cell.iG_(); size = " preferred " + p.FzB() + "x" + p.IwV(); } catch (Throwable ignored) {}
                MOD.log("bDq." + getter + "() -> " + cell.getClass().getName() + size);
            }
            dWb row = dWb.IwV(0);  // horizontal, no gap
            row.HL(new ProbeCell(getter));
            row.HL(cell);
            return row;
        } catch (Throwable t) {
            MOD.log("wrap " + getter + " failed", t);
            return cell;
        }
    }
}
