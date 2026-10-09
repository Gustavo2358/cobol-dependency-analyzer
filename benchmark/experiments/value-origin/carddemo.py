#!/usr/bin/env python3
"""Compare the experimental overlay with an unchanged frozen 73-source baseline."""
import argparse,hashlib,importlib.util,json,os,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[3]
sys.path.insert(0,str(ROOT/'benchmark'))
from normalize import load,product

def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('output',type=Path);p.add_argument('--jar',type=Path,required=True);p.add_argument('--overlay',type=Path,required=True);p.add_argument('--baseline',type=Path,default=ROOT/'benchmark/results/remote-requalification-20261009/carddemo/results.json');p.add_argument('--ids',nargs='+');p.add_argument('--diagnostic',action='store_true');a=p.parse_args();a.output=a.output.resolve();a.output.mkdir(parents=True,exist_ok=False)
 baseline=json.loads(a.baseline.read_text());assert len(baseline)==73;inputs=json.loads((a.baseline.parent/'input-hashes.json').read_text());assert all(sha(Path(path))==digest for path,digest in inputs.items());jar=a.jar.resolve();overlay=a.overlay.resolve();metadata=json.loads((overlay/'manifest.json').read_text());assert sha(jar)==metadata['jarSha256'];assert metadata['exit']==0
 spec=importlib.util.spec_from_file_location('guard',ROOT/'benchmark/run-sparse-occurs.py');guard=importlib.util.module_from_spec(spec);spec.loader.exec_module(guard);rows=[]
 for old in baseline:
  if a.ids and old['id'] not in a.ids:continue
  ident=old['id'];dest=a.output/ident;cmd=old['command'];source=Path(cmd[cmd.index('--source')+1]);assert sha(source)==old['sourceSha256']
  command=[str(Path(os.environ['JAVA_HOME'])/'bin/java'),'-Xms16m','-Xmx512m','-XX:MaxMetaspaceSize=128m','-XX:MaxDirectMemorySize=32m','-XX:+ExitOnOutOfMemoryError','-cp',str(overlay/'classes')+os.pathsep+str(jar),'com.imd.cobolexplorer.DependencyMain','--source',str(source),'--output',str(dest/'dependencies.json'),'--metrics',str(dest/'metrics.jsonl'),'--max-work','100000000']
  if a.diagnostic:
   command.remove('-XX:+ExitOnOutOfMemoryError');command.insert(5,'-XX:StartFlightRecording=settings=profile,maxsize=16m,dumponexit=true,filename='+str(dest/'profile.jfr'))
  for i,option in enumerate(cmd):
   if option=='--copy-dir':command.extend([option,cmd[i+1]])
  r=guard.execute(command,dest,timeout=60,rss_mib=768,telemetry_period=1,min_available_mib=2048);path=dest/'dependencies.json';document=load(path) if path.exists() else None;expected=product(load(a.baseline.parent/ident/'dependencies.json'));actual=product(document) if document else set();completed=document is not None and r['exit'] in (0,1) and r['guard'] is None;stderr=(dest/'stderr.log').read_text();probes=[json.loads(s.split(' ',1)[1]) for s in stderr.splitlines() if s.startswith('ORIGIN_PROBE ')]
  r.update(id=ident,source=old['source'],sourceSha256=sha(source),completed=completed,equal=sorted(actual&expected),missing=sorted(expected-actual) if completed else None,additional=sorted(actual-expected) if completed else None,fullJsonEqual=document==load(a.baseline.parent/ident/'dependencies.json') if completed else None,diagnostics=guard.diagnostics(dest/'stderr.log'),origin=probes,baselineOutputSha256=sha(a.baseline.parent/ident/'dependencies.json'),outputSha256=sha(path) if path.exists() else None,productionQualified=False)
  r['verdict']='INCOMPLETE' if not completed else 'MISSING' if r['missing'] else 'ADDITIONAL' if r['additional'] else 'EXACT' if r['fullJsonEqual'] else 'CANDIDATES_MATCH';rows.append(r);(a.output/'results.json').write_text(json.dumps(rows,indent=2)+'\n')
  print(len(rows),'/73',old['source'],r['verdict'],r['guard'],round(r['seconds'],3),'missing',len(r['missing'] or []),'additional',len(r['additional'] or []),flush=True)
  if not completed:break # Investigate the first resource/semantic failure before continuing.
 assert all(sha(Path(path))==digest for path,digest in inputs.items())
 summary={'sources':len(rows),'completed':sum(r['completed'] for r in rows),'incomplete':sum(not r['completed'] for r in rows),'sourcesMissingCandidates':sum(bool(r['missing']) for r in rows),'equal':sum(len(r['equal']) for r in rows if r['completed']),'missing':sum(len(r['missing'] or []) for r in rows),'additional':sum(len(r['additional'] or []) for r in rows),'peakRssMiB':max(r['peakRssKiB'] for r in rows)/1024,'seconds':sum(r['seconds'] for r in rows),'heapMiB':512,'rssGuardMiB':768,'hostReserveMiB':2048,'baselineRun':str(a.baseline),'baselineReused':True,'jarSha256':sha(jar),'overlayManifestSha256':sha(overlay/'manifest.json'),'inputHashesUnchanged':len(inputs),'productionQualified':False}
 (a.output/'summary.json').write_text(json.dumps(summary,indent=2)+'\n');print(summary,flush=True)
if __name__=='__main__':main()
