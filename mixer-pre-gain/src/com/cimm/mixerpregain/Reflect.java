package com.cimm.mixerpregain;

import java.lang.reflect.Method;

/** Calls a no-argument method by name, searching superclasses and interfaces; null when it can't. */
final class Reflect {
    private Reflect() {}

    static Object call(Object o, String name) {
        if (o == null) return null;
        try {
            Method m = find(o.getClass(), name);
            if (m == null) return null;
            m.setAccessible(true);
            return m.invoke(o);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Method find(Class<?> c, String name) {
        for (Class<?> k = c; k != null; k = k.getSuperclass()) {
            for (Method m : k.getDeclaredMethods()) if (m.getName().equals(name) && m.getParameterCount() == 0) return m;
            for (Class<?> i : k.getInterfaces()) {
                Method m = find(i, name);
                if (m != null) return m;
            }
        }
        return null;
    }
}
