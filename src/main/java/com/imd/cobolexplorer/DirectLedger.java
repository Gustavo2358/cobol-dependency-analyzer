package com.imd.cobolexplorer;

import java.util.*;

/** Flat successful-production spans, also the source of the existing syntax presentation.
 * No parent/children object graph is constructed by recognition.
 */
final class DirectLedger {
    final DirectToken[] tokens;
    int[] rules=new int[4096],starts=new int[4096],stops=new int[4096],parents=new int[4096],next=new int[4096],counts=new int[4096];
    int size;
    private DirectSpan[] views;
    DirectLedger(DirectToken[] tokens){this.tokens=tokens;}
    int add(int rule,int start,int stop,int parent){
        if(size==rules.length){int n=size*2;rules=Arrays.copyOf(rules,n);starts=Arrays.copyOf(starts,n);stops=Arrays.copyOf(stops,n);parents=Arrays.copyOf(parents,n);next=Arrays.copyOf(next,n);counts=Arrays.copyOf(counts,n);}
        int id=size++;rules[id]=rule;starts[id]=start;stops[id]=stop;parents[id]=parent;if(parent>=0)counts[parent]++;return id;
    }
    void close(int id){next[id]=size;}
    void seal(){views=new DirectSpan[size];}
    DirectSpan view(int i){if(views[i]==null)views[i]=rules[i]<0?new DirectTerminal(this,i):DirectSyntax.frame(this,i);return views[i];}
    DirectToken token(int p){return p<0?null:tokens[Math.min(p,tokens.length-1)];}
    List<ExplorerMain.Node> nodes(){
        var out=new ArrayList<ExplorerMain.Node>(size);int[] depths=new int[size];
        for(int i=0;i<size;i++){
            int parent=parents[i];int depth=parent<0?0:depths[parent]+1;depths[i]=depth;
            var first=token(starts[i]);var last=token(stops[i]);
            String name=rules[i]<0?first.name():DirectGrammar.NAMES[rules[i]];
            out.add(new ExplorerMain.Node(i,parent,rules[i]<0?"terminal":"rule",name,rules[i]<0?first.getText():"",first.getLine(),first.getCharPositionInLine(),last==null?first.getLine():last.getLine(),first.getTokenIndex(),last==null?-1:last.getTokenIndex(),depth,counts[i]));
        }
        return out;
    }
}
interface DirectSpan {
    int getChildCount(); DirectSpan getChild(int n); DirectSpan getParent(); String getText();
}
class DirectFrame implements DirectSpan {
    final DirectLedger ledger;final int id;private int[] childIndex;
    DirectFrame(DirectLedger ledger,int id){this.ledger=ledger;this.id=id;}
    int getRuleIndex(){return ledger.rules[id];}
    DirectToken getStart(){return ledger.token(ledger.starts[id]);}
    DirectToken getStop(){return ledger.token(ledger.stops[id]);}
    public int getChildCount(){return ledger.counts[id];}
    public DirectSpan getChild(int n){
        if(getChildCount()>8){
            if(childIndex==null){childIndex=new int[getChildCount()];int at=0;for(int i=id+1;i<ledger.next[id];i=ledger.next[i])childIndex[at++]=i;}
            return ledger.view(childIndex[n]);
        }
        for(int i=id+1;i<ledger.next[id];i=ledger.next[i])if(n--==0)return ledger.view(i);throw new IndexOutOfBoundsException();}
    public DirectFrame getParent(){int p=ledger.parents[id];return p<0?null:(DirectFrame)ledger.view(p);}
    public String getText(){var b=new StringBuilder();for(int i=ledger.starts[id];i<=ledger.stops[id];i++)b.append(ledger.token(i).getText());return b.toString();}
    <T extends DirectFrame> T child(Class<T> type,int n){for(int i=id+1;i<ledger.next[id];i=ledger.next[i])if(ledger.rules[i]>=0){var v=ledger.view(i);if(type.isInstance(v)&&n--==0)return type.cast(v);}return null;}
    <T extends DirectFrame> List<T> children(Class<T> type){var out=new ArrayList<T>();for(int i=id+1;i<ledger.next[id];i=ledger.next[i])if(ledger.rules[i]>=0){var v=ledger.view(i);if(type.isInstance(v))out.add(type.cast(v));}return out;}
    DirectTerminal getToken(int type,int n){for(int i=id+1;i<ledger.next[id];i=ledger.next[i])if(ledger.rules[i]<0&&ledger.token(ledger.starts[i]).getType()==type&&n--==0)return (DirectTerminal)ledger.view(i);return null;}
    List<DirectTerminal> getTokens(int type){var out=new ArrayList<DirectTerminal>();for(int i=id+1;i<ledger.next[id];i=ledger.next[i])if(ledger.rules[i]<0&&ledger.token(ledger.starts[i]).getType()==type)out.add((DirectTerminal)ledger.view(i));return out;}
}
final class DirectTerminal implements DirectSpan {
    final DirectLedger ledger;final int id;
    DirectTerminal(DirectLedger ledger,int id){this.ledger=ledger;this.id=id;}
    DirectToken getSymbol(){return ledger.token(ledger.starts[id]);}
    public String getText(){return getSymbol().getText();}
    public int getChildCount(){return 0;}
    public DirectSpan getChild(int n){throw new IndexOutOfBoundsException();}
    public DirectSpan getParent(){return ledger.view(ledger.parents[id]);}
}
