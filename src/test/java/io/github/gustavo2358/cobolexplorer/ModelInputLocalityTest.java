package io.github.gustavo2358.cobolexplorer;

import org.junit.jupiter.api.Test;
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
}
