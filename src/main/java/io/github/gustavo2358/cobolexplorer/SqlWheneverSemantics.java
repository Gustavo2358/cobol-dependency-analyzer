package io.github.gustavo2358.cobolexplorer;
import java.util.*;
/** Lexical directives, independent of runtime reachability and PERFORM activation. */
final class SqlWheneverSemantics {
    record Route(SqlCommandSyntax.Condition condition,Ast.EmbeddedLanguageStatement directive,
                 Optional<Ast.ProcedureReference> target) { }
    static Map<Integer,List<Route>> analyze(Ast.Division procedure) {
        var statements=new ArrayList<Ast.EmbeddedLanguageStatement>();var todo=new ArrayDeque<Ast.Node>();todo.add(procedure);
        while(!todo.isEmpty()) {
            var n=todo.removeFirst();
            if(n instanceof Ast.EmbeddedLanguageStatement s&&s.language()==Ast.EmbeddedLanguage.SQL)statements.add(s);
            todo.addAll(Ast.children(n));
        }
        statements.sort(Comparator.comparingInt(s->s.meta().span().startToken()));
        var active=new EnumMap<SqlCommandSyntax.Condition,Route>(SqlCommandSyntax.Condition.class);
        var routes=new HashMap<Integer,List<Route>>();
        for(var s:statements) {
            var parsed=SqlCommandSyntax.parse(s.rawText()).orElse(null);if(parsed==null)continue;
            if(parsed.directive().isPresent()) {
                var directive=parsed.directive().orElseThrow();
                if(directive.target().isEmpty())active.remove(directive.condition());
                else active.put(directive.condition(),new Route(directive.condition(),s,
                    s.procedureOperands().size()==1?Optional.of(s.procedureOperands().get(0)):Optional.empty()));
            } else if(!parsed.declaration())routes.put(s.meta().id(),List.copyOf(active.values()));
        }
        return Map.copyOf(routes);
    }
}
