package io.github.gustavo2358.cobolexplorer;

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
        var out=work.resolve(name);
        ExplorerMain.main(new String[]{"--source",source.toString(),"--copybooks",work.toString(),"--output",out.toString()});
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
        assertEquals(List.of("        ","   "),java.util.stream.StreamSupport.stream(m.path("logicalTransfers").spliterator(),false)
            .map(t->t.path("value").path("value").asText()).toList());
    }
    @Test void physicalSpacesUseTheDeclaredCodecAndReceivingExtents() {
        var s=StorageProductTest.state("01 A PIC X(8).\n01 B PIC X(3).","MOVE SPACES TO A B.");
        var move=io.github.gustavo2358.cobolexplorer.semanticproduct.CobolSemanticPort.open(s).moves().get(0);
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
        var p=publish("overlap","01 SRC PIC X(8).\n01 SAME REDEFINES SRC PIC X(8).\n01 DEST PIC X(8).","MOVE SRC TO SAME DEST.");
        var m=p.path("statements").get(0);
        assertTrue(m.path("logicalTransfers").isEmpty());
        if(m.path("variant").asText().equals("MOVE")) {
            assertEquals("UNAVAILABLE",m.path("copySemantics").asText());
            assertTrue(Set.of("UNAVAILABLE","MUST_UNKNOWN").contains(m.path("regionalMove").path("kind").asText()));
        }
    }
}
