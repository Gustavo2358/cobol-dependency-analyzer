#!/usr/bin/env python3
"""Isolated precision-budget experiments. None of these overlays is qualified.

The control quotient unions stop and continue at folded boundaries. Runtime
PERFORM endpoints remain exact. Context merging uses the existing join and may
pollute callers; call-depth truncation deliberately drops exploration.
"""
import argparse, hashlib, importlib.util, json, os, subprocess
from pathlib import Path
ROOT = Path(__file__).resolve().parents[2]

def module(path, name):
    spec = importlib.util.spec_from_file_location(name, path)
    result = importlib.util.module_from_spec(spec); spec.loader.exec_module(result)
    return result

def replace(source, old, new):
    assert source.count(old) == 1, old
    return source.replace(old, new)

def overlays(ref):
    def read(name):
        return subprocess.check_output(['git','-C',str(ROOT),'show',f'{ref}:src/main/java/com/imd/cobolexplorer/{name}.java'],text=True)
    original = {name:read(name) for name in ['DependencyFlow','DependencyControl','DependencyGraph','DependencyRelevance']}
    sources = dict(original)
    flow = sources['DependencyFlow']
    flow = replace(flow, '    final Map<SummaryKey,Integer> memo=new HashMap<>();', '''    final Map<SummaryKey,Integer> memo=new HashMap<>();
    private final int probeContextLimit=Integer.getInteger("probe.contextLimit",-1),probeDepthLimit=Integer.getInteger("probe.callDepth",-1);
    private final Map<List<Object>,Integer> probeFamilyCounts=new HashMap<>(),probeMergedContexts=new HashMap<>();
    private final Map<Integer,Integer> probeDepths=new HashMap<>();
    private long probeMergedInputs,probeDepthDrops;
''')
    flow = replace(flow, '        String endpoint=caller.endpoint();State state=caller.input();', '''        int probeDepth=caller.context()<0?0:probeDepths.getOrDefault(caller.context(),0)+(caller instanceof ReturnTo?1:0);
        if(probeDepthLimit>=0&&probeDepth>probeDepthLimit){probeDepthDrops++;return;}
        String endpoint=caller.endpoint();State state=caller.input();''')
    flow = replace(flow, '''        Integer context=memo.get(key);
        if(context==null) {
            context=contexts.size();memo.put(key,context);contexts.add(new Context(key));route(context,entry,key.input());
        }''', '''        Integer context=memo.get(key);
        var probeFamily=List.<Object>of(entry,endpoint,scope);
        if(context==null&&probeContextLimit>=0&&probeFamilyCounts.getOrDefault(probeFamily,0)>=probeContextLimit) {
            context=probeMergedContexts.get(probeFamily);
            if(context==null){context=contexts.size();contexts.add(new Context(key));probeMergedContexts.put(probeFamily,context);}
            probeDepths.merge(context,probeDepth,Math::min);probeMergedInputs++;route(context,entry,key.input());
        }
        if(context==null) {
            context=contexts.size();memo.put(key,context);contexts.add(new Context(key));probeFamilyCounts.merge(probeFamily,1,Integer::sum);
            probeDepths.put(context,probeDepth);route(context,entry,key.input());
        }
        probeDepths.merge(context,probeDepth,Math::min);''')
    flow = replace(flow, '        var queriesAt=new HashMap<String,List<Query>>();', '''        System.err.printf("PRECISION_PROBE {\\"mergedInputs\\":%d,\\"depthDrops\\":%d,\\"before\\":%d,\\"contexts\\":%d}%n",probeMergedInputs,probeDepthDrops,before.size(),contexts.size());
        System.err.printf("CONTROL_PLANS {\\"plans\\":%d,\\"computations\\":%d,\\"projections\\":%d}%n",continuationPlans.size(),computations.size(),projections.size());
        var queriesAt=new HashMap<String,List<Query>>();''')
    flow = replace(flow, '        for(var q:queries)if(answers.containsKey(q)&&answers.get(q).unknown())diagnostics.add(',
        '        if(Boolean.getBoolean("probe.liveHeap")){System.gc();System.err.println("LIVE_HEAP "+(Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory()));}\n        for(var q:queries)if(answers.containsKey(q)&&answers.get(q).unknown())diagnostics.add(')
    flow = replace(flow, '            var location=work.remove().location();', '''            if(Boolean.getBoolean("probe.progress")&&(visits&65535)==0)System.err.printf("FLOW_PROGRESS {\\"visits\\":%d,\\"before\\":%d,\\"contexts\\":%d,\\"queued\\":%d,\\"heapUsed\\":%d}%n",visits,before.size(),contexts.size(),work.size(),Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory());
            var location=work.remove().location();''')
    # A folded control cell has no unique callee endpoint. The binding retains
    # the original endpoint; using it is equal on unmodified control cells too.
    flow = replace(flow, '("boundary:"+exit.reference()).equals(call.entry().endpoint())', '("boundary:"+exit.reference()).equals(binding.endpoint())')
    flow = replace(flow, 'scope->scope.endpoint().equals("boundary:"+exit.reference())&&regions.get(exit.reference()).kind()==RegionKind.DECLARATIVE', 'scope->(scope.endpoint().equals("*")||scope.endpoint().equals("boundary:"+exit.reference()))&&regions.get(exit.reference()).kind()==RegionKind.DECLARATIVE')
    sources['DependencyFlow'] = flow
    graph = sources['DependencyGraph']
    graph = replace(graph, '    private int cells,closedSize;private boolean closed;', '''    private int cells,closedSize;private boolean closed;
    private final int probeScopeLimit=Integer.getInteger("probe.scopeLimit",-1);
    private final BitSet probeMayStop=new BitSet();
    void probeMayStop(int cell){probeMayStop.set(cell);}
    boolean probeMayStopAt(int cell){return probeMayStop.get(cell);}
    private final Map<Scope,Scope> probeScopeAliases=new HashMap<>();
    private final Set<Scope> probeExactScopes=new HashSet<>();
    private Scope scopeKey(Point point) {
        var requested=new Scope(point.endpoint(),point.escapeScope());
        if(probeScopeLimit<0||requested.endpoint().equals("*"))return requested;
        return probeScopeAliases.computeIfAbsent(requested,key->{
            if(probeExactScopes.size()<probeScopeLimit){probeExactScopes.add(key);return key;}
            return new Scope("*","*");
        });
    }''')
    graph = graph.replace('new Scope(point.endpoint(),point.escapeScope())','scopeKey(point)')
    # Restore the requested key inside scopeKey itself (not recursive).
    graph = replace(graph, 'var requested=scopeKey(point);','var requested=new Scope(point.endpoint(),point.escapeScope());')
    sources['DependencyGraph'] = graph
    control = sources['DependencyControl']
    control = replace(control,'construction.solve(roots);',
        'if(Boolean.getBoolean("probe.physicalOnly"))construction.physical(roots);else construction.solve(roots);')
    control = replace(control, '        void solve(List<Point> roots) {', '''        // Diagnostic conservative ICFG: enter every callee and admit both
        // ordinary completion and same-endpoint escape resumption. Runtime
        // flow keeps genuine invocation boundaries and terminal returns.
        void physical(List<Point> roots) {
            if(Integer.getInteger("probe.scopeLimit",-1)!=0)throw new IllegalArgumentException("physicalOnly needs scopeLimit=0");
            var pending=new ArrayDeque<Exit>();var seen=new HashSet<Exit>();
            roots.forEach(p->pending.add(p.exit()));
            while(!pending.isEmpty()) {
                var exit=pending.removeFirst();if(!seen.add(exit))continue;tick();
                int cell=graph.id(new Point(exit,"*","*")),position=graph.position(cell);var local=effects.apply(exit);
                if(!local.returns().apply(new Scope("*","*")).isEmpty())graph.probeMayStop(cell);
                physicalEffect(position,local.continuation(),pending);
            }
        }
        private void physicalEffect(int from,Effect effect,ArrayDeque<Exit> pending) {
            for(var next:effect.next())physicalEdge(from,new Point(next,"*","*"),NEXT,"",pending);
            if(!effect.exits().isEmpty())graph.probeMayStop(graph.id(new Point(graph.positionExit(from),"*","*")));
            for(var entry:effect.entries())physicalEdge(from,entry,ENTRY,"",pending);
            for(var call:effect.calls()) {
                physicalEdge(from,call.entry(),CALL,call.binding(),pending);
                String region=call.entry().endpoint().substring("boundary:".length());
                physicalEffect(from,delivery.apply(call,new Exit(com.imd.cobolexplorer.semanticproduct.ControlTopology.TargetKind.COMPLETE,region)),pending);
                physicalEffect(from,delivery.apply(call,new Exit(com.imd.cobolexplorer.semanticproduct.ControlTopology.TargetKind.ESCAPE,region)),pending);
            }
        }
        private void physicalEdge(int from,Point point,byte kind,String binding,ArrayDeque<Exit> pending) {
            int child=graph.id(new Point(point.exit(),"*","*"));
            graph.add(from,graph.position(child),kind,kind==NEXT?-1:child,binding);pending.add(point.exit());
        }
        void solve(List<Point> roots) {''')
    control = replace(control, 's->stop.test(s)?Set.of(exit):Set.of()', 's->s.endpoint().equals("*")||stop.test(s)?Set.of(exit):Set.of()')
    control = replace(control, '''            if(!stops.isEmpty()){graph.stop(id);for(var exit:stops)publish(physical,scope,exitId(exit));}
            else {''', '''            boolean folded=graph.scope(scope).endpoint().equals("*");
            if(!stops.isEmpty()){if(!folded)graph.stop(id);else graph.probeMayStop(id);for(var exit:stops)publish(physical,scope,exitId(exit));}
            if(stops.isEmpty()||folded) {''')
    sources['DependencyControl'] = control
    relevance = sources['DependencyRelevance']
    relevance = replace(relevance,'BitSet guaranteed=null;',
        'BitSet guaranteed=Boolean.getBoolean("probe.conservativeStops")&&graph.probeMayStopAt(point)?new BitSet():null;')
    sources['DependencyRelevance'] = relevance
    return original, sources

