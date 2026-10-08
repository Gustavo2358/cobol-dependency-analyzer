package com.imd.cobolexplorer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.*;
class NumericConversionTest {
    private JsonNode publish(String data,String procedure) throws Exception {
        var a=AstBoundaryTestSupport.analyze(ScalarMoveCheckpoint4ATest.program(data,procedure),"numeric-conversion.cbl");
        return new ObjectMapper().readTree(com.imd.cobolexplorer.semanticproduct.transport.SemanticProductJsonWriter.serialize(EofUnitBoundaryTest.publish(a,0,StorageLayoutSemantics.Profile.UNSPECIFIED)));
    }
    @Test void explicitTruncSelectsTheReceivingRule() throws Exception {
        for(var entry:java.util.Map.of("STD","51","BIN","-7621","OPT","").entrySet()) {
            var source="CBL TRUNC("+entry.getKey()+")\n"+ScalarMoveCheckpoint4ATest.program(
                "01 BINARY-A PIC S99 COMP.\n01 SOURCE-A PIC 9(6).",
                "MOVE 123451 TO BINARY-A.\nMOVE SOURCE-A TO BINARY-A.\nGOBACK.");
            var a=AstBoundaryTestSupport.analyze(source,"trunc.cbl");
            var p=new ObjectMapper().readTree(com.imd.cobolexplorer.semanticproduct.transport.SemanticProductJsonWriter.serialize(EofUnitBoundaryTest.publish(a,0,StorageLayoutSemantics.Profile.UNSPECIFIED)));
            assertEquals(entry.getKey(),p.path("dataDeclarations").get(0).path("scalarNumber").path("trunc").asText());
            var transfers=p.path("statements").get(0).path("numericTransfers");
            if(entry.getValue().isEmpty()) {
                assertEquals(1,transfers.size());assertTrue(transfers.path(0).path("value").isNull());
                assertEquals(1,p.path("statements").get(1).path("numericTransfers").size());
            }
            else {
                assertEquals(entry.getValue(),transfers.path(0).path("value").asText());
                assertEquals(1,p.path("statements").get(1).path("numericTransfers").size());
            }
        }
    }
    @Test void unspecifiedTruncKeepsConversionWithAnOpenResult() throws Exception {
        var p=publish("01 TARGET-A PIC S99 COMP.\n01 SOURCE-A PIC 9(6).",
            "MOVE 123451 TO TARGET-A.\nMOVE SOURCE-A TO TARGET-A.\nMOVE 12 TO TARGET-A.\nGOBACK.");
        assertEquals(1,p.path("statements").get(0).path("numericTransfers").size());
        assertTrue(p.path("statements").get(0).path("numericTransfers").get(0).path("value").isNull());
        assertEquals(1,p.path("statements").get(1).path("numericTransfers").size());
        assertEquals("12",p.path("statements").get(2).path("numericTransfers").get(0).path("value").asText());
        assertFalse(p.path("gaps").toString().contains("MOVE_IDENTITY_NOT_PROVEN"));
    }
    @Test void initialProgramKeepsNumericTypeAndMoveProofs() throws Exception {
        var source=ScalarMoveCheckpoint4ATest.program("01 TARGET-A PIC S9(4) COMP VALUE 0.","MOVE 12 TO TARGET-A.\nGOBACK.")
            .replace("PROGRAM-ID. SAMPLE.","PROGRAM-ID. SAMPLE IS INITIAL.");
        var a=AstBoundaryTestSupport.analyze(source,"initial-numeric.cbl");
        var p=new ObjectMapper().readTree(com.imd.cobolexplorer.semanticproduct.transport.SemanticProductJsonWriter.serialize(EofUnitBoundaryTest.publish(a,0,StorageLayoutSemantics.Profile.UNSPECIFIED)));
        assertEquals("BINARY",p.path("dataDeclarations").get(0).path("scalarNumber").path("representation").asText());
        assertEquals("12",p.path("statements").get(0).path("numericTransfers").path(0).path("value").asText());
    }
    @Test void quotedNumericCharactersMoveAsAnUnsignedInteger() throws Exception {
        var p=publish("01 TARGET-A PIC S9(3)V99 COMP-3.\n01 TARGET-B PIC 9.",
            "MOVE '00052' TO TARGET-A.\nMOVE '052' TO TARGET-B.\nGOBACK.");
        assertEquals("52.00",p.path("statements").get(0).path("numericTransfers").path(0).path("value").asText());
        assertEquals("2",p.path("statements").get(1).path("numericTransfers").path(0).path("value").asText());
        for(var text:java.util.List.of("-5"," 5","5.1","5 ","A5")) {
            var invalid=publish("01 TARGET-A PIC 99.","MOVE '"+text+"' TO TARGET-A.\nGOBACK.");
            assertEquals(0,invalid.path("statements").get(0).path("numericTransfers").size(),text);
            assertTrue(invalid.path("gaps").toString().contains("MOVE_IDENTITY_NOT_PROVEN"));
        }
    }
    @Test void textDataCarriesConversionWithoutClaimingAConstantOrValidRuntimeDigits() throws Exception {
        var p=publish("01 SOURCE-X PIC X(8).\n01 TARGET-A PIC 9(4).\n01 TARGET-B PIC S9(3)V99 COMP-3.",
            "MOVE SOURCE-X TO TARGET-A TARGET-B.\nGOBACK.");
        var move=p.path("statements").get(0);assertEquals(2,move.path("numericTransfers").size());
        for(var transfer:move.path("numericTransfers"))assertTrue(transfer.path("value").isNull()||transfer.path("value").isMissingNode());
        assertFalse(p.path("gaps").toString().contains("MOVE_IDENTITY_NOT_PROVEN"));
        assertTrue(move.path("source").path("reference").path("wholeItemAccess").has("data"));
    }
    @Test void signScaleAndTruncationFollowReceivingPicture() throws Exception {
        var p=publish("01 UNSIGNED-A PIC 99.\n01 SIGNED-A PIC S99.\n01 DECIMAL-A PIC S9(3)V99 COMP-3.",
            "MOVE -123.459 TO UNSIGNED-A.\nMOVE -123.459 TO SIGNED-A.\nMOVE -123.459 TO DECIMAL-A.\nGOBACK.");
        String[] expected={"23","-23","-123.45"};
        for(int i=0;i<3;i++)assertEquals(expected[i],p.path("statements").get(i).path("numericTransfers").path(0).path("value").asText());
        assertFalse(p.path("gaps").toString().contains("MOVE_IDENTITY_NOT_PROVEN"));
    }
    @Test void dataConversionsCarryTypesWithoutFabricatingConstants() throws Exception {
        var p=publish("01 SOURCE-A PIC S9(4)V99.\n01 TARGET-A PIC 99.\n01 TARGET-B PIC S9(3)V9 COMP-3.",
            "MOVE SOURCE-A TO TARGET-A TARGET-B.\nGOBACK.");
        var ts=p.path("statements").get(0).path("numericTransfers");assertEquals(2,ts.size());
        for(var t:ts)assertTrue(t.path("value").isNull()||t.path("value").isMissingNode());
        var n=p.path("dataDeclarations").get(0).path("scalarNumber");assertTrue(n.path("signed").asBoolean());assertEquals(2,n.path("scale").asInt());
    }
    @Test void absentTruncOptionCannotChooseBetweenStdAndBin() throws Exception {
        var p=publish("01 BINARY-A PIC S99 COMP.\n01 SOURCE-A PIC 9(5).",
            "MOVE 123451 TO BINARY-A.\nMOVE SOURCE-A TO BINARY-A.\nGOBACK.");
        for(int i=0;i<2;i++) {
            var proof=p.path("statements").get(i).path("numericTransfers");
            assertEquals(1,proof.size());assertTrue(proof.get(0).path("value").isNull());
        }
        assertFalse(p.path("gaps").toString().contains("MOVE_IDENTITY_NOT_PROVEN"));
    }
    @Test void nativeBinaryUsesTheBinaryCapacityRatherThanDecimalPicture() throws Exception {
        var p=publish("01 NATIVE-A PIC S99 COMP-5.\n01 NATIVE-B PIC 99 COMP-5.",
            "MOVE 123451 TO NATIVE-A.\nMOVE -65537 TO NATIVE-B.\nMOVE NATIVE-A TO NATIVE-A.\nGOBACK.");
        assertEquals("-7621",p.path("statements").get(0).path("numericTransfers").path(0).path("value").asText());
        assertEquals("1",p.path("statements").get(1).path("numericTransfers").path(0).path("value").asText());
        assertEquals(1,p.path("statements").get(2).path("numericTransfers").size());
    }
    @Test void binaryAndNativeBinaryUseTypedLocalCells() throws Exception {
        var p=publish("01 GROUP-A USAGE COMP.\n05 BINARY-A PIC S9(4).\n01 NATIVE-A PIC S9(4) COMP-5.\n01 PACKED-A PIC S9(7) COMP-3.",
            "MOVE -123 TO BINARY-A.\nMOVE 123 TO NATIVE-A.\nMOVE BINARY-A TO PACKED-A.\nGOBACK.");
        for(int i=0;i<3;i++)assertEquals(1,p.path("statements").get(i).path("numericTransfers").size());
    }
}
