package io.github.gustavo2358.cobolexplorer;

import java.util.*;
import static io.github.gustavo2358.cobolexplorer.StorageLayoutSemantics.*;

/** Character MOVE proofs; no physical layout or runtime value propagation. */
public final class LogicalMoveSemantics {
    public record Fact(boolean admitted, Optional<String> literal, Map<Integer,String> fitted) {
        public Fact { fitted=Map.copyOf(fitted); }
    }
    private LogicalMoveSemantics() { }
    static Optional<String> literal(Ast.Expression source) {
        if(source instanceof Ast.LiteralExpression l) {
            if(l.logicalText().isPresent())return l.logicalText().map(Ast.LogicalText::value);
            if(l.figurativeText().filter(f->f==Ast.FigurativeText.SPACES).isPresent())return Optional.of(" ");
        }
        return Optional.empty();
    }
    static Fact analyze(Ast.MoveStatement move, ResolutionContracts.ProgramUnitId unit,
            Map<Key,ResolutionContracts.SemanticEntityId> references,
            Map<ResolutionContracts.SemanticEntityId,LogicalView> views) {
        var literal=literal(move.source());
        if(move.corresponding()||move.targets().isEmpty()||move.targets().stream().anyMatch(t->!(t instanceof Ast.DataReference)))
            return new Fact(false,Optional.empty(),Map.of());
        var from=views.get(references.get(new Key(unit,move.source().meta().id())));
        var fitted=new LinkedHashMap<Integer,String>();boolean allDisjoint=from!=null,any=false;
        for(var receiver:move.targets()) {
            var to=views.get(references.get(new Key(unit,receiver.meta().id())));
            any|=to!=null;
            // Sharing a source family may change subsequent reads. Do not assert an
            // exact sequence for language-undefined overlap or an unproved receiver.
            allDisjoint&=to!=null&&from!=null&&!from.root().equals(to.root());
            if(to!=null&&literal.isPresent()) {
                var value=literal.orElseThrow();int n=to.length().intValueExact(),size=value.codePointCount(0,value.length());
                fitted.put(receiver.meta().id(),size>n?value.substring(0,value.offsetByCodePoints(0,n)):value+" ".repeat(n-size));
            }
        }
        return new Fact(literal.isPresent()?any:allDisjoint,literal,fitted);
    }
}
