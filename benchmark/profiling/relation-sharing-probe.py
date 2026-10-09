#!/usr/bin/env python3
"""Read-only census of context maps and position/obligation rows. No solver changes."""
import argparse, hashlib, importlib.util, json, os, subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
def module(path,name):
    spec=importlib.util.spec_from_file_location(name,path);m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m);return m
HOOK=r'''
    private void relationDumpProbe() {
        var directory=java.nio.file.Path.of(System.getProperty("probe.relations"),"visit-"+visits);
        try {
            java.nio.file.Files.createDirectories(directory);
            var hashes=new HashMap<Integer,Integer>();for(var location:before.keySet())hashes.merge(location.hashCode(),1,Integer::sum);
            long colliding=hashes.values().stream().filter(n->n>1).mapToLong(Integer::longValue).sum();
            System.err.printf("LOCATION_HASH {\"visit\":%d,\"keys\":%d,\"distinctFullHashes\":%d,\"keysSharingFullHash\":%d,\"maxSameHash\":%d}%n",visits,before.size(),hashes.size(),colliding,hashes.values().stream().mapToInt(Integer::intValue).max().orElse(0));
            var nodes=new HashMap<String,Integer>();var values=new HashMap<State,Integer>();
            int[] sizes=new int[contexts.size()];for(var location:before.keySet())sizes[location.context()]++;
            long[][] rows=new long[sizes.length][];for(int i=0;i<sizes.length;i++)rows[i]=new long[sizes[i]];
            Arrays.fill(sizes,0);
            for(var entry:before.entrySet()) {
                var location=entry.getKey();int node=nodes.computeIfAbsent(location.node(),k->nodes.size());
                int value=values.computeIfAbsent(entry.getValue(),k->values.size()+1);
                rows[location.context()][sizes[location.context()]++]=((long)node<<32)|(value&0xffffffffL);
            }
            relationDump(directory.resolve("before.bin"),rows,nodes.size(),values.size());
            rows=null;
            var graph=controlSummary.graph;sizes=new int[graph.scopeCount()];for(int i=0;i<graph.size();i++)sizes[graph.obligation(i)]++;
            rows=new long[sizes.length][];for(int i=0;i<sizes.length;i++)rows[i]=new long[sizes[i]];Arrays.fill(sizes,0);
            var columns=new ArrayList<BitSet[]>();var facts=new HashMap<BitSet,Integer>();var tuples=new HashMap<List<Integer>,Integer>();
            for(String name:List.of("reads","kills","needs","possible","definite")) {
                var field=DependencyRelevance.class.getDeclaredField(name);field.setAccessible(true);columns.add((BitSet[])field.get(relevance));
            }
            for(int i=0;i<graph.size();i++) {
                var tuple=new ArrayList<Integer>(6);tuple.add(graph.stopped(i)?1:0);
                for(var column:columns){var fact=i<column.length?column[i]:null;tuple.add(fact==null?0:facts.computeIfAbsent(fact,k->facts.size()+1));}
                int value=tuples.computeIfAbsent(List.copyOf(tuple),k->tuples.size()+1),scope=graph.obligation(i);
                rows[scope][sizes[scope]++]=((long)graph.position(i)<<32)|(value&0xffffffffL);
            }
            relationDump(directory.resolve("control.bin"),rows,graph.physicalSize(),tuples.size());
            try(var out=java.nio.file.Files.newBufferedWriter(directory.resolve("tuples.txt"))) {
                for(var entry:tuples.entrySet())out.write(entry.getValue()+" "+entry.getKey()+"\n");
            }
            graph.probeFind("visit-"+visits);
            System.err.printf("RELATION_CENSUS {\"visit\":%d,\"before\":%d,\"contexts\":%d,\"beforeNodes\":%d,\"states\":%d,\"controlCells\":%d,\"positions\":%d,\"scopes\":%d,\"factContents\":%d,\"factTuples\":%d}%n",visits,before.size(),contexts.size(),nodes.size(),values.size(),graph.size(),graph.physicalSize(),graph.scopeCount(),facts.size(),tuples.size());
        }catch(Exception e){throw new IllegalStateException(e);}
    }
    private void relationProbe() {
        relationDumpProbe();
        if(!Boolean.getBoolean("probe.liveHistogram")||!work.isEmpty())return;
        try {
            var server=java.lang.management.ManagementFactory.getPlatformMBeanServer();
            var diagnostic=new javax.management.ObjectName("com.sun.management:type=DiagnosticCommand");
            var content=(String)server.invoke(diagnostic,"gcClassHistogram",new Object[]{new String[0]},new String[]{"[Ljava.lang.String;"});
            java.nio.file.Files.writeString(java.nio.file.Path.of(System.getProperty("probe.relations"),"histogram.txt"),content);
        }catch(Exception e){throw new IllegalStateException(e);}
    }
    private static void relationDump(java.nio.file.Path path,long[][] rows,int keys,int values)throws java.io.IOException {
        try(var out=new java.io.DataOutputStream(new java.io.BufferedOutputStream(java.nio.file.Files.newOutputStream(path)))) {
            out.writeInt(rows.length);out.writeInt(keys);out.writeInt(values);
            for(var row:rows){Arrays.sort(row);out.writeInt(row.length);for(long item:row)out.writeLong(item);}
        }
    }
'''
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('output',type=Path);p.add_argument('--jar',required=True,type=Path);p.add_argument('--source-ref',default='HEAD');p.add_argument('--boxes',nargs='+',type=int,default=[32,64,128]);p.add_argument('--histogram',action='store_true');p.add_argument('--mix-location-hash',action='store_true',help='Diagnostic exact-equality-preserving hash alternative');p.add_argument('--variant',choices=('original','prewrite-call'),default='original');p.add_argument('--visits',nargs='*',type=int,default=[]);p.add_argument('--mode',nargs='+',choices=('return-fanout','return-fanout-fixed'),default=['return-fanout']);a=p.parse_args()
    a.output=a.output.resolve();a.output.mkdir(parents=True,exist_ok=False);a.jar=a.jar.resolve();path='src/main/java/com/imd/cobolexplorer/DependencyFlow.java'
    source=subprocess.run(['git','-C',str(ROOT),'show',a.source_ref+':'+path],capture_output=True,text=True,check=True).stdout
    original=hashlib.sha256(source.encode()).hexdigest();anchor='        var queriesAt=new HashMap<String,List<Query>>();';assert source.count(anchor)==1
    if a.mix_location_hash:
        record='        Location(int context,String node){this(context,node,null,null);}'
        assert source.count(record)==1
        source=source.replace(record,record+'''
        private static int hashMix(int x){x^=x>>>16;x*=0x7feb352d;x^=x>>>15;x*=0x846ca68b;return x^(x>>>16);}
        @Override public int hashCode(){int h=hashMix(context)^Integer.rotateLeft(hashMix(Objects.hashCode(node)),11);h=hashMix(h)^hashMix(Objects.hashCode(result));return hashMix(h)^hashMix(Objects.hashCode(caller));}
''')
    source=source.replace('            var location=work.remove().location();','            if('+(' || '.join('visits=='+str(v) for v in a.visits) or 'false')+')relationProbe();\n            var location=work.remove().location();').replace(anchor,'        relationProbe();\n'+anchor).replace('    static String handle(Ast.Statement s)',HOOK+'\n    static String handle(Ast.Statement s)')
    src=a.output/'source/com/imd/cobolexplorer/DependencyFlow.java';src.parent.mkdir(parents=True);src.write_text(source);classes=a.output/'classes';classes.mkdir();java=Path(os.environ['JAVA_HOME'])/'bin'
    graphpath='src/main/java/com/imd/cobolexplorer/DependencyGraph.java'
    graphsource=subprocess.run(['git','-C',str(ROOT),'show',a.source_ref+':'+graphpath],capture_output=True,text=True,check=True).stdout
    graphdigest=hashlib.sha256(graphsource.encode()).hexdigest()
    start='        int slot=mix(position,scope)&(cellSlots.length-1);'
    graphsource=graphsource.replace(start,'        findCalls++;long beforeProbes=findProbes;'+start.strip())
    graphsource=graphsource.replace('while(cellSlots[slot]!=0){int id=cellSlots[slot]-1;if(cellPosition[id]==position&&cellScope[id]==scope)return id;slot=(slot+1)&(cellSlots.length-1);}return -1;',
        'while(cellSlots[slot]!=0){findProbes++;int id=cellSlots[slot]-1;if(cellPosition[id]==position&&cellScope[id]==scope){findMax=Math.max(findMax,findProbes-beforeProbes);return id;}slot=(slot+1)&(cellSlots.length-1);}findProbes++;findMax=Math.max(findMax,findProbes-beforeProbes);return -1;')
    graphsource=graphsource.replace('    void close() {','    private long findCalls,findProbes,findMax;\n    void probeFind(String stage){System.err.printf("FIND_CENSUS {\\\"stage\\\":\\\"%s\\\",\\\"calls\\\":%d,\\\"probes\\\":%d,\\\"max\\\":%d}%n",stage,findCalls,findProbes,findMax);findCalls=findProbes=findMax=0;}\n    void close() {\n        probeFind("constructed");')
    # Emit after the complete SCC and reverse-postorder passes.
    graphsource=graphsource.replace('    }\n    int rank(Point point)', '        probeFind("closed");\n    }\n    int rank(Point point)')
    graphsrc=src.with_name('DependencyGraph.java');graphsrc.write_text(graphsource)
    cmd=[str(java/'javac'),'-J-Xmx128m','--release','17','-cp',str(a.jar),'-d',str(classes),str(src),str(graphsrc)];compiled=subprocess.run(cmd,capture_output=True,text=True)
    (a.output/'compile.log').write_text(compiled.stdout+compiled.stderr)
    (a.output/'manifest.json').write_text(json.dumps(dict(sourceRef=a.source_ref,originalSourceSha256=original,originalGraphSha256=graphdigest,probeGraphSha256=hashlib.sha256(graphsrc.read_bytes()).hexdigest(),probeSourceSha256=hashlib.sha256(src.read_bytes()).hexdigest(),jarSha256=hashlib.sha256(a.jar.read_bytes()).hexdigest(),command=cmd,exit=compiled.returncode,timingQualified=False,variant=a.variant,mixLocationHash=a.mix_location_hash,liveHistogram=a.histogram,intervention='Post-convergence exact row dump; equations and propagation unchanged'),indent=2)+'\n');compiled.check_returncode()
    gen=module(ROOT/'benchmark/generate-hub-dispatch.py','gen');runner=module(ROOT/'benchmark/run-sparse-occurs.py','runner');rows=[]
    for n in a.boxes:
        assert 1<=n<=128,'Bounded census only'
        for mode in a.mode:
            name=f'{mode}-{n}';dest=a.output/name;dump=a.output/f'dump-{name}';dump.mkdir();cobol=a.output/f'{name}.cbl';text=gen.source(n,2,mode)
            if a.variant=='prewrite-call':
                for box in range(n):
                    literal=f"    CALL 'PGM{box:05d}'."
                    text=text.replace(literal,f"    CALL TARGET-PGM.\n           MOVE 'PGM{box:05d}' TO TARGET-PGM.\n       "+literal)
            cobol.write_text(text)
            command=[str(java/'java'),'-Xms16m','-Xmx512m','-XX:MaxMetaspaceSize=128m','-XX:MaxDirectMemorySize=32m','-Dprobe.relations='+str(dump),'-cp',str(classes)+os.pathsep+str(a.jar),'com.imd.cobolexplorer.DependencyMain','--source',str(cobol),'--output',str(dest/'dependencies.json'),'--metrics',str(dest/'metrics.jsonl'),'--max-work','1000000000']
            if a.histogram:command.insert(1,'-Dprobe.liveHistogram=true')
            row=runner.execute(command,dest,timeout=120,rss_mib=768,telemetry_period=1,min_available_mib=2048)
            stderr=(dest/'stderr.log').read_text();row['locationHashes']=[json.loads(line.split(' ',1)[1]) for line in stderr.splitlines() if line.startswith('LOCATION_HASH ')];row['findCensus']=[json.loads(line.split(' ',1)[1]) for line in stderr.splitlines() if line.startswith('FIND_CENSUS ')];row['census']=[json.loads(line.split(' ',1)[1]) for line in stderr.splitlines() if line.startswith('RELATION_CENSUS ')]
            out=dest/'dependencies.json';actual=sorted(d['name'] for d in json.loads(out.read_text())['dependencies'] if d['type']=='program') if out.exists() else None
            row.update(boxes=n,mode=mode,variant=a.variant,passed=actual==sorted([f'PGM{b:05d}' for b in range(n)]+(['BOOT0000'] if a.variant=='prewrite-call' else [])) and row['exit'] in (0,1),timingQualified=False,sourceSha256=hashlib.sha256(cobol.read_bytes()).hexdigest());rows.append(row)
            (a.output/'results.json').write_text(json.dumps(rows,indent=2)+'\n');print(name,row['passed'],row['guard'],row['census'],flush=True)
    return 0 if all(r['passed'] for r in rows) else 1
if __name__=='__main__':raise SystemExit(main())
