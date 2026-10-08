package com.imd.cobolexplorer;
import java.util.*;
final class DirectEmbeddedProcedureSyntax {
    static int rule(String name){for(int i=0;i<DirectGrammar.NAMES.length;i++)if(DirectGrammar.NAMES[i].equals(name))return i;throw new IllegalArgumentException(name);}
    static DirectFrame parseRule(String text,String name){return (DirectFrame)new DirectRecognizer(DirectLexerBoundary.lex(text)).parse(rule(name)).view(0);}
    static void anchor(DirectFrame frame,String raw,int begin,int offset,int line,int column,int anchorToken){
        var tokens=frame.ledger.tokens;
        for(int j=0;j<tokens.length;j++){
            var t=tokens[j];int l=line,c=column;
            for(int i=0;i<begin+Math.max(t.start(),0);i++){if(raw.charAt(i)=='\n'){l++;c=0;}else c++;}
            tokens[j]=new DirectToken(t.type(),t.text(),t.name(),l,c,anchorToken,offset+begin+t.start(),offset+begin+t.stop());
        }
    }
    static Optional<DirectSyntax.ProcedureNameFrame> parse(String raw,String syntax,int begin,int offset,int line,int column,int token){
        try {var f=(DirectSyntax.ProcedureNameFrame)parseRule(syntax,"procedureName");anchor(f,raw,begin,offset,line,column,token);return Optional.of(f);}
        catch(DirectRecognizer.Unsupported e){return Optional.empty();}
    }
    static Optional<DirectSyntax.ProcedureNameFrame> handlerLabel(String raw,int offset,int line,int column,int token){
        var op=CicsHandlerSyntax.parse(raw);
        if(op.isEmpty()||op.get().targetKind()!=CicsHandlerSyntax.TargetKind.LABEL)return Optional.empty();
        var option=op.get().options().stream().filter(o->o.name().equals("LABEL")).findFirst().orElseThrow();
        return conditionLabel(raw,option,offset,line,column,token);
    }
    static Optional<DirectSyntax.ProcedureNameFrame> conditionLabel(String raw,CicsCommandSyntax.Option option,int offset,int line,int column,int token){
        return option.operand().flatMap(s->parse(raw,s,option.operandStart(),offset,line,column,token));
    }
}
