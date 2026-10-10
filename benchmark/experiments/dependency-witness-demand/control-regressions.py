#!/usr/bin/env python3
"""Independent dependency-name oracles: frozen main, before and after.

One JVM at a time, 128 MiB heap, and the common host/RSS/time guards.
Explicit source-level name oracles are checked on main and the corrected build.
Old build mismatches are recorded as discovery evidence, not hidden failures.
"""
import argparse, hashlib, json, os
from pathlib import Path
from importlib.util import spec_from_file_location, module_from_spec

ROOT=Path(__file__).resolve().parents[3]
def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('output',type=Path)
    parser.add_argument('--jar',type=Path,required=True)
    parser.add_argument('--before',type=Path,required=True)
    parser.add_argument('--after',type=Path,required=True)
    parser.add_argument('--fixtures',type=Path,default=Path(__file__).with_name('control-fixtures'))
    parser.add_argument('--only',nargs='+')
    args=parser.parse_args();args.output.mkdir(parents=True,exist_ok=False)
    spec=spec_from_file_location('bounded',ROOT/'benchmark/run-sparse-occurs.py')
    bounded=module_from_spec(spec);spec.loader.exec_module(bounded)
    fixtures=args.fixtures
    rows=[]
    for oracle in json.loads((fixtures/'expected.json').read_text()):
        if args.only and oracle['id'] not in args.only:continue
        source=fixtures/(oracle['id']+'.cbl')
        if 'sha256' in oracle:assert hashlib.sha256(source.read_bytes()).hexdigest()==oracle['sha256']
        for mode,build in [('main',None),('before',args.before),('after',args.after)]:
            dest=args.output/oracle['id']/mode;out=dest/'dependencies.json'
            cmd=[str(Path(os.environ['JAVA_HOME'])/'bin/java'),'-Xms16m','-Xmx128m',
                 '-XX:MaxMetaspaceSize=128m','-XX:MaxDirectMemorySize=32m',
                 '-XX:ActiveProcessorCount=2','-XX:+UseSerialGC','-XX:+ExitOnOutOfMemoryError']
            if build is None:cmd+=['-jar',str(args.jar.resolve())]
            else:cmd+=['-cp',str(build.resolve()/'classes')+os.pathsep+str(args.jar.resolve()),'com.imd.cobolexplorer.DependencyMain']
            cmd+=['--source',str(source.resolve()),'--output',str(out.resolve()),'--max-work','100000000']
            row=bounded.execute(cmd,dest,timeout=60,rss_mib=768,telemetry_period=1,min_available_mib=2048)
            document=json.loads(out.read_text()) if out.exists() else []
            if isinstance(document,dict):document=[document]
            names=sorted({d['name'] for p in document for d in p['dependencies'] if d['type']=='program'})
            expected=oracle.get('mainProgramNames',oracle['programNames']) if mode=='main' else oracle['programNames']
            row.update(case=oracle['id'],mode=mode,sourceSha256=hashlib.sha256(source.read_bytes()).hexdigest(),
                       expected=expected,actual=names,
                       passed=row['guard'] is None and row['exit'] in (0,1) and out.exists() and names==expected)
            row['tableProbe']=[json.loads(line.removeprefix('DEFINITIONS_TABLES ')) for line in (dest/'stderr.log').read_text().splitlines() if line.startswith('DEFINITIONS_TABLES ')]
            rows.append(row);(args.output/'results.json').write_text(json.dumps(rows,indent=2)+'\n')
            print(oracle['id'],mode,row['passed'],names,flush=True)
            if row['guard'] in ('SYSTEM_MEMORY_GUARD','RSS_GUARD'):return 1
    return any(not r['passed'] for r in rows if r['mode']!='before')
if __name__=='__main__':raise SystemExit(main())
