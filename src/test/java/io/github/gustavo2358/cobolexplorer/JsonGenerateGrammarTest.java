package io.github.gustavo2358.cobolexplorer;

import io.github.gustavo2358.cobolexplorer.antlr.CobolLexer;
import io.github.gustavo2358.cobolexplorer.antlr.CobolParser;
import org.antlr.v4.runtime.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import static io.github.gustavo2358.cobolexplorer.semanticproduct.CobolSemanticProduct.*;
import static org.junit.jupiter.api.Assertions.*;

/** Grammar/AST boundary; generated JSON values and runtime effects remain unproved. */
class JsonGenerateGrammarTest {
    private static final String DATA = "01 OUT-TEXT PIC X(200).\n01 SRC.\n"
        + "05 ITEM-TEXT PIC X(8).\n05 FLAG PIC X.\n88 IS-ON VALUE 'Y'.\n"
        + "05 NULL-FLAG PIC X.\n88 IS-NULL VALUE 'Y'.\n01 N PIC 9(4).\n";
    private static String program(String body) { return ScalarMoveCheckpoint4ATest.program(DATA, body); }
    private static AstBoundaryTestSupport.Analysis analyze(String body) {
        return AstBoundaryTestSupport.analyze(program(body), "json-generate.cbl");
    }
    private static List<Ast.PreservedStatement> json(AstBoundaryTestSupport.Analysis a) {
        return AstBoundaryTestSupport.nodes(a, Ast.PreservedStatement.class).stream()
            .filter(s -> s.grammarRule().equals("jsonGenerateStatement")).toList();
    }
    private static Set<String> targets(String body) {
        var p = ScalarMoveCheckpoint4ATest.publish(program(body));
        return p.calls().stream().map(c -> ((LiteralCallTarget)c.target()).text())
            .collect(java.util.stream.Collectors.toSet());
    }
    @Test void preservesBothSentinelsAndObservedStatementWithoutInventingEffects() {
        String body = "CALL 'BEFORE'.\nJSON GENERATE OUT-TEXT FROM SRC\n"
            + "SUPPRESS ITEM-TEXT\nEND-JSON.\nCALL 'AFTER'.\nGOBACK.";
        var a = analyze(body); var statement = json(a).get(0);
        assertEquals(Set.of("BEFORE", "AFTER"), targets(body));
        assertEquals(1, json(a).size());
        assertTrue(statement.effects().isEmpty());
        assertTrue(statement.meta().provenance().exact());
        assertFalse(statement.writtenText().contains("AFTER"));
        assertTrue(statement.operands().stream().anyMatch(o -> o.value() instanceof Ast.DataReference r && r.baseName().equals("OUT-TEXT")));
        AstBoundaryTestSupport.assertActualProductsJoin(a);
        var p = ScalarMoveCheckpoint4ATest.publish(program(body));
        assertTrue(p.statements().stream().anyMatch(ObservedStatement.class::isInstance));
        assertFalse(p.gaps().isEmpty(), "recognized syntax does not prove JSON effects");
    }
    @Test void acceptsIbmPhrasesAndKeepsTheFollowingStatementOutsideJson() {
        var phrases = List.of("", "COUNT N", "COUNT IN N", "NAME SRC OMITTED",
            "NAME OF ITEM-TEXT IS 'item' FLAG IS 'flag'", "SUPPRESS ITEM-TEXT FLAG",
            "SUPPRESS ITEM-TEXT WHEN SPACES OR LOW-VALUES",
            "SUPPRESS WHEN ZERO SPACE", "SUPPRESS EVERY WHEN SPACES",
            "SUPPRESS EVERY NUMERIC WHEN ZEROS", "SUPPRESS EVERY NONNUMERIC WHEN HIGH-VALUES",
            "SUPPRESS ITEM-TEXT WHEN SPACE\nEVERY NUMERIC WHEN ZERO FLAG",
            "CONVERTING FLAG TO BOOLEAN USING 'Y'",
            "CONVERTING FLAG JSON BOOL IS-ON", "CONVERTING FLAG TO JSON NULL USING SPACE",
            "CONVERTING FLAG BOOLEAN 'Y'\nALSO ITEM-TEXT NULL LOW-VALUES",
            "INDICATING ITEM-TEXT IS JSON NULL USING IS-NULL",
            "INDICATING ITEM-TEXT NULL 'Y' IN NULL-FLAG\nALSO FLAG IS NULL IS-NULL",
            "ENCODING 1208", "ENCODING N", "ENCODING FROM CODEPAGE",
            "COUNT IN N\nINDICATING ITEM-TEXT NULL IS-NULL\nENCODING 1208\n"
                + "NAME SRC OMITTED\nSUPPRESS FLAG WHEN SPACES\nCONVERTING FLAG BOOL IS-ON");
        for (String phrase : phrases) {
            String body = "JSON GENERATE OUT-TEXT FROM SRC\n" + phrase + "\nEND-JSON\nCALL 'AFTER'.\nGOBACK.";
            var a = analyze(body);
            assertEquals(1, json(a).size(), phrase);
            assertEquals(Set.of("AFTER"), targets(body), phrase);
            assertFalse(json(a).get(0).writtenText().contains("AFTER"), phrase);
        }
    }
    @Test void exceptionArmsAndNestedJsonAreRetainedOnceWithTheirOwnScope() {
        String body = "JSON GENERATE OUT-TEXT FROM SRC\nON EXCEPTION\n"
            + "CALL 'FAILED'\nJSON GENERATE OUT-TEXT FROM SRC\n"
            + "ON EXCEPTION CALL 'NESTFAIL' END-CALL\nNOT ON EXCEPTION CALL 'NESTOK' END-JSON\n"
            + "NOT ON EXCEPTION CALL 'OK' END-JSON\nCALL 'AFTER'.\nGOBACK.";
        var a = analyze(body); var statements = json(a);
        assertEquals(2, statements.size());
        assertEquals(2, statements.get(0).clauses().size());
        assertEquals(2, statements.get(0).clauses().get(0).nestedStatements().size());
        assertEquals(1, statements.get(0).clauses().get(1).nestedStatements().size());
        assertEquals(2, statements.get(1).clauses().size());
        assertEquals(5, AstBoundaryTestSupport.nodes(a, Ast.CallStatement.class).size());
        assertEquals(Set.of("FAILED", "NESTFAIL", "NESTOK", "OK", "AFTER"), targets(body));
        AstBoundaryTestSupport.assertActualProductsJoin(a);
    }
    @Test void supportsOptionalScopeTerminatorsAndOuterIfPerformEvaluate() {
        for (String body : List.of(
            "JSON GENERATE OUT-TEXT FROM SRC\nCALL 'AFTER'.\nGOBACK.",
            "JSON GENERATE OUT-TEXT FROM SRC\nEXCEPTION CALL 'FAILED'.\nCALL 'AFTER'.\nGOBACK.",
            "JSON GENERATE OUT-TEXT FROM SRC\nNOT EXCEPTION CALL 'OK' END-JSON\nCALL 'AFTER'.\nGOBACK.",
            "IF FLAG = 'Y'\nJSON GENERATE OUT-TEXT FROM SRC END-JSON\n"
                + "ELSE CALL 'OTHER' END-IF\nCALL 'AFTER'.\nGOBACK.",
            "PERFORM 2 TIMES\nJSON GENERATE OUT-TEXT FROM SRC END-JSON\n"
                + "CALL 'BODY' END-PERFORM\nCALL 'AFTER'.\nGOBACK.",
            "EVALUATE FLAG\nWHEN 'Y' JSON GENERATE OUT-TEXT FROM SRC\n"
                + "SUPPRESS FLAG WHEN SPACE END-JSON\nWHEN OTHER CALL 'OTHER'\n"
                + "END-EVALUATE\nCALL 'AFTER'.\nGOBACK.")) {
            assertEquals(1, json(analyze(body)).size());
            assertTrue(targets(body).contains("AFTER"));
        }
    }
    @Test void recognizesReferencesAndContextSensitiveNames() {
        String body = "JSON GENERATE OUT-TEXT(2:100) FROM SRC\n"
            + "CONVERTING FLAG BOOL IS-ON END-JSON\nDISPLAY JSON-CODE\nCALL 'AFTER'.\nGOBACK.";
        var a = analyze(body);
        assertTrue(AstBoundaryTestSupport.nodes(a, Ast.SpecialRegisterExpression.class).stream()
            .anyMatch(r -> r.registerName().equals("JSON-CODE")));
        assertTrue(AstBoundaryTestSupport.nodes(a, Ast.DataReference.class).stream().anyMatch(r -> r.baseName().equals("IS-ON")));
        assertTrue(json(a).get(0).operands().stream().anyMatch(o -> o.context() == Ast.StatementOperandContext.CONDITION_VALUE
            && o.value() instanceof Ast.DataReference r && r.baseName().equals("IS-ON")));
        var condition = a.resolution().entries().stream().filter(e -> e.occurrence().writtenText().equals("IS-ON"))
            .findFirst().orElseThrow();
        assertEquals(ResolutionContracts.ResolutionStatus.RESOLVED, condition.status());
        assertEquals(ResolutionContracts.ReferenceKind.CONDITION, condition.selectedCandidate().orElseThrow().kind());
        var names = ScalarMoveCheckpoint4ATest.publish(ScalarMoveCheckpoint4ATest.program(
            "01 NAME PIC X.\n01 CODEPAGE PIC 9.", "MOVE 'X' TO NAME\nMOVE 1 TO CODEPAGE\nGOBACK."));
        assertEquals(2, names.moves().size());
    }
    @Test void sourceAlternativesEnterEachHandlerWithoutBypassingThem() {
        String body = "JSON GENERATE OUT-TEXT FROM SRC\n"
            + "ON EXCEPTION CALL 'FAILED' END-CALL GOBACK\n"
            + "NOT ON EXCEPTION CALL 'OK' END-CALL GOBACK END-JSON\n"
            + "CALL 'AFTER'.\nGOBACK.";
        var p = ScalarMoveCheckpoint4ATest.publish(program(body));
        var t = p.controlTopology().orElseThrow();
        var jsonId = p.statements().stream().filter(ObservedStatement.class::isInstance).findFirst().orElseThrow().header().id();
        var targets = t.sourceContinuations().stream().filter(c -> c.statement().equals(("statement:" + jsonId.localId())))
            .map(c -> c.target().reference()).collect(java.util.stream.Collectors.toSet());
        var handlers = p.calls().stream().filter(c -> !((LiteralCallTarget)c.target()).text().equals("AFTER"))
            .map(c -> "statement:" + c.header().id().localId()).collect(java.util.stream.Collectors.toSet());
        assertEquals(handlers, targets);
        assertTrue(t.outcomes().stream().filter(o -> o.statement().equals(("statement:" + jsonId.localId())))
            .allMatch(o -> o.kind() == io.github.gustavo2358.cobolexplorer.semanticproduct.ControlTopology.OutcomeKind.UNKNOWN_LOCAL));
    }
    @Test void nestedIfCompletesAfterJsonAndNeverInTheOtherExceptionArm() {
        String body = "JSON GENERATE OUT-TEXT FROM SRC\nON EXCEPTION\n"
            + "IF FLAG = 'Y' CALL 'FAILED' END-IF\n"
            + "NOT ON EXCEPTION CALL 'OK' END-CALL END-JSON\nCALL 'AFTER'.\nGOBACK.";
        var p = ScalarMoveCheckpoint4ATest.publish(program(body));
        var after = p.calls().stream().filter(c -> ((LiteralCallTarget)c.target()).text().equals("AFTER"))
            .findFirst().orElseThrow().header().id();
        assertEquals(Optional.of(after), p.ifs().get(0).normalContinuation().statement());
        assertEquals(Optional.of(after), p.ifs().get(0).continuation());
    }
    @TempDir Path directory;
    @Test void copyAndSplitKeywordRetainPhysicalOriginsAndFollowingCalls() throws Exception {
        var copy = directory.resolve("JSONBODY.cpy");
        Files.writeString(copy, "           JS\n      -    ON GENERATE OUT-TEXT FROM SRC\n"
            + "           ON EXCEPTION CALL 'FAILED' END-CALL\n"
            + "           NOT ON EXCEPTION CALL 'OK' END-CALL\n           END-JSON.\n");
        var input = directory.resolve("JGEN.cbl");
        Files.writeString(input, program("CALL 'BEFORE'.\nCOPY JSONBODY.\nCALL 'AFTER'.\nGOBACK.")
            .lines().map(l -> "       " + l + "\n").collect(java.util.stream.Collectors.joining()));
        var out = directory.resolve("out");
        ExplorerMain.main(new String[]{"--source", input.toString(), "--copybooks", directory.toString(), "--output", out.toString()});
        var sp = new ObjectMapper().readTree(out.resolve("cobol-semantic-product.json").toFile());
        assertFalse(Files.readString(out.resolve("tree-data.js")).contains("\"phase\":\"PARSER\""));
        var seen = new HashSet<String>();
        for (var statement : sp.path("statements")) if (statement.path("variant").asText().equals("CALL")) {
            String target = statement.path("target").path("text").asText(); seen.add(target);
            String file = statement.path("header").path("provenance").path("original").path("file").asText();
            assertTrue(file.endsWith(Set.of("FAILED", "OK").contains(target) ? "JSONBODY.cpy" : "JGEN.cbl"), file);
        }
        assertEquals(Set.of("BEFORE", "FAILED", "OK", "AFTER"), seen);
    }
    @Test void rejectsMalformedJsonInsteadOfSwallowingArbitraryTokens() {
        for (String body : List.of("JSON GENERATE OUT-TEXT SRC END-JSON.",
            "JSON GENERATE OUT-TEXT FROM SRC SUPPRESS END-JSON.",
            "JSON GENERATE OUT-TEXT FROM SRC ENCODING END-JSON.",
            "JSON GENERATE OUT-TEXT FROM SRC CONVERTING FLAG END-JSON.",
            "JSON GENERATE OUT-TEXT FROM SRC NAME SRC END-JSON.",
            "JSON GENERATE OUT-TEXT FROM SRC\nSUPPRESS EVERY NUMERIC END-JSON.",
            "JSON GENERATE OUT-TEXT FROM SRC\nSUPPRESS FLAG WHEN 'Y' END-JSON.",
            "JSON GENERATE OUT-TEXT FROM SRC\nCOUNT N COUNT N END-JSON.",
            "JSON GENERATE OUT-TEXT FROM SRC\nNAME SRC OMITTED COUNT N END-JSON.")) {
            var lexer = new CobolLexer(CharStreams.fromString(program(body + "\nGOBACK.")));
            var parser = new CobolParser(new CommonTokenStream(lexer));
            parser.removeErrorListeners(); parser.startRule();
            assertTrue(parser.getNumberOfSyntaxErrors() > 0, body);
        }
    }
}
