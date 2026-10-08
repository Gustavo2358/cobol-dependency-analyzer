package com.imd.cobolexplorer;

import java.util.*;
import com.imd.cobolexplorer.semanticproduct.NominalValues;

/** Source DISPLAY coordinates for fixed tables. No physical allocation or index-value proof.
 * A field summarizes its occurrences; every write is weak and every read remains open. */
final class TableTextSemantics {
    private static final int MAX_WIDTH=1_000_000, MAX_OCCURRENCES=8192;
    private record Shape(int width,int count,boolean text) { }
    private record Range(int root,int start,int width) { int end(){return start+width;} }
    private final StorageComponents.Unit unit;
    private final Map<Integer,Ast.DataEntry> data=new HashMap<>();
    private final Map<Integer,StorageComponents.Position> positions=new HashMap<>();
    private final Map<Integer,String> initialTexts=new HashMap<>();
    private final Map<Integer,Shape> shapes=new HashMap<>();
    private final Map<Integer,List<Range>> ranges=new HashMap<>();
    private final Map<Integer,List<Integer>> dimensions=new HashMap<>();
    private final Map<Integer,Integer> references;
    private final Set<Integer> models=new HashSet<>();
    private final Map<Integer,NominalValues.TableField> fields=new TreeMap<>();
    private final Map<Integer,NominalValues.Symbol> symbols=new TreeMap<>();

