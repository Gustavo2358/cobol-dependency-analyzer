package com.imd.cobolexplorer;

import java.util.*;
import static com.imd.cobolexplorer.DependencyFlow.Exit;
import static com.imd.cobolexplorer.DependencyGraph.Scope;
import static com.imd.cobolexplorer.DependencyRelevance.Point;
import static com.imd.cobolexplorer.semanticproduct.ControlTopology.*;

/** Forward may-control over one physical graph. Bits carry boundary policies,
 * never caller environments or result columns. A boundary continues only for
 * policies which do not stop there. Values still share physical positions:
 * this does not recover caller/value or predicate correlations. */
final class DefinitionControl {
    private static final BitSet EMPTY_BITS=new BitSet();
    private static final Set<String> UNSET=Set.of("UNSET");
    private static final Map<String,Set<String>> EMPTY_HANDLERS=Map.of();
    static final class Position {
        final Exit exit;final Set<Integer> predecessors=new LinkedHashSet<>();boolean root,queued;
        BitSet policies=EMPTY_BITS;Map<String,Set<String>> handlers,output;
        Position(Exit exit){this.exit=exit;}
    }
    record Completion(int position,Exit exit) { }
    record SubscriptionKey(int caller,String binding) { }
    static final class Subscription {
        final DependencyControl.Call call;BitSet parents=EMPTY_BITS;
        Subscription(DependencyControl.Call call){this.call=call;}
    }
    final Map<Exit,Integer> positionIds=new HashMap<>();final List<Position> positions=new ArrayList<>();
    private final DependencyFlow flow;private final Runnable tick;private final ArrayDeque<Integer> work=new ArrayDeque<>();
    private final Map<Exit,DependencyControl.Rule> rules=new HashMap<>();
    private final Map<Scope,Integer> policyIds=new HashMap<>();private final List<Scope> policies=new ArrayList<>();
    private final List<BitSet> singletons=new ArrayList<>();
    private final Map<String,BitSet> endpoints=new HashMap<>(),ancestors=new HashMap<>();
    private final Map<Integer,Map<SubscriptionKey,Subscription>> subscriptions=new HashMap<>();
    private final Map<Integer,Set<Completion>> completions=new HashMap<>();
    private final DependencyFlyweight<BitSet> masks=new DependencyFlyweight<>();
    private boolean policiesFrozen;
    long edges,visits,deliveries;int policyCount;long retainedPolicyWords;
    DefinitionControl(DependencyFlow flow,Runnable tick){this.flow=flow;this.tick=tick;}
    private void step(){tick.run();visits++;}
    private int policy(Point point){
        var scope=new Scope(point.endpoint(),point.escapeScope());var old=policyIds.get(scope);if(old!=null)return old;
        if(policiesFrozen)throw new IllegalStateException("unregistered boundary policy "+scope);
        int id=policies.size();policyIds.put(scope,id);policies.add(scope);var bits=new BitSet();bits.set(id);singletons.add(bits);
        if(scope.endpoint().startsWith("boundary:"))endpoints.computeIfAbsent(scope.endpoint().substring(9),k->new BitSet()).set(id);
        String ancestor=scope.escapeScope();
        while(flow.regions.containsKey(ancestor)){
            ancestors.computeIfAbsent(ancestor,k->new BitSet()).set(id);ancestor=flow.regions.get(ancestor).parent();
        }
        return id;
    }
    private BitSet union(BitSet a,BitSet b){
        if(a==b||b.isEmpty())return a;if(a.isEmpty())return b;
        var joined=(BitSet)a.clone();joined.or(b);if(joined.equals(a))return a;if(joined.equals(b))return b;return masks.retain(joined);
    }
    private static Map<String,Set<String>> joinHandlers(Map<String,Set<String>> a,Map<String,Set<String>> b){
        if(a==null)return b;if(a==b||a.equals(b))return a;
        var result=new HashMap<String,Set<String>>();var names=new HashSet<>(a.keySet());names.addAll(b.keySet());
        for(var name:names){var values=new HashSet<>(a.getOrDefault(name,UNSET));values.addAll(b.getOrDefault(name,UNSET));result.put(name,Set.copyOf(values));}
        if(result.equals(a))return a;if(result.equals(b))return b;return Map.copyOf(result);
    }
    private int position(Exit exit){
        var old=positionIds.get(exit);if(old!=null)return old;step();int id=positions.size();positionIds.put(exit,id);positions.add(new Position(exit));return id;
    }
    private void arrive(int id,BitSet incoming,Map<String,Set<String>> handlers){
        if(incoming.isEmpty())return;var p=positions.get(id);var joined=union(p.policies,incoming);var control=joinHandlers(p.handlers,handlers);
        if(joined==p.policies&&control==p.handlers)return;p.policies=joined;p.handlers=control;
        if(!p.queued){p.queued=true;work.add(id);}
    }
    private void edge(int from,Exit exit,BitSet incoming,Map<String,Set<String>> handlers){
        if(incoming.isEmpty())return;step();int to=position(exit);if(positions.get(to).predecessors.add(from))edges++;arrive(to,incoming,handlers);
    }
    private Map<String,Set<String>> transfer(Exit exit,Map<String,Set<String>> input){
        var registrations=flow.registrations.getOrDefault(exit.reference(),List.of());var abend=flow.abendRegistrations.get(exit.reference());
        if(registrations.isEmpty()&&abend==null)return input;var out=new HashMap<>(input);
        for(var r:registrations)out.put(r.condition(),Set.of(r.action()==ConditionAction.LABEL?r.target().get(0).reference():r.action().name()));
        if(abend!=null)switch(abend.action()){
            case ACTIVATE -> {var target=abend.labelTarget().flatMap(CicsHandlerSemantics.LabelTarget::entry).map(id->Set.of("statement:"+id)).orElse(Set.of("UNKNOWN"));out.put("ABEND",target);out.put("ABEND-SAVED",target);}
            case CANCEL -> out.put("ABEND",Set.of("CANCEL"));
            case RESET -> out.put("ABEND",out.getOrDefault("ABEND-SAVED",Set.of("UNKNOWN")));
            default -> { }
        }
        return out.equals(input)?input:Map.copyOf(out);
    }
    private static Set<String> dispositions(Map<String,Set<String>> handlers,String name){
        var result=new HashSet<>(handlers.getOrDefault(name,UNSET));
        if(result.remove("UNSET")){result.addAll(handlers.getOrDefault("ERROR",UNSET));if(result.remove("UNSET"))result.add("DEFAULT");}
        return result;
    }
    private void events(int id,BitSet active,Map<String,Set<String>> handlers){
        var exit=positions.get(id).exit;
        for(var event:flow.events.getOrDefault(exit.reference(),List.of()))if(event.eligibility()==EventEligibility.HANDLER_ELIGIBLE){
            for(var handler:dispositions(handlers,event.condition())){
                if(handler.equals("IGNORE"))edge(id,Exit.of(event.continuation()),active,handlers);
                else if(flow.regions.containsKey(handler))edge(id,Exit.of(flow.regions.get(handler).entry()),active,handlers);
            }
        }
        for(var event:flow.exceptionalEvents.getOrDefault(exit.reference(),List.of()))if(event.eligibility()==EventEligibility.HANDLER_ELIGIBLE){
            if(event.origin()!=EventOrigin.EXPLICIT_ABEND&&!dispositions(handlers,"PGMIDERR").contains("DEFAULT"))continue;
            for(var handler:handlers.getOrDefault("ABEND",Set.of("UNKNOWN")))if(flow.handlerEndpoints.containsKey(handler)){
                var ingress=new HashMap<>(handlers);ingress.put("ABEND",Set.of("CANCEL"));
                var point=new Point(new Exit(TargetKind.OCCURRENCE,handler),flow.handlerEndpoints.get(handler));
                edge(id,point.exit(),singletons.get(policy(point)),Map.copyOf(ingress));
            }
        }
    }
    private BitSet candidates(Exit exit){
        return switch(exit.kind()){
            case COMPLETE,UNKNOWN_LOCAL -> endpoints.getOrDefault(exit.reference(),EMPTY_BITS);
            case ESCAPE -> union(endpoints.getOrDefault(exit.reference(),EMPTY_BITS),ancestors.getOrDefault(exit.reference(),EMPTY_BITS));
            default -> EMPTY_BITS;
        };
    }
    private void deliver(Completion completion,Subscription subscriber){
        step();deliveries++;var state=positions.get(completion.position()).output;
        for(var next:flow.definitionDelivery(subscriber.call,completion.exit()).next())edge(completion.position(),next,subscriber.parents,state);
    }
    private void call(int id,DependencyControl.Call call,BitSet active,Map<String,Set<String>> handlers){
        int policy=policy(call.entry());var key=new SubscriptionKey(id,call.binding());
        var subscriber=subscriptions.computeIfAbsent(policy,k->new LinkedHashMap<>()).computeIfAbsent(key,k->new Subscription(call));
        subscriber.parents=union(subscriber.parents,active);
        edge(id,call.entry().exit(),singletons.get(policy),handlers);
        for(var completion:completions.getOrDefault(policy,Set.of()))deliver(completion,subscriber);
    }
    void solve(List<Point> roots){
        // Register policies before masks are used as immutable facts. Indexing
        // endpoint/ancestor names avoids scanning every policy at every boundary.
        for(var binding:flow.bindings.values())policy(flow.definitionCall(binding).entry());
        for(var root:roots)policy(root);
        flow.handlerEndpoints.forEach((handler,endpoint)->policy(new Point(new Exit(TargetKind.OCCURRENCE,handler),endpoint)));
        policiesFrozen=true;
        for(var root:roots){int id=position(root.exit());positions.get(id).root=true;arrive(id,singletons.get(policy(root)),EMPTY_HANDLERS);}
        while(!work.isEmpty()){
            step();int id=work.remove();var p=positions.get(id);p.queued=false;
            var rule=rules.computeIfAbsent(p.exit,flow::definitionEffect);var handlers=transfer(p.exit,p.handlers);p.output=handlers;
            var stopping=(BitSet)candidates(p.exit).clone();stopping.and(p.policies);
            for(int policy=stopping.nextSetBit(0);policy>=0;policy=stopping.nextSetBit(policy+1)){
                var exits=rule.returns().apply(policies.get(policy));if(exits.isEmpty()){stopping.clear(policy);continue;}
                for(var exit:exits){var completion=new Completion(id,exit);completions.computeIfAbsent(policy,k->new LinkedHashSet<>()).add(completion);
                    for(var subscriber:subscriptions.getOrDefault(policy,Map.of()).values())deliver(completion,subscriber);}
            }
            BitSet active=p.policies;
            if(!stopping.isEmpty()){active=(BitSet)active.clone();active.andNot(stopping);active=masks.retain(active);}
            if(active.isEmpty())continue;
            var effect=rule.continuation();
            for(var next:effect.next())edge(id,next,active,handlers);
            for(var entry:effect.entries())edge(id,entry.exit(),singletons.get(policy(entry)),handlers);
            for(var call:effect.calls())call(id,call,active,handlers);
            events(id,active,handlers);
        }
        policyCount=policies.size();var distinct=Collections.newSetFromMap(new IdentityHashMap<BitSet,Boolean>());
        for(var p:positions)distinct.add(p.policies);retainedPolicyWords=distinct.stream().mapToLong(b->b.toLongArray().length).sum();
        System.err.printf("DEFINITIONS_CONTROL {\"policies\":%d,\"positions\":%d,\"edges\":%d,\"visits\":%d,\"deliveries\":%d,\"distinctPolicySets\":%d,\"policyWords\":%d}%n",policyCount,positions.size(),edges,visits,deliveries,distinct.size(),retainedPolicyWords);
        // Only topology is needed by reaching definitions. Release forward
        // facts and subscriptions before allocating the value equations.
        for(var p:positions){p.policies=EMPTY_BITS;p.handlers=null;p.output=null;}
        rules.clear();policyIds.clear();policies.clear();singletons.clear();endpoints.clear();ancestors.clear();subscriptions.clear();completions.clear();
    }
}
