package com.imd.cobolexplorer;

import java.util.*;

/** Unit-owned indexes for the existing typed ordinary-entry and member-overlap rules. */
final class ProcedureTransferIndex {
    record Transfer(int source,int destination) { }
    record Query(boolean excluded,long edgeVisits) { }
    private final Map<Integer,List<Integer>> incoming;
    private final Set<Integer> unclosed;
    ProcedureTransferIndex(List<Transfer> transfers,Set<Integer> unclosed) {
        var index=new HashMap<Integer,List<Integer>>();
        for(var edge:transfers)index.computeIfAbsent(edge.destination(),k->new ArrayList<>()).add(edge.source());
        index.replaceAll((k,v)->List.copyOf(v));incoming=Map.copyOf(index);this.unclosed=Set.copyOf(unclosed);
    }
    Query query(Set<Integer> members) {
        if(!members.containsAll(unclosed))return new Query(false,0);
        long visits=0;
        for(int destination:members)for(int source:incoming.getOrDefault(destination,List.of())) {
            visits++;if(!members.contains(source))return new Query(false,visits);
        }
        return new Query(true,visits);
    }
    static Set<Set<Integer>> overlapping(Collection<Set<Integer>> ranges) {
        var distinct=new LinkedHashSet<Set<Integer>>();for(var members:ranges)distinct.add(Set.copyOf(members));
        var ordered=new ArrayList<>(distinct);var first=new HashMap<Integer,Integer>();var flags=new boolean[ordered.size()];
        for(int ordinal=0;ordinal<ordered.size();ordinal++) {
            Integer owner=ordinal;
            for(Integer member:ordered.get(ordinal)) {
                var previous=first.putIfAbsent(member,owner);
                if(previous!=null){flags[previous]=true;flags[ordinal]=true;}
            }
        }
        var overlaps=new HashSet<Set<Integer>>();
        for(int ordinal=0;ordinal<ordered.size();ordinal++)if(flags[ordinal])overlaps.add(ordered.get(ordinal));
        return Set.copyOf(overlaps);
    }
}
