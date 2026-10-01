package io.github.gustavo2358.cobolexplorer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PreprocessorSuppressTest {
    @TempDir Path directory;
    private PreprocessorEngine.Outcome preprocess(String source) throws Exception {
        return new PreprocessorEngine(Bindings.cobol(), new CopybookLibrary(directory))
            .process(SourceMap.identity(source, "suppress.cbl"), "suppress.cbl");
    }
    @Test void ordinaryKeywordIsPreservedWithoutPreprocessingDiagnostics() throws Exception {
        for (var source : List.of("JSON GENERATE OUT-TEXT FROM IN-TEXT SUPPRESS FIELD END-JSON.\n",
                "JSON GENERATE OUT-TEXT FROM IN-TEXT\nSUPPRESS EVERY NONNUMERIC ELEMENT WHEN SPACES\nEND-JSON.\n",
                "XML GENERATE OUT-TEXT FROM IN-TEXT SUPPRESS FIELD END-XML.\n",
                "SUPPRESS PRINTING.\n", "DISPLAY 'SUPPRESS COPY'.\n")) {
            var result = preprocess(source);
            assertEquals(source, result.text());
            assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
            int start = source.indexOf("SUPPRESS");
            var origin = result.sourceMap().provenance(start, start + 8);
            assertTrue(origin.exact());
            assertEquals("suppress.cbl", origin.original().file());
        }
    }
    @Test void copySuppressStillExpandsTheMemberAndKeepsItsOrigin() throws Exception {
        Files.writeString(directory.resolve("MEMBER.cpy"), "       01 ORIGINAL-NAME PIC X.\n");
        for (var clause : List.of("COPY MEMBER SUPPRESS.\n", "COPY MEMBER SUPPRESS REPLACING ==ORIGINAL-NAME== BY ==NEW-NAME==.\n")) {
            var result = preprocess(clause);
            assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
            assertFalse(result.text().contains("COPY"));
            String name = clause.contains("REPLACING") ? "NEW-NAME" : "ORIGINAL-NAME";
            assertTrue(result.text().contains(name));
            int start = result.text().indexOf("PIC");
            assertTrue(result.sourceMap().provenance(start, start + 3).original().file().endsWith("MEMBER.cpy"));
        }
    }
    @Test void missingCopyAndUnsupportedReplaceKeepTheirOwnPolicy() throws Exception {
        var missing = preprocess("COPY ABSENT SUPPRESS.\n");
        assertEquals(1, missing.unresolved());
        assertThrows(UnsupportedOperationException.class, () -> preprocess("REPLACE OFF.\n"));
        assertThrows(IllegalStateException.class, () -> PreprocessorEngine.policyFor("unclassifiedRule"));
    }
    @Test void jsonWitnessPreservesUnknownSemanticsWithoutParserRecovery() throws Exception {
        var source = "IDENTIFICATION DIVISION.\nPROGRAM-ID. JSUP.\nDATA DIVISION.\nWORKING-STORAGE SECTION.\n"
            + "01 OUTPUT-TEXT PIC X(100).\n01 INPUT-TEXT PIC X VALUE 'X'.\nPROCEDURE DIVISION.\n"
            + "JSON GENERATE OUTPUT-TEXT FROM INPUT-TEXT\nSUPPRESS INPUT-TEXT\nEND-JSON.\nGOBACK.\n";
        var input = directory.resolve("JSUP.cbl");
        Files.writeString(input, source.lines().map(line -> "       " + line + "\n").collect(java.util.stream.Collectors.joining()));
        ExplorerMain.main(new String[]{"--source", input.toString(), "--copybooks", directory.toString(), "--output", directory.resolve("sp").toString()});
        var sp = new ObjectMapper().readTree(directory.resolve("sp/cobol-semantic-product.json").toFile());
        assertNotEquals("INPUT_MISSING", sp.path("coverage").path("inventoryStatus").asText());
        assertFalse(Files.readString(directory.resolve("sp/tree-data.js")).contains("\"phase\":\"PARSER\""));
        assertTrue(sp.path("statements").toString().contains("OBSERVED"));
        assertTrue(sp.path("statements").toString().contains("GOBACK"));
        assertFalse(sp.path("gaps").isEmpty(), "syntactic support does not prove generation effects");

        // Malformed input still reports the parse gap after successful preprocessing.
        Files.writeString(input, Files.readString(input).replace("FROM INPUT-TEXT", "INPUT-TEXT"));
        ExplorerMain.main(new String[]{"--source", input.toString(), "--copybooks", directory.toString(), "--output", directory.resolve("bad").toString()});
        var bad = new ObjectMapper().readTree(directory.resolve("bad/cobol-semantic-product.json").toFile());
        assertEquals("INPUT_MISSING", bad.path("coverage").path("inventoryStatus").asText());
        assertTrue(Files.readString(directory.resolve("bad/tree-data.js")).contains("\"phase\":\"PARSER\""));
    }
}
