package io.github.gustavo2358.cobolexplorer;

import io.github.gustavo2358.cobolexplorer.antlr.CobolLexer;
import org.antlr.v4.runtime.*;
import java.util.*;

/** The existing lexer is deliberately retained, isolated from native recognition/actions. */
final class DirectLexerBoundary {
    static DirectToken[] copy(CommonTokenStream stream) {
        return stream.getTokens().stream().filter(t->t.getChannel()==Token.DEFAULT_CHANNEL||t.getType()==Token.EOF)
            .map(t->new DirectToken(t.getType(),t.getText(),t.getType()==Token.EOF?"EOF":CobolLexer.VOCABULARY.getSymbolicName(t.getType()),t.getLine(),t.getCharPositionInLine(),t.getTokenIndex(),t.getStartIndex(),t.getStopIndex())).toArray(DirectToken[]::new);
    }
    static DirectToken[] lex(String source){
        var lexer=new CobolLexer(CharStreams.fromString(source));lexer.removeErrorListeners();
        lexer.addErrorListener(new BaseErrorListener(){@Override public void syntaxError(Recognizer<?,?> r,Object s,int l,int c,String m,RecognitionException e){throw new DirectRecognizer.Unsupported("embedded lexer: "+m);}});
        var tokens=new CommonTokenStream(lexer);tokens.fill();return copy(tokens);
    }
}
