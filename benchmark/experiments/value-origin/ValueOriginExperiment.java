package com.imd.cobolexplorer;

import java.util.*;
import static com.imd.cobolexplorer.DependencyFlow.*;
import static com.imd.cobolexplorer.semanticproduct.ControlTopology.*;
import static com.imd.cobolexplorer.DependencyRelevance.Point;

/** Investigation only: sparse definition edges, without invocation states.
 * Full writes cut a definition chain; joins retain alternative definitions.
 * Physical control is deliberately conservative and may invent returns.
 * SCCs only collapse union/copy nodes, never executable transformations. */
final class ValueOriginExperiment {
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
    static final class Origin {
        final Cell cell;final List<Integer> parents=new ArrayList<>();
        Operation operation;DependencyValues seed=BOTTOM;
        Origin(Cell cell){this.cell=cell;}
        boolean join(){return operation==null;}
    }
    static final class Operation {
        final Ast.Statement statement;final Map<Integer,Integer> inputs=new LinkedHashMap<>();
        Map<Integer,DependencyValues> lastInputs;State lastResult;
        Operation(Ast.Statement statement){this.statement=statement;}
    }
    record OperationKey(int position,Set<Integer> reads) { }
    static final class Position {
        final Exit exit;final Set<Integer> predecessors=new LinkedHashSet<>();boolean root;
        Position(Exit exit){this.exit=exit;}
    }
    static final class Component {
        final List<Integer> members=new ArrayList<>();final Set<Integer> parents=new LinkedHashSet<>();
        final Set<Integer> children=new LinkedHashSet<>();Operation operation;
        DependencyValues value=BOTTOM,seed=BOTTOM;boolean queued;
    }
    final DependencyFlow flow;
    final Map<Exit,Integer> positionIds=new HashMap<>();final List<Position> positions=new ArrayList<>();
    final Map<Cell,Integer> originIds=new HashMap<>();final List<Origin> origins=new ArrayList<>();
    final ArrayDeque<Integer> physical=new ArrayDeque<>(),discovery=new ArrayDeque<>();
    final Map<OperationKey,Operation> operations=new HashMap<>();
    final IdentityHashMap<Ast.Statement,State> independent=new IdentityHashMap<>();
    final Map<String,List<Exit>> boundaryReturns=new HashMap<>(),escapeReturns=new HashMap<>();
    long steps,physicalEdges,originEdges,evaluations,propagations,candidateSlots,constantWrites,openingWrites;
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
                    if(id!=null){id=flow.declarations.conditions.getOrDefault(id,id);if(flow.textGroups.contains(id))ids.add(id);else ids.addAll(flow.declarations.leaves(id));}
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
    ValueOriginExperiment(DependencyFlow flow){this.flow=flow;}
    void tick(){if(++steps>flow.maxWork)throw new IllegalStateException("RESOURCE_LIMIT: value-origin experiment --max-work="+flow.maxWork);}
    void stage(String name){System.err.printf("ORIGIN_STAGE %s positions=%d origins=%d components=%d tracked=%d usedHeapMiB=%d%n",name,positions.size(),origins.size(),components.size(),flow.demand.size(),(Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory())/1048576);}
    static void solve(DependencyFlow flow,List<Point> roots){new ValueOriginExperiment(flow).solve(roots);}
    int position(Exit exit){Integer old=positionIds.get(exit);if(old!=null)return old;tick();int id=positions.size();positionIds.put(exit,id);positions.add(new Position(exit));physical.add(id);return id;}
    void edge(int from,Exit exit){int to=position(exit);if(positions.get(to).predecessors.add(from))physicalEdges++;}
    void solve(List<Point> roots){
        // A resume is admitted even when normal return is unproved. Actual body
        // values also reach it through matching physical completion boundaries.
        for(var binding:flow.bindings.values()) {
            String region=binding.endpoint().startsWith("boundary:")?binding.endpoint().substring(9):binding.region();
            boundaryReturns.computeIfAbsent(region,k->new ArrayList<>()).add(flow.originPhase(binding,binding.completionPhase()));
            escapeReturns.computeIfAbsent(region,k->new ArrayList<>()).add(Exit.of(binding.resume()));
            String body=flow.regions.get(binding.region()).entry().reference();String ancestor=flow.regions.containsKey(body)?flow.regions.get(body).parent():"";
            while(flow.regions.containsKey(ancestor)) {escapeReturns.computeIfAbsent(ancestor,k->new ArrayList<>()).add(Exit.of(binding.resume()));ancestor=flow.regions.get(ancestor).parent();}
        }
        for(var root:roots)positions.get(position(root.exit())).root=true;
        while(!physical.isEmpty()) {
            tick();int id=physical.remove();Exit exit=positions.get(id).exit;var effect=flow.originEffect(exit).continuation();
            for(var next:effect.next())edge(id,next);
            for(var entry:effect.entries())edge(id,entry.exit());
            for(var call:effect.calls()) {
                edge(id,call.entry().exit());var binding=flow.bindings.get(call.binding());
                if(binding!=null)edge(id,flow.originPhase(binding,binding.completionPhase()));
            }
            if(exit.kind()==TargetKind.COMPLETE)for(var next:boundaryReturns.getOrDefault(exit.reference(),List.of()))edge(id,next);
            if(exit.kind()==TargetKind.ESCAPE)for(var next:escapeReturns.getOrDefault(exit.reference(),List.of()))edge(id,next);
        }
        stage("physical");
        // Demand only value operands. Predicate operands do not need value
        // states because this experiment admits all published branch outcomes.
        var pending=new ArrayDeque<Integer>();
        for(var query:flow.queries)if(positionIds.containsKey(new Exit(TargetKind.OCCURRENCE,handle(query.statement()))))
            for(int id:flow.declarations.reads(query.expression()))if(flow.demand.add(id))pending.add(id);
        var writers=new HashMap<Integer,List<Ast.Statement>>();var unbounded=new ArrayList<Ast.Statement>();
        for(var s:flow.statements.values()) {
            if(StatementEffectSummary.of(s).filter(e->e.unknownWriteBound()==StatementEffectSummary.Bound.ALL).isPresent())unbounded.add(s);
            for(int id:flow.originWrites(s))writers.computeIfAbsent(id,k->new ArrayList<>()).add(s);
        }
        var expanded=Collections.newSetFromMap(new IdentityHashMap<Ast.Statement,Boolean>());
        while(!pending.isEmpty()) {
            int id=pending.remove();for(int peer:flow.declarations.related(id))if(flow.demand.add(peer))pending.add(peer);
            var selected=new ArrayList<>(writers.getOrDefault(id,List.of()));selected.addAll(unbounded);
            for(var s:selected)if(expanded.add(s))for(int read:flow.declarations.reads(s))if(flow.demand.add(read))pending.add(read);
        }
        for(var q:flow.queries)snapshots(q.expression());
        for(var statement:flow.statements.values())if(statement instanceof Ast.MoveStatement move&&!Collections.disjoint(flow.originWrites(move),flow.demand))snapshots(move.source());
        for(int id:flow.declarations.textualViews.keySet())if(!Collections.disjoint(flow.declarations.leaves(id),flow.demand))flow.textGroups.add(id);
        flow.demand.addAll(flow.textGroups);
        initial=flow.originInitial();
        stage("demand");
        // Whole-group candidates remain one logical channel. Child fields are
        // also available for partial writes; no table positions are allocated.
        for(var q:flow.queries) {
            Integer pos=positionIds.get(new Exit(TargetKind.OCCURRENCE,handle(q.statement())));if(pos==null)continue;
            for(int id:reads(q.expression()))before(id,pos);
        }
        while(!discovery.isEmpty()){expand(discovery.remove());if(origins.size()/100000>reportedOrigins){reportedOrigins=origins.size()/100000;stage("discovering");}}
        stage("origins");condense();stage("components");propagate();stage("fixed-point");
        for(var q:flow.queries) {
            Integer pos=positionIds.get(new Exit(TargetKind.OCCURRENCE,handle(q.statement())));if(pos==null)continue;
            var values=new HashMap<Integer,DependencyValues>();
            for(int id:reads(q.expression())){var origin=originIds.get(new Cell(id,pos,false));values.put(id,components.get(componentOf[origin]).value);}
            DependencyValues answer=q.literal().map(DependencyValues::known).orElseGet(()->flow.read(q.expression(),new State(values,Map.of(),Set.of())));
            flow.answers.put(q,answer);if(answer.unknown())flow.diagnostics.add("DYNAMIC_REMAINDER at "+q.statement().meta().provenance().original().file()+":"+q.statement().meta().provenance().original().startLine());
        }
        flow.visits=steps;flow.evaluations=evaluations;
        flow.diagnostics.add("EXPERIMENT_APPROXIMATION: value-origin graph ignores predicate/caller correlations and admits speculative returns");
        var distinct=Collections.newSetFromMap(new IdentityHashMap<DependencyValues,Boolean>());
        for(var c:components)distinct.add(c.value);
        candidateSlots=distinct.stream().mapToLong(v->v.values().size()).sum();
        System.err.printf("ORIGIN_PRUNING constantWrites=%d openingWrites=%d independentTransfers=%d%n",constantWrites,openingWrites,independent.size());
        System.err.printf("ORIGIN_PROBE {\"physicalNodes\":%d,\"physicalEdges\":%d,\"originNodes\":%d,\"originEdges\":%d,\"components\":%d,\"joinComponents\":%d,\"operations\":%d,\"evaluations\":%d,\"propagations\":%d,\"candidateSlots\":%d,\"tracked\":%d,\"work\":%d}%n",positions.size(),physicalEdges,origins.size(),originEdges,components.size(),joinComponents,operations.size(),evaluations,propagations,candidateSlots,flow.demand.size(),steps);
    }
    int node(Cell cell){Integer old=originIds.get(cell);if(old!=null)return old;tick();int id=origins.size();origins.add(new Origin(cell));originIds.put(cell,id);discovery.add(id);return id;}
    int reportedOrigins,reportedWide;
    int before(int decl,int pos){return node(new Cell(decl,pos,false));}
    int after(int decl,int pos){return node(new Cell(decl,pos,true));}
    void parent(Origin origin,int parent){origin.parents.add(parent);originEdges++;}
    void expand(int id){
        tick();var origin=origins.get(id);var cell=origin.cell;var pos=positions.get(cell.position());
        if(!cell.after()) {
            if(pos.root)origin.seed=initial.get(cell.declaration());
            for(int predecessor:pos.predecessors)parent(origin,after(cell.declaration(),predecessor));return;
        }
        var statement=flow.statements.get(pos.exit.reference());
        if(statement==null||!flow.originSupport(statement).changed().contains(cell.declaration())) {parent(origin,before(cell.declaration(),cell.position()));return;}
        // The canonical local transformer uses TOP to certify independence.
        // Closed outputs under TOP cannot consume any previous field value.
        var closed=independent.computeIfAbsent(statement,s->flow.originTransfer(s,new State(Map.of(),Map.of(),Set.of()))).get(cell.declaration());
        if(!closed.unknown()){origin.seed=closed;constantWrites++;return;}
        // Opening is distributive: open(A union B) = A union B union UNKNOWN.
        // It is a union equation, with output-specific support already supplied
        // by the product. It does not require the other affected declarations.
        var opening=flow.originSupport(statement).widenings().get(cell.declaration());
        if(opening!=null){origin.seed=DependencyValues.UNKNOWN;for(int read:opening)if(flow.demand.contains(read))parent(origin,before(read,cell.position()));openingWrites++;return;}
        var operands=new HashSet<Integer>();
        if(statement instanceof Ast.MoveStatement move&&!move.corresponding())operands.addAll(reads(move.source()));
        else if(!(statement instanceof Ast.ModeledStatement modeled&&StatementEffectSummary.of(statement).filter(e->e.proof()==StatementEffectSummary.Proof.INITIALIZE_TARGETS).isPresent()))operands.addAll(reads(statement));
        if(!flow.originKills(statement).contains(cell.declaration()))operands.add(cell.declaration());
        if(statement instanceof Ast.MoveStatement move)for(var target:move.targets())for(var child:DependencyDeclarations.valueChildren(target))operands.addAll(reads(child));
        operands.retainAll(flow.demand);
        // Share an evaluation for identical operand support, rather than adding
        // every affected destination as an input to every requested output.
        var operation=operations.computeIfAbsent(new OperationKey(cell.position(),Set.copyOf(operands)),key->{
            var op=new Operation(statement);
            for(int read:key.reads())op.inputs.put(read,before(read,key.position()));
            if(op.inputs.size()>64&&reportedWide++<10)System.err.printf("ORIGIN_WIDE line=%d kind=%s inputs=%d changed=%d%n",statement.meta().provenance().original().startLine(),statement.getClass().getSimpleName(),op.inputs.size(),flow.originSupport(statement).changed().size());
            return op;
        });
        origin.operation=operation;
        for(int input:operation.inputs.values())parent(origin,input);
    }
    void condense(){
        // Kosaraju on join-only edges: transformations remain separate, so a
        // cyclic assignment cannot be silently equated with an identity copy.
        int count=origins.size();var children=new ArrayList<List<Integer>>(count);for(int i=0;i<count;i++)children.add(new ArrayList<>());
        for(int i=0;i<count;i++)if(origins.get(i).join())for(int p:origins.get(i).parents)if(origins.get(p).join())children.get(p).add(i);
        var seen=new BitSet();var order=new ArrayList<Integer>();
        record Visit(int id,boolean expanded) { }
        var dfs=new ArrayDeque<Visit>();
        for(int root=0;root<count;root++)if(origins.get(root).join()&&!seen.get(root)) {
            dfs.push(new Visit(root,false));while(!dfs.isEmpty()){tick();var v=dfs.pop();if(v.expanded()){order.add(v.id());continue;}if(seen.get(v.id()))continue;seen.set(v.id());dfs.push(new Visit(v.id(),true));for(int p:origins.get(v.id()).parents)if(origins.get(p).join()&&!seen.get(p))dfs.push(new Visit(p,false));}
        }
        componentOf=new int[count];Arrays.fill(componentOf,-1);var stack=new ArrayDeque<Integer>();
        for(int i=order.size()-1;i>=0;i--) {
            int root=order.get(i);if(componentOf[root]>=0)continue;int index=components.size();var component=new Component();components.add(component);componentOf[root]=index;stack.push(root);joinComponents++;
            while(!stack.isEmpty()){tick();int next=stack.pop();component.members.add(next);for(int child:children.get(next))if(componentOf[child]<0){componentOf[child]=index;stack.push(child);}}
        }
        for(int id=0;id<count;id++)if(componentOf[id]<0){int index=components.size();var c=new Component();c.members.add(id);c.operation=origins.get(id).operation;components.add(c);componentOf[id]=index;}
        for(int i=0;i<count;i++) {
            var c=components.get(componentOf[i]);c.seed=join(c.seed,origins.get(i).seed);
            for(int p:origins.get(i).parents)if(componentOf[i]!=componentOf[p])c.parents.add(componentOf[p]);
        }
        for(int i=0;i<components.size();i++)for(int p:components.get(i).parents)components.get(p).children.add(i);
    }
    void propagate(){
        var queue=new ArrayDeque<Integer>();for(int i=0;i<components.size();i++){queue.add(i);components.get(i).queued=true;}
        while(!queue.isEmpty()) {
            tick();int id=queue.remove();var c=components.get(id);c.queued=false;DependencyValues next;
            if(c.operation==null){next=c.seed;for(int p:c.parents)next=join(next,components.get(p).value);}
            else {
                var values=new HashMap<Integer,DependencyValues>();for(var input:c.operation.inputs.entrySet())values.put(input.getKey(),components.get(componentOf[input.getValue()]).value);
                if(c.operation.lastInputs==null||!values.equals(c.operation.lastInputs)) {
                    c.operation.lastInputs=Map.copyOf(values);
                    c.operation.lastResult=flow.originTransfer(c.operation.statement,new State(values,Map.of(),Set.of()));evaluations++;
                }
                var result=c.operation.lastResult;
                int declaration=origins.get(c.members.get(0)).cell.declaration();next=result.get(declaration);
            }
            // Monotone candidate equations. UNKNOWN is an independent fact;
            // bottom is not substituted with UNKNOWN at joins or reads.
            if(c.operation!=null)next=join(c.value,next);if(next==c.value||next.equals(c.value))continue;c.value=next;propagations++;
            for(int child:c.children)if(!components.get(child).queued){components.get(child).queued=true;queue.add(child);}
        }
    }
}
