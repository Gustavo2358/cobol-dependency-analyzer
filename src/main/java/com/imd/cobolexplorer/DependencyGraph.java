package com.imd.cobolexplorer;

import java.util.*;
import static com.imd.cobolexplorer.DependencyRelevance.Point;

/** One indexed control graph, shared by construction and all later consumers.
 * Edge kinds distinguish ordinary result propagation from invocation delivery.
 * The reverse index references the same edges; it is not another relation. */
final class DependencyGraph {
    static final byte NEXT=0,CALL=1,ENTRY=2;
    private final Map<Point,Integer> ids=new HashMap<>();
    private final List<Point> points=new ArrayList<>();
    private final Map<String,Integer> bindingIds=new HashMap<>();
    private final List<String> bindings=new ArrayList<>();
    private int[] outgoing=new int[16],incoming=new int[16];
    private int[] sources=new int[16],targets=new int[16],outNext=new int[16],inNext=new int[16],binding=new int[16];
    private byte[] kinds=new byte[16];
    private int[] slots=new int[32];
    private int edges,closedSize;
    private int[] order,rank,component;
    private final BitSet cyclic=new BitSet();
    DependencyGraph(){Arrays.fill(outgoing,-1);Arrays.fill(incoming,-1);}
    int size(){return points.size();}
    Point point(int id){return points.get(id);}
    int find(Point point){return ids.getOrDefault(point,-1);}
    int id(Point point) {
        var old=ids.get(point);if(old!=null)return old;
        int id=size();ids.put(point,id);points.add(point);
        if(id==outgoing.length) {
            int n=outgoing.length*2;outgoing=Arrays.copyOf(outgoing,n);incoming=Arrays.copyOf(incoming,n);
            Arrays.fill(outgoing,id,n,-1);Arrays.fill(incoming,id,n,-1);
        }return id;
    }
    int outgoing(int point){return outgoing[point];}
    int incoming(int point){return incoming[point];}
    int outNext(int edge){return outNext[edge];}
    int inNext(int edge){return inNext[edge];}
    int source(int edge){return sources[edge];}
    int target(int edge){return targets[edge];}
    byte kind(int edge){return kinds[edge];}
    String binding(int edge){return bindings.get(binding[edge]);}
    int edgeCount(){return edges;}
    private int hash(int from,int to,byte kind,int binding) {
        int h=from*0x9e3779b9+to;h=Integer.rotateLeft(h,13)^binding*0x85ebca6b^kind;
        h^=h>>>16;h*=0x7feb352d;h^=h>>>15;return h;
    }
    /** Returns a new edge ID, or its complemented ID if already registered. */
    int add(int from,int to,byte kind,String name) {
        if(order!=null)throw new IllegalStateException("control graph is closed");
        int bind=bindingIds.computeIfAbsent(name,k->{int id=bindings.size();bindings.add(k);return id;});
        if((edges+1)*2>slots.length) {
            slots=new int[slots.length*2];for(int e=0;e<edges;e++)index(e);
        }
        int slot=hash(from,to,kind,bind)&(slots.length-1);
        while(slots[slot]!=0) {
            int e=slots[slot]-1;if(sources[e]==from&&targets[e]==to&&kinds[e]==kind&&binding[e]==bind)return ~e;
            slot=(slot+1)&(slots.length-1);
        }
        if(edges==sources.length) {
            int n=edges*2;sources=Arrays.copyOf(sources,n);targets=Arrays.copyOf(targets,n);
            outNext=Arrays.copyOf(outNext,n);inNext=Arrays.copyOf(inNext,n);binding=Arrays.copyOf(binding,n);kinds=Arrays.copyOf(kinds,n);
        }
        int e=edges++;sources[e]=from;targets[e]=to;kinds[e]=kind;binding[e]=bind;
        outNext[e]=outgoing[from];outgoing[from]=e;inNext[e]=incoming[to];incoming[to]=e;slots[slot]=e+1;return e;
    }
    private void index(int e) {
        int slot=hash(sources[e],targets[e],kinds[e],binding[e])&(slots.length-1);
        while(slots[slot]!=0)slot=(slot+1)&(slots.length-1);slots[slot]=e+1;
    }
    void close() {
        closedSize=size();slots=null;bindingIds.clear();
        order=new int[size()];rank=new int[size()];component=new int[size()];Arrays.fill(component,-1);
        var seen=new BitSet();var nodes=new Ints();var cursors=new Ints();int count=0;
        for(int root=0;root<size();root++)if(!seen.get(root)) {
            seen.set(root);nodes.add(root);cursors.add(outgoing(root));
            while(!nodes.isEmpty()) {
                int edge=cursors.last();
                if(edge<0){order[count++]=nodes.pop();cursors.pop();continue;}
                cursors.last(outNext(edge));int child=target(edge);
                if(!seen.get(child)){seen.set(child);nodes.add(child);cursors.add(outgoing(child));}
            }
        }
        for(int i=0;i<order.length;i++)rank[order[i]]=order.length-1-i;
        var pending=new Ints();var members=new Ints();int components=0;
        for(int i=order.length-1;i>=0;i--) {
            int root=order[i];if(component[root]>=0)continue;
            component[root]=components;pending.add(root);members.clear();boolean loop=false;
            while(!pending.isEmpty()) {
                int point=pending.pop();members.add(point);
                for(int e=incoming(point);e>=0;e=inNext(e)) {
                    int parent=source(e);loop|=parent==point;
                    if(component[parent]<0){component[parent]=components;pending.add(parent);}
                }
            }
            if(members.size()>1||loop)for(int j=0;j<members.size();j++)cyclic.set(members.get(j));components++;
        }
    }
    int rank(Point point){int id=find(point);return id<0||id>=closedSize?Integer.MAX_VALUE:rank[id];}
    int rank(int id){return id<closedSize?rank[id]:Integer.MAX_VALUE;}
    int component(int id){return id<closedSize?component[id]:closedSize+id;}
    int[] postorder(){return order;}
    BitSet cycles(){return (BitSet)cyclic.clone();}
    /** Primitive stack/queue. Drained queues retain only an int buffer. */
    static final class Ints {
        private int[] values=new int[16];private int head,tail;
        boolean isEmpty(){return head==tail;}int size(){return tail-head;}
        void add(int value) {
            if(tail==values.length) {
                if(head>0){System.arraycopy(values,head,values,0,tail-head);tail-=head;head=0;}
                else values=Arrays.copyOf(values,values.length*2);
            }values[tail++]=value;
        }
        int remove(){int result=values[head++];if(head==tail)clear();return result;}
        int pop(){int result=values[--tail];if(head==tail)clear();return result;}
        int last(){return values[tail-1];}void last(int value){values[tail-1]=value;}
        int get(int index){return values[head+index];}void clear(){head=tail=0;}
    }
}
