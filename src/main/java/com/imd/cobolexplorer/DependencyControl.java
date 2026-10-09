package com.imd.cobolexplorer;

import java.util.*;
import java.util.function.*;
import static com.imd.cobolexplorer.DependencyRelevance.Point;
import static com.imd.cobolexplorer.DependencyFlow.Exit;

/** Parametric control summaries. A call's continuation becomes reachable only
 * after its body produces a compatible exit. Recursive equations start empty;
 * a cycle cannot manufacture its own return. No value environment is a key. */
final class DependencyControl {
    record Call(Point entry,String binding) { }
    record Effect(List<Point> next,List<Call> calls,List<Point> entries,Set<Exit> exits) {
        Effect {next=List.copyOf(next);calls=List.copyOf(calls);entries=List.copyOf(entries);exits=Set.copyOf(exits);}
        static Effect next(Point point){return new Effect(List.of(point),List.of(),List.of(),Set.of());}
        static Effect exit(Exit exit){return new Effect(List.of(),List.of(),List.of(),Set.of(exit));}
    }
    interface Delivery { Effect apply(Point caller,Call call,Exit exit); }
    private record Plan(List<Point> next,List<Point> entries,List<Call> calls) { }
    private final Map<Point,Plan> plans=new HashMap<>();
    private final Set<Point> reachable=new HashSet<>(),observed=new HashSet<>();
    private final Set<String> reachableNodes=new HashSet<>(),observedNodes=new HashSet<>();
    final int summaryCount;
    long visits,resultPairs,resultFacts;

    DependencyControl(List<Point> roots,Function<Point,Effect> effects,Delivery delivery,
            Predicate<Point> observation,long maxWork) {
        var construction=new Construction(effects,delivery,maxWork);
        construction.solve(roots);
        summaryCount=construction.plans.size();visits=construction.visits;
        resultPairs=construction.resultPairs;resultFacts=construction.resultFacts;
        construction.plans.forEach((point,plan)->plans.put(point,new Plan(
            List.copyOf(plan.next),List.copyOf(plan.entries),List.copyOf(plan.calls.keySet()))));
        var pending=new ArrayDeque<>(roots);
        while(!pending.isEmpty()) {
            var point=pending.removeFirst();if(!reachable.add(point))continue;
            var plan=plans.get(point);pending.addAll(plan.next);pending.addAll(plan.entries);
            for(var call:plan.calls)pending.add(call.entry());
        }
        // Returned values can affect an observation in the caller. Conservatively
        // retain the reachable body of every such call, without combining values.
        var retained=new HashSet<Point>();
        for(var point:reachable)if(observation.test(point)){observed.add(point);pending.add(point);}
        while(!pending.isEmpty()) {
            var point=pending.removeFirst();
            for(var parent:construction.parents.getOrDefault(point,Set.of()))if(reachable.contains(parent)&&observed.add(parent))pending.add(parent);
            for(var call:plans.get(point).calls) {
                if(call.binding().isEmpty())continue; // Independent entry has no observed caller return.
                var body=new ArrayDeque<Point>();body.add(call.entry());var seen=new HashSet<Point>();
                while(!body.isEmpty()) {
                    var child=body.removeFirst();if(!seen.add(child)||!retained.add(child))continue;
                    if(observed.add(child))pending.add(child);
                    var plan=plans.get(child);body.addAll(plan.next);body.addAll(plan.entries);
                    for(var nested:plan.calls)body.add(nested.entry());
                }
            }
        }
        reachable.forEach(p->reachableNodes.add(p.exit().reference()));observed.forEach(p->observedNodes.add(p.exit().reference()));
        // Construction, its exit index, results and delivery marks end here.
    }
    boolean reachable(String node){return reachableNodes.contains(node);}
    boolean observed(String node){return observedNodes.contains(node);}
    List<Point> successors(Point point) {
        var plan=plans.get(point);if(plan==null)return List.of();
        var next=new HashSet<>(plan.next);next.addAll(plan.entries);for(var call:plan.calls)next.add(call.entry());
        return List.copyOf(next);
    }
    Set<String> cyclicNodes() {
        var order=postorder();var seen=new HashSet<Point>();
        var reverse=new HashMap<Point,Set<Point>>();
        for(var point:reachable)for(var child:successors(point))reverse.computeIfAbsent(child,k->new HashSet<>()).add(point);
        seen.clear();var cyclic=new HashSet<Point>();var pending=new ArrayDeque<Point>();
        for(int i=order.size()-1;i>=0;i--) {
            var root=order.get(i);if(!seen.add(root))continue;
            var component=new HashSet<Point>();pending.add(root);
            while(!pending.isEmpty()) {
                var point=pending.removeFirst();component.add(point);
                for(var parent:reverse.getOrDefault(point,Set.of()))if(seen.add(parent))pending.add(parent);
            }
            if(component.size()>1||successors(root).contains(root))cyclic.addAll(component);
        }
        // A repeated call can mutate its arguments on each iteration even when
        // the body itself is acyclic. Include its effects in the recurrence.
        for(var point:List.copyOf(cyclic))for(var call:plans.get(point).calls)pending.add(call.entry());
        while(!pending.isEmpty()) {var point=pending.removeFirst();if(cyclic.add(point))pending.addAll(successors(point));}
        var names=new HashSet<String>();cyclic.forEach(p->names.add(p.exit().reference()));return Set.copyOf(names);
    }
    private List<Point> postorder() {
        var order=new ArrayList<Point>();var seen=new HashSet<Point>();
        record Visit(Point point,Iterator<Point> children) { }
        var stack=new ArrayDeque<Visit>();
        for(var root:reachable.stream().sorted(Comparator.comparing(Point::toString)).toList())if(seen.add(root)) {
            stack.push(new Visit(root,successors(root).iterator()));
            while(!stack.isEmpty()) {
                var top=stack.peek();
                if(top.children().hasNext()) {
                    var child=top.children().next();if(seen.add(child))stack.push(new Visit(child,successors(child).iterator()));
                }else {order.add(top.point());stack.pop();}
            }
        }
        return order;
    }
    Map<Point,Integer> forwardOrder() {
        var order=postorder();var ranks=new HashMap<Point,Integer>();
        for(int i=order.size()-1;i>=0;i--)ranks.put(order.get(i),ranks.size());return Map.copyOf(ranks);
    }

