package io.github.gustavo2358.cobolexplorer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class NumericEditTest {
    @Test void grammarOwnsEditedDescriptorsWithoutExpandingRepetition() {
        var p=NumericEditSyntax.parse("+9(10).99",false,true).orElseThrow();
        assertEquals(12,p.digits());assertEquals(2,p.scale());assertEquals(14,p.extent());
        assertEquals(4,p.parts().size());
        var floating=NumericEditSyntax.parse("----9",false,true).orElseThrow();
        assertEquals(4,floating.digits());assertEquals(5,floating.extent());
        assertTrue(NumericEditSyntax.parse("9(2147483647).99",false,true).isEmpty());
        assertTrue(NumericEditSyntax.parse("S9(4)V99",false,true).isEmpty());
        assertTrue(NumericEditSyntax.parse("Z9Z",false,true).isEmpty());
    }
    @Test void decimalCommaAndCurrencyAreExplicitInputs() {
        var p=NumericEditSyntax.parse("+ZZZ.ZZZ,99",true,true).orElseThrow();
        assertEquals(2,p.scale());assertEquals(8,p.digits());
        assertEquals(",",p.parts().stream().filter(x->x.kind()==Ast.EditKind.RADIX).findFirst().orElseThrow().text());
        assertTrue(NumericEditSyntax.parse("$ZZ9.99",false,false).isEmpty());
    }
    @Test void sourcePublishesTypedEditingAndProgramOptions() {
        var normal=AstBoundaryTestSupport.analyze(ScalarMoveCheckpoint4ATest.program("01 EDIT-A PIC +9(10).99.","GOBACK."),"edit.cbl");
        var picture=AstBoundaryTestSupport.nodes(normal,Ast.PictureClause.class).get(0);
        assertEquals(2,picture.edited().orElseThrow().scale());
        var source=ScalarMoveCheckpoint4ATest.program("01 EDIT-A PIC +999.999,99.","GOBACK.")
            .replace("DATA DIVISION.","ENVIRONMENT DIVISION.\nCONFIGURATION SECTION.\nSPECIAL-NAMES. DECIMAL-POINT IS COMMA.\nDATA DIVISION.");
        var comma=AstBoundaryTestSupport.analyze(source,"edit-comma.cbl");
        assertEquals(2,AstBoundaryTestSupport.nodes(comma,Ast.PictureClause.class).get(0).edited().orElseThrow().scale());
    }
    @Test void editedReceivingItemsPublishPreciseNumberFormatting() {
        var source=ScalarMoveCheckpoint4ATest.program("01 NUMBER-A PIC S9(6)V99 COMP-3.\n01 EDIT-A PIC +ZZZ,ZZ9.99.",
            "MOVE NUMBER-A TO EDIT-A.\nMOVE -12.34 TO EDIT-A.\nGOBACK.");
        var a=AstBoundaryTestSupport.analyze(source,"edit-move.cbl");
        var p=EofUnitBoundaryTest.publish(a,0,StorageLayoutSemantics.Profile.UNSPECIFIED);
        assertEquals(2,p.moves().size());
        assertTrue(p.moves().stream().allMatch(m->m.copySemantics().name().equals("FORMATTED_NUMBER")));
        assertFalse(p.gaps().stream().anyMatch(g->g.code().equals("MOVE_IDENTITY_NOT_PROVEN")));
    }
    @Test void officialFormattingOracles() {
        check("+9999.99","-123.456","-0123.45");
        check("ZZZZ.99","0","    .00");
        check("ZZZZ.ZZ","0","       ");
        check("****.**","0","****.**");
        check("----9","-23","  -23");
        check("----9","23","   23");
        check("ZZ99.99","0","  00.00");
        check("+ZZZ,ZZZ,ZZZ.99","1234.56","+      1,234.56");
        check("+ZZZ,ZZZ,ZZZ.ZZ","0","               ");
        check("9(9).99-","-12.34","000000012.34-");
        check("+9(06)","1234567","+234567");
        check("$$$$.99",".123","   $.12");
        check("$$$9.99",".12","  $0.12");
        check("$,$$$,999.99","-1234.56","   $1,234.56");
    }
    private static void check(String picture,String value,String expected) {
        assertEquals(expected,NumericEditRule.format(new java.math.BigDecimal(value),NumericEditSyntax.parse(picture,false,true).orElseThrow()),picture);
    }
}
