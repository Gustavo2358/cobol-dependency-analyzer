package com.imd.cobolexplorer;

import java.util.*;
import java.util.function.*;
import static com.imd.cobolexplorer.DependencyRelevance.Point;
import static com.imd.cobolexplorer.DependencyFlow.Exit;
import static com.imd.cobolexplorer.DependencyGraph.*;

/** Exact control closure over physical flow and return obligations. Flow is
 * built once per position. Result columns carry one exit/obligation through sets
 * of positions; calls deliver once to a shared continuation, retaining every
 * caller obligation. Recursive equations start empty and cannot invent returns. */
final class DependencyControl {
    record Call(Point entry,String binding) { }
    record Effect(List<Exit> next,List<Call> calls,List<Point> entries,Set<Exit> exits) {
        Effect {next=List.copyOf(next);calls=List.copyOf(calls);entries=List.copyOf(entries);exits=Set.copyOf(exits);}
        static Effect next(Exit position){return new Effect(List.of(position),List.of(),List.of(),Set.of());}
        static Effect exit(Exit exit){return new Effect(List.of(),List.of(),List.of(),Set.of(exit));}
    }
    /** A boundary partitions obligations: stopped ones return; the rest use
     * the ordinary continuation. The predicate does not merge their policies. */
    record Rule(Effect continuation,Function<Scope,Set<Exit>> returns) {
        static Rule flow(Effect effect){return new Rule(effect,s->Set.of());}
        static Rule boundary(Exit exit,Exit next,Predicate<Scope> stop){return new Rule(Effect.next(next),s->stop.test(s)?Set.of(exit):Set.of());}
    }
    interface Delivery { Effect apply(Call call,Exit exit); }
    final DependencyGraph graph=new DependencyGraph();
    private final Set<String> reachableNodes=new HashSet<>(),observedNodes=new HashSet<>();
    final int summaryCount,physicalNodes,physicalEdges,obligations,resultColumns;long visits,resultPairs,resultFacts;

