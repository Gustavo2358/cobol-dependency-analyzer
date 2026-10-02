package io.github.gustavo2358.cobolexplorer;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RemarksNormalizationCompatibilityTest {
    private static final String HEADER = "       IDENTIFICATION DIVISION.\n"
            + "       PROGRAM-ID. REMTEST.\n       REMARKS.\n";

    @Test
    void acceptsAsteriskDocumentationAcrossAreaAColumnsAndRecordTerminators() {
        for (String end : List.of("\n", "\r\n", "\r")) {
            for (int column = 8; column <= 11; column++) {
                String raw = HEADER.replace("\n", end)
                        + " ".repeat(column - 1) + "* VERSAO. CALL 'NOTCODE'." + end
                        + "           TEXTO EM AREA B SEM PONTO" + end
                        + "       PROCEDURE DIVISION." + end + "           GOBACK." + end;
                String normalized = normalize(raw).text();
                assertTrue(normalized.contains("*>CE * VERSAO. CALL 'NOTCODE'." + end), normalized);
                assertTrue(normalized.contains("*>CE TEXTO EM AREA B SEM PONTO" + end), normalized);
                assertTrue(normalized.contains("PROCEDURE DIVISION." + end + "    GOBACK." + end), normalized);
                assertEquals(7, normalized.split("\\r\\n|\\r|\\n").length);
                assertParses(normalized);
            }
        }
    }

    @Test
    void parsesMixedRemarksBannersWithoutCreatingCallsFromDocumentation() {
        String raw = HEADER + "           * ORIGINAL DOCUMENTATION\n"
                + "       ************************************************\n"
                + "       * CALL 'FAKE'. COPY ABSENT. AUTHOR.\n"
                + "      * NORMAL INDICATOR COMMENT\n"
                + "           LAST REMARK\n"
                + "       *> FLOATING COMMENT\n"
                + "       DATA DIVISION.\n       WORKING-STORAGE SECTION.\n"
                + "       01 WS-PGM PIC X(8).\n       PROCEDURE DIVISION.\n"
                + "           MOVE 'DYNAMIC' TO WS-PGM.\n"
                + "           CALL WS-PGM.\n           CALL 'REALPGM'.\n           GOBACK.\n";
        var tokens = assertParses(normalize(raw).text());
        assertEquals(2, tokens.getTokens().stream().filter(t -> t.getText().equals("CALL")).count());
        assertFalse(tokens.getTokens().stream().anyMatch(t -> t.getText().equals("'FAKE'")));
    }

    @Test
    void everyNonAsteriskAreaAItemStillEndsTheRemarksRegion() {
        for (String boundary : List.of("ENVIRONMENT DIVISION.", "DATA DIVISION.",
                "PROCEDURE DIVISION.", "AUTHOR.", "END-REMARKS.",
                "IDENTIFICATION DIVISION.", "END PROGRAM REMTEST.", "COPY REAL.",
                "CALL 'REAL'.", "UNKNOWN-HEADER.")) {
            String text = normalize(HEADER + "       * HEADER NOTE\n       " + boundary
                    + "\n       * OUTSIDE\n").text();
            assertTrue(text.contains("*>CE * HEADER NOTE\n"), text);
            assertTrue(text.contains("\n" + boundary + "\n* OUTSIDE\n"), text);
            assertFalse(text.contains("*>CE * OUTSIDE"), boundary);
        }
    }

    @Test
    void explicitEndRemarksInAreaBEndsTheExtension() {
        String text = normalize(HEADER + "       * NOTE\n           END-REMARKS.\n"
                + "       * OUTSIDE\n").text();
        assertTrue(text.contains("*>CE * NOTE\n    END-REMARKS.\n* OUTSIDE\n"), text);
    }

    @Test
    void otherCommentOwnersAndUnrecognizedHeadersDoNotEnableTheExtension() {
        for (String owner : List.of("AUTHOR.", "INSTALLATION.", "DATE-WRITTEN.",
                "DATE-COMPILED.", "SECURITY.", "PROGRAM-ID. P.", "REMARKS.X", "REMARKS")) {
            String text = normalize("       " + owner + "\n       * OUTSIDE\n").text();
            assertTrue(text.endsWith("\n* OUTSIDE\n"), text);
        }
        assertEquals("    REMARKS.\n* OUTSIDE\n",
                normalize("           REMARKS.\n       * OUTSIDE\n").text());
    }

    @Test
    void asteriskOutsideRemarksAndCopyColumn72RemainActive() {
        assertEquals("* BEFORE ID\n", normalize("       * BEFORE ID\n").text());
        String afterProcedure = normalize(HEADER + "       PROCEDURE DIVISION.\n"
                + "       * NOT AN EXECUTABLE COMMENT\n").text();
        assertTrue(afterProcedure.endsWith("PROCEDURE DIVISION.\n* NOT AN EXECUTABLE COMMENT\n"));
        String copy = "       COPY REAL.";
        String raw = copy + " ".repeat(71 - copy.length()) + "*12345678\n";
        assertEquals(72, raw.indexOf('*') + 1);
        assertTrue(normalize(raw).text().stripTrailing().endsWith("*"));
        assertFalse(normalize(raw).text().contains("*>CE"));
    }

    @Test
    void preservesPhysicalProvenanceAndFollowingExecutableText() {
        String raw = HEADER + "       * revisão 😀 'sem fechar\r\n"
                + "       PROCEDURE DIVISION.\n           CALL 'REAL'.\n           GOBACK.\n";
        var result = normalize(raw);
        String text = result.text();
        int comment = text.indexOf("*>CE * revisão");
        var origin = result.sourceMap().provenance(comment, comment + 4);
        assertEquals(4, origin.original().startLine());
        assertFalse(origin.exact());
        int call = text.codePointCount(0, text.indexOf("CALL 'REAL'"));
        var executable = result.sourceMap().provenance(call, call + 4);
        assertEquals(6, executable.original().startLine());
        assertEquals(11, executable.original().startColumn());
        assertTrue(executable.exact());
        assertTrue(text.contains("sem fechar\r\nPROCEDURE DIVISION."), text);
    }

    @Test
    void copyInRemarksDoesNotExpandButRealCopyKeepsItsOrigin(@TempDir Path directory) throws Exception {
        Files.writeString(directory.resolve("HEADER.cpy"), "       REMARKS.\n"
                + "       * COPY ABSENT. CALL 'FAKE'.\n");
        Files.writeString(directory.resolve("BODY.cpy"), "           CALL 'FROMCOPY'.\n");
        String raw = "       IDENTIFICATION DIVISION.\n       PROGRAM-ID. COPYREM.\n"
                + "       COPY HEADER.\n       PROCEDURE DIVISION.\n"
                + "       COPY BODY.\n           GOBACK.\n";
        var outcome = new PreprocessorEngine(Bindings.cobol(), new CopybookLibrary(directory))
                .process(normalize(raw).sourceMap(), "remarks.cbl");
        assertEquals(0, outcome.errors(), outcome.diagnostics().toString());
        assertEquals(0, outcome.unresolved(), outcome.diagnostics().toString());
        var tokens = assertParses(outcome.text());
        assertEquals(1, tokens.getTokens().stream().filter(t -> t.getText().equals("CALL")).count());
        int start = outcome.text().indexOf("CALL 'FROMCOPY'");
        var origin = outcome.sourceMap().provenance(start, start + 4);
        assertEquals("BODY.cpy", origin.original().file());
        assertEquals(1, origin.includeChain().size());
    }

    @Test
    void floatingCommentKeepsItsExistingBoundaryAndCannotHideAnAcceptedCall() {
        // The parser already accepts division headers in Area B. Preserve that
        // behavior after a floating comment; do not reinterpret it as REMARKS.
        String raw = HEADER + "       *> DOCUMENTATION BOUNDARY\n"
                + "           PROCEDURE DIVISION.\n           CALL 'REAL'.\n           GOBACK.\n";
        String text = normalize(raw).text();
        assertTrue(text.contains("\n*> DOCUMENTATION BOUNDARY\n    PROCEDURE DIVISION.\n"), text);
        var tokens = assertParses(text);
        assertEquals(1, tokens.getTokens().stream().filter(t -> t.getText().equals("CALL")).count());
    }

    @Test
    void invalidIndicatorIsNotRepairedInsideRemarks() {
        assertThrows(IllegalArgumentException.class,
                () -> normalize(HEADER + "      X* NOT A VALID INDICATOR\n"));
    }

    private static SourceNormalizer.Result normalize(String raw) {
        return SourceNormalizer.normalize(raw, "remarks.cbl", SourceNormalizer.SourceFormat.FIXED);
    }

    private static CommonTokenStream assertParses(String text) {
        var binding = Bindings.cobol();
        var lexer = binding.cobolLexer(CharStreams.fromString(text));
        var lexicalErrors = new java.util.ArrayList<String>();
        lexer.removeErrorListeners();
        lexer.addErrorListener(new BaseErrorListener() {
            @Override
            public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol,
                                    int line, int column, String message, RecognitionException error) {
                lexicalErrors.add(line + ":" + column + " " + message);
            }
        });
        var tokens = new CommonTokenStream(lexer);
        tokens.fill();
        assertTrue(lexicalErrors.isEmpty(), lexicalErrors.toString());
        var parser = binding.cobolParser(tokens);
        binding.cobolStart(parser);
        assertEquals(0, parser.getNumberOfSyntaxErrors(), text);
        return tokens;
    }
}
