#!/usr/bin/env python3
"""Differential normalized sets. Reference artifacts are read-only.
Usage: python3 benchmark/run-carddemo.py BASELINE_RESULTS_JSON [OUTPUT_DIR]
"""
import argparse, json, subprocess, sys, time, hashlib, shutil
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('baseline',type=Path)
parser.add_argument('output',nargs='?',type=Path,default=ROOT/'benchmark/results/carddemo')
parser.add_argument('--heap',type=int,default=768)
parser.add_argument('--max-work',type=int,default=1000000)
parser.add_argument('--timeout',type=int,default=45)
args=parser.parse_args()
assert args.heap>0 and args.max_work>0 and args.timeout>0
rows=json.loads(args.baseline.read_text())
assert len(rows)==73, 'Expected full 73-source corpus'
input_hashes=args.baseline.parent/'input-hashes.json'
assert input_hashes.exists(), 'Include hashes required'
for source,digest in json.loads(input_hashes.read_text()).items():
 assert hashlib.sha256(Path(source).read_bytes()).hexdigest()==digest, 'Source/include changed: '+source
out=args.output
out.mkdir(parents=True,exist_ok=True)
JAVA='/home/gustavo/.sdkman/candidates/java/21.0.12+1.1-tem/bin/java'
if not Path(JAVA).exists(): JAVA=shutil.which('java')
from normalize import load, reference, product
results=[]
for i,row in enumerate(rows):
 dest=out/row['id'];dest.mkdir(exist_ok=True)
 assert hashlib.sha256(Path(row['physicalSource']).read_bytes()).hexdigest()==row['sourceSha256'], 'Source changed'
 command=[JAVA,'-Xms32m',f'-Xmx{args.heap}m','-jar',str(ROOT/'target/cobol-dependency-analyzer.jar'),'--source',row['physicalSource'],'--output',str(dest/'dependencies.json'),'--metrics',str(dest/'metrics.jsonl'),'--max-work',str(args.max_work)]
 for copy in row['copyRoots']:command+=['--copy-dir',copy]
 start=time.monotonic()
 try:
  with (dest/'stdout.log').open('w') as stdout,(dest/'stderr.log').open('w') as stderr:
   completed=subprocess.run(['/usr/bin/time','-f','%e %M','-o',str(dest/'time.txt'),*command],stdout=stdout,stderr=stderr,timeout=args.timeout)
  code=completed.returncode
 except subprocess.TimeoutExpired:code='TIMEOUT'
 elapsed=time.monotonic()-start
 old=reference(load(Path(row['output'])/'dependencies.json.zst'))
 new=set()
 if code in (0,1):
  document=load(dest/'dependencies.json');programs=document if isinstance(document,list) else [document]
  assert {p['program'] for p in programs}=={row['programName']}, 'Wrong originating program'
  for p in programs:new.update((x['type'],x['name']) for x in p['dependencies'])
 result={'id':row['id'],'source':row['path'],'program':row.get('programName'), 'sourceSha256':row['sourceSha256'],'exit':code,'seconds':elapsed,'equal':sorted(old&new),'missing':sorted(old-new),'additional':sorted(new-old),'command':command}
 if (dest/'time.txt').exists():
  result['time']=(dest/'time.txt').read_text()
  result['peakRssKiB']=int(result['time'].strip().splitlines()[-1].split()[-1])
 results.append(result);(out/'results.json').write_text(json.dumps(results,indent=2)+'\n')
 print(i+1,'/73',row['path'],code,'equal',len(old&new),'missing',sorted(old-new),'additional',sorted(new-old),flush=True)
summary={'sources':len(results),'processed':sum(r['exit'] in (0,1) for r in results),'equal':sum(len(r['equal']) for r in results),'missing':sum(len(r['missing']) for r in results),'additional':sum(len(r['additional']) for r in results),'seconds':sum(r['seconds'] for r in results),'peakRssKiB':max(r.get('peakRssKiB',0) for r in results),'heapMiB':args.heap,'maxWork':args.max_work,'timeoutSeconds':args.timeout,'baselineReused':str(args.baseline.resolve())}
(out/'summary.json').write_text(json.dumps(summary,indent=2)+'\n');print(summary,flush=True)

sys.exit(0 if summary["processed"]==73 and summary["missing"]==0 and summary["additional"]==0 else 1)
