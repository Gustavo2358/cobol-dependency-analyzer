"""Lifecycle interventions and cycle probes for the isolated diagnostic overlay."""


def replace(source, old, new):
    if source.count(old) != 1:
        raise ValueError('production source changed; review wall probe anchor: ' + old)
    return source.replace(old, new)


def control(source):
    source = replace(source, '    long visits;', '''    long visits;
    private final boolean probeCompactResults=Boolean.getBoolean("probe.compactControlResults");
    private final List<Exit> probeExitValues=new ArrayList<>();
    private final Map<Exit,Integer> probeExitIds=new HashMap<>();
    private Set<Exit> probeResultSet() {return probeCompactResults?new ProbeExitSet():new HashSet<>();}
    private final class ProbeExitSet extends AbstractSet<Exit> {
        private final BitSet bits=new BitSet();
        public int size(){return bits.cardinality();}
        public boolean contains(Object value){Integer id=probeExitIds.get(value);return id!=null&&bits.get(id);}
        public boolean add(Exit value){
            Integer id=probeExitIds.get(value);
            if(id==null){id=probeExitValues.size();probeExitValues.add(value);probeExitIds.put(value,id);}
            boolean changed=!bits.get(id);bits.set(id);return changed;
        }
        public Iterator<Exit> iterator(){return new Iterator<>() {
            private int next=bits.nextSetBit(0);
            public boolean hasNext(){return next>=0;}
            public Exit next(){if(next<0)throw new NoSuchElementException();Exit value=probeExitValues.get(next);next=bits.nextSetBit(next+1);return value;}
        };}
    }
    private long probeCycleStart,probeCycleEdges;
    private void probeCycle(String stage) {
        System.err.println("CYCLE_PROBE {\\"stage\\":\\""+stage+"\\",\\"seconds\\":"+((System.nanoTime()-probeCycleStart)/1e9)+",\\"points\\":"+reachable.size()+",\\"reverseEdges\\":"+probeCycleEdges+",\\"resultKeys\\":"+results.size()+",\\"heapUsed\\":"+(Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory())+"}");
    }''')
    source = replace(source,
        '        reachable.forEach(p->reachableNodes.add(p.exit().reference()));observed.forEach(p->observedNodes.add(p.exit().reference()));',
        '''        reachable.forEach(p->reachableNodes.add(p.exit().reference()));observed.forEach(p->observedNodes.add(p.exit().reference()));
        probe("construction-end");
        System.err.println("CONTROL_STORAGE {\\"kind\\":\\""+(probeCompactResults?"bitset":"hashset")+"\\",\\"exitUniverse\\":"+probeExitValues.size()+",\\"historicalResultEntries\\":"+exitsAdded+"}");
        if(Boolean.getBoolean("probe.releaseControlResults")) {
            int count=results.size();results.replaceAll((point,exits)->Set.of());
            System.err.println("CONTROL_RELEASE resultSets="+count+" historicalResultEntries="+exitsAdded+" placeholderKeys="+results.size());
        }''')
    source = replace(source, '        plans.put(point,new Plan());results.put(point,new HashSet<>());schedule(point);',
        '        plans.put(point,new Plan());results.put(point,probeResultSet());schedule(point);')
    source = replace(source, '                    plans.put(child,new Plan());results.put(child,new HashSet<>());schedule(child);pending.add(child);',
        '                    plans.put(child,new Plan());results.put(child,probeResultSet());schedule(child);pending.add(child);')
    source = replace(source, '    Set<String> cyclicNodes() {',
        '    Set<String> cyclicNodes() {\n        probeCycleStart=System.nanoTime();probeCycle("postorder-start");')
    source = replace(source, '        var order=postorder();var seen=new HashSet<Point>();',
        '        var order=postorder();probeCycle("postorder-end");var seen=new HashSet<Point>();')
    source = replace(source,
        '        for(var point:reachable)for(var child:successors(point))reverse.computeIfAbsent(child,k->new HashSet<>()).add(point);',
        '        probeCycle("reverse-start");\n        for(var point:reachable)for(var child:successors(point)){probeCycleEdges++;reverse.computeIfAbsent(child,k->new HashSet<>()).add(point);}\n        probeCycle("reverse-end");')
    source = replace(source,
        '        // A repeated call can mutate its arguments on each iteration even when',
        '        probeCycle("components-end");\n        // A repeated call can mutate its arguments on each iteration even when')
    source = replace(source,
        '        var names=new HashSet<String>();cyclic.forEach(p->names.add(p.exit().reference()));return Set.copyOf(names);',
        '        probeCycle("closure-end");\n        var names=new HashSet<String>();cyclic.forEach(p->names.add(p.exit().reference()));return Set.copyOf(names);')
    return source
