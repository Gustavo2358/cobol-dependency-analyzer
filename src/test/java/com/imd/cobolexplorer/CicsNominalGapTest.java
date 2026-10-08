package com.imd.cobolexplorer;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CicsNominalGapTest {
    private static JsonNode publish(String data, String commands) throws Exception {
        var source = ScalarMoveCheckpoint4ATest.program(data, commands + "\nGOBACK.");
        var analysis = AstBoundaryTestSupport.analyze(source, "nominal-cics.cbl");
        return CicsAbendContractTest.json(EofUnitBoundaryTest.publish(analysis, 0, StorageLayoutSemantics.Profile.UNSPECIFIED));
    }
    private static List<JsonNode> references(JsonNode node) {
        var result = new ArrayList<JsonNode>();
        if (node.has("binding")) result.add(node);
        for (var child : node) result.addAll(references(child));
        return result;
    }
    private static List<JsonNode> nominalGaps(JsonNode sp, JsonNode statement) {
        var result = new ArrayList<JsonNode>();
        for (var gap : sp.path("gaps"))
            if (gap.path("statement").equals(statement.path("header").path("id"))
                    && gap.path("scope").asText().equals("NOMINAL_BINDING")) result.add(gap);
        return result;
    }
    private static void incomplete(JsonNode sp, String status) {
        int count = 0;
        for (var statement : sp.path("statements")) for (var ref : references(statement)) {
            assertEquals(status, ref.path("binding").path("status").asText());
            assertTrue(ref.path("binding").path("selected").isNull());
            assertNotEquals("MODELED", statement.path("header").path("coverage").asText());
            assertTrue(nominalGaps(sp, statement).stream().anyMatch(g -> g.path("provenance").equals(ref.path("provenance"))),
                "each incomplete reference retains its own localized gap provenance");
            count++;
        }
        assertTrue(count > 0, "fixture must exercise published DATA bindings");
    }
    @Test void unresolvedProgramTargetAndOptionsKeepLiteralAndNominalEvidence() throws Exception {
        for (var command : List.of("LINK PROGRAM('TARGET')\nCOMMAREA(UNKNOWN-AREA) LENGTH(1)",
                "XCTL PROGRAM(UNKNOWN-PROGRAM)", "LINK PROGRAM('TARGET')\nCOMMAREA(UNKNOWN-AREA) LENGTH(UNKNOWN-LENGTH)\nRESP(UNKNOWN-RESPONSE)")) {
            var sp = publish("", "EXEC CICS " + command + " END-EXEC.");
            incomplete(sp, "UNRESOLVED");
            if (command.contains("'TARGET'")) assertEquals("TARGET", sp.path("statements").get(0).path("target").path("text").asText());
        }
    }
    @Test void ambiguousAreaKeepsCandidatesWithoutSelectingOne() throws Exception {
        var sp = publish("01 A.\n05 WS-AREA PIC X.\n01 B.\n05 WS-AREA PIC X.",
            "EXEC CICS LINK PROGRAM('TARGET')\nCOMMAREA(WS-AREA) LENGTH(1) END-EXEC.");
        incomplete(sp, "AMBIGUOUS");
        var refs = references(sp.path("statements").get(0));
        assertEquals(2, refs.get(0).path("binding").path("candidates").size());
    }
    @Test void missingCopyKeepsNominalAndInputGapsInsteadOfAFalseResolution() throws Exception {
        var sp = publish("COPY ABSENT-MEMBER.", "EXEC CICS LINK PROGRAM('TARGET')\nCOMMAREA(WS-AREA) LENGTH(1) END-EXEC.");
        incomplete(sp, "UNRESOLVED");
        assertTrue(sp.path("factDependencies").toString().contains("MISSING_COPY"));
    }
    @Test void cicsFileTargetAndReadWriteOptionsUseTheSameGapContract() throws Exception {
        for (var command : List.of("READ FILE('REALFILE')\nINTO(BUF) RIDFLD(KEY)",
                "READ FILE(FILE-NAME)\nINTO(BUF) RIDFLD(KEY)", "WRITE FILE('REALFILE')\nFROM(BUF) RIDFLD(KEY)",
                "INQUIRE FILE(FILE-NAME) NEXT\nOPENSTATUS(STATUS-FIELD)"))
            incomplete(publish("", "EXEC CICS " + command + " END-EXEC."), "UNRESOLVED");
    }
    @Test void neighboringTypedCommandAndHandlerContractsRemainCovered() throws Exception {
        for (var command : List.of("SEND TEXT FROM(WS-AREA) LENGTH(1)", "HANDLE ABEND PROGRAM(PGM)"))
            incomplete(publish("", "EXEC CICS " + command + " END-EXEC."), "UNRESOLVED");
    }
    @Test void resolvedStatementsDoNotAcquireOtherStatementsNominalGaps() throws Exception {
        var sp = publish("01 WS-AREA PIC X.", "EXEC CICS LINK PROGRAM('FIRST')\nCOMMAREA(UNKNOWN-AREA) LENGTH(1) END-EXEC.\n"
            + "EXEC CICS LINK PROGRAM('SECOND')\nCOMMAREA(WS-AREA) LENGTH(1) END-EXEC.");
        var first = sp.path("statements").get(0); var second = sp.path("statements").get(1);
        assertFalse(nominalGaps(sp, first).isEmpty());
        assertTrue(nominalGaps(sp, second).isEmpty());
        for (var ref : references(second)) assertEquals("RESOLVED", ref.path("binding").path("status").asText());
    }
}
