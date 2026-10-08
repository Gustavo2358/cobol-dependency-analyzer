package com.imd.cobolexplorer;

import java.util.*;
import java.lang.ref.WeakReference;

/** Immutable declaration map. Updates share unchanged subtrees between flow
 * states instead of copying every tracked declaration at every assignment.
 * Integer keys identify AST declarations or logical table elements. */
final class DependencyEnvironment extends AbstractMap<Integer,DependencyValues> {
    // Identity equality lets projection caches recognize shared subtrees without
    // recursively hashing them. Map equality still compares keys and values.
    private static final class Node {
        final int key,size,hash;
        final DependencyValues value;
        final Node left,right;
        Node(int key,DependencyValues value,Node left,Node right) {
            this.key=key;this.value=value;this.left=left;this.right=right;
            size=1+size(left)+size(right);hash=(key^value.hashCode())+hash(left)+hash(right);
        }
        static int size(Node n){return n==null?0:n.size;}
        static int hash(Node n){return n==null?0:n.hash;}
    }
    /** A reusable selection preserves shared branches across input states.
     * Both cache sides are weak: even an unchanged result pointing to its input
     * must not keep that input alive. Cache eviction only repeats projection. */
    static final class Projection {
        private static final Node EMPTY=new Node(0,DependencyValues.UNKNOWN,null,null);
        private final Set<Integer> declarations;
        private final NavigableSet<Integer> ordered;
        private final Map<Node,WeakReference<Node>> projected=new WeakHashMap<>();
        Projection(Set<Integer> declarations){this.declarations=Set.copyOf(declarations);ordered=new TreeSet<>(declarations);}
        DependencyEnvironment apply(Map<Integer,DependencyValues> values) {
            var source=copyOf(values);var root=retain(source.root,Integer.MIN_VALUE,(long)Integer.MAX_VALUE+1);
            return root==source.root?source:new DependencyEnvironment(root);
        }
        private Node retain(Node node,int lower,long upper) {
            if(node==null)return null;
            // A sparse operand selection must neither visit nor cache branches
            // outside its key ranges. This also applies to summary projections.
            var selected=ordered.ceiling(lower);if(selected==null||selected>=upper)return null;
            var cached=projected.get(node);var result=cached==null?null:cached.get();
            if(result!=null)return result==EMPTY?null:result;
            var left=retain(node.left,lower,node.key);var right=retain(node.right,node.key+1,upper);
            result=declarations.contains(node.key)
                    ?left==node.left&&right==node.right?node:new Node(node.key,node.value,left,right)
                    :merge(left,right);
            projected.put(node,new WeakReference<>(result==null?EMPTY:result));
            return result;
        }
    }
    private final Node root;
    private DependencyEnvironment(Node root){this.root=root;}
    static DependencyEnvironment copyOf(Map<Integer,DependencyValues> map) {
        if(map instanceof DependencyEnvironment e)return e;
        if(map instanceof Builder b)return b.environment;
        var out=new DependencyEnvironment(null);
        for(var e:map.entrySet())out=out.with(e.getKey(),e.getValue());return out;
    }
    @Override public DependencyValues get(Object key) {
        if(!(key instanceof Integer id))return null;
        Node node=root;while(node!=null){if(id==node.key)return node.value;node=id<node.key?node.left:node.right;}return null;
    }
    @Override public boolean containsKey(Object key){return get(key)!=null;}
    @Override public int size(){return Node.size(root);}
    @Override public int hashCode(){return Node.hash(root);}
    @Override public boolean equals(Object object) {
        if(object==this)return true;
        if(object instanceof DependencyEnvironment e)return size()==e.size()&&hashCode()==e.hashCode()&&same(root,e.root);
        return super.equals(object);
    }
    private static boolean same(Node a,Node b) {
        return a==b||a!=null&&b!=null&&a.key==b.key&&a.value.equals(b.value)&&same(a.left,b.left)&&same(a.right,b.right);
    }
    private static int priority(int key) {
        int x=key;x^=x>>>16;x*=0x7feb352d;x^=x>>>15;x*=0x846ca68b;x^=x>>>16;return x;
    }
    private static boolean above(int a,int b) {
        int c=Integer.compareUnsigned(priority(a),priority(b));return c<0||c==0&&a<b;
    }
    DependencyEnvironment with(int key,DependencyValues value){return new DependencyEnvironment(insert(root,key,Objects.requireNonNull(value)));}
    DependencyEnvironment without(int key){return new DependencyEnvironment(remove(root,key));}
    DependencyEnvironment join(Map<Integer,DependencyValues> other) {
        var joined=join(root,copyOf(other).root);
        return joined==root?this:new DependencyEnvironment(joined);
    }
    private record Split(Node left,DependencyValues value,Node right) { }
    private static Split split(Node node,int key) {
        if(node==null)return new Split(null,null,null);
        if(node.key==key)return new Split(node.left,node.value,node.right);
        if(key<node.key) {
            var part=split(node.left,key);
            return new Split(part.left,part.value,rebuild(node,part.right,node.right,node.value));
        }
        var part=split(node.right,key);
        return new Split(rebuild(node,node.left,part.left,node.value),part.value,part.right);
    }
    private static Node rebuild(Node node,Node left,Node right,DependencyValues value) {
        return left==node.left&&right==node.right&&value.equals(node.value)?node:new Node(node.key,value,left,right);
    }
    /** Shared immutable branches already have their fixed join. Only differing
     * branches need evaluation; split handles independently projected key sets. */
    private static Node join(Node a,Node b) {
        if(a==b||b==null)return a;
        if(a==null)return b;
        if(a.key==b.key)return rebuild(a,join(a.left,b.left),join(a.right,b.right),a.value.join(b.value));
        if(above(a.key,b.key)) {
            var part=split(b,a.key);
            return rebuild(a,join(a.left,part.left),join(a.right,part.right),part.value==null?a.value:a.value.join(part.value));
        }
        var part=split(a,b.key);
        return rebuild(b,join(part.left,b.left),join(part.right,b.right),part.value==null?b.value:part.value.join(b.value));
    }
    private static Node insert(Node n,int key,DependencyValues value) {
        if(n==null)return new Node(key,value,null,null);
        if(n.key==key)return n.value.equals(value)?n:new Node(key,value,n.left,n.right);
        if(key<n.key) {
            Node child=insert(n.left,key,value);if(child==n.left)return n;
            if(above(child.key,n.key))return new Node(child.key,child.value,child.left,new Node(n.key,n.value,child.right,n.right));
            return new Node(n.key,n.value,child,n.right);
        }
        Node child=insert(n.right,key,value);if(child==n.right)return n;
        if(above(child.key,n.key))return new Node(child.key,child.value,new Node(n.key,n.value,n.left,child.left),child.right);
        return new Node(n.key,n.value,n.left,child);
    }
    private static Node remove(Node n,int key) {
        if(n==null)return null;
        if(n.key==key)return merge(n.left,n.right);
        if(key<n.key){Node child=remove(n.left,key);return child==n.left?n:new Node(n.key,n.value,child,n.right);}
        Node child=remove(n.right,key);return child==n.right?n:new Node(n.key,n.value,n.left,child);
    }
    private static Node merge(Node a,Node b) {
        if(a==null)return b;if(b==null)return a;
        return above(a.key,b.key)?new Node(a.key,a.value,a.left,merge(a.right,b)):new Node(b.key,b.value,merge(a,b.left),b.right);
    }
    @Override public Set<Entry<Integer,DependencyValues>> entrySet() {
        return new AbstractSet<>() {
            @Override public int size(){return DependencyEnvironment.this.size();}
            @Override public Iterator<Entry<Integer,DependencyValues>> iterator() {
                return new Iterator<>() {
                    final ArrayDeque<Node> pending=new ArrayDeque<>();{push(root);}
                    private void push(Node n){while(n!=null){pending.push(n);n=n.left;}}
                    public boolean hasNext(){return !pending.isEmpty();}
                    public Entry<Integer,DependencyValues> next(){if(pending.isEmpty())throw new NoSuchElementException();Node n=pending.pop();push(n.right);return Map.entry(n.key,n.value);}
                };
            }
        };
    }
    /** Local mutable facade; freezing retains an immutable tree snapshot. */
    static final class Builder extends AbstractMap<Integer,DependencyValues> {
        private DependencyEnvironment environment;
        Builder(Map<Integer,DependencyValues> base){environment=copyOf(base);}
        @Override public DependencyValues get(Object key){return environment.get(key);}
        @Override public DependencyValues put(Integer key,DependencyValues value){var old=get(key);environment=environment.with(key,value);return old;}
        @Override public DependencyValues remove(Object key){var old=get(key);if(key instanceof Integer id)environment=environment.without(id);return old;}
        @Override public Set<Entry<Integer,DependencyValues>> entrySet(){return environment.entrySet();}
    }
}
