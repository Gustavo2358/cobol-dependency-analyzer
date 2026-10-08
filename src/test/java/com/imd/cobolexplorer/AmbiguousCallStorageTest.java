package com.imd.cobolexplorer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.imd.cobolexplorer.DeclarativeValueInferenceTest.*;

class AmbiguousCallStorageTest {
    static final String DATA="01 AREA-A.\n05 PGM PIC X(8) VALUE 'PROGA'.\n01 AREA-B.\n05 PGM PIC X(8) VALUE 'PROGB'.\n77 IDX PIC 9.\n";
    @Test void ambiguousReadRetainsCanonicalWholeViewsWithoutSelectingBinding() {
        var p=product(DATA,"CALL PGM.");
        var r=(com.imd.cobolexplorer.semanticproduct.CobolSemanticProduct.DataReference)p.calls().get(0).target();
        assertTrue(r.binding().selected().isEmpty());assertEquals(2,r.regionalAlternatives().size());
        assertEquals(2,r.regionalAlternatives().stream().map(a->a.view()).distinct().count());
    }
    @Test void dynamicSliceAndSubscriptNeverInventWholeItemCandidates() {
        for(String ref:java.util.List.of("PGM(IDX:1)","PGM(IDX)")) {
            var r=(com.imd.cobolexplorer.semanticproduct.CobolSemanticProduct.DataReference)product(DATA,"CALL "+ref+".").calls().get(0).target();
            assertTrue(r.regionalAlternatives().isEmpty());
        }
    }
}
