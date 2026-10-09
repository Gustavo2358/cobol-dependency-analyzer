package com.imd.cobolexplorer;

import java.util.*;
import java.util.function.*;
import static com.imd.cobolexplorer.DependencyRelevance.Point;
import static com.imd.cobolexplorer.DependencyFlow.Exit;
import static com.imd.cobolexplorer.DependencyGraph.*;

/** Parametric control summaries. Recursive equations start empty; a cycle
 * cannot manufacture a return. Invocation results are delivered only to their
 * registered binding. The indexed graph survives without copying its plans. */
final class DependencyControl {
    record Call(Point entry,String binding) { }
    record Effect(List<Point> next,List<Call> calls,List<Point> entries,Set<Exit> exits) {
        Effect {next=List.copyOf(next);calls=List.copyOf(calls);entries=List.copyOf(entries);exits=Set.copyOf(exits);}
        static Effect next(Point point){return new Effect(List.of(point),List.of(),List.of(),Set.of());}
        static Effect exit(Exit exit){return new Effect(List.of(),List.of(),List.of(),Set.of(exit));}
    }
    interface Delivery { Effect apply(Point caller,Call call,Exit exit); }
    final DependencyGraph graph=new DependencyGraph();
    private final Set<String> reachableNodes=new HashSet<>(),observedNodes=new HashSet<>();
    final int summaryCount;
    long visits,resultPairs,resultFacts;

