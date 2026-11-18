package com.cimm.mixerminipanels;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Calls a no-argument method by name, searching superclasses and interfaces; null when it can't. Lookups are cached
 *  per class and name (searching a class's methods is slow, and the views ask many times a second). */
final class Reflect {
    private Reflect() {}

    private static final ClassValue<ConcurrentHashMap<String, Optional<Method>>> CACHE = new ClassValue<>() {
        @Override protected ConcurrentHashMap<String, Optional<Method>> computeValue(Class<?> c) { return new ConcurrentHashMap<>(); }
    };

    static Object call(Object o, String name) {
        if (o == null) return null;
        Method m = method(o.getClass(), name);
        if (m == null) return null;
        try { return m.invoke(o); } catch (Throwable t) { return null; }
    }

    /** name: the 6.1.4 name; on 6.1.7 it is translated (Names). */
    static Method method(Class<?> c, String name) {
        return CACHE.get(c).computeIfAbsent(name, n -> {
            Method m = find(c, Names.method(n));
            if (m != null) try { m.setAccessible(true); } catch (Throwable t) { m = null; }
            return Optional.ofNullable(m);
        }).orElse(null);
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
