package io.github.gustavo2358.cobolexplorer;

import java.util.*;
import io.github.gustavo2358.cobolexplorer.semanticproduct.ConditionNames.Tree;

/** Post-binding normalization shared by IF, WHEN and UNTIL. No runtime evaluation. */
final class ScalarPredicateSemantics {
    private record State(Ast.Expression subject,Ast.RelationOperator operator) {
        static final State CLOSED=new State(null,Ast.RelationOperator.UNAVAILABLE);
    }
    private record Result(Tree tree,State state) { }
    private final Map<Integer,ConditionNameSemantics.Use> names;
    private final Set<Integer> data;
    ScalarPredicateSemantics(Map<Integer,ConditionNameSemantics.Use> names,Set<Integer> data){this.names=names;this.data=data;}
    Tree condition(Ast.Expression e){return condition(e,State.CLOSED).tree();}
    private static Tree node(String kind,String value,Tree... children){return new Tree(kind,value,List.of(children));}
    private static Tree unknown(){return node("UNKNOWN","");}
    private Result condition(Ast.Expression e,State state) {
        if(e instanceof Ast.GroupedCondition g){var inner=condition(g.inner(),state);return new Result(inner.tree(),inner.state().subject()==state.subject()?inner.state():State.CLOSED);}
        if(e instanceof Ast.NegatedCondition n){var inner=condition(n.operand(),state);return new Result(node("NOT","",inner.tree()),inner.state());}
        if(e instanceof Ast.LogicalCondition l){
            var children=new ArrayList<Tree>();
            for(var child:l.operands()){var result=condition(child,state);children.add(result.tree());state=result.state();}
            return new Result(new Tree(l.connector().name(),"",children),state);
        }
        Ast.DataReference nominal=e instanceof Ast.DataReference r?r:e instanceof Ast.ContextualConditionTail t?t.nominalReference():null;
        if(nominal!=null&&names.containsKey(nominal.meta().id()))return new Result(new Tree("TEST","condition-use:"+nominal.meta().id(),addresses(nominal)),State.CLOSED);
        if(e instanceof Ast.RelationCondition relation){
            var subject=relation.subject()==null?state.subject():relation.subject();
            var operator=relation.relationalOperator()==null?state.operator():relation.operatorKind();
            var next=new State(subject,operator);
            return new Result(compare(subject,operator,relation.object()),next);
        }
        if(nominal!=null&&state.subject()!=null)return new Result(compare(state.subject(),state.operator(),nominal),state);
        if(e instanceof Ast.LiteralExpression l){
            if(l.booleanValue().isPresent())return new Result(node("BOOL",l.booleanValue().get().toString()),State.CLOSED);
            if(state.subject()!=null)return new Result(compare(state.subject(),state.operator(),l),state);
        }
        return new Result(unavailable(e),State.CLOSED);
    }
    Tree selection(Ast.Expression subject,Ast.Expression object,boolean negated) {
        Tree value=subject instanceof Ast.LiteralExpression l&&l.booleanValue().isPresent()
            ? condition(object):compare(subject,Ast.RelationOperator.EQUAL,object);
        if(subject instanceof Ast.LiteralExpression l&&l.booleanValue().filter(v->!v).isPresent())negated=!negated;
        return negated?node("NOT","",value):value;
    }
    private Tree compare(Ast.Expression subject,Ast.RelationOperator operator,Ast.Expression object) {
        String kind=switch(operator){case EQUAL->"EQ";case NOT_EQUAL->"NE";case LESS->"LT";case LESS_EQUAL->"LE";case GREATER->"GT";case GREATER_EQUAL->"GE";default->null;};
        if(subject==null||kind==null)return unavailable(object);
        if(object instanceof Ast.DistributedOperandGroup group){
            if(group.operands().size()!=group.connectors().size()+1)return unknown();
            var disjunction=new ArrayList<Tree>();var conjunction=new ArrayList<Tree>();
            for(int i=0;i<group.operands().size();i++){
                conjunction.add(compare(subject,operator,group.operands().get(i)));
                if(i==group.connectors().size()||group.connectors().get(i)==Ast.LogicalConnector.OR){disjunction.add(conjunction.size()==1?conjunction.get(0):new Tree("AND","",conjunction));conjunction=new ArrayList<>();}
            }
            return disjunction.size()==1?disjunction.get(0):new Tree("OR","",disjunction);
        }
        var left=value(subject);var right=value(object);if(left==null||right==null)return unavailable(subject,object);
        return node(kind,"",left,right);
    }
    private Tree unavailable(Ast.Expression... expressions) {
        var reads=new ArrayList<Tree>();var seen=new HashSet<Integer>();var todo=new ArrayDeque<Ast.Node>();
        for(var expression:expressions)if(expression!=null)todo.add(expression);
        while(!todo.isEmpty()){var n=todo.removeFirst();if(n instanceof Ast.DataReference r&&seen.add(r.meta().id())&&data.contains(r.meta().id())){var read=value(r);if(read!=null)reads.add(read);}else Ast.children(n).forEach(todo::add);}
        return new Tree("UNKNOWN",Arrays.stream(expressions).allMatch(this::pure)?"PURE":"",reads);
    }
    private boolean pure(Ast.Expression expression) {
        if(expression==null)return false;
        var todo=new ArrayDeque<Ast.Node>();todo.add(expression);
        while(!todo.isEmpty()) {
            var n=todo.removeFirst();
            if(n instanceof Ast.FunctionExpression||n instanceof Ast.RawExpression||n instanceof Ast.PreservedExpression)return false;
            if(n instanceof Ast.OperationExpression op&&!Set.of("+","-","*","GROUP").contains(op.operator()))return false;
            if(n instanceof Ast.DataReference r&&(r.understanding()!=Ast.ReferenceUnderstanding.STRUCTURED||!data.contains(r.meta().id())))return false;
            Ast.children(n).forEach(todo::add);
        }
        return true;
    }
    private Tree value(Ast.Expression e) {
        if(e instanceof Ast.DataReference r&&data.contains(r.meta().id())&&r.understanding()==Ast.ReferenceUnderstanding.STRUCTURED){
            return new Tree("READ","reference:"+r.meta().id(),addresses(r));
        }
        if(e instanceof Ast.LiteralExpression l){
            if(l.numericValue().isPresent())return node("NUMBER",l.numericValue().get().toPlainString());
            if(l.logicalText().isPresent())return node("TEXT",l.logicalText().get().value());
            if(l.figurativeText().isPresent())return node(l.figurativeText().get().name(),"");
        }
        return null;
    }
    private List<Tree> addresses(Ast.DataReference reference) {
        var reads=new ArrayList<Tree>();var todo=new ArrayDeque<Ast.Node>();reference.subscriptGroups().forEach(todo::add);
        if(reference.referenceModification()!=null)todo.add(reference.referenceModification());
        while(!todo.isEmpty()) {
            var n=todo.removeFirst();if(n instanceof Ast.DataReference r&&data.contains(r.meta().id()))reads.add(new Tree("READ","reference:"+r.meta().id(),addresses(r)));
            else Ast.children(n).forEach(todo::add);
        }
        return List.copyOf(reads);
    }

}
