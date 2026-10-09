package com.imd.cobolexplorer;

import java.util.*;
import java.math.BigDecimal;
import java.lang.ref.WeakReference;
import static com.imd.cobolexplorer.semanticproduct.ControlTopology.*;
import com.imd.cobolexplorer.semanticproduct.ControlTopology;
import static com.imd.cobolexplorer.DependencyRelevance.*;

/** Worklist over shared COBOL control and memoized local invocation results.
 * Environments contain only the backward closure of dependency operands and
 * control predicates. No call-stack or execution-path enumeration. */
final class DependencyFlow {
    record Query(Ast.Statement statement,String type,Ast.Expression expression,Optional<String> literal) { }
    sealed interface Term permits Parameter,Constant,Calculation,Observation,Alternatives,Decision,Refinement,Widening { }
    record Parameter(int declaration) implements Term { }
    record Constant(DependencyValues value) implements Term { }
    static final class Calculation implements Term {
        final String node;final State input;final int declaration;
        Calculation(String node,State input,int declaration){this.node=node;this.input=input;this.declaration=declaration;}
    }
    record Observation(Ast.Expression expression,State input) implements Term { }
    record Alternatives(Set<Term> terms) implements Term { Alternatives {terms=Set.copyOf(terms);} }
    record Widening(Term input) implements Term { }
    private Term open(Term root) {
        record Visit(Term term,boolean expanded) { }
        var translated=new IdentityHashMap<Term,Term>();var pending=new ArrayDeque<Visit>();pending.push(new Visit(root,false));
        while(!pending.isEmpty()) {
            var visit=pending.pop();var term=visit.term();if(translated.containsKey(term))continue;
            if(term instanceof Constant c){translated.put(term,c.equals(EMPTY)?EMPTY:new Constant(c.value().open()));continue;}
            if(!(term instanceof Decision)&&!(term instanceof Alternatives)){translated.put(term,term instanceof Widening?term:new Widening(term));continue;}
            if(!visit.expanded()) {
                pending.push(new Visit(term,true));
                if(term instanceof Decision d){pending.push(new Visit(d.high,false));pending.push(new Visit(d.low,false));}
                else for(var child:((Alternatives)term).terms())pending.push(new Visit(child,false));
            } else if(term instanceof Decision d)translated.put(term,node(d.test,translated.get(d.high),translated.get(d.low)));
            else {Term result=EMPTY;for(var child:((Alternatives)term).terms())result=combine(result,translated.get(child));translated.put(term,result);}
        }
        return translated.get(root);
    }
    private Term combineLeaves(Term first,Term second) {
        if(first.equals(second))return first;
        if(first instanceof Constant a&&second instanceof Constant b)return new Constant(a.value().join(b.value()));
        var terms=new HashSet<Term>();
        if(first instanceof Alternatives a)terms.addAll(a.terms());else terms.add(first);
        if(second instanceof Alternatives a)terms.addAll(a.terms());else terms.add(second);
        if(first instanceof Alternatives a&&a.terms().equals(terms))return first;
        if(second instanceof Alternatives a&&a.terms().equals(terms))return second;
        return new Alternatives(terms);
    }
    record State(Map<Integer,DependencyValues> values,Map<String,Set<String>> handlers,Set<String> reached,Map<Integer,Term> parameters,Term reach,Map<Input,Term> controls) {
        State { values=DependencyEnvironment.copyOf(values);handlers=Map.copyOf(handlers);reached=Set.copyOf(reached);parameters=DependencyEnvironment.copyOf(parameters);controls=Map.copyOf(controls); }
        State(Map<Integer,DependencyValues> values,Map<String,Set<String>> handlers,Set<String> reached){this(values,handlers,reached,Map.of(),YES);}
        State(Map<Integer,DependencyValues> values,Map<String,Set<String>> handlers,Set<String> reached,Map<Integer,Term> parameters){this(values,handlers,reached,parameters,YES);}
        State(Map<Integer,DependencyValues> values,Map<String,Set<String>> handlers,Set<String> reached,Map<Integer,Term> parameters,Term reach){this(values,handlers,reached,parameters,reach,Map.of());}
        Term control(Input input){return controls.getOrDefault(input,input instanceof Handler h?new Constant(new DependencyValues(handlers.getOrDefault(h.name(),Set.of(h.name().startsWith("ABEND")?"UNKNOWN":"DEFAULT")),false)):reached.contains(((Fact)input).statement())?YES:NO);}
        State withReach(Term reach){return new State(values,handlers,reached,parameters,reach,controls);}
        DependencyValues get(int id){return values.getOrDefault(id,DependencyValues.UNKNOWN);}
        Term term(int id){return parameters.getOrDefault(id,new Constant(get(id)));}
        State withValues(Map<Integer,DependencyValues> values){return new State(values,handlers,reached,parameters,reach,controls);}

    }
    // The same value DAG carries branch decisions. Interned ordered decision
    // nodes share suffixes, and operations align operands on the same test so
    // correlated fields are never evaluated as a Cartesian product.
    static final Constant YES=new Constant(DependencyValues.known("true"));
    static final Constant NO=new Constant(DependencyValues.known("false"));
    static final Constant EMPTY=new Constant(new DependencyValues(Set.of(),false));
    record RefinementKey(Ast.Expression condition,boolean wanted,State input,int declaration) { }
    static final class Refinement implements Term {
        private final RefinementKey key;
        Refinement(RefinementKey key){this.key=key;}
        Ast.Expression condition(){return key.condition();}
        boolean wanted(){return key.wanted();}
        State input(){return key.input();}
        int declaration(){return key.declaration();}
    }
    static final class Test {
        final int order;final State input;final Operation operation;
        Test(int order,Operation operation,State input){this.order=order;this.operation=operation;this.input=input;}
    }
    static final class Decision implements Term {
        final DecisionKey key;final Test test;final Term high,low;
        Decision(DecisionKey key){this.key=key;this.test=key.test();this.high=key.high();this.low=key.low();}
    }
    record TestKey(Object condition,State input,Test origin) { }
    record DecisionKey(Test test,Term high,Term low) { }
    record AlgebraKey(boolean choice,Term a,Term b,Term c) { }
    record Operation(String kind,String node,Ast.Expression condition,boolean wanted,int declaration) { }
    record LiftKey(Operation operation,State input) { }
    final Map<TestKey,Test> tests=new HashMap<>();
    // Each live node owns its interning key. The table alone never keeps
    // an obsolete decision graph alive.
    final Map<DecisionKey,WeakReference<Decision>> decisions=new WeakHashMap<>();
    long decisionNodes,decisionOperations,liftedOperations;
    final Map<RefinementKey,Refinement> refinements=new HashMap<>();
    final IdentityHashMap<Term,Optional<DependencyValues>> groundValues=new IdentityHashMap<>();
    private Term node(Test test,Term high,Term low) {
        if(high.equals(low))return high;
        var key=new DecisionKey(test,high,low);var cached=decisions.get(key);
        var result=cached==null?null:cached.get();
        if(result==null){result=new Decision(key);decisions.put(key,new WeakReference<>(result));decisionNodes++;}
        return result;
    }
    private static Test firstTest(Term... terms) {
        Test first=null;for(var term:terms)if(term instanceof Decision d&&(first==null||d.test.order<first.order))first=d.test;
        return first;
    }
    private static Term cofactor(Term term,Test test,boolean high) {
        return term instanceof Decision d&&d.test==test?(high?d.high:d.low):term;
    }
    private Term choose(Term guard,Term high,Term low){if(guard.equals(YES))return high;if(guard.equals(NO))return low;if(high.equals(low))return high;return algebra(new AlgebraKey(true,guard,high,low));}
    private Term combine(Term a,Term b){if(a.equals(b)||b.equals(EMPTY))return a;if(a.equals(EMPTY))return b;return algebra(new AlgebraKey(false,a,b,EMPTY));}
    private Term algebra(AlgebraKey root) {
        var algebra=new HashMap<AlgebraKey,Term>();
        record Visit(AlgebraKey key,boolean expanded) { }
        var pending=new ArrayDeque<Visit>();pending.push(new Visit(root,false));
        while(!pending.isEmpty()) {
            var visit=pending.pop();var key=visit.key();if(algebra.containsKey(key))continue;
            Term simple=null;
            if(key.choice()) {
                if(key.a().equals(YES))simple=key.b();else if(key.a().equals(NO))simple=key.c();else if(key.b().equals(key.c()))simple=key.b();
            }else {
                if(key.a().equals(key.b())||key.b().equals(EMPTY))simple=key.a();else if(key.a().equals(EMPTY))simple=key.b();
            }
            if(simple!=null){algebra.put(key,simple);continue;}
            var test=firstTest(key.a(),key.b(),key.choice()?key.c():EMPTY);
            if(test==null) {
                if(key.choice())throw new IllegalStateException("non-Boolean decision guard");
                algebra.put(key,combineLeaves(key.a(),key.b()));continue;
            }
            var high=new AlgebraKey(key.choice(),cofactor(key.a(),test,true),cofactor(key.b(),test,true),cofactor(key.c(),test,true));
            var low=new AlgebraKey(key.choice(),cofactor(key.a(),test,false),cofactor(key.b(),test,false),cofactor(key.c(),test,false));
            if(visit.expanded())algebra.put(key,node(test,algebra.get(high),algebra.get(low)));
            else {pending.push(new Visit(key,true));if(!algebra.containsKey(low))pending.push(new Visit(low,false));if(!algebra.containsKey(high))pending.push(new Visit(high,false));}
            if(algebra.size()>maxWork)throw new IllegalStateException("RESOURCE_LIMIT: decision operation exceeded --max-work="+maxWork);
        }
        decisionOperations+=algebra.size();return algebra.get(root);
    }
    private State join(State first,State second) {
        if(first==second||first.equals(second))return first;
        if(first.reach().equals(NO))return second;if(second.reach().equals(NO))return first;
        var values=DependencyEnvironment.copyOf(first.values()).join(second.values(),DependencyValues::join);
        var reach=choose(first.reach(),YES,second.reach());
        var overlap=choose(first.reach(),second.reach(),NO);
        var handlers=new HashMap<>(first.handlers());second.handlers().forEach((k,v)->handlers.merge(k,v,DependencyFlow::union));
        var parameters=new DependencyEnvironment.Builder<Term>(Map.of());
        var controls=new HashMap<Input,Term>();
        var fields=new HashSet<Input>();union(first.handlers().keySet(),second.handlers().keySet()).forEach(name->fields.add(new Handler(name)));union(first.reached(),second.reached()).forEach(name->fields.add(new Fact(name)));fields.addAll(first.controls().keySet());fields.addAll(second.controls().keySet());
        for(var field:fields) {
            if(field instanceof Fact)controls.put(field,choose(choose(first.reach(),first.control(field),NO),YES,choose(second.reach(),second.control(field),NO)));
            else controls.put(field,combine(choose(first.reach(),first.control(field),EMPTY),choose(second.reach(),second.control(field),EMPTY)));
        }
        for(int id:values.keySet()) {
            // Recurrence inputs use the finite value lattice. Branch joins
            // must not turn those constants back into symbolic expressions.
            if(specialized.contains(id))continue;
            var a=first.term(id);var b=second.term(id);
            // Reachability belongs to the state. Equal payloads need no copy
            // of that guard; observations and deliveries already apply it.
            Term term=a.equals(b)?a:overlap.equals(NO)?choose(first.reach(),a,b)
                :combine(choose(first.reach(),a,EMPTY),choose(second.reach(),b,EMPTY));
            if(term instanceof Constant constant)values=values.with(id,constant.value());else parameters.put(id,term);
        }
        return new State(values,handlers,union(first.reached(),second.reached()),parameters,reach,controls);
    }
    private Object expressionKey(Ast.Expression expression) {
        if(expression==null)return "absent";
        if(expression instanceof Ast.DataReference r)return List.of("reference",declarations.references.containsKey(r.meta().id())?declarations.references.get(r.meta().id()):List.of("unbound",r.meta().id()),r.subscriptGroups().stream().map(g->g.subscripts().stream().map(this::expressionKey).toList()).toList(),r.referenceModification()==null?List.of():List.of(expressionKey(r.referenceModification().offset()),expressionKey(r.referenceModification().length())));
        if(expression instanceof Ast.LiteralExpression l)return List.of("literal",l.value(),l.logicalText(),l.numericValue(),l.booleanValue(),l.figurativeText());
        if(expression instanceof Ast.RelationCondition r&&r.subject()!=null)return List.of("relation",r.operatorKind(),expressionKey(r.subject()),expressionKey(r.object()));
        if(expression instanceof Ast.ClassCondition c)return List.of("class",c.className(),c.negated(),expressionKey(c.subject()));
        return List.of(expression.getClass(),expression.meta().id());
    }
    record PredicateForm(Ast.Expression condition,boolean positive) { }
    private PredicateForm predicateForm(Ast.Expression condition) {
        boolean positive=true;
        while(true) {
            if(condition instanceof Ast.GroupedCondition g){condition=g.inner();continue;}
            if(condition instanceof Ast.NegatedCondition n){condition=n.operand();positive=!positive;continue;}break;
        }
        if(condition instanceof Ast.RelationCondition r&&r.subject()!=null) {
            Ast.RelationOperator base=switch(r.operatorKind()) {
                case NOT_EQUAL->Ast.RelationOperator.EQUAL;case LESS_EQUAL->Ast.RelationOperator.GREATER;case GREATER_EQUAL->Ast.RelationOperator.LESS;default->r.operatorKind();
            };
            if(base!=r.operatorKind()){positive=!positive;condition=new Ast.RelationCondition(r.meta(),r.subject(),"",r.object(),"",base);}
        }
        return new PredicateForm(condition,positive);
    }
    private Term predicate(Ast.Expression condition,State input) {
        input=conditionInput(condition,input);
        var form=predicateForm(condition);
        if(!form.positive())return choose(predicate(form.condition(),input),NO,YES);
        condition=form.condition();
        if(condition instanceof Ast.LogicalCondition l&&!needsNormalization(condition)) {
            Term result=l.connector()==Ast.LogicalConnector.AND?YES:NO;
            for(var operand:l.operands()){var next=predicate(operand,input);result=l.connector()==Ast.LogicalConnector.AND?choose(result,next,NO):choose(result,YES,next);}return result;
        }
        return lift(new Operation("test","",condition,false,0),input.withReach(YES));
    }
    /** Lift a canonical operation over shared decisions. Cofactoring all read
     * operands together preserves correlations; irrelevant flags are absent
     * from the selected input. This stack also supports long value chains. */
    private Term lift(Operation operation,State input) {
        var lifted=new HashMap<LiftKey,Term>();
        record Visit(LiftKey key,boolean expanded) { }
        var root=new LiftKey(operation,input);var pending=new ArrayDeque<Visit>();pending.push(new Visit(root,false));
        while(!pending.isEmpty()) {
            var visit=pending.pop();var key=visit.key();if(lifted.containsKey(key))continue;
            Test test=null;for(var term:key.input().parameters().values()){var next=firstTest(term);if(next!=null&&(test==null||next.order<test.order))test=next;}
            if(test==null){lifted.put(key,leafOperation(operation,key.input()));continue;}
            var high=new LiftKey(operation,cofactor(key.input(),test,true));var low=new LiftKey(operation,cofactor(key.input(),test,false));
            if(visit.expanded())lifted.put(key,choose(node(test,YES,NO),lifted.get(high),lifted.get(low)));
            else {pending.push(new Visit(key,true));if(!lifted.containsKey(low))pending.push(new Visit(low,false));if(!lifted.containsKey(high))pending.push(new Visit(high,false));}
            if(lifted.size()>maxWork)throw new IllegalStateException("RESOURCE_LIMIT: lifted decisions exceeded --max-work="+maxWork);
        }
        liftedOperations+=lifted.size();return lifted.get(root);
    }
    private State cofactor(State input,Test test,boolean high) {
        var values=new DependencyEnvironment.Builder<>(input.values());var parameters=new DependencyEnvironment.Builder<>(input.parameters());
        for(var entry:input.parameters().entrySet()) {
            var term=cofactor(entry.getValue(),test,high);
            if(term instanceof Constant c){values.put(entry.getKey(),c.value());parameters.remove(entry.getKey());}else parameters.put(entry.getKey(),term);
        }
        return new State(values,input.handlers(),input.reached(),parameters,YES,input.controls());
    }
    private Boolean established(Ast.Expression condition,State input) {
        Boolean established=null;
        for(int id:declarations.reads(condition)) {
            if(!(input.term(id) instanceof Refinement r)||!expressionKey(r.condition()).equals(expressionKey(condition)))return null;
            if(established!=null&&established!=r.wanted())return null;established=r.wanted();
        }
        return established;
    }
    private Term leafOperation(Operation operation,State input) {
        if(input.values().values().contains(EMPTY.value()))return operation.kind().equals("test")||operation.kind().equals("dispatch")||operation.kind().startsWith("count-")?NO:EMPTY;
        if(operation.kind().equals("test")) {
            var known=established(operation.condition(),input);if(known!=null)return known?YES:NO;
        }
        if(operation.kind().equals("test")||operation.kind().equals("count-entry")||operation.kind().equals("count-repeat")||operation.kind().equals("dispatch")) {
            if(input.parameters().isEmpty()) {int truth=testTruth(operation,input);if(truth==1)return YES;if(truth==2)return NO;}
            var test=tests.computeIfAbsent(new TestKey(List.of(operation.kind(),operation.node(),operation.declaration(),expressionKey(operation.condition())),input,null),k->new Test(tests.size(),operation,input));
            if(tests.size()>maxWork)throw new IllegalStateException("RESOURCE_LIMIT: too many predicate inputs");
            return node(test,YES,NO);
        }
        if(operation.kind().equals("read"))return input.parameters().isEmpty()?new Constant(read(operation.condition(),input)):new Observation(operation.condition(),input);
        if(operation.kind().equals("refine")) {
            // An input-independent TOP operand makes either outcome admit
            // every candidate of the other operand. Keep the branch guard,
            // but do not manufacture a different value for an identity filter.
            if(identityFilter(operation.condition(),input))return input.term(operation.declaration());
            var known=established(operation.condition(),input);
            if(known!=null)return known==operation.wanted()?input.term(operation.declaration()):EMPTY;
            if(!input.parameters().isEmpty()) {
                var key=new RefinementKey(operation.condition(),operation.wanted(),input,operation.declaration());
                var result=refinements.computeIfAbsent(key,Refinement::new);
                if(refinements.size()>maxWork)throw new IllegalStateException("RESOURCE_LIMIT: too many parametric refinements");
                return result;
            }
            var filtered=filterConcrete(operation.condition(),operation.wanted(),input);return filtered==null?EMPTY:new Constant(filtered.get(operation.declaration()));
        }
        if(input.parameters().isEmpty()) {
            var result=instantiated.computeIfAbsent(new Computation(operation.node(),input),key->{instantiationEvaluations++;return transfer(statements.get(operation.node()),input);});
            if(instantiated.size()>maxWork)throw new IllegalStateException("RESOURCE_LIMIT: too many instantiated calculations");
            return new Constant(result.get(operation.declaration()));
        }
        var key=new CalculationKey(operation.node(),input,operation.declaration());
        var term=calculations.computeIfAbsent(key,k->new Calculation(k.node(),k.input(),k.declaration()));
        if(calculations.size()>maxWork)throw new IllegalStateException("RESOURCE_LIMIT: too many parametric calculations");return term;
    }
    private int testTruth(Operation operation,State input) {
        if(operation.kind().equals("test"))return truth(operation.condition(),input);
        int number=integer(operation.condition(),input);
        if(number<0)return 3;
        if(operation.kind().equals("count-entry"))return number==0?1:2;
        if(operation.kind().equals("count-repeat"))return number==1?1:3;
        boolean yes=operation.node().equals("normal")?number==0||number>operation.declaration():operation.node().equals("target-"+(number-1));
        return yes?1:2;
    }
    private State guard(State state,Term condition,boolean wanted) {
        var reach=choose(state.reach(),wanted?condition:choose(condition,NO,YES),NO);
        return reach.equals(NO)?null:state.withReach(reach);
    }
    private State filter(Ast.Expression condition,boolean wanted,State state) {
        if(state==null)return null;
        var form=predicateForm(condition);condition=form.condition();if(!form.positive())wanted=!wanted;
        var selected=conditionInput(condition,state);
        // Finite recurrence inputs admit both feasible outcomes without creating
        // a fresh Boolean identity on each passage.
        if(!requiresDecision(selected))return filterConcrete(condition,wanted,state);
        var guard=predicate(condition,selected);if(!wanted)guard=choose(guard,NO,YES);
        var reach=choose(state.reach(),guard,NO);if(reach.equals(NO))return null;
        if(guard.equals(YES))return state.withReach(reach);
        if(state.parameters().isEmpty()) {var result=filterConcrete(condition,wanted,state);return result==null?null:result.withReach(reach);}
        var values=new DependencyEnvironment.Builder<>(state.values());var parameters=new DependencyEnvironment.Builder<>(state.parameters());
        var targets=new HashSet<Integer>(expressionInputs(condition,state));
        for(int id:new ArrayList<>(targets))targets.addAll(declarations.equivalents.getOrDefault(id,Set.of(id)));
        for(int group:textGroups)if(!Collections.disjoint(targets,declarations.leaves(group)))targets.add(group);
        var refineInputs=new HashSet<Input>();expressionInputs(condition,state).forEach(id->refineInputs.add(new Value(id)));targets.forEach(id->refineInputs.add(new Value(id)));
        var refinement=Selection.of(refineInputs).apply(state).withReach(YES);
        for(int id:targets) {
            var term=lift(new Operation("refine","",condition,wanted,id),refinement);
            if(term instanceof Constant c){values.put(id,c.value());parameters.remove(id);}else {values.put(id,DependencyValues.UNKNOWN);parameters.put(id,term);}
        }
        return new State(values,state.handlers(),state.reached(),parameters,reach,state.controls());
    }
    private boolean identityFilter(Ast.Expression condition,State input) {
        if(!(condition instanceof Ast.RelationCondition relation)||relation.subject()==null)return false;
        for(var operand:List.of(relation.subject(),relation.object()))
            if(expressionInputs(operand,input).isEmpty()&&read(operand,input).equals(DependencyValues.UNKNOWN))return true;
        return false;
    }
    private boolean requiresDecision(State input) {
        return !input.parameters().isEmpty();
    }
    private State conditionInput(Ast.Expression condition,State input) {
        var selected=new HashSet<Input>();for(int id:expressionInputs(condition,input))selected.add(new Value(id));
        return Selection.of(selected).apply(input).withReach(YES);
    }
    /** Reads consume the already-updated nominal value. Possible write aliases
     * belong to the write effect, not to every subsequent read or refinement. */
    private Set<Integer> expressionInputs(Ast.Expression expression,State input) {
        var ids=new HashSet<Integer>();var pending=new ArrayDeque<Ast.Node>();if(expression!=null)pending.add(expression);
        while(!pending.isEmpty()) {
            var next=pending.removeFirst();
            if(next instanceof Ast.DataReference reference) {
                Integer id=declarations.references.get(reference.meta().id());
                if(id!=null) {
                    id=declarations.conditions.getOrDefault(id,id);
                    if(textGroups.contains(id)&&input.values().containsKey(id))ids.add(id);
                    else ids.addAll(declarations.leaves(id));
                    ids.addAll(tableValueIds.getOrDefault(id,List.of()));
                }
            }
            pending.addAll(DependencyDeclarations.valueChildren(next));
        }
        ids.retainAll(demand);return Set.copyOf(ids);
    }
    private DependencyValues ground(Term root) {
        record Visit(Term term,boolean expanded) { }
        var pending=new ArrayDeque<Visit>();pending.push(new Visit(root,false));
        while(!pending.isEmpty()) {
            var visit=pending.pop();var term=visit.term();if(groundValues.containsKey(term))continue;
            if(term instanceof Constant c){groundValues.put(term,Optional.of(c.value()));continue;}
            if(term instanceof Decision d&&d.test.input.parameters().isEmpty()) {
                if(!visit.expanded()){pending.push(new Visit(term,true));pending.push(new Visit(d.low,false));pending.push(new Visit(d.high,false));continue;}
                var high=groundValues.get(d.high);var low=groundValues.get(d.low);int truth=testTruth(d.test.operation,d.test.input);
                groundValues.put(term,high.isPresent()&&low.isPresent()?Optional.of(truth==1?high.get():truth==2?low.get():high.get().join(low.get())):Optional.empty());
            }else if(term instanceof Alternatives a) {
                if(!visit.expanded()){pending.push(new Visit(term,true));for(var child:a.terms())pending.push(new Visit(child,false));continue;}
                DependencyValues value=EMPTY.value();boolean complete=true;
                for(var child:a.terms()){var v=groundValues.get(child);if(v.isEmpty()){complete=false;break;}value=value.join(v.get());}
                groundValues.put(term,complete?Optional.of(value):Optional.empty());
            }else groundValues.put(term,Optional.empty());
        }
        return groundValues.get(root).orElse(null);
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
    record Location(int context,String node,Exit result,Continuation caller) {
        Location(int context,String node){this(context,node,null,null);}
    }
    sealed interface Continuation permits Forward,ReturnTo,Entry {
        int context();String endpoint();State input();
    }
    record Forward(int context,String endpoint,State input) implements Continuation { }
    record ReturnTo(int context,String binding,String endpoint,State input) implements Continuation { }
    record Entry(int context,String endpoint,State input) implements Continuation {
        Entry(String endpoint,State input){this(-1,endpoint,input);}
    }
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
    final IdentityHashMap<Ast.EvaluateBranch,Ast.Expression> evaluateConditions=new IdentityHashMap<>();
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
    record WriteSupport(Set<Integer> changed,Map<Integer,Set<Integer>> widenings) { }
    final IdentityHashMap<Ast.Statement,WriteSupport> writeSupports=new IdentityHashMap<>();
    final List<Query> queries;
    final Set<Integer> demand=new HashSet<>();
    final Set<Integer> specialized=new HashSet<>();
    final Map<Point,Integer> forwardOrder;
    record CalculationKey(String node,State input,int declaration) { }
    final Map<CalculationKey,Term> calculations=new HashMap<>();
    final Map<Computation,State> instantiated=new HashMap<>();
    long instantiationEvaluations;
    final Set<Integer> textGroups=new HashSet<>();
    record Element(int declaration,List<Integer> subscripts) { Element {subscripts=List.copyOf(subscripts);} }
    final Map<Element,Integer> elements=new HashMap<>();
    /** A remainder value represents every position without a demanded cell. */
    record Table(List<Integer> dimensions,int remainder) {
        Table { dimensions=List.copyOf(dimensions); }
        boolean contains(List<Integer> indexes) {
            if(indexes.size()!=dimensions.size())return false;
            for(int i=0;i<indexes.size();i++)if(indexes.get(i)<1||indexes.get(i)>dimensions.get(i))return false;
            return true;
        }
        long cardinality() {
            long count=1;for(int n:dimensions){if(n==0||count>Long.MAX_VALUE/n)return Long.MAX_VALUE;count*=n;}return count;
        }
    }
    final Map<Integer,Table> tables=new HashMap<>();
    final Map<Integer,List<Integer>> tableValueIds=new HashMap<>();
    final Set<Element> requestedElements;
    boolean tableDemandExpanded;
    int tableDemandPasses=1;
    int nextElementId;
    final List<Context> contexts=new ArrayList<>();
    final Map<SummaryKey,Integer> memo=new HashMap<>();
    final Set<String> paragraphEntries=new HashSet<>();
    final Set<String> prerequisites=new HashSet<>();
    final DependencyRelevance relevance;
    final DependencyControl controlSummary;
    final Map<String,Point> controlEntries=new HashMap<>();
    final Map<Point,Point> controlEntryAliases=new HashMap<>();
    record DeliveryKey(String binding,String endpoint,Exit exit) { }
    final Map<DeliveryKey,DependencyControl.Effect> continuationPlans=new HashMap<>();
    final Map<Point,Selection> projections=new HashMap<>();
    final Map<String,Selection> operands=new HashMap<>();
    record Computation(String node,State input) { }
    final Map<Computation,List<Action>> computations=new HashMap<>();
    long evaluations,reusedEvaluations;
    /** A local transformation reads only its operands and preserves the rest of
     * each caller. Explicit writes, including UNKNOWN, are returned as patches. */
    record Selection(DependencyEnvironment.Projection<DependencyValues> values,Set<String> handlers,Set<String> facts) {
        static Selection of(Set<Input> inputs) {
            var values=new HashSet<Integer>();var handlers=new HashSet<String>();var facts=new HashSet<String>();
            for(var input:inputs) {
                if(input instanceof Value value)values.add(value.declaration());
                else if(input instanceof Handler handler)handlers.add(handler.name());
                else if(input instanceof Fact fact)facts.add(fact.statement());
            }
            return new Selection(new DependencyEnvironment.Projection<DependencyValues>(values),Set.copyOf(handlers),Set.copyOf(facts));
        }
        State apply(State input) {
            var selectedHandlers=new HashMap<String,Set<String>>();var selectedFacts=new HashSet<String>();
            for(String name:handlers)if(input.handlers().containsKey(name))selectedHandlers.put(name,input.handlers().get(name));
            for(String fact:facts)if(input.reached().contains(fact))selectedFacts.add(fact);
            var selected=values.apply(input.values());var params=new HashMap<Integer,Term>();
            input.parameters().forEach((id,term)->{if(selected.containsKey(id))params.put(id,term);});
            var controls=new HashMap<Input,Term>();
            for(String name:handlers){var field=new Handler(name);if(input.controls().containsKey(field))controls.put(field,input.control(field));}
            for(String name:facts){var field=new Fact(name);if(input.controls().containsKey(field))controls.put(field,input.control(field));}
            return new State(selected,selectedHandlers,selectedFacts,params,input.reach(),controls);
        }
    }
    record Patch(State result,Set<Integer> removed) {
        static Patch of(State input,State result) {
            var removed=new HashSet<>(input.values().keySet());removed.removeAll(result.values().keySet());
            var values=new HashMap<Integer,DependencyValues>();var handlers=new HashMap<String,Set<String>>();var params=new HashMap<Integer,Term>();
            result.values().forEach((id,value)->{if(!value.equals(input.values().get(id)))values.put(id,value);});
            for(int id:union(input.parameters().keySet(),result.parameters().keySet()))if(!Objects.equals(input.parameters().get(id),result.parameters().get(id))) {
                if(result.values().containsKey(id))values.put(id,result.get(id));
                if(result.parameters().containsKey(id))params.put(id,result.parameters().get(id));
            }
            result.handlers().forEach((name,value)->{if(!value.equals(input.handlers().get(name)))handlers.put(name,value);});
            var reached=new HashSet<>(result.reached());reached.removeAll(input.reached());
            var controls=new HashMap<Input,Term>();
            for(var field:union(input.controls().keySet(),result.controls().keySet()))if(!input.control(field).equals(result.control(field)))controls.put(field,result.control(field));
            return new Patch(new State(values,handlers,reached,params,result.reach(),controls),Set.copyOf(removed));
        }
        State apply(State input,DependencyFlow flow) {
            if(result.reach().equals(YES)&&removed.isEmpty()&&result.values().isEmpty()&&result.handlers().isEmpty()&&result.reached().isEmpty()&&result.controls().isEmpty())return input;
            var values=new DependencyEnvironment.Builder<>(input.values());removed.forEach(values::remove);values.putAll(result.values());
            var handlers=new HashMap<>(input.handlers());handlers.putAll(result.handlers());
            var params=new DependencyEnvironment.Builder<>(input.parameters());removed.forEach(params::remove);result.values().keySet().forEach(params::remove);params.putAll(result.parameters());
            var controls=new HashMap<>(input.controls());result.handlers().keySet().forEach(name->controls.remove(new Handler(name)));result.reached().forEach(name->controls.remove(new Fact(name)));controls.putAll(result.controls());
            return new State(values,handlers,union(input.reached(),result.reached()),params,flow.choose(input.reach(),result.reach(),NO),controls);
        }
    }
    sealed interface Action permits Advance,Phase,Enter { Patch patch(); }
    record Advance(Exit target,Patch patch) implements Action { }
    record Phase(String binding,String phase,Patch patch) implements Action { }
    record Enter(Exit entry,String endpoint,Patch patch) implements Action { }
    final Map<Exit,Effect> accessEffects=new HashMap<>();
    final Map<Location,State> before=new HashMap<>();
    // Reverse postorder coalesces incoming branch states before visiting their
    // successors, avoiding a wave of transient decision DAGs through every suffix.
    final PriorityQueue<Location> work=new PriorityQueue<>(Comparator.comparingInt(this::workRank).thenComparingInt(Location::context).thenComparing(Location::node));
    private int workRank(Location location) {
        if(location.result()!=null)return -1;
        return forwardOrder.getOrDefault(new Point(new Exit(TargetKind.OCCURRENCE,location.node()),contexts.get(location.context()).key.endpoint()),Integer.MAX_VALUE);
    }
    final Set<Location> queued=new HashSet<>();
    final Set<String> diagnostics=new TreeSet<>();
    final Map<Query,DependencyValues> answers=new LinkedHashMap<>();
    final long maxWork;
    long visits;
    long resultDeliveries;

    /** Close the cell demand using the same value solver. Computed indexes may
     * become known only when a parametric summary is instantiated. Restarting
     * after a new cell makes all projections and memo keys use one fixed schema;
     * no answer from an incomplete footprint is published. */
    static DependencyFlow analyze(CompilationUnitModel.ProgramUnit unit,DependencyDeclarations declarations,
            ControlTopology control,List<Query> queries,long maxWork,CicsProgramControlAnalyzer.Contribution cics,Map<Integer,ConditionNameSemantics.Use> conditions) {
        var cells=new LinkedHashSet<Element>();
        long work=0,evaluations=0,reused=0,instantiated=0,resolved=0,delivered=0,decisions=0,operations=0,lifted=0,controlWork=0,controlPairs=0,controlFacts=0;
        int passes=0;
        while(true) {
            var flow=new DependencyFlow(unit,declarations,control,queries,maxWork,cics,conditions,cells);passes++;
            work+=flow.visits;evaluations+=flow.evaluations;reused+=flow.reusedEvaluations;instantiated+=flow.instantiationEvaluations;
            resolved+=flow.resolutionVisits;delivered+=flow.resultDeliveries;decisions+=flow.decisionNodes;operations+=flow.decisionOperations;lifted+=flow.liftedOperations;controlWork+=flow.controlSummary.visits;controlPairs+=flow.controlSummary.resultPairs;controlFacts+=flow.controlSummary.resultFacts;
            if(work>maxWork)throw new IllegalStateException("RESOURCE_LIMIT: table demand closure exceeded --max-work="+maxWork);
            if(flow.tableDemandExpanded)continue;
            flow.visits=work;flow.evaluations=evaluations;flow.reusedEvaluations=reused;flow.instantiationEvaluations=instantiated;flow.resolutionVisits=resolved;
            flow.resultDeliveries=delivered;flow.decisionNodes=decisions;flow.decisionOperations=operations;flow.liftedOperations=lifted;flow.tableDemandPasses=passes;flow.controlSummary.visits=controlWork;flow.controlSummary.resultPairs=controlPairs;flow.controlSummary.resultFacts=controlFacts;return flow;
        }
    }
    private DependencyFlow(CompilationUnitModel.ProgramUnit unit,DependencyDeclarations declarations,
            ControlTopology control,List<Query> queries,long maxWork,CicsProgramControlAnalyzer.Contribution cics,Map<Integer,ConditionNameSemantics.Use> conditions,Set<Element> requestedElements) {
        this.declarations=declarations;this.queries=queries;this.maxWork=maxWork;this.requestedElements=requestedElements;
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
        var roots=new ArrayList<Point>();
        control.regions().stream().filter(r->r.kind()==RegionKind.PROCEDURE).findFirst().ifPresent(r->{
            String endpoint="boundary:"+r.id();roots.add(new Point(Exit.of(r.entry()),endpoint));
            for(var entry:control.entryPoints())roots.add(new Point(Exit.of(entry.target()),endpoint));
        });
        var observations=new HashSet<String>();queries.forEach(q->observations.add(handle(q.statement())));
        controlSummary=new DependencyControl(roots.stream().map(this::controlEntry).toList(),this::controlEffect,this::controlDelivery,p->{
            var statement=statements.get(p.exit().reference());
            return observations.contains(p.exit().reference())||p.exit().kind()==TargetKind.UNKNOWN_LOCAL
                ||statement!=null&&StatementEffectSummary.of(statement).filter(e->e.unknownWriteBound()==StatementEffectSummary.Bound.ALL).isPresent()
                ||statement!=null&&hasUnsupportedPredicate(statement);
        },maxWork);
        for(var q:queries)if(controlSummary.reachable(handle(q.statement())))demand.addAll(declarations.reads(q.expression()));
        // Control reads matter even when a branch chooses literal dependency sites.
        for(var s:statements.values()) {
            if(!controlSummary.observed(handle(s)))continue;
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
                }pending.addAll(DependencyDeclarations.valueChildren(n));
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
            int remainder=nextElementId++;tables.put(id,new Table(dimensions,remainder));
            tableValueIds.put(id,new ArrayList<>(List.of(remainder)));demand.add(remainder);
        }
        // Literal indexes need no discovery pass. All other indexes are learned
        // by ordinary transfer/read operations, including caller substitution.
        var unknownInput=new State(Map.of(),Map.of(),Set.of());
        for(var expression:expressions.values())if(expression instanceof Ast.DataReference reference&&!reference.subscriptGroups().isEmpty()) {
            Integer id=declarations.references.get(reference.meta().id());if(id==null)continue;
            var indexes=reference.subscriptGroups().stream().flatMap(g->g.subscripts().stream()).map(e->integer(e,unknownInput)).toList();
            requestElement(id,indexes);
        }
        for(var cell:requestedElements)if(tables.containsKey(cell.declaration())) {
            if(elements.size()>=maxWork)throw new IllegalStateException("RESOURCE_LIMIT: demanded logical table elements");
            int slot=nextElementId++;elements.put(cell,slot);tableValueIds.get(cell.declaration()).add(slot);demand.add(slot);
        }
        tableDemandExpanded=false;
        var cyclic=controlSummary.cyclicNodes();forwardOrder=controlSummary.forwardOrder();
        for(var statement:statements.values()) {
            if(cyclic.contains(handle(statement))) {
                specialized.addAll(potentialWrites(statement));
                // Repeated refinements are recurrence equations too. Keep their
                // finite value lattice rather than unroll nested symbolic filters.
                if(statement instanceof Ast.IfStatement||statement instanceof Ast.EvaluateStatement||statement instanceof Ast.PerformStatement||statement instanceof Ast.GoToStatement)
                    specialized.addAll(declarations.reads(statement));
            }
        }
        for(var binding:bindings.values())if(binding.phases().stream().anyMatch(p->cyclic.contains("phase/"+binding.id()+"/"+p.id()))) {
            var statement=statements.get(binding.caller());
            if(statement instanceof Ast.PerformStatement perform)perform.controls().forEach(c->specialized.addAll(declarations.reads(c.expression())));
        }
        do {
            int size=specialized.size();
            for(var statement:statements.values()) {
                if(!Collections.disjoint(potentialWrites(statement),specialized))specialized.addAll(declarations.reads(statement));
                // Refinement is an operation on all condition operands. A
                // finite recurrence operand cannot be refined through a
                // symbolic peer and re-enter the expression DAG indirectly.
                if(statement instanceof Ast.IfStatement branch) {
                    var reads=declarations.reads(branch.condition());
                    if(!Collections.disjoint(reads,specialized))specialized.addAll(reads);
                } else if(statement instanceof Ast.EvaluateStatement||statement instanceof Ast.PerformStatement||statement instanceof Ast.GoToStatement) {
                    var reads=declarations.reads(statement);
                    if(!Collections.disjoint(reads,specialized))specialized.addAll(reads);
                }
            }
            specialized.addAll(relatedValues(specialized));changed=size!=specialized.size();
        }while(changed);
        specialized.retainAll(demand);
        relevance=new DependencyRelevance(this::inputEffect,maxWork);
        State initial=initial();
        for(var root:roots)subscribe(new Entry(root.endpoint(),initial),root.exit());
        while(!work.isEmpty()) {
            if(++visits>maxWork)throw new IllegalStateException("RESOURCE_LIMIT: dataflow did not complete within --max-work="+maxWork);
            var location=work.remove();queued.remove(location);
            if(location.result()!=null) {
                var ctx=contexts.get(location.context());
                deliver(location.caller(),location.result(),ctx.results.get(location.result()));
            }else step(location,before.get(location));
        }
        var queriesAt=new HashMap<String,List<Query>>();
        for(var q:queries)queriesAt.computeIfAbsent(handle(q.statement()),k->new ArrayList<>()).add(q);
        for(var e:before.entrySet())for(var q:queriesAt.getOrDefault(e.getKey().node(),List.of())) {
            var value=resolve(e.getKey().context(),observe(q,e.getValue()));
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
                        var groups=new DependencyEnvironment.Builder<>(state.values());
                        groups.put(d.meta().id(),seed.fit(declarations.widths.getOrDefault(d.meta().id(),0)));state=state.withValues(groups);
                    }
                }
                if(!tableValueIds.getOrDefault(d.meta().id(),List.of()).isEmpty()) {
                    var outValues=new DependencyEnvironment.Builder<>(state.values());
                    for(int slot:tableValueIds.get(d.meta().id()))outValues.put(slot,state.get(d.meta().id()));state=state.withValues(outValues);
                }
            }
        }
        // Declarative textual tables can expose a different, repeated logical
        // structure. Slice only characters actually supported by the declarations.
        for(var view:declarations.textualViews.entrySet()) {
            var value=readDeclaration(view.getKey(),state);
            if(!value.values().isEmpty())for(int alias:view.getValue())state=decode(alias,value,state,0,false);
        }
        var snapshots=new DependencyEnvironment.Builder<>(state.values());for(int id:textGroups) {
            var value=readDeclaration(id,state);
            if(value.values().isEmpty()) {
                var seed=singletonTableText(id,state,List.of(),false);
                if(seed.isPresent())value=new DependencyValues(Set.of(seed.get()),true);
            }
            snapshots.put(id,value);
        }
        return state.withValues(snapshots);
    }
    private void requestElement(int id,List<Integer> indexes) {
        var table=tables.get(id);if(table==null||!table.contains(indexes))return;
        for(int alias:declarations.equivalents.getOrDefault(id,Set.of(id))) {
            var other=tables.get(alias);if(other==null||!other.contains(indexes))continue;
            if(requestedElements.add(new Element(alias,indexes)))tableDemandExpanded=true;
        }
    }
    private DependencyValues tableSummary(int id,State state) {
        var table=tables.get(id);if(table==null)return state.get(id);
        var ids=tableValueIds.get(id);
        DependencyValues value=ids.size()-1<table.cardinality()?state.get(table.remainder()):new DependencyValues(Set.of(),false);
        for(int slot:ids)if(slot!=table.remainder())value=value.join(state.get(slot));return value;
    }
    private Optional<String> conditionText(Ast.ConditionValue v) {
        return switch(v.kind()){case TEXT,NUMBER->Optional.of(v.value());case SPACES->Optional.of(" ");case ZERO->Optional.of("0");default->Optional.empty();};
    }
    private Set<Integer> nominalWrites(Ast.Statement statement) {
        var targets=new ArrayList<Ast.Expression>();
        if(statement instanceof Ast.MoveStatement move)targets.addAll(move.targets());
        else if(statement instanceof Ast.CallStatement call) {
            for(var argument:call.arguments())if(argument.passingMode()!=Ast.PassingMode.CONTENT&&argument.passingMode()!=Ast.PassingMode.VALUE)targets.add(argument.value());
            if(call.returning()!=null)targets.add(call.returning());
        } else if(statement instanceof Ast.EmbeddedLanguageStatement embedded)
            embedded.hostOperands().stream().filter(h->h.role()!=Ast.EmbeddedHostRole.READ).forEach(h->targets.add(h.reference()));
        else StatementEffectSummary.of(statement).ifPresent(effect->{targets.addAll(effect.mayWrites());targets.addAll(effect.sourceTargets());targets.addAll(effect.exposedRegions());});
        var ids=new HashSet<Integer>();
        for(var target:targets)if(target instanceof Ast.DataReference reference) {
            Integer id=declarations.references.get(reference.meta().id());if(id!=null){id=declarations.conditions.getOrDefault(id,id);ids.add(id);ids.addAll(declarations.leaves(id));}
        }else ids.addAll(declarations.reads(target));
        return ids;
    }
    private Set<Integer> writes(Ast.Statement statement) {
        return writeCache.computeIfAbsent(statement,key->{
            var ids=new HashSet<Integer>();for(int id:nominalWrites(key))ids.addAll(declarations.related(id));return Set.copyOf(ids);
        });
    }
    private WriteSupport writeSupport(Ast.Statement statement) {
        return writeSupports.computeIfAbsent(statement,key->{
            var effect=StatementEffectSummary.of(key);
            boolean direct=key instanceof Ast.MoveStatement||key instanceof Ast.ModeledStatement modeled&&modeled.conditionSet().isPresent()
                ||effect.filter(e->e.proof()==StatementEffectSummary.Proof.INITIALIZE_TARGETS).isPresent();
            var written=new HashSet<>(direct?nominalWrites(key):potentialWrites(key));var decoded=new HashSet<Integer>();boolean changed;
            do {
                int size=written.size();
                for(int id:new ArrayList<>(written)) {
                    written.addAll(declarations.equivalents.getOrDefault(id,Set.of(id)));written.addAll(tableValueIds.getOrDefault(id,List.of()));
                    Integer ancestor=id;
                    while(ancestor!=null) {
                        for(int view:declarations.textualViews.getOrDefault(ancestor,Set.of())){written.add(view);written.addAll(declarations.leaves(view));decoded.addAll(declarations.leaves(view));}
                        ancestor=declarations.parent.get(ancestor);
                    }
                }
                changed=size!=written.size();
            }while(changed);
            var aliases=new HashSet<Integer>();
            for(int id:written)for(int alias:declarations.possibleAliases.getOrDefault(id,Set.of())){aliases.add(alias);aliases.addAll(tableValueIds.getOrDefault(alias,List.of()));}
            aliases.removeAll(written);aliases.removeAll(textGroups);aliases.retainAll(demand);
            var affected=new HashSet<>(written);affected.addAll(aliases);
            for(int group:textGroups)if(!Collections.disjoint(affected,declarations.leaves(group)))affected.add(group);
            var widenings=new HashMap<Integer,Set<Integer>>();for(int id:aliases)widenings.put(id,Set.of(id));
            if(!direct)for(int id:written)if(!decoded.contains(id)&&!textGroups.contains(id)
                    &&declarations.children.getOrDefault(id,List.of()).isEmpty()) {
                var reads=new HashSet<Integer>();reads.add(id);
                for(int peer:declarations.equivalents.getOrDefault(id,Set.of(id)))if(written.contains(peer))reads.add(peer);
                widenings.put(id,Set.copyOf(reads));
                for(int slot:tableValueIds.getOrDefault(id,List.of()))widenings.put(slot,Set.of(slot));
            }
            // A group with unproven text extent cannot decode the incoming
            // value. The canonical write opens its previous leaves instead.
            if(key instanceof Ast.MoveStatement move)for(var target:move.targets())if(target instanceof Ast.DataReference reference) {
                Integer group=declarations.references.get(reference.meta().id());
                if(group!=null&&declarations.widths.getOrDefault(group,0)==0&&!declarations.children.getOrDefault(group,List.of()).isEmpty()) {
                    var leaves=new HashSet<>(declarations.leaves(group));
                    for(int leaf:leaves)for(int id:declarations.equivalents.getOrDefault(leaf,Set.of(leaf)))if(!decoded.contains(id)&&demand.contains(id)) {
                        var reads=new HashSet<Integer>();reads.add(id);
                        for(int peer:declarations.equivalents.getOrDefault(leaf,Set.of(leaf)))if(leaves.contains(peer))reads.add(peer);
                        widenings.put(id,Set.copyOf(reads));
                    }
                    for(int leaf:leaves)for(int id:tableValueIds.getOrDefault(leaf,List.of()))if(!decoded.contains(id))widenings.put(id,Set.of(id));
                }
            }
            affected.retainAll(demand);return new WriteSupport(Set.copyOf(affected),Map.copyOf(widenings));
        });
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
        requestElement(id,indexes);
        Integer slot=elements.get(new Element(id,indexes));
        return slot==null?(tables.containsKey(id)&&tables.get(id).contains(indexes)?value.open():DependencyValues.UNKNOWN):state.get(slot);
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
        if(!changedGroups.isEmpty()) {var out=new DependencyEnvironment.Builder<>(state.values());changedGroups.forEach(out::remove);state=state.withValues(out);}
        state=writeLocal(id,value,state,weak);
        if(!weak&&!declarations.children.getOrDefault(id,List.of()).isEmpty()&&declarations.leaves(id).stream().anyMatch(tableValueIds::containsKey)) {
            state=decode(id,value.fit(declarations.widths.getOrDefault(id,0)),state,0,false);
            var exact=new DependencyEnvironment.Builder<>(state.values());
            for(int leaf:declarations.leaves(id))if(tableValueIds.containsKey(leaf)) {
                exact.put(leaf,tableSummary(leaf,state));
            }state=state.withValues(exact);
        }
        var ancestors=new LinkedHashSet<Integer>();ancestors.add(id);
        for(int leaf:declarations.leaves(id)){Integer ancestor=leaf;while(ancestor!=null){ancestors.add(ancestor);ancestor=declarations.parent.get(ancestor);}}
        for(int ancestor:ancestors)for(int alias:declarations.textualViews.getOrDefault(ancestor,Set.of()))state=decode(alias,readDeclaration(ancestor,state),state,0,weak);
        if(!changedGroups.isEmpty()) {
            var out=new DependencyEnvironment.Builder<>(state.values());
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
        // The remainder collects only unmaterialized positions. A full group
        // write replaces it; an unknown-index write merely widens it.
        var remainders=new HashMap<Integer,DependencyValues>();
        state=decode(id,text,state,offset,weak,List.of(),remainders);
        var out=new DependencyEnvironment.Builder<>(state.values());
        for(var entry:remainders.entrySet()) {
            var table=tables.get(entry.getKey());var value=entry.getValue();
            out.put(table.remainder(),weak?state.get(table.remainder()).join(value).open():value);
        }
        state=state.withValues(out);
        out=new DependencyEnvironment.Builder<>(state.values());
        for(int leaf:declarations.leaves(id))if(tables.containsKey(leaf))out.put(leaf,tableSummary(leaf,state));
        return state.withValues(out);
    }
    private State decode(int id,DependencyValues text,State state,int offset,boolean weak,List<Integer> path,Map<Integer,DependencyValues> remainders) {
        int width=declarations.widths.getOrDefault(id,0);if(width==0)return state;
        if(text.values().isEmpty()) {
            var out=new DependencyEnvironment.Builder<>(state.values());
            for(int leaf:declarations.leaves(id)) {
                if(!demand.contains(leaf))continue;
                for(int alias:declarations.equivalents.getOrDefault(leaf,Set.of(leaf))) {
                    out.put(alias,weak?state.get(alias).open():DependencyValues.UNKNOWN);
                    for(int slot:tableValueIds.getOrDefault(alias,List.of()))out.put(slot,weak?state.get(slot).open():DependencyValues.UNKNOWN);
                }
            }
            return state.withValues(out);
        }
        int count=declarations.counts.getOrDefault(id,1);var cs=declarations.children.getOrDefault(id,List.of());
        if((long)count*width>maxWork)throw new IllegalStateException("RESOURCE_LIMIT: logical table text");
        for(int i=0;i<count;i++) {
            int base=offset+i*width;
            var current=new ArrayList<>(path);
            if(declarations.entries.get(id).clauses().stream().anyMatch(Ast.OccursClause.class::isInstance))current.add(i+1);
            if(cs.isEmpty()) {
                var value=text.map(s->base+width<=s.length()?s.substring(base,base+width):null);
                state=writeLocal(id,value,state,weak||declarations.repeated.contains(id));
                var out=new DependencyEnvironment.Builder<>(state.values());
                for(int alias:declarations.equivalents.getOrDefault(id,Set.of(id))) {
                    Integer slot=elements.get(new Element(alias,current));
                    if(slot!=null)out.put(slot,weak?state.get(slot).join(value).open():value);
                    else if(tables.containsKey(alias))remainders.merge(alias,value,DependencyValues::join);
                }
                state=state.withValues(out);
            }else {int start=base;for(int c:cs) {
                if(declarations.entries.get(c).clauses().stream().anyMatch(Ast.RedefinesClause.class::isInstance))continue;
                state=decode(c,text,state,start,weak,current,remainders);start+=declarations.widths.getOrDefault(c,0)*declarations.counts.getOrDefault(c,1);
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
            if(!next.equals(state.get(target))||!state.values().containsKey(target)) {if(out==null)out=new DependencyEnvironment.Builder<>(state.values());out.put(target,next);}
        }
        if(weak)for(int alias:declarations.equivalents.getOrDefault(id,Set.of(id)))for(int slot:tableValueIds.getOrDefault(alias,List.of()))if(!state.get(slot).unknown()) {
            if(out==null)out=new DependencyEnvironment.Builder<>(state.values());out.put(slot,state.get(slot).open());
        }
        for(int alias:declarations.possibleAliases.getOrDefault(id,Set.of()))if(demand.contains(alias)) {
            if(!state.get(alias).unknown()) {if(out==null)out=new DependencyEnvironment.Builder<>(state.values());out.put(alias,state.get(alias).open());}
            for(int slot:tableValueIds.getOrDefault(alias,List.of()))if(!state.get(slot).unknown()) {
                if(out==null)out=new DependencyEnvironment.Builder<>(state.values());out.put(slot,state.get(slot).open());
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
            requestElement(id,indexes);
            State out=write(id,value,state,true);var changed=new DependencyEnvironment.Builder<>(out.values());
            Integer selected=indexes.stream().allMatch(i->i>0)?elements.get(new Element(id,indexes)):null;
            if(selected!=null) {
                for(int alias:declarations.equivalents.getOrDefault(id,Set.of(id))) {
                    Integer aliasSlot=elements.get(new Element(alias,indexes));if(aliasSlot==null)continue;
                    for(int slot:tableValueIds.get(alias))changed.put(slot,state.get(slot));
                    changed.put(aliasSlot,fitField(alias,value));
                    changed.put(alias,tableSummary(alias,out.withValues(changed)));
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
            }else for(int alias:declarations.equivalents.getOrDefault(id,Set.of(id)))for(int slot:tableValueIds.getOrDefault(alias,List.of()))changed.put(slot,out.get(slot).join(fitField(alias,value)).open());
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
                int fallback=tables.containsKey(id)?tables.get(id).remainder():id;
                int slot=elements.getOrDefault(new Element(id,current),fallback);var v=state.get(slot);
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
        if(tableValueIds.containsKey(id)) {
            var initialized=new DependencyEnvironment.Builder<>(state.values());initialized.put(id,value);
            for(int slot:tableValueIds.get(id))initialized.put(slot,value);state=state.withValues(initialized);
        }return state;
    }
    private void enqueue(int context,String node,State state) {
        if(state.reach().equals(NO))return;
        var key=new Location(context,node);var old=before.get(key);var joined=old==null?state:join(old,state);
        if(!joined.equals(old)){before.put(key,joined);if(queued.add(key))work.add(key);}
        if(before.size()>maxWork)throw new IllegalStateException("RESOURCE_LIMIT: too many states");
    }
    private Exit normalize(Exit target) {
        var seen=new HashSet<Exit>();
        while(target.kind()==TargetKind.REGION_ENTRY) {
            if(!seen.add(target))throw new IllegalStateException("cyclic control alias "+target);
            target=Exit.of(regions.get(target.reference()).entry());
        }return target;
    }
    private boolean hasUnsupportedPredicate(Ast.Statement statement) {
        var pending=new ArrayDeque<Ast.Node>();pending.add(statement);
        while(!pending.isEmpty()) {
            var node=pending.removeFirst();
            if(node instanceof Ast.ClassCondition c&&!Set.of("NUMERIC","ALPHABETIC","ALPHABETIC-LOWER","ALPHABETIC_LOWER","ALPHABETIC-UPPER","ALPHABETIC_UPPER").contains(c.className().toUpperCase(Locale.ROOT)))return true;
            // Only own expressions; nested statements have their own observation.
            for(var child:Ast.children(node))if(!(child instanceof Ast.Statement))pending.add(child);
        }return false;
    }
    private Point controlPhase(Binding binding,String phase,String endpoint) {
        return new Point(new Exit(TargetKind.OCCURRENCE,"phase/"+binding.id()+"/"+phase),endpoint);
    }
    private Point controlEntry(Point entry) {
        return controlEntryAliases.computeIfAbsent(entry,p->{
            String name="entry/"+controlEntries.size();controlEntries.put(name,p);
            return new Point(new Exit(TargetKind.OCCURRENCE,name),p.endpoint());
        });
    }
    /** The control relation is independent of actual values. Branches remain
     * guarded by the value solver; calls acquire resumes only through delivery. */
    private DependencyControl.Effect controlEffect(Point point) {
        var exit=normalize(point.exit());String endpoint=point.endpoint();
        if(!exit.equals(point.exit()))return DependencyControl.Effect.next(new Point(exit,endpoint));
        var next=new ArrayList<Point>();var calls=new ArrayList<DependencyControl.Call>();var entries=new ArrayList<Point>();
        switch(exit.kind()) {
            case COMPLETE -> {
                return ("boundary:"+exit.reference()).equals(endpoint)?DependencyControl.Effect.exit(exit)
                    :DependencyControl.Effect.next(new Point(Exit.of(boundaries.get(exit.reference()).ordinaryDefault()),endpoint));
            }
            case ESCAPE,PROGRAM_RETURN,PROGRAM_HALT -> {return DependencyControl.Effect.exit(exit);}
            case FILE_POINT -> filePoints.get(exit.reference()).targets().forEach(t->next.add(new Point(Exit.of(t),endpoint)));
            case UNKNOWN_LOCAL -> {
                if(endpoint.equals("boundary:"+exit.reference())&&regions.get(exit.reference()).kind()==RegionKind.DECLARATIVE)
                    return DependencyControl.Effect.exit(new Exit(TargetKind.COMPLETE,exit.reference()));
            }
            case OCCURRENCE -> {
                String node=exit.reference();
                if(controlEntries.containsKey(node))calls.add(new DependencyControl.Call(controlEntries.get(node),""));
                else if(node.startsWith("phase/")) {
                    int split=node.lastIndexOf('/');var binding=bindings.get(node.substring(6,split));String phase=node.substring(split+1);
                    if(phase.equals("BODY"))calls.add(new DependencyControl.Call(new Point(Exit.of(regions.get(binding.region()).entry()),binding.endpoint()),binding.id()));
                    else if(phase.equals("RESUME"))next.add(new Point(Exit.of(binding.resume()),endpoint));
                    else for(var edge:binding.phases().stream().filter(p->p.id().equals(phase)).findFirst().orElseThrow().edges())next.add(controlPhase(binding,edge.target(),endpoint));
                }else {
                    for(var edge:edges.getOrDefault(node,List.of()))next.add(edge.kind()==OutcomeKind.LOCAL_INVOKE
                        ?controlPhase(bindings.get(edge.binding()),bindings.get(edge.binding()).entryPhase(),endpoint)
                        :new Point(Exit.of(edge.target()),endpoint));
                    for(var possibility:possibilities.getOrDefault(node,List.of()))next.add(new Point(Exit.of(possibility.target()),endpoint));
                    var statement=statements.get(node);
                    if(statement!=null)fileSurface(statement).ifPresent(surface->{for(var handler:surface.handlers()) {
                        var region=regions.get("region:"+node+"/file/handler-"+handler.kind());if(region!=null)next.add(new Point(Exit.of(region.entry()),endpoint));
                    }});
                    for(var binding:ioDeclarations.getOrDefault(node,List.of()))next.add(controlPhase(binding,binding.entryPhase(),endpoint));
                    for(var event:events.getOrDefault(node,List.of()))if(event.eligibility()==EventEligibility.HANDLER_ELIGIBLE) {
                        next.add(new Point(Exit.of(event.continuation()),endpoint));
                        for(var registrations:registrations.values())for(var registration:registrations)for(var target:registration.target())next.add(new Point(Exit.of(target),endpoint));
                    }
                    if(exceptionalEvents.getOrDefault(node,List.of()).stream().anyMatch(e->e.eligibility()==EventEligibility.HANDLER_ELIGIBLE))
                        handlerEndpoints.forEach((handler,handlerEndpoint)->entries.add(controlEntry(new Point(new Exit(TargetKind.OCCURRENCE,handler),handlerEndpoint))));
                }
            }
            default -> throw new IllegalStateException("unresolved control entry "+exit);
        }
        return new DependencyControl.Effect(next,calls,entries,Set.of());
    }
    private DependencyControl.Effect controlDelivery(Point caller,DependencyControl.Call call,Exit exit) {
        return continuationPlans.computeIfAbsent(new DeliveryKey(call.binding(),caller.endpoint(),exit),key->continuationPlan(caller,call,exit));
    }
    private DependencyControl.Effect continuationPlan(Point caller,DependencyControl.Call call,Exit exit) {
        if(call.binding().isEmpty()) {
            if(exit.kind()==TargetKind.ESCAPE&&!("boundary:"+exit.reference()).equals(call.entry().endpoint()))
                return DependencyControl.Effect.next(controlEntry(new Point(Exit.of(boundaries.get(exit.reference()).ordinaryDefault()),call.entry().endpoint())));
            return new DependencyControl.Effect(List.of(),List.of(),List.of(),Set.of());
        }
        var binding=bindings.get(call.binding());
        if(exit.kind()==TargetKind.COMPLETE)return DependencyControl.Effect.next(controlPhase(binding,binding.completionPhase(),caller.endpoint()));
        if(exit.kind()==TargetKind.ESCAPE) {
            if(("boundary:"+exit.reference()).equals(call.entry().endpoint()))return DependencyControl.Effect.next(new Point(Exit.of(binding.resume()),caller.endpoint()));
            String body=regions.get(binding.region()).entry().reference(),ancestor=body;
            while(regions.containsKey(ancestor)&&!ancestor.equals(exit.reference()))ancestor=regions.get(ancestor).parent();
            if(ancestor.equals(exit.reference())&&!ancestor.equals(body))return DependencyControl.Effect.exit(exit);
            return new DependencyControl.Effect(List.of(),List.of(new DependencyControl.Call(
                new Point(Exit.of(boundaries.get(exit.reference()).ordinaryDefault()),call.entry().endpoint()),binding.id())),List.of(),Set.of());
        }
        return DependencyControl.Effect.exit(exit);
    }
    private void route(int context,Target target,State state){route(context,Exit.of(target),state);}
    private void route(int context,Exit target,State state) {
        if(state.reach().equals(NO))return;
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
        var input=selection.apply(state);var values=new DependencyEnvironment.Builder<>(input.values());var parameters=new HashMap<Integer,Term>();
        for(int id:input.values().keySet())if(!specialized.contains(id)){values.put(id,DependencyValues.UNKNOWN);parameters.put(id,new Parameter(id));}
        var key=new SummaryKey(entry,endpoint,new State(values,input.handlers(),input.reached(),parameters,YES,input.controls()));
        Integer context=memo.get(key);
        if(context==null) {
            context=contexts.size();memo.put(key,context);contexts.add(new Context(key));route(context,entry,key.input());
        }
        var ctx=contexts.get(context);
        if(ctx.callers.add(caller))for(var exit:ctx.results.keySet())queueResult(context,exit,caller);
        if(contexts.size()>maxWork)throw new IllegalStateException("RESOURCE_LIMIT: too many summaries");
    }
    private State restore(Continuation caller,State result) {
        result=substitute(result,caller.input(),new IdentityHashMap<>());
        var restored=new DependencyEnvironment.Builder<>(caller.input().values());restored.putAll(result.values());
        var handlers=new HashMap<>(caller.input().handlers());handlers.putAll(result.handlers());
        var parameters=new DependencyEnvironment.Builder<>(caller.input().parameters());result.values().keySet().forEach(parameters::remove);parameters.putAll(result.parameters());
        var controls=new HashMap<>(caller.input().controls());result.handlers().keySet().forEach(name->controls.remove(new Handler(name)));result.reached().forEach(name->controls.remove(new Fact(name)));controls.putAll(result.controls());
        return new State(restored,handlers,union(caller.input().reached(),result.reached()),parameters,choose(caller.input().reach(),result.reach(),NO),controls);
    }
    private Term calculation(String node,State input,int declaration) {
        if(statements.get(node) instanceof Ast.MoveStatement move&&!move.corresponding()
                &&declarations.children.getOrDefault(declaration,List.of()).isEmpty()
                &&!declarations.repeated.contains(declaration)&&!declarations.assumed.contains(declaration)
                &&!textGroups.contains(declaration)) {
            // A scalar result reads the source and its own partial destination.
            // Uncertain aliases are separate openings, not value operands.
            boolean nominal=move.targets().stream().filter(Ast.DataReference.class::isInstance).map(Ast.DataReference.class::cast)
                .map(r->declarations.references.get(r.meta().id())).filter(Objects::nonNull)
                .anyMatch(id->declarations.equivalents.getOrDefault(id,Set.of(id)).contains(declaration));
            if(nominal) {
                var reads=new HashSet<Integer>(expressionInputs(move.source(),input));
                for(var target:move.targets())if(target instanceof Ast.DataReference r) {
                    if(r.referenceModification()!=null){reads.addAll(expressionInputs(r,input));}
                    for(var group:r.subscriptGroups())for(var subscript:group.subscripts())reads.addAll(expressionInputs(subscript,input));
                }
                var selected=new HashSet<Input>();reads.forEach(id->selected.add(new Value(id)));input=Selection.of(selected).apply(input);
            }
        }
        return lift(new Operation("write",node,null,false,declaration),input.withReach(YES));
    }
    private Term observe(Query query,State input) {
        var ids=expressionInputs(query.expression(),input);var inputs=new HashSet<Input>();for(int id:ids)inputs.add(new Value(id));
        var selected=Selection.of(inputs).apply(input).withReach(YES);
        Term value=query.literal().map(v->(Term)new Constant(DependencyValues.known(v))).orElseGet(()->lift(new Operation("read","",query.expression(),false,0),selected));
        return choose(input.reach(),value,EMPTY);
    }
    /** Substitute a whole caller environment, keeping correlated operands
     * together. The explicit stack handles long chains without Java recursion. */
    private Term substitute(Term root,State binding,IdentityHashMap<Term,Term> translated) {
        record Visit(Term term,boolean expanded) { }
        var pending=new ArrayDeque<Visit>();pending.push(new Visit(root,false));
        while(!pending.isEmpty()) {
            var visit=pending.pop();var term=visit.term();if(translated.containsKey(term))continue;
            if(term instanceof Parameter parameter){translated.put(term,binding.term(parameter.declaration()));continue;}
            if(term instanceof Constant){translated.put(term,term);continue;}
            if(!visit.expanded()) {
                pending.push(new Visit(term,true));
                Collection<Term> children;
                if(term instanceof Decision d){var list=new ArrayList<Term>(d.test.input.parameters().values());list.add(d.high);list.add(d.low);children=list;}
                else if(term instanceof Refinement r)children=r.input().parameters().values();
                else if(term instanceof Widening widening)children=List.of(widening.input());
                else children=term instanceof Calculation c?c.input.parameters().values():term instanceof Observation o?o.input().parameters().values():((Alternatives)term).terms();
                for(var child:children)if(!translated.containsKey(child))pending.push(new Visit(child,false));
                continue;
            }
            Term replacement;
            if(term instanceof Calculation c) {
                var input=substitute(c.input,binding,translated);
                replacement=input.equals(c.input)?c:calculation(c.node,input,c.declaration);
            }else if(term instanceof Decision d) {
                var input=substitute(d.test.input,binding,translated);
                replacement=choose(input.equals(d.test.input)?node(d.test,YES,NO):boundPredicate(d.test,input),translated.get(d.high),translated.get(d.low));
            }else if(term instanceof Widening widening) {
                replacement=open(translated.get(widening.input()));
            }else if(term instanceof Refinement r) {
                replacement=lift(new Operation("refine","",r.condition(),r.wanted(),r.declaration()),substitute(r.input(),binding,translated));
            }else if(term instanceof Observation o) {
                var input=substitute(o.input(),binding,translated);
                replacement=lift(new Operation("read","",o.expression(),false,0),input);
            }else {
                replacement=null;
                for(var child:((Alternatives)term).terms())replacement=replacement==null?translated.get(child):combine(replacement,translated.get(child));
            }
            translated.put(term,replacement);
        }
        return translated.get(root);
    }
    private Term boundPredicate(Test original,State input) {
        // Equal abstract UNKNOWN values do not prove that two mutations are
        // the same runtime value. Preserve the symbolic decision's identity
        // when binding cannot establish either outcome.
        if(input.parameters().isEmpty()&&testTruth(original.operation,input)==3) {
            var key=new TestKey(original.operation,input,original);
            var test=tests.computeIfAbsent(key,k->new Test(tests.size(),original.operation,input));
            return node(test,YES,NO);
        }
        return lift(original.operation,input);
    }
    private State substitute(State input,State binding,IdentityHashMap<Term,Term> translated) {
        if(input.parameters().isEmpty()&&input.controls().isEmpty()&&input.reach() instanceof Constant)return input;
        var values=new DependencyEnvironment.Builder<>(input.values());var parameters=new HashMap<Integer,Term>();
        for(var entry:input.parameters().entrySet()) {
            var term=translated.get(entry.getValue());if(term==null)term=substitute(entry.getValue(),binding,translated);
            if(term instanceof Constant constant)values.put(entry.getKey(),constant.value());else parameters.put(entry.getKey(),term);
        }
        var handlers=new HashMap<>(input.handlers());var reached=new HashSet<>(input.reached());var controls=new HashMap<Input,Term>();
        for(var entry:input.controls().entrySet()) {
            var term=substitute(entry.getValue(),binding,translated);
            if(term instanceof Constant c) {
                if(entry.getKey() instanceof Handler h)handlers.put(h.name(),c.value().values());
                else if(c.equals(YES))reached.add(((Fact)entry.getKey()).statement());else reached.remove(((Fact)entry.getKey()).statement());
            }else controls.put(entry.getKey(),term);
        }
        return new State(values,handlers,reached,parameters,substitute(input.reach(),binding,translated),controls);
    }
    record ResolutionKey(int context,Term term) { }
    final Map<ResolutionKey,DependencyValues> resolved=new HashMap<>();
    final Map<ResolutionKey,Set<ResolutionKey>> resolutionInputs=new HashMap<>(),resolutionParents=new HashMap<>();
    long resolutionVisits;
    private DependencyValues resolve(int context,Term term) {
        var root=new ResolutionKey(context,term);var work=new ArrayDeque<ResolutionKey>();var queued=new HashSet<ResolutionKey>();work.add(root);queued.add(root);
        var discover=new ArrayDeque<ResolutionKey>();discover.add(root);
        while(!discover.isEmpty()) {
            var key=discover.removeFirst();if(resolutionInputs.containsKey(key))continue;
            var inputs=new HashSet<ResolutionKey>();resolutionInputs.put(key,inputs);
            var grounded=ground(key.term());
            if(grounded!=null)resolved.put(key,grounded);
            else {
                resolved.put(key,new DependencyValues(Set.of(),false));
                var grouped=new HashMap<Integer,Term>();
                for(var caller:contexts.get(key.context()).callers) {
                    var actual=choose(caller.input().reach(),substitute(key.term(),caller.input(),new IdentityHashMap<>()),EMPTY);
                    int owner=caller.context()<0?key.context():caller.context();
                    grouped.merge(owner,actual,this::combine);
                }
                for(var actual:grouped.entrySet()) {
                    var child=new ResolutionKey(actual.getKey(),actual.getValue());inputs.add(child);resolutionParents.computeIfAbsent(child,k->new HashSet<>()).add(key);discover.add(child);
                }
            }
            if(queued.add(key))work.add(key);
            if(resolutionInputs.size()>maxWork)throw new IllegalStateException("RESOURCE_LIMIT: too many parametric resolutions");
        }
        while(!work.isEmpty()) {
            if(++resolutionVisits>maxWork)throw new IllegalStateException("RESOURCE_LIMIT: parametric resolution exceeded --max-work="+maxWork);
            var key=work.removeFirst();queued.remove(key);var value=resolved.get(key);
            for(var child:resolutionInputs.get(key))value=value.join(resolved.get(child));
            if(!value.equals(resolved.get(key))) {
                resolved.put(key,value);
                for(var parent:resolutionParents.getOrDefault(key,Set.of()))if(queued.add(parent))work.add(parent);
            }
        }
        return resolved.get(root);
    }
    /** A fallthrough forwards the result unchanged. Only a real invocation owns
     * repeat phases and contextual escapes; an independent entry has no resume. */
    private void deliver(Continuation caller,Exit exit,State result) {
        resultDeliveries++;
        State state=restore(caller,result);
        if(state.reach().equals(NO))return;
        if(caller instanceof Forward forward){finish(forward.context(),exit,state);return;}
        ReturnTo returnTo=caller instanceof ReturnTo r?r:null;
        String endpoint=returnTo==null?caller.endpoint():contexts.get(returnTo.context()).key.endpoint();
        var plan=controlDelivery(new Point(exit,endpoint),new DependencyControl.Call(new Point(exit,caller.endpoint()),returnTo==null?"":returnTo.binding()),exit);
        for(var next:plan.next()) {
            if(returnTo==null)subscribe(new Entry(caller.context(),next.endpoint(),state),controlEntries.get(next.exit().reference()).exit());
            else if(next.exit().reference().startsWith("phase/")) {
                String node=next.exit().reference();int split=node.lastIndexOf('/');
                phase(returnTo.context(),bindings.get(node.substring(6,split)),node.substring(split+1),state);
            }else route(returnTo.context(),next.exit(),state);
        }
        for(var call:plan.calls())subscribe(new ReturnTo(returnTo.context(),returnTo.binding(),call.entry().endpoint(),state),call.entry().exit());
        for(var resultExit:plan.exits())if(returnTo!=null)finish(returnTo.context(),resultExit,state);
    }

    private Set<Integer> expandNeeded(Set<Integer> needed) {
        for(int group:textGroups)if(!Collections.disjoint(needed,declarations.leaves(group)))needed.add(group);
        for(int id:new ArrayList<>(needed))needed.addAll(tableValueIds.getOrDefault(id,List.of()));
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
        var exit=point.exit();
        if(exit.reference().startsWith("phase/")) {
            int split=exit.reference().lastIndexOf('/');var binding=bindings.get(exit.reference().substring(6,split));
            exit=new Exit(TargetKind.OCCURRENCE,binding.caller());
        }
        var local=accessEffects.computeIfAbsent(exit,this::localEffect);
        return new Effect(local.reads(),local.kills(),controlSummary.successors(point));
    }

    private void queueResult(int context,Exit exit,Continuation caller) {
        var location=new Location(context,"",exit,caller);if(queued.add(location))work.add(location);
    }
    private void finish(int context,Exit exit,State state) {
        // A terminal program exit propagates only reachability. Its variables,
        // handlers and prerequisite facts can never be consumed by a resume;
        // dependency observations already retain their own BEFORE operands.
        if(exit.kind()==TargetKind.PROGRAM_RETURN||exit.kind()==TargetKind.PROGRAM_HALT)
            state=new State(Map.of(),Map.of(),Set.of(),Map.of(),state.reach());
        var ctx=contexts.get(context);var old=ctx.results.get(exit);var joined=old==null?state:join(old,state);
        if(joined.equals(old))return;ctx.results.put(exit,joined);for(var caller:ctx.callers)queueResult(context,exit,caller);
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
        var key=new Computation(node,selection.apply(input).withReach(YES));
        var actions=computations.get(key);
        if(actions==null) {
            evaluations++;actions=compute(node,key.input());computations.put(key,actions);
        }else reusedEvaluations++;
        for(var action:actions) {
            var state=action.patch().apply(input,this);
            if(action instanceof Advance advance)route(location.context(),advance.target(),state);
            else if(action instanceof Phase phase)phase(location.context(),bindings.get(phase.binding()),phase.phase(),state);
            else if(action instanceof Enter enter)subscribe(new Entry(location.context(),enter.endpoint(),state),enter.entry());
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
        values.addAll(relatedValues(values));
        for(int id:values)inputs.add(new Value(id));
        // A destination needed as an actual source remains an operand even if
        // the same statement also fully overwrites it (e.g. MOVE A TO A).
        for(var kill:effect.kills())if(!effect.reads().contains(kill))inputs.remove(kill);
        return Selection.of(inputs);
    }
    private Set<Integer> relatedValues(Collection<Integer> ids) {
        var values=new HashSet<>(ids);boolean changed;
        do {
            int size=values.size();
            for(int id:new ArrayList<>(values)){values.addAll(declarations.related(id));values.addAll(tableValueIds.getOrDefault(id,List.of()));}
            for(int group:textGroups)if(values.contains(group)||!Collections.disjoint(values,declarations.leaves(group))) {
                values.add(group);values.addAll(declarations.leaves(group));
            }
            values.retainAll(demand);changed=size!=values.size();
        }while(changed);
        return Set.copyOf(values);
    }
    private List<Action> compute(String node,State input) {
        var actions=calculate(node,input);var statement=statements.get(node);
        if(input.parameters().isEmpty()||statement==null||potentialWrites(statement).isEmpty())return actions;
        // Evaluate with TOP for every symbolic operand. A closed result under
        // that input is independent of caller values and needs no expression.
        // The ordinary approximation may contain closed sets from a branch
        // join; those sets alone cannot prove independence or correlation.
        var unknown=new DependencyEnvironment.Builder<>(input.values());
        input.parameters().keySet().forEach(id->unknown.put(id,DependencyValues.UNKNOWN));
        var independent=transfer(statement,input.withValues(unknown));
        var support=writeSupport(statement);var changed=support.changed();var lifted=new ArrayList<Action>();
        for(var action:actions) {
            var result=action.patch().apply(input,this);var values=new DependencyEnvironment.Builder<>(result.values());var parameters=new DependencyEnvironment.Builder<>(result.parameters());
            for(int id:changed) {
                if(specialized.contains(id)){parameters.remove(id);continue;}
                Term term;
                var opening=support.widenings().get(id);
                if(!independent.get(id).unknown())term=new Constant(independent.get(id));
                else if(opening!=null){term=EMPTY;for(int source:opening)term=combine(term,input.term(source));term=open(term);}
                else term=calculation(node,input,id);
                if(term instanceof Constant c){values.put(id,c.value());parameters.remove(id);}
                else {values.put(id,DependencyValues.UNKNOWN);parameters.put(id,term);}
            }
            var patch=Patch.of(input,new State(values,result.handlers(),result.reached(),parameters,result.reach(),result.controls()));
            if(action instanceof Advance a)lifted.add(new Advance(a.target(),patch));
            else if(action instanceof Phase a)lifted.add(new Phase(a.binding(),a.phase(),patch));
            else if(action instanceof Enter a)lifted.add(new Enter(a.entry(),a.endpoint(),patch));
        }
        return List.copyOf(lifted);
    }
    private Set<Integer> potentialWrites(Ast.Statement statement) {
        return StatementEffectSummary.of(statement).filter(e->e.unknownWriteBound()==StatementEffectSummary.Bound.ALL).isPresent()?Set.copyOf(demand):writes(statement);
    }
    private List<Action> calculate(String node,State input) {
        var actions=new ArrayList<Action>();
        if(node.startsWith("phase/")) {
            int split=node.lastIndexOf('/');var b=bindings.get(node.substring(6,split));String id=node.substring(split+1);
            var p=b.phases().stream().filter(x->x.id().equals(id)).findFirst().orElseThrow();
            var perform=(Ast.PerformStatement)statements.get(b.caller());State state=input;Term decision=null;
            if(p.operation().equals("UNTIL_PREDICATE")) {
                var condition=perform.controls().stream().filter(c->c.context()==Ast.PerformControlContext.CONDITION&&(p.level()==0||c.varyingLevel()==p.level())).map(Ast.PerformControl::expression).findFirst();
                if(condition.isPresent()) {
                    var expression=condition.get();
                    var selected=conditionInput(expression,state);
                    if(requiresDecision(selected)||truth(expression,state)!=3)decision=predicate(expression,selected);
                }
            } else if(p.operation().equals("COUNT_ENTRY")||p.operation().equals("COUNT_REPEAT")) {
                var expression=perform.controls().isEmpty()?null:perform.controls().get(0).expression();
                var test=new Operation(p.operation().equals("COUNT_ENTRY")?"count-entry":"count-repeat","",expression,false,0);
                var selected=conditionInput(expression,state);
                if(requiresDecision(selected)||testTruth(test,state)!=3)decision=lift(test,selected);
            }
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
            for(var edge:p.edges()) {
                State branch=edge.role().equals("next")||decision==null?state:guard(state,decision,edge.role().equals("true"));
                if(branch!=null)actions.add(new Phase(b.id(),edge.target(),Patch.of(input,branch)));
            }
            return List.copyOf(actions);
        }
        var s=statements.get(node);if(s==null)return List.copyOf(actions);
        State state=transfer(s,input);
        if(prerequisites.contains(node)) {
            var controls=new HashMap<>(state.controls());controls.remove(new Fact(node));
            state=new State(state.values(),state.handlers(),union(state.reached(),Set.of(node)),state.parameters(),state.reach(),controls);
        }
        var registration=abendRegistrations.get(node);
        if(registration!=null) {
            var h=new HashMap<>(state.handlers());
            if(registration.action()==CicsHandlerSemantics.Action.ACTIVATE) {
                var target=registration.labelTarget().flatMap(CicsHandlerSemantics.LabelTarget::entry).map(id->Set.of("statement:"+id)).orElse(Set.of("UNKNOWN"));
                h.put("ABEND",target);h.put("ABEND-SAVED",target);
            } else if(registration.action()==CicsHandlerSemantics.Action.CANCEL)h.put("ABEND",Set.of("CANCEL"));
            else if(registration.action()==CicsHandlerSemantics.Action.RESET)h.put("ABEND",h.getOrDefault("ABEND-SAVED",Set.of("UNKNOWN")));
            var saved=state.control(new Handler("ABEND-SAVED"));
            state=withHandlers(state,h,registration.action()==CicsHandlerSemantics.Action.ACTIVATE?Set.of("ABEND","ABEND-SAVED"):Set.of("ABEND"));
            if(registration.action()==CicsHandlerSemantics.Action.RESET&&!(saved instanceof Constant)) {
                var controls=new HashMap<>(state.controls());controls.put(new Handler("ABEND"),saved);
                state=new State(state.values(),state.handlers(),state.reached(),state.parameters(),state.reach(),controls);
            }
        }
        if(registrations.containsKey(node)) {
            var h=new HashMap<>(state.handlers());
            for(var r:registrations.get(node))h.put(r.condition(),Set.of(r.action()==ConditionAction.LABEL?r.target().get(0).reference():r.action().name()));
            state=withHandlers(state,h,registrations.get(node).stream().map(ConditionRegistration::condition).collect(java.util.stream.Collectors.toSet()));
        }
        for(var e:edges.getOrDefault(node,List.of())) {
            State branch=state;
            if(s instanceof Ast.IfStatement f && e.kind()==OutcomeKind.BRANCH&&controlSummary.observed(node))branch=filter(f.condition(),e.role().equals("then"),state);
            if(s instanceof Ast.EvaluateStatement evaluate&&e.kind()==OutcomeKind.BRANCH)branch=evaluate(evaluate,e.role(),state);
            if(s instanceof Ast.GoToStatement g&&g.goToKind()==Ast.GoToKind.DEPENDING_ON) {
                var expression=g.dependingOn();
                var selected=conditionInput(expression,branch);var test=new Operation("dispatch",e.role(),expression,false,g.targets().size());
                if(requiresDecision(selected)||testTruth(test,state)!=3)branch=guard(branch,lift(test,selected),true);
            }
            if(branch==null)continue;
            if(e.kind()==OutcomeKind.LOCAL_INVOKE)actions.add(new Phase(e.binding(),bindings.get(e.binding()).entryPhase(),Patch.of(input,branch)));
            else actions.add(new Advance(Exit.of(e.target()),Patch.of(input,branch)));
        }
        // Inventory the source-qualified continuations with uncertainty. Their
        // prerequisites must have been reached in this invocation.
        for(var p:possibilities.getOrDefault(node,List.of())) {
            Term eligible=YES;for(var fact:p.prerequisites())eligible=choose(eligible,state.control(new Fact(fact)),NO);
            var branch=guard(state,eligible,true);if(branch!=null)actions.add(new Advance(Exit.of(p.target()),Patch.of(input,branch)));
        }
        // File handler bodies already have grammar-owned entries/completions.
        var surface=fileSurface(s);
        if(surface.isPresent())for(var h:surface.get().handlers()) {
            var r=regions.get("region:"+node+"/file/handler-"+h.kind());if(r!=null)actions.add(new Advance(Exit.of(r.entry()),Patch.of(input,state)));
        }
        for(var use:ioDeclarations.getOrDefault(node,List.of()))actions.add(new Phase(use.id(),use.entryPhase(),Patch.of(input,state)));
        for(var event:events.getOrDefault(node,List.of()))if(event.eligibility()==EventEligibility.HANDLER_ELIGIBLE) {
            var field=state.handlers().containsKey(event.condition())||state.controls().containsKey(new Handler(event.condition()))?new Handler(event.condition()):new Handler("ERROR");
            var dispositions=state.control(field);
            for(String handler:controlCandidates(dispositions)) {
                var branch=guard(state,controlMatch(dispositions,handler),true);if(branch==null)continue;
                if(handler.equals("IGNORE"))actions.add(new Advance(Exit.of(event.continuation()),Patch.of(input,branch)));
                else if(regions.containsKey(handler))actions.add(new Advance(Exit.of(regions.get(handler).entry()),Patch.of(input,branch)));
            }
        }
        for(var event:exceptionalEvents.getOrDefault(node,List.of()))if(event.eligibility()==EventEligibility.HANDLER_ELIGIBLE) {
            boolean explicit=event.origin()==EventOrigin.EXPLICIT_ABEND;
            var field=state.handlers().containsKey("PGMIDERR")||state.controls().containsKey(new Handler("PGMIDERR"))?new Handler("PGMIDERR"):new Handler("ERROR");
            var eligible=explicit?state:guard(state,controlMatch(state.control(field),"DEFAULT"),true);if(eligible==null)continue;
            var abend=eligible.control(new Handler("ABEND"));
            for(String handler:controlCandidates(abend))if(handlerEndpoints.containsKey(handler)) {
                var branch=guard(eligible,controlMatch(abend,handler),true);if(branch==null)continue;
                var h=new HashMap<>(branch.handlers());h.put("ABEND",Set.of("CANCEL"));
                State ingress=withHandlers(branch,h,Set.of("ABEND"));String endpoint=handlerEndpoints.get(handler);
                actions.add(new Enter(new Exit(TargetKind.OCCURRENCE,handler),endpoint,Patch.of(input,ingress)));
            }
        }
        return List.copyOf(actions);
    }
    private State withHandlers(State state,Map<String,Set<String>> handlers,Set<String> written) {
        var controls=new HashMap<>(state.controls());written.forEach(name->controls.remove(new Handler(name)));
        return new State(state.values(),handlers,state.reached(),state.parameters(),state.reach(),controls);
    }
    private Set<String> controlCandidates(Term root) {
        var values=new HashSet<String>();var pending=new ArrayDeque<Term>();var seen=Collections.newSetFromMap(new IdentityHashMap<Term,Boolean>());pending.push(root);
        while(!pending.isEmpty()){var term=pending.pop();if(!seen.add(term))continue;if(term instanceof Constant c)values.addAll(c.value().values());else if(term instanceof Decision d){pending.push(d.high);pending.push(d.low);}else if(term instanceof Alternatives a)pending.addAll(a.terms());else throw new IllegalStateException("unresolved control disposition");}return values;
    }
    private Term controlMatch(Term root,String value) {
        record Visit(Term term,boolean expanded) { }
        var translated=new IdentityHashMap<Term,Term>();var pending=new ArrayDeque<Visit>();pending.push(new Visit(root,false));
        while(!pending.isEmpty()) {
            var visit=pending.pop();var term=visit.term();if(translated.containsKey(term))continue;
            if(term instanceof Constant c){translated.put(term,c.value().values().contains(value)?YES:NO);continue;}
            if(!visit.expanded()){pending.push(new Visit(term,true));if(term instanceof Decision d){pending.push(new Visit(d.high,false));pending.push(new Visit(d.low,false));}else for(var child:((Alternatives)term).terms())pending.push(new Visit(child,false));continue;}
            if(term instanceof Decision d)translated.put(term,choose(node(d.test,YES,NO),translated.get(d.high),translated.get(d.low)));
            else {Term out=NO;for(var child:((Alternatives)term).terms())out=choose(out,YES,translated.get(child));translated.put(term,out);}
        }return translated.get(root);
    }
    private static Optional<Ast.FileIoSurface> fileSurface(Ast.Statement s) {
        return s instanceof Ast.ModeledStatement m?m.fileIo():s instanceof Ast.PreservedStatement p?p.fileIo():Optional.empty();
    }
    private State evaluate(Ast.EvaluateStatement statement,String role,State state) {
        State remaining=state;int ordinal=0;
        for(var branch:statement.branches()) {
            if(branch.other())return role.equals("other")?remaining:null;
            var condition=evaluateConditions.computeIfAbsent(branch,b->evaluateCondition(statement,b));
            var match=condition==null?remaining:filter(condition,true,remaining);
            if(role.equals("when-"+(ordinal++)))return match;
            if(condition!=null)remaining=filter(condition,false,remaining);
            if(remaining==null)return null;
        }
        return role.equals("other")?remaining:null;
    }
    private Ast.Expression evaluateCondition(Ast.EvaluateStatement statement,Ast.EvaluateBranch branch) {
        var alternatives=new ArrayList<Ast.Expression>();var operands=new ArrayList<Ast.Expression>();
        // The frontend flattens WHEN groups. Subject index zero starts each
        // alternative; increasing indexes are the conjunction written as ALSO.
        for(var selector:branch.selectors()) {
            if(selector.subjectIndex()==0&&!operands.isEmpty()) {
                alternatives.add(logical(branch.meta(),Ast.LogicalConnector.AND,operands));operands.clear();
            }
            if(selector.subjectIndex()>=statement.subjects().size())continue;
            var subject=statement.subjects().get(selector.subjectIndex());Ast.Expression condition;
            if(selector.context()==Ast.EvaluateSelectorContext.VALUE_COMPARISON||selector.context()==Ast.EvaluateSelectorContext.SIMPLE_LITERAL)
                condition=new Ast.RelationCondition(branch.meta(),subject,"",selector.expression(),"",selector.negated()?Ast.RelationOperator.NOT_EQUAL:Ast.RelationOperator.EQUAL);
            else {
                condition=selector.expression();
                boolean invert=selector.negated()^(subject instanceof Ast.LiteralExpression literal&&literal.booleanValue().equals(Optional.of(false)));
                if(invert)condition=new Ast.NegatedCondition(branch.meta(),condition,"");
            }
            operands.add(condition);
        }
        if(!operands.isEmpty())alternatives.add(logical(branch.meta(),Ast.LogicalConnector.AND,operands));
        return alternatives.isEmpty()?null:logical(branch.meta(),Ast.LogicalConnector.OR,alternatives);
    }
    private static Ast.Expression logical(Ast.Meta meta,Ast.LogicalConnector connector,List<Ast.Expression> operands) {
        return operands.size()==1?operands.get(0):new Ast.LogicalCondition(meta,connector,operands,"");
    }
    private State filterConcrete(Ast.Expression condition,boolean wanted,State state) {
        if(state==null)return null;int bit=wanted?1:2;if((truth(condition,state)&bit)==0)return null;
        var out=new DependencyEnvironment.Builder<>(state.values());
        for(int id:declarations.reads(condition)) {
            var old=state.get(id);if(declarations.repeated.contains(id)||declarations.assumed.contains(id))continue;
            var kept=new HashSet<String>();
            for(String value:old.values()) {
                var trial=new DependencyEnvironment.Builder<>(state.values());for(int alias:declarations.equivalents.getOrDefault(id,Set.of(id)))trial.put(alias,DependencyValues.known(value));
                if((truth(condition,state.withValues(trial))&bit)!=0)kept.add(value);
            }
            if(kept.isEmpty()&&!old.unknown())return null;
            out.put(id,new DependencyValues(kept,old.unknown()));
        }
        var conditionReads=declarations.reads(condition);
        for(int group:textGroups)if(!Collections.disjoint(conditionReads,declarations.leaves(group))) {
            var original=state.get(group);var kept=new HashSet<String>();
            for(String candidate:original.values()) {
                var trial=new DependencyEnvironment.Builder<>(state.values());trial.put(group,DependencyValues.known(candidate));
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
