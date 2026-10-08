package com.imd.cobolexplorer;

import java.util.*;

/** Closed embedded Db2 syntax. Parses source once upstream; never evaluates SQL or database state. */
final class SqlCommandSyntax {
    enum Kind { SELECT_INTO, UPDATE, INSERT, DELETE, OPEN, FETCH, CLOSE, DECLARE_CURSOR, PREPARE, EXECUTE, EXECUTE_IMMEDIATE, WHENEVER }
    record Host(String operand,int start,Ast.EmbeddedHostRole role) { }
    enum Condition { SQLERROR, SQLWARNING, NOT_FOUND }
    record Label(String syntax,int start) { }
    record Directive(Condition condition,Optional<Label> target) { }
    record Command(Kind kind,List<Host> hosts,Optional<Directive> directive) {
        Command {hosts=List.copyOf(hosts);Objects.requireNonNull(directive);}
        boolean declaration(){return kind==Kind.DECLARE_CURSOR||kind==Kind.WHENEVER;}
    }
    static Optional<Command> parse(String raw) {
        try {return Optional.of(new Parser(raw).command());}catch(Unproved e){return Optional.empty();}
    }
    private static final class Unproved extends RuntimeException {Unproved(){super(null,null,false,false);}}
    private enum TokenKind { WORD, QUOTED_NAME, STRING, NUMBER, SYMBOL }
    private record Token(String text,int start,int end,TokenKind kind) { }
    private static final Set<String> RESERVED=Set.of("EXEC","SQL","END-EXEC","SELECT","INTO","FROM","WHERE","SET","VALUES","UPDATE","INSERT","DELETE","OPEN","FETCH","CLOSE","DECLARE","CURSOR","FOR","ORDER","BY","ASC","DESC","FIRST","ROWS","ROW","ONLY","AND","OR","NOT","LIKE","IS","NULL","CURRENT","DATE","TIME","TIMESTAMP","WITH","HOLD","USING","NEXT","DISTINCT","ALL","GROUP","HAVING","UNION","JOIN","WHENEVER");
    private static List<Token> lex(String raw) {
        var out=new ArrayList<Token>();int i=0;while(i<raw.length()&&Character.isWhitespace(raw.charAt(i)))i++;
        if(raw.regionMatches(true,i,"*>EXECSQL",0,9))i+=9;
        while(i<raw.length()) {
            char c=raw.charAt(i);if(Character.isWhitespace(c)){i++;continue;}int start=i;
            if(raw.startsWith("--",i)||raw.startsWith("*>",i)){while(i<raw.length()&&raw.charAt(i)!='\n'&&raw.charAt(i)!='\r')i++;continue;}
            if(raw.startsWith("/*",i)){int end=raw.indexOf("*/",i+2);if(end<0)throw new Unproved();i=end+2;continue;}
            if(c=='\''||c=='"') {
                char quote=c;i++;boolean closed=false;
                while(i<raw.length()){if(raw.charAt(i++)==quote){if(i<raw.length()&&raw.charAt(i)==quote)i++;else {closed=true;break;}}}
                if(!closed)throw new Unproved();out.add(new Token(raw.substring(start,i),start,i,quote=='\''?TokenKind.STRING:TokenKind.QUOTED_NAME));continue;
            }
            if(Character.isLetter(c)||"_@#$".indexOf(c)>=0) {
                while(i<raw.length()&&(Character.isLetterOrDigit(raw.charAt(i))||"_@#$-".indexOf(raw.charAt(i))>=0))i++;
                out.add(new Token(raw.substring(start,i).toUpperCase(Locale.ROOT),start,i,TokenKind.WORD));continue;
            }
            if(c>='0'&&c<='9') {
                while(i<raw.length()&&Character.isDigit(raw.charAt(i)))i++;
                if(i+1<raw.length()&&raw.charAt(i)=='.'&&Character.isDigit(raw.charAt(i+1))){i++;while(i<raw.length()&&Character.isDigit(raw.charAt(i)))i++;}
                out.add(new Token(raw.substring(start,i),start,i,TokenKind.NUMBER));continue;
            }
            if(".,:()+-*/=<>".indexOf(c)<0)throw new Unproved();i++;
            if(i<raw.length()&&(c=='<'&&(raw.charAt(i)=='='||raw.charAt(i)=='>')||c=='>'&&raw.charAt(i)=='='))i++;
            out.add(new Token(raw.substring(start,i),start,i,TokenKind.SYMBOL));
        }
        return List.copyOf(out);
    }
    private static final class Parser {
        final String raw;final List<Token> tokens;final List<Host> hosts=new ArrayList<>();int at,depth;
        Parser(String raw){this.raw=raw;tokens=lex(raw);}
        Command command() {
            need("EXEC");need("SQL");Kind kind;Optional<Directive> directive=Optional.empty();
            if(take("SELECT")){query(true);kind=Kind.SELECT_INTO;}
            else if(take("UPDATE")){qualified();need("SET");do {qualified();need("=");value();}while(take(","));where();kind=Kind.UPDATE;}
            else if(take("INSERT")) {
                need("INTO");qualified();int columns=0;
                if(take("(")){do {identifier(false);columns++;}while(take(","));need(")");}
                need("VALUES");need("(");int values=0;do {value();values++;}while(take(","));need(")");
                if(columns!=0&&columns!=values)throw new Unproved();kind=Kind.INSERT;
            } else if(take("DELETE")){need("FROM");qualified();where();kind=Kind.DELETE;}
            else if(take("OPEN")){identifier(true);if(take("USING"))targets(Ast.EmbeddedHostRole.READ);kind=Kind.OPEN;}
            else if(take("CLOSE")){identifier(true);kind=Kind.CLOSE;}
            else if(take("FETCH")){take("NEXT");take("FROM");identifier(true);need("INTO");targets(Ast.EmbeddedHostRole.WRITE);kind=Kind.FETCH;}
            else if(take("DECLARE")){identifier(true);need("CURSOR");if(take("WITH"))need("HOLD");need("FOR");need("SELECT");query(false);kind=Kind.DECLARE_CURSOR;}
            else if(take("PREPARE")){identifier(true);need("FROM");host(Ast.EmbeddedHostRole.READ);kind=Kind.PREPARE;}
            else if(take("EXECUTE")) {
                if(take("IMMEDIATE")){if(peek(":"))host(Ast.EmbeddedHostRole.READ);else if(next().kind()!=TokenKind.STRING)throw new Unproved();kind=Kind.EXECUTE_IMMEDIATE;}
                else {identifier(true);if(take("USING"))targets(Ast.EmbeddedHostRole.READ);kind=Kind.EXECUTE;}
            } else if(take("WHENEVER")) {
                Condition condition;
                if(take("SQLERROR"))condition=Condition.SQLERROR;
                else if(take("SQLWARNING"))condition=Condition.SQLWARNING;
                else {need("NOT");need("FOUND");condition=Condition.NOT_FOUND;}
                Optional<Label> target=Optional.empty();
                if(!take("CONTINUE")) {
                    if(!take("GOTO")){need("GO");need("TO");}
                    int start=at;identifier(true);
                    if(take("OF")||take("IN"))identifier(true);
                    var first=tokens.get(start);String syntax=raw.substring(first.start(),tokens.get(at-1).end());
                    if(DirectParseScope.active()?DirectEmbeddedProcedureSyntax.parse(raw,syntax,first.start(),0,1,0,0).isEmpty():EmbeddedProcedureSyntax.parse(raw,syntax,first.start(),0,1,0,0).isEmpty())throw new Unproved();
                    target=Optional.of(new Label(syntax,first.start()));
                }
                directive=Optional.of(new Directive(condition,target));kind=Kind.WHENEVER;
            } else throw new Unproved();
            need("END-EXEC");take(".");if(at!=tokens.size())throw new Unproved();return new Command(kind,hosts,directive);
        }
        void query(boolean into) {
            int selected=0;do {value();selected++;}while(take(","));
            if(into){need("INTO");if(targets(Ast.EmbeddedHostRole.WRITE)!=selected)throw new Unproved();}
            need("FROM");qualified();where();
            if(take("ORDER")){need("BY");do {qualified();if(!take("ASC"))take("DESC");}while(take(","));}
            if(take("FETCH")){need("FIRST");var n=next();if(n.kind()!=TokenKind.NUMBER||!n.text().chars().allMatch(Character::isDigit)||n.text().chars().allMatch(c->c=='0'))throw new Unproved();if(!take("ROW"))need("ROWS");need("ONLY");}
        }
        void where(){if(take("WHERE")&&!expression(0))throw new Unproved();}
        void value(){if(expression(4))throw new Unproved();}
        // Boolean result marks a predicate; WHERE cannot be a bare scalar/function.
        boolean expression(int min) {
            if(++depth>256)throw new Unproved();boolean predicate;
            if(take("NOT")){if(!expression(3))throw new Unproved();predicate=true;}
            else if(take("+")||take("-")){value();predicate=false;}
            else if(take("(")){predicate=expression(0);need(")");}
            else if(peek(":")){host(Ast.EmbeddedHostRole.READ);predicate=false;}
            else if(take("CURRENT")){if(!take("DATE")&&!take("TIME")&&!take("TIMESTAMP"))throw new Unproved();predicate=false;}
            else if(take("NULL")){predicate=false;}
            else {
                var token=next();predicate=false;
                if(token.kind()!=TokenKind.STRING&&token.kind()!=TokenKind.NUMBER) {
                    checkIdentifier(token,false);while(take("."))identifier(false);
                    if(take("(")){if(!take("*")){value();while(take(","))value();}need(")");}
                }
            }
            while(at<tokens.size()) {
                String op=tokens.get(at).text();int precedence=switch(op){case "OR"->1;case "AND"->2;case "=","<>","<",">","<=",">=","LIKE","IS"->3;case "+","-"->4;case "*","/"->5;default->-1;};
                if(precedence<min||tokens.get(at).kind()==TokenKind.STRING||tokens.get(at).kind()==TokenKind.QUOTED_NAME)break;at++;
                if(op.equals("IS")){if(predicate)throw new Unproved();take("NOT");need("NULL");predicate=true;continue;}
                boolean right=expression(precedence+1);
                if(precedence<=2){if(!predicate||!right)throw new Unproved();predicate=true;}
                else {if(predicate||right)throw new Unproved();predicate=precedence==3;}
            }
            depth--;return predicate;
        }
        int targets(Ast.EmbeddedHostRole role) {
            int count=0;do {host(role);count++;if(peek(":"))host(role);}while(take(","));return count;
        }
        void host(Ast.EmbeddedHostRole role) {
            need(":");var t=next();checkIdentifier(t,true);String operand=raw.substring(t.start(),t.end());
            if(!CicsHostSyntax.supportsReference(operand,raw,t.start(),0,1,0,0))throw new Unproved();hosts.add(new Host(operand,t.start(),role));
        }
        void qualified(){identifier(false);if(take("."))identifier(false);}
        void identifier(boolean host){checkIdentifier(next(),host);}
        void checkIdentifier(Token t,boolean host){if(t.kind()==TokenKind.QUOTED_NAME&&!host)return;if(t.kind()!=TokenKind.WORD||RESERVED.contains(t.text())||!host&&t.text().indexOf('-')>=0)throw new Unproved();}
        Token next(){if(at>=tokens.size())throw new Unproved();return tokens.get(at++);}
        boolean peek(String text){return at<tokens.size()&&tokens.get(at).kind()!=TokenKind.STRING&&tokens.get(at).kind()!=TokenKind.QUOTED_NAME&&tokens.get(at).text().equals(text);}
        boolean take(String text){if(!peek(text))return false;at++;return true;}
        void need(String text){if(!take(text))throw new Unproved();}
    }
}
