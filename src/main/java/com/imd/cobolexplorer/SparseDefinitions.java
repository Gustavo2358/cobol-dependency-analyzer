package com.imd.cobolexplorer;

import java.util.*;
import static com.imd.cobolexplorer.DependencyFlow.*;
import static com.imd.cobolexplorer.semanticproduct.ControlTopology.*;
import static com.imd.cobolexplorer.DependencyRelevance.Point;

/** Sparse reaching definitions: sparse definition edges, without invocation states.
 * Full writes cut a definition chain; joins retain alternative definitions.
 * Physical control respects may-reachable boundary and handler policies.
 * Identity control chains are bypassed during lookup; SCCs collapse union nodes
 * only. One graph is shared by every query, with no invocation-state solver. */
final class SparseDefinitions {
    static final DependencyValues BOTTOM=new DependencyValues(Set.of(),false);
    static DependencyValues join(DependencyValues a,DependencyValues b) {
        if(a==b)return a;
        if(a.values().isEmpty()&&!a.unknown())return b;
        if(b.values().isEmpty()&&!b.unknown())return a;
        if(a.unknown()||!b.unknown())if(a.values().containsAll(b.values()))return a;
        if(b.unknown()||!a.unknown())if(b.values().containsAll(a.values()))return b;
        return a.join(b);
    }
    record Cell(int declaration,int position,boolean after) { }
    static final class Definition {
        final Cell cell;final List<Integer> parents=new ArrayList<>();
        Operation operation;DependencyValues seed=BOTTOM;boolean emptyCycle;
        Definition(Cell cell){this.cell=cell;}
        boolean join(){return operation==null;}
    }
    static final class Operation {
        final Ast.Statement statement;final Map<Integer,Integer> inputs=new LinkedHashMap<>();
        Map<Integer,DependencyValues> lastInputs;State lastResult;
        Operation(Ast.Statement statement){this.statement=statement;}
    }
    record OperationKey(int position,Set<Integer> reads) { }
    static final class Component {
        final List<Integer> members=new ArrayList<>();final Set<Integer> parents=new LinkedHashSet<>();
        final Set<Integer> children=new LinkedHashSet<>();Operation operation;
        DependencyValues value=BOTTOM,seed=BOTTOM;boolean queued;
    }
    final DependencyFlow flow;final DefinitionValues logical;
    final DefinitionControl control;
    final Map<Exit,Integer> positionIds;final List<DefinitionControl.Position> positions;
    final Map<Cell,Integer> definitionIds=new HashMap<>();final List<Definition> definitions=new ArrayList<>();
    final ArrayDeque<Integer> discovery=new ArrayDeque<>();
    final Map<OperationKey,Operation> operations=new HashMap<>();
    final IdentityHashMap<Ast.Statement,State> independent=new IdentityHashMap<>();
    long steps,physicalEdges,definitionEdges,evaluations,propagations,candidateSlots;
    int joinComponents;int[] componentOf;final List<Component> components=new ArrayList<>();
    State initial;
    final IdentityHashMap<Ast.Node,Set<Integer>> readCache=new IdentityHashMap<>();
    Set<Integer> reads(Ast.Node node) {
        return readCache.computeIfAbsent(node,n->{
            var ids=new HashSet<Integer>();var todo=new ArrayDeque<Ast.Node>();if(n!=null)todo.add(n);
            while(!todo.isEmpty()){
                var next=todo.remove();
                if(next instanceof Ast.DataReference r){
                    Integer id=flow.declarations.references.get(r.meta().id());
                    if(id!=null){id=flow.declarations.conditions.getOrDefault(id,id);if(flow.textGroups.contains(id))ids.add(id);else ids.addAll(flow.declarations.leaves(id));
                    for(int leaf:flow.declarations.leaves(id))ids.addAll(flow.tableValueIds.getOrDefault(leaf,List.of()));}
                }
                todo.addAll(DependencyDeclarations.valueChildren(next));
            }
            return Set.copyOf(ids);
        });
    }
    void snapshots(Ast.Node node) {
        var todo=new ArrayDeque<Ast.Node>();if(node!=null)todo.add(node);
        while(!todo.isEmpty()){var next=todo.remove();if(next instanceof Ast.DataReference r){Integer id=flow.declarations.references.get(r.meta().id());if(id!=null&&!flow.declarations.children.getOrDefault(id,List.of()).isEmpty()&&!Collections.disjoint(flow.declarations.leaves(id),flow.demand))flow.textGroups.add(id);}todo.addAll(DependencyDeclarations.valueChildren(next));}
    }
    SparseDefinitions(DependencyFlow flow){this.flow=flow;this.logical=new DefinitionValues(flow);control=new DefinitionControl(flow,this::tick);positionIds=control.positionIds;positions=control.positions;}
    void tick(){if(++steps>flow.maxWork)throw new IllegalStateException("RESOURCE_LIMIT: reaching definitions --max-work="+flow.maxWork);}
    static void solve(DependencyFlow flow,List<Point> roots){new SparseDefinitions(flow).solve(roots);}
    void solve(List<Point> roots){
        control.solve(roots);physicalEdges=control.edges;
        
        // Demand only value operands. Predicate operands do not need value
        // states because this solver admits all published branch outcomes.
        var pending=new ArrayDeque<Integer>();
        for(var query:flow.queries)if(positionIds.containsKey(new Exit(TargetKind.OCCURRENCE,handle(query.statement()))))
            for(int id:flow.declarations.reads(query.expression()))if(flow.demand.add(id))pending.add(id);
        var writers=new HashMap<Integer,List<Ast.Statement>>();var unbounded=new ArrayList<Ast.Statement>();
        for(var s:flow.statements.values()) {
            if(StatementEffectSummary.of(s).filter(e->e.unknownWriteBound()==StatementEffectSummary.Bound.ALL).isPresent())unbounded.add(s);
            for(int id:flow.definitionWrites(s))writers.computeIfAbsent(id,k->new ArrayList<>()).add(s);
        }
        var expanded=Collections.newSetFromMap(new IdentityHashMap<Ast.Statement,Boolean>());
        while(!pending.isEmpty()) {
            int id=pending.remove();for(int peer:flow.declarations.related(id))if(flow.demand.add(peer))pending.add(peer);
            var selected=new ArrayList<>(writers.getOrDefault(id,List.of()));selected.addAll(unbounded);
            for(var s:selected)if(expanded.add(s))for(int read:flow.declarations.reads(s))if(flow.demand.add(read))pending.add(read);
        }
        for(var q:flow.queries)snapshots(q.expression());
        for(var statement:flow.statements.values())if(statement instanceof Ast.MoveStatement move&&!Collections.disjoint(flow.definitionWrites(move),flow.demand))snapshots(move.source());
        for(int id:flow.declarations.textualViews.keySet())if(!Collections.disjoint(flow.declarations.leaves(id),flow.demand))flow.textGroups.add(id);
        flow.demand.addAll(flow.textGroups);
        int tableRounds=0;
        do {
            tick();tableRounds++;
            flow.allocateDemandedTables();
            solveValues();
            // Learning an exact index refines storage, not execution contexts.
            // Only value equations are rebuilt; physical control stays shared.
            if(flow.tableDemandExpanded){flow.answers.clear();flow.diagnostics.removeIf(d->d.startsWith("DYNAMIC_REMAINDER at "));}
        }while(flow.tableDemandExpanded);
        flow.tableDemandPasses=tableRounds;
        flow.visits=steps;flow.evaluations=evaluations;
        flow.diagnostics.add("REACHING_DEFINITIONS_APPROXIMATION: conditions and caller/value correlations are conservatively ignored; dependencies may include false positives");
        var distinct=Collections.newSetFromMap(new IdentityHashMap<DependencyValues,Boolean>());
        for(var c:components)distinct.add(c.value);
        candidateSlots=distinct.stream().mapToLong(v->v.values().size()).sum();
        flow.definitionsMetrics=new DependencyAnalyzer.ReachingDefinitionsMetrics(
            positions.size(),physicalEdges,control.policyCount,control.visits,control.deliveries,
            definitions.size(),definitionEdges,components.size(),joinComponents,operations.size(),
            evaluations,propagations,candidateSlots,steps,bypassed,aliases.size(),logical.combinations,
            flow.tables.size(),flow.elements.size(),tableRounds);
    }
    void solveValues(){
        readCache.clear();supports.clear();independent.clear();
        definitionIds.clear();definitions.clear();discovery.clear();operations.clear();aliases.clear();components.clear();joinComponents=0;definitionEdges=0;
        initial=flow.definitionInitial();
        
        // Whole-group candidates remain one logical channel. Child fields are
        // also available for partial writes, with demand-created table positions.
        for(var q:flow.queries) {
            Integer pos=positionIds.get(new Exit(TargetKind.OCCURRENCE,handle(q.statement())));if(pos==null)continue;
            for(int id:reads(q.expression()))before(id,pos);
        }
        while(!discovery.isEmpty()){expand(discovery.remove());}
        condense();propagate();
        for(var q:flow.queries) {
            Integer pos=positionIds.get(new Exit(TargetKind.OCCURRENCE,handle(q.statement())));if(pos==null)continue;
            var values=new HashMap<Integer,DependencyValues>();
            for(int id:reads(q.expression())){var definition=aliases.get(new Cell(id,pos,false));values.put(id,components.get(componentOf[definition]).value);}
            DependencyValues answer=q.literal().map(DependencyValues::known).orElseGet(()->logical.read(q.expression(),new State(values,Map.of(),Set.of())));
            flow.answers.put(q,answer);if(answer.unknown())flow.diagnostics.add("DYNAMIC_REMAINDER at "+q.statement().meta().provenance().original().file()+":"+q.statement().meta().provenance().original().startLine());
        }
    }
    int node(Cell cell){Integer old=definitionIds.get(cell);if(old!=null)return old;tick();int id=definitions.size();definitions.add(new Definition(cell));definitionIds.put(cell,id);discovery.add(id);return id;}
    final Map<Cell,Integer> aliases=new HashMap<>();
    final IdentityHashMap<Ast.Statement,WriteSupport> supports=new IdentityHashMap<>();
    long bypassed;
    WriteSupport support(Ast.Statement s){return supports.computeIfAbsent(s,flow::definitionSupport);}
    int before(int decl,int pos){return lookup(new Cell(decl,pos,false));}
    int after(int decl,int pos){return lookup(new Cell(decl,pos,true));}
    int lookup(Cell start){
        var path=new ArrayList<Cell>();var seen=new HashSet<Cell>();Cell current=start;int result;
        while(true){
            tick();var known=aliases.get(current);if(known!=null){result=known;break;}
            var pos=positions.get(current.position());
            var statement=flow.statements.get(pos.exit.reference());
            boolean definition=current.after()&&statement!=null&&support(statement).changed().contains(current.declaration());
            boolean merge=!current.after()&&(pos.root||pos.predecessors.size()!=1);
            if(definition||merge){result=node(current);aliases.put(current,result);break;}
            if(!seen.add(current)){
                // A closed cycle of identity edges has no reaching definition.
                // Seed bottom, without recursively rebuilding the cycle.
                result=node(current);definitions.get(result).emptyCycle=true;aliases.put(current,result);break;
            }
            path.add(current);
            current=current.after()?new Cell(current.declaration(),current.position(),false)
                :new Cell(current.declaration(),pos.predecessors.iterator().next(),true);
        }
        for(var cell:path)aliases.put(cell,result);bypassed+=path.size();return result;
    }
    void parent(Definition definition,int parent){definition.parents.add(parent);definitionEdges++;}
    void expand(int id){
        tick();var definition=definitions.get(id);var cell=definition.cell;var pos=positions.get(cell.position());
        if(definition.emptyCycle)return;
        if(!cell.after()) {
            if(pos.root)definition.seed=initial.get(cell.declaration());
            for(int predecessor:pos.predecessors)parent(definition,after(cell.declaration(),predecessor));return;
        }
        var statement=flow.statements.get(pos.exit.reference());
        if(statement==null||!support(statement).changed().contains(cell.declaration())) {parent(definition,before(cell.declaration(),cell.position()));return;}
        // The canonical local transformer uses TOP to certify independence.
        // Closed outputs under TOP cannot consume any previous field value.
        var closed=independent.computeIfAbsent(statement,s->logical.transfer(s,new State(Map.of(),Map.of(),Set.of()))).get(cell.declaration());
        if(!closed.unknown()){definition.seed=closed;return;}
        // Opening is distributive: open(A union B) = A union B union UNKNOWN.
        // It is a union equation, with output-specific support already supplied
        // by the product. It does not require the other affected declarations.
        var opening=support(statement).widenings().get(cell.declaration());
        if(opening!=null){definition.seed=DependencyValues.UNKNOWN;for(int read:opening)if(flow.demand.contains(read))parent(definition,before(read,cell.position()));return;}
        var operands=new HashSet<Integer>();
        if(statement instanceof Ast.MoveStatement move&&!move.corresponding())operands.addAll(reads(move.source()));
        else if(statement instanceof Ast.MoveStatement move)operands.addAll(flow.declarations.reads(move.source()));
        else if(!(statement instanceof Ast.ModeledStatement modeled&&StatementEffectSummary.of(statement).filter(e->e.proof()==StatementEffectSummary.Proof.INITIALIZE_TARGETS).isPresent()))operands.addAll(reads(statement));
        if(!flow.definitionKills(statement).contains(cell.declaration()))operands.add(cell.declaration());
        // A textual REDEFINES reconstructs another declaration from sibling
        // fields. Preserve those exact logical view operands; possible aliases
        // remain separate openings and do not expand all reads/destinations.
        Integer ancestor=cell.declaration();
        while(ancestor!=null){
            for(int view:flow.declarations.textualViews.getOrDefault(ancestor,Set.of())){
                operands.add(ancestor);operands.add(view);
                operands.addAll(flow.declarations.leaves(ancestor));operands.addAll(flow.declarations.leaves(view));
            }
            ancestor=flow.declarations.parent.get(ancestor);
        }
        if(statement instanceof Ast.MoveStatement move)for(var target:move.targets())for(var child:DependencyDeclarations.valueChildren(target))operands.addAll(reads(child));
        // Canonical indexed writes reconstruct table summaries from sparse
        // slots. Include those logical operands, never enumerate OCCURS extent.
        for(int operand:new ArrayList<>(operands))operands.addAll(flow.tableValueIds.getOrDefault(operand,List.of()));
        operands.addAll(logical.addresses(statement));
        operands.retainAll(flow.demand);
        // Share an evaluation for identical operand support, rather than adding
        // every affected destination as an input to every requested output.
        var operation=operations.computeIfAbsent(new OperationKey(cell.position(),Set.copyOf(operands)),key->{
            var op=new Operation(statement);
            for(int read:key.reads())op.inputs.put(read,before(read,key.position()));
            return op;
        });
        definition.operation=operation;
        for(int input:operation.inputs.values())parent(definition,input);
    }
    void condense(){
        // Kosaraju on join-only edges: transformations remain separate, so a
        // cyclic assignment cannot be silently equated with an identity copy.
        int count=definitions.size();var children=new ArrayList<List<Integer>>(count);for(int i=0;i<count;i++)children.add(new ArrayList<>());
        for(int i=0;i<count;i++)if(definitions.get(i).join())for(int p:definitions.get(i).parents)if(definitions.get(p).join())children.get(p).add(i);
        var seen=new BitSet();var order=new ArrayList<Integer>();
        record Visit(int id,boolean expanded) { }
        var dfs=new ArrayDeque<Visit>();
        for(int root=0;root<count;root++)if(definitions.get(root).join()&&!seen.get(root)) {
            dfs.push(new Visit(root,false));while(!dfs.isEmpty()){tick();var v=dfs.pop();if(v.expanded()){order.add(v.id());continue;}if(seen.get(v.id()))continue;seen.set(v.id());dfs.push(new Visit(v.id(),true));for(int p:definitions.get(v.id()).parents)if(definitions.get(p).join()&&!seen.get(p))dfs.push(new Visit(p,false));}
        }
        componentOf=new int[count];Arrays.fill(componentOf,-1);var stack=new ArrayDeque<Integer>();
        for(int i=order.size()-1;i>=0;i--) {
            int root=order.get(i);if(componentOf[root]>=0)continue;int index=components.size();var component=new Component();components.add(component);componentOf[root]=index;stack.push(root);joinComponents++;
            while(!stack.isEmpty()){tick();int next=stack.pop();component.members.add(next);for(int child:children.get(next))if(componentOf[child]<0){componentOf[child]=index;stack.push(child);}}
        }
        for(int id=0;id<count;id++)if(componentOf[id]<0){int index=components.size();var c=new Component();c.members.add(id);c.operation=definitions.get(id).operation;components.add(c);componentOf[id]=index;}
        for(int i=0;i<count;i++) {
            var c=components.get(componentOf[i]);c.seed=join(c.seed,definitions.get(i).seed);
            for(int p:definitions.get(i).parents)if(componentOf[i]!=componentOf[p])c.parents.add(componentOf[p]);
        }
        for(int i=0;i<components.size();i++)for(int p:components.get(i).parents)components.get(p).children.add(i);
    }
    /** Union a component's incoming facts, freezing only the final set. A
     * pairwise fold would copy every growing prefix at a high fan-in join.
     * Reuse a covering immutable input until a mutable builder is needed;
     * unknown remains independent of which input covers the candidates. */
    static DependencyValues unionParents(Component c,List<Component> components){
        var next=c.seed;HashSet<String> combined=null;boolean unknown=next.unknown();
        for(int p:c.parents){
            var value=components.get(p).value;unknown|=value.unknown();
            if(combined!=null){combined.addAll(value.values());continue;}
            if(next==value||next.values().containsAll(value.values()))continue;
            if(value.values().containsAll(next.values())){next=value;continue;}
            combined=new HashSet<>(next.values());combined.addAll(value.values());
        }
        if(combined!=null)return new DependencyValues(combined,unknown);
        return next.unknown()==unknown?next:next.open();
    }
    void propagate(){
        var queue=new ArrayDeque<Integer>();for(int i=0;i<components.size();i++){queue.add(i);components.get(i).queued=true;}
        while(!queue.isEmpty()) {
            tick();int id=queue.remove();var c=components.get(id);c.queued=false;DependencyValues next;
            if(c.operation==null)next=unionParents(c,components);
            else {
                var values=new HashMap<Integer,DependencyValues>();for(var input:c.operation.inputs.entrySet())values.put(input.getKey(),components.get(componentOf[input.getValue()]).value);
                // BOTTOM means no address fact has arrived yet. Evaluating it
                // as UNKNOWN would introduce a weak write that monotone union
                // can never retract when the exact index subsequently arrives.
                if(!logical.ready(c.operation.statement,values))continue;
                if(c.operation.lastInputs==null||!values.equals(c.operation.lastInputs)) {
                    c.operation.lastInputs=Map.copyOf(values);
                    c.operation.lastResult=logical.transfer(c.operation.statement,new State(values,Map.of(),Set.of()));evaluations++;
                }
                var result=c.operation.lastResult;
                int declaration=definitions.get(c.members.get(0)).cell.declaration();next=result.get(declaration);
            }
            // Monotone candidate equations. UNKNOWN is an independent fact;
            // bottom is not substituted with UNKNOWN at joins or reads.
            if(c.operation!=null)next=join(c.value,next);if(next==c.value||next.equals(c.value))continue;c.value=next;propagations++;
            for(int child:c.children)if(!components.get(child).queued){components.get(child).queued=true;queue.add(child);}
        }
    }
}