    TableTextSemantics(StorageComponents.Unit unit,Map<Integer,Integer> references) {
        this.unit=unit;this.references=Map.copyOf(references);
        unit.positions().forEach(p->{data.put(p.data().meta().id(),p.data());positions.put(p.data().meta().id(),p);});
        if(!unit.structureProven()||!unit.rootRelationsProven())return;
        for(var component:unit.rootComponents()) {
            if(component.members().stream().anyMatch(unit.uncertainRoots()::contains))continue;
            for(int root:component.members())shape(root);
            for(int root:component.members())place(root,component.representative(),0,List.of(),false,new int[]{0});
        }
        var assumedRoots=new HashSet<Integer>();for(int node:models)for(var range:ranges.getOrDefault(node,List.of()))assumedRoots.add(range.root());
        ranges.forEach((node,locations)->{if(locations.stream().anyMatch(r->assumedRoots.contains(r.root())))models.add(node);});
        ranges.forEach((node,locations)->{if(models.contains(node)||redefining(node))return;var values=data.get(node).clauses().stream().filter(Ast.ValueClause.class::isInstance).map(Ast.ValueClause.class::cast).toList();if(values.size()==1&&values.get(0).logicalText().isPresent())initialTexts.put(node,fit(values.get(0).logicalText().orElseThrow().value(),shapes.get(node).width()));});
        var repeated=new ArrayList<Range>();
        dimensions.forEach((node,dims)->{var shape=shapes.get(node);if(!dims.isEmpty()&&shape!=null&&shape.text())repeated.addAll(ranges.getOrDefault(node,List.of()));});
        for(var entry:dimensions.entrySet()) {
            int node=entry.getKey();var shape=shapes.get(node);
            if(shape==null||!shape.text()||hasStorageChildren(data.get(node)))continue;
            if(ranges.getOrDefault(node,List.of()).stream().noneMatch(r->repeated.stream().anyMatch(t->r.root()==t.root()&&r.start()<t.end()&&t.start()<r.end())))continue;
            var initial=new LinkedHashSet<NominalValues.Initial>();
            if(!models.contains(node))for(var range:ranges.getOrDefault(node,List.of())) {
                for(var owner:ranges.entrySet()) {
                    int origin=owner.getKey();if(!initialTexts.containsKey(origin))continue;
                    for(var source:owner.getValue())if(source.root()==range.root()&&source.start()<=range.start()&&source.end()>=range.end()) {
                        var text=initialTexts.get(origin);
                        initial.add(new NominalValues.Initial(id(origin),slice(text,range.start()-source.start(),range.end()-source.start())));
                        symbols.put(origin,new NominalValues.Symbol(id(origin),source.width(),false));
                    }
                }
            }
            fields.put(node,new NominalValues.TableField(id(node),List.copyOf(initial)));
            symbols.put(node,new NominalValues.Symbol(id(node),shape.width(),models.contains(node)));
        }
    }
    List<NominalValues.Symbol> symbols(){return List.copyOf(symbols.values());}
    List<NominalValues.TableField> fields(){return List.copyOf(fields.values());}
    Optional<String> read(Ast.DataReference ref) {
        var node=references.get(ref.meta().id());
        return node!=null&&fields.containsKey(node)&&selected(ref,node)!=null?Optional.of(id(node)):Optional.empty();
    }
    List<NominalValueSemantics.Assignment> writes(Ast.MoveStatement move,NominalValues.Term source) {
        var terms=new TreeMap<Integer,List<NominalValues.Term>>();
        for(var operand:move.targets()) {
            if(!(operand instanceof Ast.DataReference receiver))continue;
            var node=references.get(receiver.meta().id());if(node==null)continue;
            var destinations=selected(receiver,node);
            // Refmod/unknown geometry is deliberately not a strong update. All potentially
            // overlapping field summaries stay open and retain their existing possibilities.
            if(destinations==null)continue;
            for(var field:fields.keySet())for(var target:ranges.getOrDefault(field,List.of()))for(var destination:destinations) {
                if(target.root()!=destination.root()||target.start()>=destination.end()||destination.start()>=target.end())continue;
                var value=new NominalValues.Term("UNKNOWN","");
                if(target.start()==destination.start()&&target.width()==destination.width())value=source;
                else if(destination.start()<=target.start()&&destination.end()>=target.end()) {
                    if(source.kind().equals("LITERAL")||source.kind().equals("SPACES")) {
                        var text=fit(source.kind().equals("SPACES")?" ":source.value(),destination.width());
                        value=new NominalValues.Term("LITERAL",slice(text,target.start()-destination.start(),target.end()-destination.start()));
                    }
                }
                terms.computeIfAbsent(field,k->new ArrayList<>()).add(value);
            }
        }
        var result=new ArrayList<NominalValueSemantics.Assignment>();
        terms.forEach((node,values)->{var distinct=values.stream().distinct().toList();result.add(new NominalValueSemantics.Assignment(move.meta().id(),id(node),distinct.size()==1?distinct.get(0):new NominalValues.Term("CHOICE","",distinct)));});
        return List.copyOf(result);
    }
    private List<Range> selected(Ast.DataReference ref,int node) {
        if(ref.understanding()!=Ast.ReferenceUnderstanding.STRUCTURED||ref.referenceModification()!=null)return null;
        var subs=ref.subscriptGroups().stream().flatMap(g->g.subscripts().stream()).toList();
        var dims=dimensions.getOrDefault(node,List.of());if(subs.size()!=dims.size())return null;
        for(int i=0;i<subs.size();i++)if(subs.get(i) instanceof Ast.LiteralExpression literal&&literal.integerValue().isPresent()) {
            var v=literal.integerValue().orElseThrow();if(v.signum()<=0||v.compareTo(java.math.BigInteger.valueOf(dims.get(i)))>0)return null;
        }
        // Index-insensitive within this field, including constant subscripts. This does not
        // identify different occurrences and therefore cannot authorize strong updates.
        return ranges.getOrDefault(node,List.of());
    }
    private Shape shape(int node) {
        if(shapes.containsKey(node))return shapes.get(node);
        var d=data.get(node);if(d==null)return null;
        int count=1;var occurs=d.clauses().stream().filter(Ast.OccursClause.class::isInstance).map(Ast.OccursClause.class::cast).toList();
        if(occurs.size()>1)return null;
        if(!occurs.isEmpty()) {
            var o=occurs.get(0);if(o.dependingOn()!=null||o.maximum()!=null||!(o.minimum() instanceof Ast.LiteralExpression literal)||literal.integerValue().isEmpty())return null;
            try{count=literal.integerValue().orElseThrow().intValueExact();}catch(ArithmeticException invalid){return null;}
            if(count<1||count>MAX_OCCURRENCES)return null;
        }
        if(d.clauses().stream().anyMatch(c->c instanceof Ast.UsageClause u&&!u.display()||c instanceof Ast.PreservedDataClause))return null;
        var pictures=d.clauses().stream().filter(Ast.PictureClause.class::isInstance).map(Ast.PictureClause.class::cast).toList();
        int width=0;boolean text=false;
        if(!hasStorageChildren(d)) {
            if(pictures.size()!=1)return null;var p=pictures.get(0);
            if(p.textExtent().isPresent()){width=p.textExtent().orElseThrow();text=true;}
            else if(p.integerDigits().isPresent()&&!p.picture().toUpperCase(Locale.ROOT).contains("S"))width=p.integerDigits().orElseThrow();
            else return null;
        } else {
            if(!pictures.isEmpty())return null;
            for(var c:unit.children().getOrDefault(node,List.of())) {
                int extent=0;for(int member:storageMembers(c)){var s=shape(member);if(s==null)return null;long size=(long)s.width()*s.count();if(size>MAX_WIDTH)return null;extent=Math.max(extent,(int)size);}
                if((long)width+extent>MAX_WIDTH)return null;width+=extent;
            }
        }
        if(width<=0||(long)width*count>MAX_WIDTH)return null;
        var result=new Shape(width,count,text);shapes.put(node,result);return result;
    }
    private void place(int node,int root,int offset,List<Integer> parents,boolean model,int[] budget) {
        var s=shapes.get(node);if(s==null)return;var d=data.get(node);model|=d.meta().syntheticModel();if(model)models.add(node);
        var dims=new ArrayList<>(parents);if(s.count()>1||d.clauses().stream().anyMatch(Ast.OccursClause.class::isInstance))dims.add(s.count());
        dimensions.put(node,List.copyOf(dims));
        for(int i=0;i<s.count();i++) {
            if(++budget[0]>MAX_OCCURRENCES)return;
            int start=offset+i*s.width();ranges.computeIfAbsent(node,k->new ArrayList<>()).add(new Range(root,start,s.width()));
            int next=start;
            for(var c:unit.children().getOrDefault(node,List.of())) {
                int extent=0;for(int member:storageMembers(c)){var child=shapes.get(member);if(child==null)return;place(member,root,next,dims,model,budget);extent=Math.max(extent,child.width()*child.count());}next+=extent;
            }
        }
    }
    private static boolean hasStorageChildren(Ast.DataEntry data) {
        return data.children().stream().anyMatch(c->c.levelKind()!=Ast.DataLevelKind.CONDITION_88);
    }
    private List<Integer> storageMembers(StorageComponents.Component component) {
        return component.members().stream().filter(n->data.get(n).levelKind()!=Ast.DataLevelKind.CONDITION_88).toList();
    }
    private boolean redefining(int node) {
        var current=positions.get(node);
        while(true){if(current.data().clauses().stream().anyMatch(Ast.RedefinesClause.class::isInstance))return true;if(current.parent().isEmpty())return false;int parent=current.parent().orElseThrow();current=positions.get(parent);}
    }
    private static String fit(String s,int n){int size=s.codePointCount(0,s.length());return size>=n?slice(s,0,n):s+" ".repeat(n-size);}
    private static String slice(String s,int start,int end){return s.substring(s.offsetByCodePoints(0,start),s.offsetByCodePoints(0,end));}
    private static String id(int node){return "storage-node:"+node;}
}
