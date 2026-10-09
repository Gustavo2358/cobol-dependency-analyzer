package com.imd.cobolexplorer;

import java.util.*;

/** Logical declaration relationships over the frontend's existing binding.
 * No second name resolver, byte layout, address or allocation model. */
final class DependencyDeclarations {
    final Map<Integer,Ast.DataEntry> entries = new LinkedHashMap<>();
    final Map<Integer,Integer> references = new HashMap<>();
    final Map<Integer,Integer> parent = new HashMap<>();
    final Map<Integer,List<Integer>> children = new HashMap<>();
    final Map<Integer,Set<Integer>> equivalents = new HashMap<>(), possibleAliases = new HashMap<>();
    final Map<Integer,Integer> widths = new HashMap<>();
    final Map<Integer,Integer> counts = new HashMap<>();
    final Map<Integer,Set<Integer>> textualViews = new HashMap<>();
    final Set<Integer> numeric = new HashSet<>(), repeated = new HashSet<>(), assumed = new HashSet<>();
    private final Map<Integer,List<Integer>> leafCache=new HashMap<>();
    private final Map<Integer,Set<Integer>> relationCache=new HashMap<>();
    private final IdentityHashMap<Ast.Node,Set<Integer>> readCache=new IdentityHashMap<>();
    private boolean indexed;
    final Map<Integer,Integer> conditions = new HashMap<>();

