package com.imd.cobolexplorer;
import java.util.*;
/** Explicit SQL hosts plus an open footprint for SQLCA, cursor state and external effects. */
final class SqlCommandSemantics {
    static Optional<StatementEffectSummary> effects(Ast.Statement statement) {
        if(!(statement instanceof Ast.EmbeddedLanguageStatement s)||s.language()!=Ast.EmbeddedLanguage.SQL)return Optional.empty();
        var parsed=SqlCommandSyntax.parse(s.rawText());if(parsed.isEmpty())return Optional.empty();
        if(parsed.get().declaration())return Optional.of(new StatementEffectSummary(List.of(),List.of(),List.of(),List.of(),
            StatementEffectSummary.Bound.NONE,StatementEffectSummary.Bound.NONE,StatementEffectSummary.Bound.NONE,
            StatementEffectSummary.Environment.NONE,StatementEffectSummary.ValueTransform.NONE,StatementEffectSummary.Proof.NO_OP));
        var byOffset=new HashMap<Integer,Ast.EmbeddedHostOperand>();
        for(var h:s.hostOperands())if(byOffset.put(h.optionStart(),h)!=null)return Optional.empty();
        if(byOffset.size()!=parsed.get().hosts().size())return Optional.empty();
        var reads=new ArrayList<Ast.DataReference>();var writes=new ArrayList<Ast.DataReference>();
        for(var h:parsed.get().hosts()) {
            var operand=byOffset.get(h.start());if(operand==null||operand.role()!=h.role())return Optional.empty();
            if(h.role()==Ast.EmbeddedHostRole.WRITE)writes.add(operand.reference());else reads.add(operand.reference());
        }
        return Optional.of(new StatementEffectSummary(reads,writes,List.of(),List.of(),
            StatementEffectSummary.Bound.ALL,StatementEffectSummary.Bound.ALL,StatementEffectSummary.Bound.ALL,
            StatementEffectSummary.Environment.UNKNOWN,StatementEffectSummary.ValueTransform.UNKNOWN,StatementEffectSummary.Proof.SQL_HOST_OPERANDS));
    }
}
