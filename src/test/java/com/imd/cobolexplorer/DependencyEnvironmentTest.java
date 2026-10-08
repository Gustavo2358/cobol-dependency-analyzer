package com.imd.cobolexplorer;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DependencyEnvironmentTest {
    @Test void mapContractAndFrozenStatesSurviveUpdatesAndRemovals() {
        var expected=new HashMap<Integer,DependencyValues>();
        for(int i=0;i<2000;i++)expected.put(i*3,DependencyValues.known("V"+i));
        var original=DependencyEnvironment.copyOf(expected);
        var builder=new DependencyEnvironment.Builder(original);
        for(int i=0;i<2000;i+=7)builder.put(i*3,DependencyValues.UNKNOWN);
        var first=DependencyEnvironment.copyOf(builder);
        for(int i=0;i<2000;i+=11)builder.remove(i*3);
        var second=DependencyEnvironment.copyOf(builder);
        assertEquals(expected,original);assertEquals(expected.hashCode(),original.hashCode());
        assertEquals(DependencyValues.UNKNOWN,first.get(0));assertNull(second.get(0));
        assertEquals(DependencyValues.known("V0"),original.get(0));
        var shuffled=new ArrayList<>(second.entrySet());Collections.shuffle(shuffled,new Random(1));
        var independent=new HashMap<Integer,DependencyValues>();shuffled.forEach(e->independent.put(e.getKey(),e.getValue()));
        assertEquals(second,DependencyEnvironment.copyOf(independent));assertEquals(independent,second);
        assertEquals(independent.hashCode(),second.hashCode());
    }
}
