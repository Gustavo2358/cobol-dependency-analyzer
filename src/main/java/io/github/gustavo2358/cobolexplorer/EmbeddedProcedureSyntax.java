package io.github.gustavo2358.cobolexplorer;
import io.github.gustavo2358.cobolexplorer.antlr.*;
import org.antlr.v4.runtime.*;
import java.util.*;
/** COBOL procedure syntax embedded in a command; resolution remains canonical. */
final class EmbeddedProcedureSyntax {
    static Optional<CobolParser.ProcedureNameContext> parse(String raw,String syntax,int begin,int offset,int line,int column,int anchorToken) {
        var lexer=new CobolLexer(CharStreams.fromString(syntax));lexer.removeErrorListeners();
        var failed=new boolean[1];lexer.addErrorListener(new BaseErrorListener(){@Override public void syntaxError(Recognizer<?,?> r,Object symbol,int l,int c,String msg,RecognitionException e){failed[0]=true;}});
        var tokens=new CommonTokenStream(lexer);tokens.fill();var parser=new CobolParser(tokens);
        parser.removeErrorListeners();parser.setErrorHandler(new BailErrorStrategy());
        CobolParser.ProcedureNameContext tree;
        try {tree=parser.procedureName();if(failed[0]||parser.getCurrentToken().getType()!=Token.EOF)return Optional.empty();}
        catch(org.antlr.v4.runtime.misc.ParseCancellationException e){return Optional.empty();}
        for(var token:tokens.getTokens())if(token instanceof CommonToken t) {
            t.setText(t.getText());int l=line,c=column;
            for(int i=0;i<begin+Math.max(t.getStartIndex(),0);i++){if(raw.charAt(i)=='\n'){l++;c=0;}else c++;}
            t.setLine(l);t.setCharPositionInLine(c);t.setStartIndex(offset+begin+t.getStartIndex());t.setStopIndex(offset+begin+t.getStopIndex());t.setTokenIndex(anchorToken);
        }
        return Optional.of(tree);
    }
}
