"""Source transforms for an isolated diagnostic overlay, never production files."""


def replace(source, old, new):
    if source.count(old) != 1:
        raise ValueError('production source changed; review phase probe anchor: ' + old)
    return source.replace(old, new)


def flow(source):
    edits = [
        ('    long visits;', '''    long visits;
    private static int probeInstances;
    private final int probeInstance=++probeInstances;
    private final boolean probeNoEscape=Boolean.getBoolean("probe.noescape");
    private final long probeStart=System.nanoTime();
    private long probePhaseStart=probeStart,probeLast=probeStart,probeSuppressed;
    private String probeCurrent="setup";
    private void probeFlow(String event) {
        long now=System.nanoTime();probeLast=now;
        System.err.println("FLOW_PROBE {\\"event\\":\\""+event+"\\",\\"phase\\":\\""+probeCurrent+"\\",\\"instance\\":"+probeInstance+",\\"noescape\\":"+probeNoEscape+",\\"seconds\\":"+((now-probeStart)/1e9)+",\\"phaseSeconds\\":"+((now-probePhaseStart)/1e9)+",\\"visits\\":"+visits+",\\"contexts\\":"+contexts.size()+",\\"beforeStates\\":"+before.size()+",\\"queue\\":"+work.size()+",\\"resultDeliveries\\":"+resultDeliveries+",\\"resolutionVisits\\":"+resolutionVisits+",\\"resolutionKeys\\":"+resolutionInputs.size()+",\\"projections\\":"+projections.size()+",\\"demand\\":"+demand.size()+",\\"specialized\\":"+specialized.size()+",\\"suppressedEscapes\\":"+probeSuppressed+",\\"heapUsed\\":"+(Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory())+"}");
    }
    private void probePhase(String next) {
        probeFlow("phase-end");probeCurrent=next;probePhaseStart=System.nanoTime();probeFlow("phase-start");
    }
    private void probeProgress() {if(System.nanoTime()-probeLast>1000000000L)probeFlow("progress");}'''),
        ('            var flow=new DependencyFlow(unit,declarations,control,queries,maxWork,cics,conditions,cells);passes++;',
         '            System.err.println("FLOW_PASS pass="+(passes+1)+" requestedElements="+cells.size());\n            var flow=new DependencyFlow(unit,declarations,control,queries,maxWork,cics,conditions,cells);passes++;'),
        ('        this.declarations=declarations;this.queries=queries;this.maxWork=maxWork;this.requestedElements=requestedElements;',
         '        probeFlow("phase-start");if(probeNoEscape)System.err.println("DIAGNOSTIC_ABLATION: escape reinvocation disabled; dependencies are not production-qualified");\n        this.declarations=declarations;this.queries=queries;this.maxWork=maxWork;this.requestedElements=requestedElements;'),
        ('        controlSummary=new DependencyControl(', '        probePhase("control");\n        controlSummary=new DependencyControl('),
        ('        for(var q:queries)if(controlSummary.reachable(handle(q.statement())))demand.addAll(declarations.reads(q.expression()));',
         '        probePhase("demand-closure");\n        for(var q:queries)if(controlSummary.reachable(handle(q.statement())))demand.addAll(declarations.reads(q.expression()));'),
        ('        // Keep whole logical text alternatives at joins.', '        probePhase("footprint");\n        // Keep whole logical text alternatives at joins.'),
        ('        var cyclic=controlSummary.cyclicNodes();forwardOrder=controlSummary.forwardOrder();',
         '        probePhase("cycles");\n        var cyclic=controlSummary.cyclicNodes();probePhase("forward-order");forwardOrder=controlSummary.forwardOrder();\n        probePhase("specialization");'),
        ('        relevance=new DependencyRelevance(this::inputEffect,maxWork);',
         '        probePhase("initial-subscription");\n        relevance=new DependencyRelevance(this::inputEffect,maxWork);'),
        ('        for(var root:roots)subscribe(new Entry(root.endpoint(),initial),root.exit());',
         '        for(var root:roots)subscribe(new Entry(root.endpoint(),initial),root.exit());\n        probePhase("values");'),
        ('            var location=work.remove();queued.remove(location);',
         '            if(visits%4096==0)probeProgress();\n            var location=work.remove();queued.remove(location);'),
        ('        var queriesAt=new HashMap<String,List<Query>>();',
         '        probePhase("query-resolution");\n        var queriesAt=new HashMap<String,List<Query>>();'),
        ('        for(var q:queries)if(answers.containsKey(q)&&answers.get(q).unknown())diagnostics.add("DYNAMIC_REMAINDER at "+q.statement().meta().provenance().original().file()+":"+q.statement().meta().provenance().original().startLine());',
         '        for(var q:queries)if(answers.containsKey(q)&&answers.get(q).unknown())diagnostics.add("DYNAMIC_REMAINDER at "+q.statement().meta().provenance().original().file()+":"+q.statement().meta().provenance().original().startLine());\n        probeFlow("phase-end");'),
        ('            return new DependencyControl.Effect(List.of(),List.of(new DependencyControl.Call(\n                new Point(Exit.of(boundaries.get(exit.reference()).ordinaryDefault()),call.entry().endpoint()),binding.id())),List.of(),Set.of());',
         '            if(probeNoEscape){probeSuppressed++;return new DependencyControl.Effect(List.of(),List.of(),List.of(),Set.of());}\n            return new DependencyControl.Effect(List.of(),List.of(new DependencyControl.Call(\n                new Point(Exit.of(boundaries.get(exit.reference()).ordinaryDefault()),call.entry().endpoint()),binding.id())),List.of(),Set.of());'),
        ('            if(resolutionInputs.size()>maxWork)',
         '            if(resolutionInputs.size()%4096==0)probeProgress();\n            if(resolutionInputs.size()>maxWork)'),
        ('            var key=work.removeFirst();queued.remove(key);var value=resolved.get(key);',
         '            if(resolutionVisits%4096==0)probeProgress();\n            var key=work.removeFirst();queued.remove(key);var value=resolved.get(key);'),
    ]
    for old, new in edits:
        source = replace(source, old, new)
    return source


