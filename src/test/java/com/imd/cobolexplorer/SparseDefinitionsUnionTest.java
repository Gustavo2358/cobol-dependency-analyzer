package com.imd.cobolexplorer;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SparseDefinitionsUnionTest {
    private DependencyValues union(DependencyValues seed,List<DependencyValues> inputs){
        var component=new SparseDefinitions.Component();component.seed=seed;
        var parents=new ArrayList<SparseDefinitions.Component>();
        for(var input:inputs){
            component.parents.add(parents.size());
            var parent=new SparseDefinitions.Component();parent.value=input;parents.add(parent);
        }
        return SparseDefinitions.unionParents(component,parents);
    }
    @Test void emptyAndCoveredInputsReuseImmutableValues(){
        var a=DependencyValues.known("A");
        var ab=new DependencyValues(Set.of("A","B"),false);
        assertSame(SparseDefinitions.BOTTOM,union(SparseDefinitions.BOTTOM,List.of()));
        assertSame(a,union(a,List.of(SparseDefinitions.BOTTOM,a)));
        assertSame(ab,union(a,List.of(ab,a)));
        assertEquals(Set.of("A"),a.values());
        assertThrows(UnsupportedOperationException.class,()->ab.values().add("C"));
    }
    @Test void unknownSurvivesCoveredInputsAndReplacementBySuperset(){
        var a=DependencyValues.known("A");
        var ab=new DependencyValues(Set.of("A","B"),false);
        assertEquals(new DependencyValues(Set.of("A","B"),true),union(a.open(),List.of(ab,a)));
        assertEquals(a.open(),union(a,List.of(DependencyValues.UNKNOWN,a)));
        assertEquals(DependencyValues.UNKNOWN,union(SparseDefinitions.BOTTOM,List.of(DependencyValues.UNKNOWN)));
    }
    @Test void allIncomingFactsArePreservedRegardlessOfOrder(){
        var inputs=new ArrayList<>(List.of(DependencyValues.known("A"),
                new DependencyValues(Set.of("A","B"),false),DependencyValues.known("C"),
                DependencyValues.UNKNOWN,SparseDefinitions.BOTTOM));
        var expected=new DependencyValues(Set.of("SEED","A","B","C"),true);
        for(int i=0;i<30;i++){
            Collections.shuffle(inputs,new Random(i));
            assertEquals(expected,union(DependencyValues.known("SEED"),inputs));
        }
    }
    @Test void highFanInRetainsEveryCandidateAndIndependentRemainder(){
        var inputs=new ArrayList<DependencyValues>();var expected=new HashSet<String>();
        for(int i=0;i<4096;i++){String name=String.format("PGM%05d",i);expected.add(name);inputs.add(DependencyValues.known(name));}
        inputs.add(DependencyValues.UNKNOWN);
        var result=union(SparseDefinitions.BOTTOM,inputs);
        assertEquals(expected,result.values());assertTrue(result.unknown());
        assertEquals(1,inputs.get(0).values().size());
    }
}
