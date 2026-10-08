package com.imd.cobolexplorer;

import java.util.*;
import java.math.BigDecimal;
import static com.imd.cobolexplorer.semanticproduct.ControlTopology.*;
import com.imd.cobolexplorer.semanticproduct.ControlTopology;
import static com.imd.cobolexplorer.DependencyRelevance.*;

/** Worklist over shared COBOL control and memoized local invocation results.
 * Environments contain only the backward closure of dependency operands and
 * control predicates. No call-stack or execution-path enumeration. */
final class DependencyFlow {
    record Query(Ast.Statement statement,String type,Ast.Expression expression,Optional<String> literal) { }
    record State(Map<Integer,DependencyValues> values,Map<String,Set<String>> handlers,Set<String> reached) {
        State { values=DependencyEnvironment.copyOf(values);handlers=Map.copyOf(handlers);reached=Set.copyOf(reached); }
        DependencyValues get(int id){return values.getOrDefault(id,DependencyValues.UNKNOWN);}
        State withValues(Map<Integer,DependencyValues> values){return new State(values,handlers,reached);}
        State withHandlers(Map<String,Set<String>> handlers){return new State(values,handlers,reached);}
        State join(State other) {
            if(this==other||equals(other))return this;
            var out=DependencyEnvironment.copyOf(values).join(other.values);
            var h=new HashMap<>(handlers);other.handlers.forEach((k,v)->h.merge(k,v,(x,y)->union(x,y)));
            return new State(out,h,union(reached,other.reached));
        }
    }
    private static <T> Set<T> union(Set<T> a,Set<T> b) {
        if(a.containsAll(b))return a;if(b.containsAll(a))return b;
        var result=new HashSet<>(a);result.addAll(b);return Set.copyOf(result);
    }
    /** Exits are results of control, independent of a caller's return policy. */
    record Exit(TargetKind kind,String reference) {
        static Exit of(Target target){return new Exit(target.kind(),target.reference());}
    }
    record SummaryKey(Exit entry,String endpoint,State input) { }
    record Location(int context,String node,Exit result) {
        Location(int context,String node){this(context,node,null);}
    }
    sealed interface Continuation permits Forward,ReturnTo,Entry {
        String endpoint();State input();
    }
    record Forward(int context,String endpoint,State input) implements Continuation { }
    record ReturnTo(int context,String binding,String endpoint,State input) implements Continuation { }
    record Entry(String endpoint,State input) implements Continuation { }
    static final class Context {
        final SummaryKey key;
        final Set<Continuation> callers=new HashSet<>();
        final Map<Exit,State> results=new HashMap<>();
        Context(SummaryKey key){this.key=key;}
    }
    final DependencyDeclarations declarations;
    final ScalarPredicateSemantics predicates;
    final Map<Integer,Ast.Expression> expressions=new HashMap<>();
    final IdentityHashMap<Ast.Expression,com.imd.cobolexplorer.semanticproduct.ConditionNames.Tree> normalized=new IdentityHashMap<>();
    final IdentityHashMap<Ast.Expression,Boolean> abbreviated=new IdentityHashMap<>();
    final Map<String,Ast.Statement> statements=new HashMap<>();
    final Map<String,List<Outcome>> edges=new HashMap<>();
    final Map<String,Region> regions=new HashMap<>();
    final Map<String,Boundary> boundaries=new HashMap<>();
    final Map<String,Binding> bindings=new HashMap<>();
    final Map<String,List<SourceContinuation>> possibilities=new HashMap<>();
    final Map<String,List<ConditionRegistration>> registrations=new HashMap<>();
    final Map<String,List<ConditionEvent>> events=new HashMap<>();
    final Map<String,List<ExceptionalEvent>> exceptionalEvents=new HashMap<>();
    final Map<String,CicsHandlerSemantics.Fact> abendRegistrations=new HashMap<>();
    final Map<String,String> handlerEndpoints=new HashMap<>();
    final Map<String,FilePoint> filePoints=new HashMap<>();
    final Map<String,List<Binding>> ioDeclarations=new HashMap<>();
    final IdentityHashMap<Ast.Statement,Set<Integer>> writeCache=new IdentityHashMap<>();
    final List<Query> queries;
    final Set<Integer> demand=new HashSet<>();
    final Set<Integer> textGroups=new HashSet<>();
    record Element(int declaration,List<Integer> subscripts) { Element {subscripts=List.copyOf(subscripts);} }
    final Map<Element,Integer> elements=new HashMap<>();
    final Map<Integer,List<Integer>> elementIds=new HashMap<>();
    int nextElementId;
    final List<Context> contexts=new ArrayList<>();
    final Map<SummaryKey,Integer> memo=new HashMap<>();
    final Set<String> paragraphEntries=new HashSet<>();
    final Set<String> prerequisites=new HashSet<>();
    final DependencyRelevance relevance;
    final Map<Point,Selection> projections=new HashMap<>();
    final Map<String,Selection> operands=new HashMap<>();
    record Computation(String node,State input) { }
    final Map<Computation,List<Action>> computations=new HashMap<>();
    long evaluations,reusedEvaluations;
    /** A local transformation reads only its operands and preserves the rest of
     * each caller. Explicit writes, including UNKNOWN, are returned as patches. */
    record Selection(DependencyEnvironment.Projection values,Set<String> handlers,Set<String> facts) {
        static Selection of(Set<Input> inputs) {
            var values=new HashSet<Integer>();var handlers=new HashSet<String>();var facts=new HashSet<String>();
            for(var input:inputs) {
                if(input instanceof Value value)values.add(value.declaration());
                else if(input instanceof Handler handler)handlers.add(handler.name());
                else if(input instanceof Fact fact)facts.add(fact.statement());
            }
            return new Selection(new DependencyEnvironment.Projection(values),Set.copyOf(handlers),Set.copyOf(facts));
        }
        State apply(State input) {
            var selectedHandlers=new HashMap<String,Set<String>>();var selectedFacts=new HashSet<String>();
            for(String name:handlers)if(input.handlers().containsKey(name))selectedHandlers.put(name,input.handlers().get(name));
            for(String fact:facts)if(input.reached().contains(fact))selectedFacts.add(fact);
            return new State(values.apply(input.values()),selectedHandlers,selectedFacts);
        }
    }
    record Patch(State result,Set<Integer> removed) {
        static Patch of(State input,State result) {
            var removed=new HashSet<>(input.values().keySet());removed.removeAll(result.values().keySet());
            var values=new HashMap<Integer,DependencyValues>();var handlers=new HashMap<String,Set<String>>();
            result.values().forEach((id,value)->{if(!value.equals(input.values().get(id)))values.put(id,value);});
            result.handlers().forEach((name,value)->{if(!value.equals(input.handlers().get(name)))handlers.put(name,value);});
            var reached=new HashSet<>(result.reached());reached.removeAll(input.reached());
            return new Patch(new State(values,handlers,reached),Set.copyOf(removed));
        }
        State apply(State input) {
            if(removed.isEmpty()&&result.values().isEmpty()&&result.handlers().isEmpty()&&result.reached().isEmpty())return input;
            var values=new DependencyEnvironment.Builder(input.values());removed.forEach(values::remove);values.putAll(result.values());
            var handlers=new HashMap<>(input.handlers());handlers.putAll(result.handlers());
            return new State(values,handlers,union(input.reached(),result.reached()));
        }
    }
    sealed interface Action permits Advance,Phase,Enter { Patch patch(); }
    record Advance(Exit target,Patch patch) implements Action { }
    record Phase(String binding,String phase,Patch patch) implements Action { }
    record Enter(Exit entry,String endpoint,Patch patch) implements Action { }
    final Map<Exit,Effect> accessEffects=new HashMap<>();
    final Map<Location,State> before=new HashMap<>();
    final ArrayDeque<Location> work=new ArrayDeque<>();
    final Set<Location> queued=new HashSet<>();
    final Set<String> diagnostics=new TreeSet<>();
    final Map<Query,DependencyValues> answers=new LinkedHashMap<>();
    final long maxWork;
    long visits;

