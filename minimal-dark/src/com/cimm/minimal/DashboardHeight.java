package com.cimm.minimal;

/**
 * The Dashboard box's height: what its content asks for (as Bitwig's own kn), but no more than a share of the window's
 * height, so the box sits centred with space above and below instead of running from the top to the bottom.
 * (A size rule: svm's three methods give the minimum, preferred and maximum size from the content's sizes, ruA.)
 */
final class DashboardHeight implements bitwig.obf.svm {
    static volatile double share = 0.78;
    private final bitwig.obf.dve box;

    DashboardHeight(bitwig.obf.dve box) { this.box = box; }

    private long cap() {
        try {
            bitwig.obf.dve root = box;
            for (bitwig.obf.dve p = box.RbC(); p != null; p = p.RbC()) root = p;
            long h = root.yiX();
            return h > 0 ? (long) (h * share) : Long.MAX_VALUE;
        } catch (Throwable t) { return Long.MAX_VALUE; }
    }

    @Override public long Hnt(bitwig.obf.ruA r) { return Math.min(r.Hnt(), cap()); }
    @Override public long MRY(bitwig.obf.ruA r) { return Math.min(r.cfM(), cap()); }
    @Override public long cfM(bitwig.obf.ruA r) { return Math.min(r.MRY(), cap()); }
}
