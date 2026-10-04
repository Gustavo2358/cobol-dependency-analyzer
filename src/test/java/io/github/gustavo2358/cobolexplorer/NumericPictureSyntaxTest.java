package io.github.gustavo2358.cobolexplorer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class NumericPictureSyntaxTest {
    @Test void precisionScaleAndSignAreIndependent() {
        assertEquals(new Ast.NumericPicture(5,2,true),NumericPictureSyntax.parse("S9(3)V99").orElseThrow());
        assertEquals(new Ast.NumericPicture(3,3,false),NumericPictureSyntax.parse("V999").orElseThrow());
        assertEquals(new Ast.NumericPicture(3,5,false),NumericPictureSyntax.parse("PP999").orElseThrow());
        assertEquals(new Ast.NumericPicture(3,-2,false),NumericPictureSyntax.parse("999PP").orElseThrow());
        assertEquals(1000000000,NumericPictureSyntax.parse("9(1000000000)").orElseThrow().digits());
    }
    @Test void explicitRedundantDecimalPointWithScalingPositions() {
        assertEquals(new Ast.NumericPicture(3,5,false),NumericPictureSyntax.parse("VPP999").orElseThrow());
        assertEquals(new Ast.NumericPicture(3,-2,false),NumericPictureSyntax.parse("999PPV").orElseThrow());
    }
    @Test void malformedAndEditedPicturesCannotClaimAnUneditedDomain() {
        for(String s:new String[]{"S","9(0)","9(999999999999999999999999)","9V9V9","9P9","9Z9","+999.99","9(S)","9(-1)"})
            assertTrue(NumericPictureSyntax.parse(s).isEmpty(),s);
    }
}
