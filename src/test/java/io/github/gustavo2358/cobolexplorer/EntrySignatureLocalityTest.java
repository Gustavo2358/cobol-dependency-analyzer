package io.github.gustavo2358.cobolexplorer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

/** Missing DATA cannot erase the independently complete PROCEDURE header. */
class EntrySignatureLocalityTest {
    @TempDir Path directory;

    @Test void modeledSqlcaPreservesAbsentReturnAcrossSharedEntryBody() throws Exception {
        var sp = publish("WORKING-STORAGE SECTION.\nEXEC SQL INCLUDE SQLCA END-EXEC.\n"
                + "LINKAGE SECTION.\n01 ARGUMENT PIC X.", "PROCEDURE DIVISION.",
                "ENTRY 'ALTERNATE' USING ARGUMENT.\nDISPLAY ARGUMENT.\nGOBACK.");
        assertTrue(sp.toString().contains("NOMINAL_COPYBOOK"));
        assertEquals("INPUT_MISSING", sp.path("coverage").path("inventoryStatus").asText());
        assertEquals(2, sp.path("entryInventory").path("entries").size());
        assertSignature(sp, "KNOWN", 0, "ABSENT");
        var alternate = sp.path("entryInventory").path("entries").get(1);
        assertEquals("PARTIAL", alternate.path("signature").path("availability").asText());
        assertEquals("ABSENT", alternate.path("signature").path("returningClause").asText());
        assertEquals("KNOWN", alternate.path("start").path("availability").asText());
        export("sqlca-shared-entry", sp);
    }

    @Test void missingDataKeepsWrittenUsingShapeWithoutClaimingBindings() throws Exception {
        var sp = publish("LINKAGE SECTION.\nCOPY MISSING.",
                "PROCEDURE DIVISION USING ARGUMENT.", "GOBACK.");
        assertSignature(sp, "PARTIAL", 1, "ABSENT");
        assertTrue(primary(sp).path("gaps").toString().contains("ENTRY_SIGNATURE_NOT_PROJECTED"));
        assertEquals("INPUT_MISSING", sp.path("storageIndependence").path("availability").asText());
        assertFalse(sp.path("dataDeclarations").toString().contains("ARGUMENT"));
    }

    @Test void missingDataKeepsPresentReturningAndItsOpenContract() throws Exception {
        var sp = publish("LINKAGE SECTION.\nCOPY MISSING.",
                "PROCEDURE DIVISION RETURNING RESULT-N.", "GOBACK.");
        assertSignature(sp, "PARTIAL", 0, "PRESENT");
        assertTrue(primary(sp).path("gaps").toString().contains("ENTRY_SIGNATURE_NOT_PROJECTED"));
    }

    @Test void missingCopyInHeaderCannotProveAbsenceOfSignatureClauses() throws Exception {
        var sp = publish("WORKING-STORAGE SECTION.",
                "PROCEDURE DIVISION\nCOPY MISSING.\n.", "GOBACK.");
        assertUnknownSignature(sp);
    }

    @Test void missingExecutableCopyCannotReuseDataProof() throws Exception {
        var sp = publish("WORKING-STORAGE SECTION.\nCOPY DATA-GAP.",
                "PROCEDURE DIVISION.", "COPY CODE-GAP.\nGOBACK.");
        assertUnknownSignature(sp);
        assertEquals("BLOCKED", primary(sp).path("readiness").path("lowering").path("status").asText());
    }

    @Test void syntaxErrorsCannotCertifyRecoveredHeader() throws Exception {
        var sp = publish("WORKING-STORAGE SECTION.\nCOPY MISSING.\n*",
                "PROCEDURE DIVISION.", "GOBACK.");
        assertUnknownSignature(sp);
    }

    private static void assertSignature(JsonNode sp, String availability, int count, String returning) {
        var signature = primary(sp).path("signature");
        assertEquals(availability, signature.path("availability").asText());
        assertEquals(count, signature.path("parameterCount").asInt(-1));
        assertEquals(returning, signature.path("returningClause").asText());
        assertTrue(primary(sp).path("gaps").toString().contains("ENTRY_INPUT_INCOMPLETE"));
    }

    private static void assertUnknownSignature(JsonNode sp) {
        var signature = primary(sp).path("signature");
        assertEquals("INPUT_MISSING", signature.path("availability").asText());
        assertTrue(signature.path("parameterCount").isNull());
        assertEquals("UNKNOWN", signature.path("returningClause").asText());
    }

    private static JsonNode primary(JsonNode sp) { return sp.path("entryInventory").path("entries").get(0); }

    private static void export(String name, JsonNode sp) throws Exception {
        var out = Path.of("target/entry-signature-locality");
        Files.createDirectories(out);
        Files.write(out.resolve(name + ".json"), new ObjectMapper().writeValueAsBytes(sp));
    }

    private JsonNode publish(String data, String header, String body) throws Exception {
        String source = "IDENTIFICATION DIVISION.\nPROGRAM-ID. ENTRYRET.\nDATA DIVISION.\n"
                + data + "\n" + header + "\n" + body + "\n";
        Files.createDirectories(directory.resolve("copybooks"));
        Path input = directory.resolve("input.cbl");
        Files.writeString(input, source.lines().map(line -> "       " + line + "\n")
                .collect(java.util.stream.Collectors.joining()));
        ExplorerMain.main(new String[]{"--json-compression", "none", "--source", input.toString(),
                "--copybooks", directory.resolve("copybooks").toString(),
                "--output", directory.resolve("out").toString()});
        return new ObjectMapper().readTree(directory.resolve("out/cobol-semantic-product.json").toFile());
    }
}
