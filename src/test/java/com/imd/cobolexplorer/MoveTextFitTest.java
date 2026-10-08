package com.imd.cobolexplorer;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.imd.cobolexplorer.semanticproduct.CobolSemanticProduct.*;

class MoveTextFitTest {
    private com.imd.cobolexplorer.semanticproduct.CobolSemanticPort publish(String data,String body) {
        var a=AstBoundaryTestSupport.analyze(ScalarMoveCheckpoint4ATest.program(data,body),"text-fit.cbl");
        return EofUnitBoundaryTest.publish(a,0,StorageLayoutSemantics.Profile.UNSPECIFIED);
    }
    @Test void fittingPublishesARecipeWithoutExpandedResult() {
        var p=publish("01 TARGET-A PIC X(1000000000).","MOVE 'A' TO TARGET-A.\nGOBACK.");
        var fields=java.util.Arrays.stream(p.moves().get(0).textAdjustment().orElseThrow().getClass().getRecordComponents()).map(java.lang.reflect.RecordComponent::getName).toList();
        assertEquals(1000000000,p.moves().get(0).textAdjustment().orElseThrow().receiverExtent());
        assertFalse(fields.contains("result"),"fitting transports rule and extent, not expanded receiver text");
    }
    @Test void coarseDeclarationMappingDoesNotEraseTypedWholeItems() throws Exception {
        for(var picture:java.util.List.of("X(8)","S9(4) COMP")) {
            var source=ScalarMoveCheckpoint4ATest.program("01 TARGET-A PIC "+picture+".",
                "MOVE "+(picture.startsWith("X")?"'ABC'":"12")+" TO TARGET-A.\nGOBACK.");
            int begin=source.indexOf("01 TARGET-A"),end=begin+"01".length();
            var map=SourceMap.mapped(source,"coarse.cbl",source,java.util.List.of(
                new SourceMap.Segment(0,begin,"coarse.cbl",0,begin,java.util.List.of(),true),
                new SourceMap.Segment(begin,end,"coarse.cbl",begin,end,java.util.List.of(),false),
                new SourceMap.Segment(end,source.length(),"coarse.cbl",end,source.length(),java.util.List.of(),true)));
            var pre=new PreprocessorEngine(Bindings.cobol(),new CopybookLibrary(java.nio.file.Path.of("src/test/resources/cobol/provenance/cpy"))).process(map,"coarse.cbl");
            var a=AstBoundaryTestSupport.analyze(pre,"coarse.cbl");
            var p=EofUnitBoundaryTest.publish(a,0,StorageLayoutSemantics.Profile.UNSPECIFIED);
            assertFalse(p.dataDeclarations().get(0).provenance().exact());
            assertFalse(p.gaps().stream().anyMatch(g->g.code().equals("MOVE_IDENTITY_NOT_PROVEN")),picture);
        }
    }
    @Test void initialProgramPreservesTheTextFamilyLayout() {
        var source=ScalarMoveCheckpoint4ATest.program("01 REC-A.\n05 A PIC X(2).\n05 B PIC X(2).",
            "MOVE '1234' TO REC-A.\nGOBACK.").replace("PROGRAM-ID. SAMPLE.","PROGRAM-ID. SAMPLE IS INITIAL.");
        var a=AstBoundaryTestSupport.analyze(source,"initial-text.cbl");
        var layout=StorageLayoutSemantics.analyze(a.build(),a.tables(),a.resolution(),a.report(),StorageLayoutSemantics.Profile.UNSPECIFIED,StorageComponents.analyze(a.build(),a.tables(),a.resolution()),true);
        assertEquals(3,layout.logicalViews().size());
        var storage=StorageAccessSemantics.analyze(a.build(),a.resolution(),layout);
        var scalar=ScalarMoveSemantics.analyze(a.build(),a.tables(),a.resolution(),a.report(),StorageComponents.analyze(a.build(),a.tables(),a.resolution()),java.util.Optional.of(storage));
        assertTrue(scalar.logicalMoves().move(new StorageLayoutSemantics.Key(a.model().programUnits().get(0).id(),AstBoundaryTestSupport.nodes(a,Ast.MoveStatement.class).get(0).meta().id())).orElseThrow().admitted());
    }
    @Test void dataFitUsesReceivingLengthForPaddingAndTruncation() {
        var p=publish("01 REC-A.\n05 SHORT-A PIC X(3).\n05 LONG-A PIC X(8).",
            "MOVE SHORT-A OF REC-A TO LONG-A OF REC-A.\nMOVE LONG-A TO SHORT-A.\nGOBACK.");
        assertEquals(2,p.moves().size());
        for(var move:p.moves()) {
            assertEquals(CopySemantics.FITTED_TEXT,move.copySemantics());
            assertTrue(move.target().wholeItemAccess().isPresent());
            assertTrue(((DataReference)move.source()).wholeItemAccess().isPresent());
            assertTrue(move.textAdjustment().isEmpty(),"DATA fitting does not invent a constant result");
        }
        assertFalse(p.gaps().stream().anyMatch(g->g.code().equals("MOVE_IDENTITY_NOT_PROVEN")));
    }
    @Test void integerLiteralsKeepTheirWrittenDigitsWithoutTheOperationalSign() {
        var p=publish("01 TARGET-A PIC X(6).\n01 NUMBER-A PIC S9(5).",
            "MOVE -00052 TO TARGET-A.\nMOVE +005 TO TARGET-A.\nMOVE 0 TO TARGET-A.\nMOVE -00052 TO NUMBER-A.\nGOBACK.");
        assertEquals("00052 ",TextFitOracle.value(p.moves().get(0)));
        assertEquals("005   ",TextFitOracle.value(p.moves().get(1)));
        assertEquals("0     ",TextFitOracle.value(p.moves().get(2)));
        assertEquals("-52",p.moves().get(3).numericTransfers().get(0).value().orElseThrow().toPlainString());
    }
    @Test void literalTruncationKeepsTheLeftmostCharacters() {
        var p=publish("01 TARGET-A PIC X(3).","MOVE 'ABCDE' TO TARGET-A.\nGOBACK.");
        var move=p.moves().get(0);
        assertEquals(CopySemantics.FITTED_TEXT,move.copySemantics());
        assertEquals("ABC",TextFitOracle.value(move));
    }
    @Test void spacesFillIndependentFieldsInsideMixedRecords() {
        var p=publish("01 REC-A.\n05 TEXT-A PIC X(8).\n05 NUMBER-A PIC S9(4) COMP.",
            "MOVE SPACES TO TEXT-A.\nGOBACK.");
        var move=p.moves().get(0);
        assertEquals(CopySemantics.FITTED_TEXT,move.copySemantics());
        assertEquals("        ",TextFitOracle.value(move));
        assertFalse(p.gaps().stream().anyMatch(g->g.code().equals("MOVE_IDENTITY_NOT_PROVEN")));
    }
    @Test void figurativeZeroUsesTheReceiverCategory() {
        for(var spelling:java.util.List.of("ZERO","ZEROS","ZEROES")) {
            var p=publish("01 REC-A.\n05 TEXT-A PIC X(4).\n05 NUMBER-A PIC S9(4) COMP-3.",
                "MOVE "+spelling+" TO TEXT-A.\nMOVE "+spelling+" TO NUMBER-A.\nMOVE '0' TO TEXT-A.\nGOBACK.");
            var moves=p.moves();
            assertEquals(LiteralKind.FIGURATIVE_ZERO,((LiteralSource)moves.get(0).source()).kind());
            assertEquals("0",((LiteralSource)moves.get(0).source()).logicalValue().orElseThrow().value());
            assertEquals(CopySemantics.FITTED_TEXT,moves.get(0).copySemantics(),spelling);
            assertEquals("0000",TextFitOracle.value(moves.get(0)));
            assertEquals("0",moves.get(1).numericTransfers().get(0).value().orElseThrow().toPlainString());
            assertEquals("0   ",TextFitOracle.value(moves.get(2)));
            assertFalse(p.gaps().stream().anyMatch(g->g.code().equals("MOVE_IDENTITY_NOT_PROVEN")));
        }
    }
    @Test void binaryIntegerDataHasAnExplicitTextConversion() {
        for(var usage:java.util.List.of("COMP","COMP-5")) {
            var p=publish("01 SOURCE-A PIC S9(4) "+usage+".\n01 TARGET-A PIC X(8).", "MOVE SOURCE-A TO TARGET-A.\nGOBACK.");
            assertEquals("FORMATTED_NUMBER",p.moves().get(0).copySemantics().name(),usage);
        }
    }
    @Test void integerDataFormatsDigitsUsingTheSendingPicture() {
        var p=publish("01 SOURCE-A PIC S9(5) COMP-3.\n01 SCALE-A PIC 99PP.\n01 TARGET-A PIC X(8).",
            "MOVE SOURCE-A TO TARGET-A.\nMOVE SCALE-A TO TARGET-A.\nGOBACK.");
        for(var move:p.moves()) {
            assertEquals("FORMATTED_NUMBER",move.copySemantics().name());
            assertTrue(move.target().wholeItemAccess().isPresent());
            assertTrue(((DataReference)move.source()).wholeItemAccess().isPresent());
            assertTrue(move.textAdjustment().isEmpty(),"DATA formatting must not invent a constant");
        }
        assertFalse(p.gaps().stream().anyMatch(g->g.code().equals("MOVE_IDENTITY_NOT_PROVEN")));
    }
    @Test void indexAndAliasStillRequireTheirOwnAccessProof() {
        var p=publish("01 SOURCE-A PIC X(8).\n01 TARGET-A PIC X(3) OCCURS 2.","MOVE SOURCE-A TO TARGET-A(1).\nGOBACK.");
        assertTrue(p.moves().isEmpty()||p.moves().get(0).copySemantics()==CopySemantics.UNAVAILABLE);
    }
}
