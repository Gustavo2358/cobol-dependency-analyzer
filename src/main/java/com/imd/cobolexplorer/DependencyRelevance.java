package com.imd.cobolexplorer;

import java.util.*;
import java.lang.ref.WeakReference;
import java.util.function.Function;
import java.util.function.IntPredicate;
import static com.imd.cobolexplorer.DependencyGraph.Ints;

/** Backward input demand over the canonical indexed control graph. Only demand
 * facts belong to this solver; topology, component IDs and order are shared.
 * Conditional overwrites retain passthrough before the returning paths join. */
final class DependencyRelevance {
    sealed interface Input permits Value,Handler,Fact { }
    record Value(int declaration) implements Input { }
    record Handler(String name) implements Input { }
    record Fact(String statement) implements Input { }
    record Point(DependencyFlow.Exit exit,String endpoint,String escapeScope) {
        Point(DependencyFlow.Exit exit,String endpoint){this(exit,endpoint,"");}
    }
    record Effect(Set<Input> reads,Set<Input> kills) {
        Effect {reads=Set.copyOf(reads);kills=Set.copyOf(kills);}
    }
    private final Function<Point,Effect> effects;
    private final DependencyGraph graph;
    private final long maxWork;private long work;
    private final Map<Input,Integer> slots=new HashMap<>();private final List<Input> inputs=new ArrayList<>();
    private final BitSet discovered=new BitSet();
    private BitSet[] reads=new BitSet[16],kills=new BitSet[16],needs=new BitSet[16],possible=new BitSet[16],definite=new BitSet[16];
    private final Map<Integer,Set<Input>> decoded=new HashMap<>();
    // Stored facts are immutable. Weak interning shares equal demand across
    // obligations without keeping obsolete iterations alive as a global cache.
    private final WeakHashMap<BitSet,WeakReference<BitSet>> facts=new WeakHashMap<>();
    private final BitSet empty=new BitSet();

    DependencyRelevance(DependencyGraph graph,Function<Point,Effect> effects,long maxWork){this.graph=graph;this.effects=effects;this.maxWork=maxWork;}
    Set<Input> needed(Point point) {
        int entry=graph.id(point);if(!discovered.get(entry))extend(entry);
        return decoded.computeIfAbsent(entry,key->{var result=new HashSet<Input>();var bits=needs[key];for(int i=bits.nextSetBit(0);i>=0;i=bits.nextSetBit(i+1))result.add(inputs.get(i));return Set.copyOf(result);});
    }
    boolean sameComponent(Point a,Point b){return graph.component(graph.id(a))==graph.component(graph.id(b));}
    private BitSet encode(Set<Input> values) {
        var bits=new BitSet();for(var value:values)bits.set(slots.computeIfAbsent(value,k->{int id=inputs.size();inputs.add(k);return id;}));return retain(bits);
    }
    private BitSet retain(BitSet bits) {
        if(bits.isEmpty())return empty;
        var reference=facts.get(bits);var existing=reference==null?null:reference.get();if(existing!=null)return existing;
        facts.put(bits,new WeakReference<>(bits));return bits;
    }
    private void tick(){if(++work>maxWork)throw new IllegalStateException("RESOURCE_LIMIT: input relevance exceeded --max-work="+maxWork);}
    private void capacity(int id) {
        if(id<needs.length)return;int n=Math.max(id+1,needs.length*2);reads=Arrays.copyOf(reads,n);kills=Arrays.copyOf(kills,n);needs=Arrays.copyOf(needs,n);possible=Arrays.copyOf(possible,n);definite=Arrays.copyOf(definite,n);
    }
    private void extend(int entry) {
        var added=new BitSet();var pending=new Ints();pending.add(entry);
        while(!pending.isEmpty()) {
            int point=pending.remove();if(discovered.get(point))continue;tick();capacity(point);discovered.set(point);added.set(point);
            var effect=effects.apply(graph.point(point));reads[point]=encode(effect.reads());kills[point]=encode(effect.kills());needs[point]=empty;possible[point]=empty;definite[point]=empty;
            for(long e=graph.outgoing(point);e>=0;e=graph.outNext(e))pending.add(graph.target(e));
        }
        // A closed successor subset has the same SCCs and postorder as the
        // canonical graph. No topology or reverse relation is rebuilt here.
        var order=new Ints();for(int point:graph.postorder())if(added.get(point)){order.add(point);added.clear(point);}
        for(int point=added.nextSetBit(0);point>=0;point=added.nextSetBit(point+1))order.add(point); // Isolated query points.
        propagate(order,point->{
            var writes=(BitSet)kills[point].clone();BitSet guaranteed=null;
            for(long e=graph.outgoing(point);e>=0;e=graph.outNext(e)) {
                int child=graph.target(e);writes.or(possible[child]);if(guaranteed==null)guaranteed=(BitSet)definite[child].clone();else guaranteed.and(definite[child]);
            }
            if(guaranteed==null)guaranteed=new BitSet();guaranteed.or(kills[point]);
            if(writes.equals(possible[point])&&guaranteed.equals(definite[point]))return false;
            possible[point]=retain(writes);definite[point]=retain(guaranteed);return true;
        });
        propagate(order,point->{
            var next=new BitSet();for(long e=graph.outgoing(point);e>=0;e=graph.outNext(e))next.or(needs[graph.target(e)]);
            next.andNot(kills[point]);next.or(reads[point]);var passthrough=(BitSet)possible[point].clone();passthrough.andNot(definite[point]);next.or(passthrough);
            if(next.equals(needs[point]))return false;needs[point]=retain(next);decoded.remove(point);return true;
        });
    }
    private void propagate(Ints order,IntPredicate update) {
        var pending=new Ints();var scheduled=new BitSet();for(int i=0;i<order.size();i++){int point=order.get(i);pending.add(point);scheduled.set(point);}
        while(!pending.isEmpty()) {
            tick();int point=pending.remove();scheduled.clear(point);if(!update.test(point))continue;
            for(long e=graph.incoming(point);e>=0;e=graph.inNext(e)) {
                int parent=graph.source(e);if(discovered.get(parent)&&!scheduled.get(parent)){scheduled.set(parent);pending.add(parent);}
            }
        }
    }
}
