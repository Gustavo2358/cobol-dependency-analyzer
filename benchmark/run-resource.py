#!/usr/bin/env python3
"""Run the imported context/memory sources in individual JVMs, with a 512 MiB heap."""
import json, subprocess, sys, time, shutil
from pathlib import Path
from normalize import load, product
root=Path(__file__).resolve().parents[1]
fixtures=root/'src/test/resources/dependency-regression/resource-stress'
out=Path(sys.argv[1]); out.mkdir(parents=True,exist_ok=True)
java='/home/gustavo/.sdkman/candidates/java/21.0.12+1.1-tem/bin/java'
if not Path(java).exists(): java=shutil.which('java')
results=[]
for case in load(fixtures/'expected.json'):
    dest=out/case['id']; dest.mkdir(exist_ok=True)
    target=dest/'dependencies.json'
    if target.exists(): target.unlink()
    command=[java,'-Xms32m','-Xmx512m','-jar',str(root/'target/cobol-dependency-analyzer.jar'),
        '--source',str(fixtures/case['source']),'--output',str(target),'--metrics',str(dest/'metrics.jsonl')]
    start=time.monotonic()
    try:
        with (dest/'stdout.log').open('w') as stdout,(dest/'stderr.log').open('w') as stderr:
            code=subprocess.run(['/usr/bin/time','-f','%e %M','-o',str(dest/'time.txt'),*command],stdout=stdout,stderr=stderr,timeout=30).returncode
    except subprocess.TimeoutExpired: code='TIMEOUT'
    elapsed=time.monotonic()-start
    if 'expectedFailure' in case:
        passed=code==2 and not target.exists() and case['expectedFailure'] in (dest/'stderr.log').read_text()
    else:
        actual={name for kind,name in product(load(target))} if code in (0,1) else set()
        passed=code in (0,1) and actual==set(case['expected'])
    rss=int((dest/'time.txt').read_text().strip().splitlines()[-1].split()[-1])
    results.append({'id':case['id'],'exit':code,'passed':passed,'seconds':elapsed,'peakRssKiB':rss,'sourceSha256':case['sha256'],'command':command})
    print(case['id'],code,passed,round(elapsed,3),rss,flush=True)
summary={'cases':len(results),'passed':sum(r['passed'] for r in results),'failed':sum(not r['passed'] for r in results),'seconds':sum(r['seconds'] for r in results),'peakRssKiB':max(r['peakRssKiB'] for r in results),'heapMiB':512}
(out/'results.json').write_text(json.dumps({'summary':summary,'results':results},indent=2)+'\n'); print(summary)
sys.exit(0 if all(r['passed'] for r in results) else 1)
