package io.github.gustavo2358.cobolexplorer;
import java.math.*;
/** Small test interpreter for grammar descriptors, checked against handwritten IBM examples. */
final class NumericEditRule {
    static String format(BigDecimal value,Ast.NumericEdit pattern) {
        BigInteger coefficient=value.movePointRight(pattern.scale()).toBigInteger().abs().remainder(BigInteger.TEN.pow(pattern.digits()));
        String digits=coefficient.toString();digits="0".repeat(pattern.digits()-digits.length())+digits;
        boolean allOptional=pattern.parts().stream().noneMatch(p->p.kind()==Ast.EditKind.DIGITS);
        boolean star=pattern.parts().stream().anyMatch(p->p.kind()==Ast.EditKind.SUPPRESS_STAR);
        boolean allZero=coefficient.signum()==0&&allOptional;
        StringBuilder result=new StringBuilder();boolean suppress=true,started=false;int at=0,floatAt=-1;String sign=null;
        for(var part:pattern.parts())for(int i=0;i<part.count();i++) {
            switch(part.kind()) {
                case DIGITS,SUPPRESS_SPACE,SUPPRESS_STAR -> {
                    char digit=digits.charAt(at++);started=true;
                    if(part.kind()==Ast.EditKind.DIGITS||digit!='0')suppress=false;
                    if(!suppress&&floatAt>=0){result.setCharAt(result.length()-1,sign.charAt(0));floatAt=-1;}
                    result.append(suppress?(star?'*':' '):digit);
                }
                case RADIX -> {
                    if(floatAt>=0){result.setCharAt(result.length()-1,sign.charAt(0));floatAt=-1;}
                    suppress=false;result.append(part.text());
                }
                case SIGN -> result.append(value.signum()<0?part.negative():part.text());
                case FLOAT_SIGN -> {floatAt=result.length();sign=value.signum()<0?part.negative():part.text();result.append(' ');started=true;}
                case INSERT -> result.append(started&&suppress?(star?"*":" ").repeat(part.text().length()):part.text());
            }
        }
        if(allZero){int index=0;for(var part:pattern.parts())for(int i=0;i<part.count();i++) {
            int size=part.text().isEmpty()?1:part.text().length();
            for(int n=0;n<size;n++)if(!star||part.kind()!=Ast.EditKind.RADIX)result.setCharAt(index+n,star?'*':' ');
            index+=size;
        }}
        return result.toString();
    }
}
