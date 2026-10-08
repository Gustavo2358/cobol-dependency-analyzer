#!/usr/bin/env python3
"""Compare all categories against another reference publication for the same IDs.
Usage: BASELINE_RESULTS MONOLITH_OUTPUT_DIR REFERENCE_OUTPUT_DIR REPORT_JSON
"""
import json, sys, hashlib
from pathlib import Path
from normalize import load, reference, product

manifest=Path(sys.argv[1]); rows=json.loads(manifest.read_text())
assert len(rows)==73
results=[]
for row in rows:
    old=Path(sys.argv[3])/row['id']/'dependencies.json'
    if not old.exists(): old=old.with_suffix('.json.zst')
    new=Path(sys.argv[2])/row['id']/'dependencies.json'
    document=load(new); programs=document if isinstance(document,list) else [document]
    assert {p['program'] for p in programs}=={row['programName']}, 'Wrong originating program'
    a=reference(load(old)); b=product(document)
    results.append({'id':row['id'],'program':row['programName'],'source':row['path'],
        'sourceSha256':row['sourceSha256'],'referenceSha256':hashlib.sha256(old.read_bytes()).hexdigest(),
        'productSha256':hashlib.sha256(new.read_bytes()).hexdigest(),
        'equal':sorted(a&b),'missing':sorted(a-b),'additional':sorted(b-a)})
summary={'sources':len(results),'equal':sum(len(r['equal']) for r in results),
    'missing':sum(len(r['missing']) for r in results),'additional':sum(len(r['additional']) for r in results)}
Path(sys.argv[4]).write_text(json.dumps({'summary':summary,'results':results},indent=2)+'\n')
print(summary)
sys.exit(0 if summary['missing']==summary['additional']==0 else 1)
