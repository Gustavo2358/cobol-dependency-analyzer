package com.imd.cobolexplorer;

import java.util.*;

/** Independent, memoized recognition over packed lexer tokens. No runtime prediction engine.
 * Decisions prefer the longest complete alternative, with grammar order breaking ties.
 * Only the chosen derivation is retained in the flat provenance ledger.
 */
final class DirectRecognizer {
    private final DirectToken[] tokens;
    private final Memo memo = new Memo();
    private int farthest;private long work;
    DirectRecognizer(DirectToken[] tokens) { this.tokens=tokens; }
    DirectLedger parse(int rule) {
        int end=match(DirectGrammar.ROOTS[rule],0);
        if(end<0 || end<tokens.length && tokens[end].getType()!=-1)
            throw new Unsupported("incomplete "+DirectGrammar.NAMES[rule]+" at "+token(Math.max(farthest,Math.max(0,end))));
        var ledger=new DirectLedger(tokens);
        emitRule(rule,0,-1,ledger);
        ledger.seal();
        return ledger;
    }
    static final class Unsupported extends RuntimeException {
        Unsupported(String message){super(message,null,false,false);}
    }
    private DirectToken token(int p){return tokens[Math.min(p,tokens.length-1)];}
    private int match(int n,int p) {
        if(++work>1_000_000L+tokens.length*400L)throw new Unsupported("recognition work budget exceeded");
        long key=((long)p<<32)|(n+1L);
        int cached=memo.get(key);if(cached!=Integer.MIN_VALUE)return cached;
        int[] code=DirectGrammar.CODE[n];int kind=code[0],end=-1;
        if(!DirectGrammar.NULLABLE[n]&&!DirectGrammar.starts(n,token(p).getType()))return -1;
        // paragraph allows an omitted period and an empty body. Reserve a complete
        // section header for procedureSection before paragraph* consumes its name.
        // The grammar header only looks ahead through name, SECTION and segment number.
        if(n==DirectGrammar.PARAGRAPH){
            int header=match(DirectGrammar.SECTION_HEADER,p);
            if(header>p&&token(header).type()==DirectSyntax.DOT_FS){memo.put(key,-1);return -1;}
        }
        // An INSPECT counter followed by FOR belongs to the next inspectFor,
        // rather than the repeated inspectAllLeading operand of the previous counter.
        if(n==DirectGrammar.INSPECT_ITEM){int ref=match(DirectGrammar.IDENTIFIER,p);if(ref>=0&&token(ref).type()==DirectSyntax.FOR){memo.put(key,-1);return -1;}}
        if(n==DirectGrammar.ARGUMENT){int shortName=argumentNameEnd(p);if(shortName>=0){memo.put(key,shortName);return shortName;}}
        switch(kind) {
            case 0 -> end=p;
            case 8 -> end=p<tokens.length?p+1:-1;
            case 1 -> {if(p<tokens.length&&tokens[p].getType()==code[1])end=p+1;}
            case 2 -> end=match(DirectGrammar.ROOTS[code[1]],p);
            case 3 -> {end=p;for(int i=1;i<code.length&&end>=0;i++)end=match(code[i],end);}
            case 4 -> {for(int i=1;i<code.length;i++){
                int candidate=match(code[i],p);end=Math.max(end,candidate);
                if(n==DirectGrammar.QUALIFIER&&candidate>=0&&token(candidate).type()!=DirectSyntax.LPARENCHAR){end=candidate;break;}
                // Function arguments are a sequence with optional commas. Prefer the
                // first complete grammar alternative when another argument can follow.
                if(n==DirectGrammar.ARGUMENT&&candidate>=0&&(token(candidate).type()==DirectSyntax.RPARENCHAR||token(candidate).type()==DirectSyntax.COMMACHAR||token(candidate).type()==DirectSyntax.LEADING||token(candidate).type()==DirectSyntax.TRAILING||DirectGrammar.starts(n,token(candidate).type()))) {end=candidate;break;}
            }}
            case 5 -> end=Math.max(p,match(code[1],p));
            case 6,7 -> {
                end=p;int next;
                while((next=match(code[1],end))>end)end=next;
                if(kind==7&&end==p)end=-1;
            }
            default -> throw new AssertionError(kind);
        }
        farthest=Math.max(farthest,Math.max(p,end));memo.put(key,end);return end;
    }
    private int argumentNameEnd(int p){
        int name=match(DirectGrammar.QUALIFIED,p);
        if(name<0||token(name).type()!=DirectSyntax.LPARENCHAR)return -1;
        // Optional commas make a parenthesized arithmetic expression a separate
        // argument. Grammar order chooses qualifiedDataName before tableCall.
        int following=match(DirectGrammar.ARITHMETIC,name);
        return following>name?name:-1;
    }
    private int emitRule(int rule,int p,int parent,DirectLedger out) {
        int end=match(DirectGrammar.ROOTS[rule],p);
        int id=out.add(rule,p,Math.max(p-1,end-1),parent);
        emit(DirectGrammar.ROOTS[rule],p,id,out);out.close(id);return end;
    }
    private int emit(int n,int p,int parent,DirectLedger out) {
        int[] code=DirectGrammar.CODE[n];int end=match(n,p);
        if(n==DirectGrammar.ARGUMENT&&argumentNameEnd(p)==end){
            int id=out.add(DirectGrammar.IDENTIFIER_RULE,p,end-1,parent);
            emitRule(DirectGrammar.QUALIFIED_RULE,p,id,out);out.close(id);return end;
        }
        switch(code[0]) {
            case 1,8 -> {int id=out.add(-1,p,p,parent);out.close(id);}
            case 2 -> emitRule(code[1],p,parent,out);
            case 3 -> {int at=p;for(int i=1;i<code.length;i++)at=emit(code[i],at,parent,out);}
            case 4 -> {for(int i=1;i<code.length;i++)if(match(code[i],p)==end){emit(code[i],p,parent,out);break;}}
            case 5 -> {if(match(code[1],p)>=p)emit(code[1],p,parent,out);}
            case 6,7 -> {int at=p;while(at<end)at=emit(code[1],at,parent,out);}
            default -> { }
        }
        return end;
    }
    /** Primitive open addressing avoids a boxed object per speculative decision. */
    private static final class Memo {
        private long[] keys=new long[8192];private int[] values=new int[8192];private int size;
        private int slot(long k){long h=k*0x9E3779B97F4A7C15L;h^=h>>>32;int s=(int)h&(keys.length-1);while(keys[s]!=0&&keys[s]!=k)s=(s+1)&(keys.length-1);return s;}
        int get(long key){int s=slot(key);return keys[s]==0?Integer.MIN_VALUE:values[s];}
        void put(long key,int value){if(size*2>=keys.length)grow();int s=slot(key);if(keys[s]==0){keys[s]=key;size++;}values[s]=value;}
        private void grow(){if(keys.length>=1<<24)throw new Unsupported("recognition memory budget exceeded");long[] old=keys;int[] vals=values;keys=new long[old.length*2];values=new int[keys.length];size=0;for(int i=0;i<old.length;i++)if(old[i]!=0)put(old[i],vals[i]);}
    }
}
