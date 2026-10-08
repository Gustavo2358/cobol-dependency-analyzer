package com.imd.cobolexplorer;

import java.util.*;
import static com.imd.cobolexplorer.StatementEffectSummary.*;

/** Separates a MOVE's receiving footprint from unavailable value transforms. */
final class MoveEffectSemantics {
    private MoveEffectSemantics() { }
    static Optional<StatementEffectSummary> analyze(Ast.MoveStatement move) {
        var reads=new LinkedHashSet<Ast.DataReference>();var writes=new ArrayList<Ast.DataReference>();
        var expressions=new ArrayDeque<Ast.Expression>();expressions.add(move.source());
        boolean output=false,input=false;
        for(var target:move.targets()) {
            if(target instanceof Ast.DataReference ref&&ref.understanding()==Ast.ReferenceUnderstanding.STRUCTURED
                    &&ref.subscriptGroups().isEmpty()&&ref.referenceModification()==null) {
                writes.add(ref);for(var child:Ast.children(ref))collectExpressions(child,expressions);
            } else if(target instanceof Ast.SpecialRegisterExpression reg&&reg.registerName().toUpperCase(Locale.ROOT).equals("RETURN-CODE")&&reg.operands().isEmpty())output=true;
            else return Optional.empty();
        }
        while(!expressions.isEmpty()) {
            var e=expressions.removeFirst();
            if(e instanceof Ast.DataReference ref) {
                if(ref.understanding()!=Ast.ReferenceUnderstanding.STRUCTURED)return Optional.empty();reads.add(ref);
            } else if(e instanceof Ast.FunctionExpression function) {
                // User-defined/unknown functions may have effects beyond their value.
                if(!Set.of("CURRENT-DATE","UPPER-CASE","TRIM").contains(function.functionName().toUpperCase(Locale.ROOT)))return Optional.empty();
                input|=function.functionName().toUpperCase(Locale.ROOT).equals("CURRENT-DATE");
            } else if(e instanceof Ast.SpecialRegisterExpression reg) {
                if(!Set.of("RETURN-CODE","LENGTH").contains(reg.registerName().toUpperCase(Locale.ROOT)))return Optional.empty();
                input|=reg.registerName().toUpperCase(Locale.ROOT).equals("RETURN-CODE");
            } else if(!(e instanceof Ast.LiteralExpression)&&!(e instanceof Ast.OperationExpression))return Optional.empty();
            for(var child:Ast.children(e))collectExpressions(child,expressions);
        }
        return Optional.of(new StatementEffectSummary(List.copyOf(reads),writes,List.of(),List.of(),Bound.NONE,Bound.NONE,Bound.NONE,
            input&&output?Environment.UNKNOWN:input?Environment.INPUT:output?Environment.OUTPUT:Environment.NONE,
            ValueTransform.UNKNOWN,Proof.MOVE_TARGETS));
    }
    private static void collectExpressions(Ast.Node node,ArrayDeque<Ast.Expression> out) {
        if(node instanceof Ast.Expression e)out.add(e);
        else for(var child:Ast.children(node))collectExpressions(child,out);
    }
}
