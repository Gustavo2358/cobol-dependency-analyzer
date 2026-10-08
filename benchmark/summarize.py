#!/usr/bin/env python3
"""Summarize preserved raw runs; no changes to sources, outputs or oracles.
Usage: MONOLITH_RUN REFERENCE_RUN RESOURCE_RUN REPORT_JSON
"""
import json, sys
from pathlib import Path
from collections import Counter

new=Path(sys.argv[1]); old=Path(sys.argv[2]); resource=Path(sys.argv[3])
def read(p): return json.loads(p.read_text())
rows=read(new/'results.json'); ref=read(old/'results.json')
phases=Counter(); categories=Counter(); sources=[]
for row in rows:
    metrics={}; publication=0
    for line in (new/row['id']/'metrics.jsonl').read_text().splitlines():
        item=json.loads(line)
        if 'metrics' in item: metrics=item['metrics']
        else: publication+=item.get('publicationNanos',0)
    for key in ['parseNanos','bindingNanos','cfgNanos','dataflowNanos','resolutionNanos']: phases[key]+=metrics[key]/1e9
    phases['publicationNanos']+=publication/1e9
    categories.update(kind for kind,name in row['equal'])
    sources.append({key:row[key] for key in ['id','program','source','sourceSha256','exit','seconds','peakRssKiB','equal','missing','additional']}|{'metrics':metrics,'publicationNanos':publication})
referencePhases={stage:sum(s['seconds'] for r in ref for s in r['stages'] if s['stage']==stage) for stage in ['frontend','lower','dependency']}
report={'monolith':read(new/'summary.json'),'reference':read(old/'summary.json'),
    'monolithPhaseSeconds':dict(phases),'referenceStageSeconds':referencePhases,
    'categories':dict(categories),'resource':read(resource/'results.json')['summary'],'sources':sources,
    'rawDirectories':{'monolith':str(new),'reference':str(old),'resource':str(resource)}}
report['uniqueProgramNames']=len({r['program'] for r in rows})
report['uniqueTriples']=len({(r['program'],kind,name) for r in rows for kind,name in r['equal']})
report['speedup']=report['reference']['seconds']/report['monolith']['seconds']
report['rssReductionPercent']=100*(1-report['monolith']['peakRssKiB']/report['reference']['peakRssKiB'])
Path(sys.argv[4]).write_text(json.dumps(report,indent=2)+'\n')
print({k:v for k,v in report.items() if k not in ['sources']})
