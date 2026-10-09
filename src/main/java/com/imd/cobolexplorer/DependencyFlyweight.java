package com.imd.cobolexplorer;

import java.lang.ref.WeakReference;
import java.util.WeakHashMap;

/** Analysis-local sharing of equal immutable payloads. Both sides are weak:
 * an obsolete payload must not survive solely because it was interned.
 * Eviction only loses sharing; it never changes the payload or its equality.
 * Callers must never mutate a retained value. This pool is single-threaded. */
final class DependencyFlyweight<T> {
    private final WeakHashMap<T,WeakReference<T>> entries=new WeakHashMap<>();
    T retain(T value) {
        var reference=entries.get(value);var existing=reference==null?null:reference.get();
        if(existing!=null)return existing;
        entries.put(value,new WeakReference<>(value));return value;
    }
}
