#!/usr/bin/env python3
"""Read-only solver instrumentation: count graph storage and collect live histograms.

Compiles an isolated DependencyControl overlay. Equations are unchanged; forced
live GC invalidates timing comparisons with production. No heap dumps.
"""
import argparse, hashlib, importlib.util, json, os, shutil, subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
JDK = (Path(os.environ['JAVA_HOME'])/'bin' if os.environ.get('JAVA_HOME')
       else Path(shutil.which('java')).resolve().parent)

HOOK = r'''
    private void layoutProbe(String stage, Construction construction) {
        String directory=System.getProperty("probe.layout");if(directory==null)return;
        long next=0,entries=0,calls=0,parents=0,resultParents=0,subscriptions=0,facts=0;
        var physical=new HashSet<Exit>();var endpoints=new HashSet<String>();var scopes=new HashSet<String>();
        if(construction!=null) {
            for(var plan:construction.plans.values()) {
                next+=plan.next.size();entries+=plan.entries.size();calls+=plan.calls.size();
                resultParents+=plan.resultParents.size();subscriptions+=plan.subscribers.size();facts+=plan.results.cardinality();
                physical.add(plan.point.exit());endpoints.add(plan.point.endpoint());scopes.add(plan.point.escapeScope());
            }
            for(var values:construction.parents.values())parents+=values.size();
        }else for(var entry:plans.entrySet()) {
            physical.add(entry.getKey().exit());endpoints.add(entry.getKey().endpoint());scopes.add(entry.getKey().escapeScope());
            next+=entry.getValue().next.size();entries+=entry.getValue().entries.size();calls+=entry.getValue().calls.size();
        }
        String buckets=bucketProbe(construction==null?plans:construction.plans);
        System.err.printf(Locale.ROOT,"CONTROL_LAYOUT {\"stage\":\"%s\",\"points\":%d,\"physicalExits\":%d,\"endpoints\":%d,\"scopes\":%d,\"next\":%d,\"entries\":%d,\"calls\":%d,\"parents\":%d,\"resultParents\":%d,\"subscriptions\":%d,\"facts\":%d,\"frozenPlans\":%d,\"buckets\":%s}%n",
            stage,construction==null?plans.size():construction.plans.size(),physical.size(),endpoints.size(),scopes.size(),next,entries,calls,parents,resultParents,subscriptions,facts,plans.size(),buckets);
        try {
            var server=java.lang.management.ManagementFactory.getPlatformMBeanServer();
            var diagnostic=new javax.management.ObjectName("com.sun.management:type=DiagnosticCommand");
            for(var command:List.of("gcClassHistogram","gcHeapInfo")) {
                var content=(String)server.invoke(diagnostic,command,new Object[]{new String[0]},new String[]{"[Ljava.lang.String;"});
                java.nio.file.Files.writeString(java.nio.file.Path.of(directory,stage+"."+command+".txt"),content);
            }
        }catch(Exception e){throw new IllegalStateException(e);}
    }
    private static String bucketProbe(Map<?,?> map) {
        try {
            var tableField=HashMap.class.getDeclaredField("table");tableField.setAccessible(true);
            var table=(Object[])tableField.get(map);int occupied=0,trees=0,max=0;
            if(table!=null)for(var head:table)if(head!=null) {
                occupied++;if(head.getClass().getSimpleName().equals("TreeNode"))trees++;int count=0;
                for(var node=head;node!=null;) {
                    count++;var type=node.getClass();java.lang.reflect.Field next=null;
                    while(next==null){try{next=type.getDeclaredField("next");}catch(NoSuchFieldException e){type=type.getSuperclass();}}
                    next.setAccessible(true);node=next.get(node);
                }max=Math.max(max,count);
            }
            return "{\"capacity\":"+(table==null?0:table.length)+",\"occupied\":"+occupied+",\"treeBins\":"+trees+",\"maxBin\":"+max+"}";
        }catch(Exception e){throw new IllegalStateException(e);}
    }
    private static void rankProbe(Map<Point,Integer> ranks) {
        if(System.getProperty("probe.layout")==null)return;
        try {
            var field=ranks.getClass().getDeclaredField("table");field.setAccessible(true);var table=(Object[])field.get(ranks);
            long probes=0;int max=0,over16=0;
            for(var key:ranks.keySet()) {
                int index=Math.floorMod(key.hashCode(),table.length>>1)<<1,count=1;
                while(table[index]!=key){index+=2;if(index==table.length)index=0;count++;if(count>table.length)throw new IllegalStateException("rank key missing");}
                probes+=count;max=Math.max(max,count);if(count>16)over16++;
            }
            System.err.printf(Locale.ROOT,"FORWARD_LAYOUT {\"keys\":%d,\"totalProbes\":%d,\"maxProbes\":%d,\"over16\":%d}%n",ranks.size(),probes,max,over16);
        }catch(Exception e){throw new IllegalStateException(e);}
    }
'''


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def module(path, name):
    spec = importlib.util.spec_from_file_location(name, path)
    result = importlib.util.module_from_spec(spec); spec.loader.exec_module(result)
    return result


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('output', type=Path)
    p.add_argument('--jar', type=Path, required=True)
    p.add_argument('--boxes', type=int, nargs='+', default=[16, 32, 48])
    p.add_argument('--mode', choices=('return-fanout','return-fanout-fixed'), default='return-fanout')
    a = p.parse_args(); a.output = a.output.resolve(); a.jar = a.jar.resolve()
    a.output.mkdir(parents=True, exist_ok=False)
    original = ROOT/'src/main/java/com/imd/cobolexplorer/DependencyControl.java'
    source = original.read_text()
    replacements = [
        ('construction.solve(roots);', 'construction.solve(roots);layoutProbe("solved",construction);'),
        ('var pending=new ArrayDeque<>(roots);', 'layoutProbe("frozen",construction);var pending=new ArrayDeque<>(roots);'),
        ('var order=postorder();var seen=new HashSet<Point>();', 'layoutProbe("released",null);var order=postorder();var seen=new HashSet<Point>();'),
        ('return Map.copyOf(ranks);', 'var frozen=Map.copyOf(ranks);rankProbe(frozen);return frozen;'),
        ('private abstract static sealed class Task', HOOK+'\n    private abstract static sealed class Task'),
    ]
    for old, new in replacements:
        assert source.count(old)==1, old
        source = source.replace(old, new)
    probe_source = a.output/'source/com/imd/cobolexplorer/DependencyControl.java'
    probe_source.parent.mkdir(parents=True); probe_source.write_text(source)
    classes = a.output/'classes'; classes.mkdir()
    compile_cmd = [str(JDK/'javac'), '-J-Xmx128m', '--release', '17', '-cp', str(a.jar), '-d', str(classes), str(probe_source)]
    compile_result = subprocess.run(compile_cmd, capture_output=True, text=True, timeout=30)
    (a.output/'compile.log').write_text(compile_result.stdout+compile_result.stderr)
    manifest = dict(originalSourceSha256=sha(original), probeSourceSha256=sha(probe_source),
                    jarSha256=sha(a.jar), command=compile_cmd, exit=compile_result.returncode,
                    timingQualified=False, mode=a.mode, intervention='Counts and live histograms through VM DiagnosticCommand MBean; equations unchanged')
    (a.output/'manifest.json').write_text(json.dumps(manifest, indent=2)+'\n')
    compile_result.check_returncode()
    gen = module(ROOT/'benchmark/generate-hub-dispatch.py', 'generator')
    runner = module(ROOT/'benchmark/run-sparse-occurs.py', 'runner')
    rows=[]
    for boxes in a.boxes:
        assert 1<=boxes<=48, 'Small probes only; larger production measurements already exist'
        case=a.output/f'{a.mode}-{boxes}'; checkpoints=a.output/f'checkpoints-{boxes}'; checkpoints.mkdir()
        cobol=a.output/f'input-{boxes}.cbl'; cobol.write_text(gen.source(boxes,2,a.mode))
        cmd=[str(JDK/'java'), '-Xms16m', '-Xmx256m', '-XX:MaxMetaspaceSize=128m', '-XX:MaxDirectMemorySize=32m',
             '--add-opens=java.base/java.util=ALL-UNNAMED', '-Dprobe.layout='+str(checkpoints),
             '-cp', str(classes)+os.pathsep+str(a.jar), 'com.imd.cobolexplorer.DependencyMain',
             '--source', str(cobol), '--output', str(case/'dependencies.json'), '--metrics', str(case/'metrics.jsonl'), '--max-work', '100000000']
        row=runner.execute(cmd,case,timeout=90,rss_mib=640,telemetry_period=1,min_available_mib=2048)
        collected=[dict(file=str(path.relative_to(a.output)),sha256=sha(path)) for path in sorted(checkpoints.glob('*.txt'))]
        target=case/'dependencies.json';actual=sorted(d['name'] for d in json.loads(target.read_text())['dependencies'] if d['type']=='program') if target.exists() else None
        layout=[json.loads(line.removeprefix('CONTROL_LAYOUT ')) for line in (case/'stderr.log').read_text().splitlines() if line.startswith('CONTROL_LAYOUT ')]
        rank=[json.loads(line.removeprefix('FORWARD_LAYOUT ')) for line in (case/'stderr.log').read_text().splitlines() if line.startswith('FORWARD_LAYOUT ')]
        row.update(boxes=boxes,mode=a.mode,sourceSha256=sha(cobol),actual=actual,passed=actual==[f'PGM{b:05d}' for b in range(boxes)] and row['exit'] in (0,1),
                   layout=layout,rank=rank,collected=collected,timingQualified=False,productionQualified=False)
        rows.append(row);(a.output/'results.json').write_text(json.dumps(rows,indent=2)+'\n')
        print(boxes,row['exit'],row['guard'],row['passed'],layout,len(collected),flush=True)
    return 0 if all(row['passed'] and len(row['collected'])==6 for row in rows) else 1


if __name__=='__main__':raise SystemExit(main())
