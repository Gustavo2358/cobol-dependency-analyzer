#!/usr/bin/env python3
"""Small, read-only live-state census. Forced GC makes timings diagnostic only."""
import argparse, hashlib, importlib.util, json, os, subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
def module(path,name):
    spec=importlib.util.spec_from_file_location(name,path);m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m);return m
HOOK=r'''
    private void stateCensus() {
        var states=new ArrayList<State>(before.values());
        for(var context:contexts){states.add(context.key.input());states.addAll(context.results.values());for(var caller:context.callers)states.add(caller.input());}
        states.addAll(instantiated.values());
        instantiated.keySet().forEach(k->states.add(k.input()));
        calculations.keySet().forEach(k->states.add(k.input()));
        refinements.keySet().forEach(k->states.add(k.input()));
        tests.keySet().forEach(k->states.add(k.input()));
        for(var entry:computations.entrySet()){states.add(entry.getKey().input());for(var action:entry.getValue())states.add(action.patch().result());}
        var nodeRefs=Collections.newSetFromMap(new IdentityHashMap<String,Boolean>());var nodeContents=new HashSet<String>();
        for(var location:before.keySet()){nodeRefs.add(location.node());nodeContents.add(location.node());}
        System.err.printf("STATE_FIELD {\"field\":\"locationNodes\",\"identities\":%d,\"equalClasses\":%d}%n",nodeRefs.size(),nodeContents.size());
        var identity=Collections.newSetFromMap(new IdentityHashMap<State,Boolean>());identity.addAll(states);
        var equal=new HashSet<State>(identity);
        System.err.printf("STATE_SHARING {\"references\":%d,\"identities\":%d,\"equalClasses\":%d,\"before\":%d,\"contexts\":%d}%n",states.size(),identity.size(),equal.size(),before.size(),contexts.size());
        for(String field:List.of("values","parameters","handlers","reached","controls")) {
            var refs=Collections.newSetFromMap(new IdentityHashMap<Object,Boolean>());var contents=new HashSet<Object>();
            for(var state:identity){Object object=switch(field){case "values"->state.values();case "parameters"->state.parameters();case "handlers"->state.handlers();case "reached"->state.reached();default->state.controls();};refs.add(object);contents.add(object);}
            System.err.printf("STATE_FIELD {\"field\":\"%s\",\"identities\":%d,\"equalClasses\":%d}%n",field,refs.size(),contents.size());
        }
    }
    private void sharingProbe() {
        stateCensus();
        try {
            var server=java.lang.management.ManagementFactory.getPlatformMBeanServer();
            var diagnostic=new javax.management.ObjectName("com.sun.management:type=DiagnosticCommand");
            for(var command:List.of("gcClassHistogram","gcHeapInfo")) {
                var content=(String)server.invoke(diagnostic,command,new Object[]{new String[0]},new String[]{"[Ljava.lang.String;"});
                java.nio.file.Files.writeString(java.nio.file.Path.of(System.getProperty("probe.sharing"),command+".txt"),content);
            }
        }catch(Exception e){throw new IllegalStateException(e);}
    }
'''
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('output',type=Path);p.add_argument('--jar',required=True,type=Path);p.add_argument('--source-ref',default='HEAD');p.add_argument('--boxes',nargs='+',type=int,default=[64,128]);a=p.parse_args()
    a.output=a.output.resolve();a.output.mkdir(parents=True,exist_ok=False);a.jar=a.jar.resolve()
    path='src/main/java/com/imd/cobolexplorer/DependencyFlow.java'
    source=subprocess.run(['git','-C',str(ROOT),'show',a.source_ref+':'+path],capture_output=True,text=True,check=True).stdout if a.source_ref!='working' else (ROOT/path).read_text()
    original=hashlib.sha256(source.encode()).hexdigest();anchor='        var queriesAt=new HashMap<String,List<Query>>();';assert source.count(anchor)==1
    source=source.replace(anchor,'        sharingProbe();\n'+anchor).replace('    static String handle(Ast.Statement s)',HOOK+'\n    static String handle(Ast.Statement s)')
    src=a.output/'source/com/imd/cobolexplorer/DependencyFlow.java';src.parent.mkdir(parents=True);src.write_text(source);classes=a.output/'classes';classes.mkdir()
    java=Path(os.environ['JAVA_HOME'])/'bin'
    cmd=[str(java/'javac'),'-J-Xmx128m','--release','17','-cp',str(a.jar),'-d',str(classes),str(src)]
    compiled=subprocess.run(cmd,capture_output=True,text=True);(a.output/'compile.log').write_text(compiled.stdout+compiled.stderr);compiled.check_returncode()
    (a.output/'manifest.json').write_text(json.dumps(dict(sourceRef=a.source_ref,originalSourceSha256=original,probeSourceSha256=hashlib.sha256(src.read_bytes()).hexdigest(),jarSha256=hashlib.sha256(a.jar.read_bytes()).hexdigest(),command=cmd,timingQualified=False,intervention='Exact equality and identity census, followed by forced live GC histogram; equations unchanged'),indent=2)+'\n')
    gen=module(ROOT/'benchmark/generate-hub-dispatch.py','gen');runner=module(ROOT/'benchmark/run-sparse-occurs.py','runner');rows=[]
    for n in a.boxes:
        assert 1<=n<=128,'Bounded census only'
        dest=a.output/f'n{n}';hist=a.output/f'histogram-{n}';hist.mkdir();cobol=a.output/f'input-{n}.cbl';cobol.write_text(gen.source(n,2,'return-fanout'))
        command=[str(java/'java'),'-Xms16m','-Xmx512m','-XX:MaxMetaspaceSize=128m','-XX:MaxDirectMemorySize=32m','-Dprobe.sharing='+str(hist),'-cp',str(classes)+os.pathsep+str(a.jar),'com.imd.cobolexplorer.DependencyMain','--source',str(cobol),'--output',str(dest/'dependencies.json'),'--metrics',str(dest/'metrics.jsonl'),'--max-work','1000000000']
        row=runner.execute(command,dest,timeout=120,rss_mib=768,telemetry_period=1,min_available_mib=2048)
        stderr=(dest/'stderr.log').read_text();row['census']=[json.loads(line.split(' ',1)[1]) for line in stderr.splitlines() if line.startswith(('STATE_SHARING ','STATE_FIELD '))]
        out=dest/'dependencies.json';actual=sorted(d['name'] for d in json.loads(out.read_text())['dependencies'] if d['type']=='program') if out.exists() else None
        row.update(boxes=n,passed=actual==[f'PGM{b:05d}' for b in range(n)] and row['exit'] in (0,1),timingQualified=False,sourceSha256=hashlib.sha256(cobol.read_bytes()).hexdigest());rows.append(row)
        (a.output/'results.json').write_text(json.dumps(rows,indent=2)+'\n');print(n,row['passed'],row['guard'],row['census'],flush=True)
    return 0 if all(r['passed'] for r in rows) else 1
if __name__=='__main__':raise SystemExit(main())
