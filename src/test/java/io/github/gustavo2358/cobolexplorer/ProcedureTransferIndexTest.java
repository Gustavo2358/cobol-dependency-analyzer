package io.github.gustavo2358.cobolexplorer;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProcedureTransferIndexTest {
    private static boolean scalar(List<ProcedureTransferIndex.Transfer> transfers,Set<Integer> unclosed,Set<Integer> members) {
        for(int source:unclosed)if(!members.contains(source))return false;
        for(var edge:transfers)if(!members.contains(edge.source())&&members.contains(edge.destination()))return false;
        return true;
    }
    @Test void typedIncomingQueriesMatchScalarEdgesAndUnclosedTransfers() {
        var random=new Random(181053);
        for(int round=0;round<100;round++) {
            var transfers=new ArrayList<ProcedureTransferIndex.Transfer>();var unclosed=new HashSet<Integer>();
            for(int source=0;source<40;source++) {
                for(int i=0;i<3;i++)if(random.nextBoolean())transfers.add(new ProcedureTransferIndex.Transfer(source,random.nextInt(40)));
                if(random.nextInt(15)==0)unclosed.add(source);
            }
            Collections.shuffle(transfers,random);var index=new ProcedureTransferIndex(transfers,unclosed);
            for(int query=0;query<30;query++) {
                var members=new HashSet<Integer>();for(int i=0;i<40;i++)if(random.nextBoolean())members.add(i);
                var actual=index.query(members);assertEquals(scalar(transfers,unclosed,members),actual.excluded());
                assertTrue(actual.edgeVisits()<=transfers.stream().filter(e->members.contains(e.destination())).count());
            }
        }
        var edges=new ArrayList<ProcedureTransferIndex.Transfer>();
        for(int i=0;i<20000;i++)edges.add(new ProcedureTransferIndex.Transfer(i,i));
        var index=new ProcedureTransferIndex(edges,Set.of());
        assertEquals(new ProcedureTransferIndex.Query(true,1),index.query(Set.of(0)));
        edges.clear();assertEquals(new ProcedureTransferIndex.Query(true,1),index.query(Set.of(0)));
    }
    @Test void overlapFlagsMatchIndependentPairwiseSetsIncludingEqualDuplicates() {
        var random=new Random(77189);
        for(int round=0;round<200;round++) {
            var ranges=new ArrayList<Set<Integer>>();
            for(int i=0;i<30;i++){var members=new HashSet<Integer>();for(int n=0;n<40;n++)if(random.nextInt(12)==0)members.add(n);ranges.add(Set.copyOf(members));}
            ranges.add(ranges.get(0));ranges.add(Set.of());
            var expected=new HashSet<Set<Integer>>();
            for(var a:ranges)for(var b:ranges)if(!a.equals(b))for(int member:a)if(b.contains(member)){expected.add(a);expected.add(b);break;}
            Collections.shuffle(ranges,random);assertEquals(expected,ProcedureTransferIndex.overlapping(ranges));
        }
    }
}
