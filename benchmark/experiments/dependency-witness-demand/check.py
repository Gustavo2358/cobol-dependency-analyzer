#!/usr/bin/env python3
"""Check real dependency preservation and report approximation/resource limits."""
import argparse,json
from pathlib import Path
def main():
 p=argparse.ArgumentParser();p.add_argument('directories',type=Path,nargs='+');a=p.parse_args();report=[];failures=[]
 for directory in a.directories:
  rows=json.loads((directory/'results.json').read_text());summary=dict(directory=str(directory),runs=len(rows),completed=0,resourceFailures=[],lost=0,extra=0,changedAt=0)
  for r in rows:
   if not r['completed']:
    summary['resourceFailures'].append(dict(case=r['case'],mode=r['mode'],exit=r['exit'],guard=r['guard']))
    if r['mode']=='definitions':failures.append([directory.name,r['case'],'experiment did not complete'])
    continue
   summary['completed']+=1
   for key in ('lost','extra','changedAt'):summary[key]+=len(r.get(key,[]))
   if r.get('lost'):failures.append([directory.name,r['case'],r['mode'],'lost',r['lost']])
   if 'expectedExtra' in r and sorted(r.get('extra',[]))!=sorted(r['expectedExtra']):failures.append([directory.name,r['case'],'unexpected extras'])
   if r.get('unsupported'):failures.append([directory.name,r['case'],'unsupported statement admission'])
   if 'expectedNamesEqual' in r and not r['expectedNamesEqual']:failures.append([directory.name,r['case'],'synthetic oracle mismatch'])
  cases={r['case']:r for r in rows if r['mode']=='definitions' and r['completed']}
  summary['identityMetamorphicChecks']=0
  for name,changed in cases.items():
   if not name.startswith('rd-noops-'):continue
   original=cases.get(name.removeprefix('rd-noops-'))
   if original is None:continue
   def names(row):
    doc=row['document'];return {(p['program'],d['type'],d['name']) for p in (doc if isinstance(doc,list) else [doc]) for d in p['dependencies']}
   summary['identityMetamorphicChecks']+=1
   if names(original)!=names(changed):failures.append([name,'CONTINUE changed dependencies'])
   before=original['probe'][0];after=changed['probe'][0]
   if after['definitionNodes']>before['definitionNodes']+1:failures.append([name,'identity nodes were materialized'])
  report.append(summary)
 print(json.dumps(dict(reports=report,failures=failures,qualification='packaged CLI validation; reaching definitions remains PARTIAL'),indent=2))
 return bool(failures)
if __name__=='__main__':raise SystemExit(main())
