package io.github.gustavo2358.cobolexplorer;

import java.util.*;

/** Closed Db2 DECLARE TABLE syntax, which describes schema without inserting COBOL storage. */
final class SqlDeclarationSyntax {
    private SqlDeclarationSyntax() { }
    static boolean nonallocating(Ast.DataEntry entry) {
        if(!entry.level().equals("SQL"))return false;
        if(SqlCommandSyntax.parse(entry.declaration()).filter(SqlCommandSyntax.Command::declaration).isPresent())return true;
        try {return new Parser(entry.declaration()).table();}catch(Unproved ignored){return false;}
    }
    private static final class Unproved extends RuntimeException { Unproved(){super(null,null,false,false);} }
    private record Token(String text,boolean quoted) { }
    private static final class Parser {
        final List<Token> tokens=new ArrayList<>();int index;
        Parser(String text) {
            int start=text.stripLeading().startsWith("*>EXECSQL")?text.indexOf("*>EXECSQL")+9:0;
            for(int i=start;i<text.length();) {
                char c=text.charAt(i);if(Character.isWhitespace(c)){i++;continue;}
                if(c=='"') {
                    int from=++i;var name=new StringBuilder();boolean closed=false;
                    while(i<text.length()) {char x=text.charAt(i++);if(x=='"'){if(i<text.length()&&text.charAt(i)=='"'){name.append(x);i++;}else{closed=true;break;}}else name.append(x);}
                    if(!closed||name.isEmpty())throw new Unproved();tokens.add(new Token(name.toString(),true));continue;
                }
                if(Character.isLetterOrDigit(c)||c=='_'||c=='@'||c=='#'||c=='$') {
                    int from=i++;while(i<text.length()&&(Character.isLetterOrDigit(text.charAt(i))||"_@#$-".indexOf(text.charAt(i))>=0))i++;
                    tokens.add(new Token(text.substring(from,i).toUpperCase(Locale.ROOT),false));continue;
                }
                if("().,".indexOf(c)<0)throw new Unproved();tokens.add(new Token(String.valueOf(c),false));i++;
            }
        }
        boolean table() {
            need("EXEC");need("SQL");need("DECLARE");identifier();if(take("."))identifier();need("TABLE");need("(");
            do {identifier();type();if(take("NOT"))need("NULL");}while(take(","));
            need(")");need("END-EXEC");take(".");return index==tokens.size();
        }
        void type() {
            String type=next().text();
            if(Set.of("CHAR","CHARACTER","VARCHAR","GRAPHIC","VARGRAPHIC","BINARY","VARBINARY").contains(type)) {
                if(type.equals("CHARACTER"))take("VARYING");need("(");positive();need(")");return;
            }
            if(Set.of("DECIMAL","DEC","NUMERIC","NUM","FLOAT").contains(type)) {
                if(take("(")){positive();if(!type.equals("FLOAT")&&take(","))number();need(")");}return;
            }
            if(type.equals("DOUBLE")){take("PRECISION");return;}
            if(type.equals("TIMESTAMP")){if(take("(")){number();need(")");}return;}
            if(!Set.of("SMALLINT","INTEGER","INT","BIGINT","REAL","DATE","TIME").contains(type))throw new Unproved();
        }
        void identifier() {var t=next();if(t.quoted())return;String n=t.text();if(n.isEmpty()||!Character.isLetter(n.charAt(0))&&"_@#$".indexOf(n.charAt(0))<0||Set.of("EXEC","SQL","DECLARE","TABLE","GLOBAL","TEMPORARY","END-EXEC","NOT","NULL").contains(n)||n.indexOf('-')>=0)throw new Unproved();}
        void positive(){String n=number();if(n.chars().allMatch(c->c=='0'))throw new Unproved();}
        String number(){var t=next();if(t.quoted()||t.text().isEmpty()||!t.text().chars().allMatch(Character::isDigit))throw new Unproved();return t.text();}
        Token next(){if(index>=tokens.size())throw new Unproved();return tokens.get(index++);}
        boolean take(String s){if(index<tokens.size()&&!tokens.get(index).quoted()&&tokens.get(index).text().equals(s)){index++;return true;}return false;}
        void need(String s){if(!take(s))throw new Unproved();}
    }
}
