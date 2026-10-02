package io.github.gustavo2358.cobolexplorer;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CicsSourcePossibilityTest {
    private static JsonNode product(String command) throws Exception {
        return CicsCommandContractTest.publish("EXEC CICS " + command
            + "\nEND-EXEC\nCALL 'AFTERPGM'\nGOBACK.");
    }

    private static void assertSourceOnly(JsonNode p) {
        var topology=p.path("controlTopology");
        var continuations=topology.path("sourceContinuations");
        assertEquals(1,continuations.size(),p.toString());
        var continuation=continuations.get(0);
        var statement=continuation.path("statement");
        assertTrue(topology.path("proofs").findValuesAsText("kind").contains("CONTROL_POSSIBILITY"));
        assertEquals("OCCURRENCE",continuation.path("target").path("kind").asText());
        assertEquals(p.path("statements").get(1).path("header").path("id"),
            continuation.path("target").path("reference"));
        for(var outcome:topology.path("outcomes"))if(outcome.path("statement").equals(statement))
            assertEquals("UNKNOWN_LOCAL",outcome.path("kind").asText(),"hypothesis cannot authorize execution");
    }

    @Test void unsupportedReturnOptionsCanReturnAConditionLocally() throws Exception {
        for(var options:List.of("CHANNEL('CH')", "INPUTMSG(WS-AREA)\nINPUTMSGLEN(8)"))
            for(var handler:List.of("NOHANDLE", "RESP(RC)", "RESP(RC) RESP2(RC2)")) {
                var p=product("RETURN "+options+"\n"+handler);
                assertEquals("UNAVAILABLE",CicsCommandContractTest.command(p).path("syntaxStatus").asText());
                assertSourceOnly(p);
            }
    }

    @Test void futureProgramControlGapsDoNotEraseLocalConditionPossibility() throws Exception {
        for(var handler:List.of("NOHANDLE", "RESP(RC)"))
            assertSourceOnly(product("XCTL PROGRAM('FIRSTPGM')\nMYSTERY\n"+handler));
    }

    @Test void terminalFormsAndMalformedHandlingDoNotGainContinuations() throws Exception {
        for(var command:List.of("RETURN", "RETURN NOHANDLE", "RETURN RESP(RC)",
            "RETURN CHANNEL('CH')", "RETURN CHANNEL('CH') RESP2(RC2)",
            "RETURN CHANNEL('NOHANDLE')", "RETURN CHANNEL('CH') NOHANDLE(RC)",
            "RETURN CHANNEL('CH') RESP", "RETURN CHANNEL('CH') RESP()",
            "XCTL PROGRAM('FIRSTPGM')", "XCTL PROGRAM('FIRSTPGM') MYSTERY",
            "XCTL PROGRAM('FIRSTPGM') MYSTERY RESP2(RC2)", "ABEND NOHANDLE"))
            assertEquals(0,product(command).path("controlTopology").path("sourceContinuations").size(),command);
    }

    @Test void returnInsidePerformedParagraphUsesItsSymbolicBoundary() throws Exception {
        var p=CicsCommandContractTest.publish("PERFORM WORK-P\nCALL 'AFTERPGM'\nGOBACK.\n"
            +"WORK-P.\nEXEC CICS RETURN CHANNEL('CH') NOHANDLE\nEND-EXEC.");
        var c=p.path("controlTopology").path("sourceContinuations");
        assertEquals(1,c.size());
        assertEquals("COMPLETE",c.get(0).path("target").path("kind").asText());
    }
}
