package com.imd.cobolexplorer;
import java.util.*;
/** Source-owned numeric MOVE proofs. No interval enumeration or physical representation inference. */
public final class NumericMoveSemantics {
    public record Transfer(int target,ResolutionContracts.SemanticEntityId receiver,
        Optional<ResolutionContracts.SemanticEntityId> source,Optional<java.math.BigDecimal> value) { }
    private final Map<ScalarMoveSemantics.NodeKey,List<Transfer>> moves;
    private NumericMoveSemantics(Map<ScalarMoveSemantics.NodeKey,List<Transfer>> moves){this.moves=Map.copyOf(moves);}
    public List<Transfer> transfers(ResolutionContracts.ProgramUnitId unit,int statement){return moves.getOrDefault(new ScalarMoveSemantics.NodeKey(unit,statement),List.of());}
    public Optional<ResolutionContracts.SemanticEntityId> source(ResolutionContracts.ProgramUnitId unit,int statement) {
        var facts=transfers(unit,statement);return facts.isEmpty()?Optional.empty():facts.get(0).source();
    }
    static NumericMoveSemantics analyze(CompilationUnitBuildResult frontend,NumericSemantics numbers,
            Map<ScalarMoveSemantics.NodeKey,ReferenceResolution.Entry> references,
            Map<ResolutionContracts.SemanticEntityId,ScalarMoveSemantics.ScalarText> texts) {
        var result=new HashMap<ScalarMoveSemantics.NodeKey,List<Transfer>>();
        for(var unit:frontend.compilationUnit().programUnits()) {
            var pending=new ArrayDeque<Ast.Node>();pending.push(unit.program());
            while(!pending.isEmpty()) {
                var node=pending.pop();Ast.children(node).forEach(pending::push);
                if(!(node instanceof Ast.MoveStatement move)||move.corresponding()||!move.meta().provenance().exact())continue;
                var sourceNumber=numbers.wholeNumber(move.source(),unit.id(),references);
                var sourceText=text(move.source(),unit.id(),references,texts).filter(id->texts.get(id).edited().isEmpty());
                var source=sourceNumber.or(()->sourceText);
                Optional<java.math.BigDecimal> literal=move.source() instanceof Ast.LiteralExpression l&&l.meta().provenance().exact()?l.numericValue().or(()->l.logicalText().flatMap(t->com.imd.cobolexplorer.semanticproduct.NumericMoveRule.textNumber(t.value()))):Optional.empty();
                if(source.isEmpty()&&literal.isEmpty())continue;
                var transfers=new ArrayList<Transfer>();boolean sourcePreserved=true;
                for(var target:move.targets()) {
                    var receiver=numbers.wholeNumber(target,unit.id(),references);
                    var text=move.targets().size()>1?text(target,unit.id(),references,texts):Optional.<ResolutionContracts.SemanticEntityId>empty();
                    boolean formatting=text.isPresent()&&(sourceNumber.isPresent()
                        ?texts.get(text.orElseThrow()).edited().isPresent()||numbers.declaration(sourceNumber.orElseThrow()).orElseThrow().scale()<=0
                        :texts.get(text.orElseThrow()).edited().isPresent()&&move.source() instanceof Ast.LiteralExpression l&&l.numericValue().isPresent());
                    boolean disjoint=source.isEmpty()||receiver.map(r->numbers.exactCell(source.orElseThrow())&&numbers.exactCell(r)
                        ||numbers.disjoint(source.orElseThrow(),r)).orElseGet(()->text.isPresent()&&numbers.disjoint(source.orElseThrow(),text.orElseThrow()));
                    if(!disjoint)sourcePreserved=false;
                    if(sourcePreserved&&formatting) {
                        transfers.add(new Transfer(target.meta().id(),text.orElseThrow(),source,literal));
                    } else if(sourcePreserved&&receiver.isPresent()) {
                        var n=numbers.declaration(receiver.orElseThrow()).orElseThrow();
                        var resultValue=literal.map(v->com.imd.cobolexplorer.semanticproduct.NumericMoveRule.fit(v,n.digits(),n.scale(),n.signed(),n.representation().name(),n.trunc().name()));
                        boolean common=literal.isEmpty()||!com.imd.cobolexplorer.semanticproduct.NumericMoveRule.requiresFit(n.representation().name(),n.trunc().name())
                            ||fits(literal.orElseThrow(),n);
                        transfers.add(new Transfer(target.meta().id(),receiver.orElseThrow(),source,common?resultValue:Optional.empty()));
                    } else if(source.isPresent())sourcePreserved=false;
                }
                // DATA reads are exact through the proved prefix. An unknown peer may change the sending cell;
                // subsequent receivers retain uncertainty without inventing a snapshot of its old value.
                if(!transfers.isEmpty())result.put(new ScalarMoveSemantics.NodeKey(unit.id(),move.meta().id()),List.copyOf(transfers));
            }
        }
        return new NumericMoveSemantics(result);
    }
    private static Optional<ResolutionContracts.SemanticEntityId> text(Ast.Expression expression,ResolutionContracts.ProgramUnitId unit,
            Map<ScalarMoveSemantics.NodeKey,ReferenceResolution.Entry> references,Map<ResolutionContracts.SemanticEntityId,ScalarMoveSemantics.ScalarText> texts) {
        if(!(expression instanceof Ast.DataReference r)||!r.meta().provenance().exact()||r.understanding()!=Ast.ReferenceUnderstanding.STRUCTURED
                ||!r.subscriptGroups().isEmpty()||r.referenceModification()!=null)return Optional.empty();
        var binding=references.get(new ScalarMoveSemantics.NodeKey(unit,r.meta().id()));
        return binding!=null&&binding.status()==ResolutionContracts.ResolutionStatus.RESOLVED&&binding.candidates().size()==1
            ?binding.selectedCandidate().map(c->c.entityId()).filter(texts::containsKey):Optional.empty();
    }
    /** Without a TRUNC option, only the intersection of STD/OPT/BIN is a positive fact. */
    private static boolean fits(java.math.BigDecimal value,NumericSemantics.NumberItem target) {
        return com.imd.cobolexplorer.semanticproduct.NumericMoveRule.fits(value,target.digits(),target.scale());
    }

}
