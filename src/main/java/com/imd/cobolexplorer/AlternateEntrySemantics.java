package com.imd.cobolexplorer;

import java.util.*;

/** Declared external roots, independent of primary flow and runtime parameter values. */
public final class AlternateEntrySemantics {
    public record Declaration(int ordinal,Ast.PreservedStatement source,Optional<String> name,int parameters,List<String> gaps) {
        public Declaration {Objects.requireNonNull(source);Objects.requireNonNull(name);gaps=List.copyOf(gaps);}
        public boolean supported(){return gaps.isEmpty();}
        public String entry(){return "entry:"+ordinal;}
    }
    private record Pending(Ast.Node node,boolean direct) { }
    private AlternateEntrySemantics() { }
    public static List<Declaration> analyze(CompilationUnitModel.ProgramUnit unit) {
        var procedure=unit.program().divisions().stream().filter(d->d.divisionKind()==Ast.DivisionKind.PROCEDURE).findFirst();
        if(procedure.isEmpty())return List.of();
        boolean returning=procedure.orElseThrow().children().stream().filter(Ast.ProcedureSignature.class::isInstance).map(Ast.ProcedureSignature.class::cast).anyMatch(s->s.returning()!=null);
        var sources=new ArrayList<Ast.PreservedStatement>();var direct=new HashSet<Integer>();var pending=new ArrayDeque<Pending>();pending.add(new Pending(procedure.orElseThrow(),true));
        while(!pending.isEmpty()) {
            var p=pending.removeFirst();var n=p.node();
            if(n instanceof Ast.PreservedStatement entry&&entry.grammarRule().equals("entryStatement")){sources.add(entry);if(p.direct())direct.add(n.meta().id());}
            boolean childDirect=p.direct()&&!(n instanceof Ast.Statement)&&!(n instanceof Ast.Section section&&section.children().stream().anyMatch(Ast.UseClause.class::isInstance));
            Ast.children(n).forEach(child->pending.addLast(new Pending(child,childDirect)));
        }
        sources.sort(Comparator.comparingInt(s->s.meta().span().startToken()));
        var counts=new HashMap<String,Integer>();for(var entry:sources)entry.entrySurface().flatMap(Ast.EntrySurface::name).ifPresent(n->counts.merge(SymbolTable.canonical(n.value()),1,Integer::sum));
        var result=new ArrayList<Declaration>();
        for(var entry:sources) {
            var surface=entry.entrySurface();var name=surface.flatMap(Ast.EntrySurface::name).map(Ast.LogicalText::value);var gaps=new ArrayList<String>();
            if(name.isEmpty()||name.orElseThrow().isBlank())gaps.add("ALTERNATE_ENTRY_NAME_UNAVAILABLE");
            else if(counts.get(SymbolTable.canonical(name.orElseThrow()))!=1||SymbolTable.canonical(name.orElseThrow()).equals(unit.id().canonicalProgramName()))gaps.add("ALTERNATE_ENTRY_NAME_CONFLICT");
            if(unit.parentId()!=null)gaps.add("ALTERNATE_ENTRY_IN_NESTED_PROGRAM");
            if(returning)gaps.add("ALTERNATE_ENTRY_WITH_RETURNING");
            if(!direct.contains(entry.meta().id()))gaps.add("ALTERNATE_ENTRY_SCOPE_UNSUPPORTED");
            result.add(new Declaration(result.size()+1,entry,name,surface.map(Ast.EntrySurface::parameterCount).orElse(0),gaps));
        }
        return List.copyOf(result);
    }
}
