package io.github.gustavo2358.cobolexplorer;
import java.nio.file.*;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
/** Differential grammar gate; no fallback is available in this test entry point. */
public final class DirectRecognitionCorpusCheck {
    public static void main(String[] args) throws Exception {
        var manifest=new ObjectMapper().readTree(Path.of(args[0]).toFile());int pass=0,failed=0;
        for(var item:manifest){
            var path=Path.of(item.asText());var text=Files.readString(path);
            try {
                var nativeResult=new DirectCobolParser().parse(DirectLexerBoundary.lex(text),text,SourceMap.identity(text,"test.cbl"),"test.cbl",true);
                var own=nativeResult.nodes();
                var baseline=DirectDataCorpusCheck.parse(text,false);var base=baseline.nodes();
                if(!base.equals(own)){
                    int at=0;while(at<Math.min(base.size(),own.size())&&base.get(at).equals(own.get(at)))at++;
                    throw new AssertionError("first="+at+" base="+(at<base.size()?base.get(at):"EOF")+" own="+(at<own.size()?own.get(at):"EOF"));
                }
                if(!baseline.ast().compilationUnit().programUnits().equals(nativeResult.ast().compilationUnit().programUnits())){
                    Files.writeString(Path.of("target/native-base-ast.txt"),baseline.ast().compilationUnit().programUnits().toString());
                    Files.writeString(Path.of("target/native-own-ast.txt"),nativeResult.ast().compilationUnit().programUnits().toString());
                    throw new AssertionError("AST differs");
                }
                if(!baseline.ast().coverageByProgramUnit().equals(nativeResult.ast().coverageByProgramUnit()))throw new AssertionError("coverage differs");
                if(!baseline.ast().diagnosticsByProgramUnit().equals(nativeResult.ast().diagnosticsByProgramUnit()))throw new AssertionError("diagnostics differ");
                pass++;System.out.println("PASS "+path);
            }catch(Exception|AssertionError e){failed++;System.out.println("FAIL "+path+" "+e);}
        }
        System.out.println("pass="+pass+" failed="+failed);if(failed!=0)throw new AssertionError();
    }
}
