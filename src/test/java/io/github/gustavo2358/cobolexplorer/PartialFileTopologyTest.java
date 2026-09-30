package io.github.gustavo2358.cobolexplorer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Recovery must preserve FILE inventory without promoting damaged input to control proof. */
class PartialFileTopologyTest {
    @TempDir Path directory;
    private static final String SELECT="SELECT F ASSIGN TO INDD FILE STATUS FS.\nSELECT G ASSIGN TO OUTDD.\nSELECT H ASSIGN TO THIRDDD.";
    private static final String FILES="FD F.\n01 REC PIC X(8).\nFD G.\n01 OTHER-REC PIC X(8).\nFD H.\n01 THIRD-REC PIC X(8).\nSD S.\n01 SORT-REC PIC X(8).";
    private JsonNode publish(String name,String records,String body) throws Exception {
        var path=directory.resolve(name);Files.createDirectories(path.resolve("copybooks"));
        var source="IDENTIFICATION DIVISION.\nPROGRAM-ID. FROUTES.\nENVIRONMENT DIVISION.\nINPUT-OUTPUT SECTION.\nFILE-CONTROL.\n"+SELECT
            +"\nDATA DIVISION.\nFILE SECTION.\n"+records+"\nWORKING-STORAGE SECTION.\n01 WORK-TEXT PIC X.\n01 FS PIC XX.\nPROCEDURE DIVISION.\n"+body+"\nCALL 'AFTERIO'.\nGOBACK.\n";
        var input=path.resolve("FROUTES.cbl");
        Files.writeString(input,source.lines().map(line->"       "+line+"\n").collect(java.util.stream.Collectors.joining()));
        var evidence=Path.of("target/partial-file-topology",name);Files.createDirectories(evidence);
        Files.copy(input,evidence.resolve("FROUTES.cbl"),StandardCopyOption.REPLACE_EXISTING);
        ExplorerMain.main(new String[]{"--source",input.toString(),"--copybooks",path.resolve("copybooks").toString(),"--output",path.resolve("sp").toString()});
        return new ObjectMapper().readTree(path.resolve("sp/cobol-semantic-product.json").toFile());
    }
    private static List<JsonNode> fileOutcomes(JsonNode sp) {
        var result=new ArrayList<JsonNode>();for(var o:sp.path("controlTopology").path("outcomes"))if(o.path("role").asText().startsWith("file/"))result.add(o);return result;
    }
    private static void partial(JsonNode sp,int uses) {
        assertEquals("INPUT_MISSING",sp.path("coverage").path("inventoryStatus").asText());
        assertEquals("INPUT_MISSING",sp.path("entryInventory").path("entries").get(0).path("availability").asText());
        var topology=sp.path("controlTopology");var routes=fileOutcomes(sp);
        assertEquals(uses,sp.path("fileInventory").path("operations").path("uses").size());
        int slots=0;
        for(var use:sp.path("fileInventory").path("operations").path("uses"))
            for(var route:use.path("control").path("routes"))slots+=route.path("destinations").size();
        assertEquals(slots,routes.size(),"every destination keeps its authoritative role");
        assertEquals(uses>0,!routes.isEmpty());
        for(var route:routes) {
            assertEquals("UNKNOWN_LOCAL",route.path("kind").asText());
            assertEquals("UNKNOWN_LOCAL",route.path("target").path("kind").asText());
            assertTrue(topology.path("regions").findValues("id").contains(route.path("target").path("reference")),"unknown target has a real owning region");
            var proofId=route.path("proofs").get(0);boolean partialProof=false;
            for(var proof:topology.path("proofs"))if(proof.path("id").equals(proofId)) {
                partialProof=proof.path("kind").asText().equals("PARTIAL_UNKNOWN");
                assertFalse(proof.path("dependencies").isEmpty());
                assertTrue(proof.path("provenance").path("original").path("startLine").asInt()>0);
            }
            assertTrue(partialProof,"route carries an explicit incomplete-control proof");
        }
        assertTrue(topology.path("fileFlows").isEmpty(),"partial parse cannot invent sequencing between operands");
        for(var region:topology.path("regions"))assertNotEquals("FILE_HANDLER",region.path("kind").asText(),"damaged scope does not license handler entry");
    }
    @Test void invalidLabelWithAndWithoutFileOperationKeepsPartialPublication() throws Exception {
        var fd=FILES.replace("FD F.","FD F LABEL RECORDS IS STANDARD.");
        partial(publish("invalid-label-open",fd,"OPEN INPUT F."),1);
        partial(publish("invalid-label-only",fd,"CONTINUE."),0);
    }
    @Test void malformedMoveCannotAbortAnyNativeFileFamily() throws Exception {
        var verbs=List.of("OPEN INPUT F.","CLOSE F.","READ F.","WRITE REC.","REWRITE REC.","DELETE F RECORD.","START F KEY IS EQUAL TO REC.","RELEASE SORT-REC.","RETURN S.");
        for(int i=0;i<verbs.size();i++)partial(publish("native-"+i,FILES,"MOVE TO WORK-TEXT.\n"+verbs.get(i)),1);
    }
    @Test void damagedInputCannotInventHandlerRegions() throws Exception {
        for(var body:List.of("READ F AT END CALL 'ATEND' NOT AT END CALL 'SUCCESS' END-READ.",
                "DELETE F RECORD INVALID KEY CALL 'INVALID' NOT INVALID KEY CALL 'SUCCESS' END-DELETE."))
            partial(publish("handler-"+body.split(" ")[0],FILES,"MOVE TO WORK-TEXT.\n"+body),1);
        partial(publish("damage-after",FILES,"READ F AT END CALL 'ATEND' END-READ.\nMOVE TO WORK-TEXT."),1);
    }
    @Test void compositeAndSortDoNotAcquireMissingNormalFlow() throws Exception {
        partial(publish("multi",FILES,"MOVE TO WORK-TEXT.\nOPEN INPUT F OUTPUT G H.\nCLOSE F G H."),6);
        partial(publish("sort",FILES,"MOVE TO WORK-TEXT.\nSORT S ON ASCENDING KEY SORT-REC USING F G GIVING H."),4);
        partial(publish("merge",FILES,"MOVE TO WORK-TEXT.\nMERGE S ON ASCENDING KEY SORT-REC USING F G GIVING H."),4);
    }
    @Test void callbacksStayUnknownOnPartialInput() throws Exception {
        partial(publish("sort-callback",FILES,"MOVE TO WORK-TEXT.\nSORT S ON ASCENDING KEY SORT-REC INPUT PROCEDURE FEED GIVING H.\nGOBACK.\nFEED.\nRELEASE SORT-REC."),3);
        partial(publish("use-callback",FILES,"DECLARATIVES.\nIO-ERROR SECTION.\nUSE AFTER STANDARD ERROR PROCEDURE ON F.\nIO-HANDLER.\nCONTINUE.\nEND DECLARATIVES.\nMAIN SECTION.\nMOVE TO WORK-TEXT.\nOPEN INPUT F."),1);
    }
    @Test void unrelatedMissingDataCopyCannotEraseProvedFileControl() throws Exception {
        var sp=publish("missing-data-copy",FILES+"\nCOPY ABSENT-DATA.","OPEN INPUT F.");
        assertFalse(fileOutcomes(sp).isEmpty());
        for(var route:fileOutcomes(sp))assertEquals("COMPLETE",route.path("target").path("kind").asText());
        assertFalse(sp.path("gaps").isEmpty(),"the missing copy remains visible");
    }
    @Test void validLabelFormsKeepProvedRoutes() throws Exception {
        for(var clause:List.of("RECORD IS","RECORDS ARE","RECORD","RECORDS")) {
            var sp=publish("valid-"+clause.replace(' ','-'),FILES.replace("FD F.","FD F LABEL "+clause+" STANDARD."),"MOVE 'X' TO WORK-TEXT.\nOPEN INPUT F.");
            assertNotEquals("INPUT_MISSING",sp.path("coverage").path("inventoryStatus").asText());
            assertFalse(fileOutcomes(sp).isEmpty());
            for(var route:fileOutcomes(sp))assertEquals("COMPLETE",route.path("target").path("kind").asText());
        }
    }
}
