package com.imd.cobolexplorer;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.imd.cobolexplorer.semanticproduct.ControlTopology.*;

class PerformControlCompletionTest {
    @Test void paragraphExitIsARegionalTransfer() {
        var t=ControlTopologyAuthorityTest.publish("MAIN.\nPERFORM P.\nGOBACK.\nP.\nEXIT PARAGRAPH\nCALL 'DEAD'.\n").controlTopology().orElseThrow();
        assertTrue(t.outcomes().stream().anyMatch(o->o.kind()==OutcomeKind.EXPLICIT_TRANSFER&&o.target().kind().name().equals("ESCAPE")));
    }
    @Test void inlineExitBypassesRepetition() {
        var t=ControlTopologyAuthorityTest.publish("MAIN.\nPERFORM 2 TIMES\nEXIT PERFORM\nCALL 'DEAD'\nEND-PERFORM\nCALL 'LIVE'\nGOBACK.\n").controlTopology().orElseThrow();
        assertTrue(t.outcomes().stream().anyMatch(o->o.kind()==OutcomeKind.EXPLICIT_TRANSFER&&o.target().kind().name().equals("ESCAPE")));
    }
    @Test void sectionHasItsOwnInvocationBoundary() {
        var t=ControlTopologyAuthorityTest.publish("MAIN.\nPERFORM S.\nGOBACK.\nS SECTION.\nP.\nCONTINUE.\nOUTSIDE SECTION.\nQ.\nCALL 'DEAD'.\n").controlTopology().orElseThrow();
        assertEquals(1,t.bindings().size());
        var endpoint=t.boundaries().stream().filter(b->b.id().equals(t.bindings().get(0).endpoint())).findFirst().orElseThrow();
        assertTrue(t.regions().stream().anyMatch(r->r.id().equals(endpoint.region())&&r.kind().name().equals("SECTION")));
    }

    @Test void paragraphMayStartInsideItsThroughSection() {
        var t=ControlTopologyAuthorityTest.publish("MAIN.\nPERFORM P THRU S.\nGOBACK.\nS SECTION.\nEARLIER-P.\nCALL 'DEAD'.\nP.\nCONTINUE.\nQ.\nCONTINUE.\nOUTSIDE SECTION.\nCALL 'DEAD'.\n").controlTopology().orElseThrow();
        assertEquals(1,t.bindings().size());var b=t.bindings().get(0);
        var range=t.regions().stream().filter(r->r.id().equals(b.region())).findFirst().orElseThrow();
        var start=t.regions().stream().filter(r->r.id().equals(range.entry().reference())).findFirst().orElseThrow();
        var end=t.boundaries().stream().filter(r->r.id().equals(b.endpoint())).findFirst().orElseThrow();
        assertEquals(RegionKind.PARAGRAPH,start.kind());assertEquals(start.parent(),end.region());
        assertEquals(end.region(),range.regions().get(range.regions().size()-1));
    }
    @Test void varyingLevelsHaveIndependentTestsAndCurrentFromResets() {
        for(boolean before:new boolean[]{true,false}) {
            String body="MAIN.\nPERFORM P WITH TEST "+(before?"BEFORE":"AFTER")+"\n"
                +"VARYING I FROM 1 BY 1 UNTIL I > 2\nAFTER J FROM I BY 1 UNTIL J > 2\nAFTER K FROM J BY -1 UNTIL K < 1\n"
                +"CALL PGM\nGOBACK.\nP.\nCONTINUE.\n";
            var port=ScalarMoveCheckpoint4ATest.publish(ControlTopologyAuthorityTest.source(body).replace("PROCEDURE DIVISION.","01 I PIC 9(4).\n01 J PIC 9(4).\n01 K PIC 9(4).\nPROCEDURE DIVISION."));
            var t=port.controlTopology().orElseThrow();assertEquals(1,t.bindings().size());var b=t.bindings().get(0);
            assertEquals("initial-1",b.entryPhase());assertEquals(before?"update-3":"test-3",b.completionPhase());
            assertEquals("initial-2",edge(b,"initial-1","next"));assertEquals("initial-3",edge(b,"initial-2","next"));
            assertEquals(before?"test-1":"BODY",edge(b,"initial-3","next"));
            assertEquals("RESUME",edge(b,"test-1","true"));
            if(before) {
                assertEquals("test-2",edge(b,"test-1","false"));assertEquals("test-3",edge(b,"test-2","false"));
                assertEquals("BODY",edge(b,"test-3","false"));assertEquals("update-2",edge(b,"test-3","true"));
                assertEquals("reset-2-3",edge(b,"update-2","next"));assertEquals("test-2",edge(b,"reset-2-3","next"));
                assertEquals("reset-1-2",edge(b,"update-1","next"));assertEquals("reset-1-3",edge(b,"reset-1-2","next"));
                assertEquals("test-1",edge(b,"reset-1-3","next"));
            } else {
                assertEquals("test-2",edge(b,"test-3","true"));assertEquals("test-1",edge(b,"test-2","true"));
                assertEquals("update-3",edge(b,"test-3","false"));assertEquals("BODY",edge(b,"update-3","next"));
                assertEquals("initial-3",edge(b,"update-2","next"));assertEquals("initial-2",edge(b,"update-1","next"));
            }
            var p=port.statements().stream().filter(com.imd.cobolexplorer.semanticproduct.CobolSemanticProduct.ProcedurePerformFact.class::isInstance)
                .map(com.imd.cobolexplorer.semanticproduct.CobolSemanticProduct.ProcedurePerformFact.class::cast).findFirst().orElseThrow();
            assertEquals(2,p.varying().orElseThrow().afterLoops().size());
            assertEquals(1,p.loop().orElseThrow().condition().references().size());
            for(var loop:p.varying().orElseThrow().afterLoops())assertEquals(1,loop.condition().references().size());
            for(var phase:b.phases())assertTrue(phase.level()>=1&&phase.level()<=3);
        }
    }
    @Test void inlineVaryingHasTheSameIntegerFootprintAsProcedureVarying() {
        var port=ScalarMoveCheckpoint4ATest.publish(ControlTopologyAuthorityTest.source("MAIN.\nPERFORM VARYING I FROM 1 BY 1 UNTIL I > 2\nCONTINUE\nEND-PERFORM\nGOBACK.\n")
            .replace("PROCEDURE DIVISION.","01 I PIC 9(4).\nPROCEDURE DIVISION."));
        var p=port.statements().stream().filter(com.imd.cobolexplorer.semanticproduct.CobolSemanticProduct.ProcedurePerformFact.class::isInstance)
            .map(com.imd.cobolexplorer.semanticproduct.CobolSemanticProduct.ProcedurePerformFact.class::cast).findFirst().orElseThrow();
        assertTrue(p.varying().orElseThrow().controls().get(0).references().get(0).wholeItemAccess().isPresent());
    }
    private static String edge(Binding b,String phase,String role) {
        return b.phases().stream().filter(p->p.id().equals(phase)).findFirst().orElseThrow().edges().stream()
            .filter(e->e.role().equals(role)).findFirst().orElseThrow().target();
    }
}
