package io.github.gustavo2358.cobolexplorer;

import java.math.BigInteger;
import java.util.*;
import io.github.gustavo2358.cobolexplorer.SourceStorageGeometry.Span;
import io.github.gustavo2358.cobolexplorer.SourceStorageGeometry.Descriptor;

/** Source geometry proves value-cell identity, never a runtime encoding or physical profile. */
final class StorageValueCells {
    record Leaf(Span span,Descriptor descriptor) { }
    private final Map<Integer,Span> spans;
    private final Map<Integer,Integer> representatives;
    private final Map<Integer,List<Span>> cells;
    private StorageValueCells(Map<Integer,Span> spans,Map<Integer,Integer> representatives,Map<Integer,List<Span>> cells) {
        this.spans=Map.copyOf(spans);this.representatives=Map.copyOf(representatives);this.cells=Map.copyOf(cells);
    }
    OptionalInt representative(int node) {
        var n=representatives.get(node);return n==null?OptionalInt.empty():OptionalInt.of(n);
    }
    /** The canonical cells are disjoint; binary search plus emitted matches bounds each query. */
    List<Integer> overlapping(int node,int region) {
        var all=cells.getOrDefault(region,List.of());var span=spans.get(node);
        if(span==null)return all.stream().map(Span::node).toList();
        int lo=0,hi=all.size();
        while(lo<hi){int middle=(lo+hi)>>>1;if(all.get(middle).end().compareTo(span.start())<=0)lo=middle+1;else hi=middle;}
        var result=new ArrayList<Integer>();
        for(int i=lo;i<all.size()&&all.get(i).start().compareTo(span.end())<0;i++)result.add(all.get(i).node());
        return List.copyOf(result);
    }
    static StorageValueCells analyze(StorageComponents.Unit structure,SourceStorageGeometry geometry) {
        var spans=geometry.spans();var descriptors=geometry.descriptors();
        var leaves=new HashMap<Integer,List<Leaf>>();
        for(var position:structure.positions()) {
            int id=position.data().meta().id();var span=spans.get(id);var descriptor=descriptors.get(id);
            if(span!=null&&descriptor!=null&&(!position.data().filler()||descriptor.category().equals("REPEATED")))
                leaves.computeIfAbsent(span.region(),k->new ArrayList<>()).add(new Leaf(span,descriptor));
        }
        var representatives=new HashMap<Integer,Integer>();var cells=new HashMap<Integer,List<Span>>();
        for(var entry:leaves.entrySet()) {
            var sorted=entry.getValue();sorted.sort(Comparator.comparing((Leaf l)->l.span().start()).thenComparing(l->l.span().end()).thenComparingInt(l->l.span().node()));
            var closed=new ArrayList<Span>();int first=0;
            while(first<sorted.size()) {
                var head=sorted.get(first);var end=head.span().end();int after=first+1;boolean same=true;int representative=head.span().node();
                while(after<sorted.size()&&sorted.get(after).span().start().compareTo(end)<0) {
                    var next=sorted.get(after++);same&=next.span().start().equals(head.span().start())&&next.span().length().equals(head.span().length())&&next.descriptor().equals(head.descriptor());
                    end=end.max(next.span().end());representative=Math.min(representative,next.span().node());
                }
                if(same&&!head.descriptor().category().equals("REPEATED")&&head.span().length().signum()>0) {
                    for(int i=first;i<after;i++)representatives.put(sorted.get(i).span().node(),representative);
                    closed.add(new Span(representative,entry.getKey(),head.span().start(),head.span().length()));
                }
                first=after;
            }
            cells.put(entry.getKey(),List.copyOf(closed));
        }
        return new StorageValueCells(spans,representatives,cells);
    }
}
