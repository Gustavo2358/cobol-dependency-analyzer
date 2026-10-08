package com.imd.cobolexplorer;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

/** Backward input demand over shared control. An invocation enters its own
 * endpoint; its caller's resume remains in the caller's scope. Full writes
 * kill only proven overwritten inputs; conditional writes retain passthrough
 * inputs before results join. Dense bit sets avoid copying sets of
 * declarations at every edge. The graph is immutable and extended lazily. */
final class DependencyRelevance {
    sealed interface Input permits Value,Handler,Fact { }
    record Value(int declaration) implements Input { }
    record Handler(String name) implements Input { }
    record Fact(String statement) implements Input { }
    record Point(DependencyFlow.Exit exit,String endpoint) { }
    record Effect(Set<Input> reads,Set<Input> kills,List<Point> successors) {
        Effect {reads=Set.copyOf(reads);kills=Set.copyOf(kills);successors=List.copyOf(successors);}
    }
    private record Node(BitSet reads,BitSet kills,List<Point> successors) { }
    private record Overwrites(BitSet possible,BitSet definite) { }
    private final Function<Point,Effect> effects;
    private final long maxWork;
    private long work;
    private final Map<Input,Integer> slots=new HashMap<>();
    private final List<Input> inputs=new ArrayList<>();
    private final Map<Point,Node> nodes=new HashMap<>();
    private final Map<Point,BitSet> needs=new HashMap<>();
    private final Map<Point,Overwrites> overwrites=new HashMap<>();
    private final Map<Point,Set<Input>> decoded=new HashMap<>();
    private final Map<Point,Set<Point>> predecessors=new HashMap<>();
    private final Map<Point,Integer> components=new HashMap<>();
    private int nextComponent;

    DependencyRelevance(Function<Point,Effect> effects,long maxWork) {this.effects=effects;this.maxWork=maxWork;}
    Set<Input> needed(Point entry) {
        if(!nodes.containsKey(entry))extend(entry);
        return decoded.computeIfAbsent(entry,key->{
            var result=new HashSet<Input>();var bits=needs.get(key);
            for(int i=bits.nextSetBit(0);i>=0;i=bits.nextSetBit(i+1))result.add(inputs.get(i));
            return Set.copyOf(result);
        });
    }
    boolean sameComponent(Point a,Point b) {
        needed(a);needed(b);return components.get(a).equals(components.get(b));
    }
    private BitSet encode(Set<Input> values) {
        var bits=new BitSet();for(var value:values) {
            Integer slot=slots.get(value);
            if(slot==null){slot=inputs.size();slots.put(value,slot);inputs.add(value);}
            bits.set(slot);
        }return bits;
    }
    private void tick() {
        if(++work>maxWork)throw new IllegalStateException("RESOURCE_LIMIT: input relevance exceeded --max-work="+maxWork);
    }
    private void extend(Point entry) {
        var added=new HashSet<Point>();var pending=new ArrayDeque<Point>();pending.add(entry);
        while(!pending.isEmpty()) {
            var point=pending.removeFirst();if(nodes.containsKey(point))continue;tick();
            var effect=effects.apply(point);var node=new Node(encode(effect.reads()),encode(effect.kills()),effect.successors());
            nodes.put(point,node);needs.put(point,new BitSet());
            overwrites.put(point,new Overwrites(new BitSet(),new BitSet()));added.add(point);
            for(var child:node.successors()) {
                predecessors.computeIfAbsent(child,k->new HashSet<>()).add(point);pending.add(child);
            }
        }
        // Successors precede parents. Acyclic chains need a single pass rather
        // than propagating each newly discovered operand through every prefix.
        var order=new ArrayList<Point>();var seen=new HashSet<Point>();
        record Visit(Point point,Iterator<Point> successors) { }
        var stack=new ArrayDeque<Visit>();
        for(var root:added)if(seen.add(root)) {
            stack.push(new Visit(root,nodes.get(root).successors().iterator()));
            while(!stack.isEmpty()) {
                var top=stack.peek();
                if(top.successors().hasNext()) {
                    var child=top.successors().next();
                    if(added.contains(child)&&seen.add(child))stack.push(new Visit(child,nodes.get(child).successors().iterator()));
                }else{order.add(top.point());stack.pop();}
            }
        }
        // Existing nodes have a closed successor graph: no existing component
        // can acquire a back edge to this extension. Their IDs stay valid.
        for(int i=order.size()-1;i>=0;i--) {
            var root=order.get(i);if(components.containsKey(root))continue;
            int component=nextComponent++;components.put(root,component);pending.add(root);
            while(!pending.isEmpty())for(var parent:predecessors.getOrDefault(pending.removeFirst(),Set.of()))
                if(added.contains(parent)&&!components.containsKey(parent)){components.put(parent,component);pending.add(parent);}
        }
        // A conditional overwrite also returns the original value on its
        // untouched path. Keep that input before joining results, regardless of
        // which branch happens to reach the worklist first. Invocation/resume
        // edges conservatively intersect guarantees, without assuming a callee
        // overwrites a value on every returning path.
        propagate(order,point->{
            var node=nodes.get(point);var possible=(BitSet)node.kills().clone();BitSet definite=null;
            for(var child:node.successors()) {
                var writes=overwrites.get(child);possible.or(writes.possible());
                if(definite==null)definite=(BitSet)writes.definite().clone();else definite.and(writes.definite());
            }
            if(definite==null)definite=new BitSet();definite.or(node.kills());
            var old=overwrites.get(point);
            if(possible.equals(old.possible())&&definite.equals(old.definite()))return false;
            overwrites.put(point,new Overwrites(possible,definite));return true;
        });
        propagate(order,point->{
            var node=nodes.get(point);var next=new BitSet();
            for(var child:node.successors())next.or(needs.get(child));
            next.andNot(node.kills());next.or(node.reads());
            var writes=overwrites.get(point);var passthrough=(BitSet)writes.possible().clone();
            passthrough.andNot(writes.definite());next.or(passthrough);
            if(next.equals(needs.get(point)))return false;
            needs.put(point,next);decoded.remove(point);return true;
        });
    }
    private void propagate(List<Point> order,Predicate<Point> update) {
        var pending=new ArrayDeque<>(order);var scheduled=new HashSet<>(order);
        while(!pending.isEmpty()) {
            tick();var point=pending.removeFirst();scheduled.remove(point);
            if(!update.test(point))continue;
            for(var parent:predecessors.getOrDefault(point,Set.of()))if(scheduled.add(parent))pending.addLast(parent);
        }
    }
}
