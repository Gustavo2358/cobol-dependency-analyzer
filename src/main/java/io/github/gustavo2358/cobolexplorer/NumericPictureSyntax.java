package io.github.gustavo2358.cobolexplorer;
import java.util.Optional;
/** Grammar-owned unedited fixed-point descriptor. Linear in spelling, never expands repetitions. */
final class NumericPictureSyntax {
    static Optional<Ast.NumericPicture> parse(String spelling) {
        long digits=0,scale=0; boolean signed=false,point=false,explicitPoint=false,seenDigit=false,leadingP=false,trailingP=false;
        int at=0;
        if(!spelling.isEmpty()&&Character.toUpperCase(spelling.charAt(0))=='S'){signed=true;at++;}
        while(at<spelling.length()) {
            char symbol=Character.toUpperCase(spelling.charAt(at++)); long count=1;
            if(at<spelling.length()&&spelling.charAt(at)=='(') {
                at++;count=0;int first=at;
                while(at<spelling.length()&&spelling.charAt(at)>='0'&&spelling.charAt(at)<='9') {
                    count=count*10+spelling.charAt(at++)-'0';if(count>Integer.MAX_VALUE)return Optional.empty();
                }
                if(at==first||count==0||at>=spelling.length()||spelling.charAt(at++)!=')')return Optional.empty();
            }
            if(symbol=='V') {
                if(explicitPoint||count!=1||leadingP||trailingP&&at<spelling.length())return Optional.empty();
                explicitPoint=true;if(!trailingP)point=true;
            }
            else if(symbol=='9') {
                if(trailingP)return Optional.empty();seenDigit=true;digits+=count;if(point||leadingP)scale+=count;
            } else if(symbol=='P') {
                if(!seenDigit){leadingP=true;point=true;scale+=count;}
                else {if(point||leadingP)return Optional.empty();trailingP=true;scale-=count;}
            } else return Optional.empty();
            if(digits>Integer.MAX_VALUE||scale>Integer.MAX_VALUE||scale<Integer.MIN_VALUE)return Optional.empty();
        }
        return digits>0?Optional.of(new Ast.NumericPicture((int)digits,(int)scale,signed)):Optional.empty();
    }
}
