package io.github.gustavo2358.cobolexplorer;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import io.github.gustavo2358.cobolexplorer.semanticproduct.CobolSemanticProduct.*;

class CicsLexicalCompatibilityTest {
    private static final Map<String,String> FILE_FORMS=Map.ofEntries(
        Map.entry("READ","INTO(BUF) RIDFLD(KEY)"),Map.entry("WRITE","FROM(BUF) RIDFLD(KEY)"),
        Map.entry("REWRITE","FROM(BUF)"),Map.entry("DELETE","RIDFLD(KEY)"),
        Map.entry("STARTBR","RIDFLD(KEY)"),Map.entry("READNEXT","INTO(BUF) RIDFLD(KEY)"),
        Map.entry("READPREV","INTO(BUF) RIDFLD(KEY)"),Map.entry("RESETBR","RIDFLD(KEY)"),
        Map.entry("ENDBR","REQID(1)"),Map.entry("UNLOCK","TOKEN(TOK)"));
    @Test void documentedAliasesPreserveEachCommandAndOperandRole() {
        var parser=new CicsFileControlAnalyzer();
        for(var form:FILE_FORMS.entrySet())for(var response:List.of("NOHANDLE","RESP(R) RESP2(R2)","")) {
            String code="EXEC CICS "+form.getKey()+" FILE('F') "+form.getValue()+" "+response+" END-EXEC";
            var file=parser.parse(code).orElseThrow();var alias=parser.parse(code.replace(" FILE("," DATASET(")).orElseThrow();
            assertTrue(alias.gaps().isEmpty(),alias.toString());assertEquals(file.command(),alias.command());assertEquals(file.targetMode(),alias.targetMode());assertEquals(file.literal(),alias.literal());
            assertEquals(file.options().stream().map(o->o.canonicalName()+":"+o.role()+":"+o.syntax().operand()).toList(),alias.options().stream().map(o->o.canonicalName()+":"+o.role()+":"+o.syntax().operand()).toList());
            assertFalse(parser.parse(code.replace(" FILE('F')"," FILE('F') DATASET('G')")).orElseThrow().gaps().isEmpty());
        }
        assertTrue(parser.parse("EXEC CICS INQUIRE DATASET('F') NOHANDLE END-EXEC").isEmpty());
        assertFalse(parser.parse("EXEC CICS WRITE DATASET('F') FROM(B) MYSTERY(X) END-EXEC").orElseThrow().gaps().isEmpty());
    }
    @Test void commentsPreserveQuotedPayloadAndOptionOffsets() {
        for(var newline:List.of("\n","\r\n","\r")) {
            String raw="EXEC CICS READ *> don't parse ( END-EXEC "+newline+"FILE('A*>B') INTO *> ( false "+newline+"(BUF) RIDFLD(KEY) NOHANDLE END-EXEC";
            var command=CicsCommandSyntax.parse(raw).orElseThrow();assertTrue(command.gaps().isEmpty(),command.toString());
            assertEquals(List.of("FILE","INTO","RIDFLD","NOHANDLE"),command.options().stream().map(CicsCommandSyntax.Option::name).toList());
            assertEquals("'A*>B'",command.options().get(0).operand().orElseThrow());
            var into=command.options().get(1);assertEquals(raw.indexOf("INTO"),into.start());assertEquals(raw.indexOf("(BUF)")+5,into.end());
            var hosts=CicsHostSyntax.parse(raw,0,1,0,0);var host=hosts.stream().filter(h->h.option().equals("INTO")).findFirst().orElseThrow();
            assertEquals(raw.indexOf("BUF"),host.identifier().getStart().getStartIndex());
        }
        var quoted=CicsCommandSyntax.parse("EXEC CICS LINK PROGRAM('A''*>B') NOHANDLE END-EXEC").orElseThrow();assertTrue(quoted.gaps().isEmpty());assertEquals("'A''*>B'",quoted.options().get(0).operand().orElseThrow());
        assertFalse(CicsCommandSyntax.parse("EXEC CICS READ*> invalid\nFILE('F') END-EXEC").orElseThrow().gaps().isEmpty());
    }
    @Test void commentsWithinHostOperandDoNotMoveItsSourceReference() {
        String raw="EXEC CICS READ FILE('F') INTO( *> unmatched ')\nBUF) RIDFLD(KEY) END-EXEC";
        var command=CicsCommandSyntax.parse(raw).orElseThrow();assertTrue(command.gaps().isEmpty(),command.toString());
        var host=CicsHostSyntax.parse(raw,20,3,4,1).stream().filter(h->h.option().equals("INTO")).findFirst().orElseThrow();
        assertEquals(20+raw.indexOf("BUF"),host.identifier().getStart().getStartIndex());assertEquals(4,host.identifier().getStart().getLine());
    }
    @Test void realProducerRecoversTypedFactsAndKeepsProvenance() {
        var state=CicsProgramControlTest.regional("01 BUF PIC X(80).\n01 KEY-F PIC X(8).",
            "EXEC CICS READ\n*> misleading END-EXEC FILE('WRONG')\nDATASET('ACCTS') INTO(BUF) RIDFLD(KEY-F)\nNOHANDLE END-EXEC.\nCALL 'AFTER'.");
        var file=assertInstanceOf(CicsFileFact.class,state.statements().get(0));assertEquals("ACCTS",((LiteralCallTarget)file.target().orElseThrow()).text());
        assertTrue(file.ordinaryContinuation().statement().isPresent());
        var into=file.options().stream().filter(o->o.canonicalName().equals("INTO")).findFirst().orElseThrow();assertTrue(into.reference().orElseThrow().binding().selected().isPresent());
        // Existing FILE operand policy exposes the approximate EXEC envelope.
        // The separately retained map is checked precisely below.
        assertEquals(file.header().provenance().original(),into.reference().orElseThrow().provenance().original());
    }

    @Test void fixedCommentTokensAreBlankedBeforeFlatteningAndHostMapStaysExactInPosition() throws Exception {
        for(String indicator:List.of("*","/"," "))for(String nl:List.of("\n","\r\n","\r")) {
            var lines=List.of("       IDENTIFICATION DIVISION.","       PROGRAM-ID. COMMENTS.","       PROCEDURE DIVISION.",
                "           EXEC CICS SEND", "      "+indicator+(indicator.equals(" ")?"*> ":"")+" END-EXEC ) don't 😀 }", "           FROM(BUF)","           END-EXEC.","           GOBACK.");
            var normalized=SourceNormalizer.normalize(String.join(nl,lines)+nl,"comments.cbl",SourceNormalizer.SourceFormat.FIXED);
            var product=new PreprocessorEngine(Bindings.cobol(),new CopybookLibrary(java.nio.file.Path.of("src/test/resources/cobol/provenance/cpy"))).process(normalized.sourceMap(),"comments.cbl");
            assertEquals(0,product.unresolved());assertFalse(product.text().contains("don't"));
            int start=product.text().indexOf("BUF");assertTrue(start>=0);var origin=product.sourceMap().embeddedOperandProvenance(start,start+3).original();
            assertEquals("comments.cbl",origin.file());assertEquals(6,origin.startLine());assertEquals(lines.get(5).indexOf("BUF"),origin.startColumn());assertEquals(origin.startColumn()+2,origin.endColumn());
        }
    }
}
