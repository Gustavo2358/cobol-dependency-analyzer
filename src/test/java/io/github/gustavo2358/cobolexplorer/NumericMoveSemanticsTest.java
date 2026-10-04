package io.github.gustavo2358.cobolexplorer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.*;
class NumericMoveSemanticsTest {
    private JsonNode publish(String data,String procedure) throws Exception {
        var a=AstBoundaryTestSupport.analyze(ScalarMoveCheckpoint4ATest.program(data,procedure),"numeric.cbl");
        return new ObjectMapper().readTree(io.github.gustavo2358.cobolexplorer.semanticproduct.transport.SemanticProductJsonWriter.serialize(EofUnitBoundaryTest.publish(a,0,StorageLayoutSemantics.Profile.UNSPECIFIED)));
    }
    @Test void logicalIntegersUseLocalProofsInsideGroupsAndWithValue() throws Exception {
        var p=publish("01 REC-A.\n05 COUNT-A PIC 99 VALUE 12.\n05 COUNT-B PIC 9(4).", "MOVE 12 TO COUNT-A.\nMOVE COUNT-A OF REC-A TO COUNT-B.\nGOBACK.");
        assertEquals("2.66.0",p.path("contractVersion").asText());
        int transfers=0;for(var s:p.path("statements"))transfers+=s.path("numericTransfers").size();
        assertEquals(2,transfers);
        assertFalse(p.path("gaps").toString().contains("LITERAL_KIND_NOT_PUBLISHED"));
        assertFalse(p.path("gaps").toString().contains("MOVE_IDENTITY_NOT_PROVEN"));
    }
    @Test void numericLiteralPublicationUsesExplicitReceivingConversion() throws Exception {
        var p=publish("01 SMALL-A PIC 99.\n01 PACKED-A PIC 99 COMP-3.","MOVE 123 TO SMALL-A.\nMOVE -1 TO SMALL-A.\nMOVE 1.5 TO SMALL-A.\nMOVE 1 TO PACKED-A.\nGOBACK.");
        int numeric=0,transfers=0;for(var s:p.path("statements")){if(s.path("source").path("kind").asText().equals("NUMERIC"))numeric++;transfers+=s.path("numericTransfers").size();}
        assertEquals(4,numeric);assertEquals(4,transfers);
        assertFalse(p.path("gaps").toString().contains("MOVE_IDENTITY_NOT_PROVEN"));
        assertFalse(p.path("gaps").toString().contains("LITERAL_KIND_NOT_PUBLISHED"));
    }
    @Test void zeroAndMultipleReceiversKeepWrittenOrder() throws Exception {
        var p=publish("01 COUNT-A PIC 99.\n01 COUNT-B PIC 9(4).","MOVE ZERO TO COUNT-A COUNT-B.\nGOBACK.");
        var move=p.path("statements").get(0);assertEquals(2,move.path("numericTransfers").size());
        assertEquals("0",move.path("numericTransfers").get(0).path("value").asText());
        assertFalse(p.path("gaps").toString().contains("MOVE_IDENTITY_NOT_PROVEN"));
    }
    @Test void sharedNumericStorageKeepsTypeAndConversionWithoutClaimingIndependentCells() throws Exception {
        var p=publish("01 DATE-A.\n05 YEAR-A PIC 9(4).\n05 MONTH-A PIC 99.\n05 DAY-A PIC 99.\n01 DATE-N REDEFINES DATE-A PIC 9(8).\n01 OUT-A PIC 99.",
            "MOVE 20261004 TO DATE-N.\nMOVE MONTH-A TO OUT-A.\nMOVE 12 TO MONTH-A.\nGOBACK.");
        for(int i=0;i<3;i++)assertEquals(1,p.path("statements").get(i).path("numericTransfers").size());
        for(var d:p.path("dataDeclarations"))if(java.util.Set.of("YEAR-A","MONTH-A","DAY-A","DATE-N").contains(d.path("canonicalName").asText()))
            assertTrue(d.path("scalarNumber").isObject());
        assertFalse(p.path("gaps").toString().contains("MOVE_IDENTITY_NOT_PROVEN"));
    }
    @Test void sharedNumericFormattingRequiresDisjointDestination() throws Exception {
        var p=publish("01 AREA-A.\n05 COUNT-A PIC 9999.\n01 ALIAS-A REDEFINES AREA-A PIC X(4).\n01 OUT-A PIC X(6).",
            "MOVE COUNT-A TO ALIAS-A.\nMOVE COUNT-A TO OUT-A.\nGOBACK.");
        assertEquals("UNAVAILABLE",p.path("statements").get(0).path("copySemantics").asText());
        assertEquals("FORMATTED_NUMBER",p.path("statements").get(1).path("copySemantics").asText());
    }

