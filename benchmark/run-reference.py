#!/usr/bin/env python3
"""Replay the frozen reference CLI commands, with outputs confined to this clone.
Same sources/includes/JDK/heap as run-carddemo.py. No redundant CFG-only run.
Usage: python3 benchmark/run-reference.py BASELINE_RESULTS_JSON OUTPUT_DIR [DEPENDENCY_RUNTIME_DIR]
"""
import json,subprocess,sys,time,hashlib
from pathlib import Path
rows=json.loads(Path(sys.argv[1]).read_text());out=Path(sys.argv[2]).resolve();out.mkdir(parents=True,exist_ok=True)
assert len(rows)==73, 'Expected full 73-source corpus'
input_hashes=Path(sys.argv[1]).parent/'input-hashes.json'
assert input_hashes.exists(), 'Include hashes required'
for source,digest in json.loads(input_hashes.read_text()).items():
 assert hashlib.sha256(Path(source).read_bytes()).hexdigest()==digest, 'Source/include changed: '+source
results=[]
for ordinal,row in enumerate(rows):
 dest=out/row['id'];dest.mkdir(exist_ok=True)
 assert hashlib.sha256(Path(row['physicalSource']).read_bytes()).hexdigest()==row['sourceSha256']
 stages=[]
 for name in ['frontend','lower','dependency']:
  old=row['stages'][name];cmd=[x.replace(str(Path(row['output'])),str(dest)) for x in old['command']]
  cmd=[x.replace('-Xmx1536m','-Xmx768m') for x in cmd];cmd.insert(1,'-Xms32m')
  if name=='dependency' and len(sys.argv)>3:
   runtime=Path(sys.argv[3]).resolve()
   classpath=str(runtime/'analysis-launcher/target/classes')+':'+str(runtime/'analysis-launcher/target/dependency/*')
   cmd=[cmd[0],'-Xms32m','-Xmx768m','-DANALYZER_LOG_LEVEL=ERROR','-cp',classpath,
        'io.github.gustavo2358.analysis.launcher.AnalysisDependencies',str(dest/'dependency-input.json.zst'),str(dest/'dependencies.json.zst'),'--conservative-control']
  started=time.monotonic()
  try:
   with (dest/(name+'.stdout')).open('w') as stdout,(dest/(name+'.stderr')).open('w') as stderr:
    p=subprocess.run(['/usr/bin/time','-f','%e %M','-o',str(dest/(name+'.time')),*cmd],cwd=old['cwd'] if name=='frontend' else dest,stdout=stdout,stderr=stderr,timeout=90)
   code=p.returncode
  except subprocess.TimeoutExpired:code='TIMEOUT'
  raw=(dest/(name+'.time')).read_text();numbers=raw.strip().splitlines()[-1].split();rss=int(numbers[1]) if len(numbers)==2 and numbers[1].isdigit() else None
  stages.append({'stage':name,'exit':code,'seconds':time.monotonic()-started,'peakRssKiB':rss,'command':cmd})
  if code!=0:break
 result={'id':row['id'],'source':row['path'],'sourceSha256':row['sourceSha256'],'stages':stages,'complete':len(stages)==3 and all(s['exit']==0 for s in stages)}
 results.append(result);(out/'results.json').write_text(json.dumps(results,indent=2)+'\n')
 print(ordinal+1,'/73',row['path'],result['complete'],[(s['stage'],s['exit'],round(s['seconds'],2)) for s in stages],flush=True)
(out/'summary.json').write_text(json.dumps({'sources':len(results),'complete':sum(r['complete'] for r in results),'seconds':sum(s['seconds'] for r in results for s in r['stages']),'peakRssKiB':max(s['peakRssKiB'] or 0 for r in results for s in r['stages'])},indent=2)+'\n')

sys.exit(0 if all(r["complete"] for r in results) and len(results)==73 else 1)
