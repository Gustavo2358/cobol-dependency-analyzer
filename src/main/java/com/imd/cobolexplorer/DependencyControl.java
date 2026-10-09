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
            List.copyOf(plan.next),List.copyOf(plan.entries),List.copyOf(plan.calls))));
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

    private static final class PendingPlan {
        final Set<Point> next=new HashSet<>(),entries=new HashSet<>();
        final Set<Call> calls=new HashSet<>();
        final BitSet exits=new BitSet(),results=new BitSet();
        final Map<Call,BitSet> delivered=new HashMap<>();
    }
    private static final class Construction {
        final Map<Point,PendingPlan> plans=new HashMap<>();
        final Map<Point,Set<Point>> parents=new HashMap<>();
        final Set<Point> queued=new HashSet<>();
        final ArrayDeque<Point> work=new ArrayDeque<>();
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
            roots.forEach(this::discover);
            while(!work.isEmpty()) {
                if(++visits>maxWork)throw new IllegalStateException("RESOURCE_LIMIT: control summaries exceeded --max-work="+maxWork);
                var point=work.removeFirst();queued.remove(point);var plan=plans.get(point);
                for(var call:List.copyOf(plan.calls)) {
                    var known=(BitSet)plans.get(call.entry()).results.clone();
                    var sent=plan.delivered.computeIfAbsent(call,c->new BitSet());known.andNot(sent);
                    for(int id=known.nextSetBit(0);id>=0;id=known.nextSetBit(id+1)) {
                        sent.set(id);resultPairs++;add(point,delivery.apply(point,call,exitValues.get(id)));
                    }
                }
                var exits=(BitSet)plan.exits.clone();
                for(var next:plan.next)exits.or(plans.get(next).results);
                exits.andNot(plan.results);
                if(!exits.isEmpty()) {
                    resultFacts+=exits.cardinality();plan.results.or(exits);
                    parents.getOrDefault(point,Set.of()).forEach(this::schedule);
                }
            }
        }
        void schedule(Point point){if(queued.add(point))work.addLast(point);}
        void discover(Point point) {
            if(plans.containsKey(point))return;
            plans.put(point,new PendingPlan());schedule(point);
            var pending=new ArrayDeque<Point>();pending.add(point);
            while(!pending.isEmpty()) {
                var current=pending.removeFirst();var effect=effects.apply(current);var plan=plans.get(current);
                plan.next.addAll(effect.next());plan.calls.addAll(effect.calls());plan.entries.addAll(effect.entries());
                effect.exits().forEach(e->plan.exits.set(exitId(e)));
                var children=new HashSet<>(plan.next);for(var call:plan.calls)children.add(call.entry());children.addAll(plan.entries);
                for(var child:children) {
                    parents.computeIfAbsent(child,k->new HashSet<>()).add(current);
                    if(!plans.containsKey(child)) {
                        if(plans.size()>=maxWork)throw new IllegalStateException("RESOURCE_LIMIT: too many control summaries");
                        plans.put(child,new PendingPlan());schedule(child);pending.add(child);
                    }
                }
            }
        }
        void add(Point point,Effect effect) {
            var plan=plans.get(point);effect.exits().forEach(e->plan.exits.set(exitId(e)));
            for(var next:effect.next())if(plan.next.add(next)){parents.computeIfAbsent(next,k->new HashSet<>()).add(point);discover(next);}
            for(var call:effect.calls())if(plan.calls.add(call)){parents.computeIfAbsent(call.entry(),k->new HashSet<>()).add(point);discover(call.entry());}
            for(var entry:effect.entries())if(plan.entries.add(entry)){parents.computeIfAbsent(entry,k->new HashSet<>()).add(point);discover(entry);}
        }
    }
}