    DependencyDeclarations(CompilationUnitModel.ProgramUnit unit,CompilationUnitModel compilation,
            CompilationUnitSymbolTables tables,ReferenceResolution binding) {
        walk(unit.program(),null,unit.program());
        var owners=new HashSet<ResolutionContracts.ProgramUnitId>();owners.add(unit.id());
        for(var r:binding.entries())if(r.occurrence().programUnitId().equals(unit.id()))r.selectedCandidate().ifPresent(c->owners.add(c.entityId().programUnitId()));
        for(var owner:compilation.programUnits())if(owners.contains(owner.id())&&!owner.id().equals(unit.id()))walk(owner.program(),null,owner.program());
        var declarationBySymbol=new HashMap<ResolutionContracts.ProgramUnitId,Map<Integer,Integer>>();
        for(var owner:owners)tables.forProgramUnit(owner).ifPresent(t->{
            var local=new HashMap<Integer,Integer>();t.symbolTable().symbols().forEach(s->local.put(s.id(),s.declarationAstNodeId()));declarationBySymbol.put(owner,local);
        });
        for(var r:binding.entries())if(owners.contains(r.occurrence().programUnitId()))r.selectedCandidate()
            .map(c->declarationBySymbol.getOrDefault(c.entityId().programUnitId(),Map.of()).get(c.entityId().localId())).ifPresent(id->references.put(r.occurrence().referenceAstNodeId(),id));
        for (var d : entries.values()) {
            var pic = d.clauses().stream().filter(Ast.PictureClause.class::isInstance).map(Ast.PictureClause.class::cast).findFirst();
            if (pic.isPresent()) {
                pic.get().textExtent().ifPresent(n -> widths.put(d.meta().id(),n));
                if(pic.get().numeric().isPresent()) {
                    numeric.add(d.meta().id());
                    boolean display=d.clauses().stream().noneMatch(c->c instanceof Ast.UsageClause u&&!u.display());
                    if(display&&!pic.get().picture().toUpperCase(Locale.ROOT).contains("S"))pic.get().integerDigits().ifPresent(n->widths.put(d.meta().id(),n));
                }
            }
            if (d.levelKind()==Ast.DataLevelKind.CONDITION_88) conditions.put(d.meta().id(),parent.getOrDefault(d.meta().id(),-1));
            for(var c:d.clauses())if(c instanceof Ast.OccursClause o) {
                repeated.add(d.meta().id());
                if(o.dependingOn()==null&&o.maximum()==null&&o.minimum() instanceof Ast.LiteralExpression l)
                    l.integerValue().ifPresent(n->{try{int count=n.intValueExact();if(count>0)counts.put(d.meta().id(),count);}catch(ArithmeticException ignored){}});
            }
            if(d.meta().syntheticModel()) assumed.add(d.meta().id());
        }
        for (var d : entries.values()) width(d.meta().id());
        for(var d:entries.values()) {Integer ancestor=parent.get(d.meta().id());while(ancestor!=null){if(repeated.contains(ancestor))repeated.add(d.meta().id());ancestor=parent.get(ancestor);}}
        for (var d : entries.values()) for (var clause : d.clauses()) {
            if(clause instanceof Ast.RedefinesClause r) {
                Integer target = references.get(r.target().meta().id());
                if(target!=null) relate(d.meta().id(),target);
            }
            if(clause instanceof Ast.RenamesClause r) {
                Integer first=references.get(r.from().meta().id()), last=r.through()==null?first:references.get(r.through().meta().id());
                if(first!=null && first.equals(last)) { widths.put(d.meta().id(),widths.getOrDefault(first,0));relate(d.meta().id(),first); }
                else if(first!=null && last!=null && Objects.equals(parent.get(first),parent.get(last))) {
                    var siblings=children.getOrDefault(parent.get(first),List.of()); int a=siblings.indexOf(first),b=siblings.indexOf(last);
                    if(a>=0 && b>=a) { children.put(d.meta().id(),List.copyOf(siblings.subList(a,b+1))); widths.remove(d.meta().id()); width(d.meta().id()); }
                }
            }
        }
        indexed=true;
    }
    private void walk(Ast.Node node,Integer owner,Ast.Program program) {
        if(node instanceof Ast.Program && node!=program)return;
        if(node instanceof Ast.DataEntry d) {
            entries.put(d.meta().id(),d);
            if(owner!=null) parent.put(d.meta().id(),owner);
            children.put(d.meta().id(),d.children().stream().filter(c->c.levelKind()!=Ast.DataLevelKind.CONDITION_88&&c.levelKind()!=Ast.DataLevelKind.RENAMES_66).map(c->c.meta().id()).toList());
            owner=d.meta().id();
        }
        for(var child:Ast.children(node))walk(child,owner,program);
    }
    private int width(int id) {
        if(widths.containsKey(id))return widths.get(id);
        var cs=children.getOrDefault(id,List.of()); if(cs.isEmpty())return 0;
        long total=0;
        for(int c:cs) {
            if(entries.get(c).clauses().stream().anyMatch(Ast.RedefinesClause.class::isInstance))continue;
            int n=width(c); if(n==0||repeated.contains(c)&&!counts.containsKey(c)&&entries.get(c).clauses().stream().anyMatch(Ast.OccursClause.class::isInstance))return 0;
            total+=(long)n*counts.getOrDefault(c,1); if(total>Integer.MAX_VALUE)return 0;
        }
        widths.put(id,(int)total);return (int)total;
    }
    private void relate(int a,int b) {
        var ac=children.getOrDefault(a,List.of());var bc=children.getOrDefault(b,List.of());
        boolean exact=widths.getOrDefault(a,0)>0 && Objects.equals(widths.get(a),widths.get(b)) && !numeric.contains(a)&&!numeric.contains(b);
        if(exact && ac.isEmpty() && bc.isEmpty()) {
            var merged=new HashSet<>(equivalents.getOrDefault(a,Set.of(a)));merged.addAll(equivalents.getOrDefault(b,Set.of(b)));
            var immutable=Set.copyOf(merged);merged.forEach(k->equivalents.put(k,immutable));
            return;
        }
        if(exact && ac.size()==bc.size() && !ac.isEmpty() && java.util.stream.IntStream.range(0,ac.size()).allMatch(i->Objects.equals(widths.get(ac.get(i)),widths.get(bc.get(i)))&&Objects.equals(counts.getOrDefault(ac.get(i),1),counts.getOrDefault(bc.get(i),1)))) {
            for(int i=0;i<ac.size();i++)relate(ac.get(i),bc.get(i));return;
        }
        // One textual child is logically the same entire textual group.
        if(exact && ac.size()==1 && bc.isEmpty()&&counts.getOrDefault(ac.get(0),1)==1){relate(ac.get(0),b);return;}
        if(exact && bc.size()==1 && ac.isEmpty()&&counts.getOrDefault(bc.get(0),1)==1){relate(a,bc.get(0));return;}
        if(widths.getOrDefault(a,0)>0&&widths.getOrDefault(b,0)>0) {
            textualViews.computeIfAbsent(a,k->new HashSet<>()).add(b);
            textualViews.computeIfAbsent(b,k->new HashSet<>()).add(a);
        }
        for(int x:leaves(a))for(int y:leaves(b)) {
            possibleAliases.computeIfAbsent(x,k->new HashSet<>()).add(y);
            possibleAliases.computeIfAbsent(y,k->new HashSet<>()).add(x);
        }
    }
    List<Integer> leaves(int id) {
        if(indexed&&leafCache.containsKey(id))return leafCache.get(id);
        var out=new ArrayList<Integer>();var todo=new ArrayDeque<Integer>();todo.add(id);var seen=new HashSet<Integer>();
        while(!todo.isEmpty()){int next=todo.removeFirst();if(!seen.add(next))continue;var cs=children.getOrDefault(next,List.of());if(cs.isEmpty())out.add(next);else todo.addAll(cs);}
        if(indexed)leafCache.put(id,List.copyOf(out));
        return out;
    }
    Set<Integer> related(int id) {
        if(indexed&&relationCache.containsKey(id))return relationCache.get(id);
        var out=new HashSet<Integer>(leaves(id));
        for(int leaf:leaves(id)){out.addAll(equivalents.getOrDefault(leaf,Set.of()));out.addAll(possibleAliases.getOrDefault(leaf,Set.of()));}
        Integer ancestor=id;
        while(ancestor!=null){for(int view:textualViews.getOrDefault(ancestor,Set.of()))out.addAll(leaves(view));ancestor=parent.get(ancestor);}
        if(indexed)relationCache.put(id,Set.copyOf(out));
        return out;
    }
    Set<Integer> reads(Ast.Node node) {
        if(readCache.containsKey(node))return readCache.get(node);
        var out=new HashSet<Integer>();var todo=new ArrayDeque<Ast.Node>();if(node!=null)todo.add(node);
        while(!todo.isEmpty()) {var n=todo.removeFirst();if(n instanceof Ast.DataReference r) {
            Integer id=references.get(r.meta().id());if(id!=null)out.addAll(related(conditions.getOrDefault(id,id)));
        } todo.addAll(valueChildren(n));}
        var result=Set.copyOf(out);readCache.put(node,result);return result;
    }
    /** Qualifiers select the declaration's scope; they do not read its container.
     * Address expressions on every reference still contribute value operands. */
    static List<? extends Ast.Node> valueChildren(Ast.Node node) {
        if(!(node instanceof Ast.DataReference reference))return Ast.children(node);
        var out=new ArrayList<Ast.Node>();var pending=new ArrayDeque<Ast.DataReference>();pending.add(reference);
        while(!pending.isEmpty()) {
            var next=pending.removeFirst();out.addAll(next.subscriptGroups());
            if(next.referenceModification()!=null)out.add(next.referenceModification());
            next.qualifiers().forEach(q->pending.add(q.reference()));
        }
        return out;
    }
}
