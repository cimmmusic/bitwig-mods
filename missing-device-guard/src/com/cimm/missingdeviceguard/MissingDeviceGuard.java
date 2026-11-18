package com.cimm.missingdeviceguard;

import bitwig.obf.uMW; // a device's saved contents (as read from the project)
import bml.api.At;
import bml.api.Bml;
import bml.api.Hook;
import com.bitwig.flt.device.Wkj; // a device's identity: UUID + version
import com.bitwig.flt.document.core.master.device.AxS; // a native device in a project

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Missing Device Guard.
 *
 * AxS.AVR() returns a device's identity: from its loaded definition, else from its saved contents. When the
 * device isn't installed the definition never loads ("Device not found"), and for some instances the saved
 * contents are gone too, so AVR() calls E1b() on null and Bitwig crashes (seen on 6.1.7 when a project holds
 * VU Meter without the device registered). Here that call gets a stand-in identity instead.
 */
public final class MissingDeviceGuard {
    static final Bml.Mod MOD = Bml.mod("missing-device-guard");
    /** Stand-in identity: a fixed UUID that no real device has, so lookups by it just fail as "not found". */
    public static final UUID MISSING = UUID.nameUUIDFromBytes("cimm.missing-device-guard.missing".getBytes());
    private static final Set<String> logged = ConcurrentHashMap.newKeySet();

    private MissingDeviceGuard() {}

    @Hook(id = "device-identity", target = AxS.class, method = "AVR()Lcom/bitwig/flt/device/Wkj;",
          at = At.REPLACE_CALL, callOwner = uMW.class, call = "E1b()Lcom/bitwig/flt/device/Wkj;", occurrence = 1)
    public static Wkj identity(uMW contents) {
        if (contents != null) return contents.E1b();
        try {
            StackTraceElement[] st = new Throwable().getStackTrace();
            StringBuilder where = new StringBuilder();
            for (int i = 1; i < Math.min(st.length, 7); i++) where.append(i > 1 ? " <- " : "").append(st[i]);
            if (logged.add(where.toString())) MOD.log("missing device: stand-in identity given, asked from " + where);
        } catch (Throwable ignored) {}
        return new Wkj(MISSING, 1);
    }
}
