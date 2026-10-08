package com.imd.cobolexplorer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.imd.cobolexplorer.semanticproduct.transport.SemanticProductJsonWriter;
import static com.imd.cobolexplorer.semanticproduct.CobolSemanticProduct.*;
import static com.imd.cobolexplorer.ScalarMoveCheckpoint4ATest.*;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import java.util.List;

class MoveDataSourceTest {
    @Test void typedSourceReadsAndLinearStorageAuthority() throws Exception {
        var p = publish(program("01 WS-A PIC X(8).\n01 WS-PGM PIC X(8).",
                "MOVE 'PROGA' TO WS-A.\nMOVE WS-A TO WS-PGM.\nCALL WS-PGM.\nGOBACK."));
        var literal = p.moves().get(0);
        assertInstanceOf(LiteralSource.class, literal.source());
        assertEquals("PROGA   ", TextFitOracle.value(literal));
        var copy = p.moves().get(1);
        var read = assertInstanceOf(DataReference.class, copy.source());
        assertEquals(OperandRole.READ, read.role());
        assertEquals(literal.target().binding().selected(), read.binding().selected());
        assertEquals(read.binding().selected().orElseThrow(), read.wholeItemAccess().orElseThrow().data());
        assertEquals(CopySemantics.FULL_IDENTITY, copy.copySemantics());
        assertTrue(copy.textAdjustment().isEmpty());
        assertNotEquals(read.id(), copy.target().id());
        assertNotEquals(read.provenance(), copy.target().provenance());
        assertEquals(Availability.KNOWN, p.storageIndependence().availability());
        assertEquals(2, p.storageIndependence().members().size());
        var json = new ObjectMapper().readTree(SemanticProductJsonWriter.serialize(p));
        assertEquals("2.67.0", json.path("contractVersion").asText());
        assertEquals("LITERAL", json.path("statements").get(0).path("source").path("variant").asText());
        var source = json.path("statements").get(1).path("source");
        assertEquals("DATA", source.path("variant").asText());
        assertEquals("READ", source.path("reference").path("role").asText());
    }
    @Test void unsupportedSourcesNeverProveCopy() {
        for (String source : List.of("WS-A(1)", "WS-A(1:4)", "MISSING")) {
            var p = publish(program("01 WS-A PIC X(8).\n01 WS-PGM PIC X(8).",
                    "MOVE " + source + " TO WS-PGM.\nGOBACK."));
            assertTrue(p.moves().isEmpty() || p.moves().get(0).copySemantics() == CopySemantics.UNAVAILABLE, source);
        }
        for (String data : List.of("01 WS-A PIC 9(7)V9.",
                "01 WS-A PIC X(8).\n01 WS-A PIC X(8).")) {
            var p = publish(program(data + "\n01 WS-PGM PIC X(8).", "MOVE WS-A TO WS-PGM.\nGOBACK."));
            assertTrue(p.moves().isEmpty() || p.moves().get(0).copySemantics() == CopySemantics.UNAVAILABLE, data);
        }
    }
}
