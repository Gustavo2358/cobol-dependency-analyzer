package com.imd.cobolexplorer;

import java.util.*;
import java.util.function.Consumer;
import static com.imd.cobolexplorer.DependencyFlow.*;

/** Local address alternatives, not caller/path environments. A merged offset
 * must not erase a known substring. Stream the finite operand product and also
 * evaluate its unknown remainder; do not materialize the Cartesian states. */
final class DefinitionValues {
    final DependencyFlow flow;
    final IdentityHashMap<Ast.Node,List<Integer>> addressReads=new IdentityHashMap<>();
    long combinations;
    DefinitionValues(DependencyFlow flow){this.flow=flow;}
    List<Integer> addresses(Ast.Node node){
        return addressReads.computeIfAbsent(node,n->{
            var ids=new TreeSet<Integer>();var todo=new ArrayDeque<Ast.Node>();if(n!=null)todo.add(n);
            while(!todo.isEmpty()){
                var next=todo.remove();
                if(next instanceof Ast.DataReference r&&r.referenceModification()!=null){
                    var mod=r.referenceModification();ids.addAll(flow.declarations.reads(mod.offset()));
                    if(mod.length()!=null)ids.addAll(flow.declarations.reads(mod.length()));
                }
                todo.addAll(DependencyDeclarations.valueChildren(next));
            }
            return List.copyOf(ids);
        });
    }
    void alternatives(Ast.Node node,State input,Consumer<State> consume){
        var ids=addresses(node);if(ids.isEmpty()){consume.accept(input);return;}
        var options=new ArrayList<List<DependencyValues>>();
        for(int id:ids){
            var value=input.get(id);var choices=new ArrayList<DependencyValues>();
            for(String text:new TreeSet<>(value.values()))choices.add(DependencyValues.known(text));
            if(value.unknown()||choices.isEmpty())choices.add(value.values().isEmpty()?value:DependencyValues.UNKNOWN);
            options.add(choices);
        }
        int[] selected=new int[ids.size()];
        while(true){
            if(++combinations>flow.maxWork)throw new IllegalStateException("RESOURCE_LIMIT: local address alternatives exceeded --max-work="+flow.maxWork);
            var values=new HashMap<>(input.values());
            for(int i=0;i<ids.size();i++)values.put(ids.get(i),options.get(i).get(selected[i]));
            consume.accept(new State(values,Map.of(),Set.of()));
            int i=0;while(i<selected.length&&++selected[i]==options.get(i).size()){selected[i]=0;i++;}
            if(i==selected.length)return;
        }
    }
    DependencyValues read(Ast.Expression expression,State input){
        var result=new DependencyValues[]{SparseDefinitions.BOTTOM};
        alternatives(expression,input,state->result[0]=SparseDefinitions.join(result[0],flow.read(expression,state)));
        return result[0];
    }
    State transfer(Ast.Statement statement,State input){
        if(addresses(statement).isEmpty())return flow.definitionTransfer(statement,input);
        // Keep the conservative remainder of the merged address as well as its
        // known alternatives. Existing weak-write candidates remain published.
        var result=new HashMap<Integer,DependencyValues>(flow.definitionTransfer(statement,input).values());
        alternatives(statement,input,state->flow.definitionTransfer(statement,state).values().forEach((id,value)->
            result.merge(id,value,SparseDefinitions::join)));
        return new State(result,Map.of(),Set.of());
    }
}
