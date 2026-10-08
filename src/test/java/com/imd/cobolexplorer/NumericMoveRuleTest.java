package com.imd.cobolexplorer;
import com.imd.cobolexplorer.semanticproduct.NumericMoveRule;
import java.math.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class NumericMoveRuleTest {
    @Test void exponentSizeDoesNotExpandAnIntermediateInteger() {
        for(int scale:new int[]{Integer.MIN_VALUE,Integer.MAX_VALUE}) {
            var value=new BigDecimal(BigInteger.ONE,scale);
            assertEquals(0,NumericMoveRule.fit(value,2,0,false,"DISPLAY").signum());
            assertEquals(0,NumericMoveRule.fit(value,2,0,true,"NATIVE_BINARY").signum());
            assertEquals(scale>0,NumericMoveRule.fits(value,2,0));
        }
    }
    @Test void signedRemainderAndTruncationAgreeWithDecimalArithmetic() {
        for(String value:new String[]{"-123.456","123.456","0.001","-0.001","999999","0"})
            for(int scale:new int[]{-2,0,2,5})for(boolean signed:new boolean[]{false,true}) {
                var number=new BigDecimal(value);var adjusted=signed?number:number.abs();
                var expected=adjusted.setScale(scale,RoundingMode.DOWN).remainder(BigDecimal.ONE.scaleByPowerOfTen(3-scale));
                assertEquals(0,expected.compareTo(NumericMoveRule.fit(number,3,scale,signed,"DISPLAY")));
            }
    }
}
