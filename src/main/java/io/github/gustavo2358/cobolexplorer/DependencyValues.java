package io.github.gustavo2358.cobolexplorer;

import java.util.*;
import java.util.function.Function;

/** Finite logical candidates and an independent unknown remainder. Bottom is
 * represented by absence of a reachable flow state, never by UNKNOWN. */
record DependencyValues(Set<String> values, boolean unknown) {
    static final DependencyValues UNKNOWN = new DependencyValues(Set.of(), true);
    DependencyValues { values = Set.copyOf(values); }
    static DependencyValues known(String value) { return new DependencyValues(Set.of(value), false); }
    DependencyValues open() { return unknown ? this : new DependencyValues(values, true); }
    DependencyValues join(DependencyValues other) {
        if (equals(other)) return this;
        var merged = new HashSet<>(values); merged.addAll(other.values);
        return new DependencyValues(merged, unknown || other.unknown);
    }
    DependencyValues map(Function<String,String> transform) {
        var out = new HashSet<String>(); boolean remainder = unknown;
        for (String value : values) {
            String result = transform.apply(value);
            if (result == null) remainder = true; else out.add(result);
        }
        return new DependencyValues(out, remainder);
    }
    DependencyValues fit(int length) {
        return length <= 0 ? this : map(s -> s.length() >= length ? s.substring(0,length) : s + " ".repeat(length-s.length()));
    }
}
