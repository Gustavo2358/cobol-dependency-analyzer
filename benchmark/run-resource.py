#!/usr/bin/env python3
"""Run the imported context/memory sources in individual JVMs.
Usage: run-resource.py OUTPUT_DIR [--heap MiB] [--max-work N] [--timeout SECONDS]
"""
import argparse, hashlib, json, subprocess, sys, time, shutil
from pathlib import Path
from normalize import load, product
root=Path(__file__).resolve().parents[1]
fixtures=root/'src/test/resources/dependency-regression/resource-stress'
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('output')
parser.add_argument('--heap',type=int,default=512)
parser.add_argument('--max-work',type=int,default=1000000)
parser.add_argument('--timeout',type=int,default=30)
args=parser.parse_args()
assert args.heap>0 and args.max_work>0 and args.timeout>0
out=Path(args.output); out.mkdir(parents=True,exist_ok=True)
java='/home/gustavo/.sdkman/candidates/java/21.0.12+1.1-tem/bin/java'
if not Path(java).exists(): java=shutil.which('java')
results=[]
for case in load(fixtures/'expected.json'):
    dest=out/case['id']; dest.mkdir(exist_ok=True)
    target=dest/'dependencies.json'
    if target.exists(): target.unlink()
    assert hashlib.sha256((fixtures/case['source']).read_bytes()).hexdigest()==case['sha256']
    command=[java,'-Xms32m',f'-Xmx{args.heap}m','-jar',str(root/'target/cobol-dependency-analyzer.jar'),
        '--source',str(fixtures/case['source']),'--output',str(target),'--metrics',str(dest/'metrics.jsonl'),
        '--max-work',str(args.max_work)]
    start=time.monotonic()
    try:
        with (dest/'stdout.log').open('w') as stdout,(dest/'stderr.log').open('w') as stderr:
            proc=subprocess.Popen(['/usr/bin/time','-f','%e %M','-o',str(dest/'time.txt'),*command],stdout=stdout,stderr=stderr,start_new_session=True)
            try: code=proc.wait(timeout=args.timeout)
            except subprocess.TimeoutExpired:
                import os,signal
                os.killpg(proc.pid,signal.SIGKILL);proc.wait();code='TIMEOUT'
    except OSError as failure: raise RuntimeError('Unable to execute case '+case['id']) from failure
    elapsed=time.monotonic()-start
    if 'expectedFailure' in case:
        passed=code==2 and not target.exists() and case['expectedFailure'] in (dest/'stderr.log').read_text()
    else:
        actual={name for kind,name in product(load(target))} if code in (0,1) else set()
        passed=code in (0,1) and actual==set(case['expected'])
    timing=(dest/'time.txt').read_text().strip().splitlines()
    rss=int(timing[-1].split()[-1]) if timing and timing[-1].split()[-1].isdigit() else None
    results.append({'id':case['id'],'exit':code,'passed':passed,'seconds':elapsed,'peakRssKiB':rss,'sourceSha256':case['sha256'],'command':command})
    print(case['id'],code,passed,round(elapsed,3),rss,flush=True)
summary={'cases':len(results),'passed':sum(r['passed'] for r in results),'failed':sum(not r['passed'] for r in results),'seconds':sum(r['seconds'] for r in results),'peakRssKiB':max(r['peakRssKiB'] or 0 for r in results),'heapMiB':args.heap,'maxWork':args.max_work,'timeoutSeconds':args.timeout}
(out/'results.json').write_text(json.dumps({'summary':summary,'results':results},indent=2)+'\n'); print(summary)
sys.exit(0 if all(r['passed'] for r in results) else 1)
