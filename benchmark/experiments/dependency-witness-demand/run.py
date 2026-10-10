#!/usr/bin/env python3
"""Sequential bounded A/B: frozen main versus sparse reaching definitions."""
import argparse,hashlib,importlib.util,json,os,time
from pathlib import Path
ROOT=Path(__file__).resolve().parents[3]
def load(path,name):
 spec=importlib.util.spec_from_file_location(name,path);m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m);return m
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def deps(doc):
 return {(p['program'],d['type'],d['name']):(d['at']['file'],d['at']['line']) for p in (doc if isinstance(doc,list) else [doc]) for d in p['dependencies']}
def main():
 p=argparse.ArgumentParser();p.add_argument('output',type=Path);p.add_argument('--jar',type=Path,required=True);p.add_argument('--build',type=Path,required=True);p.add_argument('--sizes',type=int,nargs='*',default=[]);p.add_argument('--modes',nargs='+',choices=['main','definitions'],default=['definitions']);p.add_argument('--family',choices=['dynamic','dynamic-only','negative-bound'],default='dynamic');p.add_argument('--focused',action='store_true');p.add_argument('--carddemo',action='store_true');p.add_argument('--repeats',type=int,default=1);p.add_argument('--heap',type=int,default=512);p.add_argument('--only',nargs='+');p.add_argument('--timeout',type=int,default=60);a=p.parse_args();a.output=a.output.resolve();a.output.mkdir(parents=True,exist_ok=False);inputs=a.output/'inputs';inputs.mkdir();(a.output/'run.py').write_bytes(Path(__file__).read_bytes());a.jar=a.jar.resolve();a.build=a.build.resolve()
 runner=load(ROOT/'benchmark/run-sparse-occurs.py','bounded');gen=load(ROOT/'benchmark/generate-hub-dispatch.py','hub');baseline=ROOT/'benchmark/results/remote-requalification-20261009';cases=[]
 for n in a.sizes:
  if a.family!='negative-bound':
   text=gen.source(n,2,'return-fanout')
   for i in range(n):text=text.replace(f"    CALL 'PGM{i:05d}'.",f"    CALL TARGET-PGM.\n           MOVE 'PGM{i:05d}' TO TARGET-PGM.\n           CALL 'PGM{i:05d}'.")
   if a.family=='dynamic-only':
    for i in range(n):text=text.replace(f"           CALL 'PGM{i:05d}'.", "           CONTINUE.")
   expected={'BOOT0000',*[f'PGM{i:05d}' for i in range(n)]}
  else:
   lines=['IDENTIFICATION DIVISION.','PROGRAM-ID. NEGATIVEBOUND.','DATA DIVISION.','WORKING-STORAGE SECTION.',"01 P PIC X(8) VALUE 'DEAD'.",'01 SELECTOR-VALUE PIC 9(5).','PROCEDURE DIVISION.',"MOVE 'SAME' TO P.",'HUB.','ACCEPT SELECTOR-VALUE.','GO TO']
   for i in range(0,n,4):lines.append(' '.join(f'BOX-{j:05d}' for j in range(i,min(i+4,n))))
   lines+=['LAST-BOX DEPENDING ON SELECTOR-VALUE.','GO TO HUB.']
   for i in range(n):lines += [f'BOX-{i:05d}.','CALL P.','GO TO HUB.']
   lines += ['LAST-BOX.','GOBACK.'];text=''.join('       '+l+'\n' for l in lines);expected={'SAME'}
  source=inputs/f'{a.family}-{n}.cbl';source.write_text(text);cases.append((f'{a.family}-{n}',source,[],None,expected))
 if a.focused:
  for old in json.loads((baseline/'standalone-results.json').read_text()):
   cmd=old['command'];cases.append((old['id'],Path(cmd[cmd.index('--source')+1]),[],Path(cmd[cmd.index('--output')+1]),None))
 hashes={}
 if a.carddemo:
  hashes=json.loads((baseline/'carddemo/input-hashes.json').read_text());assert all(sha(Path(k))==v for k,v in hashes.items())
  for old in json.loads((baseline/'carddemo/results.json').read_text()):
   cmd=old['command'];extra=[]
   for i,v in enumerate(cmd):
    if v=='--copy-dir':extra.extend([v,cmd[i+1]])
   cases.append((old['id'],Path(cmd[cmd.index('--source')+1]),extra,Path(cmd[cmd.index('--output')+1]),None))
 # Named negative cases generated below are also part of the experiment.
 fixtureOracles={r['id']:r for r in json.loads(Path(__file__).with_name('fixtures').joinpath('expected.json').read_text())}
 if not (a.sizes or a.focused or a.carddemo):
  for source in sorted(Path(__file__).with_name('fixtures').glob('*.cbl')):
   frozen=fixtureOracles[source.stem];assert sha(source)==frozen['sha256'],source
   oracle=inputs/(source.stem+'.oracle.json');oracle.write_text(json.dumps(frozen['main'])+'\n')
   cases.append((source.stem,source,[],oracle,None))
 if a.only:cases=[c for c in cases if c[0] in a.only]
 assert cases
 for case,source,extra,oracle,expected in cases:
  frozen=inputs/(case+".source.txt");frozen.write_bytes(source.read_bytes())
 rows=[];java=str(Path(os.environ['JAVA_HOME'])/'bin/java')
 for case,source,extra,oracle,expected in cases:
  reference=json.loads(oracle.read_text()) if oracle else None
  for mode in a.modes:
   for repeat in range(a.repeats):
    dest=a.output/case/mode/str(repeat+1);out=dest/'dependencies.json'
    cmd=[java,'-Xms16m',f'-Xmx{a.heap}m','-XX:MaxMetaspaceSize=128m','-XX:MaxDirectMemorySize=32m','-XX:ActiveProcessorCount=2','-XX:+UseSerialGC','-XX:+ExitOnOutOfMemoryError']
    if mode=='main':cmd+=['-jar',str(a.jar)]
    else:cmd+=['-cp',str(a.build/'classes')+os.pathsep+str(a.jar),'com.imd.cobolexplorer.DependencyMain']
    cmd+=['--source',str(source),'--output',str(out),'--metrics',str(dest/'metrics.jsonl'),'--max-work','100000000',*extra]
    r=runner.execute(cmd,dest,timeout=a.timeout,rss_mib=768,telemetry_period=1,min_available_mib=2048)
    r.update(case=case,mode=mode,repeat=repeat+1,source=str(source),sourceSha256=sha(source),heapMiB=a.heap)
    stderr=(dest/'stderr.log').read_text();r['unsupported']='UNSUPPORTED_EXPERIMENT:' in stderr;r['completed']=r['exit'] in (0,1) and r['guard'] is None and out.exists();r['document']=json.loads(out.read_text()) if out.exists() else None
    r['probe']=[json.loads(l.removeprefix('DEFINITIONS ')) for l in stderr.splitlines() if l.startswith('DEFINITIONS ')]
    if mode=='main' and r['completed'] and reference is None:reference=r['document']
    if r['completed']:
     actual=deps(r['document']);r['programNames']=sorted({k[2] for k in actual if k[1]=='program'})
     if reference is not None:
      before=deps(reference);r['lost']=sorted(set(before)-set(actual));r['extra']=sorted(set(actual)-set(before));r['changedAt']=[list(k) for k in set(before)&set(actual) if before[k]!=actual[k]];r['fullJsonEqual']=r['document']==reference
     if expected is not None:r['expectedNamesEqual']=set(r['programNames'])==expected
    if case in fixtureOracles:r['expectedExtra']=fixtureOracles[case]['experimentExtra'] if mode=='definitions' else []
    rows.append(r);(a.output/'results.json').write_text(json.dumps(rows,indent=2)+'\n');print(case,mode,repeat+1,r['exit'],r['guard'],'UNSUPPORTED' if r['unsupported'] else 'DONE' if r['completed'] else 'FAIL',round(r['seconds'],3),round(r['peakRssKiB']/1024,1),'lost',len(r.get('lost',[])),'extra',len(r.get('extra',[])),'at',len(r.get('changedAt',[])),r['probe'],flush=True)
    if r['guard'] in ('SYSTEM_MEMORY_GUARD','RSS_GUARD'):break
   if r['guard'] in ('SYSTEM_MEMORY_GUARD','RSS_GUARD'):break
  if r['guard'] in ('SYSTEM_MEMORY_GUARD','RSS_GUARD'):break
 assert all(sha(Path(k))==v for k,v in hashes.items())
 (a.output/'manifest.json').write_text(json.dumps(dict(baseJarSha256=sha(a.jar),buildManifestSha256=sha(a.build/'manifest.json'),selectedCases=[c[0] for c in cases],inputHashes=hashes,heapMiB=a.heap,rssMiB=768,reserveMiB=2048,productionQualified=False),indent=2)+'\n')
if __name__=='__main__':main()
