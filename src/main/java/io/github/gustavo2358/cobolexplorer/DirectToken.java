package io.github.gustavo2358.cobolexplorer;

/** Immutable boundary value; recognition and AST actions never need lexer runtime objects. */
record DirectToken(int type,String text,String name,int line,int column,int index,int start,int stop) {
    static final int EOF=-1;
    int getType(){return type;} String getText(){return text;}
    int getLine(){return line;} int getCharPositionInLine(){return column;}
    int getTokenIndex(){return index;} int getStartIndex(){return start;} int getStopIndex(){return stop;}
}
