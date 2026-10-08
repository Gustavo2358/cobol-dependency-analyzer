package com.imd.cobolexplorer;
import java.util.*;
/** Grammar-owned numeric editing descriptor. One pass over tokens and one over compressed runs. */
final class NumericEditSyntax {
    private record Run(String symbol,int count) { }
    static Optional<Ast.NumericEdit> parse(String spelling,boolean decimalComma,boolean defaultCurrency) {
        var runs=new ArrayList<Run>();long extent=0;var totals=new HashMap<String,Long>();boolean edited=false;
        for(int i=0;i<spelling.length();) {
            String symbol=String.valueOf(Character.toUpperCase(spelling.charAt(i++)));
            if((symbol.equals("C")||symbol.equals("D"))&&i<spelling.length())symbol+=Character.toUpperCase(spelling.charAt(i++));
            if(!Set.of("9","Z","*","B","0","/",",",".","+","-","$","CR","DB").contains(symbol)||symbol.equals("$")&&!defaultCurrency)return Optional.empty();
            long count=1;
            if(i<spelling.length()&&spelling.charAt(i)=='(') {
                i++;count=0;int first=i;
                while(i<spelling.length()&&spelling.charAt(i)>='0'&&spelling.charAt(i)<='9') {
                    count=count*10+spelling.charAt(i++)-'0';if(count>Integer.MAX_VALUE)return Optional.empty();
                }
                if(i==first||count==0||i>=spelling.length()||spelling.charAt(i++)!=')')return Optional.empty();
            }
            totals.merge(symbol,count,Long::sum);extent+=count*symbol.length();
            if(extent>Integer.MAX_VALUE)return Optional.empty();
            edited|=!symbol.equals("9");runs.add(new Run(symbol,(int)count));
        }
        if(!edited)return Optional.empty();
        String floating=null;for(var symbol:List.of("+","-","$"))if(totals.getOrDefault(symbol,0L)>1) {
            if(floating!=null)return Optional.empty();floating=symbol;
        }
        boolean z=totals.containsKey("Z"),star=totals.containsKey("*");
        if(z&&star||floating!=null&&(z||star))return Optional.empty();
        String radix=decimalComma?",":".";boolean point=false,reserved=false,mandatory=false;long digits=0,scale=0;
        var parts=new ArrayList<Ast.EditPart>();
        for(var r:runs) {
            String symbol=r.symbol();int count=r.count();Ast.EditKind kind;String text="",negative="";
            boolean optional=symbol.equals("Z")||symbol.equals("*")||symbol.equals(floating);
            if(symbol.equals("9")||optional) {
                if(optional&&mandatory)return Optional.empty();
                if(symbol.equals(floating)&&!reserved) {
                    if(point)return Optional.empty();reserved=true;
                    parts.add(new Ast.EditPart(Ast.EditKind.FLOAT_SIGN,1,symbol.equals("-")?" ":symbol,symbol.equals("$")?"$":"-"));count--;
                }
                if(count==0)continue;
                kind=symbol.equals("9")?Ast.EditKind.DIGITS:star?Ast.EditKind.SUPPRESS_STAR:Ast.EditKind.SUPPRESS_SPACE;
                digits+=count;if(point)scale+=count;mandatory|=symbol.equals("9");
            } else if(symbol.equals(radix)) {
                if(point||count!=1)return Optional.empty();point=true;kind=Ast.EditKind.RADIX;text=symbol;
            } else if(Set.of("+","-","CR","DB").contains(symbol)) {
                if(count!=1)return Optional.empty();kind=Ast.EditKind.SIGN;text=symbol.equals("+")?"+":" ".repeat(symbol.length());negative=symbol.equals("+")?"-":symbol;
            } else {kind=Ast.EditKind.INSERT;text=symbol.equals("B")?" ":symbol;}
            if(!parts.isEmpty()&&parts.get(parts.size()-1).kind()==kind&&parts.get(parts.size()-1).text().equals(text)&&parts.get(parts.size()-1).negative().equals(negative)) {
                var previous=parts.remove(parts.size()-1);count+=previous.count();
            }
            parts.add(new Ast.EditPart(kind,count,text,negative));
        }
        if(digits<=0||digits>31)return Optional.empty();
        return Optional.of(new Ast.NumericEdit(parts,(int)digits,(int)scale,(int)extent));
    }
}
