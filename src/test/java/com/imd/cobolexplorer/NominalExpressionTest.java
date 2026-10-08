package com.imd.cobolexplorer;
import org.junit.jupiter.api.Test;
import com.imd.cobolexplorer.semanticproduct.*;
import static org.junit.jupiter.api.Assertions.*;
class NominalExpressionTest {
    @Test void typedFunctionsProduceGenericExpressionsWithoutRuntimeEvaluation() {
        var p=CobolSemanticPort.open(StorageProductTest.state("01 SRC PIC X(12).\n01 DEST PIC X(8).",
            "MOVE FUNCTION UPPER-CASE(FUNCTION TRIM(SRC)) TO DEST.\nCALL DEST.\nGOBACK."));
        var values=p.nominalValues().orElseThrow();assertEquals("NOMINAL_TEXT_SOURCE_V3",values.authority());
        var term=values.assignments().get(0).source();assertEquals("UPPER_ASCII",term.kind());
        assertEquals("TRIM_SPACES",term.arguments().get(0).kind());assertEquals("READ",term.arguments().get(0).arguments().get(0).kind());
    }
    @Test void trimDirectionsKeepFollowingStatementsAndSelectTypedOperators() {
        for (var direction: java.util.List.of("LEADING", "TRAILING")) {
            var source=ScalarMoveCheckpoint4ATest.program("01 SRC PIC X(12).\n01 DEST PIC X(8).",
                "MOVE FUNCTION TRIM(SRC "+direction+") TO DEST.\nCALL DEST.\nGOBACK.")
                .lines().map(line->"       "+line).collect(java.util.stream.Collectors.joining("\n","","\n"));
            AstBoundaryTestSupport.analyze(source,"trim-"+direction+".cbl");
            var p=CobolSemanticPort.open(StorageProductTest.state("01 SRC PIC X(12).\n01 DEST PIC X(8).",
                "MOVE FUNCTION TRIM(SRC "+direction+") TO DEST.\nCALL DEST.\nGOBACK."));
            var values=p.nominalValues().orElseThrow();
            assertEquals("TRIM_"+direction+"_SPACES",values.assignments().get(0).source().kind());
            assertEquals(1,values.queries().size());
        }
    }
    @Test void unknownFunctionKeepsUnknownValue() {
        var p=CobolSemanticPort.open(StorageProductTest.state("01 DEST PIC X(8).",
            "MOVE FUNCTION USER-FN TO DEST.\nCALL DEST.\nGOBACK."));
        assertEquals("UNKNOWN",p.nominalValues().orElseThrow().assignments().get(0).source().kind());
    }
}
