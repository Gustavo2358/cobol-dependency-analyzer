package io.github.gustavo2358.cobolexplorer;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CicsCatalogueTest {
    static final List<String> FORMS=List.of("ASKTIME ABSTIME(CLOCK-X)","ASKTIME", "FORMATTIME ABSTIME(CLOCK-X) YYYYMMDD(AREA-X) DATESEP('-') TIME(AREA-X) TIMESEP", "FORMATTIME ABSTIME(CLOCK-X) YYDDD(AREA-X) MILLISECONDS(RESP-CD)", "ASSIGN APPLID(AREA-X)","ASSIGN SYSID(AREA-X)","INQUIRE PROGRAM(AREA-X)","SEND TEXT FROM(AREA-X) LENGTH(LENGTH OF AREA-X) ERASE FREEKB","WRITEQ TD QUEUE('JOBS') FROM(AREA-X) LENGTH(80)");
    static CicsCommandSemantics.Fact parse(String form){return CicsCommandSemantics.parse("EXEC CICS "+form+" END-EXEC").orElseThrow();}
    @Test void commandsHaveTheirOwnClosedSyntaxAndControl() {
        for(var form:FORMS)for(var response:List.of(""," NOHANDLE"," RESP(RESP-CD)"," NOHANDLE NOHANDLE")) {
            var c=parse(form+response);assertTrue(c.supported(),c.toString());
            var control=CicsCommandControl.qualify(c).orElseThrow();assertFalse(control.programReturn());
            assertEquals(response.isEmpty(),!control.unresolvedConditions().isEmpty());
            assertFalse(parse(form+" INVALID-X(AREA-X)").supported());
        }
        for(var form:List.of("FORMATTIME YYYYMMDD(AREA-X)","ASSIGN", "INQUIRE PROGRAM", "SEND TEXT", "WRITEQ TD QUEUE('JOBS')", "WRITEQ TD FROM(AREA-X)", "ASKTIME ABSTIME(CLOCK-X) ABSTIME(AREA-X)"))assertFalse(parse(form).supported(),form);
        assertTrue(CicsCommandSemantics.parse("EXEC CICS WRITEQ TS QUEUE('Q') FROM(B) END-EXEC").isEmpty());
    }
    @Test void producerPublishesDirectionAndOpenEffects()throws Exception {
        for(var form:FORMS) {
            var p=CicsMemoryLocalityTest.publish("01 CLOCK-X PIC S9(15) COMP-3.\n01 AREA-X PIC X(80).\n01 RESP-CD PIC S9(9) COMP.","EXEC CICS "+form+" NOHANDLE END-EXEC.\nCALL 'AFTERIO'.\nGOBACK.",false);
            var j=CicsAbendContractTest.json(p);var c=CicsCommandContractTest.command(j);assertEquals("2.54.0",j.path("contractVersion").asText());
            assertEquals("SUPPORTED",c.path("syntaxStatus").asText());assertFalse(c.hasNonNull("hostEffects"),"implicit environment writes remain open");
            for(var o:c.path("options"))if(o.hasNonNull("reference")) {
                var name=o.path("name").asText();boolean write=Set.of("RESP","RESP2","APPLID").contains(name)||form.startsWith("ASSIGN")||form.startsWith("ASKTIME")||form.startsWith("FORMATTIME")&&!Set.of("ABSTIME","DATESEP","TIMESEP").contains(name);
                assertEquals(write?"WRITE":"READ",o.path("reference").path("role").asText(),form+name);
            }
        }
    }
}