    DependencyControl(List<Point> roots,Function<Exit,Rule> effects,Delivery delivery,
            Predicate<Exit> observation,long maxWork) {
        var construction=new Construction(effects,delivery,maxWork);construction.solve(roots);
        summaryCount=graph.size();physicalNodes=graph.physicalSize();physicalEdges=graph.edgeCount();obligations=graph.scopeCount();resultColumns=construction.columns.size();
        visits=construction.visits;resultPairs=construction.resultPairs;resultFacts=construction.resultFacts;construction.release();
        var observed=new BitSet();var retained=new BitSet();var pending=new Ints();var body=new Ints();
        for(int id=0;id<graph.size();id++){var exit=graph.positionExit(graph.position(id));reachableNodes.add(exit.reference());if(observation.test(exit)){observed.set(id);pending.add(id);}}
        while(!pending.isEmpty()) {
            int point=pending.remove();
            for(long e=graph.incoming(point);e>=0;e=graph.inNext(e)){int parent=graph.source(e);if(!observed.get(parent)){observed.set(parent);pending.add(parent);}}
            for(long e=graph.outgoing(point);e>=0;e=graph.outNext(e))if(graph.kind(e)==CALL&&!graph.binding(e).isEmpty())body.add(graph.target(e));
            while(!body.isEmpty()){int child=body.remove();if(retained.get(child))continue;retained.set(child);if(!observed.get(child)){observed.set(child);pending.add(child);}for(long e=graph.outgoing(child);e>=0;e=graph.outNext(e))body.add(graph.target(e));}
        }
        for(int id=observed.nextSetBit(0);id>=0;id=observed.nextSetBit(id+1))observedNodes.add(graph.positionExit(graph.position(id)).reference());graph.close();
    }
    boolean reachable(String node){return reachableNodes.contains(node);}boolean observed(String node){return observedNodes.contains(node);}
    int forwardRank(Point point){return graph.rank(point);}
    Set<String> cyclicNodes() {
        var cyclic=graph.cycles();var pending=new Ints();
        for(int point=cyclic.nextSetBit(0);point>=0;point=cyclic.nextSetBit(point+1))for(long e=graph.outgoing(point);e>=0;e=graph.outNext(e))if(graph.kind(e)==CALL)pending.add(graph.target(e));
        while(!pending.isEmpty()){int point=pending.remove();if(cyclic.get(point))continue;cyclic.set(point);for(long e=graph.outgoing(point);e>=0;e=graph.outNext(e))pending.add(graph.target(e));}
        var names=new HashSet<String>();for(int id=cyclic.nextSetBit(0);id>=0;id=cyclic.nextSetBit(id+1))names.add(graph.positionExit(graph.position(id)).reference());return Set.copyOf(names);
    }
    private static final class Column {
        final int scope,exit;final BitSet positions=new BitSet(),pending=new BitSet();boolean queued;
        Column(int scope,int exit){this.scope=scope;this.exit=exit;}
    }
    private final class Construction {
        private final Function<Exit,Rule> effects;private final Delivery delivery;private final long maxWork;
        private Rule[] rules=new Rule[16];private Ints[] discoveries=new Ints[16];private BitSet[] direct=new BitSet[16],delivered=new BitSet[16],pendingDeliveries=new BitSet[16];
        private final BitSet initialized=new BitSet(),discovered=new BitSet(),queuedPhysical=new BitSet(),queuedDeliveries=new BitSet();
        private final Ints discovery=new Ints(),work=new Ints();
        private final List<Exit> exits=new ArrayList<>();private final Map<Exit,Integer> exitIds=new HashMap<>();
        private final List<Column> columns=new ArrayList<>();private final List<Map<Integer,Integer>> byScope=new ArrayList<>();
        long visits,resultPairs,resultFacts;
        Construction(Function<Exit,Rule> effects,Delivery delivery,long maxWork){this.effects=effects;this.delivery=delivery;this.maxWork=maxWork;}
        void release(){rules=null;discoveries=null;direct=null;delivered=null;pendingDeliveries=null;columns.clear();byScope.clear();exits.clear();exitIds.clear();}
        private void tick(){if(++visits>maxWork)throw new IllegalStateException("RESOURCE_LIMIT: control summaries exceeded --max-work="+maxWork);}
        private void capacity(int physical) {
            if(physical<rules.length)return;int n=Math.max(physical+1,rules.length*2);rules=Arrays.copyOf(rules,n);discoveries=Arrays.copyOf(discoveries,n);direct=Arrays.copyOf(direct,n);
        }
        private Rule rule(int physical){capacity(physical);if(rules[physical]==null)rules[physical]=effects.apply(graph.positionExit(physical));return rules[physical];}
        private int exitId(Exit exit){return exitIds.computeIfAbsent(exit,k->{int id=exits.size();exits.add(k);return id;});}
        private int ensure(Point point) {
            int old=graph.find(point);if(old>=0)return old;
            if(graph.size()>=maxWork)throw new IllegalStateException("RESOURCE_LIMIT: too many control summaries");
            int id=graph.id(point),physical=graph.position(id),scope=graph.obligation(id);var local=rule(physical);
            while(byScope.size()<=scope)byScope.add(new HashMap<>());
            var stops=local.returns().apply(graph.scope(scope));
            if(!stops.isEmpty()){graph.stop(id);for(var exit:stops)publish(physical,scope,exitId(exit));}
            else {
                for(var exit:local.continuation().exits())publish(physical,scope,exitId(exit));
                if(direct[physical]!=null)for(int exit=direct[physical].nextSetBit(0);exit>=0;exit=direct[physical].nextSetBit(exit+1))publish(physical,scope,exit);
                if(discoveries[physical]==null)discoveries[physical]=new Ints();discoveries[physical].add(id);
                if(!queuedPhysical.get(physical)){queuedPhysical.set(physical);discovery.add(physical);}
            }return id;
        }
        private int ensure(int physical,int scope){int old=graph.find(physical,scope);if(old>=0)return old;var s=graph.scope(scope);return ensure(new Point(graph.positionExit(physical),s.endpoint(),s.escapeScope()));}
        void solve(List<Point> roots) {
            roots.forEach(this::ensure);
            while(!discovery.isEmpty()||!work.isEmpty()) {
                while(!discovery.isEmpty()) {
                    tick();int physical=discovery.remove();queuedPhysical.clear(physical);var points=discoveries[physical];discoveries[physical]=null;
                    if(!initialized.get(physical)){initialized.set(physical);add(physical,rule(physical).continuation());}
                    while(!points.isEmpty()) {
                        int point=points.remove();discovered.set(point);
                        for(int e=graph.physicalOutgoing(physical);e>=0;e=graph.physicalOutNext(e))activate(point,e);
                    }
                }
                if(work.isEmpty())continue;tick();int task=work.remove();
                if(task<0) {
                    int edge=~task;queuedDeliveries.clear(edge);var pending=pendingDeliveries[edge];pendingDeliveries[edge]=null;
                    for(int exit=pending.nextSetBit(0);exit>=0;exit=pending.nextSetBit(exit+1))deliver(edge,exit);
                    continue;
                }
                var column=columns.get(task);column.queued=false;
                var delta=(BitSet)column.pending.clone();column.pending.clear();
                for(int physical=delta.nextSetBit(0);physical>=0;physical=delta.nextSetBit(physical+1)) {
                    int child=graph.find(physical,column.scope);
                    for(int e=graph.physicalIncoming(physical);e>=0;e=graph.physicalInNext(e)) {
                        if(graph.physicalKind(e)==NEXT) {
                            int parent=graph.find(graph.physicalSource(e),column.scope);
                            if(parent>=0&&discovered.get(parent)&&!graph.stopped(parent))publish(graph.physicalSource(e),column.scope,column.exit);
                        }else if(graph.physicalKind(e)==CALL&&graph.fixedTarget(e)==child)offer(e,column.exit);
                    }
                }
            }
        }
        private void publish(int physical,int scope,int exit) {
            int id=byScope.get(scope).computeIfAbsent(exit,k->{int index=columns.size();columns.add(new Column(scope,exit));return index;});var column=columns.get(id);
            if(column.positions.get(physical))return;column.positions.set(physical);column.pending.set(physical);resultFacts++;
            if(!column.queued){column.queued=true;work.add(id);}
        }
        private void existing(int child,IntConsumer consumer) {
            int physical=graph.position(child);for(int id:List.copyOf(byScope.get(graph.obligation(child)).values())){var column=columns.get(id);if(column.positions.get(physical))consumer.accept(column.exit);}
        }
        private void activate(int point,int edge) {
            int child=graph.physicalKind(edge)==NEXT?ensure(graph.physicalTarget(edge),graph.obligation(point)):graph.fixedTarget(edge);
            if(graph.physicalKind(edge)==NEXT)existing(child,exit->publish(graph.position(point),graph.obligation(point),exit));
            else if(graph.physicalKind(edge)==CALL) {
                if(edge<delivered.length&&delivered[edge]!=null)resultPairs+=delivered[edge].cardinality();
                existing(child,exit->offer(edge,exit));
            }
        }
        private void offer(int edge,int exit) {
            if(edge>=delivered.length){int n=Math.max(edge+1,delivered.length*2);delivered=Arrays.copyOf(delivered,n);pendingDeliveries=Arrays.copyOf(pendingDeliveries,n);}
            if(delivered[edge]!=null&&delivered[edge].get(exit))return;
            if(pendingDeliveries[edge]==null)pendingDeliveries[edge]=new BitSet();pendingDeliveries[edge].set(exit);
            if(!queuedDeliveries.get(edge)){queuedDeliveries.set(edge);work.add(~edge);}
        }
        private void deliver(int edge,int exit) {
            if(delivered[edge]==null)delivered[edge]=new BitSet();if(delivered[edge].get(exit))return;delivered[edge].set(exit);
            int physical=graph.physicalSource(edge);
            for(int point=graph.firstCell(physical);point>=0;point=graph.nextCell(point))if(discovered.get(point)&&!graph.stopped(point))resultPairs++;
            add(physical,delivery.apply(new Call(graph.point(graph.fixedTarget(edge)),graph.physicalBinding(edge)),exits.get(exit)));
        }
        private void newEdge(int edge) {
            if(edge<0)return;int physical=graph.physicalSource(edge);
            for(int point=graph.firstCell(physical);point>=0;point=graph.nextCell(point))if(discovered.get(point)&&!graph.stopped(point))activate(point,edge);
        }
        private void add(int physical,Effect effect) {
            for(var exit:effect.exits()) {
                int id=exitId(exit);if(direct[physical]==null)direct[physical]=new BitSet();if(direct[physical].get(id))continue;direct[physical].set(id);
                for(int point=graph.firstCell(physical);point>=0;point=graph.nextCell(point))if(discovered.get(point)&&!graph.stopped(point))publish(physical,graph.obligation(point),id);
            }
            for(var next:effect.next())newEdge(graph.add(physical,graph.physicalId(next),NEXT,-1,""));
            for(var call:effect.calls()){int child=ensure(call.entry());newEdge(graph.add(physical,graph.position(child),CALL,child,call.binding()));}
            for(var entry:effect.entries()){int child=ensure(entry);newEdge(graph.add(physical,graph.position(child),ENTRY,child,""));}
        }
    }
}
