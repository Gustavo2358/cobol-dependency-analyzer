package com.imd.cobolexplorer;

import java.util.*;
import com.imd.cobolexplorer.semanticproduct.ControlTopology;
import static com.imd.cobolexplorer.semanticproduct.ControlTopology.*;

/** Research overlay, not a production solver. May-control paths ignore predicate
 * correlations and matching PERFORM returns. Unsupported value transforms FAIL
 * explicitly; there is no fallback to the old solver. */
final class WitnessFlow {
    record Query(Ast.Statement statement,String type,Ast.Expression expression,Optional<String> literal) {}
    record Fit(int prefix,int length) {
        static final Fit ID=new Fit(Integer.MAX_VALUE,-1);
        Fit prepend(int width) {return width<=0?this:new Fit(Math.min(prefix,width),length<0?width:length);}
        String apply(String text) {var s=text.substring(0,Math.min(prefix,text.length()));return length<0?s:s+" ".repeat(Math.max(0,length-s.length()));}
    }
    record Goal(String point,int declaration,Fit fit) {}
    record Fact(int declaration,String value) {}
    record DemandKey(String type,int declaration) {}
    static final class Pending {final Set<String> values=new HashSet<>();final Set<Goal> absent=new HashSet<>();int version;}
    record Write(int target,Ast.Expression source,boolean unknown) {}
    final Map<Query,DependencyValues> answers=new LinkedHashMap<>();
    final Set<String> diagnostics=new TreeSet<>();
    final DependencyDeclarations declarations;
    final Map<String,Ast.Statement> statements=new HashMap<>();
    final Map<String,List<String>> incoming=new HashMap<>(),outgoing=new HashMap<>();
    final Map<String,List<Write>> writes=new HashMap<>();
    final Map<Integer,Set<String>> bounds=new HashMap<>();
    final Set<Integer> demand=new HashSet<>();
    final Set<String> roots=new HashSet<>(),reachable=new HashSet<>();
    final Map<String,Region> regions=new HashMap<>();
    final boolean stop=System.getProperty("witness.stop","true").equals("true");
    final boolean cache=System.getProperty("witness.cache","true").equals("true");
    long visits,queriesSearched,queriesSkipped,earlyStops,originsFound,graphEdges,boundFacts,negativeHits,negativeFacts;final long maxWork;