def relevance(source):
    edits = [
        ('    private long work;', '''    private long work;
    private long probeNeeded,probeExtensions,probeEdges,probeScans,probeLast;
    private final long probeStart=System.nanoTime();
    private String probeStage="idle";
    private void probeRelevance(String stage) {
        probeStage=stage;probeLast=System.nanoTime();
        System.err.println("RELEVANCE_PROBE {\\"stage\\":\\""+stage+"\\",\\"seconds\\":"+((probeLast-probeStart)/1e9)+",\\"neededCalls\\":"+probeNeeded+",\\"extensions\\":"+probeExtensions+",\\"nodes\\":"+nodes.size()+",\\"inputs\\":"+inputs.size()+",\\"edges\\":"+probeEdges+",\\"successorScans\\":"+probeScans+",\\"work\\":"+work+",\\"decoded\\":"+decoded.size()+",\\"components\\":"+nextComponent+"}");
    }'''),
        ('    Set<Input> needed(Point entry) {', '    Set<Input> needed(Point entry) {\n        probeNeeded++;'),
        ('        if(++work>maxWork)',
         '        if(work%4096==0&&System.nanoTime()-probeLast>1000000000L)probeRelevance(probeStage);\n        if(++work>maxWork)'),
        ('    private void extend(Point entry) {',
         '    private void extend(Point entry) {\n        probeExtensions++;probeRelevance("discover");'),
        ('            nodes.put(point,node);needs.put(point,new BitSet());',
         '            nodes.put(point,node);probeEdges+=node.successors().size();needs.put(point,new BitSet());'),
        ('        var order=new ArrayList<Point>();var seen=new HashSet<Point>();',
         '        probeRelevance("order");\n        var order=new ArrayList<Point>();var seen=new HashSet<Point>();'),
        ('        for(int i=order.size()-1;i>=0;i--) {',
         '        probeRelevance("components");\n        for(int i=order.size()-1;i>=0;i--) {'),
        ('        propagate(order,point->{\n            var node=nodes.get(point);var possible=',
         '        probeRelevance("overwrites");\n        propagate(order,point->{\n            var node=nodes.get(point);var possible='),
        ('                var writes=overwrites.get(child);possible.or(writes.possible());',
         '                probeScans++;var writes=overwrites.get(child);possible.or(writes.possible());'),
        ('        propagate(order,point->{\n            var node=nodes.get(point);var next=new BitSet();',
         '        probeRelevance("needs");\n        propagate(order,point->{\n            var node=nodes.get(point);var next=new BitSet();'),
        ('            for(var child:node.successors())next.or(needs.get(child));',
         '            for(var child:node.successors()){probeScans++;next.or(needs.get(child));}'),
        ('            needs.put(point,next);decoded.remove(point);return true;\n        });',
         '            needs.put(point,next);decoded.remove(point);return true;\n        });\n        probeRelevance("extension-end");'),
    ]
    for old, new in edits:
        source = replace(source, old, new)
    return source
