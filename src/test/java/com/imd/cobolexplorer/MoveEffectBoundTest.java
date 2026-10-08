package com.imd.cobolexplorer;

import com.fasterxml.jackson.databind.*;
import com.imd.cobolexplorer.semanticproduct.*;
import com.imd.cobolexplorer.semanticproduct.transport.SemanticProductJsonWriter;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MoveEffectBoundTest {
    static JsonNode effect(String data,String code)throws Exception {
        var port=CobolSemanticPort.open(StorageProductTest.state(data,code+"\nGOBACK."));
        var doc=new ObjectMapper().readTree(SemanticProductJsonWriter.serialize(port));
        assertEquals(1,doc.path("statementEffects").size());
        return doc.path("statementEffects").get(0);
    }
    @Test void currentDateWritesOnlyItsReceiver()throws Exception {
        var e=effect("01 TS PIC X(21).\n01 OTHER-PGM PIC X(8).","MOVE FUNCTION CURRENT-DATE TO TS.");
        assertEquals("MOVE_TARGETS",e.path("proof").asText());
        assertEquals("NONE",e.path("unknownWriteBound").asText());
        assertEquals(1,e.path("mayWrites").size());assertEquals(0,e.path("mustOverwrite").size());
        assertEquals("INPUT",e.path("environment").asText());
    }
    @Test void returnCodeWritesImplicitRuntimeStateWithoutApplicationMemory()throws Exception {
        var e=effect("01 OTHER-PGM PIC X(8).","MOVE 4 TO RETURN-CODE.");
        assertEquals("MOVE_TARGETS",e.path("proof").asText());
        assertEquals("NONE",e.path("unknownWriteBound").asText());assertEquals(0,e.path("mayWrites").size());
        assertEquals("OUTPUT",e.path("environment").asText());
    }
    @Test void pureTextFunctionKeepsReadAndAllReceivingIdentities()throws Exception {
        var e=effect("01 SRC PIC X(8).\n01 A PIC X(8).\n01 B PIC X(8).","MOVE FUNCTION UPPER-CASE(SRC) TO A B.");
        assertEquals(1,e.path("knownReads").size());assertEquals(2,e.path("mayWrites").size());
        assertEquals("NONE",e.path("unknownWriteBound").asText());
    }
    @Test void missingReceiverKeepsAnOpenWriteBound()throws Exception {
        var e=effect("01 OTHER-PGM PIC X(8).","MOVE FUNCTION CURRENT-DATE TO MISSING-TARGET.");
        assertEquals("ALL",e.path("unknownWriteBound").asText());
        assertEquals(0,e.path("mustOverwrite").size());
    }
    @Test void lowerCaseIntrinsicHasTheSameEffects()throws Exception {
        assertEquals(effect("01 TS PIC X(21).","MOVE FUNCTION CURRENT-DATE TO TS."),
            effect("01 TS PIC X(21).","move function current-date to TS."));
    }
    @Test void unknownFunctionDoesNotGetAPureIntrinsicProof()throws Exception {
        var port=CobolSemanticPort.open(StorageProductTest.state("01 A PIC X(8).","MOVE FUNCTION USER-FN TO A.\nGOBACK."));
        var doc=new ObjectMapper().readTree(SemanticProductJsonWriter.serialize(port));
        assertTrue(doc.path("statementEffects").isEmpty());
    }
}
