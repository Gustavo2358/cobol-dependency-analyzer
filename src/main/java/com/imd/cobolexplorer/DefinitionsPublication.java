package com.imd.cobolexplorer;

import java.util.*;

/** Exact product aggregation: enumerate an immutable candidate set once per
 * dependency kind/validation rule, preserving earliest output provenance and
 * every unsupported-file notice. Identity sharing never equates unequal sets. */
final class DefinitionsPublication {
    interface Sink {void add(String type,String name,Ast.SourceLocation source);}
    record Kind(String type,boolean strictFile) { }
    static final class Group {
        Ast.SourceLocation earliest;final List<DependencyFlow.Query> queries=new ArrayList<>();
        void accept(DependencyFlow.Query query){
            var at=query.statement().meta().provenance().original();
            if(earliest==null||Comparator.comparing(Ast.SourceLocation::file).thenComparingInt(Ast.SourceLocation::startLine).compare(at,earliest)<0)earliest=at;
            queries.add(query);
        }
    }
    static void publish(Map<DependencyFlow.Query,DependencyValues> answers,Sink sink,Set<String> notices){
        var sets=new IdentityHashMap<Set<String>,Map<Kind,Group>>();
        for(var answer:answers.entrySet()){
            var q=answer.getKey();var names=answer.getValue().values();
            var kind=new Kind(q.type(),q.type().equals("file")&&q.literal().isEmpty());
            sets.computeIfAbsent(names,k->new HashMap<>()).computeIfAbsent(kind,k->new Group()).accept(q);
        }
        for(var set:sets.entrySet())for(var entry:set.getValue().entrySet()){
            var kind=entry.getKey();var group=entry.getValue();boolean unsupported=false;
            for(String name:set.getKey()){
                if(kind.type().equals("file")&&(kind.strictFile()&&name.length()!=8||name.stripTrailing().length()>8||!name.stripTrailing().matches("[A-Z0-9$@#]+")))unsupported=true;
                else sink.add(kind.type(),name,group.earliest);
            }
            if(unsupported)for(var q:group.queries)notices.add("CICS_FILE_NAME_UNSUPPORTED at "+q.statement().meta().provenance().original().startLine());
        }
    }
}
