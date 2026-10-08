package io.github.gustavo2358.cobolexplorer;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class DependencyAnalyzerTest {
    @TempDir Path temp;
    private DependencyAnalyzer.Result analyze(String data,String procedure)throws Exception {
        String source="IDENTIFICATION DIVISION.\nPROGRAM-ID. TEST-PROG.\nDATA DIVISION.\nWORKING-STORAGE SECTION.\n"+data+"\nPROCEDURE DIVISION.\n"+procedure+"\n";
        var path=temp.resolve("test.cbl");Files.writeString(path,source.lines().map(s->"       "+s+"\n").reduce("",String::concat));
        return new DependencyAnalyzer().analyze(path,DependencyAnalyzer.Options.defaults());
    }
    private void targets(String data,String procedure,String... expected)throws Exception {
        var result=analyze(data,procedure);
        var actual=new TreeSet<String>();for(var d:result.programs().get(0).dependencies())if(d.type().equals("program"))actual.add(d.name());
        assertEquals(new TreeSet<>(List.of(expected)),actual,result.diagnostics().toString());
    }
    @Test void literalsDeduplicateAndIgnoreUnrelatedText()throws Exception {
        targets("01 TARGET PIC X(8).","DISPLAY 'FAKE'.\nCALL 'REAL'.\nCALL 'REAL'.\nGOBACK.","REAL");
    }
    @Test void valueAndLifecycleRemainder()throws Exception {
        var r=analyze("01 TARGET PIC X(8) VALUE 'PROGA'.","CALL TARGET.\nGOBACK.");
        assertEquals("PROGA",r.programs().get(0).dependencies().get(0).name());
        assertTrue(r.diagnostics().stream().anyMatch(s->s.contains("DYNAMIC_REMAINDER")));
    }
    @Test void successiveAssignmentsKillImpossibleValuesAndFutureWrites()throws Exception {
        targets("01 TARGET PIC X(8).","MOVE 'PROGA' TO TARGET.\nMOVE 'PROGB' TO TARGET.\nCALL TARGET.\nMOVE 'FUTURE' TO TARGET.\nGOBACK.","PROGB");
    }
    @Test void moveVariableChain()throws Exception {
        targets("01 A PIC X(8).\n01 B PIC X(8).","MOVE 'PROGA' TO A.\nMOVE A TO B.\nCALL B.\nGOBACK.","PROGA");
    }
    @Test void unknownBranchJoinsCandidates()throws Exception {
        targets("01 TARGET PIC X(8) VALUE 'PROGA'.\n01 FLAG PIC X.","IF FLAG = 'Y'\nMOVE 'PROGB' TO TARGET\nEND-IF.\nCALL TARGET.\nGOBACK.","PROGA","PROGB");
    }
    @Test void knownPredicateEliminatesUnreachableCandidate()throws Exception {
        targets("01 TARGET PIC X(8).\n01 FLAG PIC X.","MOVE 'Y' TO FLAG.\nIF FLAG = 'Y'\nMOVE 'GOOD' TO TARGET\nELSE\nMOVE 'BAD' TO TARGET\nEND-IF.\nCALL TARGET.\nGOBACK.","GOOD");
    }
    @Test void conditionRefinesJoinedTarget()throws Exception {
        targets("01 TARGET PIC X(8).\n01 FLAG PIC X.","IF FLAG = 'Y'\nMOVE 'A' TO TARGET\nELSE\nMOVE 'B' TO TARGET\nEND-IF.\nIF TARGET = 'A'\nCALL TARGET\nEND-IF.\nGOBACK.","A");
    }
    @Test void redefiningGroupsUseLogicalCorrespondence()throws Exception {
        targets("01 WS-AREA.\n05 ORIGINAL PIC X(8).\n01 ALIAS-AREA REDEFINES WS-AREA.\n05 ALIAS-TARGET PIC X(8).","MOVE 'PROGA' TO ORIGINAL.\nCALL ALIAS-TARGET.\nGOBACK.","PROGA");
    }
    @Test void groupMoveUsesTextOrderNotFieldNames()throws Exception {
        targets("01 A-GROUP.\n05 A PIC X(3).\n05 B PIC X(5).\n01 B-GROUP.\n05 TARGET PIC X(8).","MOVE 'PRO' TO A.\nMOVE 'GA' TO B.\nMOVE A-GROUP TO B-GROUP.\nCALL TARGET.\nGOBACK.","PROGA");
    }
    @Test void renamesOneField()throws Exception {
        targets("01 WS-AREA.\n05 ORIGINAL PIC X(8).\n66 ALIAS-TARGET RENAMES ORIGINAL.","MOVE 'PROGA' TO ORIGINAL.\nCALL ALIAS-TARGET.\nGOBACK.","PROGA");
    }
    @Test void partialWritesAssembleText()throws Exception {
        targets("01 TARGET PIC X(8).","MOVE 'PROGA' TO TARGET.\nMOVE 'B' TO TARGET(5:1).\nCALL TARGET.\nGOBACK.","PROGB");
    }
    @Test void paddingTruncation()throws Exception {
        targets("01 TARGET PIC X(5).","MOVE 'PROGRAM' TO TARGET.\nCALL TARGET.\nGOBACK.","PROGR");
    }
    @Test void performReturnsToOwnCaller()throws Exception {
        targets("01 TARGET PIC X(8).","MAIN.\nMOVE 'PROGA' TO TARGET.\nPERFORM ROUTINE.\nMOVE 'PROGB' TO TARGET.\nPERFORM ROUTINE.\nGOBACK.\nROUTINE.\nCALL TARGET.","PROGA","PROGB");
    }
    @Test void performWritesReturnBeforeSink()throws Exception {
        targets("01 TARGET PIC X(8).","MAIN.\nPERFORM A.\nPERFORM B.\nCALL TARGET.\nGOBACK.\nA.\nMOVE 'PROGA' TO TARGET.\nB.\nMOVE 'PROGB' TO TARGET.","PROGB");
    }
    @Test void performThruAndNestedInvocations()throws Exception {
        targets("01 TARGET PIC X(8).","MAIN.\nPERFORM A THRU B.\nCALL TARGET.\nGOBACK.\nA.\nPERFORM C.\nB.\nMOVE 'PROGB' TO TARGET.\nC.\nMOVE 'PROGA' TO TARGET.","PROGB");
    }
    @Test void loopingJoinsConverge()throws Exception {
        targets("01 TARGET PIC X(8) VALUE 'PROGA'.\n01 FLAG PIC X.","PERFORM UNTIL FLAG = 'Y'\nMOVE 'PROGB' TO TARGET\nEND-PERFORM.\nCALL TARGET.\nGOBACK.","PROGA","PROGB");
    }
    @Test void gotoSkipsUnreachableLiteral()throws Exception {
        targets("01 TARGET PIC X(8).","MAIN.\nGO TO DONE.\nCALL 'BAD'.\nDONE.\nCALL 'GOOD'.\nGOBACK.","GOOD");
    }
    @Test void evaluateKnownSelector()throws Exception {
        targets("01 TARGET PIC X(8).\n01 FLAG PIC X.","MOVE 'Y' TO FLAG.\nEVALUATE FLAG\nWHEN 'Y'\nMOVE 'GOOD' TO TARGET\nWHEN OTHER\nMOVE 'BAD' TO TARGET\nEND-EVALUATE.\nCALL TARGET.\nGOBACK.","GOOD");
    }
    @Test void cicsDynamicSharesCallResolver()throws Exception {
        targets("01 TARGET PIC X(8) VALUE 'PROGA'.","EXEC CICS LINK PROGRAM(TARGET) NOHANDLE\nEND-EXEC.\nMOVE 'PROGB' TO TARGET.\nEXEC CICS XCTL PROGRAM(TARGET) NOHANDLE\nEND-EXEC.\nGOBACK.","PROGA","PROGB");
    }
    @Test void foreignMutationKeepsKnownCandidateAndRemainder()throws Exception {
        targets("01 TARGET PIC X(8).","MOVE 'PROGA' TO TARGET.\nCALL 'OTHER' USING TARGET.\nCALL TARGET.\nGOBACK.","OTHER","PROGA");
    }
    @Test void unknownNeverInventsDependency()throws Exception {
        targets("01 TARGET PIC X(8).","CALL TARGET.\nGOBACK.");
    }
    @Test void declaredRowsReadThroughOccursViewWithoutGlobalLiteralScan()throws Exception {
        targets("01 ROWS-DATA.\n05 FILLER PIC 99 VALUE 1.\n05 FILLER PIC X(8) VALUE 'PROGA001'.\n05 FILLER PIC 99 VALUE 2.\n05 FILLER PIC X(8) VALUE 'PROGB001'.\n01 ROWS-VIEW REDEFINES ROWS-DATA.\n05 ROW-ITEM OCCURS 3 TIMES.\n10 ROW-NUMBER PIC 99.\n10 ROW-PROGRAM PIC X(8).\n01 IDX PIC 99.\n01 UNRELATED PIC X(8) VALUE 'FAKE0001'.",
            "CALL ROW-PROGRAM(IDX).\nGOBACK.","PROGA001","PROGB001");
    }
    @Test void cicsDatasetIsTheFileAlias()throws Exception {
        var result=analyze("01 WS-FILE PIC X(8) VALUE 'ACCTDAT'.","EXEC CICS READ DATASET(WS-FILE) NOHANDLE\nEND-EXEC.\nGOBACK.");
        assertEquals(List.of("ACCTDAT"),result.programs().get(0).dependencies().stream().filter(d->d.type().equals("file")).map(DependencyAnalyzer.Dependency::name).toList());
    }
    @Test void minimalAtomicJsonAndExplicitGlobalFailure()throws Exception {
        analyze("01 TARGET PIC X(8).","CALL 'PROGA'.\nGOBACK.");
        var output=temp.resolve("dependencies.json");
        assertEquals(0,DependencyMain.run(new String[]{"--source",temp.resolve("test.cbl").toString(),"--output",output.toString()}));
        var json=new com.fasterxml.jackson.databind.ObjectMapper().readTree(output.toFile());
        assertEquals(Set.of("program","dependencies"),keys(json));assertEquals(Set.of("type","name","at"),keys(json.get("dependencies").get(0)));
        String previous=Files.readString(output);
        assertEquals(2,DependencyMain.run(new String[]{"--source",temp.resolve("test.cbl").toString(),"--output",output.toString(),"--max-work","1"}));
        assertEquals(previous,Files.readString(output));
    }
    private static Set<String> keys(com.fasterxml.jackson.databind.JsonNode node){var keys=new HashSet<String>();node.fieldNames().forEachRemaining(keys::add);return keys;}
    @Test void manyPerformSitesDoNotCloneBodiesOrEnumeratePaths()throws Exception {
        String calls="PERFORM ROUTINE.\n".repeat(1000);
        var result=analyze("01 TARGET PIC X(8).","MAIN.\nMOVE 'PROGA' TO TARGET.\n"+calls+"CALL TARGET.\nGOBACK.\nROUTINE.\nDISPLAY TARGET.");
        assertEquals("PROGA",result.programs().get(0).dependencies().get(0).name());
        assertTrue(result.metrics().contexts()<5,result.metrics().toString());
        assertTrue(result.metrics().workItems()<5000,result.metrics().toString());
    }
    @Test void exitSectionReturnsWithoutRunningLaterParagraphs()throws Exception {
        targets("01 TARGET PIC X(8).","MAIN.\nPERFORM WORK-SECTION.\nCALL TARGET.\nGOBACK.\nWORK-SECTION SECTION.\nA.\nMOVE 'GOOD' TO TARGET.\nPERFORM 1 TIMES\nEXIT SECTION\nEND-PERFORM.\nB.\nMOVE 'BAD' TO TARGET.","GOOD");
    }
    @Test void functionsTransformOnlyReachingValues()throws Exception {
        targets("01 A PIC X(8).\n01 TARGET PIC X(8).","MOVE 'proga' TO A.\nMOVE FUNCTION UPPER-CASE(A) TO TARGET.\nCALL TARGET.\nGOBACK.","PROGA");
    }
    @Test void uncertainSliceCannotTurnPartialLiteralIntoWholeTarget()throws Exception {
        targets("01 TARGET PIC X(8).\n01 IDX PIC 9.","MOVE 'PROGA' TO TARGET.\nMOVE 'B' TO TARGET(IDX:1).\nCALL TARGET.\nGOBACK.","PROGA");
    }
    @Test void callHandlersAreAlternativesAndResumeAfterTheirOwnBodies()throws Exception {
        targets("01 TARGET PIC X(8).", "MOVE 'OLD' TO TARGET.\nCALL 'EXTERNAL'\nON EXCEPTION MOVE 'FAILURE' TO TARGET\nNOT ON EXCEPTION MOVE 'SUCCESS' TO TARGET\nEND-CALL.\nCALL TARGET.\nGOBACK.","EXTERNAL","FAILURE","SUCCESS");
    }
    @Test void zeroTimesSkipsBodyAndKeepsContinuation()throws Exception {
        targets("01 TARGET PIC X(8).", "MOVE 'GOOD' TO TARGET.\nPERFORM 0 TIMES\nMOVE 'BAD' TO TARGET\nEND-PERFORM.\nCALL TARGET.\nGOBACK.","GOOD");
    }
    @Test void initializeRemovesAnImpossibleOldName()throws Exception {
        targets("01 TARGET PIC X(8).", "MOVE 'OLD' TO TARGET.\nINITIALIZE TARGET.\nCALL TARGET.\nGOBACK.");
    }
    @Test void unknownTextTransformationOpensTheRelevantOperand()throws Exception {
        var result=analyze("01 TARGET PIC X(8).", "MOVE 'PROGA' TO TARGET.\nINSPECT TARGET REPLACING ALL 'A' BY 'B'.\nCALL TARGET.\nGOBACK.");
        assertTrue(result.diagnostics().stream().anyMatch(d->d.contains("DYNAMIC_REMAINDER")));
    }
    @Test void groupCorrelationSurvivesPerformAndPredicateRefinement()throws Exception {
        targets("01 AREA-X.\n05 A PIC X(4).\n05 B PIC X(4).\n01 TARGET PIC X(8).\n01 FLAG PIC X.",
            "IF FLAG = 'Y'\nMOVE 'PROG' TO A\nMOVE '0001' TO B\nELSE\nMOVE 'MODU' TO A\nMOVE '0002' TO B\nEND-IF.\nPERFORM ROUTINE.\nGOBACK.\nROUTINE.\nIF A = 'PROG'\nMOVE AREA-X TO TARGET\nCALL TARGET\nEND-IF.","PROG0001");
    }
    @Test void knownTableIndexDoesNotInventOtherDeclaredRows()throws Exception {
        targets("01 ROWS-DATA.\n05 FILLER PIC X(8) VALUE 'PROGA001'.\n05 FILLER PIC X(8) VALUE 'PROGB001'.\n01 ROWS-VIEW REDEFINES ROWS-DATA.\n05 ROW-PROGRAM PIC X(8) OCCURS 2 TIMES.\n01 IDX PIC 9.",
            "MOVE 1 TO IDX.\nCALL ROW-PROGRAM(IDX).\nGOBACK.","PROGA001");
    }
    @Test void knownRowWritesRemainLocalAndSurvivePerform()throws Exception {
        targets("01 ROWS-VIEW.\n05 ROW-PROGRAM PIC X(8) OCCURS 2 TIMES.",
            "MOVE 'PROGA001' TO ROW-PROGRAM(1).\nMOVE 'PROGB001' TO ROW-PROGRAM(2).\nPERFORM ROUTINE.\nGOBACK.\nROUTINE.\nCALL ROW-PROGRAM(1).", "PROGA001");
    }
    @Test void declaredOccursValueIsAvailableAtEveryKnownRow()throws Exception {
        targets("01 ROWS-VIEW.\n05 ROW-PROGRAM PIC X(8) OCCURS 2 TIMES\nVALUE 'PROGA001'.", "CALL ROW-PROGRAM(2).\nGOBACK.", "PROGA001");
    }
    @Test void fullGroupMoveDistributesItsDeclaredTextRows()throws Exception {
        targets("01 ROWS-VIEW.\n05 ROW-PROGRAM PIC X(8) OCCURS 2 TIMES.",
            "MOVE 'PROGA001PROGB001' TO ROWS-VIEW.\nCALL ROW-PROGRAM(2).\nGOBACK.", "PROGB001");
    }
    @Test void knownClassPredicateDoesNotPublishImpossibleBranch()throws Exception {
        targets("01 FLAG PIC X(8).", "MOVE 'PROGA' TO FLAG.\nIF FLAG IS NUMERIC\nCALL 'BAD'\nELSE\nCALL 'GOOD'\nEND-IF.\nGOBACK.","GOOD");
    }
    @Test void abbreviatedRelationsReuseFrontendNormalization()throws Exception {
        targets("01 A PIC X.\n01 B PIC X.\n01 C PIC X.", "MOVE 'A' TO A.\nMOVE 'B' TO B.\nMOVE 'C' TO C.\nIF A = B OR C\nCALL 'BAD'\nELSE\nCALL 'GOOD'\nEND-IF.\nGOBACK.","GOOD");
    }
    @Test void indexedPartialWriteCannotAssembleNamesFromOtherRows()throws Exception {
        targets("01 ROWS-VIEW.\n05 ROW-PROGRAM PIC X(8) OCCURS 2 TIMES.",
            "MOVE 'PROGA001' TO ROW-PROGRAM(1).\nMOVE 'PROGB001' TO ROW-PROGRAM(2).\nMOVE 'C' TO ROW-PROGRAM(1)(5:1).\nCALL ROW-PROGRAM(1).\nGOBACK.", "PROGC001");
    }
}
