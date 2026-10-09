package com.imd.cobolexplorer;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.imd.cobolexplorer.DependencyFlow.*;
import static com.imd.cobolexplorer.DependencyRelevance.*;

class DependencyFlyweightTest {
    @Test void independentEqualStatesShareTheEntireImmutablePayload() {
        var pool=new DependencyFlyweight<State>();
        var first=new State(Map.of(1,DependencyValues.known("A")),Map.of(),Set.of());
        var second=new State(new HashMap<>(first.values()),Map.of(),Set.of());
        assertNotSame(first.values(),second.values());assertNotSame(first,second);
        assertSame(first,pool.retain(first));assertSame(first,pool.retain(second));
        var changed=first.withValues(DependencyEnvironment.copyOf(first.values()).with(1,DependencyValues.known("B")));
        assertNotSame(first,pool.retain(changed));assertEquals(DependencyValues.known("A"),first.get(1));
        assertEquals(DependencyValues.known("B"),changed.get(1));
    }
    @Test void everySemanticStateComponentRemainsDistinct() {
        var pool=new DependencyFlyweight<State>();
        var base=new State(Map.of(1,DependencyValues.known("A")),Map.of(),Set.of());pool.retain(base);
        var variants=List.of(
            new State(Map.of(1,DependencyValues.known("A").open()),Map.of(),Set.of()),
            new State(base.values(),Map.of("ABEND",Set.of("HANDLER")),Set.of()),
            new State(base.values(),Map.of(),Set.of("STATEMENT")),
            new State(base.values(),Map.of(),Set.of(),Map.of(1,new Parameter(1))),
            new State(base.values(),Map.of(),Set.of(),Map.of(),NO),
            new State(base.values(),Map.of(),Set.of(),Map.of(),YES,Map.of(new Fact("STATEMENT"),YES)));
        var retained=new HashSet<State>();retained.add(base);
        for(var variant:variants){assertSame(variant,pool.retain(variant));assertTrue(retained.add(variant));}
    }
    @Test void samePayloadDoesNotMergeCallerOrPositionObligations() {
        var pool=new DependencyFlyweight<State>();
        var input=pool.retain(new State(Map.of(),Map.of(),Set.of()));
        var another=pool.retain(new State(Map.of(),Map.of(),Set.of()));assertSame(input,another);
        assertNotEquals(new Location(1,"NODE"),new Location(2,"NODE"));
        assertNotEquals(new ReturnTo(1,"BINDING","END",input),new ReturnTo(2,"BINDING","END",another));
        assertNotEquals(new ReturnTo(1,"BINDING","END",input),new ReturnTo(1,"OTHER","END",another));
    }
    @Test void evictionAndSeparateAnalysesOnlyLoseSharing() throws Exception {
        var pool=new DependencyFlyweight<State>();var state=new State(Map.of(1,DependencyValues.UNKNOWN),Map.of(),Set.of());
        pool.retain(state);
        var entries=DependencyFlyweight.class.getDeclaredField("entries");entries.setAccessible(true);
        ((Map<?,?>)entries.get(pool)).clear();
        var replacement=new State(Map.of(1,DependencyValues.UNKNOWN),Map.of(),Set.of());
        assertSame(replacement,pool.retain(replacement));assertEquals(state,replacement);
        var independent=new State(Map.of(1,DependencyValues.UNKNOWN),Map.of(),Set.of());
        assertSame(independent,new DependencyFlyweight<State>().retain(independent));
    }
    @Test void equalHashCodesNeverSubstituteDifferentContent() {
        var pool=new DependencyFlyweight<String>();
        assertEquals("Aa".hashCode(),"BB".hashCode());assertEquals("Aa",pool.retain("Aa"));assertEquals("BB",pool.retain("BB"));
    }
}