    private abstract static sealed class Task permits PendingPlan,Subscription {boolean queued;}
    private static final class PendingPlan extends Task {
        final Point point;
        final Set<Point> next=new HashSet<>(),entries=new HashSet<>(),resultParents=new HashSet<>();
        final Map<Call,Subscription> calls=new HashMap<>();
        final List<Subscription> subscribers=new ArrayList<>();
        final BitSet results=new BitSet(),pending=new BitSet();
        PendingPlan(Point point){this.point=point;}
    }
    private static final class Subscription extends Task {
        final Point caller;
        final Call call;
        final BitSet seen=new BitSet(),pending=new BitSet();
        Subscription(Point caller,Call call){this.caller=caller;this.call=call;}
    }
    private static final class Construction {
        final Map<Point,PendingPlan> plans=new HashMap<>();
        final Map<Point,Set<Point>> parents=new HashMap<>();
        final ArrayDeque<Point> discovery=new ArrayDeque<>();
        final ArrayDeque<Task> work=new ArrayDeque<>();
        final List<Exit> exitValues=new ArrayList<>();
        final Map<Exit,Integer> exitIds=new HashMap<>();
        final Function<Point,Effect> effects;
        final Delivery delivery;
        final long maxWork;
        long visits,resultPairs,resultFacts;
        Construction(Function<Point,Effect> effects,Delivery delivery,long maxWork) {
            this.effects=effects;this.delivery=delivery;this.maxWork=maxWork;
        }
        int exitId(Exit exit) {
            return exitIds.computeIfAbsent(exit,e->{int id=exitValues.size();exitValues.add(e);return id;});
        }
        void solve(List<Point> roots) {
            roots.forEach(this::ensure);
            while(!discovery.isEmpty()||!work.isEmpty()) {
                // Discovery and delivery both stay iterative, including recursion.
                while(!discovery.isEmpty()){var point=discovery.removeFirst();add(point,effects.apply(point));}
                if(work.isEmpty())continue;
                if(++visits>maxWork)throw new IllegalStateException("RESOURCE_LIMIT: control summaries exceeded --max-work="+maxWork);
                var task=work.removeFirst();task.queued=false;
                if(task instanceof PendingPlan plan) {
                    var delta=drain(plan.pending);
                    for(var parent:plan.resultParents)publish(plans.get(parent),delta);
                    for(var subscriber:plan.subscribers)offer(subscriber,delta);
                }else {
                    var subscriber=(Subscription)task;var delta=drain(subscriber.pending);
                    for(int id=delta.nextSetBit(0);id>=0;id=delta.nextSetBit(id+1)) {
                        resultPairs++;add(subscriber.caller,delivery.apply(subscriber.caller,subscriber.call,exitValues.get(id)));
                    }
                }
            }
        }
        BitSet drain(BitSet pending){var delta=(BitSet)pending.clone();pending.clear();return delta;}
        void schedule(Task task){if(!task.queued){task.queued=true;work.addLast(task);}}
        PendingPlan ensure(Point point) {
            var plan=plans.get(point);if(plan!=null)return plan;
            if(plans.size()>=maxWork)throw new IllegalStateException("RESOURCE_LIMIT: too many control summaries");
            plan=new PendingPlan(point);plans.put(point,plan);discovery.addLast(point);return plan;
        }
        void publish(PendingPlan plan,BitSet values) {
            var delta=(BitSet)values.clone();delta.andNot(plan.results);
            if(delta.isEmpty())return;
            resultFacts+=delta.cardinality();plan.results.or(delta);plan.pending.or(delta);schedule(plan);
        }
        void offer(Subscription subscriber,BitSet values) {
            var delta=(BitSet)values.clone();delta.andNot(subscriber.seen);
            if(delta.isEmpty())return;
            subscriber.seen.or(delta);subscriber.pending.or(delta);schedule(subscriber);
        }
        void parent(Point child,Point parent){parents.computeIfAbsent(child,k->new HashSet<>()).add(parent);}
        void add(Point point,Effect effect) {
            var plan=plans.get(point);var exits=new BitSet();effect.exits().forEach(e->exits.set(exitId(e)));publish(plan,exits);
            for(var next:effect.next())if(plan.next.add(next)) {
                var child=ensure(next);parent(next,point);child.resultParents.add(point);
                // A new edge must receive results that predate its registration.
                publish(plan,child.results);
            }
            for(var call:effect.calls())if(!plan.calls.containsKey(call)) {
                var child=ensure(call.entry());parent(call.entry(),point);
                var subscriber=new Subscription(point,call);plan.calls.put(call,subscriber);child.subscribers.add(subscriber);
                // Symmetric join: new caller receives old exits; old callers receive new exits.
                offer(subscriber,child.results);
            }
            for(var entry:effect.entries())if(plan.entries.add(entry)){ensure(entry);parent(entry,point);}
        }
    }
}
