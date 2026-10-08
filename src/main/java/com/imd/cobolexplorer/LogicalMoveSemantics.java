package com.imd.cobolexplorer;

import java.util.*;
import java.math.BigInteger;
import static com.imd.cobolexplorer.StorageLayoutSemantics.*;

/** Character MOVE proofs; no physical layout or runtime value propagation. */
public final class LogicalMoveSemantics {
    public record Fact(boolean admitted, boolean complete, Optional<String> literal) { }
    public record LogicalSlice(ResolutionContracts.SemanticEntityId data,BigInteger start,BigInteger length) { }
    static Optional<LogicalSlice> logicalSlice(ResolutionContracts.SemanticEntityId data,BigInteger extent,Ast.ReferenceModification modification) {
        if(!(modification.offset() instanceof Ast.LiteralExpression p)||p.integerValue().isEmpty())return Optional.empty();
        var start=p.integerValue().orElseThrow().subtract(BigInteger.ONE);BigInteger length;
        if(modification.length()==null)length=extent.subtract(start);
        else if(modification.length() instanceof Ast.LiteralExpression n&&n.integerValue().isPresent())length=n.integerValue().orElseThrow();
        else return Optional.empty();
        return start.signum()>=0&&length.signum()>0&&start.add(length).compareTo(extent)<=0
            ?Optional.of(new LogicalSlice(data,start,length)):Optional.empty();
    }
    public record Analysis(Map<Key,ResolutionContracts.SemanticEntityId> wholeItems,Map<Key,LogicalSlice> slices,Map<Key,Fact> moves) {
        public Analysis {wholeItems=Map.copyOf(wholeItems);slices=Map.copyOf(slices);moves=Map.copyOf(moves);}
        public Optional<ResolutionContracts.SemanticEntityId> wholeItem(Key key){return Optional.ofNullable(wholeItems.get(key));}
        public Optional<LogicalSlice> slice(Key key){return Optional.ofNullable(slices.get(key));}
        public Optional<Fact> move(Key key){return Optional.ofNullable(moves.get(key));}
    }
    static Analysis analyze(CompilationUnitBuildResult frontend,ReferenceResolution resolution,Optional<StorageAccessSemantics> storage,
            Map<ResolutionContracts.SemanticEntityId,ScalarMoveSemantics.ScalarText> declarations,
            Map<ResolutionContracts.ProgramUnitId,com.imd.cobolexplorer.semanticproduct.FactDependencies> dependencies,
            Set<ResolutionContracts.SemanticEntityId> boundedText,
            java.util.function.BiPredicate<ResolutionContracts.SemanticEntityId,ResolutionContracts.SemanticEntityId> disjoint) {
        var whole=new HashMap<Key,ResolutionContracts.SemanticEntityId>();var slices=new HashMap<Key,LogicalSlice>();var moves=new HashMap<Key,Fact>();
        if(storage.isEmpty())return new Analysis(whole,slices,moves);
        var layout=storage.orElseThrow().layout();var views=new HashMap<ResolutionContracts.SemanticEntityId,LogicalView>();
        var allViews=new HashMap<Key,LogicalView>();layout.logicalViews().forEach(v->allViews.put(v.node(),v));
        var edited=new HashSet<ResolutionContracts.SemanticEntityId>();declarations.forEach((id,d)->{if(d.edited().isPresent())edited.add(id);});
        for(var unit:frontend.compilationUnit().programUnits()) {
            var representatives=new HashMap<String,String>();var graph=dependencies.get(unit.id());
            if(graph!=null)graph.bindings().forEach(b->{if(!b.exactCell().isEmpty())representatives.put(b.node(),b.exactCell());});
            for(var node:layout.layout(unit.id()).nodes())node.entity().ifPresent(id->{
                var view=allViews.get(node.id());var shape=declarations.get(id);
                if(view==null&&shape!=null) {
                    var representative=representatives.getOrDefault("storage-node:"+node.id().node(),"storage-node:"+node.id().node());
                    var root=new Key(unit.id(),Integer.parseInt(representative.substring("storage-node:".length())));
                    view=new LogicalView(node.id(),root,java.math.BigInteger.ZERO,java.math.BigInteger.valueOf(shape.extent()));
                }
                if(view!=null)views.put(id,view);
            });
        }
        var bindings=new HashMap<Key,ReferenceResolution.Entry>();resolution.entries().forEach(e->bindings.put(new Key(e.occurrence().programUnitId(),e.occurrence().referenceAstNodeId()),e));
        for(var unit:frontend.compilationUnit().programUnits()) {
            var pending=new ArrayDeque<Ast.Node>();pending.push(unit.program());var statements=new ArrayList<Ast.MoveStatement>();
            while(!pending.isEmpty()) {
                var node=pending.pop();if(node instanceof Ast.Program&&node!=unit.program())continue;
                if(node instanceof Ast.MoveStatement move)statements.add(move);
                if(node instanceof Ast.DataReference r&&r.understanding()==Ast.ReferenceUnderstanding.STRUCTURED&&r.subscriptGroups().isEmpty()) {
                    var key=new Key(unit.id(),r.meta().id());var binding=bindings.get(key);
                    if(binding!=null&&binding.status()==ResolutionContracts.ResolutionStatus.RESOLVED&&binding.candidates().size()==1&&binding.selectedCandidate().isPresent()) {
                        var id=binding.selectedCandidate().orElseThrow().entityId();var view=views.get(id);
                        if(view!=null) {
                            if(r.referenceModification()==null)whole.put(key,id);
                            else logicalSlice(id,view.length(),r.referenceModification()).ifPresent(a->slices.put(key,a));
                        }
                    }
                }
                pending.addAll(Ast.children(node));
            }
            for(var move:statements)moves.put(new Key(unit.id(),move.meta().id()),analyze(move,unit.id(),whole,slices,views,edited,boundedText,disjoint));
        }
        return new Analysis(whole,slices,moves);
    }
    private LogicalMoveSemantics() { }
    public static Optional<String> literal(Ast.Expression source) {
        if(source instanceof Ast.LiteralExpression l) {
            if(zero(l))return Optional.of("0");
            if(l.integerDigits().isPresent())return l.integerDigits().map(Ast.LogicalText::value);
            if(l.logicalText().isPresent())return l.logicalText().map(Ast.LogicalText::value);
            if(l.figurativeText().filter(f->f==Ast.FigurativeText.SPACES).isPresent())return Optional.of(" ");
        }
        return Optional.empty();
    }
    public static Optional<Ast.FigurativeText> collatingFill(Ast.Expression source) {
        return source instanceof Ast.LiteralExpression l?l.figurativeText()
            .filter(f->f==Ast.FigurativeText.LOW_VALUES||f==Ast.FigurativeText.HIGH_VALUES):Optional.empty();
    }
    public static boolean zero(Ast.Expression source) {
        return source instanceof Ast.LiteralExpression l&&l.figurativeText().filter(f->f==Ast.FigurativeText.ZERO).isPresent();
    }
    static Fact analyze(Ast.MoveStatement move, ResolutionContracts.ProgramUnitId unit,
            Map<Key,ResolutionContracts.SemanticEntityId> references,
            Map<Key,LogicalSlice> slices,Map<ResolutionContracts.SemanticEntityId,LogicalView> views,Set<ResolutionContracts.SemanticEntityId> editedReceivers,Set<ResolutionContracts.SemanticEntityId> boundedText,
            java.util.function.BiPredicate<ResolutionContracts.SemanticEntityId,ResolutionContracts.SemanticEntityId> disjoint) {
        var literal=literal(move.source());
        if(move.corresponding()||move.targets().isEmpty()||move.targets().stream().anyMatch(t->!(t instanceof Ast.DataReference)))
            return new Fact(false,false,Optional.empty());
        var from=access(new Key(unit,move.source().meta().id()),references,slices,views);
        var sourceKey=new Key(unit,move.source().meta().id());
        var sourceId=slices.containsKey(sourceKey)?slices.get(sourceKey).data():references.get(sourceKey);
        boolean allDisjoint=from!=null,any=false,all=true;
        for(var receiver:move.targets()) {
            var receiverId=references.get(new Key(unit,receiver.meta().id()));
            var to=editedReceivers.contains(receiverId)?null:access(new Key(unit,receiver.meta().id()),references,slices,views);
            any|=to!=null;all&=to!=null;
            var receiverKey=new Key(unit,receiver.meta().id());
            var targetId=slices.containsKey(receiverKey)?slices.get(receiverKey).data():receiverId;
            if(boundedText.contains(sourceId)||boundedText.contains(targetId))allDisjoint&=sourceId!=null&&targetId!=null&&disjoint.test(sourceId,targetId);
            // Sharing a source family may change subsequent reads. Do not assert an
            // exact sequence for language-undefined overlap or an unproved receiver.
            allDisjoint&=to!=null&&from!=null&&(!from.root().equals(to.root())||from.start().add(from.length()).compareTo(to.start())<=0||to.start().add(to.length()).compareTo(from.start())<=0);
        }
        boolean fill=literal.isPresent()||collatingFill(move.source()).isPresent();
        return new Fact(fill?any:allDisjoint,fill?all:allDisjoint,literal);
    }
    private static LogicalView access(Key key,Map<Key,ResolutionContracts.SemanticEntityId> references,
            Map<Key,LogicalSlice> slices,Map<ResolutionContracts.SemanticEntityId,LogicalView> views) {
        var slice=slices.get(key);var whole=views.get(slice==null?references.get(key):slice.data());
        return whole==null||slice==null?whole:new LogicalView(whole.node(),whole.root(),whole.start().add(slice.start()),slice.length());
    }

}