def build(output, jar, ref):
    output.mkdir(parents=True,exist_ok=False); original,sources=overlays(ref)
    directory=output/'source/com/imd/cobolexplorer';directory.mkdir(parents=True)
    for name,content in sources.items():(directory/(name+'.java')).write_text(content)
    classes=output/'classes';classes.mkdir();java=Path(os.environ['JAVA_HOME'])/'bin'
    command=[str(java/'javac'),'-J-Xmx128m','--release','17','-cp',str(jar),'-d',str(classes),*[str(directory/(n+'.java')) for n in sources]]
    c=subprocess.run(command,capture_output=True,text=True);(output/'compile.log').write_text(c.stdout+c.stderr);c.check_returncode()
    manifest={'sourceRef':ref,'jarSha256':hashlib.sha256(jar.read_bytes()).hexdigest(),'command':command,'qualifiedForProduction':False,'original':{n:hashlib.sha256(s.encode()).hexdigest() for n,s in original.items()},'overlay':{n:hashlib.sha256(s.encode()).hexdigest() for n,s in sources.items()}}
    (output/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')

def run(classes,jar,source,dest,properties,timeout=45):
    java=Path(os.environ['JAVA_HOME'])/'bin/java'
    command=[str(java),'-Xms16m','-Xmx512m','-XX:MaxMetaspaceSize=128m','-XX:MaxDirectMemorySize=32m','-XX:+ExitOnOutOfMemoryError']
    command += ['-Dprobe.'+k+'='+str(v) for k,v in properties.items()]
    if properties.get('jfr'):
        command += ['-XX:StartFlightRecording=settings=profile,maxsize=16m,dumponexit=true,filename='+str(dest/'profile.jfr')]
    command += ['-cp',str(classes)+os.pathsep+str(jar),'com.imd.cobolexplorer.DependencyMain'] if classes else ['-jar',str(jar)]
    command += ['--source',str(source),'--output',str(dest/'dependencies.json'),'--metrics',str(dest/'metrics.jsonl'),'--max-work','1000000000']
    runner=module(ROOT/'benchmark/run-sparse-occurs.py','runner')
    result=runner.execute(command,dest,timeout=timeout,rss_mib=768,telemetry_period=1,min_available_mib=2048)
    output=dest/'dependencies.json';result['document']=json.loads(output.read_text()) if output.exists() else None
    result['diagnostics']=[line for line in (dest/'stderr.log').read_text().splitlines() if ': ' in line and not line.startswith('PRECISION_PROBE ')]
    result['probe']=[json.loads(line.split(' ',1)[1]) for line in (dest/'stderr.log').read_text().splitlines() if line.startswith('PRECISION_PROBE ')]
    result['plans']=[json.loads(line.split(' ',1)[1]) for line in (dest/'stderr.log').read_text().splitlines() if line.startswith('CONTROL_PLANS ')]
    result['liveHeapBytes']=[int(line.split(' ',1)[1]) for line in (dest/'stderr.log').read_text().splitlines() if line.startswith('LIVE_HEAP ')]
    result['progress']=[json.loads(line.split(' ',1)[1]) for line in (dest/'stderr.log').read_text().splitlines() if line.startswith('FLOW_PROGRESS ')]
    metrics=dest/'metrics.jsonl';result['metrics']=[json.loads(l)['metrics'] for l in metrics.read_text().splitlines() if '"metrics"' in l] if metrics.exists() else []
    result.update(sourceSha256=hashlib.sha256(source.read_bytes()).hexdigest(),properties=properties,qualifiedForProduction=False)
    return result

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('output',type=Path);p.add_argument('--jar',required=True,type=Path);p.add_argument('--ref',default='HEAD');a=p.parse_args();build(a.output.resolve(),a.jar.resolve(),a.ref)
if __name__=='__main__':main()
