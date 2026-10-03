package io.github.gustavo2358.cobolexplorer;

import com.fasterxml.jackson.databind.json.JsonMapper;
import io.github.gustavo2358.cobolexplorer.semanticproduct.*;
import io.github.gustavo2358.cobolexplorer.semanticproduct.consumer.SemanticGapAssessment;
import io.github.gustavo2358.cobolexplorer.semanticproduct.transport.SemanticProductJsonWriter;
import org.junit.jupiter.api.Test;
import java.util.*;
import static io.github.gustavo2358.cobolexplorer.semanticproduct.CobolSemanticProduct.*;
import static io.github.gustavo2358.cobolexplorer.semanticproduct.consumer.SemanticGapAssessment.Status.*;
import static org.junit.jupiter.api.Assertions.*;

class SemanticGapAssessmentTest {
    private static CobolSemanticPort publish(String data, String code) {
        var analysis = AstBoundaryTestSupport.analyze(ScalarMoveCheckpoint4ATest.program(data, code), "gap-assessment.cbl");
        var p = EofUnitBoundaryTest.publish(analysis, 0, StorageLayoutSemantics.Profile.UNSPECIFIED);
        return p;
    }
    private static List<SemanticGapAssessment.Row> rows(CobolSemanticPort p, String code) {
        return SemanticGapAssessment.assess(p).gaps().stream().filter(g -> g.code().equals(code)).toList();
    }
    private static CobolSemanticPort copy(CobolSemanticPort p, List<Gap> gaps, boolean topology) {
        return copy(p, gaps, topology ? p.controlTopology() : Optional.empty());
    }
    private static CobolSemanticPort copy(CobolSemanticPort p, List<Gap> gaps, Optional<ControlTopology> topology) {
        return CobolSemanticPort.open(new State(p.unit(), p.policy(), p.dataDeclarations(), p.statements(), gaps,
                p.coverage(), p.entryInventory(), p.storageIndependence(), p.storage(), p.fileInventory(),
                p.sourceDependencies(), p.ordinaryContinuations(), topology,
                topology.isPresent() ? p.factDependencies() : Optional.empty(), p.nominalValues()));
    }
    @Test void noOpAndUnknownStatementAreAssessedIndependently() {
        var p = publish("01 N PIC 9.", "CONTINUE.\nADD 1 TO N.\nGOBACK.");
        var observed = rows(p, "OBSERVED_STATEMENT_UNSUPPORTED");
        assertTrue(observed.stream().anyMatch(r -> r.status() == SUPERSEDED && r.rule().equals("NO_OP_WITH_CONTROL")));
        assertTrue(observed.stream().anyMatch(r -> r.status() == PARTIAL && r.rule().equals("EFFECT_SUMMARY_PUBLISHED")));
        assertTrue(rows(copy(p, p.gaps(), false), "OBSERVED_STATEMENT_UNSUPPORTED").stream().noneMatch(r -> r.status() == SUPERSEDED));
    }
    @Test void currentInvocationRetiresIsolationButDoesNotProveBodyOrNumericValues() {
        var p = publish("01 N PIC 9.", "PERFORM BODY-A.\nPERFORM BODY-A.\nGOBACK.\nBODY-A.\nADD 1 TO N.");
        assertFalse(rows(p, "PERFORM_ISOLATED_PRIMARY_NOT_PROVEN").isEmpty());
        assertTrue(rows(p, "PERFORM_ISOLATED_PRIMARY_NOT_PROVEN").stream().allMatch(r -> r.status() == SUPERSEDED));
        assertTrue(rows(p, "PERFORM_RANGE_CONTROL_NOT_PROVEN").stream().allMatch(r -> r.status() == PARTIAL));
        assertTrue(rows(copy(p, p.gaps(), false), "PERFORM_ISOLATED_PRIMARY_NOT_PROVEN").stream().allMatch(r -> r.status() == OPEN));
        var unresolved = publish("", "PERFORM ABSENT-PARAGRAPH.\nGOBACK.");
        assertTrue(SemanticGapAssessment.assess(unresolved).gaps().stream()
                .filter(r -> r.code().startsWith("PERFORM_")).noneMatch(r -> r.status() == SUPERSEDED));
    }
    @Test void transitiveUnknownProofCannotRetireLegacyControlDiagnostics() {
        var p = publish("01 N PIC 9.", "PERFORM BODY-A.\nGOBACK.\nBODY-A.\nADD 1 TO N.");
        var t = p.controlTopology().orElseThrow();
        var dependency = t.proofs().stream().filter(proof -> proof.rule().equals("statement-scope")).findFirst().orElseThrow();
        var weakened = t.proofs().stream().map(proof -> proof.id().equals(dependency.id())
                ? new ControlTopology.Proof(proof.id(), ControlTopology.ProofKind.PARTIAL_UNKNOWN,
                    proof.rule(), proof.provenance(), proof.dependencies()) : proof).toList();
        var weakerTopology = new ControlTopology(t.authority(), t.occurrences(), t.regions(), t.boundaries(), t.outcomes(),
                t.bindings(), weakened, t.exceptionalEvents(), t.fileFlows(), t.sourceContinuations(), t.entryPoints(),
                t.conditionRegistrations(), t.conditionEvents());
        var assessment = rows(copy(p, p.gaps(), Optional.of(weakerTopology)), "PERFORM_ISOLATED_PRIMARY_NOT_PROVEN");
        assertFalse(assessment.isEmpty());
        assertTrue(assessment.stream().allMatch(r -> r.status() == OPEN));
    }
    @Test void hostEffectsDoNotCloseCicsRuntimeAndNeighboringCommands() {
        var p = publish("01 BUF PIC X(8).", "EXEC CICS SYNCPOINT END-EXEC.\n"
                + "EXEC CICS SEND TEXT FROM(BUF) LENGTH(8) END-EXEC.\nGOBACK.");
        var rows = rows(p, "CICS_COMMAND_EFFECTS_NOT_MODELED");
        assertEquals(List.of(PARTIAL, OPEN), rows.stream().map(SemanticGapAssessment.Row::status).toList());
        assertTrue(rows.get(0).remaining().contains("runtime"));
    }
    @Test void nominalMoveDoesNotClosePhysicalProof() {
        var p = publish("01 REC-A.\n 05 NAME-A PIC X(8) OCCURS 2.\n01 NAME-B PIC X(8).",
                "MOVE NAME-A(1) TO NAME-B.\nCALL NAME-B.\nGOBACK.");
        var moves = rows(p, "MOVE_IDENTITY_NOT_PROVEN");
        assertFalse(moves.isEmpty());
        assertTrue(moves.stream().allMatch(r -> r.status() == PARTIAL));
        assertTrue(rows(p, "DYNAMIC_CALL_TARGET_VALUE_UNKNOWN").stream().allMatch(r -> r.status() == OPEN));
    }
    @Test void textPredicateClosesOnlyPredicateAndMembershipClosesOnlyContainment() {
        var p = publish("01 NAME-A PIC X(8).", "IF NAME-A = LOW-VALUES OR SPACES\n"
                + "CONTINUE\nEND-IF.\nGOBACK.");
        assertFalse(rows(p, "CONDITION_SEMANTICS_NOT_AVAILABLE").isEmpty());
        assertTrue(rows(p, "CONDITION_SEMANTICS_NOT_AVAILABLE").stream().allMatch(r -> r.status() == SUPERSEDED));
        assertTrue(rows(p, "IF_OUTSIDE_SIMPLE_PROFILE").stream().allMatch(r -> r.status() == PARTIAL));
        var nested = publish("01 N PIC 9.", "PERFORM UNTIL N > 2\nCONTINUE\nADD 1 TO N\nEND-PERFORM.\nGOBACK.");
        assertFalse(rows(nested, "CONTAINMENT_NOT_PROJECTED").isEmpty());
        assertTrue(rows(nested, "CONTAINMENT_NOT_PROJECTED").stream().allMatch(r -> r.status() == SUPERSEDED));
    }
    @Test void unknownCodesAndWrongDimensionsRemainOpenWithDuplicateProvenanceRetained() {
        var p = publish("", "CONTINUE.\nGOBACK.");
        var s = p.statements().get(0);
        var gaps = new ArrayList<>(p.gaps());
        gaps.add(new Gap(s.header().id(), GapScope.CAPABILITY, "FUTURE_UNKNOWN", "new diagnostic", s.header().provenance()));
        gaps.add(new Gap(s.header().id(), GapScope.ANALYSIS_INPUT, "OBSERVED_STATEMENT_UNSUPPORTED", "other dimension", s.header().provenance()));
        gaps.add(gaps.get(gaps.size() - 2));
        var a = SemanticGapAssessment.assess(copy(p, gaps, true));
        assertEquals(gaps.size(), a.counts().raw());
        assertEquals(2, a.gaps().stream().filter(g -> g.code().equals("FUTURE_UNKNOWN") && g.status() == OPEN).count());
        assertEquals(OPEN, a.gaps().get(a.gaps().size() - 2).status());
        for (int i = 0; i < gaps.size(); i++) {
            assertEquals(i, a.gaps().get(i).gapIndex());
            assertEquals(gaps.get(i).provenance(), a.gaps().get(i).provenance());
        }
    }
    @Test void everyEvidencePointerResolvesAndAssessmentCannotMutateSemanticProduct() throws Exception {
        var p = publish("01 NAME-A PIC X(8).", "IF NAME-A = LOW-VALUES OR SPACES\nCONTINUE\nEND-IF.\n"
                + "MOVE 'TARGET01' TO NAME-A.\nEXEC CICS SYNCPOINT END-EXEC.\nGOBACK.");
        var before = SemanticProductJsonWriter.serialize(p);
        var report = SemanticGapAssessment.assess(List.of(p));
        assertEquals(report, SemanticGapAssessment.assess(List.of(p)));
        assertArrayEquals(before, SemanticProductJsonWriter.serialize(p));
        var json = new JsonMapper().readTree(before);
        int checked = 0;
        for (var row : report.units().get(0).gaps()) for (var e : row.evidence()) {
            assertFalse(json.at(e.pointer()).isMissingNode(), e.pointer());
            assertFalse(json.at(e.pointer()).isNull(), e.pointer());
            checked++;
        }
        assertTrue(checked > 0);
        assertEquals(report.counts().raw(), report.counts().superseded() + report.counts().pending());
        assertEquals(report.counts().pending(), report.counts().partial() + report.counts().open());
    }
    @Test void identicalLocalIdsInDifferentUnitsDoNotShareProofs() {
        var a = AstBoundaryTestSupport.analyze(
                EofUnitBoundaryTest.source("UNIT-A", "", true).replace("MOVE 'BEFORE01' TO TARGET.\nCALL TARGET.", "CONTINUE.")
                + EofUnitBoundaryTest.source("UNIT-B", "01 N PIC 9.", true).replace("MOVE 'BEFORE01' TO TARGET.\nCALL TARGET.", "ADD 1 TO N."), "units.cbl");
        var p = EofUnitBoundaryTest.publish(a, 0, StorageLayoutSemantics.Profile.UNSPECIFIED);
        var q = EofUnitBoundaryTest.publish(a, 1, StorageLayoutSemantics.Profile.UNSPECIFIED);
        var report = SemanticGapAssessment.assess(List.of(p, q));
        assertNotEquals(report.units().get(0).unit(), report.units().get(1).unit());
        assertEquals(1, report.units().get(0).counts().superseded());
        assertEquals(0, report.units().get(1).counts().superseded());
        assertEquals(0, SemanticGapAssessment.assess(List.of()).counts().raw());
    }
}
