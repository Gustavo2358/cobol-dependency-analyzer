package com.imd.cobolexplorer;

import java.math.BigInteger;
import java.util.*;

/** Source-proved storage intervals. Representation width does not select a codec or a runtime value. */
final class SourceStorageGeometry {
    record Span(int node,int region,BigInteger start,BigInteger length) {
        BigInteger end(){return start.add(length);}
    }
    record Descriptor(String category,int digits,int scale,boolean signed,String representation) { }
    private final Map<Integer,Span> spans;
    private final Map<Integer,Descriptor> descriptors;
    private SourceStorageGeometry(Map<Integer,Span> spans,Map<Integer,Descriptor> descriptors) {
        this.spans=Map.copyOf(spans);this.descriptors=Map.copyOf(descriptors);
    }
    Map<Integer,Span> spans(){return spans;}
    Map<Integer,Descriptor> descriptors(){return descriptors;}
    static SourceStorageGeometry analyze(StorageComponents.Unit structure,Map<Integer,StorageLayoutSemantics.Shape> textShapes) {
        var numbers=NumericSemantics.layoutShapes(structure);
        var extents=new HashMap<Integer,BigInteger>();var descriptors=new HashMap<Integer,Descriptor>();
        var positions=structure.positions();var repeated=new HashSet<Integer>();
        for(int i=positions.size()-1;i>=0;i--) {
            var p=positions.get(i);var d=p.data();int id=d.meta().id();
            if(d.meta().syntheticModel()||d.clauses().stream().anyMatch(Ast.PreservedDataClause.class::isInstance))continue;
            var occurs=d.clauses().stream().filter(Ast.OccursClause.class::isInstance).map(Ast.OccursClause.class::cast).toList();
            BigInteger count=BigInteger.ONE;
            if(!occurs.isEmpty()) {
                if(occurs.size()!=1)continue;var dimension=occurs.get(0);
                if(dimension.dependingOn()!=null||dimension.maximum()!=null||!(dimension.minimum() instanceof Ast.LiteralExpression literal)
                        ||literal.integerValue().isEmpty()||literal.integerValue().orElseThrow().signum()<=0)continue;
                count=literal.integerValue().orElseThrow();repeated.add(id);
            }
            var children=structure.children().getOrDefault(id,List.of());var text=textShapes.get(id);var numeric=numbers.get(id);
            if(numeric!=null) {
                int size=switch(numeric.representation()) {
                    case DISPLAY -> numeric.digits();
                    case PACKED_DECIMAL -> (numeric.digits()+2)/2;
                    case BINARY,NATIVE_BINARY -> numeric.digits()<=4?2:numeric.digits()<=9?4:8;
                    case UNAVAILABLE -> 0;
                };
                if(size>0){extents.put(id,BigInteger.valueOf(size));descriptors.put(id,new Descriptor("NUMBER",numeric.digits(),numeric.scale(),numeric.signed(),numeric.representation().name()));}
            } else if(text!=null&&(text.supported()&&text.kind()==StorageLayoutSemantics.Kind.ELEMENTARY||!occurs.isEmpty()&&children.isEmpty()&&textElement(d))&&text.leafExtent().isPresent()) {
                extents.put(id,text.leafExtent().orElseThrow());descriptors.put(id,new Descriptor("TEXT",text.leafExtent().orElseThrow().intValueExact(),0,false,"DISPLAY"));
            } else if(!children.isEmpty()&&d.clauses().stream().allMatch(c->c instanceof Ast.RedefinesClause||c instanceof Ast.ValueClause||c instanceof Ast.UsageClause||c instanceof Ast.OccursClause)) {
                BigInteger size=BigInteger.ZERO;
                for(var component:children){var width=width(component,extents);if(width==null){size=null;break;}size=size.add(width);}
                if(size!=null)extents.put(id,size);
            }
            if(repeated.contains(id)&&extents.containsKey(id)) {
                extents.put(id,extents.get(id).multiply(count));
                descriptors.put(id,new Descriptor("REPEATED",0,0,false,"UNAVAILABLE"));
            }
        }
        var spans=new HashMap<Integer,Span>();
        for(var component:structure.rootComponents()) {
            if(!structure.structureProven()||!structure.rootRelationsProven()
                ||component.members().stream().anyMatch(structure.uncertainRoots()::contains)||width(component,extents)==null)continue;
            for(int id:component.members()){spans.put(id,new Span(id,component.representative(),BigInteger.ZERO,extents.get(id)));}
        }

        for(var position:positions) {
            int id=position.data().meta().id();var span=spans.get(id);if(span==null)continue;

            if(repeated.contains(id))continue; // One range for the entire table, never one cell per occurrence.
            var cursor=span.start();
            for(var component:structure.children().getOrDefault(id,List.of())) {
                for(int member:component.members())spans.put(member,new Span(member,span.region(),cursor,extents.get(member)));
                cursor=cursor.add(Objects.requireNonNull(width(component,extents)));
            }
        }
        return new SourceStorageGeometry(spans,descriptors);
    }
    private static boolean textElement(Ast.DataEntry data) {
        long pictures=data.clauses().stream().filter(Ast.PictureClause.class::isInstance).count();
        return pictures==1&&data.clauses().stream().allMatch(c->c instanceof Ast.PictureClause p&&(p.textExtent().isPresent()||p.edited().isPresent())
            ||c instanceof Ast.UsageClause u&&u.display()||c instanceof Ast.ValueClause||c instanceof Ast.RedefinesClause||c instanceof Ast.OccursClause);
    }
    private static BigInteger width(StorageComponents.Component c,Map<Integer,BigInteger> extents) {
        var max=BigInteger.ZERO;for(int id:c.members()){var n=extents.get(id);if(n==null)return null;max=max.max(n);}return max;
    }
}
