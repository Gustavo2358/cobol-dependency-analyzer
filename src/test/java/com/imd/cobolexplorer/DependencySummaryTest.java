package com.imd.cobolexplorer;

import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class DependencySummaryTest {
    @TempDir Path temp;

    @Test void recursiveObservationRelationsConvergeWithoutChangingCallerBindings()throws Exception {
        var result=check("01 TARGET PIC X(8).\n01 FLAG PIC X.",
            "MAIN.\nMOVE 'FIRST' TO TARGET.\nPERFORM BODY.\nCALL TARGET.\n"
            +"MOVE 'SECOND' TO TARGET.\nPERFORM BODY.\nCALL TARGET.\nGOBACK.\n"
            +"BODY.\nCALL TARGET.\nIF FLAG = 'Y'\nPERFORM BODY\nEND-IF.\nEXIT.","FIRST","SECOND");
        assertEquals(2,result.metrics().contexts());
        assertTrue(result.metrics().resolutionWorkItems()<30,result.metrics().toString());
    }

    @Test void longParametricChainsUseAnExplicitEvaluationStack()throws Exception {
        var body=new StringBuilder("MAIN.\nMOVE 'ONLY' TO A.\nPERFORM BODY.\nCALL B.\nGOBACK.\nBODY.\n");
        for(int i=0;i<600;i++)body.append("MOVE A TO B.\nMOVE B TO A.\n");
        body.append("EXIT.");
        var result=check("01 A PIC X(8).\n01 B PIC X(8).",body.toString(),"ONLY");
        assertEquals(2,result.metrics().contexts());
    }

    @Test void oneFlowPerControlInputServesOneHundredDifferentDataInputs()throws Exception {
        var procedure=new StringBuilder("MAIN.\n");var names=new TreeSet<String>();
        for(int i=0;i<100;i++) {
            String name=String.format("P%06d",i);if(i%2==0)names.add(name);
            procedure.append("MOVE '").append(name).append("' TO ORIGIN.\nMOVE '").append(i%2==0?"N":"S")
                .append("' TO FLAG.\nPERFORM BODY.\nCALL TARGET.\n");
        }
        procedure.append("GOBACK.\nBODY.\nMOVE ORIGIN TO TARGET.\nIF FLAG = 'S'\nMOVE 'SPECIAL' TO TARGET\nEND-IF.\nCALL TARGET.\nEXIT.");
        names.add("SPECIAL");
        var result=check("01 ORIGIN PIC X(8).\n01 TARGET PIC X(8).\n01 FLAG PIC X.",procedure.toString(),names.toArray(String[]::new));
        assertEquals(3,result.metrics().contexts(),"Root and two control inputs; data inputs are parameters");
        assertTrue(result.metrics().workItems()<600,result.metrics().toString());
        assertTrue(result.metrics().resultDeliveries()<110,"New subscribers must not replay results to old subscribers: "+result.metrics());
    }
    @Test void parametricPartialWritesBindCorrelatedOperandsTogether()throws Exception {
        var result=check("01 TARGET PIC X(8).\n01 PATCH-VALUE PIC X(4).",
            "MAIN.\nMOVE 'AAAA0000' TO TARGET.\nMOVE '1111' TO PATCH-VALUE.\nPERFORM BODY.\nCALL TARGET.\n"
            +"MOVE 'BBBB0000' TO TARGET.\nMOVE '2222' TO PATCH-VALUE.\nPERFORM BODY.\nCALL TARGET.\nGOBACK.\n"
            +"BODY.\nMOVE PATCH-VALUE TO TARGET(5:4).\nCALL TARGET.\nEXIT.","AAAA1111","BBBB2222");
        assertEquals(2,result.metrics().contexts());
    }
    @Test void nestedParametricResultsPreserveObservationBindings()throws Exception {
        var result=check("01 TARGET PIC X(8).\n01 ORIGIN PIC X(8).",
            "MAIN.\nMOVE 'FIRST' TO ORIGIN.\nPERFORM OUTER.\nCALL TARGET.\n"
            +"MOVE 'SECOND' TO ORIGIN.\nPERFORM OUTER.\nCALL TARGET.\nGOBACK.\n"
            +"OUTER.\nPERFORM INNER.\nEXIT.\nINNER.\nMOVE ORIGIN TO TARGET.\nCALL TARGET.\nEXIT.","FIRST","SECOND");
        assertEquals(3,result.metrics().contexts());
    }

    @Test void nonReturningBodiesDoNotEnumerateDeadValueCombinations()throws Exception {
        var data=new StringBuilder();var body=new StringBuilder("BODY.\n");
        for(int i=0;i<16;i++) {
            data.append("01 V-").append(i).append(" PIC X.\n");
            body.append("IF V-").append(i).append(" = 'X'\nMOVE 'X' TO V-").append((i+1)%16).append("\nEND-IF.\n");
        }
        body.append("PERFORM BODY.\nCALL 'DEAD-BODY'.\nEXIT.");
        var result=check(data.toString(),"MAIN.\nCALL 'LIVE'.\nPERFORM BODY.\nCALL 'DEAD-CALLER'.\nGOBACK.\n"+body,"LIVE");
        assertTrue(result.metrics().contexts()<=3,result.metrics().toString());
        assertTrue(result.metrics().workItems()<200,result.metrics().toString());
    }

    @Test void localTransformationsShareWorkWithoutMergingCallerInputs() throws Exception {
        var procedure=new StringBuilder("MAIN.\n");var names=new ArrayList<String>();
        for(int i=0;i<40;i++) {
            String name=String.format("P%06d",i);names.add(name);
            procedure.append("MOVE '").append(name).append("' TO KEEP.\nPERFORM BODY.\nCALL KEEP.\nCALL TARGET.\n");
        }
        procedure.append("GOBACK.\nBODY.\nMOVE 'SAME' TO TARGET.\nIF KEEP = 'NEVER'\nCALL 'BAD'\nEND-IF.\nEXIT.");
        names.add("SAME");
        var result=check("01 KEEP PIC X(8).\n01 TARGET PIC X(8).",procedure.toString(),names.toArray(String[]::new));
        assertEquals(41,result.metrics().contexts(),"Each caller input remains distinct");
        assertTrue(result.metrics().reusedEvaluations()>=39,result.metrics().toString());
    }
    @Test void sharedPatchesKeepPartialWritesAndCallerCorrelation() throws Exception {
        check("01 TARGET PIC X(8).\n01 KEEP PIC X(8).\n01 FLAG PIC X.",
            "MAIN.\nMOVE 'FIRST' TO TARGET.\nMOVE '0' TO FLAG.\nPERFORM BODY.\nCALL TARGET.\n"
            +"MOVE 'OTHER' TO TARGET.\nMOVE '1' TO FLAG.\nPERFORM BODY.\nGOBACK.\n"
            +"BODY.\nIF FLAG = '0'\nMOVE 'X' TO TARGET(5:1)\nELSE\nMOVE 'BAD' TO TARGET\nEND-IF.\nEXIT.","FIRSX");
    }

    private Path source(String data,String procedure)throws Exception {
        String text="IDENTIFICATION DIVISION.\nPROGRAM-ID. SUFFIX-TEST.\nDATA DIVISION.\n"
            +"WORKING-STORAGE SECTION.\n"+data+"\nPROCEDURE DIVISION.\n"+procedure+"\n";
        Path path=temp.resolve("suffix.cbl");
        Files.writeString(path,text.lines().map(s->"       "+s).collect(Collectors.joining("\n","","\n")));
        return path;
    }
    private DependencyAnalyzer.Result check(String data,String procedure,String... names)throws Exception {
        Path path=source(data,procedure);
        var shared=new DependencyAnalyzer().analyze(path,DependencyAnalyzer.Options.defaults());
        assertEquals(new TreeSet<>(List.of(names)),new TreeSet<>(shared.programs().get(0).dependencies().stream()
            .filter(d->d.type().equals("program")).map(DependencyAnalyzer.Dependency::name).toList()));
        return shared;
    }
    @Test void endpointsAndInternalObservationsRemainDistinct()throws Exception {
        check("01 TARGET PIC X(8).", "MAIN.\nMOVE 'BEFORE' TO TARGET.\nPERFORM A THRU B.\n"
            +"CALL TARGET.\nMOVE 'SECOND' TO TARGET.\nPERFORM A THRU C.\nCALL TARGET.\nGOBACK.\n"
            +"A.\nCALL TARGET.\nMOVE 'MIDDLE' TO TARGET.\nB.\nCALL TARGET.\n"
            +"MOVE 'SHORT' TO TARGET.\nC.\nCALL TARGET.\nMOVE 'LONG' TO TARGET.",
            "BEFORE","SECOND","MIDDLE","SHORT","LONG");
    }
    @Test void differentInputsAndCallerOnlyValuesArePreserved()throws Exception {
        check("01 TARGET PIC X(8).\n01 KEEP PIC X(8).", "MAIN.\nMOVE 'KEPT' TO KEEP.\n"
            +"MOVE 'FIRST' TO TARGET.\nPERFORM A THRU B.\nMOVE 'SECOND' TO TARGET.\n"
            +"PERFORM B.\nCALL KEEP.\nGOBACK.\nA.\nCONTINUE.\nB.\nCALL TARGET.",
            "FIRST","SECOND","KEPT");
    }
    @Test void joinsNestedPerformAndPartialAliasWritesKeepCandidates()throws Exception {
        check("01 TARGET PIC X(8).\n01 ALIAS-TARGET REDEFINES TARGET PIC X(8).\n01 FLAG PIC X.",
            "MAIN.\nMOVE 'PROGA' TO TARGET.\nPERFORM A THRU C.\nPERFORM B THRU C.\n"
            +"CALL TARGET.\nGOBACK.\nA.\nIF FLAG = 'Y'\nMOVE 'PROGA' TO TARGET\n"
            +"ELSE\nMOVE 'PROGB' TO TARGET\nEND-IF.\nB.\nCALL ALIAS-TARGET.\n"
            +"PERFORM HELPER.\nC.\nCALL TARGET.\nHELPER.\nMOVE 'C' TO ALIAS-TARGET(5:1).",
            "PROGA","PROGB","PROGC");
    }
    @Test void foreignMutationRetainsTheUnknownRemainder()throws Exception {
        var result=check("01 TARGET PIC X(8).", "MAIN.\nMOVE 'KNOWN' TO TARGET.\n"
            +"PERFORM A THRU B.\nPERFORM B.\nGOBACK.\nA.\nCALL 'EXTERNAL' USING TARGET.\n"
            +"B.\nCALL TARGET.", "KNOWN","EXTERNAL");
        assertTrue(result.diagnostics().stream().anyMatch(s->s.contains("DYNAMIC_REMAINDER")));
    }
    @Test void contextualEscapePreservesTheEndpoint()throws Exception {
        var result=check("01 TARGET PIC X(8).", "MAIN.\nPERFORM WORK-SECTION.\nCALL TARGET.\n"
            +"GOBACK.\nWORK-SECTION SECTION.\nA.\nMOVE 'GOOD' TO TARGET.\nEXIT SECTION.\n"
            +"B.\nMOVE 'BAD' TO TARGET.", "GOOD");
        assertFalse(result.diagnostics().stream().anyMatch(d->d.contains("CONTROL_REMAINDER")));
    }
    @Test void callHandlersAndExplicitTransfersShareTheControlModel()throws Exception {
        check("01 TARGET PIC X(8).", "MAIN.\nPERFORM A THRU B.\nCALL TARGET.\nGOBACK.\n"
            +"A.\nCALL 'EXTERNAL' ON EXCEPTION MOVE 'FAILURE' TO TARGET\n"
            +"NOT ON EXCEPTION MOVE 'SUCCESS' TO TARGET END-CALL.\n"
            +"GO TO B.\nCALL 'UNREACH'.\nB.\nCONTINUE.", "EXTERNAL","FAILURE","SUCCESS");
    }
    @Test void grammarOwnedFileHandlersParticipateInSummaries()throws Exception {
        Path path=source("01 TARGET PIC X(8).", "MAIN.\nPERFORM A THRU B.\nCALL TARGET.\nGOBACK.\n"
            +"A.\nREAD INFILE AT END MOVE 'END' TO TARGET END-READ.\nB.\nCALL TARGET.");
        var shared=new DependencyAnalyzer().analyze(path,DependencyAnalyzer.Options.defaults());
        assertTrue(shared.programs().get(0).dependencies().stream().anyMatch(d->d.name().equals("END")));
    }
    @Test void loopPhasesAndRecursiveBodiesReachTheSameFixedPoint()throws Exception {
        check("01 TARGET PIC X(8).\n01 FLAG PIC X.", "MAIN.\nMOVE 'INITIAL' TO TARGET.\n"
            +"PERFORM A THRU B 0 TIMES.\nPERFORM A THRU B 1 TIMES.\n"
            +"PERFORM B 3 TIMES.\nCALL TARGET.\nGOBACK.\nA.\nCALL TARGET.\n"
            +"IF FLAG = 'Y'\nPERFORM A THRU B\nEND-IF.\nB.\nMOVE 'FINAL' TO TARGET.",
            "INITIAL","FINAL");
    }
    @Test void variedProgramsMatchFrozenDependenciesProvenanceAndDiagnostics()throws Exception {
        // Frozen before unification at 2b0ebd6c25b2c2c7ac938d567ce00c82e16b5b65.
        // Each digest includes ordered dependencies, consumer locations and diagnostics.
        // No reference implementation remains in the application or test runtime.
        var expected=Files.readAllLines(Path.of("src/test/resources/dependency-summary-seeds.sha256"));
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
            var shared=new DependencyAnalyzer().analyze(path,DependencyAnalyzer.Options.defaults());
            String actual=shared.programs()+"|"+shared.diagnostics();
            String digest=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(actual.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            assertEquals(expected.get(sample),digest,"sample "+sample);
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
            var result=check("01 TARGET PIC X.\n01 FLAG PIC X.",procedure.toString(),"H");
            long work=result.metrics().workItems();
            assertTrue(work<50L*size,result.metrics().toString());
            if(previous>0)assertTrue(work<previous*3,"doubling size must avoid the former fourfold work growth");
            previous=work;
        }
    }
    @Test void terminalExitDoesNotResumeAnInvocation()throws Exception {
        check("", "MAIN.\nPERFORM A THRU B.\nCALL 'UNREACH'.\nGOBACK.\n"
            +"A.\nCALL 'REACHED'.\nGOBACK.\nB.\nCONTINUE.","REACHED");
    }
    @Test void activeAndSavedHandlersRemainPartOfSummaryInputs()throws Exception {
        check("", "MAIN.\nEXEC CICS HANDLE CONDITION ERROR(FIRST-HANDLER) END-EXEC.\n"
            +"PERFORM A THRU B.\nEXEC CICS HANDLE CONDITION ERROR(SECOND-HANDLER) END-EXEC.\n"
            +"PERFORM A THRU B.\nGOBACK.\nA.\nCONTINUE.\nB.\n"
            +"EXEC CICS LINK PROGRAM('TARGET') END-EXEC.\n"
            +"FIRST-HANDLER.\nCALL 'FIRST'.\nGOBACK.\nSECOND-HANDLER.\nCALL 'SECOND'.\nGOBACK.",
            "TARGET","FIRST","SECOND");
    }
    @Test void transfersAndHandlerArmsShareOverlappingRanges()throws Exception {
        for(boolean handlers:new boolean[]{false,true}) {
            long previous=0;
            for(int size:new int[]{40,80,160}) {
                var procedure=new StringBuilder("MAIN.\n");
                for(int i=1;i<=size;i++)procedure.append("PERFORM P-").append(i).append(" THRU P-").append(size).append(".\n");
                procedure.append("CALL TARGET.\nGOBACK.\n");
                for(int i=1;i<=size;i++) {
                    procedure.append("P-").append(i).append(".\n");
                    if(handlers)procedure.append("CALL 'EXTERNAL' ON EXCEPTION MOVE 'FAIL' TO TARGET\n"
                        +"NOT ON EXCEPTION MOVE 'GOOD' TO TARGET END-CALL.\n");
                    procedure.append("MOVE 'FINAL' TO TARGET.\n");
                    if(i<size)procedure.append("GO TO P-").append(i+1).append(".\nCALL 'UNREACH'.\n");
                }
                var result=check("01 TARGET PIC X(8).",procedure.toString(),handlers?new String[]{"EXTERNAL","FINAL"}:new String[]{"FINAL"});
                long work=result.metrics().workItems();
                assertTrue(work<50L*size,result.metrics().toString());
                if(previous>0)assertTrue(work<previous*3,"transfers and handlers must share summaries too");
                previous=work;
            }
        }
    }

    @Test void cyclicParagraphsJoinWithinTheSameSummary()throws Exception {
        var result=check("01 TARGET PIC X(8).\n01 FLAG PIC X.",
            "MAIN.\nMOVE 'FIRST' TO TARGET.\nGO TO A.\n"
            +"A.\nCALL TARGET.\nIF FLAG = 'Y' GO TO DONE END-IF.\nGO TO B.\n"
            +"B.\nMOVE 'NEXT' TO TARGET.\nGO TO A.\nDONE.\nGOBACK.","FIRST","NEXT");
        assertTrue(result.metrics().contexts()<=3,result.metrics().toString());
        assertTrue(result.metrics().workItems()<40,result.metrics().toString());
    }

    @Test void parametricReadsAndPartialWritesShareFlowAndKeepEveryCallerValue()throws Exception {
        for(String body:List.of("MOVE 'PROGA' TO TARGET.","MOVE 'A' TO TARGET(8:1).",
                "CALL TARGET.\nMOVE 'PROGA' TO TARGET.")) {
            var procedure=new StringBuilder("MAIN.\n");var expected=new TreeSet<String>();
            for(int i=0;i<12;i++) {
                String incoming=String.format("F%06dX",i);
                procedure.append("MOVE '").append(incoming).append("' TO TARGET.\nPERFORM BODY.\nCALL TARGET.\n");
                if(body.contains("(8:1)"))expected.add(incoming.substring(0,7)+"A");
                else {expected.add("PROGA");if(body.startsWith("CALL"))expected.add(incoming);}
            }
            procedure.append("GOBACK.\nBODY.\n").append(body).append("\nEXIT.");
            var result=check("01 TARGET PIC X(8).",procedure.toString(),expected.toArray(String[]::new));
            assertEquals(2,result.metrics().contexts(),"The body is parameterized, including observations and partial writes");
        }
    }
    @Test void predicateReadAfterAFullWriteDoesNotDistinguishInputs()throws Exception {
        var procedure=new StringBuilder("MAIN.\n");
        for(int i=0;i<12;i++)procedure.append("MOVE '").append(i).append("' TO FLAG.\nPERFORM BODY.\n");
        procedure.append("GOBACK.\nBODY.\nMOVE 'Y' TO FLAG.\nIF FLAG = 'Y'\nCALL 'GOOD'\nELSE\nCALL 'BAD'\nEND-IF.\nEXIT.");
        var result=check("01 FLAG PIC X(8).",procedure.toString(),"GOOD");
        assertEquals(2,result.metrics().contexts());
    }
    @Test void unrelatedParagraphPlacementDoesNotPolluteNestedInputs()throws Exception {
        for(boolean after:List.of(false,true)) {
            var main=new StringBuilder("MAIN.\nMOVE 'PROGA' TO TARGET.\n");
            for(int i=0;i<12;i++)main.append("MOVE '").append(i).append("' TO FLAG.\nPERFORM OUTER.\n");
            main.append("CALL TARGET.\nGOBACK.\nOUTER.\nPERFORM INNER.\nEXIT.\n");
            String inner="INNER.\nMOVE 'PROGA' TO TARGET.\nEXIT.\n";
            String tail="TAIL.\nIF FLAG = '0' CALL 'BAD-A' ELSE CALL 'BAD-B' END-IF.\nEXIT.\n";
            var result=check("01 TARGET PIC X(8).\n01 FLAG PIC X(8).",main+(after?inner+tail:tail+inner),"PROGA");
            assertEquals(3,result.metrics().contexts(),"placement must not change the nested invocation input");
        }
    }
    @Test void unusedHandlersShareSummariesAndAreRestoredForCallerEvents()throws Exception {
        var procedure=new StringBuilder("MAIN.\n");
        for(int i=0;i<12;i++)procedure.append("EXEC CICS HANDLE CONDITION ERROR(H-").append(i)
            .append(") END-EXEC.\nPERFORM BODY.\n");
        procedure.append("EXEC CICS LINK PROGRAM('TARGET') END-EXEC.\nGOBACK.\nBODY.\nCONTINUE.\nEXIT.\n");
        for(int i=0;i<12;i++)procedure.append("H-").append(i).append(".\nCALL 'HAND-").append(i).append("'.\nGOBACK.\n");
        var result=check("",procedure.toString(),"TARGET","HAND-11");
        assertEquals(3,result.metrics().contexts()); // Root, shared BODY and the reached handler.
    }
    @Test void sharedQueryIndexKeepsCallerValueCorrelation()throws Exception {
        check("01 FLAG PIC X.\n01 TARGET PIC X(8).","MAIN.\nMOVE '0' TO FLAG.\nPERFORM BODY.\nCALL TARGET.\n"
            +"MOVE '1' TO FLAG.\nPERFORM BODY.\nGOBACK.\nBODY.\nIF FLAG = '0'\nMOVE 'ONLY' TO TARGET\n"
            +"ELSE\nMOVE 'UNOBS' TO TARGET\nEND-IF.\nEXIT.","ONLY");
    }

    @Test void fullUnknownWriteMasksTheCallersPreviousValue()throws Exception {
        var result=check("01 TARGET PIC X(8).\n01 SOURCE-VALUE PIC X(8).", "MAIN.\nMOVE 'OLD' TO TARGET.\n"
            +"PERFORM BODY.\nCALL TARGET.\nGOBACK.\nBODY.\nMOVE SOURCE-VALUE TO TARGET.\nEXIT.");
        assertTrue(result.diagnostics().stream().anyMatch(d->d.contains("DYNAMIC_REMAINDER")));
    }
    @Test void conditionalWritesPreserveTheIdentityReturnPath()throws Exception {
        for(boolean nested:List.of(false,true)) {
            check("01 TARGET PIC X(8).\n01 FLAG PIC X.","MAIN.\nMOVE 'OLD' TO TARGET.\nPERFORM BODY.\nCALL TARGET.\n"
                +"GOBACK.\nBODY.\nIF FLAG = 'Y'\n"+(nested?"PERFORM HELPER":"MOVE 'NEW' TO TARGET")
                +"\nELSE\n"+"CONTINUE\n".repeat(12)+"END-IF.\nEXIT.\nHELPER.\nMOVE 'NEW' TO TARGET.\nEXIT.","OLD","NEW");
        }
    }
    @Test void conditionalRegistrationPreservesThePreviousHandlerOnTheOtherPath()throws Exception {
        check("01 FLAG PIC X.","MAIN.\nEXEC CICS HANDLE CONDITION ERROR(FIRST-HANDLER) END-EXEC.\nPERFORM BODY.\n"
            +"EXEC CICS LINK PROGRAM('TARGET') END-EXEC.\nGOBACK.\nBODY.\nIF FLAG = 'Y'\n"
            +"EXEC CICS HANDLE CONDITION ERROR(SECOND-HANDLER) END-EXEC\nELSE\n"+"CONTINUE\n".repeat(12)+"END-IF.\nEXIT.\n"
            +"FIRST-HANDLER.\nCALL 'FIRST'.\nGOBACK.\nSECOND-HANDLER.\nCALL 'SECOND'.\nGOBACK.","FIRST","SECOND","TARGET");
    }

}
