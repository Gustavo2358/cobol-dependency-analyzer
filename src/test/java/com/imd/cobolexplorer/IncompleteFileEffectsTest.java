package com.imd.cobolexplorer;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.imd.cobolexplorer.FileMemoryEffectsTest.fixture;
import static com.imd.cobolexplorer.FileEffectsContractTest.*;

class IncompleteFileEffectsTest {
    static JsonNode plan(String select,String fd,String ws,String code)throws Exception {
        return effect(publish(fixture(select,fd,ws,code)));
    }
    static JsonNode success(JsonNode p){return p.path("outcomes").get(0).path("steps");}
    static JsonNode role(JsonNode steps,String role){for(var s:steps)if(s.path("role").asText().equals(role))return s;throw new AssertionError("missing "+role+": "+steps);}
    @Test void fdWithoutSelectRetainsRecordAndSuccessOnlyInto()throws Exception {
        var p=plan("","FD F.\n01 REC PIC X(8).","01 DEST PIC X(8).","READ F INTO DEST.");
        assertEquals("PARTIAL",p.path("availability").asText());
        assertEquals("MAY_UNKNOWN",role(success(p),"RECORD").path("kind").asText());
        assertEquals("MUST_UNKNOWN",role(success(p),"INTO").path("kind").asText());
        assertFalse(p.path("unknownWriteBound").asBoolean(),"known receiver identities remain bounded without SELECT");
        for(var outcome:p.path("outcomes"))if(!outcome.path("outcome").asText().equals("SUCCESS"))
            for(var step:outcome.path("steps"))assertNotEquals("INTO",step.path("role").asText());
    }
    @Test void selectWithoutFdRetainsStatusAlongsideUnknownBuffer()throws Exception {
        var p=plan("SELECT F ASSIGN TO INDD FILE STATUS FS.","","01 FS PIC XX.","READ F.");
        assertTrue(p.path("unknownWriteBound").asBoolean());
        assertEquals("MAY_UNKNOWN",role(success(p),"FILE_STATUS").path("kind").asText(),"missing record owner cannot certify a strong effect");
    }
    @Test void knownFromOperandsWithoutFileOwnerRemainMayBeforeIo()throws Exception {
        for(var verb:new String[]{"WRITE","REWRITE","RELEASE"}) {
            var p=plan("","","01 REC PIC X(8).\n01 SRC PIC X(8).",verb+" REC FROM SRC.");
            assertEquals(1,p.path("before").size());var move=p.path("before").get(0);
            assertEquals("MAY_UNKNOWN",move.path("kind").asText());
            assertFalse(move.path("source").isNull());assertFalse(move.path("destination").path("data").isNull());
            assertFalse(p.path("unknownReadBound").asBoolean(),"explicit source and record are known");
        }
    }
    @Test void missingDestinationsAndFromSourceHaveOpenBounds()throws Exception {
        var from=plan("","","01 SRC PIC X(8).","WRITE MISSING FROM SRC.");
        assertTrue(from.path("unknownWriteBound").asBoolean());
        assertTrue(from.path("before").isEmpty());
        var into=plan("SELECT F ASSIGN TO INDD.","FD F.\n01 REC PIC X(8).","","READ F INTO MISSING.");
        assertTrue(into.path("unknownWriteBound").asBoolean());role(success(into),"RECORD");
        var source=plan("SELECT F ASSIGN TO OUTDD.","FD F.\n01 REC PIC X(8).","","WRITE REC FROM MISSING.");
        assertTrue(source.path("unknownReadBound").asBoolean());assertEquals(1,source.path("before").size());
    }
    @Test void completeFromAndProfileGapsKeepIndependentProofs()throws Exception {
        for(var select:new String[]{"SELECT F ASSIGN TO OUTDD.","SELECT F ASSIGN TO DYNAMIC DEST."}) {
            var p=plan(select,"FD F.\n01 REC PIC X(8).","01 SRC PIC X(8).\n01 DEST PIC X(8).","WRITE REC FROM SRC.");
            assertEquals("COPY_BYTES",p.path("before").get(0).path("kind").asText());
            assertFalse(p.path("unknownWriteBound").asBoolean());assertFalse(p.path("unknownReadBound").asBoolean());
        }
    }
    @Test void wrongRecordKindCannotAuthorizeFromKill()throws Exception {
        var p=plan("SELECT F ASSIGN TO INDD.","FD F.\n01 REC PIC X(8).","01 SRC PIC X(8).","RELEASE REC FROM SRC.");
        assertEquals("MAY_UNKNOWN",p.path("before").get(0).path("kind").asText());
        assertTrue(p.path("unknownWriteBound").asBoolean());
    }
    @Test void mergeWithMissingControlRetainsItsQualificationGap()throws Exception {
        var f=fixture("",FileSortContractTest.FILES,"","MERGE S ON ASCENDING KEY SK USING A B GIVING C.");
        var p=publish(f);var plan=p.path("fileInventory").path("sortPlans").get(0);
        assertEquals("PARTIAL",plan.path("availability").asText());
        assertTrue(plan.path("gapCodes").toString().contains("FILE_SORT_BINDING_NOT_PROVEN"));
    }

}
