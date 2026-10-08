package com.imd.cobolexplorer;

import org.junit.jupiter.api.Test;
import org.antlr.v4.runtime.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DirectCobolParserTest {
    record Parsed(CobolFrontend.Result result,List<Diagnostic> diagnostics) { }
    static Parsed parse(SourceMap source,String mode){
        var diagnostics=new ArrayList<Diagnostic>();var binding=Bindings.cobol();
        var lexer=binding.cobolLexer(CharStreams.fromString(source.text()));lexer.removeErrorListeners();
        lexer.addErrorListener(new AntlrDiagnosticListener(binding.name(),Diagnostic.Phase.LEXER,"test.cbl",diagnostics));
        var tokens=new CommonTokenStream(lexer);tokens.fill();
        return new Parsed(CobolFrontend.parse(binding,tokens,source.text(),source,"test.cbl",diagnostics,mode,true,diagnostics.isEmpty()),diagnostics);
    }
    private static void equivalent(Parsed a,Parsed b,String label){
        assertEquals(a.diagnostics(),b.diagnostics(),label+": diagnostics");
        assertEquals(a.result().nodes(),b.result().nodes(),label+": syntax");
        assertEquals(a.result().ruleCounts(),b.result().ruleCounts(),label+": counts");
        assertEquals(a.result().ast().compilationUnit().programUnits(),b.result().ast().compilationUnit().programUnits(),label+": AST");
        assertEquals(a.result().ast().coverageByProgramUnit(),b.result().ast().coverageByProgramUnit(),label+": coverage");
        assertEquals(a.result().ast().diagnosticsByProgramUnit(),b.result().ast().diagnosticsByProgramUnit(),label+": semantic diagnostics");
    }
    private static String program(String body){return "IDENTIFICATION DIVISION. PROGRAM-ID. LAB. DATA DIVISION. WORKING-STORAGE SECTION. 01 X PIC 9. 01 T. 02 ITEM PIC X OCCURS 3. PROCEDURE DIVISION. "+body+" END PROGRAM LAB.";}

    @Test void scalarPredicateActionsAgreeAcrossParserRoutes() {
        for(var body:List.of("IF X NOT > 3 OR NOT 5 CONTINUE END-IF.",
            "IF X > (1 OR 3 AND 5) CONTINUE END-IF.",
            "EVALUATE TRUE WHEN X > 3 AND X < 8 CONTINUE END-EVALUATE.",
            "EVALUATE X WHEN 1 THRU 3 CONTINUE END-EVALUATE.",
            "EVALUATE X WHEN ANY CONTINUE END-EVALUATE.")) {
            var source=SourceMap.identity(program(body),"test.cbl");var nativeResult=parse(source,"direct-ast-lab");
            assertEquals("native",nativeResult.result().route());equivalent(parse(source,"antlr"),nativeResult,body);
        }
    }
    @Test void nativePathPreservesAmbiguousGrammarDecisions(){
        for(String body:List.of("COMPUTE X = FUNCTION DATE-OF-INTEGER(FUNCTION INTEGER-OF-DATE(X) - 1).",
            "MOVE FUNCTION TRIM(ITEM(X)) TO ITEM(1).",
            "INSPECT ITEM(1) TALLYING X FOR ALL SPACES LOW-VALUES X FOR ALL 'X'.",
            "IF X > 0 IF X = 1 MOVE 2 TO X ELSE MOVE 3 TO X END-IF END-IF.",
            "EVALUATE TRUE WHEN X > 0 MOVE 1 TO X WHEN OTHER CONTINUE END-EVALUATE.",
            "CALL 'OTHER' USING BY REFERENCE ITEM(X) ON EXCEPTION MOVE 0 TO X END-CALL.")){
            var source=SourceMap.identity(program(body),"test.cbl");var own=parse(source,"direct-ast-lab");
            assertEquals("native",own.result().route(),body+own.result().fallbackReason());equivalent(parse(source,"antlr"),own,body);
        }
    }
    @Test void procedureSectionsUseNativePathAndPreserveParagraphBoundaries(){
        for(String body:List.of(
            "S SECTION. P. GOBACK.",
            "P. CONTINUE. S SECTION. Q. GOBACK.",
            "S-FIRST SECTION. S-SECOND SECTION. S-THIRD SECTION. GOBACK.",
            "P. CONTINUE. S SECTION 42. Q. CONTINUE. T SECTION 99. GOBACK.",
            "100 SECTION. 200. CONTINUE. 300 SECTION 01. 400. GOBACK.",
            "ASCII SECTION. BINARY. CONTINUE. P. GOBACK.",
            "P CONTINUE. Q GOBACK.",
            "P. CONTINUE. s\n*> section boundary comment\nsection. Q. GOBACK.")){
            var source=SourceMap.identity(program(body),"test.cbl");
            var base=parse(source,"antlr");var own=parse(source,"direct-ast-lab");
            assertEquals(List.of(),base.diagnostics(),body);
            assertEquals("native",own.result().route(),body+own.result().fallbackReason());
            equivalent(base,own,body);
        }
    }
    @Test void procedureSectionFixturesNeverFallBack() throws Exception {
        for(String name:List.of("resolution/procedure-binding.cbl","semantic/nominal-references.cbl")){
            var file=Path.of("src/test/resources/cobol").resolve(name);
            var source=SourceNormalizer.normalize(Files.readString(file),file.toString(),
                    SourceNormalizer.SourceFormat.FIXED).sourceMap();
            var base=parse(source,"antlr");var own=parse(source,"direct-ast-lab");
            assertEquals(List.of(),base.diagnostics(),name);
            assertEquals("native",own.result().route(),name+own.result().fallbackReason());
            equivalent(base,own,name);
        }
    }
    @Test void malformedProcedureSectionsStillFallBack(){
        for(String body:List.of("P. CONTINUE. S SECTION GOBACK.","S SECTION 42 43. GOBACK.")){
            var source=SourceMap.identity(program(body),"test.cbl");
            var base=parse(source,"antlr");var own=parse(source,"direct-ast-lab");
            assertFalse(base.diagnostics().isEmpty(),body);
            assertEquals("fallback",own.result().route(),body);
            equivalent(base,own,body);
        }
    }
    @Test void embeddedOperandsUseOnlyNativeSyntax(){
        for(String body:List.of(
            "\n*>EXECSQL EXEC SQL WHENEVER SQLERROR GO TO SQL-ERR END-EXEC\n. GOBACK. SQL-ERR. GOBACK.",
            "\n*>EXECSQL EXEC SQL DELETE FROM T WHERE C = :X END-EXEC\n. GOBACK.",
            "\n*>EXECCICS EXEC CICS SEND MAP('MAP1') FROM(T) LENGTH(LENGTH OF T) RESP(X) END-EXEC\n. GOBACK.",
            "\n*>EXECCICS EXEC CICS HANDLE CONDITION ERROR(SQL-ERR) END-EXEC\n. GOBACK. SQL-ERR. GOBACK.",
            "MOVE '😀' TO ITEM(1).")){
            var source=SourceMap.identity(program(body),"test.cbl");var own=parse(source,"direct-ast-lab");
            assertEquals("native",own.result().route(),own.result().fallbackReason());equivalent(parse(source,"antlr"),own,body);
        }
    }
    @Test void generatedGrammarAndActionsAreCurrent() throws Exception {
        var result=new ProcessBuilder("python3","-B","scripts/direct-parser/generate.py","--check").redirectErrorStream(true).start();
        String output=new String(result.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(0,result.waitFor(),output);
    }
    @Test void legacyParserCannotRunInsideNativeScope(){
        try(var scope=DirectParseScope.enter()){
            assertThrows(IllegalStateException.class,()->Bindings.cobol().cobolParser(new CommonTokenStream(Bindings.cobol().cobolLexer(CharStreams.fromString("")))));
        }
        assertFalse(DirectParseScope.active());
        assertNotNull(Bindings.cobol().cobolParser(new CommonTokenStream(Bindings.cobol().cobolLexer(CharStreams.fromString("")))));
    }
    @Test void malformedInputFallsBackAtomically(){
        for(String body:List.of("MOVE 1 TO .","IF X = 1 MOVE 2 TO X END-IF END-IF.","MOVE @ TO X.")){
            var source=SourceMap.identity(program(body),"test.cbl");var own=parse(source,"direct-ast-lab");
            assertEquals("fallback",own.result().route());assertFalse(own.result().fallbackReason().isBlank());
            equivalent(parse(source,"antlr"),own,body);assertFalse(DirectParseScope.active());
        }
    }
    @Test void completeFixtureCorpusPreservesAstDiagnosticsAndProvenance() throws Exception {
        int nativeCount=0,compared=0,sameFailure=0;var errors=new ArrayList<String>();var rows=new ArrayList<String>();
        try(var files=Files.walk(Path.of("src/test/resources"))){
            for(var file:files.filter(p->p.toString().endsWith(".cbl")).sorted().toList()){
                SourceMap source;
                try {source=SourceNormalizer.normalize(Files.readString(file),file.toString(),new SourceNormalizer.Options(SourceNormalizer.SourceFormat.FIXED,SourceNormalizer.DebugLinePolicy.EXCLUDE)).sourceMap();}
                catch(IllegalArgumentException rejected){rows.add(file+"\tNORMALIZATION_REJECTED");continue;}
                Parsed base=null,own=null;RuntimeException baseError=null,ownError=null;
                try{base=parse(source,"antlr");}catch(RuntimeException e){baseError=e;}
                try{own=parse(source,"direct-ast-lab");}catch(RuntimeException e){ownError=e;}
                if(baseError!=null||ownError!=null){
                    if(baseError!=null&&ownError!=null&&baseError.getClass()==ownError.getClass()&&Objects.equals(baseError.getMessage(),ownError.getMessage())){sameFailure++;rows.add(file+"\tSAME_BASELINE_FAILURE");continue;}
                    errors.add(file+" failure base="+baseError+" own="+ownError);continue;
                }
                compared++;if(own.result().route().equals("native"))nativeCount++;
                rows.add(file+"\t"+own.result().route()+"\t"+own.result().fallbackReason());
                if(!base.result().nodes().equals(own.result().nodes())){
                    int i=0;while(i<Math.min(base.result().nodes().size(),own.result().nodes().size())&&base.result().nodes().get(i).equals(own.result().nodes().get(i)))i++;
                    System.out.println("DIFFERENT "+file+" base="+base.result().nodes().get(i)+" own="+own.result().nodes().get(i));
                }
                try{equivalent(base,own,file.toString());}catch(AssertionError e){errors.add(file+" "+e.getMessage().split(" ==> ")[0]);}
            }
        }
        Files.createDirectories(Path.of("target/direct-ast-lab"));Files.write(Path.of("target/direct-ast-lab/fixtures.tsv"),rows);
        System.out.println("DIRECT_AST_FIXTURES compared="+compared+" native="+nativeCount+" sameFailure="+sameFailure);
        assertTrue(compared>200);assertTrue(nativeCount>150,"native route must be exercised");assertEquals(List.of(),errors);
    }
}
