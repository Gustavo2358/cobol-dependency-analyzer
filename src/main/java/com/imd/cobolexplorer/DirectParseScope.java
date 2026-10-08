package com.imd.cobolexplorer;

/** Lexically scoped dispatch for shared embedded-syntax predicates.
 * Thread-local ownership prevents cross-request interference and is restored on failure.
 */
public final class DirectParseScope implements AutoCloseable {
    private static final ThreadLocal<Boolean> ACTIVE=ThreadLocal.withInitial(()->false);
    private final boolean previous;
    private DirectParseScope(){previous=ACTIVE.get();ACTIVE.set(true);}
    static DirectParseScope enter(){return new DirectParseScope();}
    static boolean active(){return ACTIVE.get();}
    public static void requireLegacyAllowed(){if(active())throw new IllegalStateException("ANTLR parser instantiated inside the independent parser path");}
    @Override public void close(){if(previous)ACTIVE.set(true);else ACTIVE.remove();}
}
