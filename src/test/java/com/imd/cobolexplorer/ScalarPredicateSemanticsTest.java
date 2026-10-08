package com.imd.cobolexplorer;

import org.junit.jupiter.api.Test;
import java.util.*;
import com.imd.cobolexplorer.semanticproduct.ConditionNames;
import static org.junit.jupiter.api.Assertions.*;

class ScalarPredicateSemanticsTest {
    private List<ConditionNames.Predicate> predicates(String declarations,String statements) {
        var a=AstBoundaryTestSupport.analyze(ScalarMoveCheckpoint4ATest.program(declarations,statements+"\nGOBACK."),"predicates.cbl");
        return EofUnitBoundaryTest.publish(a,0,StorageLayoutSemantics.Profile.UNSPECIFIED).conditionNames().orElseThrow().predicates();
    }
    @Test void sharedRelationsCrossIfWhenAndUntilWithMixed88() {
        var predicates=predicates("01 COUNT-A PIC 9.\n01 FLAG-A PIC X.\n88 ACTIVE-A VALUE 'Y'.",
            "IF COUNT-A > 3 AND ACTIVE-A CONTINUE END-IF.\n"
            +"EVALUATE TRUE\nWHEN COUNT-A > 3 AND ACTIVE-A CONTINUE\nEND-EVALUATE.\n"
            +"PERFORM UNTIL COUNT-A > 3 AND ACTIVE-A CONTINUE END-PERFORM.");
        assertEquals(3,predicates.size(),predicates.toString());
        for(var p:predicates){assertTrue(p.tree().complete());assertEquals("AND",p.tree().kind());assertEquals("GT",p.tree().children().get(0).kind());assertEquals("TEST",p.tree().children().get(1).kind());}
    }
    @Test void abbreviationKeepsRelationalNotAndLogicalNotDistinct() {
        var p=predicates("01 COUNT-A PIC 9.","IF COUNT-A NOT > 3 OR NOT 5 CONTINUE END-IF.").get(0).tree();
        assertEquals("OR",p.kind());assertEquals("LE",p.children().get(0).kind());
        assertEquals("NOT",p.children().get(1).kind());assertEquals("LE",p.children().get(1).children().get(0).kind());
    }
    @Test void valueSelectionIsEqualityAndRepeatedWhenIsOr() {
        var p=predicates("01 COUNT-A PIC 9.","EVALUATE COUNT-A WHEN 3 WHEN 5 CONTINUE\nWHEN OTHER CONTINUE\nEND-EVALUATE.");
        assertEquals(1,p.size());assertEquals("OR",p.get(0).tree().kind());
        assertEquals(List.of("EQ","EQ"),p.get(0).tree().children().stream().map(ConditionNames.Tree::kind).toList());
    }
    @Test void decimalAndTextLiteralMeaningIsPreserved() {
        var p=predicates("01 AMOUNT-A PIC 9V99.\n01 NAME-A PIC X(8).",
            "IF AMOUNT-A >= 1.25 OR NAME-A NOT = 'ABC' CONTINUE END-IF.").get(0).tree();
        assertEquals("GE",p.children().get(0).kind());assertEquals("NUMBER",p.children().get(0).children().get(1).kind());
        assertEquals("1.25",p.children().get(0).children().get(1).use());assertEquals("NE",p.children().get(1).kind());
    }
    @Test void distributedRelationsAndFalseSubjectKeepMeaning() {
        var p=predicates("01 COUNT-A PIC 9.","IF COUNT-A > (1 OR 3 AND 5) CONTINUE END-IF.\n"
            +"EVALUATE FALSE WHEN COUNT-A < 2 CONTINUE END-EVALUATE.");
        var branch=p.stream().filter(v->v.role().equals("IF")).findFirst().orElseThrow().tree();
        var selection=p.stream().filter(v->v.role().startsWith("EVALUATE_WHEN/")).findFirst().orElseThrow().tree();
        assertEquals("OR",branch.kind());assertEquals("GT",branch.children().get(0).kind());
        assertEquals("AND",branch.children().get(1).kind());assertEquals("NOT",selection.kind());
        assertEquals("LT",selection.children().get(0).kind());
    }
    @Test void unsupportedRangeKeepsSubjectReadWithoutBecomingEquality() {
        var p=predicates("01 COUNT-A PIC 9.","EVALUATE COUNT-A WHEN 1 THRU 3 CONTINUE END-EVALUATE.");
        assertEquals("UNKNOWN",p.get(0).tree().kind());assertFalse(p.get(0).tree().complete());
        assertEquals("READ",p.get(0).tree().children().get(0).kind());
    }
    @Test void unknownClassRetainsItsReadAndKnownSibling() {
        var p=predicates("01 COUNT-A PIC 9.\n01 TEXT-A PIC X.","IF COUNT-A > 1 AND TEXT-A NUMERIC CONTINUE END-IF.");
        assertEquals("AND",p.get(0).tree().kind());var other=p.get(0).tree().children().get(1);
        assertEquals("UNKNOWN",other.kind());assertEquals("READ",other.children().get(0).kind());
    }

    @Test void missingBindingKeepsReadOnlyEffectsSeparateFromOpenReads() {
        var p=predicates("01 FLAG-A PIC X.","IF FLAG-A = 'Y' AND MISSING-A = 1 CONTINUE END-IF.");
        var tree=p.get(0).tree();assertEquals("AND",tree.kind());
        assertEquals("READS_OPEN",tree.children().get(1).use());
        assertEquals("READ",tree.children().get(0).children().get(0).kind());
    }
}