    DependencyControl(List<Point> roots,Function<Point,Effect> effects,Delivery delivery,
            Predicate<Point> observation,long maxWork) {
        var construction=new Construction(effects,delivery,maxWork);construction.solve(roots);
        summaryCount=graph.size();visits=construction.visits;resultPairs=construction.resultPairs;resultFacts=construction.resultFacts;
        construction.release();
        // Every interned node was reached from a root through an admitted edge.
        var observed=new BitSet();var retained=new BitSet();var pending=new Ints();var body=new Ints();
        for(int id=0;id<graph.size();id++) {
            var point=graph.point(id);reachableNodes.add(point.exit().reference());
            if(observation.test(point)){observed.set(id);pending.add(id);}
        }
        while(!pending.isEmpty()) {
            int point=pending.remove();
            for(int e=graph.incoming(point);e>=0;e=graph.inNext(e)) {
                int parent=graph.source(e);if(!observed.get(parent)){observed.set(parent);pending.add(parent);}
            }
            for(int e=graph.outgoing(point);e>=0;e=graph.outNext(e))if(graph.kind(e)==CALL&&!graph.binding(e).isEmpty())body.add(graph.target(e));
            while(!body.isEmpty()) {
                int child=body.remove();if(retained.get(child))continue;retained.set(child);
                if(!observed.get(child)){observed.set(child);pending.add(child);}
                for(int e=graph.outgoing(child);e>=0;e=graph.outNext(e))body.add(graph.target(e));
            }
        }
        for(int id=observed.nextSetBit(0);id>=0;id=observed.nextSetBit(id+1))observedNodes.add(graph.point(id).exit().reference());
        graph.close(); // Scratch results, deltas and delivery marks end with construction.
    }
    boolean reachable(String node){return reachableNodes.contains(node);}
    boolean observed(String node){return observedNodes.contains(node);}
    int forwardRank(Point point){return graph.rank(point);}
    Set<String> cyclicNodes() {
        var cyclic=graph.cycles();var pending=new Ints();
        for(int point=cyclic.nextSetBit(0);point>=0;point=cyclic.nextSetBit(point+1))
            for(int e=graph.outgoing(point);e>=0;e=graph.outNext(e))if(graph.kind(e)==CALL)pending.add(graph.target(e));
        while(!pending.isEmpty()) {
            int point=pending.remove();if(cyclic.get(point))continue;cyclic.set(point);
            for(int e=graph.outgoing(point);e>=0;e=graph.outNext(e))pending.add(graph.target(e));
        }
        var names=new HashSet<String>();for(int id=cyclic.nextSetBit(0);id>=0;id=cyclic.nextSetBit(id+1))names.add(graph.point(id).exit().reference());return Set.copyOf(names);
    }
    private final class Construction {
        private BitSet[] results=new BitSet[16],pending=new BitSet[16],seenCalls=new BitSet[16],pendingCalls=new BitSet[16];
        private final BitSet queuedPoints=new BitSet(),queuedCalls=new BitSet();
        private final Ints discovery=new Ints(),work=new Ints();
        private final List<Exit> exits=new ArrayList<>();private final Map<Exit,Integer> exitIds=new HashMap<>();
        private final Function<Point,Effect> effects;private final Delivery delivery;private final long maxWork;
        long visits,resultPairs,resultFacts;
        Construction(Function<Point,Effect> effects,Delivery delivery,long maxWork){this.effects=effects;this.delivery=delivery;this.maxWork=maxWork;}
        void release(){results=pending=seenCalls=pendingCalls=null;exits.clear();exitIds.clear();}
        int ensure(Point point) {
            int old=graph.find(point);if(old>=0)return old;
            if(graph.size()>=maxWork)throw new IllegalStateException("RESOURCE_LIMIT: too many control summaries");
            int id=graph.id(point);if(id==results.length){results=Arrays.copyOf(results,id*2);pending=Arrays.copyOf(pending,id*2);}discovery.add(id);return id;
        }
        void solve(List<Point> roots) {
            roots.forEach(this::ensure);
            while(!discovery.isEmpty()||!work.isEmpty()) {
                while(!discovery.isEmpty()){int id=discovery.remove();add(id,effects.apply(graph.point(id)));}
                if(work.isEmpty())continue;
                if(++visits>maxWork)throw new IllegalStateException("RESOURCE_LIMIT: control summaries exceeded --max-work="+maxWork);
                int task=work.remove();
                if(task>=0) {
                    queuedPoints.clear(task);var delta=pending[task];pending[task]=null;
                    for(int e=graph.incoming(task);e>=0;e=graph.inNext(e)) {
                        if(graph.kind(e)==NEXT)publish(graph.source(e),delta);
                        else if(graph.kind(e)==CALL)offer(e,delta);
                    }
                }else {
                    int edge=~task;queuedCalls.clear(edge);var delta=pendingCalls[edge];pendingCalls[edge]=null;
                    var call=new Call(graph.point(graph.target(edge)),graph.binding(edge));int caller=graph.source(edge);
                    for(int id=delta.nextSetBit(0);id>=0;id=delta.nextSetBit(id+1)) {
                        resultPairs++;add(caller,delivery.apply(graph.point(caller),call,exits.get(id)));
                    }
                }
            }
        }
        void publish(int point,BitSet values) {
            if(values==null||values.isEmpty())return;var delta=(BitSet)values.clone();if(results[point]!=null)delta.andNot(results[point]);if(delta.isEmpty())return;
            resultFacts+=delta.cardinality();if(results[point]==null)results[point]=new BitSet();results[point].or(delta);
            if(pending[point]==null)pending[point]=delta;else pending[point].or(delta);
            if(!queuedPoints.get(point)){queuedPoints.set(point);work.add(point);}
        }
        void offer(int edge,BitSet values) {
            if(values==null||values.isEmpty())return;
            if(edge>=seenCalls.length){int n=Math.max(edge+1,seenCalls.length*2);seenCalls=Arrays.copyOf(seenCalls,n);pendingCalls=Arrays.copyOf(pendingCalls,n);}
            var delta=(BitSet)values.clone();if(seenCalls[edge]!=null)delta.andNot(seenCalls[edge]);if(delta.isEmpty())return;
            if(seenCalls[edge]==null)seenCalls[edge]=new BitSet();seenCalls[edge].or(delta);
            if(pendingCalls[edge]==null)pendingCalls[edge]=delta;else pendingCalls[edge].or(delta);
            if(!queuedCalls.get(edge)){queuedCalls.set(edge);work.add(~edge);}
        }
        void add(int point,Effect effect) {
            if(!effect.exits().isEmpty()) {
                var values=new BitSet();for(var exit:effect.exits())values.set(exitIds.computeIfAbsent(exit,k->{int id=exits.size();exits.add(k);return id;}));publish(point,values);
            }
            for(var next:effect.next()) {
                int child=ensure(next);if(graph.add(point,child,NEXT,"")>=0)publish(point,results[child]);
            }
            for(var call:effect.calls()) {
                int child=ensure(call.entry());int edge=graph.add(point,child,CALL,call.binding());if(edge>=0)offer(edge,results[child]);
            }
            for(var entry:effect.entries())graph.add(point,ensure(entry),ENTRY,"");
        }
    }
}
