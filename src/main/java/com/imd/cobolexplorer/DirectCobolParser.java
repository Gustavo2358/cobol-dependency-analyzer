package com.imd.cobolexplorer;

import java.util.*;

/** Complete native transaction: tokens -> canonical AST plus compact origins.
 * Nothing is published until both recognition and semantic actions have completed.
 */
final class DirectCobolParser {
    record Result(CompilationUnitBuildResult ast,List<ExplorerMain.Node> nodes,
                  Map<String,Integer> ruleCounts,long recognitionNanos,long indexingNanos,long astNanos) { }
    Result parse(DirectToken[] tokens,String source,SourceMap sourceMap,String compilationId,boolean inputIntegrityKnown){
        try(var scope=DirectParseScope.enter()){
            long start=System.nanoTime();var ledger=new DirectRecognizer(tokens).parse(0);long recognition=System.nanoTime()-start;
            start=System.nanoTime();var ast=new DirectAstActions(ledger,source,sourceMap,inputIntegrityKnown).buildCompilationUnit(ledger.view(0),compilationId);long astNanos=System.nanoTime()-start;
            start=System.nanoTime();var nodes=ledger.nodes();var counts=new TreeMap<String,Integer>();
            for(int i=0;i<ledger.size;i++)if(ledger.rules[i]>=0)counts.merge(DirectGrammar.NAMES[ledger.rules[i]],1,Integer::sum);
            return new Result(ast,nodes,counts,recognition,System.nanoTime()-start,astNanos);
        }
    }
}
