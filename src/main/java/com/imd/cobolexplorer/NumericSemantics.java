package com.imd.cobolexplorer;

import java.util.*;

/** Integer item identity for logical MOVE and repetition controls, without assuming byte encoding. */
public final class NumericSemantics {
    public record NumberItem(int digits,int scale,boolean signed,Ast.NumericUsage representation,ResolutionContracts.TruncMode trunc) {
        public NumberItem { if(digits<=0)throw new IllegalArgumentException("positive digits"); Objects.requireNonNull(representation); Objects.requireNonNull(trunc); }
    }
    private final Map<ResolutionContracts.SemanticEntityId,NumberItem> declarations;
    private final Set<ResolutionContracts.SemanticEntityId> cells;
    private final Map<ResolutionContracts.SemanticEntityId,Set<String>> bounds;
    private NumericSemantics(Map<ResolutionContracts.SemanticEntityId,NumberItem> declarations,Set<ResolutionContracts.SemanticEntityId> cells,
            Map<ResolutionContracts.SemanticEntityId,Set<String>> bounds) {
        this.declarations=Map.copyOf(declarations);this.cells=Set.copyOf(cells);this.bounds=Map.copyOf(bounds);
    }
    public boolean exactCell(ResolutionContracts.SemanticEntityId id){return cells.contains(id);}
    boolean disjoint(ResolutionContracts.SemanticEntityId source,ResolutionContracts.SemanticEntityId target) {
        var a=bounds.get(source);var b=bounds.get(target);return a!=null&&b!=null&&!a.isEmpty()&&!b.isEmpty()&&Collections.disjoint(a,b);
    }
    static NumericSemantics empty() { return new NumericSemantics(Map.of(),Set.of(),Map.of()); }
    public Optional<NumberItem> declaration(ResolutionContracts.SemanticEntityId id) { return Optional.ofNullable(declarations.get(id)); }
    static NumericSemantics analyze(CompilationUnitBuildResult frontend,CompilationUnitSymbolTables tables,boolean complete,StorageComponents components) {
        return analyze(frontend,tables,u->complete,components,Map.of(),ResolutionContracts.TruncMode.UNSPECIFIED);
    }
    static NumericSemantics analyze(CompilationUnitBuildResult frontend,CompilationUnitSymbolTables tables,
            java.util.function.Predicate<ResolutionContracts.ProgramUnitId> complete,StorageComponents components,
            Map<ResolutionContracts.ProgramUnitId,com.imd.cobolexplorer.semanticproduct.FactDependencies> graphs,ResolutionContracts.TruncMode trunc) {
        if(!components.belongsTo(frontend))throw new IllegalArgumentException("storage components belong to another snapshot");
        var result=new HashMap<ResolutionContracts.SemanticEntityId,NumberItem>();
        var exact=new HashSet<ResolutionContracts.SemanticEntityId>();
        var bounds=new HashMap<ResolutionContracts.SemanticEntityId,Set<String>>();
        for(var unit:frontend.compilationUnit().programUnits()) {
            var attributes=unit.program().attributes();
            if(attributes.recursive()||attributes.common()||attributes.library()||attributes.definition())continue;
            var graph=graphs.get(unit.id());var cells=new HashSet<String>();var types=new HashSet<String>();
            var regionBounds=new HashMap<String,Set<String>>();
            if(graph!=null) {
                var known=graph.proofAvailability();
                for(var fact:graph.facts())if(fact.dependencies().stream().allMatch(p->Boolean.TRUE.equals(known.get(p)))) {
                    if(fact.kind()==com.imd.cobolexplorer.semanticproduct.FactDependencies.FactKind.LOCAL_CELL)cells.add(fact.subject());
                    if(fact.kind()==com.imd.cobolexplorer.semanticproduct.FactDependencies.FactKind.LOGICAL_NUMBER)types.add(fact.subject());
                }
                for(var binding:graph.bindings())if(binding.dependencies().stream().allMatch(p->Boolean.TRUE.equals(known.get(p)))) {
                    var locations=new HashSet<>(binding.cells());locations.addAll(binding.regions());
                    if(!locations.isEmpty())regionBounds.put(binding.node(),Set.copyOf(locations));
                }
            }
            var shapes=shapes(components.unit(unit.id()));
            var eligible=new HashMap<Integer,NumberItem>();
            for(var position:components.unit(unit.id()).positions()) {
                var d=position.data();
                boolean independent=graph!=null?cells.contains("storage-node:"+d.meta().id()):
                    complete.test(unit.id())&&components.unit(unit.id()).standaloneIndependent(d.meta().id())
                    &&(d.level().equals("01")||d.levelKind()==Ast.DataLevelKind.STANDALONE_77);
                boolean typed=independent||types.contains("storage-node:"+d.meta().id())&&regionBounds.containsKey("storage-node:"+d.meta().id())
                    &&components.unit(unit.id()).rootRelationsProven()&&!components.unit(unit.id()).uncertainRoots().contains(position.root());
                if(typed&&shapes.containsKey(d.meta().id())){
                    var n=shapes.get(d.meta().id());
                    eligible.put(d.meta().id(),new NumberItem(n.digits(),n.scale(),n.signed(),n.representation(),
                        n.representation()==Ast.NumericUsage.BINARY?trunc:ResolutionContracts.TruncMode.UNSPECIFIED));
                }
            }
            for(var symbol:tables.forProgramUnit(unit.id()).orElseThrow().symbolTable().symbols())
                if(symbol.namespace()==SymbolTable.Namespace.DATA&&symbol.kind()==SymbolTable.SymbolKind.DATA_ITEM) {
                    var entity=new ResolutionContracts.SemanticEntityId(unit.id(),ResolutionContracts.SemanticEntityDomain.DATA_SYMBOL,symbol.id());
                    var node="storage-node:"+symbol.declarationAstNodeId();
                    if(regionBounds.containsKey(node))bounds.put(entity,regionBounds.get(node));
                    if(eligible.containsKey(symbol.declarationAstNodeId())) {
                        result.put(entity,eligible.get(symbol.declarationAstNodeId()));
                        if(graph==null||cells.contains(node))exact.add(entity);
                    }
                }
        }
        return new NumericSemantics(result,exact,bounds);
    }
    /** An explicit stack memoizes inherited usage once per declaration; no ancestor scan per MOVE. */
    static Map<Integer,NumberItem> shapes(StorageComponents.Unit unit) { return shapes(unit,false); }
    static Map<Integer,NumberItem> layoutShapes(StorageComponents.Unit unit) { return shapes(unit,true); }
    private static Map<Integer,NumberItem> shapes(StorageComponents.Unit unit,boolean layout) {
        var positions=new HashMap<Integer,StorageComponents.Position>();unit.positions().forEach(p->positions.put(p.data().meta().id(),p));
        record Usage(Ast.NumericUsage kind,boolean constrained) { }
        var usages=new HashMap<Integer,Usage>();var result=new HashMap<Integer,NumberItem>();
        for(var position:unit.positions()) {
            int id=position.data().meta().id(),current=id;var path=new ArrayDeque<Integer>();
            while(!usages.containsKey(current)) {
                path.push(current);var parent=positions.get(current).parent();
                if(parent.isEmpty())break;current=parent.orElseThrow();
            }
            var inherited=usages.getOrDefault(current,new Usage(Ast.NumericUsage.DISPLAY,false));
            while(!path.isEmpty()) {
                int at=path.pop();var declaration=positions.get(at).data();
                var explicit=declaration.clauses().stream().filter(Ast.UsageClause.class::isInstance).map(Ast.UsageClause.class::cast).toList();
                if(explicit.size()>1||declaration.clauses().stream().anyMatch(c->c instanceof Ast.PreservedDataClause
                    ||c instanceof Ast.ValueClause value&&!knownValues(value)))inherited=new Usage(Ast.NumericUsage.UNAVAILABLE,true);
                if(!explicit.isEmpty()) {
                    var own=explicit.get(0).numeric();
                    inherited=new Usage(!inherited.constrained()||inherited.kind()==own?own:Ast.NumericUsage.UNAVAILABLE,true);
                }
                usages.put(at,inherited);
            }
            shape(position.data(),usages.get(id).kind(),layout).ifPresent(value->result.put(id,value));
        }
        return Map.copyOf(result);
    }
    private static boolean knownValues(Ast.ValueClause value) {
        return !value.ranges().isEmpty()&&value.ranges().stream().allMatch(r->r.first().kind()!=Ast.ConditionValueKind.UNAVAILABLE
            &&r.last().filter(v->v.kind()==Ast.ConditionValueKind.UNAVAILABLE).isEmpty());
    }
    static Optional<NumberItem> shape(Ast.DataEntry d,Ast.NumericUsage usage) { return shape(d,usage,false); }
    private static Optional<NumberItem> shape(Ast.DataEntry d,Ast.NumericUsage usage,boolean layout) {
        if(usage==Ast.NumericUsage.UNAVAILABLE)return Optional.empty();
        if(d.meta().syntheticModel()||d.filler()&&!layout
            ||d.visibility()!=Ast.DeclarationVisibility.LOCAL
            ||d.children().stream().anyMatch(c->c.levelKind()!=Ast.DataLevelKind.CONDITION_88))return Optional.empty();
        int pictures=0,usages=0;Optional<Ast.NumericPicture> number=Optional.empty();
        for(var c:d.clauses()) {
            if(!c.meta().provenance().exact())return Optional.empty();
            if(c instanceof Ast.PictureClause p){pictures++;number=p.numeric();}
            else if(c instanceof Ast.UsageClause)usages++;
            else if(c instanceof Ast.RedefinesClause) { /* Storage relation does not alter the numeric descriptor. */ }
            else if(c instanceof Ast.OccursClause&&layout) { /* Extent multiplies the independently typed element; not a scalar value proof. */ }
            else if(!(c instanceof Ast.ValueClause value)||!knownValues(value))return Optional.empty();
        }
        return pictures==1&&usages<=1?number.filter(n->n.digits()<=31&&Math.abs((long)n.scale())<=31)
            .filter(n->(usage!=Ast.NumericUsage.BINARY&&usage!=Ast.NumericUsage.NATIVE_BINARY)||n.digits()<=18)
            .map(n->new NumberItem(n.digits(),n.scale(),n.signed(),usage,ResolutionContracts.TruncMode.UNSPECIFIED)):Optional.empty();
    }
    public Optional<ResolutionContracts.SemanticEntityId> whole(Ast.Expression expression,ResolutionContracts.ProgramUnitId unit,
            Map<ScalarMoveSemantics.NodeKey,ReferenceResolution.Entry> references) {
        return wholeNumber(expression,unit,references).filter(id->exactCell(id)&&declaration(id).orElseThrow().scale()==0);
    }
    public Optional<ResolutionContracts.SemanticEntityId> wholeNumber(Ast.Expression expression,ResolutionContracts.ProgramUnitId unit,
            Map<ScalarMoveSemantics.NodeKey,ReferenceResolution.Entry> references) {
        if(!(expression instanceof Ast.DataReference r)||!r.meta().provenance().exact()
            ||r.understanding()!=Ast.ReferenceUnderstanding.STRUCTURED||!r.subscriptGroups().isEmpty()||r.referenceModification()!=null)return Optional.empty();
        var binding=references.get(new ScalarMoveSemantics.NodeKey(unit,r.meta().id()));
        if(binding==null||binding.status()!=ResolutionContracts.ResolutionStatus.RESOLVED||binding.candidates().size()!=1)return Optional.empty();
        var id=binding.selectedCandidate().orElseThrow().entityId();return declaration(id).isPresent()?Optional.of(id):Optional.empty();
    }
}
