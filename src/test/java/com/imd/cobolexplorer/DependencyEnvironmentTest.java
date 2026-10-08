package com.imd.cobolexplorer;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DependencyEnvironmentTest {
    @Test void joinsMatchIndependentPointwiseUnionAndReuseSharedBranches() throws Exception {
        var random=new Random(29);var first=new HashMap<Integer,DependencyValues>();var second=new HashMap<Integer,DependencyValues>();
        for(int i=-300;i<300;i++) {
            if(random.nextBoolean())first.put(i,random.nextBoolean()?DependencyValues.UNKNOWN:DependencyValues.known("A"));
            if(random.nextBoolean())second.put(i,random.nextBoolean()?DependencyValues.UNKNOWN:DependencyValues.known("B"));
        }
        var a=DependencyEnvironment.copyOf(first);var b=DependencyEnvironment.copyOf(second);
        var expected=new HashMap<>(first);second.forEach((k,v)->expected.merge(k,v,DependencyValues::join));
        assertEquals(expected,a.join(b));assertEquals(a.join(b),b.join(a));assertEquals(expected.hashCode(),a.join(b).hashCode());
        assertSame(a,a.join(a));assertEquals(first,a);assertEquals(second,b);
        var expanded=a.with(1001,DependencyValues.known("C"));var joined=a.join(expanded);
        var shared=nodes(a);shared.retainAll(nodes(joined));
        assertTrue(shared.size()>a.size()-20,"Join must skip unchanged branches");
        assertEquals(expanded,joined);
        var c=a.with(-1001,DependencyValues.known("D"));assertEquals(a.join(b).join(c),a.join(b.join(c)));
    }
    @Test void projectionRetainsExactEntriesIncludingExplicitUnknown() {
        var values=Map.of(1,DependencyValues.UNKNOWN,2,DependencyValues.known("X"),3,DependencyValues.known("Y"));
        var source=DependencyEnvironment.copyOf(values);
        var projection=new DependencyEnvironment.Projection(Set.of(1,2,4));
        var selected=projection.apply(source);
        assertEquals(Map.of(1,DependencyValues.UNKNOWN,2,DependencyValues.known("X")),selected);
        assertTrue(selected.containsKey(1));assertFalse(selected.containsKey(4));
        assertEquals(values,source);
        assertEquals(selected.hashCode(),DependencyEnvironment.copyOf(new HashMap<>(selected)).hashCode());
        assertEquals(selected,DependencyEnvironment.copyOf(new HashMap<>(selected)));
        assertTrue(new DependencyEnvironment.Projection(Set.of()).apply(source).isEmpty());
        assertSame(source,new DependencyEnvironment.Projection(values.keySet()).apply(source));
    }
    @Test void projectionSharesBranchesAcrossDifferentInputs() throws Exception {
        var values=new HashMap<Integer,DependencyValues>();var keep=new HashSet<Integer>();
        for(int i=0;i<1000;i++){values.put(i,DependencyValues.UNKNOWN);if(i%10!=0)keep.add(i);}
        var source=DependencyEnvironment.copyOf(values);var projection=new DependencyEnvironment.Projection(keep);
        var first=projection.apply(source);var changed=projection.apply(source.with(501,DependencyValues.known("X")));
        assertEquals(DependencyValues.UNKNOWN,first.get(501));assertEquals(DependencyValues.known("X"),changed.get(501));
        var originalNodes=nodes(first);var newNodes=nodes(changed);
        newNodes.retainAll(originalNodes);
        assertTrue(newNodes.size()>850,"Unchanged branches must be shared, not rebuilt");
        assertSame(root(first),root(projection.apply(source)),"Repeated projection must reuse its tree");
    }
    @Test void projectionCacheEvictionDoesNotChangeTheMap() throws Exception {
        var source=DependencyEnvironment.copyOf(Map.of(1,DependencyValues.UNKNOWN,2,DependencyValues.known("X"),3,DependencyValues.known("Y")));
        var projection=new DependencyEnvironment.Projection(Set.of(1,3));var first=projection.apply(source);
        var cache=DependencyEnvironment.Projection.class.getDeclaredField("projected");cache.setAccessible(true);
        ((Map<?,?>)cache.get(projection)).clear();
        assertEquals(first,projection.apply(source));
    }
    @Test void sparseProjectionDoesNotMemoizeUnrelatedBranches() throws Exception {
        var values=new HashMap<Integer,DependencyValues>();for(int i=0;i<10000;i++)values.put(i,DependencyValues.UNKNOWN);
        var source=DependencyEnvironment.copyOf(values);var projection=new DependencyEnvironment.Projection(Set.of(5001));
        assertEquals(Map.of(5001,DependencyValues.UNKNOWN),projection.apply(source));
        var cache=DependencyEnvironment.Projection.class.getDeclaredField("projected");cache.setAccessible(true);
        assertTrue(((Map<?,?>)cache.get(projection)).size()<40,"Sparse operands must not retain a cache entry per unrelated field");
        var extremes=DependencyEnvironment.copyOf(Map.of(Integer.MIN_VALUE,DependencyValues.UNKNOWN,Integer.MAX_VALUE,DependencyValues.known("X")));
        assertEquals(Map.of(Integer.MAX_VALUE,DependencyValues.known("X")),new DependencyEnvironment.Projection(Set.of(Integer.MAX_VALUE)).apply(extremes));
    }
    @Test void projectedUpdatesAndRemovalsMatchIndependentMapFiltering() {
        var random=new Random(17);var expected=new HashMap<Integer,DependencyValues>();var keep=new HashSet<Integer>();
        for(int i=-100;i<100;i++)if(random.nextBoolean())keep.add(i);
        var projection=new DependencyEnvironment.Projection(keep);var source=DependencyEnvironment.copyOf(expected);
        for(int i=0;i<500;i++) {
            int key=random.nextInt(200)-100;
            if(random.nextInt(4)==0){expected.remove(key);source=source.without(key);}
            else{var value=random.nextBoolean()?DependencyValues.UNKNOWN:DependencyValues.known("V"+i);expected.put(key,value);source=source.with(key,value);}
            var selected=new HashMap<Integer,DependencyValues>();expected.forEach((k,v)->{if(keep.contains(k))selected.put(k,v);});
            var actual=projection.apply(source);
            assertEquals(selected,actual);assertEquals(selected.hashCode(),actual.hashCode());
            assertEquals(DependencyEnvironment.copyOf(selected),actual);
        }
    }
    private static Object root(DependencyEnvironment environment) throws Exception {
        var field=DependencyEnvironment.class.getDeclaredField("root");field.setAccessible(true);return field.get(environment);
    }
    private static Set<Object> nodes(DependencyEnvironment environment) throws Exception {
        Set<Object> result=Collections.newSetFromMap(new IdentityHashMap<>());var pending=new ArrayDeque<Object>();
        var root=root(environment);if(root!=null)pending.add(root);
        while(!pending.isEmpty()) {
            var node=pending.removeFirst();if(!result.add(node))continue;
            for(var name:List.of("left","right")){var field=node.getClass().getDeclaredField(name);field.setAccessible(true);var child=field.get(node);if(child!=null)pending.add(child);}
        }
        return result;
    }
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
