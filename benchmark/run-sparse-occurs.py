#!/usr/bin/env python3
"""Sequential, RSS-bounded CLI measurements of the sparse OCCURS adversaries."""
import argparse,hashlib,json,os,shutil,signal,subprocess,time
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def execute(cmd,dest,timeout=45,rss_mib=640):
 dest.mkdir(parents=True,exist_ok=False)
 with (dest/'stdout.log').open('w') as stdout,(dest/'stderr.log').open('w') as stderr:
  proc=subprocess.Popen(['/usr/bin/time','-f','%e %M','-o',str(dest/'time.txt'),*cmd],cwd=ROOT,stdout=stdout,stderr=stderr,start_new_session=True)
  start=time.monotonic();reason=None;peak=0
  while proc.poll() is None:
   pending=[proc.pid];rss=0
   while pending:
    pid=pending.pop()
    try:
     pending.extend(map(int,Path(f'/proc/{pid}/task/{pid}/children').read_text().split()))
     fields=[l.split() for l in Path(f'/proc/{pid}/status').read_text().splitlines() if l.startswith('VmRSS:')]
     rss+=int(fields[0][1]) if fields else 0
    except (FileNotFoundError,ProcessLookupError):pass
   peak=max(peak,rss)
   if rss>rss_mib*1024:reason='RSS_GUARD'
   if time.monotonic()-start>timeout:reason='TIME_GUARD'
   if reason:os.killpg(proc.pid,signal.SIGKILL);proc.wait();break
   time.sleep(.05)
 return dict(exit=proc.returncode,guard=reason,seconds=time.monotonic()-start,peakRssKiB=peak,command=cmd)
def diagnostics(p):return [s for s in p.read_text().splitlines() if ': ' in s and not s.startswith(('Picked up ','totalMs='))]
def main():
 parser=argparse.ArgumentParser(description=__doc__)
 parser.add_argument('output',type=Path);parser.add_argument('--jar',type=Path,default=ROOT/'target/cobol-dependency-analyzer.jar');parser.add_argument('--repeats',type=int,default=3);parser.add_argument('--case',action='append');parser.add_argument('--max-work',type=int,default=10000)
 args=parser.parse_args();args.output.mkdir(parents=True,exist_ok=False)
 fixtures=ROOT/'src/test/resources/dependency-regression/sparse-occurs';cases=json.loads((fixtures/'expected.json').read_text());rows=[]
 java=shutil.which('java');jar=args.jar.resolve()
 for case in cases:
  if args.case and case['id'] not in args.case:continue
  source=fixtures/case['source'];assert sha(source)==case['sha256']
  for repeat in range(1,args.repeats+1):
   dest=args.output/case['id']/f'run{repeat}';output=dest/'dependencies.json'
   cmd=[java,'-Xms16m','-Xmx512m','-XX:MaxMetaspaceSize=256m','-XX:MaxDirectMemorySize=64m','-XX:+ExitOnOutOfMemoryError','-jar',str(jar),'--source',str(source),'--output',str(output),'--metrics',str(dest/'metrics.jsonl'),'--max-work',str(args.max_work)]
   row=execute(cmd,dest);shutil.copyfile(source,dest/'input.cbl');row.update(id=case['id'],repeat=repeat,sourceSha256=sha(source),jarSha256=sha(jar),expected=case['expected'])
   row['actual']=sorted(d['name'] for d in json.loads(output.read_text())['dependencies'] if d['type']=='program') if row['exit'] in (0,1) else []
   row['diagnostics']=diagnostics(dest/'stderr.log');row['unknown']=any('DYNAMIC_REMAINDER' in s for s in row['diagnostics'])
   row['passed']=row['exit'] in (0,1) and row['actual']==case['expected'] and row['unknown']==case['unknown']
   ms=[json.loads(s) for s in (dest/'metrics.jsonl').read_text().splitlines()] if (dest/'metrics.jsonl').exists() else []
   row['metrics']=next((m['metrics'] for m in ms if 'metrics' in m),None)
   row['outputSha256']=sha(output) if output.exists() else None
   rows.append(row);(args.output/'results.json').write_text(json.dumps(rows,indent=2)+'\n')
   print(case['id'],repeat,row['exit'],row['guard'],'PASS' if row['passed'] else 'RED',round(row['seconds'],2),round(row['peakRssKiB']/1024,1),'tracked',row['metrics']['trackedDeclarations'] if row['metrics'] else None,row['actual'],row['unknown'],flush=True)
 return 0 if all(r['passed'] for r in rows) else 1
if __name__=='__main__':raise SystemExit(main())