    @Test void sharedTextKeepsItsLogicalTypeWithoutInventingIndependentStorage() throws Exception {
        var p=publish("01 NUM-A PIC 9(8).\n01 TEXT-A REDEFINES NUM-A PIC X(8).\n01 NUM-B PIC 9(8).\n01 TEXT-B REDEFINES NUM-B PIC X(8).\n01 OUT-A PIC X(8).",
            "MOVE 'PROGA' TO TEXT-A.\nMOVE TEXT-A TO OUT-A.\nMOVE 'BC' TO TEXT-A(2:2).\nMOVE TEXT-A TO TEXT-B.\nGOBACK.");
        for(var d:p.path("dataDeclarations"))if(java.util.Set.of("TEXT-A","TEXT-B").contains(d.path("canonicalName").asText()))
            assertTrue(d.path("scalarText").isObject(),d.toString());
        assertFalse(p.path("gaps").toString().contains("MOVE_IDENTITY_NOT_PROVEN"));
    }

    @Test void aliasAndTableDoNotBecomeIndependentIntegerCells() throws Exception {
        var p=publish("01 AREA-A PIC 99.\n01 ALIAS-A REDEFINES AREA-A PIC XX.\n01 TABLE-A.\n05 ITEM-A PIC 99 OCCURS 2.","MOVE 1 TO AREA-A.\nMOVE 1 TO ITEM-A(1).\nGOBACK.");
        assertEquals(1,p.path("statements").get(0).path("numericTransfers").size());
        assertEquals(0,p.path("statements").get(1).path("numericTransfers").size());
    }
    @Test void integralFixedPointSpellingUsesItsValueAndHugePicDoesNotExpand() throws Exception {
        var p=publish("01 COUNT-A PIC 999.\n01 HUGE-A PIC 9(1000000000).", "MOVE 12.00 TO COUNT-A.\nMOVE 1 TO HUGE-A.\nMOVE 1.0E+2 TO COUNT-A.\nGOBACK.");
        assertEquals("12",p.path("statements").get(0).path("numericTransfers").get(0).path("value").asText());
        assertEquals(0,p.path("statements").get(1).path("numericTransfers").size());
        assertEquals(0,p.path("statements").get(2).path("numericTransfers").size());
    }

    @Test void dataPrefixSurvivesAnUnsupportedPeerWithoutCertifyingLaterReads() throws Exception {
        var p=publish("01 COUNT-A PIC 99.\n01 COUNT-B PIC 99.\n01 TEXT-A PIC NN USAGE NATIONAL.","MOVE COUNT-A TO COUNT-B TEXT-A COUNT-B.\nGOBACK.");
        var m=p.path("statements").get(0);assertEquals("MOVE",m.path("variant").asText());
        assertEquals(1,m.path("numericTransfers").size());
        assertEquals("operand:0:1",m.path("numericTransfers").get(0).path("target").asText());
        assertTrue(p.path("gaps").toString().contains("MOVE_IDENTITY_NOT_PROVEN"));
    }

    @Test void mixedNumericAndFormattedReceiversHaveOneOrderedProofPerDestination() throws Exception {
        var p=publish("01 COUNT-A PIC S9(4).\n01 COUNT-B PIC 99.\n01 TEXT-A PIC X(6).\n01 EDIT-A PIC +9999.",
            "MOVE COUNT-A TO COUNT-B TEXT-A EDIT-A COUNT-B.\nMOVE -23 TO EDIT-A COUNT-B.\nGOBACK.");
        assertEquals(4,p.path("statements").get(0).path("numericTransfers").size());
        assertEquals(2,p.path("statements").get(1).path("numericTransfers").size());
        assertFalse(p.path("gaps").toString().contains("MOVE_IDENTITY_NOT_PROVEN"));
        var fractional=publish("01 N PIC 99V9.\n01 X PIC XX.\n01 T PIC 99.","MOVE N TO X T.\nGOBACK.");
        assertEquals(0,fractional.path("statements").get(0).path("numericTransfers").size(),"noninteger to plain text is not a legal numeric formatting proof");
    }

    @Test void unresolvedPeerRetainsBindingGapWithoutErasingProvedLiteralReceiver() throws Exception {
        var p=publish("01 COUNT-A PIC 99.","MOVE 12 TO COUNT-A MISSING-A.\nGOBACK.");
        var m=p.path("statements").get(0);assertEquals("MOVE",m.path("variant").asText());
        assertEquals(1,m.path("numericTransfers").size());
        assertTrue(p.path("gaps").toString().contains("REFERENCE_UNRESOLVED_DECLARATION_NOT_FOUND"));
    }

    @Test void usageOfTheGroupAppliesToItsNumericChildren() throws Exception {
        for(String usage:new String[]{"GROUP-USAGE NATIONAL"}) {
            var p=publish("01 REC-A "+usage+".\n05 COUNT-A PIC 99.\n01 COUNT-B PIC 9999.","MOVE 1 TO COUNT-A.\nMOVE COUNT-A TO COUNT-B.\nGOBACK.");
            for(var m:p.path("statements"))assertEquals(0,m.path("numericTransfers").size(),usage);
        }
        var p=publish("01 REC-A USAGE DISPLAY.\n05 COUNT-A PIC 99.","MOVE 1 TO COUNT-A.\nGOBACK.");
        assertEquals(1,p.path("statements").get(0).path("numericTransfers").size());
    }

}
