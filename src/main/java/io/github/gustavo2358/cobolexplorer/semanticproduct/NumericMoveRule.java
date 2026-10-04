package io.github.gustavo2358.cobolexplorer.semanticproduct;
import java.math.*;
/** Elementary unedited numeric receiving rule. Bounds are fixed by the proved descriptor. */
public final class NumericMoveRule {
    private NumericMoveRule() { }
    public static java.util.Optional<BigDecimal> textNumber(String text) {
        if(text.isEmpty())return java.util.Optional.empty();
        for(int i=0;i<text.length();i++)if(text.charAt(i)<'0'||text.charAt(i)>'9')return java.util.Optional.empty();
        return java.util.Optional.of(new BigDecimal(text));
    }
    public static boolean integerText(String numeric,String text) {
        var parsed=textNumber(text);if(parsed.isEmpty())return false;
        try {var value=new BigDecimal(numeric);return value.scale()==0&&value.abs().compareTo(parsed.orElseThrow())==0;}
        catch(NumberFormatException ex){return false;}
    }
    public static BigDecimal fit(BigDecimal value,int digits,int scale,boolean signed,String representation) {
        return fit(value,digits,scale,signed,representation,"UNSPECIFIED");
    }
    public static boolean requiresFit(String representation,String trunc) {
        return representation.equals("BINARY")&&(trunc.equals("UNSPECIFIED")||trunc.equals("OPT"));
    }
    public static BigDecimal fit(BigDecimal value,int digits,int scale,boolean signed,String representation,String trunc) {
        if(digits<1||digits>31||scale< -31||scale>31)throw new IllegalArgumentException("numeric descriptor bound");
        boolean nativeBinary=representation.equals("NATIVE_BINARY")||representation.equals("BINARY")&&trunc.equals("BIN");
        int bits=digits<=4?16:digits<=9?32:64;
        var modulus=nativeBinary?BigInteger.ONE.shiftLeft(bits):BigInteger.TEN.pow(digits);
        var coefficient=coefficientRemainder(value,scale,modulus);
        if(!signed)coefficient=coefficient.abs();
        if(nativeBinary) {
            coefficient=coefficient.mod(modulus);
            if(signed&&coefficient.testBit(bits-1))coefficient=coefficient.subtract(modulus);
        }
        return new BigDecimal(coefficient,scale);
    }
    /** Exponents are descriptors, not a request to allocate their expanded zeros. */
    private static BigInteger coefficientRemainder(BigDecimal value,int scale,BigInteger modulus) {
        long shift=(long)scale-value.scale();
        var coefficient=value.unscaledValue();
        if(shift>=0)return coefficient.remainder(modulus)
            .multiply(BigInteger.TEN.modPow(BigInteger.valueOf(shift),modulus)).remainder(modulus);
        long dropped=-shift;
        return dropped>=value.precision()?BigInteger.ZERO:
            coefficient.divide(BigInteger.TEN.pow((int)dropped)).remainder(modulus);
    }
    public static boolean fits(BigDecimal value,int digits,int scale) {
        if(value.signum()==0)return true;
        long receivedDigits=(long)value.precision()+scale-value.scale();
        // An integer coefficient with at most d decimal digits is strictly below 10^d.
        return receivedDigits<=digits;
    }
    public static BigDecimal maximum(int digits,int scale,boolean signed,String representation) {
        BigInteger coefficient;
        if(representation.equals("BINARY")||representation.equals("NATIVE_BINARY")) {
            int width=digits<=4?16:digits<=9?32:64;
            coefficient=BigInteger.ONE.shiftLeft(width-(signed?1:0));
            if(!signed)coefficient=coefficient.subtract(BigInteger.ONE);
        } else coefficient=BigInteger.TEN.pow(digits).subtract(BigInteger.ONE);
        return new BigDecimal(coefficient,scale);
    }

}
