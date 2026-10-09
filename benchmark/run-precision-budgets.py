#!/usr/bin/env python3
"""Guarded differential experiments; semantic differences are evidence, not PASS."""
import argparse, hashlib, importlib.util, json, os
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def module(path,name):
    spec=importlib.util.spec_from_file_location(name,path);m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m);return m
def names(document):
    if document is None:return []
    programs=document if isinstance(document,list) else [document]
    return sorted({d['name'] for p in programs for d in p['dependencies'] if d['type']=='program'})
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('output',type=Path);p.add_argument('--jar',type=Path,required=True);p.add_argument('--overlay',type=Path,required=True);p.add_argument('--cases',nargs='+',required=True);p.add_argument('--variants',nargs='+',default=['disabled','depth4','context0','scope0']);p.add_argument('--timeout',type=int,default=45);p.add_argument('--allow-failed-baseline',action='store_true');a=p.parse_args()
    a.output=a.output.resolve();a.output.mkdir(parents=True,exist_ok=False);a.jar=a.jar.resolve();classes=a.overlay.resolve()/'classes'
    probe=module(ROOT/'benchmark/profiling/precision-budget-probe.py','probe');gen=module(ROOT/'benchmark/generate-hub-dispatch.py','gen')
    fixed=json.loads((ROOT/'benchmark/fixtures/precision-budget/expected.json').read_text());fixed={x['id']:x for x in fixed};rows=[];inputs=a.output/'inputs';inputs.mkdir()
    variants={'disabled':{},**{'depth'+str(k):{'callDepth':k} for k in [0,1,4,8,16,32,64]},**{'context'+str(k):{'contextLimit':k} for k in [0,1,2,4,8,16]},**{'scope'+str(k):{'scopeLimit':k} for k in [0,1,4,16]},**{'scope'+str(k)+'-safe':{'scopeLimit':k,'conservativeStops':'true'} for k in [0,1,4,16]},'scope0-context0':{'scopeLimit':0,'contextLimit':0}}
    variants['physical']={'scopeLimit':0,'conservativeStops':'true','physicalOnly':'true'}
    for case in a.cases:
        if case.startswith('fanout-') or case.startswith('dynamic-') or case.startswith('fixed-'):
            n=int(case.split('-')[1]);assert n<=384
            mode='return-fanout-fixed' if case.startswith('fixed-') else 'return-fanout';text=gen.source(n,2,mode)
            if case.startswith('dynamic-'):
                for b in range(n):text=text.replace(f"    CALL 'PGM{b:05d}'.",f"    CALL TARGET-PGM.\n           MOVE 'PGM{b:05d}' TO TARGET-PGM.\n           CALL 'PGM{b:05d}'.")
            expected=[f'PGM{b:05d}' for b in range(n)]+(['BOOT0000'] if case.startswith('dynamic-') else []);unknown=case.startswith('dynamic-')
        elif case=='escape-sentinel':
            text=(ROOT/'benchmark/fixtures/control-return-fanout/escape-ablation-sentinel.cbl').read_text();expected=['AFTER','ESCAPED'];unknown=False
        elif case=='fixture02':
            text=(ROOT/'src/test/resources/dependency-regression/video-reconstructed/fixture02.fixed.cbl').read_text();expected=None;unknown=None
        else:
            c=fixed[case];source=ROOT/'benchmark/fixtures/precision-budget'/c['source'];assert hashlib.sha256(source.read_bytes()).hexdigest()==c['sha256'];text=source.read_text();expected=c['expected'];unknown=c['expectedUnknown']
        source=inputs/(case+'.cbl');source.write_text(text)
        baseline=None
        for variant in ['baseline']+a.variants:
            properties={} if variant=='baseline' else variants[variant]
            r=probe.run(None if variant=='baseline' else classes,a.jar,source,a.output/case/variant,properties,a.timeout)
            actual=names(r['document']);dynamic=any('DYNAMIC_REMAINDER' in d for d in r['diagnostics'])
            r.update(case=case,variant=variant,actual=actual,unknown=dynamic)
            if variant=='baseline':
                baseline=r
                completed=r['exit'] in (0,1)
                assert completed or a.allow_failed_baseline,('Baseline failed',case,r['exit'],r['guard'])
                if completed:assert (expected is None or actual==sorted(expected)) and (unknown is None or dynamic==unknown),('Baseline oracle invalid',case,actual,dynamic)
            comparable=baseline['exit'] in (0,1) and r['exit'] in (0,1)
            r.update(comparable=comparable,fullJsonEqual=r['document']==baseline['document'] if comparable else None,diagnosticsEqual=r['diagnostics']==baseline['diagnostics'] if comparable else None,missing=sorted(set(baseline['actual'])-set(actual)) if comparable else None,additional=sorted(set(actual)-set(baseline['actual'])) if comparable else None)
            r['equivalentOnCase']=comparable and r['fullJsonEqual'] and r['diagnosticsEqual']
            r['oraclePass']=r['exit'] in (0,1) and expected is not None and actual==sorted(expected) and (unknown is None or dynamic==unknown)
            rows.append(r);(a.output/'results.json').write_text(json.dumps(rows,indent=2)+'\n')
            metric=r['metrics'][0] if r['metrics'] else {}
            print(case,variant,r['exit'],r['guard'],'UNCOMPARABLE' if not comparable else ('EQUAL' if r['equivalentOnCase'] else 'DIFF'),round(r['seconds'],3),round(r['peakRssKiB']/1024,1),'control',metric.get('controlSummaries'),'contexts',metric.get('contexts'),'work',metric.get('workItems'),'missing',(r['missing'] or [])[:8],'additional',(r['additional'] or [])[:8],r['probe'],flush=True)
    (a.output/'manifest.json').write_text(json.dumps({'jarSha256':hashlib.sha256(a.jar.read_bytes()).hexdigest(),'overlayManifestSha256':hashlib.sha256((a.overlay/'manifest.json').read_bytes()).hexdigest(),'qualifiedForProduction':False,'guards':{'heapMiB':512,'rssMiB':768,'reserveMiB':2048,'timeoutSeconds':a.timeout,'sequential':True}},indent=2)+'\n')
if __name__=='__main__':main()