    static WitnessFlow analyze(CompilationUnitModel.ProgramUnit unit,DependencyDeclarations declarations,
            ControlTopology topology,List<Query> queries,long maxWork) {
        return new WitnessFlow(unit,declarations,topology,queries,maxWork);
    }
    private WitnessFlow(CompilationUnitModel.ProgramUnit unit,DependencyDeclarations declarations,ControlTopology t,List<Query> queries,long maxWork) {
        this.declarations=declarations;this.maxWork=maxWork;
        var todo=new ArrayDeque<Ast.Node>();todo.add(unit.program());
        while(!todo.isEmpty()){var n=todo.remove();if(n instanceof Ast.Program&&n!=unit.program())continue;
            if(n instanceof Ast.Statement s)statements.put(handle(s),s);todo.addAll(Ast.children(n));}
        t.regions().forEach(r->regions.put(r.id(),r));
        if(!t.fileFlows().isEmpty()||!t.exceptionalEvents().isEmpty()||!t.conditionEvents().isEmpty()||!t.conditionRegistrations().isEmpty())unsupported("event/file control");
        var bindings=new HashMap<String,Binding>();t.bindings().forEach(b->bindings.put(b.id(),b));
        for(var e:t.outcomes()) {
            if(e.kind()==OutcomeKind.UNKNOWN_LOCAL)unsupported("unknown control at "+e.statement());
            link(occurrence(e.statement()),e.kind()==OutcomeKind.LOCAL_INVOKE?phase(bindings.get(e.binding()),bindings.get(e.binding()).entryPhase()):key(e.target()));
        }
        for(var b:t.boundaries()) {link(boundary(TargetKind.COMPLETE,b.region()),key(b.ordinaryDefault()));link(boundary(TargetKind.ESCAPE,b.region()),key(b.ordinaryDefault()));}
        var endpoints=new HashMap<String,String>();t.boundaries().forEach(b->endpoints.put(b.id(),b.region()));
        for(var b:t.bindings()) {
            link(phase(b,"BODY"),key(regions.get(b.region()).entry()));link(phase(b,"RESUME"),key(b.resume()));
            for(var p:b.phases())for(var e:p.edges())link(phase(b,p.id()),phase(b,e.target()));
            var endpoint=Objects.requireNonNull(endpoints.get(b.endpoint()));
            link(boundary(TargetKind.COMPLETE,endpoint),phase(b,b.completionPhase()));
            link(boundary(TargetKind.ESCAPE,endpoint),phase(b,"RESUME"));
            // Ancestor EXIT propagates through invocations. Physical edges are
            // shared, hence cannot distinguish the active return obligation.
            var r=regions.get(b.region());String ancestor=r.parent();
            var seen=new HashSet<String>();while(regions.containsKey(ancestor)&&seen.add(ancestor)) {
                link(boundary(TargetKind.ESCAPE,ancestor),phase(b,"RESUME"));ancestor=regions.get(ancestor).parent();
            }
        }
        t.sourceContinuations().forEach(e->link(occurrence(e.statement()),key(e.target())));
        t.regions().stream().filter(r->r.kind()==RegionKind.PROCEDURE).findFirst().ifPresent(r->roots.add(key(r.entry())));
        t.entryPoints().forEach(e->roots.add(key(e.target())));
        var queue=new ArrayDeque<String>(roots);
        while(!queue.isEmpty()){tick();var n=queue.remove();if(reachable.add(n))queue.addAll(outgoing.getOrDefault(n,List.of()));}
        for(var q:queries)if(q.literal().isEmpty()&&reachable.contains(occurrence(handle(q.statement()))))demand.add(scalar(q.expression()));
        boolean changed;
        do {changed=false;for(var s:statements.values())if(s instanceof Ast.MoveStatement m)for(var target:m.targets()) {
            var refs=declarations.reads(target);if(!Collections.disjoint(refs,demand)) {
                if(m.corresponding())unsupported("MOVE CORRESPONDING");int dst=scalar(target);
                if(m.source() instanceof Ast.DataReference)changed|=demand.add(scalar(m.source()));else literal(m.source());
                demand.add(dst);
            }
        }}while(changed);
        for(var id:demand) {
            if(!declarations.related(id).equals(Set.of(id)))unsupported("aliases/group views of "+id);
            var entry=declarations.entries.get(id);
            if(declarations.numeric.contains(id)||declarations.repeated.contains(id)||!declarations.children.getOrDefault(id,List.of()).isEmpty())unsupported("non-scalar text declaration "+id);
            var seed=entry.clauses().stream().filter(Ast.ValueClause.class::isInstance).map(Ast.ValueClause.class::cast).findFirst();
            seed.flatMap(Ast.ValueClause::logicalText).ifPresent(v->bounds.computeIfAbsent(id,k->new HashSet<>()).add(fit(id,v.value())));
            if(seed.isPresent()&&seed.get().logicalText().isEmpty())unsupported("non-text VALUE "+id);
        }
        for(var s:statements.values()) {
            String point=occurrence(handle(s));var ws=new ArrayList<Write>();
            if(s instanceof Ast.MoveStatement m)for(var target:m.targets())if(!Collections.disjoint(declarations.reads(target),demand))ws.add(new Write(scalar(target),m.source(),false));
            if(!(s instanceof Ast.MoveStatement)) {
                var effect=StatementEffectSummary.of(s);
                if(effect.isPresent()) {
                    var e=effect.get();if(e.unknownWriteBound()==StatementEffectSummary.Bound.ALL&&!demand.isEmpty())unsupported("unbounded mutation at "+handle(s));
                    for(var target:e.mayWrites())if(!Collections.disjoint(declarations.reads(target),demand)) {
                        if(e.proof()!=StatementEffectSummary.Proof.ACCEPT_TARGET)unsupported("value transform "+e.proof()+" at "+handle(s));
                        ws.add(new Write(scalar(target),null,true));
                    }
                }else if(!(s instanceof Ast.GobackStatement||s instanceof Ast.CallStatement||s instanceof Ast.IfStatement||s instanceof Ast.EvaluateStatement||s instanceof Ast.GoToStatement||s instanceof Ast.PerformStatement)) {
                    if(!(s instanceof Ast.ModeledStatement m&&m.exitKind().isPresent()))unsupported("unmodeled statement "+s.getClass().getSimpleName());
                }
                if(s instanceof Ast.CallStatement c&&c.returning()!=null&&!Collections.disjoint(declarations.reads(c.returning()),demand))unsupported("CALL RETURNING");
            }
            if(!ws.isEmpty())writes.put(point,List.copyOf(ws));
        }
        buildBounds();
        var sorted=new ArrayList<>(queries);sorted.sort(Comparator.comparing((Query q)->q.statement().meta().provenance().original().file()).thenComparingInt(q->q.statement().meta().provenance().original().startLine()).thenComparingInt(q->q.statement().meta().id()));
        var found=new HashSet<String>();var foundOrder=new HashMap<String,List<String>>();var pendingByDemand=new HashMap<DemandKey,Pending>();
        for(var q:sorted) {
            if(!reachable.contains(occurrence(handle(q.statement()))))continue;
            if(q.literal().isPresent()) {
                var name=q.literal().get();answers.put(q,DependencyValues.known(name));if(found.add(q.type()+"\0"+name.stripTrailing()))foundOrder.computeIfAbsent(q.type(),k->new ArrayList<>()).add(name.stripTrailing());continue;
            }
            int declaration=scalar(q.expression());Set<String> pending;Set<Goal> absent=Set.of();
            if(stop) {
                var p=pendingByDemand.computeIfAbsent(new DemandKey(q.type(),declaration),k->{var x=new Pending();bounds.getOrDefault(declaration,Set.of()).forEach(v->x.values.add(v.stripTrailing()));return x;});
                var order=foundOrder.getOrDefault(q.type(),List.of());while(p.version<order.size())p.values.remove(order.get(p.version++));pending=p.values;if(cache)absent=p.absent;
            }else pending=new HashSet<>();
            if(stop&&pending.isEmpty()){queriesSkipped++;answers.put(q,DependencyValues.UNKNOWN);continue;}
            queriesSearched++;var result=search(q,declaration,pending,absent);
            answers.put(q,new DependencyValues(result,true));for(var value:result)if(found.add(q.type()+"\0"+value.stripTrailing()))foundOrder.computeIfAbsent(q.type(),k->new ArrayList<>()).add(value.stripTrailing());
        }
        diagnostics.add("EXPERIMENT_APPROXIMATE: predicate correlations and matching PERFORM returns relaxed; unknown remainder retained conservatively");
        System.err.printf(Locale.ROOT,"WITNESS {\"stop\":%s,\"visits\":%d,\"nodes\":%d,\"edges\":%d,\"boundFacts\":%d,\"queriesSearched\":%d,\"queriesSkipped\":%d,\"earlyStops\":%d,\"originsFound\":%d,\"negativeHits\":%d,\"negativeFacts\":%d}%n",stop,visits,reachable.size(),graphEdges,boundFacts,queriesSearched,queriesSkipped,earlyStops,originsFound,negativeHits,negativeFacts);
    }
    private Set<String> search(Query q,int declaration,Set<String> pending,Set<Goal> absent) {
        var seen=new HashSet<Goal>();var todo=new ArrayDeque<Goal>();var result=new HashSet<String>();
        todo.add(new Goal(occurrence(handle(q.statement())),declaration,Fit.ID));boolean exhausted=true;
        while(!todo.isEmpty()) {
            tick();var g=todo.remove();if(absent.contains(g)){negativeHits++;continue;}if(!seen.add(g))continue;
            if(roots.contains(g.point()))for(var value:initial(g.declaration()))record(value,g.fit(),pending,result);
            if(stop&&pending.isEmpty()){earlyStops++;exhausted=false;break;}
            for(var predecessor:incoming.getOrDefault(g.point(),List.of())) {
                if(!reachable.contains(predecessor))continue;
                var write=writes.getOrDefault(predecessor,List.of()).stream().filter(w->w.target()==g.declaration()).findFirst();
                if(write.isEmpty())todo.add(new Goal(predecessor,g.declaration(),g.fit()));
                else if(write.get().unknown())todo.add(new Goal(predecessor,g.declaration(),g.fit()));
                else {
                    var w=write.get();var nextFit=g.fit().prepend(declarations.widths.getOrDefault(w.target(),0));
                    if(w.source() instanceof Ast.DataReference)todo.add(new Goal(predecessor,scalar(w.source()),nextFit));
                    else record(literal(w.source()),nextFit,pending,result);
                }
            }
            if(stop&&pending.isEmpty()){earlyStops++;exhausted=false;break;}
        }
        // Only an exhausted traversal proves absence. All still-pending names
        // are absent from every visited goal; pending only SHRINKS for this
        // (dependency type, root declaration), so these certificates remain
        // valid for later CALLs. Never cache failure from a stopped traversal.
        if(stop&&cache&&exhausted){int old=absent.size();absent.addAll(seen);negativeFacts+=absent.size()-old;}
        return result;
    }
    private void record(String value,Fit fit,Set<String> pending,Set<String> result){originsFound++;var v=fit.apply(value);if(!stop||pending.remove(v.stripTrailing()))result.add(v);}
    private Set<String> initial(int id){return declarations.entries.get(id).clauses().stream().filter(Ast.ValueClause.class::isInstance).map(Ast.ValueClause.class::cast).flatMap(c->c.logicalText().stream()).map(v->fit(id,v.value())).collect(java.util.stream.Collectors.toSet());}
    private void buildBounds() {
        var copies=new HashMap<Integer,List<Integer>>();var todo=new ArrayDeque<Fact>();
        for(var ws:writes.values())for(var w:ws)if(!w.unknown()) {
            if(w.source() instanceof Ast.DataReference)copies.computeIfAbsent(scalar(w.source()),k->new ArrayList<>()).add(w.target());
            else bounds.computeIfAbsent(w.target(),k->new HashSet<>()).add(fit(w.target(),literal(w.source())));
        }
        bounds.forEach((id,vs)->vs.forEach(v->todo.add(new Fact(id,v))));
        while(!todo.isEmpty()){tick();var f=todo.remove();boundFacts++;for(var dst:copies.getOrDefault(f.declaration(),List.of())) {
            var value=fit(dst,f.value());if(bounds.computeIfAbsent(dst,k->new HashSet<>()).add(value))todo.add(new Fact(dst,value));
        }}
    }
    private int scalar(Ast.Expression e) {
        if(!(e instanceof Ast.DataReference r))throw new IllegalArgumentException("UNSUPPORTED_EXPERIMENT: non-reference target");
        if(!r.subscriptGroups().isEmpty()||r.referenceModification()!=null)unsupported("indexed/partial reference");
        var id=declarations.references.get(r.meta().id());if(id==null)unsupported("unbound reference "+r.writtenText());return id;
    }
    private String literal(Ast.Expression e) {
        if(e instanceof Ast.LiteralExpression l&&l.logicalText().isPresent())return l.logicalText().get().value();
        throw new IllegalArgumentException("UNSUPPORTED_EXPERIMENT: non-text expression");
    }
    private String fit(int id,String value){return Fit.ID.prepend(declarations.widths.getOrDefault(id,0)).apply(value);}
    private String key(Target t) {
        var seen=new HashSet<String>();while(t.kind()==TargetKind.REGION_ENTRY){if(!seen.add(t.reference()))unsupported("cyclic region");t=regions.get(t.reference()).entry();}
        return boundary(t.kind(),t.reference());
    }
    private static String occurrence(String reference){return boundary(TargetKind.OCCURRENCE,reference);}
    private static String boundary(TargetKind kind,String reference){return kind+":"+reference;}
    private static String phase(Binding b,String p){return occurrence("phase/"+b.id()+"/"+p);}
    private static String handle(Ast.Statement s){return "statement:"+s.meta().id();}
    private void link(String from,String to){outgoing.computeIfAbsent(from,k->new ArrayList<>()).add(to);incoming.computeIfAbsent(to,k->new ArrayList<>()).add(from);graphEdges++;}
    private void tick(){if(++visits>maxWork)throw new IllegalStateException("RESOURCE_LIMIT: witness search exceeded --max-work="+maxWork);}
    private static void unsupported(String reason){throw new IllegalArgumentException("UNSUPPORTED_EXPERIMENT: "+reason);}
}
