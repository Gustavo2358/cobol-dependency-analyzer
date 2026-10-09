#!/usr/bin/env python3
"""Check actual dependencies and source locations, never output-existence alone."""
import argparse,json
from pathlib import Path
def main():
 p=argparse.ArgumentParser();p.add_argument('directories',type=Path,nargs='+');a=p.parse_args();report=[];failures=[]
 for directory in a.directories:
  rows=json.loads((directory/'results.json').read_text());groups={};summary=dict(directory=str(directory),runs=len(rows),completed=0,unsupported=0,resourceFailures=[],lost=0,extra=0,changedAt=0,pairedJsonChecks=0)
  for r in rows:
   groups.setdefault((r['case'],r['repeat']),{})[r['mode']]=r
   if r['unsupported']:summary['unsupported']+=1;continue
   if not r['completed']:
    summary['resourceFailures'].append(dict(case=r['case'],mode=r['mode'],exit=r['exit'],guard=r['guard']));continue
   summary['completed']+=1
   for key in ('lost','extra','changedAt'):summary[key]+=len(r.get(key,[]))
   if r.get('lost'):failures.append([directory.name,r['case'],r['mode'],'lost',r['lost']])
   if 'expectedNamesEqual' in r and not r['expectedNamesEqual']:failures.append([directory.name,r['case'],r['mode'],'synthetic oracle mismatch'])
  for (case,repeat),g in groups.items():
   full=g.get('full',g.get('stop-no-cache'));stop=g.get('stop')
   if full and stop and full['completed'] and stop['completed']:
    summary['pairedJsonChecks']+=1
    if full['document']!=stop['document']:failures.append([directory.name,case,'pruning changed JSON'])
  report.append(summary)
 print(json.dumps(dict(reports=report,failures=failures,qualification='research only; unsupported cases are NOT passes'),indent=2))
 return bool(failures)
if __name__=='__main__':raise SystemExit(main())
