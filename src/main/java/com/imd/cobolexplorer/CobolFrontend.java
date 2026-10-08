package com.imd.cobolexplorer;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.ParseTree;
import java.util.*;
import org.slf4j.LoggerFactory;

/** Owns the atomic choice between native parsing and the unchanged legacy pipeline. */
final class CobolFrontend {
    record Result(CompilationUnitBuildResult ast,List<ExplorerMain.Node> nodes,Map<String,Integer> ruleCounts,
                  String route,String fallbackReason,long recognitionNanos,long indexingNanos,long astNanos,long nativeAttemptNanos) { }
    static Result parse(GrammarBinding binding,CommonTokenStream tokens,String text,SourceMap sourceMap,
                        String file,List<Diagnostic> diagnostics,String mode,boolean preprocessingClean,boolean lexerClean){
        long attempt=0;String reason="";
        if(mode.equals("direct-ast-lab")){
            long start=System.nanoTime();
            try {
                if(!lexerClean)throw new DirectRecognizer.Unsupported("LEXER_DIAGNOSTICS");
                var input=DirectLexerBoundary.copy(tokens);long boundary=System.nanoTime()-start;
                var own=new DirectCobolParser().parse(input,text,sourceMap,file,preprocessingClean);
                return new Result(own.ast(),own.nodes(),own.ruleCounts(),"native","",own.recognitionNanos()+boundary,own.indexingNanos(),own.astNanos(),System.nanoTime()-start);
            } catch(RuntimeException | StackOverflowError failure){
                attempt=System.nanoTime()-start;
                reason=failure.getClass().getSimpleName()+": "+Objects.toString(failure.getMessage(),"");
                LoggerFactory.getLogger(CobolFrontend.class).warn("event=direct_parser_fallback reason={} nativeAttemptNanos={}",reason,attempt);
            }
        }
        // The original token stream is never advanced or mutated by the native parser.
        // Fresh legacy parser/tree/AST; no native drafts, diagnostics or IDs survive fallback.
        tokens.seek(0);var parser=binding.cobolParser(tokens);
        if(mode.equals("direct-data-lab"))((com.imd.cobolexplorer.antlr.CobolParser)parser).directDataSession=new DirectDataParser.Session();
        parser.removeErrorListeners();parser.addErrorListener(new AntlrDiagnosticListener(binding.name(),Diagnostic.Phase.PARSER,file,diagnostics));
        long start=System.nanoTime();var tree=binding.cobolStart(parser);long recognition=System.nanoTime()-start;
        var nodes=new ArrayList<ExplorerMain.Node>();var counts=new TreeMap<String,Integer>();
        var ids=new IdentityHashMap<ParseTree,Integer>();var sizes=new IdentityHashMap<ParseTree,Integer>();
        start=System.nanoTime();ExplorerMain.walk(tree,-1,0,parser,nodes,counts,ids,sizes);long indexing=System.nanoTime()-start;
        start=System.nanoTime();var ast=new AstBuilder(parser,text,sourceMap,ids,sizes,preprocessingClean&&lexerClean&&parser.getNumberOfSyntaxErrors()==0).buildCompilationUnit(tree,file);long astNanos=System.nanoTime()-start;
        if(mode.equals("direct-data-lab")){
            var session=((com.imd.cobolexplorer.antlr.CobolParser)parser).directDataSession;
            LoggerFactory.getLogger(CobolFrontend.class).info("event=direct_data_lab acceptedDivisions={} fallbackDivisions={} entries={} reasons={}",session.acceptedDivisions(),session.fallbackDivisions(),session.entries(),session.fallbackReasons());
        }
        return new Result(ast,nodes,counts,reason.isEmpty()?mode:"fallback",reason,recognition,indexing,astNanos,attempt);
    }
}
