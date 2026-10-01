package io.github.gustavo2358.cobolexplorer;

import org.junit.jupiter.api.Test;
import java.util.*;
import static io.github.gustavo2358.cobolexplorer.FactLocalitySemantics.*;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.gustavo2358.cobolexplorer.FactDependencyLocalityTest.*;
import static io.github.gustavo2358.cobolexplorer.semanticproduct.FactDependencies.*;

class ModelInputLocalityTest {
    @Test void closedRealRecordAfterModelRetainsItsLocalIdentity() {
        var p=publish("COPY DFHAID.\n01 TARGET PIC X(8).\n01 SENTINEL PIC X.");
        assertTrue(known(p,"TARGET",FactKind.LOCAL_CELL));
        var g=p.factDependencies().orElseThrow();
        assertTrue(g.inputs().stream().anyMatch(i->i.kind().name().equals("MODEL_STORAGE")&&!i.available()));
        assertFalse(known(p,"DFHENTER",FactKind.LOCAL_CELL));
        assertFalse(known(p,"DFHENTER",FactKind.PHYSICAL_VIEW));
    }
    @Test void modelDoesNotChooseTheSectionOfRealDeclarations() {
        var p=publish("COPY DFHAID.\nLINKAGE SECTION.\n01 TARGET PIC X(8).\n01 SENTINEL PIC X.");
        assertTrue(p.dataDeclarations().stream().filter(x->x.canonicalName().equals("TARGET")).findFirst().orElseThrow().scalarText().isEmpty());
    }
    @Test void actualMissingPrefixRemainsUnavailableAlongsideModel() {
        for(var prefix:java.util.List.of("COPY UNKNOWN-DATA.\nCOPY DFHAID.","COPY DFHAID.\nCOPY UNKNOWN-DATA."))
            assertFalse(known(publish(prefix+"\n01 TARGET PIC X(8).\n01 SENTINEL PIC X."),"TARGET",FactKind.LOCAL_CELL));
    }
    @Test void aliasSharingModelStorageCannotBecomeExact() {
        var p=publish("COPY DFHAID.\n01 TARGET REDEFINES DFHAID PIC X(37).\n01 SENTINEL PIC X.");
        assertFalse(known(p,"TARGET",FactKind.LOCAL_CELL));
        var g=p.factDependencies().orElseThrow();
        var b=g.bindings().stream().filter(x->x.node().equals(node(p,"TARGET"))).findFirst().orElseThrow();
        assertTrue(b.cells().isEmpty());assertTrue(b.regions().isEmpty());
    }
    @Test void independentRealAliasesKeepTheirSharedRegionAfterModel() {
        var p=publish("COPY DFHAID.\n01 RECORD-A.\n05 TARGET PIC X(8).\n05 ALIAS-A REDEFINES TARGET PIC X(4).\n01 SENTINEL PIC X.");
        var g=p.factDependencies().orElseThrow();
        var a=g.bindings().stream().filter(x->x.node().equals(node(p,"TARGET"))).findFirst().orElseThrow();
        var b=g.bindings().stream().filter(x->x.node().equals(node(p,"ALIAS-A"))).findFirst().orElseThrow();
        assertEquals(a.region(),b.region());assertFalse(a.regions().isEmpty());
        assertFalse(known(p,"TARGET",FactKind.LOCAL_CELL));
    }
    @Test void realClosedRecordBeforeModelIsUnaffected() {
        var p=publish("01 TARGET PIC X(8).\n01 SENTINEL PIC X.\nCOPY DFHAID.");
        assertTrue(known(p,"TARGET",FactKind.LOCAL_CELL));
    }
    @Test void modelBoundaryAloneDoesNotCertifyThePreviousOpenRecord() {
        var p=publish("01 RECORD-A.\n05 TARGET PIC X(8).\nEXEC SQL INCLUDE SQLCA END-EXEC.");
        assertFalse(known(p,"TARGET",FactKind.LOCAL_CELL));
    }

    @Test void indexedModelOverlapsMatchStrictScanAcrossBoundariesAndNesting() {
        long line=1L<<32;
        var nodes=new ArrayList<>(List.of(
            new ModelNode(1,"outer",line,line*4),
            new ModelNode(2,"inner",line+3,line+8),
            new ModelNode(3,"inner",line+3,line+8),
            new ModelNode(4,"point",line+5,line+5),
            new ModelNode(5,"next",line+8,line*2),
            new ModelNode(6,"later",line*3+4,line*3+9)));
        var random=new Random(7301);
        for(int i=7;i<300;i++) {
            long start=line*(1+random.nextInt(4))+random.nextInt(12);
            nodes.add(new ModelNode(i,"region:"+i,start,start+random.nextInt(20)));
        }
        Collections.shuffle(nodes,random);
        var index=new ModelIntervals(nodes);
        var queries=new ArrayList<long[]>(List.of(new long[]{line,line*4},new long[]{line+8,line+9},
            new long[]{line+5,line+5},new long[]{line+8,line+8},new long[]{0,line},
            new long[]{line*5,line*6}));
        for(int i=0;i<500;i++) {
            long start=line*random.nextInt(6)+random.nextInt(12);
            queries.add(new long[]{start,start+random.nextInt(40)});
        }
        for(var query:queries) {
            // Independent oracle: the strict predicate used for source locality before indexing.
            var expected=nodes.stream().filter(n->n.start()<query[1]&&query[0]<n.end()).toList();
            var actual=index.overlapping(query[0],query[1]);
            assertEquals(expected.size(),actual.size(),"overlap multiplicity");
            assertEquals(new HashSet<>(expected),new HashSet<>(actual),Arrays.toString(query));
        }
        assertTrue(new ModelIntervals(List.of()).overlapping(0,Long.MAX_VALUE).isEmpty());
    }
}
