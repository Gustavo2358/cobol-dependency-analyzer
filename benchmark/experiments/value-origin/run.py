#!/usr/bin/env python3
"""Differential evidence: exact, extra candidates, missing candidates, or incomplete.
No result of this experiment qualifies production. Raw outputs remain intact.
"""
import argparse,hashlib,importlib.util,json,os,subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[3]
def module(path,name):
 spec=importlib.util.spec_from_file_location(name,path);m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m);return m

def dependencies(doc):
 programs=doc if isinstance(doc,list) else [doc] if doc else []
 return {(p['program'],d['type'],d['name']) for p in programs for d in p['dependencies']}

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('output',type=Path);p.add_argument('--jar',type=Path,required=True);p.add_argument('--overlay',type=Path,required=True);p.add_argument('--cases',nargs='+',required=True);p.add_argument('--repeats',type=int,default=1);p.add_argument('--timeout',type=int,default=60);p.add_argument('--prototype-only',action='store_true');p.add_argument('--profile',action='store_true');a=p.parse_args()
 a.output=a.output.resolve();a.output.mkdir(parents=True,exist_ok=False);inputs=a.output/'inputs';inputs.mkdir();jar=a.jar.resolve();overlay=a.overlay.resolve();manifest=json.loads((overlay/'manifest.json').read_text());assert manifest['exit']==0;assert hashlib.sha256(jar.read_bytes()).hexdigest()==manifest['jarSha256']
 guard=module(ROOT/'benchmark/run-sparse-occurs.py','guard');gen=module(ROOT/'benchmark/generate-hub-dispatch.py','gen')
 fixed={c['id']:(ROOT/'benchmark/fixtures/precision-budget'/c['source'],c['expected']) for c in json.loads((ROOT/'benchmark/fixtures/precision-budget/expected.json').read_text())}
 fixed.update({'occurs-'+c['id']:(ROOT/'src/test/resources/dependency-regression/sparse-occurs'/c['source'],c['expected']) for c in json.loads((ROOT/'src/test/resources/dependency-regression/sparse-occurs/expected.json').read_text())})
 fixed['fixture02']=(ROOT/'src/test/resources/dependency-regression/video-reconstructed/fixture02.fixed.cbl',None);fixed['escape-sentinel']=(ROOT/'benchmark/fixtures/control-return-fanout/escape-ablation-sentinel.cbl',['AFTER','ESCAPED'])
 for name in ['group-partial','group-alias','publication-kinds']:fixed['experiment-'+name]=(Path(__file__).parent/'fixtures'/(name+'.cbl'),None)
 rows=[]
 for case in a.cases:
  if case.startswith(('fanout-','dynamic-','perform-','flags-','hub-value-')):
   n=int(case.rsplit('-',1)[1]);mode='return-fanout' if case.startswith(('fanout-','dynamic-')) else 'perform' if case.startswith('perform-') else 'flags' if case.startswith('flags-') else 'hub-perform';text=gen.source(n,2,mode)
   if case.startswith('dynamic-'):
    for b in range(n):text=text.replace(f"    CALL 'PGM{b:05d}'.",f"    CALL TARGET-PGM.\n           MOVE 'PGM{b:05d}' TO TARGET-PGM.\n           CALL 'PGM{b:05d}'.")
   expected=[f'PGM{b:05d}' for b in range(n)]+(['BOOT0000'] if case.startswith(('dynamic-','hub-value-')) else ['ZERO0000'] if case.startswith('flags-') else [])
  else:
   path,expected=fixed[case];text=path.read_text()
  source=inputs/(case+'.cbl');source.write_text(text)
  for repeat in range(1,a.repeats+1):
   baseline=None
   for variant in (['origin'] if a.prototype_only else ['baseline','origin']):
    dest=a.output/case/f'run{repeat}'/variant
    command=[str(Path(os.environ['JAVA_HOME'])/'bin/java'),'-Xms16m','-Xmx512m','-XX:MaxMetaspaceSize=128m','-XX:MaxDirectMemorySize=32m','-XX:+ExitOnOutOfMemoryError']
    if a.profile:command += ['-XX:StartFlightRecording=settings=profile,maxsize=16m,dumponexit=true,filename='+str(dest/'profile.jfr')]
    command += ['-cp',str(overlay/'classes')+os.pathsep+str(jar),'com.imd.cobolexplorer.DependencyMain'] if variant=='origin' else ['-jar',str(jar)]
    command += ['--source',str(source),'--output',str(dest/'dependencies.json'),'--metrics',str(dest/'metrics.jsonl'),'--max-work','100000000']
    r=guard.execute(command,dest,timeout=a.timeout,rss_mib=768,telemetry_period=1,min_available_mib=2048)
    path=dest/'dependencies.json';doc=json.loads(path.read_text()) if path.exists() else None;stderr=(dest/'stderr.log').read_text();completed=doc is not None and r['exit'] in (0,1) and r['guard'] is None
    probes=[json.loads(s.split(' ',1)[1]) for s in stderr.splitlines() if s.startswith('ORIGIN_PROBE ')]
    if variant=='origin' and completed:assert probes,'Origin solver was not used'
    r.update(case=case,repeat=repeat,variant=variant,completed=completed,sourceSha256=hashlib.sha256(source.read_bytes()).hexdigest(),jarSha256=manifest['jarSha256'],overlaySha256=hashlib.sha256((overlay/'manifest.json').read_bytes()).hexdigest(),origin=probes,document=doc,diagnostics=guard.diagnostics(dest/'stderr.log'),dependencies=sorted(dependencies(doc)),expectedPrograms=sorted(expected) if expected is not None else None,qualifiedForProduction=False)
    actual={d[2] for d in dependencies(doc) if d[1]=='program'}
    r['missingOraclePrograms']=sorted(set(expected)-actual) if expected is not None and completed else None
    r['additionalOraclePrograms']=sorted(actual-set(expected)) if expected is not None and completed else None
    if variant=='baseline':
     baseline=r
     # Expected names are an explicit synthetic oracle, not a parity substitute.
     if completed and expected is not None:assert not r['missingOraclePrograms'] and not r['additionalOraclePrograms'],('Invalid synthetic oracle',case,r['missingOraclePrograms'],r['additionalOraclePrograms'])
    comparable=baseline is not None and baseline['completed'] and completed
    r.update(comparable=comparable,missing=sorted(dependencies(baseline['document'])-dependencies(doc)) if comparable else None,additional=sorted(dependencies(doc)-dependencies(baseline['document'])) if comparable else None,fullJsonEqual=doc==baseline['document'] if comparable else None)
    verdict='INCOMPLETE' if not completed else 'BASELINE' if variant=='baseline' else 'MISSING' if comparable and r['missing'] or r['missingOraclePrograms'] else 'ADDITIONAL' if comparable and r['additional'] or r['additionalOraclePrograms'] else 'EXACT' if comparable and r['fullJsonEqual'] else 'CANDIDATES_MATCH' if comparable else 'ORACLE_ONLY' if expected is not None else 'NO_ORACLE'
    r['verdict']=verdict;rows.append(r);(a.output/'results.json').write_text(json.dumps(rows,indent=2)+'\n')
    print(case,repeat,variant,verdict,'exit',r['exit'],r['guard'],'seconds',round(r['seconds'],3),'RSSMiB',round(r['peakRssKiB']/1024,1),'missing',len(r['missing'] or r['missingOraclePrograms'] or []),'additional',len(r['additional'] or r['additionalOraclePrograms'] or []),probes,flush=True)
 (a.output/'manifest.json').write_text(json.dumps({'baselineCommit':manifest['baselineCommit'],'jarSha256':manifest['jarSha256'],'overlayManifest':str(overlay/'manifest.json'),'productionQualified':False,'heapMiB':512,'rssGuardMiB':768,'systemReserveMiB':2048,'timeoutSeconds':a.timeout,'maxWork':100000000,'prototypeOnly':a.prototype_only,'rows':len(rows)},indent=2)+'\n')
 return 0
if __name__=='__main__':raise SystemExit(main())