    DependencyFlow(CompilationUnitModel.ProgramUnit unit,DependencyDeclarations declarations,
            ControlTopology control,List<Query> queries,long maxWork,CicsProgramControlAnalyzer.Contribution cics,Map<Integer,ConditionNameSemantics.Use> conditions) {
        this.declarations=declarations;this.queries=queries;this.maxWork=maxWork;
        predicates=new ScalarPredicateSemantics(conditions,declarations.references.keySet());
        var todo=new ArrayDeque<Ast.Node>();todo.add(unit.program());
        while(!todo.isEmpty()){var node=todo.removeFirst();if(node instanceof Ast.Program&&node!=unit.program())continue;
            if(node instanceof Ast.Expression e)expressions.put(e.meta().id(),e);
            if(node instanceof Ast.Statement s)statements.put(handle(s),s);todo.addAll(Ast.children(node));}
        control.outcomes().forEach(o->edges.computeIfAbsent(o.statement(),k->new ArrayList<>()).add(o));
        control.regions().forEach(r->regions.put(r.id(),r));control.boundaries().forEach(b->boundaries.put(b.region(),b));
        control.bindings().forEach(b->bindings.put(b.id(),b));
        control.sourceContinuations().forEach(s->possibilities.computeIfAbsent(s.statement(),k->new ArrayList<>()).add(s));
        control.conditionRegistrations().forEach(r->registrations.computeIfAbsent(r.statement(),k->new ArrayList<>()).add(r));
        control.conditionEvents().forEach(e->events.computeIfAbsent(e.statement(),k->new ArrayList<>()).add(e));
        control.exceptionalEvents().forEach(e->exceptionalEvents.computeIfAbsent(e.statement(),k->new ArrayList<>()).add(e));
        for(var s:statements.values())cics.handlerFact(unit.id(),s.meta().id()).ifPresent(f->{
            abendRegistrations.put(handle(s),f);
            f.labelTarget().flatMap(CicsHandlerSemantics.LabelTarget::entry).ifPresent(entry->{
                String region=control.occurrences().stream().filter(o->o.statement().equals("statement:"+entry)).map(Occurrence::region).findFirst().orElse("");
                while(regions.containsKey(region)&&regions.get(region).kind()!=RegionKind.PARAGRAPH)region=regions.get(region).parent();
                handlerEndpoints.put("statement:"+entry,"boundary:"+region);
            });
        });
        control.fileFlows().forEach(f->f.points().forEach(p->filePoints.put(p.id(),p)));
        var sections=new ArrayDeque<Ast.Node>();sections.add(unit.program());
        while(!sections.isEmpty()) {var n=sections.removeFirst();if(n instanceof Ast.Program&&n!=unit.program())continue;
            if(n instanceof Ast.Section section)for(var child:section.children())if(child instanceof Ast.UseClause use&&use.kind()==Ast.UseKind.AFTER_EXCEPTION) {
                String region="region:declarative:"+section.meta().id();
                if(!regions.containsKey(region))continue;
                for(var statement:statements.values()) {
                    var io=fileSurface(statement);if(io.isEmpty())continue;
                    boolean applicable=io.get().files().stream().anyMatch(f->use.files().isEmpty()
                        ?use.mode()==Ast.FileOpenMode.UNSPECIFIED||f.mode()==Ast.FileOpenMode.UNSPECIFIED||use.mode()==f.mode()
                        :use.files().stream().anyMatch(r->Objects.equals(declarations.references.get(r.meta().id()),declarations.references.get(f.reference().meta().id()))&&declarations.references.containsKey(r.meta().id())));
                    if(!applicable)continue;
                    var successor=edges.getOrDefault(handle(statement),List.of()).stream().filter(e->e.kind()==OutcomeKind.NORMAL).map(Outcome::target).findFirst();
                    if(successor.isEmpty())continue;
                    String id="io-use/"+handle(statement)+"/"+section.meta().id();
                    var binding=new Binding(id,handle(statement),region,"boundary:"+region,successor.get(),"BODY","RESUME",List.of(),regions.get(region).proofs(),ReentryPolicy.SOURCE_UNDEFINED);
                    bindings.put(id,binding);ioDeclarations.computeIfAbsent(handle(statement),k->new ArrayList<>()).add(binding);
                }
            }sections.addAll(Ast.children(n));
        }
        for(var q:queries)demand.addAll(declarations.reads(q.expression()));
        // Control reads matter even when a branch chooses literal dependency sites.
        for(var s:statements.values()) {
            if(s instanceof Ast.IfStatement f)demand.addAll(declarations.reads(f.condition()));
            if(s instanceof Ast.EvaluateStatement e){e.subjects().forEach(x->demand.addAll(declarations.reads(x)));e.branches().forEach(b->b.selectors().forEach(x->demand.addAll(declarations.reads(x.expression()))));}
            if(s instanceof Ast.PerformStatement p)p.controls().forEach(c->demand.addAll(declarations.reads(c.expression())));
            if(s instanceof Ast.GoToStatement g)demand.addAll(declarations.reads(g.dependingOn()));
        }
        boolean changed;
        do {changed=false;
            for(var s:statements.values()) {
                var writes=writes(s);
                if(!Collections.disjoint(writes,demand)) {
                    if(s instanceof Ast.MoveStatement m)changed|=demand.addAll(declarations.reads(m.source()));
                    else changed|=demand.addAll(declarations.reads(s));
                }
            }
            var old=new ArrayList<>(demand);for(int id:old)changed|=demand.addAll(declarations.related(id));
        }while(changed);
        // Keep whole logical text alternatives at joins. Splitting a branch's
        // group into independent field sets would invent Cartesian combinations.
        var textReads=new ArrayList<Ast.Node>();
        queries.stream().map(Query::expression).filter(Objects::nonNull).forEach(textReads::add);
        for(var statement:statements.values())if(statement instanceof Ast.MoveStatement move&&!Collections.disjoint(writes(move),demand))textReads.add(move.source());
        for(var node:textReads) {
            var pending=new ArrayDeque<Ast.Node>();pending.add(node);
            while(!pending.isEmpty()) {var n=pending.removeFirst();
                if(n instanceof Ast.DataReference r) {Integer id=declarations.references.get(r.meta().id());
                    if(id!=null&&!declarations.children.getOrDefault(id,List.of()).isEmpty()&&!Collections.disjoint(declarations.leaves(id),demand))textGroups.add(id);
                }pending.addAll(Ast.children(n));
            }
        }
        for(int id:declarations.textualViews.keySet())if(!Collections.disjoint(declarations.leaves(id),demand))textGroups.add(id);
        demand.addAll(textGroups);
        // Paragraph entries are natural control joins, regardless of the source
        // construct that reaches them. They all use the same summary mechanism.
        for(var r:regions.values())if(r.kind()==RegionKind.PARAGRAPH) {
            var entry=normalize(Exit.of(r.entry()));
            if(entry.kind()==TargetKind.OCCURRENCE)paragraphEntries.add(entry.reference());
        }
        possibilities.values().forEach(ps->ps.forEach(p->prerequisites.addAll(p.prerequisites())));
        nextElementId=declarations.entries.keySet().stream().mapToInt(Integer::intValue).max().orElse(0)+1;
        for(int id:new ArrayList<>(demand))if(declarations.repeated.contains(id)&&declarations.children.getOrDefault(id,List.of()).isEmpty()) {
            var dimensions=new ArrayList<Integer>();Integer ancestor=id;
            while(ancestor!=null) {if(declarations.entries.get(ancestor).clauses().stream().anyMatch(Ast.OccursClause.class::isInstance))dimensions.add(0,declarations.counts.getOrDefault(ancestor,0));ancestor=declarations.parent.get(ancestor);}
            if(!dimensions.contains(0))indexElements(id,dimensions,0,List.of());
        }
        relevance=new DependencyRelevance(this::inputEffect,maxWork);
        State initial=initial();
        control.regions().stream().filter(r->r.kind()==RegionKind.PROCEDURE).findFirst().ifPresent(r->{
            String endpoint="boundary:"+r.id();
            subscribe(new Entry(endpoint,initial),Exit.of(r.entry()));
            for(var e:control.entryPoints())subscribe(new Entry(endpoint,initial),Exit.of(e.target()));
        });
        while(!work.isEmpty()) {
            if(++visits>maxWork)throw new IllegalStateException("RESOURCE_LIMIT: dataflow did not complete within --max-work="+maxWork);
            var location=work.removeFirst();queued.remove(location);
            if(location.result()!=null) {
                var ctx=contexts.get(location.context());
                for(var caller:List.copyOf(ctx.callers))deliver(caller,location.result(),ctx.results.get(location.result()));
            }else step(location,before.get(location));
        }
        var queriesAt=new HashMap<String,List<Query>>();
        for(var q:queries)queriesAt.computeIfAbsent(handle(q.statement()),k->new ArrayList<>()).add(q);
        for(var e:before.entrySet())for(var q:queriesAt.getOrDefault(e.getKey().node(),List.of())) {
            var value=q.literal().map(DependencyValues::known).orElseGet(()->read(q.expression(),e.getValue()));
            answers.merge(q,value,DependencyValues::join);
        }
        for(var q:queries)if(answers.containsKey(q)&&answers.get(q).unknown())diagnostics.add("DYNAMIC_REMAINDER at "+q.statement().meta().provenance().original().file()+":"+q.statement().meta().provenance().original().startLine());
    }
    static String handle(Ast.Statement s){return "statement:"+s.meta().id();}
    private State initial() {
        var out=new HashMap<Integer,DependencyValues>();demand.stream().filter(id->!textGroups.contains(id)).forEach(id->out.put(id,DependencyValues.UNKNOWN));
        State state=new State(out,Map.of(),Set.of());
        for(var d:declarations.entries.values())if(demand.stream().anyMatch(declarations.related(d.meta().id())::contains)) {
            var value=d.clauses().stream().filter(Ast.ValueClause.class::isInstance).map(Ast.ValueClause.class::cast).findFirst();
            if(value.isEmpty()||d.levelKind()==Ast.DataLevelKind.CONDITION_88||d.meta().syntheticModel())continue;
            var text=value.get().logicalText().map(Ast.LogicalText::value).or(()->value.get().ranges().stream().findFirst().map(Ast.ConditionRange::first).flatMap(this::conditionText));
            if(text.isPresent()) {
                var seed=DependencyValues.known(text.get()).open();
                state=writeLocal(d.meta().id(),seed,state,false);
                if(!declarations.children.getOrDefault(d.meta().id(),List.of()).isEmpty()) {
                    state=decode(d.meta().id(),seed.fit(declarations.widths.getOrDefault(d.meta().id(),0)),state,0,false);
                    if(textGroups.contains(d.meta().id())) {
                        var groups=new DependencyEnvironment.Builder(state.values());
                        groups.put(d.meta().id(),seed.fit(declarations.widths.getOrDefault(d.meta().id(),0)));state=state.withValues(groups);
                    }
                }
                if(!elementIds.getOrDefault(d.meta().id(),List.of()).isEmpty()) {
                    var outValues=new DependencyEnvironment.Builder(state.values());
                    for(int slot:elementIds.get(d.meta().id()))outValues.put(slot,state.get(d.meta().id()));state=state.withValues(outValues);
                }
            }
        }
        // Declarative textual tables can expose a different, repeated logical
        // structure. Slice only characters actually supported by the declarations.
        for(var view:declarations.textualViews.entrySet()) {
            var value=readDeclaration(view.getKey(),state);
            if(!value.values().isEmpty())for(int alias:view.getValue())state=decode(alias,value,state,0,false);
        }
        var snapshots=new DependencyEnvironment.Builder(state.values());for(int id:textGroups) {
            var value=readDeclaration(id,state);
            if(value.values().isEmpty()) {
                var seed=singletonTableText(id,state,List.of(),false);
                if(seed.isPresent())value=new DependencyValues(Set.of(seed.get()),true);
            }
            snapshots.put(id,value);
        }
        return state.withValues(snapshots);
    }
    private void indexElements(int declaration,List<Integer> dimensions,int level,List<Integer> path) {
        if(elements.size()>maxWork)throw new IllegalStateException("RESOURCE_LIMIT: logical table elements");
        if(level==dimensions.size()) {
            int id=nextElementId++;elements.put(new Element(declaration,path),id);
            elementIds.computeIfAbsent(declaration,k->new ArrayList<>()).add(id);demand.add(id);return;
        }
        if(dimensions.get(level)>maxWork)throw new IllegalStateException("RESOURCE_LIMIT: logical table dimension");
        for(int i=1;i<=dimensions.get(level);i++){var next=new ArrayList<>(path);next.add(i);indexElements(declaration,dimensions,level+1,next);}
    }
    private Optional<String> conditionText(Ast.ConditionValue v) {
        return switch(v.kind()){case TEXT,NUMBER->Optional.of(v.value());case SPACES->Optional.of(" ");case ZERO->Optional.of("0");default->Optional.empty();};
    }
    private Set<Integer> writes(Ast.Statement s) {
        if(writeCache.containsKey(s))return writeCache.get(s);
        var out=new HashSet<Integer>();
        if(s instanceof Ast.MoveStatement m)m.targets().forEach(t->out.addAll(declarations.reads(t)));
        else if(s instanceof Ast.CallStatement c) {for(var a:c.arguments())if(a.passingMode()!=Ast.PassingMode.CONTENT&&a.passingMode()!=Ast.PassingMode.VALUE)out.addAll(declarations.reads(a.value()));out.addAll(declarations.reads(c.returning()));}
        else if(s instanceof Ast.EmbeddedLanguageStatement e) {
            e.hostOperands().stream().filter(h->h.role()!=Ast.EmbeddedHostRole.READ).forEach(h->out.addAll(declarations.reads(h.reference())));
        } else StatementEffectSummary.of(s).ifPresent(e->{e.mayWrites().forEach(r->out.addAll(declarations.reads(r)));e.sourceTargets().forEach(r->out.addAll(declarations.reads(r)));e.exposedRegions().forEach(r->out.addAll(declarations.reads(r)));});
        var result=Set.copyOf(out);writeCache.put(s,result);return result;
    }
    DependencyValues read(Ast.Expression expression,State state) {
        if(expression==null)return DependencyValues.UNKNOWN;
        var literal=LogicalMoveSemantics.literal(expression);if(literal.isPresent())return DependencyValues.known(literal.get());
        if(expression instanceof Ast.DataReference r) {
            Integer id=declarations.references.get(r.meta().id());if(id==null)return DependencyValues.UNKNOWN;
            var value=readWholeReference(r,id,state);
            if(r.referenceModification()!=null) {
                var mod=r.referenceModification();int start=integer(mod.offset(),state)-1;
                int n=mod.length()==null?declarations.widths.getOrDefault(id,0)-start:integer(mod.length(),state);
                if(start<0||n<=0)return value.open();
                final int length=n;value=value.map(s->start+length<=s.length()?s.substring(start,start+length):null);
            }
            return value;
        }
        if(expression instanceof Ast.FunctionExpression f&&f.arguments().size()==1) {
            var value=read(f.arguments().get(0),state);
            return switch(f.functionName().toUpperCase(Locale.ROOT)) {
                case "UPPER-CASE"->value.map(s->s.toUpperCase(Locale.ROOT));
                case "LOWER-CASE"->value.map(s->s.toLowerCase(Locale.ROOT));
                case "TRIM"->value.map(s->f.directions().contains(Ast.FunctionDirection.LEADING)?s.stripLeading():f.directions().contains(Ast.FunctionDirection.TRAILING)?s.stripTrailing():s.strip());
                default->value.open();
            };
        }
        if(expression instanceof Ast.OperationExpression o&&o.operands().size()==2) {
            var a=read(o.operands().get(0),state);var b=read(o.operands().get(1),state);var out=new HashSet<String>();boolean unknown=a.unknown()||b.unknown();
            checkProduct(a,b);
            for(String x:a.values())for(String y:b.values())try {
                var l=new BigDecimal(x.strip());var r=new BigDecimal(y.strip());
                var result=switch(o.operator()){case "+"->l.add(r);case "-"->l.subtract(r);case "*"->l.multiply(r);default->null;};
                if(result==null)unknown=true;else out.add(result.stripTrailingZeros().toPlainString());
            }catch(NumberFormatException ex){unknown=true;}
            return new DependencyValues(out,unknown);
        }
        return DependencyValues.UNKNOWN;
    }
    private DependencyValues readWholeReference(Ast.DataReference r,int id,State state) {
        var value=readDeclaration(id,state);
        if(r.subscriptGroups().isEmpty())return value;
        var indexes=r.subscriptGroups().stream().flatMap(g->g.subscripts().stream()).map(e->integer(e,state)).toList();
        if(indexes.stream().anyMatch(i->i<=0))return value.open();
        Integer slot=elements.get(new Element(id,indexes));
        return slot==null?DependencyValues.UNKNOWN:state.get(slot);
    }
    private DependencyValues readDeclaration(int id,State state) {
        if(textGroups.contains(id)&&state.values().containsKey(id))return state.get(id);
        return assemble(id,state);
    }
    private DependencyValues assemble(int id,State state) {
        var cs=declarations.children.getOrDefault(id,List.of());if(cs.isEmpty())return state.get(id);
        DependencyValues value=DependencyValues.known("");
        if(declarations.widths.getOrDefault(id,0)==0)return DependencyValues.UNKNOWN;
        for(int c:cs) {
            if(declarations.entries.get(c).clauses().stream().anyMatch(Ast.RedefinesClause.class::isInstance))continue;
            var next=readDeclaration(c,state);var joined=new HashSet<String>();
            if(declarations.counts.getOrDefault(c,1)>1)return DependencyValues.UNKNOWN;
            if((long)value.values().size()*next.values().size()>maxWork)throw new IllegalStateException("RESOURCE_LIMIT: logical group "+declarations.entries.get(id).name()+" exceeds candidate budget");
            for(String a:value.values())for(String b:next.values()) {if(a.length()+b.length()>maxWork)throw new IllegalStateException("RESOURCE_LIMIT: logical text extent");joined.add(a+b);}
            value=new DependencyValues(joined,value.unknown()||next.unknown());
        }
        return value;
    }
    private void checkProduct(DependencyValues a,DependencyValues b) {
        if((long)a.values().size()*b.values().size()>maxWork)throw new IllegalStateException("RESOURCE_LIMIT: logical candidate product exceeds --max-work="+maxWork);
    }
    private int integer(Ast.Expression expression,State state) {
        var value=read(expression,state);if(value.unknown()||value.values().size()!=1)return -1;
        try{return new BigDecimal(value.values().iterator().next().strip()).intValueExact();}catch(ArithmeticException|NumberFormatException ex){return -1;}
    }
    private State write(int id,DependencyValues value,State state,boolean weak) {
        State incoming=state;
        if(weak&&value.equals(state.get(id))&&state.get(id).unknown()
                &&declarations.related(id).stream().allMatch(a->incoming.get(a).unknown()&&incoming.get(a).values().containsAll(value.values())))return state;
        State original=state;
        var changedGroups=new HashSet<Integer>();
        for(int group:textGroups)if(!Collections.disjoint(declarations.leaves(group),declarations.related(id)))changedGroups.add(group);
        if(!changedGroups.isEmpty()) {var out=new DependencyEnvironment.Builder(state.values());changedGroups.forEach(out::remove);state=state.withValues(out);}
        state=writeLocal(id,value,state,weak);
        if(!weak&&!declarations.children.getOrDefault(id,List.of()).isEmpty()&&declarations.leaves(id).stream().anyMatch(elementIds::containsKey)) {
            state=decode(id,value.fit(declarations.widths.getOrDefault(id,0)),state,0,false);
            var exact=new DependencyEnvironment.Builder(state.values());
            for(int leaf:declarations.leaves(id))if(elementIds.containsKey(leaf)) {
                DependencyValues all=null;for(int slot:elementIds.get(leaf))all=all==null?state.get(slot):all.join(state.get(slot));
                exact.put(leaf,all);
            }state=state.withValues(exact);
        }
        var ancestors=new LinkedHashSet<Integer>();ancestors.add(id);
        for(int leaf:declarations.leaves(id)){Integer ancestor=leaf;while(ancestor!=null){ancestors.add(ancestor);ancestor=declarations.parent.get(ancestor);}}
        for(int ancestor:ancestors)for(int alias:declarations.textualViews.getOrDefault(ancestor,Set.of()))state=decode(alias,readDeclaration(ancestor,state),state,0,weak);
        if(!changedGroups.isEmpty()) {
            var out=new DependencyEnvironment.Builder(state.values());
            for(int group:changedGroups) {
                var prior=original.get(group);var span=span(group,id,0);
                DependencyValues next;
                if(weak)next=prior.open();
                else if(id==group&&declarations.widths.getOrDefault(id,0)>0)next=value.fit(declarations.widths.get(id));
                else if(span!=null&&!prior.values().isEmpty()) {
                    var replacement=readDeclaration(id,state).fit(span[1]);var candidates=new HashSet<String>();
                    checkProduct(prior,replacement);
                    for(String a:prior.values())for(String b:replacement.values())if(span[0]+span[1]<=a.length())candidates.add(a.substring(0,span[0])+b+a.substring(span[0]+span[1]));
                    next=new DependencyValues(candidates,prior.unknown()||replacement.unknown());
                }else next=assemble(group,state);
                out.put(group,next);
            }
            state=state.withValues(out);
        }
        return state;
    }
    /** Declarative character order, only for plain logical textual groups. */
    private int[] span(int group,int target,int offset) {
        if(group==target||declarations.equivalents.getOrDefault(group,Set.of()).contains(target))return new int[]{offset,declarations.widths.getOrDefault(group,0)};
        for(int child:declarations.children.getOrDefault(group,List.of())) {
            if(declarations.counts.getOrDefault(child,1)>1)return null;
            if(declarations.entries.get(child).clauses().stream().anyMatch(Ast.RedefinesClause.class::isInstance))continue;
            var found=span(child,target,offset);if(found!=null)return found;
            offset+=declarations.widths.getOrDefault(child,0);
        }return null;
    }
    private State decode(int id,DependencyValues text,State state,int offset,boolean weak) {
        return decode(id,text,state,offset,weak,List.of());
    }
    private State decode(int id,DependencyValues text,State state,int offset,boolean weak,List<Integer> path) {
        int width=declarations.widths.getOrDefault(id,0);if(width==0)return state;
        int count=declarations.counts.getOrDefault(id,1);var cs=declarations.children.getOrDefault(id,List.of());
        if((long)count*width>maxWork)throw new IllegalStateException("RESOURCE_LIMIT: logical table text");
        for(int i=0;i<count;i++) {
            int base=offset+i*width;
            var current=new ArrayList<>(path);
            if(declarations.entries.get(id).clauses().stream().anyMatch(Ast.OccursClause.class::isInstance))current.add(i+1);
            if(cs.isEmpty()) {
                var value=text.map(s->base+width<=s.length()?s.substring(base,base+width):null);
                state=writeLocal(id,value,state,weak||declarations.repeated.contains(id));
                var out=new DependencyEnvironment.Builder(state.values());
                for(int alias:declarations.equivalents.getOrDefault(id,Set.of(id))) {
                    Integer slot=elements.get(new Element(alias,current));
                    if(slot!=null)out.put(slot,weak?state.get(slot).join(value).open():value);
                }
                state=state.withValues(out);
            }else {int start=base;for(int c:cs) {
                if(declarations.entries.get(c).clauses().stream().anyMatch(Ast.RedefinesClause.class::isInstance))continue;
                state=decode(c,text,state,start,weak,current);start+=declarations.widths.getOrDefault(c,0)*declarations.counts.getOrDefault(c,1);
            }}
        }
        return state;
    }
    private State writeLocal(int id,DependencyValues value,State state,boolean weak) {
        if(!declarations.entries.containsKey(id))return state;
        weak|=declarations.assumed.contains(id)||declarations.repeated.contains(id);
        var cs=declarations.children.getOrDefault(id,List.of());
        int width=declarations.widths.getOrDefault(id,0);
        if(width>maxWork)throw new IllegalStateException("RESOURCE_LIMIT: logical text extent");
        if(!cs.isEmpty()) {
            if(width==0) {for(int leaf:declarations.leaves(id))state=writeLocal(leaf,state.get(leaf).open(),state,true);return state;}
            var fitted=value.fit(width);int start=0;
            for(int child:cs) {
                if(declarations.entries.get(child).clauses().stream().anyMatch(Ast.RedefinesClause.class::isInstance))continue;
                int n=declarations.widths.getOrDefault(child,0);final int offset=start;
                state=writeLocal(child,fitted.map(s->offset+n<=s.length()?s.substring(offset,offset+n):null),state,weak);start+=n;
            }
            return state;
        }
        value=fitField(id,value);
        Map<Integer,DependencyValues> out=null;
        for(int target:declarations.equivalents.getOrDefault(id,Set.of(id)))if(demand.contains(target)) {
            // An absent input is not an unwritten result: an explicit UNKNOWN
            // must mask the caller's previous value when this summary returns.
            var next=weak?state.get(target).join(value).open():value;
            if(!next.equals(state.get(target))||!state.values().containsKey(target)) {if(out==null)out=new DependencyEnvironment.Builder(state.values());out.put(target,next);}
        }
        if(weak)for(int alias:declarations.equivalents.getOrDefault(id,Set.of(id)))for(int slot:elementIds.getOrDefault(alias,List.of()))if(!state.get(slot).unknown()) {
            if(out==null)out=new DependencyEnvironment.Builder(state.values());out.put(slot,state.get(slot).open());
        }
        for(int alias:declarations.possibleAliases.getOrDefault(id,Set.of()))if(demand.contains(alias)) {
            if(!state.get(alias).unknown()) {if(out==null)out=new DependencyEnvironment.Builder(state.values());out.put(alias,state.get(alias).open());}
            for(int slot:elementIds.getOrDefault(alias,List.of()))if(!state.get(slot).unknown()) {
                if(out==null)out=new DependencyEnvironment.Builder(state.values());out.put(slot,state.get(slot).open());
            }
        }
        if(value.values().size()>maxWork)throw new IllegalStateException("RESOURCE_LIMIT: too many logical candidates");
        return out==null?state:state.withValues(out);
    }
    private DependencyValues fitField(int id,DependencyValues value) {
        int width=declarations.widths.getOrDefault(id,0);
        if(!declarations.numeric.contains(id))return value.fit(width);
        return value.map(s->{String number=s.strip();return width>0&&number.matches("[0-9]+")&&number.length()<width?"0".repeat(width-number.length())+number:number;});
    }
    private State assign(Ast.Expression target,DependencyValues value,State state) {
        if(!(target instanceof Ast.DataReference r))return state;
        Integer id=declarations.references.get(r.meta().id());if(id==null)return state;
        if(r.referenceModification()==null)return writeReference(r,id,value,state);
        var m=r.referenceModification();int start=integer(m.offset(),state)-1;
        int length=m.length()==null?declarations.widths.getOrDefault(id,0)-start:integer(m.length(),state);
        if(start<0||length<=0)return writeReference(r,id,readWholeReference(r,id,state).open(),state);
        var old=readWholeReference(r,id,state);var replacement=value.fit(length);var out=new HashSet<String>();
        checkProduct(old,replacement);
        for(String a:old.values())for(String b:replacement.values())if(start+length<=a.length())out.add(a.substring(0,start)+b+a.substring(start+length));
        // Unknown original prefix/suffix cannot prove a full assembled candidate.
        if(start==0&&length==declarations.widths.getOrDefault(id,0))return writeReference(r,id,replacement,state);
        return writeReference(r,id,new DependencyValues(out,old.unknown()||replacement.unknown()),state);
    }
    private State writeReference(Ast.DataReference r,int id,DependencyValues value,State state) {
        if(!r.subscriptGroups().isEmpty()) {
            var indexes=r.subscriptGroups().stream().flatMap(g->g.subscripts().stream()).map(e->integer(e,state)).toList();
            State out=write(id,value,state,true);var changed=new DependencyEnvironment.Builder(out.values());
            Integer selected=indexes.stream().allMatch(i->i>0)?elements.get(new Element(id,indexes)):null;
            if(selected!=null) {
                for(int alias:declarations.equivalents.getOrDefault(id,Set.of(id))) {
                    Integer aliasSlot=elements.get(new Element(alias,indexes));if(aliasSlot==null)continue;
                    for(int slot:elementIds.get(alias))changed.put(slot,state.get(slot));
                    changed.put(aliasSlot,fitField(alias,value));
                    DependencyValues summary=null;
                    for(int slot:elementIds.get(alias))summary=summary==null?changed.get(slot):summary.join(changed.get(slot));
                    changed.put(alias,summary);
                    for(int group:textGroups) {
                        var span=indexedSpan(group,alias,indexes);if(span==null)continue;
                        var prior=state.get(group);var replacement=changed.get(aliasSlot).fit(span[1]);
                        var candidates=new HashSet<String>();checkProduct(prior,replacement);
                        for(String a:prior.values())for(String b:replacement.values())if(span[0]+span[1]<=a.length())candidates.add(a.substring(0,span[0])+b+a.substring(span[0]+span[1]));
                        var next=new DependencyValues(candidates,prior.unknown()||replacement.unknown());
                        var exact=singletonTableText(group,out.withValues(changed),List.of(),true);
                        if(exact.isPresent())next=DependencyValues.known(exact.get());
                        changed.put(group,next);
                    }
                }
            }else for(int alias:declarations.equivalents.getOrDefault(id,Set.of(id)))for(int slot:elementIds.getOrDefault(alias,List.of()))changed.put(slot,out.get(slot).join(fitField(alias,value)).open());
            State result=out.withValues(changed);
            if(selected!=null)for(int group:textGroups)if(indexedSpan(group,id,indexes)!=null)
                for(int alias:declarations.textualViews.getOrDefault(group,Set.of()))result=decode(alias,result.get(group),result,0,false);
            return result;
        }
        return write(id,value,state,false);
    }
    /** Reassemble only singleton, closed elements; never form Cartesian names. */
    private Optional<String> singletonTableText(int id,State state,List<Integer> path,boolean closed) {
        var text=new StringBuilder();int count=declarations.counts.getOrDefault(id,1);
        var children=declarations.children.getOrDefault(id,List.of());
        if((long)count*declarations.widths.getOrDefault(id,0)>maxWork)throw new IllegalStateException("RESOURCE_LIMIT: logical table text");
        for(int i=1;i<=count;i++) {
            var current=new ArrayList<>(path);
            if(declarations.entries.get(id).clauses().stream().anyMatch(Ast.OccursClause.class::isInstance))current.add(i);
            if(children.isEmpty()) {
                int slot=elements.getOrDefault(new Element(id,current),id);var v=state.get(slot);
                if(closed&&v.unknown()||v.values().size()!=1)return Optional.empty();
                text.append(v.values().iterator().next());
            }else for(int child:children) {
                if(declarations.entries.get(child).clauses().stream().anyMatch(Ast.RedefinesClause.class::isInstance))continue;
                var part=singletonTableText(child,state,current,closed);if(part.isEmpty())return Optional.empty();text.append(part.get());
            }
        }
        return Optional.of(text.toString());
    }
    /** Character positions in a known static textual table element. */
    private int[] indexedSpan(int group,int id,List<Integer> indexes) {
        var chain=new ArrayList<Integer>();Integer cursor=id;
        while(cursor!=null){chain.add(cursor);cursor=declarations.parent.get(cursor);}
        Collections.reverse(chain);int offset=0,index=0;boolean inside=false;
        for(int node:chain) {
            if(node==group)inside=true;
            if(inside&&node!=group) {
                int parent=declarations.parent.get(node);
                int positioned=node;var seen=new HashSet<Integer>();
                while(seen.add(positioned)) {
                    var redef=declarations.entries.get(positioned).clauses().stream().filter(Ast.RedefinesClause.class::isInstance).map(Ast.RedefinesClause.class::cast).findFirst();
                    if(redef.isEmpty())break;
                    Integer original=declarations.references.get(redef.get().target().meta().id());
                    if(original==null||!Objects.equals(declarations.parent.get(original),parent))return null;
                    positioned=original;
                }
                for(int sibling:declarations.children.getOrDefault(parent,List.of())) {
                    if(sibling==positioned)break;
                    if(declarations.entries.get(sibling).clauses().stream().anyMatch(Ast.RedefinesClause.class::isInstance))continue;
                    offset+=declarations.widths.getOrDefault(sibling,0)*declarations.counts.getOrDefault(sibling,1);
                }
            }
            if(declarations.entries.get(node).clauses().stream().anyMatch(Ast.OccursClause.class::isInstance)) {
                if(index>=indexes.size()||indexes.get(index)>declarations.counts.getOrDefault(node,0))return null;
                if(inside)offset+=(indexes.get(index)-1)*declarations.widths.getOrDefault(node,0);
                index++;
            }
        }
        return inside?new int[]{offset,declarations.widths.getOrDefault(id,0)}:null;
    }
    private State transfer(Ast.Statement s,State state) {
        if(s instanceof Ast.MoveStatement m) {
            var value=read(m.source(),state);
            if(!m.corresponding()) {for(var t:m.targets())state=assign(t,value,state);return state;}
            // CORRESPONDING is name based by language, unlike ordinary group MOVE.
            Integer src=m.source() instanceof Ast.DataReference r?declarations.references.get(r.meta().id()):null;
            if(src!=null)for(var t:m.targets())if(t instanceof Ast.DataReference r) {
                Integer dst=declarations.references.get(r.meta().id());if(dst==null)continue;
                for(int a:declarations.leaves(src))for(int b:declarations.leaves(dst))if(declarations.entries.get(a).name().equalsIgnoreCase(declarations.entries.get(b).name()))state=write(b,readDeclaration(a,state),state,false);
            }
            return state;
        }
        if(s instanceof Ast.ModeledStatement m&&m.conditionSet().isPresent()) {
            for(var assignment:m.conditionSet().get().assignments()) {
                Integer id=declarations.references.get(assignment.target().meta().id());if(id==null)continue;
                var d=declarations.entries.get(id);if(d==null)continue;
                var v=d.clauses().stream().filter(Ast.ValueClause.class::isInstance).map(Ast.ValueClause.class::cast).findFirst();
                if(v.isEmpty())continue;
                var text=assignment.truth()?v.get().ranges().stream().findFirst().map(Ast.ConditionRange::first).flatMap(this::conditionText):v.get().falseValue().flatMap(this::conditionText);
                int p=declarations.conditions.getOrDefault(id,id);state=write(p,text.map(DependencyValues::known).orElse(state.get(p).open()),state,false);
            }
            return state;
        }
        var effect=StatementEffectSummary.of(s);
        if(s instanceof Ast.ModeledStatement m&&effect.filter(e->e.proof()==StatementEffectSummary.Proof.INITIALIZE_TARGETS).isPresent()
                &&m.operands().size()==effect.get().sourceTargets().size()) {
            for(var target:effect.get().sourceTargets()) {
                Integer id=declarations.references.get(target.meta().id());if(id!=null)state=initialize(id,state);
            }return state;
        }
        for(int id:writes(s))if(demand.contains(id))state=write(id,state.get(id).open(),state,true);
        if(effect.isPresent()&&effect.get().unknownWriteBound()==StatementEffectSummary.Bound.ALL) {
            diagnostics.add("UNBOUNDED_WRITE at "+s.meta().provenance().original().startLine());
            for(int id:demand)state=write(id,state.get(id).open(),state,true);
        }
        return state;
    }
    private State initialize(int id,State state) {
        var children=declarations.children.getOrDefault(id,List.of());
        if(!children.isEmpty()) {for(int child:children) {
            var entry=declarations.entries.get(child);
            if(!entry.filler()&&entry.clauses().stream().noneMatch(Ast.RedefinesClause.class::isInstance))state=initialize(child,state);
        }return state;}
        if(declarations.entries.get(id).filler())return state;
        int width=declarations.widths.getOrDefault(id,0);
        if(width>maxWork)throw new IllegalStateException("RESOURCE_LIMIT: INITIALIZE logical text extent");
        var value=width>0?DependencyValues.known(declarations.numeric.contains(id)?"0".repeat(width):" ".repeat(width)):DependencyValues.UNKNOWN;
        state=write(id,value,state,false);
        if(elementIds.containsKey(id)) {
            var initialized=new DependencyEnvironment.Builder(state.values());initialized.put(id,value);
            for(int slot:elementIds.get(id))initialized.put(slot,value);state=state.withValues(initialized);
        }return state;
    }
    private void enqueue(int context,String node,State state) {
        var key=new Location(context,node);var old=before.get(key);var joined=old==null?state:old.join(state);
        if(!joined.equals(old)){before.put(key,joined);if(queued.add(key))work.addLast(key);}
        if(before.size()>maxWork)throw new IllegalStateException("RESOURCE_LIMIT: too many states");
    }
    private Exit normalize(Exit target) {
        var seen=new HashSet<Exit>();
        while(target.kind()==TargetKind.REGION_ENTRY) {
            if(!seen.add(target))throw new IllegalStateException("cyclic control alias "+target);
            target=Exit.of(regions.get(target.reference()).entry());
        }return target;
    }
    private void route(int context,Target target,State state){route(context,Exit.of(target),state);}
    private void route(int context,Exit target,State state) {
        var seen=new HashSet<Exit>();
        while(true) {
            target=normalize(target);
            if(!seen.add(target))throw new IllegalStateException("cyclic control alias "+target);
            switch(target.kind()) {
                case OCCURRENCE -> {enqueue(context,target.reference(),state);return;}
                case COMPLETE -> {
                    if(("boundary:"+target.reference()).equals(contexts.get(context).key.endpoint())){finish(context,target,state);return;}
                    target=Exit.of(boundaries.get(target.reference()).ordinaryDefault());
                }
                case ESCAPE,PROGRAM_RETURN,PROGRAM_HALT -> {finish(context,target,state);return;}
                case FILE_POINT -> {for(var t:filePoints.get(target.reference()).targets())route(context,t,state);return;}
                case UNKNOWN_LOCAL -> {
                    if(contexts.get(context).key.endpoint().equals("boundary:"+target.reference())&&regions.get(target.reference()).kind()==RegionKind.DECLARATIVE) {
                        diagnostics.add("IO_HANDLER_REMAINDER "+target.reference());finish(context,new Exit(TargetKind.COMPLETE,target.reference()),state);return;
                    }
                    diagnostics.add("CONTROL_REMAINDER "+target.reference());return;
                }
                default -> throw new IllegalStateException("unresolved control entry "+target);
            }
        }
    }
    private void invoke(int caller,Binding binding,State state) {
        subscribe(new ReturnTo(caller,binding.id(),binding.endpoint(),state),Exit.of(regions.get(binding.region()).entry()));
    }
    private void subscribe(Continuation caller,Exit entry) {
        String endpoint=caller.endpoint();State state=caller.input();
        entry=normalize(entry);
        var point=new Point(entry,endpoint);
        var selection=projections.computeIfAbsent(point,key->Selection.of(relevance.needed(key)));
        var key=new SummaryKey(entry,endpoint,selection.apply(state));
        Integer context=memo.get(key);
        if(context==null) {
            context=contexts.size();memo.put(key,context);contexts.add(new Context(key));route(context,entry,key.input());
        }
        var ctx=contexts.get(context);
        if(ctx.callers.add(caller))for(var exit:ctx.results.keySet())queueResult(context,exit);
        if(contexts.size()>maxWork)throw new IllegalStateException("RESOURCE_LIMIT: too many summaries");
    }
    private State restore(Continuation caller,State result) {
        var restored=new DependencyEnvironment.Builder(caller.input().values());restored.putAll(result.values());
        var handlers=new HashMap<>(caller.input().handlers());handlers.putAll(result.handlers());
        return new State(restored,handlers,union(caller.input().reached(),result.reached()));
    }
    /** A fallthrough forwards the result unchanged. Only a real invocation owns
     * repeat phases and contextual escapes; an independent entry has no resume. */
    private void deliver(Continuation caller,Exit exit,State result) {
        State state=restore(caller,result);
        if(caller instanceof Forward forward){finish(forward.context(),exit,state);return;}
        ReturnTo returnTo=caller instanceof ReturnTo r?r:null;
        Binding binding=returnTo==null?null:bindings.get(returnTo.binding());
        if(exit.kind()==TargetKind.COMPLETE) {
            if(returnTo!=null)phase(returnTo.context(),binding,binding.completionPhase(),state);
            return;
        }
        if(exit.kind()==TargetKind.ESCAPE) {
            if(("boundary:"+exit.reference()).equals(caller.endpoint())) {
                if(returnTo!=null)route(returnTo.context(),binding.resume(),state);
                return;
            }
            if(returnTo!=null) {
                String body=regions.get(binding.region()).entry().reference(),ancestor=body;
                while(regions.containsKey(ancestor)&&!ancestor.equals(exit.reference()))ancestor=regions.get(ancestor).parent();
                if(ancestor.equals(exit.reference())&&!ancestor.equals(body)){finish(returnTo.context(),exit,state);return;}
            }
            Continuation next=returnTo==null?new Entry(caller.endpoint(),state)
                :new ReturnTo(returnTo.context(),returnTo.binding(),caller.endpoint(),state);
            subscribe(next,Exit.of(boundaries.get(exit.reference()).ordinaryDefault()));
        }else if(returnTo!=null)finish(returnTo.context(),exit,state);
    }
    private Set<Integer> expandNeeded(Set<Integer> needed) {
        for(int group:textGroups)if(!Collections.disjoint(needed,declarations.leaves(group)))needed.add(group);
        for(int id:new ArrayList<>(needed))needed.addAll(elementIds.getOrDefault(id,List.of()));
        needed.retainAll(demand);return Set.copyOf(needed);
    }
    private Point inputPoint(Exit exit,String endpoint){return new Point(normalize(exit),endpoint);}
    private void valueInputs(Set<Input> inputs,Collection<Integer> ids) {
        for(int id:expandNeeded(new HashSet<>(ids)))inputs.add(new Value(id));
    }
    /** Own operands, rather than all references nested under a compound AST.
     * Only scalar full writes with no retained group/table/possible-alias state
     * prove a kill. All other writes still read their prior logical contents. */
    private Effect localEffect(Exit exit) {
        var reads=new HashSet<Input>();var kills=new HashSet<Input>();var s=statements.get(exit.reference());
        if(s==null)return new Effect(reads,kills,List.of());
        if(s instanceof Ast.MoveStatement m&&!m.corresponding()) {
            valueInputs(reads,declarations.reads(m.source()));
            for(var target:m.targets()) {
                if(!(target instanceof Ast.DataReference r))continue;
                Integer id=declarations.references.get(r.meta().id());
                if(id==null)continue;
                var aliases=declarations.equivalents.getOrDefault(id,Set.of(id));
                boolean full=r.referenceModification()==null&&r.subscriptGroups().isEmpty()
                    &&declarations.children.getOrDefault(id,List.of()).isEmpty()&&!declarations.repeated.contains(id)
                    &&!declarations.assumed.contains(id)&&declarations.related(id).equals(aliases)
                    &&textGroups.stream().noneMatch(g->!Collections.disjoint(declarations.leaves(g),aliases));
                if(full) {
                    for(int alias:aliases)if(demand.contains(alias))kills.add(new Value(alias));
                }else valueInputs(reads,declarations.reads(r));
            }
        }else if(s instanceof Ast.IfStatement f)valueInputs(reads,declarations.reads(f.condition()));
        else if(s instanceof Ast.EvaluateStatement e) {
            for(var x:e.subjects())valueInputs(reads,declarations.reads(x));
            for(var branch:e.branches())for(var selector:branch.selectors())valueInputs(reads,declarations.reads(selector.expression()));
        }else if(s instanceof Ast.PerformStatement p)for(var c:p.controls())valueInputs(reads,declarations.reads(c.expression()));
        else if(s instanceof Ast.GoToStatement g)valueInputs(reads,declarations.reads(g.dependingOn()));
        else valueInputs(reads,declarations.reads(s));
        if(StatementEffectSummary.of(s).filter(e->e.unknownWriteBound()==StatementEffectSummary.Bound.ALL).isPresent()
                ||exceptionalEvents.containsKey(exit.reference()))valueInputs(reads,demand);
        for(var p:possibilities.getOrDefault(exit.reference(),List.of()))for(String fact:p.prerequisites())reads.add(new Fact(fact));
        for(var event:events.getOrDefault(exit.reference(),List.of()))if(event.eligibility()==EventEligibility.HANDLER_ELIGIBLE) {
            reads.add(new Handler(event.condition()));reads.add(new Handler("ERROR"));
        }
        for(var event:exceptionalEvents.getOrDefault(exit.reference(),List.of()))if(event.eligibility()==EventEligibility.HANDLER_ELIGIBLE) {
            reads.add(new Handler("ABEND"));
            if(event.origin()!=EventOrigin.EXPLICIT_ABEND){reads.add(new Handler("PGMIDERR"));reads.add(new Handler("ERROR"));}
        }
        var abend=abendRegistrations.get(exit.reference());
        if(abend!=null) {
            if(abend.action()==CicsHandlerSemantics.Action.RESET)reads.add(new Handler("ABEND-SAVED"));
            if(abend.action()!=CicsHandlerSemantics.Action.UNAVAILABLE)kills.add(new Handler("ABEND"));
            if(abend.action()==CicsHandlerSemantics.Action.ACTIVATE)kills.add(new Handler("ABEND-SAVED"));
        }
        for(var registration:registrations.getOrDefault(exit.reference(),List.of()))kills.add(new Handler(registration.condition()));
        return new Effect(reads,kills,List.of());
    }
    private Effect inputEffect(Point point) {
        var exit=point.exit();var local=accessEffects.computeIfAbsent(exit,this::localEffect);var successors=new ArrayList<Point>();
        switch(exit.kind()) {
            case COMPLETE,ESCAPE -> {
                if(!("boundary:"+exit.reference()).equals(point.endpoint()))successors.add(inputPoint(Exit.of(boundaries.get(exit.reference()).ordinaryDefault()),point.endpoint()));
            }
            case OCCURRENCE -> {
                var statement=statements.get(exit.reference());
                for(var edge:edges.getOrDefault(exit.reference(),List.of())) {
                    if(edge.kind()==OutcomeKind.LOCAL_INVOKE) {
                        var binding=bindings.get(edge.binding());
                        successors.add(inputPoint(Exit.of(regions.get(binding.region()).entry()),binding.endpoint()));
                        successors.add(inputPoint(Exit.of(binding.resume()),point.endpoint()));
                    }else successors.add(inputPoint(Exit.of(edge.target()),point.endpoint()));
                }
                for(var p:possibilities.getOrDefault(exit.reference(),List.of()))successors.add(inputPoint(Exit.of(p.target()),point.endpoint()));
                for(var binding:ioDeclarations.getOrDefault(exit.reference(),List.of()))successors.add(inputPoint(Exit.of(regions.get(binding.region()).entry()),binding.endpoint()));
                if(statement!=null)fileSurface(statement).ifPresent(surface->{for(var h:surface.handlers()) {
                    var r=regions.get("region:"+handle(statement)+"/file/handler-"+h.kind());if(r!=null)successors.add(inputPoint(Exit.of(r.entry()),point.endpoint()));
                }});
                for(var event:events.getOrDefault(exit.reference(),List.of())) {
                    successors.add(inputPoint(Exit.of(event.continuation()),point.endpoint()));
                    registrations.values().forEach(rs->rs.forEach(r->r.target().forEach(t->successors.add(inputPoint(Exit.of(t),point.endpoint())))));
                }
                if(exceptionalEvents.containsKey(exit.reference()))for(var handler:handlerEndpoints.entrySet())successors.add(inputPoint(new Exit(TargetKind.OCCURRENCE,handler.getKey()),handler.getValue()));
            }
            case FILE_POINT -> filePoints.get(exit.reference()).targets().forEach(t->successors.add(inputPoint(Exit.of(t),point.endpoint())));
            default -> { }
        }
        return new Effect(local.reads(),local.kills(),successors);
    }
    private void queueResult(int context,Exit exit) {
        var location=new Location(context,"",exit);if(queued.add(location))work.addLast(location);
    }
    private void finish(int context,Exit exit,State state) {
        var ctx=contexts.get(context);var old=ctx.results.get(exit);var joined=old==null?state:old.join(state);
        if(joined.equals(old))return;ctx.results.put(exit,joined);queueResult(context,exit);
    }
    private void phase(int caller,Binding binding,String phase,State state) {
        if(phase.equals("BODY")){invoke(caller,binding,state);return;}
        if(phase.equals("RESUME")){route(caller,binding.resume(),state);return;}
        enqueue(caller,"phase/"+binding.id()+"/"+phase,state);
    }
    private void step(Location location,State input) {
        String node=location.node();
        var context=contexts.get(location.context());
        if(paragraphEntries.contains(node)&&!relevance.sameComponent(inputPoint(context.key.entry(),context.key.endpoint()),inputPoint(new Exit(TargetKind.OCCURRENCE,node),context.key.endpoint()))) {
            subscribe(new Forward(location.context(),context.key.endpoint(),input),new Exit(TargetKind.OCCURRENCE,node));return;
        }
        var selection=operands.computeIfAbsent(node,this::operands);
        var key=new Computation(node,selection.apply(input));
        var actions=computations.get(key);
        if(actions==null) {
            evaluations++;actions=compute(node,key.input());computations.put(key,actions);
        }else reusedEvaluations++;
        for(var action:actions) {
            var state=action.patch().apply(input);
            if(action instanceof Advance advance)route(location.context(),advance.target(),state);
            else if(action instanceof Phase phase)phase(location.context(),bindings.get(phase.binding()),phase.phase(),state);
            else if(action instanceof Enter enter)subscribe(new Entry(enter.endpoint(),state),enter.entry());
        }
    }
    private Selection operands(String node) {
        var statement=statements.get(node);
        if(node.startsWith("phase/")) {
            int split=node.lastIndexOf('/');statement=statements.get(bindings.get(node.substring(6,split)).caller());
        }
        if(statement==null)return Selection.of(Set.of());
        var effect=localEffect(new Exit(TargetKind.OCCURRENCE,handle(statement)));
        var inputs=new HashSet<>(effect.reads());
        // Full scalar kills need no previous destination. Other writes may read
        // aliases, group snapshots or table elements while updating the target.
        var written=new HashSet<Input>();valueInputs(written,writes(statement));inputs.addAll(written);
        var values=new HashSet<Integer>();for(var operand:inputs)if(operand instanceof Value value)values.add(value.declaration());
        boolean changed;
        do {
            int size=values.size();
            for(int id:new ArrayList<>(values)){values.addAll(declarations.related(id));values.addAll(elementIds.getOrDefault(id,List.of()));}
            for(int group:textGroups)if(values.contains(group)||!Collections.disjoint(values,declarations.leaves(group))) {
                values.add(group);values.addAll(declarations.leaves(group));
            }
            values.retainAll(demand);changed=size!=values.size();
        }while(changed);
        for(int id:values)inputs.add(new Value(id));
        // A destination needed as an actual source remains an operand even if
        // the same statement also fully overwrites it (e.g. MOVE A TO A).
        for(var kill:effect.kills())if(!effect.reads().contains(kill))inputs.remove(kill);
        return Selection.of(inputs);
    }
    private List<Action> compute(String node,State input) {
        var actions=new ArrayList<Action>();
        if(node.startsWith("phase/")) {
            int split=node.lastIndexOf('/');var b=bindings.get(node.substring(6,split));String id=node.substring(split+1);
            var p=b.phases().stream().filter(x->x.id().equals(id)).findFirst().orElseThrow();
            var perform=(Ast.PerformStatement)statements.get(b.caller());State state=input;int truth=3;
            if(p.operation().equals("UNTIL_PREDICATE")) {
                var condition=perform.controls().stream().filter(c->c.context()==Ast.PerformControlContext.CONDITION&&(p.level()==0||c.varyingLevel()==p.level())).map(Ast.PerformControl::expression).findFirst();
                if(condition.isPresent())truth=truth(condition.get(),state);
            } else if(p.operation().equals("COUNT_ENTRY")) {int count=perform.controls().isEmpty()?-1:integer(perform.controls().get(0).expression(),state);truth=count<0?3:count==0?1:2;}
            else if(p.operation().equals("COUNT_REPEAT")) {int count=perform.controls().isEmpty()?-1:integer(perform.controls().get(0).expression(),state);truth=count==1?1:3;}
            else if(p.operation().equals("VARY_INITIAL")||p.operation().equals("VARY_UPDATE")) {
                var controls=perform.controls().stream().filter(c->p.level()==0||c.varyingLevel()==p.level()).toList();
                var variable=controls.stream().filter(c->c.context()==Ast.PerformControlContext.CONTROL_VARIABLE).map(Ast.PerformControl::expression).findFirst();
                var value=controls.stream().filter(c->c.context()==(p.operation().equals("VARY_INITIAL")?Ast.PerformControlContext.FROM:Ast.PerformControlContext.BY)).map(Ast.PerformControl::expression).findFirst();
                if(variable.isPresent()&&value.isPresent()) {
                    var assigned=read(value.get(),state);
                    // Numeric increments are widened immediately; target text values remain finite.
                    if(p.operation().equals("VARY_UPDATE"))assigned=read(variable.get(),state).open();
                    state=assign(variable.get(),assigned,state);
                }
            }
            for(var edge:p.edges())if(edge.role().equals("next")||edge.role().equals("true")&&(truth&1)!=0||edge.role().equals("false")&&(truth&2)!=0)actions.add(new Phase(b.id(),edge.target(),Patch.of(input,state)));
            return List.copyOf(actions);
        }
        var s=statements.get(node);if(s==null)return List.copyOf(actions);
        State state=transfer(s,input);
        if(prerequisites.contains(node)&&!state.reached().contains(node))state=new State(state.values(),state.handlers(),union(state.reached(),Set.of(node)));
        var registration=abendRegistrations.get(node);
        if(registration!=null) {
            var h=new HashMap<>(state.handlers());
            if(registration.action()==CicsHandlerSemantics.Action.ACTIVATE) {
                var target=registration.labelTarget().flatMap(CicsHandlerSemantics.LabelTarget::entry).map(id->Set.of("statement:"+id)).orElse(Set.of("UNKNOWN"));
                h.put("ABEND",target);h.put("ABEND-SAVED",target);
            } else if(registration.action()==CicsHandlerSemantics.Action.CANCEL)h.put("ABEND",Set.of("CANCEL"));
            else if(registration.action()==CicsHandlerSemantics.Action.RESET)h.put("ABEND",h.getOrDefault("ABEND-SAVED",Set.of("UNKNOWN")));
            state=state.withHandlers(h);
        }
        if(registrations.containsKey(node)) {
            var h=new HashMap<>(state.handlers());
            for(var r:registrations.get(node))h.put(r.condition(),Set.of(r.action()==ConditionAction.LABEL?r.target().get(0).reference():r.action().name()));
            state=state.withHandlers(h);
        }
        for(var e:edges.getOrDefault(node,List.of())) {
            State branch=state;
            if(s instanceof Ast.IfStatement f && e.kind()==OutcomeKind.BRANCH)branch=filter(f.condition(),e.role().equals("then"),state);
            if(s instanceof Ast.EvaluateStatement evaluate&&e.kind()==OutcomeKind.BRANCH)branch=evaluate(evaluate,e.role(),state);
            if(s instanceof Ast.GoToStatement g&&g.goToKind()==Ast.GoToKind.DEPENDING_ON) {
                int index=integer(g.dependingOn(),state);
                if(index>=0 && !(e.role().equals("normal")?index==0||index>g.targets().size():e.role().equals("target-"+(index-1))))continue;
            }
            if(branch==null)continue;
            if(e.kind()==OutcomeKind.LOCAL_INVOKE)actions.add(new Phase(e.binding(),bindings.get(e.binding()).entryPhase(),Patch.of(input,branch)));
            else actions.add(new Advance(Exit.of(e.target()),Patch.of(input,branch)));
        }
        // Inventory the source-qualified continuations with uncertainty. Their
        // prerequisites must have been reached in this invocation.
        for(var p:possibilities.getOrDefault(node,List.of()))if(state.reached().containsAll(p.prerequisites()))actions.add(new Advance(Exit.of(p.target()),Patch.of(input,state)));
        // File handler bodies already have grammar-owned entries/completions.
        var surface=fileSurface(s);
        if(surface.isPresent())for(var h:surface.get().handlers()) {
            var r=regions.get("region:"+node+"/file/handler-"+h.kind());if(r!=null)actions.add(new Advance(Exit.of(r.entry()),Patch.of(input,state)));
        }
        for(var use:ioDeclarations.getOrDefault(node,List.of()))actions.add(new Phase(use.id(),use.entryPhase(),Patch.of(input,state)));
        for(var event:events.getOrDefault(node,List.of()))if(event.eligibility()==EventEligibility.HANDLER_ELIGIBLE) {
            var hs=state.handlers().getOrDefault(event.condition(),state.handlers().getOrDefault("ERROR",Set.of("DEFAULT")));
            for(String handler:hs)if(handler.equals("IGNORE"))actions.add(new Advance(Exit.of(event.continuation()),Patch.of(input,state)));else if(regions.containsKey(handler))actions.add(new Advance(Exit.of(regions.get(handler).entry()),Patch.of(input,state)));
        }
        for(var event:exceptionalEvents.getOrDefault(node,List.of()))if(event.eligibility()==EventEligibility.HANDLER_ELIGIBLE) {
            boolean explicit=event.origin()==EventOrigin.EXPLICIT_ABEND;
            var dispositions=state.handlers().getOrDefault("PGMIDERR",state.handlers().getOrDefault("ERROR",Set.of("DEFAULT")));
            if(!explicit&&!dispositions.contains("DEFAULT"))continue;
            for(String handler:state.handlers().getOrDefault("ABEND",Set.of("UNKNOWN")))if(handlerEndpoints.containsKey(handler)) {
                var h=new HashMap<>(state.handlers());h.put("ABEND",Set.of("CANCEL"));
                State ingress=state.withHandlers(h);String endpoint=handlerEndpoints.get(handler);
                actions.add(new Enter(new Exit(TargetKind.OCCURRENCE,handler),endpoint,Patch.of(input,ingress)));
            }
        }
        return List.copyOf(actions);
    }
    private static Optional<Ast.FileIoSurface> fileSurface(Ast.Statement s) {
        return s instanceof Ast.ModeledStatement m?m.fileIo():s instanceof Ast.PreservedStatement p?p.fileIo():Optional.empty();
    }
    private State evaluate(Ast.EvaluateStatement e,String role,State state) {
        State remaining=state;int ordinal=0;
        for(var b:e.branches()) {
            if(b.other())return role.equals("other")?remaining:null;
            boolean selected=role.equals("when-"+(ordinal++));State match=remaining;
            for(var selector:b.selectors()) {
                if(selector.subjectIndex()>=e.subjects().size())continue;
                var subject=e.subjects().get(selector.subjectIndex());
                if((selector.context()==Ast.EvaluateSelectorContext.VALUE_COMPARISON||selector.context()==Ast.EvaluateSelectorContext.SIMPLE_LITERAL)) {
                    var predicate=new Ast.RelationCondition(e.meta(),subject,selector.negated()?"NOT =":"=",selector.expression(),"",selector.negated()?Ast.RelationOperator.NOT_EQUAL:Ast.RelationOperator.EQUAL);
                    if(match!=null)match=filter(predicate,true,match);
                    // Exclude a prior arm only for one simple selector; ALSO needs a disjunction.
                    if(b.selectors().size()==1&&remaining!=null)remaining=filter(predicate,false,remaining);
                } else {int t=truth(selector.expression(),remaining);if((t&1)==0)match=null;if(t==1)remaining=null;}
            }
            if(selected)return match;if(remaining==null)return null;
        }
        return role.equals("other")?remaining:null;
    }
    private State filter(Ast.Expression condition,boolean wanted,State state) {
        if(state==null)return null;int bit=wanted?1:2;if((truth(condition,state)&bit)==0)return null;
        var out=new DependencyEnvironment.Builder(state.values());
        for(int id:declarations.reads(condition)) {
            var old=state.get(id);if(declarations.repeated.contains(id)||declarations.assumed.contains(id))continue;
            var kept=new HashSet<String>();
            for(String value:old.values()) {
                var trial=new DependencyEnvironment.Builder(state.values());for(int alias:declarations.equivalents.getOrDefault(id,Set.of(id)))trial.put(alias,DependencyValues.known(value));
                if((truth(condition,state.withValues(trial))&bit)!=0)kept.add(value);
            }
            if(kept.isEmpty()&&!old.unknown())return null;
            out.put(id,new DependencyValues(kept,old.unknown()));
        }
        var conditionReads=declarations.reads(condition);
        for(int group:textGroups)if(!Collections.disjoint(conditionReads,declarations.leaves(group))) {
            var original=state.get(group);var kept=new HashSet<String>();
            for(String candidate:original.values()) {
                var trial=new DependencyEnvironment.Builder(state.values());trial.put(group,DependencyValues.known(candidate));
                for(int leaf:declarations.leaves(group)) {var position=span(group,leaf,0);
                    if(position!=null&&position[0]+position[1]<=candidate.length())trial.put(leaf,DependencyValues.known(candidate.substring(position[0],position[0]+position[1])));
                }
                if((truth(condition,state.withValues(trial))&bit)!=0)kept.add(candidate);
            }
            out.put(group,new DependencyValues(kept,original.unknown()));
        }
        return state.withValues(out);
    }
    private boolean needsNormalization(Ast.Expression expression) {
        return abbreviated.computeIfAbsent(expression,e->{
            var todo=new ArrayDeque<Ast.Node>();todo.add(e);
            while(!todo.isEmpty()) {var n=todo.removeFirst();
                if(n instanceof Ast.ContextualConditionTail||n instanceof Ast.DistributedOperandGroup||n instanceof Ast.RelationCondition r&&r.subject()==null)return true;
                todo.addAll(Ast.children(n));
            }return false;
        });
    }
    private int treeTruth(com.imd.cobolexplorer.semanticproduct.ConditionNames.Tree tree,State state) {
        String kind=tree.kind();
        if(kind.equals("BOOL"))return tree.use().equals("true")?1:2;
        if(kind.equals("TEST"))return truth(expressions.get(Integer.parseInt(tree.use().substring("condition-use:".length()))),state);
        if(kind.equals("NOT")){int t=treeTruth(tree.children().get(0),state);return (t&1)*2+(t&2)/2;}
        if(kind.equals("AND")||kind.equals("OR")) {
            int result=kind.equals("AND")?1:2;
            for(var child:tree.children()) {int next=0,t=treeTruth(child,state);
                for(int a:new int[]{1,2})for(int b:new int[]{1,2})if((result&a)!=0&&(t&b)!=0)next|=(kind.equals("AND")?a==1&&b==1:a==1||b==1)?1:2;result=next;
            }return result;
        }
        var operator=switch(kind){case "EQ"->Ast.RelationOperator.EQUAL;case "NE"->Ast.RelationOperator.NOT_EQUAL;case "LT"->Ast.RelationOperator.LESS;case "LE"->Ast.RelationOperator.LESS_EQUAL;case "GT"->Ast.RelationOperator.GREATER;case "GE"->Ast.RelationOperator.GREATER_EQUAL;default->null;};
        if(operator==null)return 3;
        var left=tree.children().get(0);var right=tree.children().get(1);
        Ast.Expression subject=left.kind().equals("READ")?expressions.get(Integer.parseInt(left.use().substring("reference:".length()))):null;
        boolean numbers=left.kind().equals("NUMBER")||subject instanceof Ast.DataReference r&&declarations.numeric.contains(declarations.references.get(r.meta().id()));
        return compare(operator,treeValue(left,state),treeValue(right,state),numbers);
    }
    private DependencyValues treeValue(com.imd.cobolexplorer.semanticproduct.ConditionNames.Tree tree,State state) {
        return switch(tree.kind()) {
            case "READ" -> read(expressions.get(Integer.parseInt(tree.use().substring("reference:".length()))),state);
            case "TEXT", "NUMBER" -> DependencyValues.known(tree.use());
            case "SPACES" -> DependencyValues.known(" ");case "ZERO", "ZEROS", "ZEROES" -> DependencyValues.known("0");
            default -> DependencyValues.UNKNOWN;
        };
    }
    private int truth(Ast.Expression e,State state) {
        if(state==null)return 0;
        if(needsNormalization(e))return treeTruth(normalized.computeIfAbsent(e,predicates::condition),state);
        if(e instanceof Ast.LiteralExpression l&&l.booleanValue().isPresent())return l.booleanValue().get()?1:2;
        if(e instanceof Ast.GroupedCondition g)return truth(g.inner(),state);
        if(e instanceof Ast.NegatedCondition n){int t=truth(n.operand(),state);return (t&1)*2+(t&2)/2;}
        if(e instanceof Ast.LogicalCondition l) {
            int result=l.connector()==Ast.LogicalConnector.AND?1:2;
            for(var operand:l.operands()) {int t=truth(operand,state);int next=0;
                for(int a:new int[]{1,2})for(int b:new int[]{1,2})if((result&a)!=0&&(t&b)!=0)next|=(l.connector()==Ast.LogicalConnector.AND?a==1&&b==1:a==1||b==1)?1:2;result=next;}
            return result;
        }
        if(e instanceof Ast.DataReference r) {
            Integer id=declarations.references.get(r.meta().id());if(id!=null&&declarations.conditions.containsKey(id)) {
                var clause=declarations.entries.get(id).clauses().stream().filter(Ast.ValueClause.class::isInstance).map(Ast.ValueClause.class::cast).findFirst();
                var value=state.get(declarations.conditions.get(id));if(clause.isEmpty()||value.unknown())return 3;
                int out=0;
                for(String v:value.values()) {boolean yes=clause.get().ranges().stream().anyMatch(range->conditionText(range.first()).map(x->range.last().isPresent()?v.strip().compareTo(x)>=0&&conditionText(range.last().get()).map(y->v.strip().compareTo(y)<=0).orElse(true):v.stripTrailing().equals(x.stripTrailing())).orElse(true));out|=yes?1:2;}
                return out;
            }
        }
        if(e instanceof Ast.ClassCondition c) {
            var value=read(c.subject(),state);int result=value.unknown()?3:0;
            for(String text:value.values()) {
                boolean yes;
                switch(c.className().toUpperCase(Locale.ROOT)) {
                    case "NUMERIC" -> yes=text.matches("[0-9]+");
                    case "ALPHABETIC" -> yes=text.matches("[A-Za-z ]+");
                    case "ALPHABETIC-LOWER", "ALPHABETIC_LOWER" -> yes=text.matches("[a-z ]+");
                    case "ALPHABETIC-UPPER", "ALPHABETIC_UPPER" -> yes=text.matches("[A-Z ]+");
                    default -> {diagnostics.add("PREDICATE_REMAINDER at "+e.meta().provenance().original().startLine());return 3;}
                }result|=(yes!=c.negated())?1:2;
            }return result;
        }
        if(!(e instanceof Ast.RelationCondition r)||r.subject()==null)return 3;
        boolean numbers=r.subject() instanceof Ast.DataReference d&&declarations.numeric.contains(declarations.references.get(d.meta().id())) || r.subject() instanceof Ast.LiteralExpression l&&l.numericValue().isPresent();
        return compare(r.operatorKind(),read(r.subject(),state),read(r.object(),state),numbers);
    }
    private int compare(Ast.RelationOperator operator,DependencyValues a,DependencyValues b,boolean numbers) {
        int out=a.unknown()||b.unknown()?3:0;
        for(String x:a.values())for(String y:b.values()) {
            int cmp;
            try{cmp=numbers?new BigDecimal(x.strip()).compareTo(new BigDecimal(y.strip())):x.stripTrailing().compareTo(y.stripTrailing());}catch(NumberFormatException ex){out|=3;continue;}
            boolean yes=switch(operator){case EQUAL->cmp==0;case NOT_EQUAL->cmp!=0;case LESS->cmp<0;case LESS_EQUAL->cmp<=0;case GREATER->cmp>0;case GREATER_EQUAL->cmp>=0;default->{out|=3;yield true;}};
            out|=yes?1:2;
        }
        return out==0?3:out;
    }
}
