#!/usr/bin/env python3
"""Pilot adversaries through the real four-stage runtime.
Pass analysis-cfg/scripts/project/e2e_perform_completion.py as --runner.
Raw stage products, provenance, supports and process logs stay in --out.
"""
import argparse, importlib.util, json
from pathlib import Path
p=argparse.ArgumentParser()
p.add_argument('--runner',type=Path,required=True)
p.add_argument('--runtime',type=Path,required=True)
p.add_argument('--out',type=Path,required=True)
p.add_argument('--baseline-runtime',type=Path)
p.add_argument('--java',default='java')
a=p.parse_args()
spec=importlib.util.spec_from_file_location('perform_runner',a.runner)
r=importlib.util.module_from_spec(spec);spec.loader.exec_module(r)
r.ROOT=Path(__file__).resolve().parents[2]/'src/test/resources/cobol/nominal-copybooks'
c=json.loads(a.runtime.read_text());a.out.mkdir(parents=True,exist_ok=False)
r.dump(a.out/'runtime.json',c);results=[]
for case in json.loads((r.ROOT/'expected.json').read_text())['cases']:
    row=r.run_case(case,c,a.out,180,a.java,'2g')
    if row['status']=='PASS' and 'xctl' in case:
        d=json.loads((a.out/case['id']/'dependencies.json').read_text())
        sites=[s for s in d['sites'] if s['command']=='XCTL']
        names={v['referenceName'] for s in sites for v in s['candidates']}
        if names!=set(case['xctl']) or not all(v.get('supports') for s in sites for v in s['candidates']):
            row['status']='FAIL';row['xctlFailure']='Candidates/supports do not match oracle'
    if case.get('sourceCandidates') and row.get('oracle'):
        # Preserve the historical executable oracle, including its expected failure.
        # The product obligation is now a source-qualified candidate with real support.
        doc=json.loads((a.out/case['id']/'dependencies.json').read_text())
        programs=doc['dependencies']['programs']
        candidates=[v for p in programs for v in p['candidates']]
        names={v['referenceName'] for v in candidates}
        supported=all(v.get('conditionalSupports') and any(
            e['kind']=='ASSIGNMENT' and e['provenance']['original']['file']==case['source']
            and e['provenance']['original']['startLine']==case['assignmentSupportLine']
            for support in v['conditionalSupports'] for e in support['evidence']) for v in candidates)
        legacy=row['oracle']['calls']['target']
        honest=(not row['oracle']['sourceMappingErrors'] and
            set(legacy['failures'])=={'MISSING_CANDIDATES','MISSING_EDGES'} and
            not legacy['actual'] and not legacy['actualEdges'] and legacy['activations'] and
            all(v['effectiveUnknownRemainder'] is True for v in legacy['activations']))
        row['sourceOracle']={'required':case['sourceCandidates'],'actual':sorted(names),'supported':supported,'executionUnproven':bool(honest)}
        row['status']='PASS' if names==set(case['sourceCandidates']) and supported and honest else 'FAIL'
    if row['status']=='FAIL' and case.get('knownLimitation') and not case.get('sourceCandidates') and a.baseline_runtime:
        baseline=json.loads(a.baseline_runtime.read_text());baseout=a.out/'baseline';baseout.mkdir(exist_ok=True)
        before=r.run_case(case,baseline,baseout,180,a.java,'2g')
        row['baseline']=before
        def unavailable(x):
            if x.get('status')!='FAIL' or x.get('oracle',{}).get('sourceMappingErrors'):return False
            call=x['oracle']['calls']['target']
            return (set(call['failures'])=={'MISSING_CANDIDATES','MISSING_EDGES'}
                and not call['actual'] and not call['actualEdges'] and bool(call['activations'])
                and all(v['targetStatus']=='UNSUPPORTED_TARGET_EXPRESSION'
                    and v['effectiveUnknownRemainder'] is True for v in call['activations']))
        if unavailable(row) and unavailable(before):
            row['status']='KNOWN_LIMITATION';row['limitation']=case['knownLimitation']
        assert baseline['artifactHashes']=={path:r.sha(Path(path)) for path in baseline['artifactHashes']}
    results.append(row)
    r.dump(a.out/'results.json',{'runs':results});print(row['id'],row['status'],flush=True)
assert c['artifactHashes']=={path:r.sha(Path(path)) for path in c['artifactHashes']}
raise SystemExit(0 if all(row['status'] in ('PASS','KNOWN_LIMITATION') for row in results) else 1)
