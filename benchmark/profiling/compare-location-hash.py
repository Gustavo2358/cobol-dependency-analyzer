#!/usr/bin/env python3
"""Bounded paired experiment changing only Location hashing in an isolated overlay."""
import argparse,hashlib,importlib.util,json,os,statistics,subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
def module(path,name):
 spec=importlib.util.spec_from_file_location(name,path);m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m);return m
PATCH='''
        private static int hashMix(int x){x^=x>>>16;x*=0x7feb352d;x^=x>>>15;x*=0x846ca68b;return x^(x>>>16);}
        @Override public int hashCode(){int h=hashMix(context)^Integer.rotateLeft(hashMix(Objects.hashCode(node)),11);h=hashMix(h)^hashMix(Objects.hashCode(result));return hashMix(h)^hashMix(Objects.hashCode(caller));}
'''
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('output',type=Path);p.add_argument('--jar',type=Path,required=True);p.add_argument('--source-ref',default='HEAD');p.add_argument('--boxes',type=int,default=128);a=p.parse_args()
 assert 1<=a.boxes<=128;a.output=a.output.resolve();a.output.mkdir(parents=True,exist_ok=False);a.jar=a.jar.resolve();java=Path(os.environ['JAVA_HOME'])/'bin'
 path='src/main/java/com/imd/cobolexplorer/DependencyFlow.java';source=subprocess.run(['git','-C',str(ROOT),'show',a.source_ref+':'+path],capture_output=True,text=True,check=True).stdout;original=hashlib.sha256(source.encode()).hexdigest()
 anchor='        Location(int context,String node){this(context,node,null,null);}';assert source.count(anchor)==1;source=source.replace(anchor,anchor+PATCH)
 src=a.output/'source/com/imd/cobolexplorer/DependencyFlow.java';src.parent.mkdir(parents=True);src.write_text(source);classes=a.output/'classes';classes.mkdir()
 compilecmd=[str(java/'javac'),'-J-Xmx128m','--release','17','-cp',str(a.jar),'-d',str(classes),str(src)];c=subprocess.run(compilecmd,capture_output=True,text=True);(a.output/'compile.log').write_text(c.stdout+c.stderr);c.check_returncode()
 (a.output/'manifest.json').write_text(json.dumps(dict(sourceRef=a.source_ref,originalSourceSha256=original,overlaySha256=hashlib.sha256(src.read_bytes()).hexdigest(),jarSha256=hashlib.sha256(a.jar.read_bytes()).hexdigest(),command=compilecmd,equationsUnchanged=True,intervention='Location hash only; no instrumentation in measured runs, exact equality unchanged'),indent=2)+'\n')
 gen=module(ROOT/'benchmark/generate-hub-dispatch.py','gen');runner=module(ROOT/'benchmark/run-sparse-occurs.py','runner');cobol=a.output/'input.cbl';cobol.write_text(gen.source(a.boxes,2,'return-fanout'));rows=[];oracle=None;counters=None
 for i,order in enumerate([('baseline','mixed'),('mixed','baseline'),('baseline','mixed')],1):
  for kind in order:
   dest=a.output/f'{kind}-{i}';cmd=[str(java/'java'),'-Xms16m','-Xmx512m','-XX:MaxMetaspaceSize=128m','-XX:MaxDirectMemorySize=32m','-XX:+ExitOnOutOfMemoryError']
   cmd+=['-jar',str(a.jar)] if kind=='baseline' else ['-cp',str(classes)+os.pathsep+str(a.jar),'com.imd.cobolexplorer.DependencyMain']
   cmd+=['--source',str(cobol),'--output',str(dest/'dependencies.json'),'--metrics',str(dest/'metrics.jsonl'),'--max-work','1000000000']
   r=runner.execute(cmd,dest,timeout=90,rss_mib=768,telemetry_period=1,min_available_mib=2048)
   out=dest/'dependencies.json';document=json.loads(out.read_text()) if out.exists() else None;metrics=[json.loads(line)['metrics'] for line in (dest/'metrics.jsonl').read_text().splitlines() if '"metrics"' in line] if (dest/'metrics.jsonl').exists() else []
   if oracle is None and document is not None:oracle=document
   work={k:v for k,v in metrics[0].items() if not k.endswith('Nanos')} if metrics else None
   if counters is None and work is not None:counters=work
   actual=sorted(d['name'] for d in document['dependencies'] if d['type']=='program') if document else None
   r.update(kind=kind,iteration=i,passed=r['exit'] in (0,1) and document==oracle and actual==[f'PGM{b:05d}' for b in range(a.boxes)],identicalCounters=work==counters,sourceSha256=hashlib.sha256(cobol.read_bytes()).hexdigest(),productionQualified=False,metrics=metrics);rows.append(r)
   (a.output/'results.json').write_text(json.dumps(rows,indent=2)+'\n');print(kind,i,r['passed'],r['identicalCounters'],round(r['seconds'],3),round(r['peakRssKiB']/1024,1),flush=True)
 summary={kind:{'secondsMedian':statistics.median(r['seconds'] for r in rows if r['kind']==kind),'rssMiBMedian':statistics.median(r['peakRssKiB']/1024 for r in rows if r['kind']==kind)} for kind in ('baseline','mixed')};(a.output/'summary.json').write_text(json.dumps(summary,indent=2)+'\n');print(summary,flush=True)
 return 0 if all(r['passed'] and r['identicalCounters'] for r in rows) else 1
if __name__=='__main__':raise SystemExit(main())
