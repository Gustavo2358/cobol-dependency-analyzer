package com.imd.cobolexplorer;

import java.util.*;
import static com.imd.cobolexplorer.DependencyRelevance.Point;
import static com.imd.cobolexplorer.DependencyFlow.Exit;

/** A physical control graph with exact return obligations. Ordinary edges
 * preserve the obligation; calls/independent entries carry a fixed obligation.
 * Logical cells are sparse IDs, not cloned nodes or cloned adjacency lists. */
final class DependencyGraph {
    static final byte NEXT=0,CALL=1,ENTRY=2;
    record Scope(String endpoint,String escapeScope) { }
    private final Map<Exit,Integer> positionIds=new HashMap<>();private final List<Exit> positions=new ArrayList<>();
    private final Map<Scope,Integer> scopeIds=new HashMap<>();private final List<Scope> scopes=new ArrayList<>();
    private int[] cellPosition=new int[16],cellScope=new int[16],nextCell=new int[16],cellSlots=new int[32];
    private int cells,closedSize;private boolean closed;
    private final BitSet stopped=new BitSet();
    private final Map<String,Integer> bindingIds=new HashMap<>();private final List<String> bindings=new ArrayList<>();
    private int[] outgoing=new int[16],incoming=new int[16],headCell=new int[16];
    private int[] sources=new int[16],targets=new int[16],outNext=new int[16],inNext=new int[16],binding=new int[16],fixed=new int[16];
    private byte[] kinds=new byte[16];private int[] slots=new int[32];private int edges;
    private int[] order,rank,component;private final BitSet cyclic=new BitSet();
    DependencyGraph(){Arrays.fill(outgoing,-1);Arrays.fill(incoming,-1);Arrays.fill(headCell,-1);}
    int size(){return cells;}int physicalSize(){return positions.size();}int scopeCount(){return scopes.size();}int edgeCount(){return edges;}
    Exit positionExit(int id){return positions.get(id);}Scope scope(int id){return scopes.get(id);}
    int position(int cell){return cellPosition[cell];}int obligation(int cell){return cellScope[cell];}
    Point point(int id){var s=scopes.get(cellScope[id]);return new Point(positions.get(cellPosition[id]),s.endpoint(),s.escapeScope());}
    int physicalId(Exit exit) {
        var old=positionIds.get(exit);if(old!=null)return old;int id=physicalSize();positionIds.put(exit,id);positions.add(exit);
        if(id==outgoing.length){int n=id*2;outgoing=Arrays.copyOf(outgoing,n);incoming=Arrays.copyOf(incoming,n);headCell=Arrays.copyOf(headCell,n);Arrays.fill(outgoing,id,n,-1);Arrays.fill(incoming,id,n,-1);Arrays.fill(headCell,id,n,-1);}return id;
    }
    private int scopeId(Scope scope){return scopeIds.computeIfAbsent(scope,s->{int id=scopes.size();scopes.add(s);return id;});}
    private static int mix(int a,int b){int h=a*0x9e3779b9+b;h^=h>>>16;h*=0x7feb352d;h^=h>>>15;return h;}
    int find(int position,int scope) {
        int slot=mix(position,scope)&(cellSlots.length-1);
        while(cellSlots[slot]!=0){int id=cellSlots[slot]-1;if(cellPosition[id]==position&&cellScope[id]==scope)return id;slot=(slot+1)&(cellSlots.length-1);}return -1;
    }
    int find(Point point) {
        var position=positionIds.get(point.exit());var scope=scopeIds.get(new Scope(point.endpoint(),point.escapeScope()));return position==null||scope==null?-1:find(position,scope);
    }
    int id(Point point){return id(physicalId(point.exit()),scopeId(new Scope(point.endpoint(),point.escapeScope())));}
    int id(int position,int scope) {
        int old=find(position,scope);if(old>=0)return old;
        if((cells+1)*2>cellSlots.length){cellSlots=new int[cellSlots.length*2];for(int id=0;id<cells;id++)indexCell(id);}
        if(cells==cellPosition.length){int n=cells*2;cellPosition=Arrays.copyOf(cellPosition,n);cellScope=Arrays.copyOf(cellScope,n);nextCell=Arrays.copyOf(nextCell,n);}
        int id=cells++;cellPosition[id]=position;cellScope[id]=scope;nextCell[id]=headCell[position];headCell[position]=id;indexCell(id);return id;
    }
    private void indexCell(int id){int slot=mix(cellPosition[id],cellScope[id])&(cellSlots.length-1);while(cellSlots[slot]!=0)slot=(slot+1)&(cellSlots.length-1);cellSlots[slot]=id+1;}
    int firstCell(int position){return headCell[position];}int nextCell(int cell){return nextCell[cell];}
    void stop(int cell){stopped.set(cell);}boolean stopped(int cell){return stopped.get(cell);}
    private boolean active(int cell){return cell>=0&&(!closed||cell<closedSize);}
    int physicalOutgoing(int point){return outgoing[point];}int physicalIncoming(int point){return incoming[point];}
    int physicalOutNext(int edge){return outNext[edge];}int physicalInNext(int edge){return inNext[edge];}
    int physicalSource(int edge){return sources[edge];}int physicalTarget(int edge){return targets[edge];}
    int fixedTarget(int edge){return fixed[edge];}byte physicalKind(int edge){return kinds[edge];}String physicalBinding(int edge){return bindings.get(binding[edge]);}
    private int edgeHash(int from,int to,byte kind,int fixed,int binding){return mix(mix(from,to),mix(fixed,binding)^kind);}
    /** A shared edge is registered once, even when many obligations traverse it. */
    int add(int from,int to,byte kind,int targetCell,String name) {
        if(closed)throw new IllegalStateException("control graph is closed");
        int bind=bindingIds.computeIfAbsent(name,k->{int id=bindings.size();bindings.add(k);return id;});
        if((edges+1)*2>slots.length){slots=new int[slots.length*2];for(int e=0;e<edges;e++)indexEdge(e);}
        int slot=edgeHash(from,to,kind,targetCell,bind)&(slots.length-1);
        while(slots[slot]!=0){int e=slots[slot]-1;if(sources[e]==from&&targets[e]==to&&kinds[e]==kind&&fixed[e]==targetCell&&binding[e]==bind)return ~e;slot=(slot+1)&(slots.length-1);}
        if(edges==sources.length){int n=edges*2;sources=Arrays.copyOf(sources,n);targets=Arrays.copyOf(targets,n);outNext=Arrays.copyOf(outNext,n);inNext=Arrays.copyOf(inNext,n);binding=Arrays.copyOf(binding,n);fixed=Arrays.copyOf(fixed,n);kinds=Arrays.copyOf(kinds,n);}
        int e=edges++;sources[e]=from;targets[e]=to;kinds[e]=kind;binding[e]=bind;fixed[e]=targetCell;
        outNext[e]=outgoing[from];outgoing[from]=e;inNext[e]=incoming[to];incoming[to]=e;slots[slot]=e+1;return e;
    }
    private void indexEdge(int e){int slot=edgeHash(sources[e],targets[e],kinds[e],fixed[e],binding[e])&(slots.length-1);while(slots[slot]!=0)slot=(slot+1)&(slots.length-1);slots[slot]=e+1;}
    private static long cursor(int cell,int edge){return ((long)cell<<32)|(edge&0xffffffffL);}
    private static int cursorCell(long cursor){return (int)(cursor>>>32);}private static int cursorEdge(long cursor){return (int)cursor;}
    private int target(int cell,int edge){return kinds[edge]==NEXT?find(targets[edge],cellScope[cell]):fixed[edge];}
    private long outgoing(int cell,int edge) {
        if(!active(cell)||stopped.get(cell))return -1;
        while(edge>=0){if(active(target(cell,edge)))return cursor(cell,edge);edge=outNext[edge];}return -1;
    }
    long outgoing(int cell){return outgoing(cell,outgoing[cellPosition[cell]]);}
    long outNext(long cursor){return outgoing(cursorCell(cursor),outNext[cursorEdge(cursor)]);}
    int source(long cursor){return cursorCell(cursor);}int target(long cursor){return target(cursorCell(cursor),cursorEdge(cursor));}
    byte kind(long cursor){return kinds[cursorEdge(cursor)];}String binding(long cursor){return physicalBinding(cursorEdge(cursor));}
    private long incoming(int child,int edge) {
        if(!active(child))return -1;
        while(edge>=0) {
            if(kinds[edge]==NEXT){int parent=find(sources[edge],cellScope[child]);if(active(parent)&&!stopped.get(parent))return cursor(parent,edge);}
            else if(fixed[edge]==child)for(int parent=headCell[sources[edge]];parent>=0;parent=nextCell[parent])if(active(parent)&&!stopped.get(parent))return cursor(parent,edge);
            edge=inNext[edge];
        }return -1;
    }
    long incoming(int cell){return incoming(cell,incoming[cellPosition[cell]]);}
    long inNext(long cursor) {
        int edge=cursorEdge(cursor),child=target(cursor);
        if(kinds[edge]!=NEXT)for(int parent=nextCell[cursorCell(cursor)];parent>=0;parent=nextCell[parent])if(active(parent)&&!stopped.get(parent))return cursor(parent,edge);
        return incoming(child,inNext[edge]);
    }
    void close() {
        closed=true;closedSize=cells;slots=null;bindingIds.clear();order=new int[cells];rank=new int[cells];component=new int[cells];Arrays.fill(component,-1);
        var seen=new BitSet();var nodes=new Ints();var cursors=new Longs();int count=0;
        for(int root=0;root<cells;root++)if(!seen.get(root)) {
            seen.set(root);nodes.add(root);cursors.add(outgoing(root));
            while(!nodes.isEmpty()) {
                long edge=cursors.last();if(edge<0){order[count++]=nodes.pop();cursors.pop();continue;}
                cursors.last(outNext(edge));int child=target(edge);if(!seen.get(child)){seen.set(child);nodes.add(child);cursors.add(outgoing(child));}
            }
        }
        for(int i=0;i<order.length;i++)rank[order[i]]=order.length-1-i;
        var pending=new Ints();var members=new Ints();int components=0;
        for(int i=order.length-1;i>=0;i--) {
            int root=order[i];if(component[root]>=0)continue;component[root]=components;pending.add(root);members.clear();boolean loop=false;
            while(!pending.isEmpty()) {
                int point=pending.pop();members.add(point);
                for(long e=incoming(point);e>=0;e=inNext(e)){int parent=source(e);loop|=parent==point;if(component[parent]<0){component[parent]=components;pending.add(parent);}}
            }
            if(members.size()>1||loop)for(int j=0;j<members.size();j++)cyclic.set(members.get(j));components++;
        }
    }
    int rank(Point point){int id=find(point);return id<0||id>=closedSize?Integer.MAX_VALUE:rank[id];}
    int component(int id){return id<closedSize?component[id]:closedSize+id;}
    int[] postorder(){return order;}BitSet cycles(){return (BitSet)cyclic.clone();}
    /** Primitive stack/queue; only a compact int buffer survives a drained batch. */
    static final class Ints {
        private int[] values=new int[16];private int head,tail;
        boolean isEmpty(){return head==tail;}int size(){return tail-head;}
        void add(int value){if(tail==values.length){if(head>0){System.arraycopy(values,head,values,0,tail-head);tail-=head;head=0;}else values=Arrays.copyOf(values,values.length*2);}values[tail++]=value;}
        int remove(){int result=values[head++];if(head==tail)clear();return result;}int pop(){int result=values[--tail];if(head==tail)clear();return result;}
        int get(int index){return values[head+index];}void clear(){head=tail=0;}
    }
    private static final class Longs {
        private long[] values=new long[16];private int size;
        void add(long value){if(size==values.length)values=Arrays.copyOf(values,size*2);values[size++]=value;}
        long last(){return values[size-1];}void last(long value){values[size-1]=value;}void pop(){size--;}
    }
}
