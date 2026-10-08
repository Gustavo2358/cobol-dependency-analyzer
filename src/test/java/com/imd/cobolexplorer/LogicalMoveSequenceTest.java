package com.imd.cobolexplorer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class LogicalMoveSequenceTest {
    @TempDir Path work;
    JsonNode publish(String name,String data,String code) throws Exception {
        var source=work.resolve(name+".cbl");
        Files.writeString(source,ScalarMoveCheckpoint4ATest.program(data,code+"\nGOBACK.").lines()
            .map(s->"       "+s).collect(java.util.stream.Collectors.joining("\n","","\n")));
        AstBoundaryTestSupport.analyze(Files.readString(source),name+".cbl");
        var out=work.resolve(name);
        ExplorerMain.main(new String[]{"--json-compression", "none","--source",source.toString(),"--copybooks",work.toString(),"--output",out.toString()});
        return new ObjectMapper().readTree(out.resolve("cobol-semantic-product.json").toFile());
    }
    @Test void dataSourceHasOneOrderedTransferPerReceiver() throws Exception {
        var p=publish("data","01 SRC PIC X(8).\n01 A PIC X(8).\n01 B PIC X(4).","MOVE SRC TO A B.");
        var m=p.path("statements").get(0);
        assertEquals("MOVE",m.path("variant").asText());assertEquals(1,m.path("additionalTransfers").size());
        assertTrue(m.path("source").path("reference").path("logicalWholeItem").isTextual());
        assertEquals(m.path("source").path("reference").path("logicalWholeItem"),
            m.path("additionalTransfers").get(0).path("source").path("reference").path("logicalWholeItem"));
    }
    @Test void spacesFitEachReceiverWithoutPhysicalEncoding() throws Exception {
        var p=publish("spaces","01 A PIC X(8).\n01 B PIC X(3).","MOVE SPACES TO A B.");
        var m=p.path("statements").get(0);assertEquals("MOVE",m.path("variant").asText());
        assertTrue(m.path("logicalTransfers").isEmpty(),"logical receivers use the common fitting recipe");
        assertTrue(m.path("target").path("logicalWholeItem").isTextual());
        assertTrue(m.path("additionalTransfers").get(0).path("target").path("logicalWholeItem").isTextual());
    }
    @Test void completeLogicalSequenceClosesMoveObligationsButPartialSequenceDoesNot() throws Exception {
        var complete=publish("complete-proof","01 SRC PIC X(8).\n01 REC-A.\n05 A PIC X(8).\n01 REC-B.\n05 B PIC X(4).","MOVE SRC TO REC-A REC-B.");
        assertFalse(java.util.stream.StreamSupport.stream(complete.path("gaps").spliterator(),false)
            .anyMatch(g->Set.of("MOVE_IDENTITY_NOT_PROVEN","SCALAR_WHOLE_ITEM_NOT_PROVEN").contains(g.path("code").asText())));
        var partial=publish("partial-proof","01 A PIC X(8).\n01 EDIT-A PIC +999.99.","MOVE '1' TO A EDIT-A.");
        assertTrue(java.util.stream.StreamSupport.stream(partial.path("gaps").spliterator(),false)
            .anyMatch(g->g.path("code").asText().equals("MOVE_IDENTITY_NOT_PROVEN")));
    }
    @Test void constantReferenceModificationPublishesCharacterBounds() throws Exception {
        var p=publish("slice","01 REC.\n05 A PIC X(8).\n05 B PIC X(2).\n01 SOURCE-A PIC X(6).",
            "MOVE 'ABCDEFGH12' TO REC.\nMOVE 'xy' TO A(3:2).\nMOVE SOURCE-A(2:3) TO B.\nMOVE 'z' TO A(8:).");
        var moves=p.path("statements");
        assertEquals("2",moves.get(1).path("target").path("logicalSlice").path("start").asText());
        assertEquals("2",moves.get(1).path("target").path("logicalSlice").path("length").asText());
        assertTrue(moves.get(1).path("target").path("wholeItemAccess").isNull());
        assertTrue(moves.get(1).path("target").path("logicalWholeItem").isNull());
        assertEquals("1",moves.get(2).path("source").path("reference").path("logicalSlice").path("start").asText());
        assertEquals("1",moves.get(3).path("target").path("logicalSlice").path("length").asText());
        assertFalse(java.util.stream.StreamSupport.stream(p.path("gaps").spliterator(),false)
            .anyMatch(g->Set.of("MOVE_IDENTITY_NOT_PROVEN","SCALAR_WHOLE_ITEM_NOT_PROVEN").contains(g.path("code").asText())));
        var bad=publish("invalid-slice","01 A PIC X(8).\n01 N PIC 9.",
            "MOVE 'x' TO A(0:1).\nMOVE 'x' TO A(8:2).\nMOVE 'x' TO A(N:1).");
        for(var stmt:bad.path("statements"))if(stmt.path("variant").asText().equals("MOVE"))
            assertTrue(stmt.path("target").path("logicalSlice").isMissingNode()||stmt.path("target").path("logicalSlice").isNull());
    }
    @Test void partialCellInMixedRecordRetainsTheSameAliasProof() throws Exception {
        var p=publish("mixed-slice","01 REC.\n05 SIZE-A PIC S9(4) COMP.\n05 A PIC X(8).\n05 A-ALIAS REDEFINES A PIC X(8).\n01 OUT-A PIC X(8).",
            "MOVE 'ABCDEFGH' TO A.\nMOVE 'xy' TO A-ALIAS(3:2).\nMOVE A TO OUT-A.");
        assertEquals("2",p.path("statements").get(1).path("target").path("logicalSlice").path("start").asText());
        assertFalse(java.util.stream.StreamSupport.stream(p.path("gaps").spliterator(),false)
            .anyMatch(g->g.path("code").asText().equals("MOVE_IDENTITY_NOT_PROVEN")));
    }
    @Test void collatingExtremesPublishTypedFillsWithoutInventingCharacters() throws Exception {
        var p=publish("extremes","01 REC.\n05 A PIC X(8).\n05 B PIC X(2).\n01 C PIC X(1000000000).",
            "MOVE LOW-VALUES TO A(3:2).\nMOVE HIGH-VALUES TO B C.");
        assertEquals("FIGURATIVE_LOW",p.path("statements").get(0).path("source").path("kind").asText());
        assertEquals("FIGURATIVE_HIGH",p.path("statements").get(1).path("source").path("kind").asText());
        for(var m:p.path("statements"))if(m.path("variant").asText().equals("MOVE")) {
            assertTrue(m.path("source").path("logicalValue").isNull());
            for(var t:m.path("additionalTransfers"))assertTrue(t.path("source").path("logicalValue").isNull());
        }
        assertFalse(java.util.stream.StreamSupport.stream(p.path("gaps").spliterator(),false)
            .anyMatch(g->Set.of("LITERAL_KIND_NOT_PUBLISHED","MOVE_IDENTITY_NOT_PROVEN","SCALAR_WHOLE_ITEM_NOT_PROVEN").contains(g.path("code").asText())));
        var unsupported=publish("unsupported-extreme","01 N PIC 99.","MOVE LOW-VALUES TO N.");
        assertTrue(unsupported.path("gaps").toString().contains("MOVE_IDENTITY_NOT_PROVEN"));
    }
    @Test void physicalSpacesUseTheDeclaredCodecAndReceivingExtents() {
        var s=StorageProductTest.state("01 A PIC X(8).\n01 B PIC X(3).","MOVE SPACES TO A B.");
        var move=com.imd.cobolexplorer.semanticproduct.CobolSemanticPort.open(s).moves().get(0);
        assertEquals(java.util.Collections.nCopies(8,64),move.regionalMove().orElseThrow().bytes());
        assertEquals(java.util.Collections.nCopies(3,64),move.additionalTransfers().get(0).effect().bytes());
    }
    @Test void receivingGroupsKeepTheirCanonicalFamily() throws Exception {
        var p=publish("groups","01 SRC PIC X(8).\n01 REC-A.\n05 A PIC X(8).\n01 REC-B.\n05 B PIC X(8).","MOVE SRC TO REC-A REC-B.");
        var m=p.path("statements").get(0);assertEquals("MOVE",m.path("variant").asText());
        assertTrue(m.path("target").path("logicalWholeItem").isTextual());
        assertTrue(m.path("additionalTransfers").get(0).path("target").path("logicalWholeItem").isTextual());
    }
    @Test void overlappingSendingFamilyDoesNotGetExactSequenceAuthority() throws Exception {
        var p=publish("overlap","01 SRC PIC X(8).\n01 SRC-ALIAS REDEFINES SRC PIC X(8).\n01 DEST PIC X(8).","MOVE SRC TO SRC-ALIAS DEST.");
        var m=p.path("statements").get(0);
        assertTrue(m.path("logicalTransfers").isEmpty());
        if(m.path("variant").asText().equals("MOVE")) {
            assertEquals("UNAVAILABLE",m.path("copySemantics").asText());
            assertTrue(Set.of("UNAVAILABLE","MUST_UNKNOWN").contains(m.path("regionalMove").path("kind").asText()));
        }
    }
}
