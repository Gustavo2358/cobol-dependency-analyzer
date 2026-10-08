package com.imd.cobolexplorer;

import java.util.*;

/** Immutable declaration map. Updates share unchanged subtrees between flow
 * states instead of copying every tracked declaration at every assignment.
 * Integer keys identify AST declarations or logical table elements. */
final class DependencyEnvironment extends AbstractMap<Integer,DependencyValues> {
    private record Node(int key,DependencyValues value,Node left,Node right,int size,int hash) {
        Node(int key,DependencyValues value,Node left,Node right) {
            this(key,value,left,right,1+size(left)+size(right),(key^value.hashCode())+hash(left)+hash(right));
        }
        static int size(Node n){return n==null?0:n.size;}
        static int hash(Node n){return n==null?0:n.hash;}
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
