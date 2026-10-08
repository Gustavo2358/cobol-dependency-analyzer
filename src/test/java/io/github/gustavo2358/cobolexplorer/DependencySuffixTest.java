package io.github.gustavo2358.cobolexplorer;

import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class DependencySuffixTest {
    @TempDir Path temp;

    private Path source(String data,String procedure)throws Exception {
        String text="IDENTIFICATION DIVISION.\nPROGRAM-ID. SUFFIX-TEST.\nDATA DIVISION.\n"
            +"WORKING-STORAGE SECTION.\n"+data+"\nPROCEDURE DIVISION.\n"+procedure+"\n";
        Path path=temp.resolve("suffix.cbl");
        Files.writeString(path,text.lines().map(s->"       "+s).collect(Collectors.joining("\n","","\n")));
        return path;
    }
    private DependencyAnalyzer.Result compare(String data,String procedure,String... names)throws Exception {
        Path path=source(data,procedure);
        var reference=new DependencyAnalyzer(false).analyze(path,DependencyAnalyzer.Options.defaults());
        var shared=new DependencyAnalyzer().analyze(path,DependencyAnalyzer.Options.defaults());
        assertEquals(reference.programs(),shared.programs(),"dependencies including provenance");
        assertEquals(reference.diagnostics(),shared.diagnostics(),"uncertainty and control diagnostics");
        assertEquals(new TreeSet<>(List.of(names)),new TreeSet<>(shared.programs().get(0).dependencies().stream()
            .filter(d->d.type().equals("program")).map(DependencyAnalyzer.Dependency::name).toList()));
        return shared;
    }
    @Test void endpointsAndInternalObservationsRemainDistinct()throws Exception {
        compare("01 TARGET PIC X(8).", "MAIN.\nMOVE 'BEFORE' TO TARGET.\nPERFORM A THRU B.\n"
            +"CALL TARGET.\nMOVE 'SECOND' TO TARGET.\nPERFORM A THRU C.\nCALL TARGET.\nGOBACK.\n"
            +"A.\nCALL TARGET.\nMOVE 'MIDDLE' TO TARGET.\nB.\nCALL TARGET.\n"
            +"MOVE 'SHORT' TO TARGET.\nC.\nCALL TARGET.\nMOVE 'LONG' TO TARGET.",
            "BEFORE","SECOND","MIDDLE","SHORT","LONG");
    }
    @Test void differentInputsAndCallerOnlyValuesArePreserved()throws Exception {
        compare("01 TARGET PIC X(8).\n01 KEEP PIC X(8).", "MAIN.\nMOVE 'KEPT' TO KEEP.\n"
            +"MOVE 'FIRST' TO TARGET.\nPERFORM A THRU B.\nMOVE 'SECOND' TO TARGET.\n"
            +"PERFORM B.\nCALL KEEP.\nGOBACK.\nA.\nCONTINUE.\nB.\nCALL TARGET.",
            "FIRST","SECOND","KEPT");
    }
    @Test void joinsNestedPerformAndPartialAliasWritesKeepCandidates()throws Exception {
        compare("01 TARGET PIC X(8).\n01 ALIAS-TARGET REDEFINES TARGET PIC X(8).\n01 FLAG PIC X.",
            "MAIN.\nMOVE 'PROGA' TO TARGET.\nPERFORM A THRU C.\nPERFORM B THRU C.\n"
            +"CALL TARGET.\nGOBACK.\nA.\nIF FLAG = 'Y'\nMOVE 'PROGA' TO TARGET\n"
            +"ELSE\nMOVE 'PROGB' TO TARGET\nEND-IF.\nB.\nCALL ALIAS-TARGET.\n"
            +"PERFORM HELPER.\nC.\nCALL TARGET.\nHELPER.\nMOVE 'C' TO ALIAS-TARGET(5:1).",
            "PROGA","PROGB","PROGC");
    }
    @Test void foreignMutationRetainsTheUnknownRemainder()throws Exception {
        var result=compare("01 TARGET PIC X(8).", "MAIN.\nMOVE 'KNOWN' TO TARGET.\n"
            +"PERFORM A THRU B.\nPERFORM B.\nGOBACK.\nA.\nCALL 'EXTERNAL' USING TARGET.\n"
            +"B.\nCALL TARGET.", "KNOWN","EXTERNAL");
        assertTrue(result.diagnostics().stream().anyMatch(s->s.contains("DYNAMIC_REMAINDER")));
    }
    @Test void contextualEscapeUsesTheReferenceSolver()throws Exception {
        var result=compare("01 TARGET PIC X(8).", "MAIN.\nPERFORM WORK-SECTION.\nCALL TARGET.\n"
            +"GOBACK.\nWORK-SECTION SECTION.\nA.\nMOVE 'GOOD' TO TARGET.\nEXIT SECTION.\n"
            +"B.\nMOVE 'BAD' TO TARGET.", "GOOD");
        assertTrue(result.metrics().contexts()<4);
    }
    @Test void callHandlersAndExplicitTransfersUseTheReferenceSolver()throws Exception {
        compare("01 TARGET PIC X(8).", "MAIN.\nPERFORM A THRU B.\nCALL TARGET.\nGOBACK.\n"
            +"A.\nCALL 'EXTERNAL' ON EXCEPTION MOVE 'FAILURE' TO TARGET\n"
            +"NOT ON EXCEPTION MOVE 'SUCCESS' TO TARGET END-CALL.\n"
            +"GO TO B.\nCALL 'UNREACH'.\nB.\nCONTINUE.", "EXTERNAL","FAILURE","SUCCESS");
    }
    @Test void loopPhasesAndRecursiveBodiesReachTheSameFixedPoint()throws Exception {
        compare("01 TARGET PIC X(8).\n01 FLAG PIC X.", "MAIN.\nMOVE 'INITIAL' TO TARGET.\n"
            +"PERFORM A THRU B 0 TIMES.\nPERFORM A THRU B 1 TIMES.\n"
            +"PERFORM B 3 TIMES.\nCALL TARGET.\nGOBACK.\nA.\nCALL TARGET.\n"
            +"IF FLAG = 'Y'\nPERFORM A THRU B\nEND-IF.\nB.\nMOVE 'FINAL' TO TARGET.",
            "INITIAL","FINAL");
    }
    @Test void variedStructuredProgramsMatchTheReferenceIncludingProvenance()throws Exception {
        var random=new Random(20261008);
        for(int sample=0;sample<24;sample++) {
            var procedure=new StringBuilder("MAIN.\nMOVE 'PROGA' TO TARGET.\n");
            for(int call=0;call<6;call++) {
                int first=1+random.nextInt(5),last=first+random.nextInt(6-first);
                procedure.append("PERFORM P-").append(first).append(" THRU P-").append(last).append(".\nCALL TARGET.\n");
            }
            procedure.append("GOBACK.\n");
            for(int paragraph=1;paragraph<=5;paragraph++) {
                procedure.append("P-").append(paragraph).append(".\nCALL TARGET.\n");
                switch(random.nextInt(4)) {
                    case 0 -> procedure.append("MOVE 'PROGB' TO TARGET.\n");
                    case 1 -> procedure.append("IF FLAG = 'Y'\nMOVE 'PROGC' TO TARGET\nELSE\nMOVE 'PROGD' TO TARGET\nEND-IF.\n");
                    case 2 -> procedure.append("MOVE 'E' TO ALIAS-TARGET(5:1).\n");
                    default -> procedure.append("PERFORM HELPER.\n");
                }
            }
            procedure.append("HELPER.\nCALL TARGET.\nMOVE 'PROGF' TO TARGET.");
            Path path=source("01 TARGET PIC X(8).\n01 ALIAS-TARGET REDEFINES TARGET PIC X(8).\n01 FLAG PIC X.",procedure.toString());
            var reference=new DependencyAnalyzer(false).analyze(path,DependencyAnalyzer.Options.defaults());
            var shared=new DependencyAnalyzer().analyze(path,DependencyAnalyzer.Options.defaults());
            assertEquals(reference.programs(),shared.programs(),"sample "+sample);
            assertEquals(reference.diagnostics(),shared.diagnostics(),"sample "+sample);
        }
    }
    @Test void sharedOverlappingSuffixesGrowBelowQuadratic()throws Exception {
        long previous=0;
        for(int size:new int[]{80,160,320}) {
            var procedure=new StringBuilder("MAIN.\n");
            for(int i=1;i<=size;i++)procedure.append("PERFORM P-").append(i).append(" THRU P-").append(size).append(".\n");
            procedure.append("CALL TARGET.\nGOBACK.\n");
            for(int i=1;i<=size;i++) {
                procedure.append("P-").append(i).append(".\nMOVE 'A' TO TARGET.\nIF FLAG = 'Y'\n")
                    .append("MOVE 'B' TO TARGET\nEND-IF.\nMOVE 'D' TO TARGET.\n");
                if(i>size-10)procedure.append("PERFORM HELPER.\n");
            }
            procedure.append("HELPER.\nMOVE 'H' TO TARGET.");
            var result=compare("01 TARGET PIC X.\n01 FLAG PIC X.",procedure.toString(),"H");
            long work=result.metrics().workItems();
            assertTrue(work<50L*size,result.metrics().toString());
            if(previous>0)assertTrue(work<previous*3,"doubling size must avoid the former fourfold work growth");
            previous=work;
        }
    }
}
