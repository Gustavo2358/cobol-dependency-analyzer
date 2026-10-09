#!/usr/bin/env python3
"""Sequential diagnostic matrix, fixed 512 MiB heap and 640 MiB tree RSS guard."""
import argparse, hashlib, importlib.util, json, os, shutil
from pathlib import Path
spec_gen = importlib.util.spec_from_file_location("hub_generator", Path(__file__).with_name("generate-hub-dispatch.py"))
generator = importlib.util.module_from_spec(spec_gen); spec_gen.loader.exec_module(generator)
source = generator.source

ROOT = Path(__file__).resolve().parents[1]


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('output', type=Path)
    p.add_argument('--jar', type=Path, default=ROOT/'target/cobol-dependency-analyzer.jar')
    p.add_argument('--boxes', type=int, nargs='+', default=[8, 32, 128, 254])
    p.add_argument('--hubs', type=int, default=2)
    p.add_argument('--mode', nargs='+', choices=generator.MODES, default=['literal', 'value', 'flags', 'perform'])
    p.add_argument('--profile', action='store_true')
    p.add_argument('--heap', type=int, choices=(128, 256, 512), default=512)
    p.add_argument('--overlay', type=Path)
    p.add_argument('--timeout', type=int, default=35)
    a = p.parse_args()
    a.output = a.output.resolve(); a.output.mkdir(parents=True, exist_ok=False)
    spec = importlib.util.spec_from_file_location('bounded_runner', ROOT/'benchmark/run-sparse-occurs.py')
    runner = importlib.util.module_from_spec(spec); spec.loader.exec_module(runner)
    java = shutil.which('java'); jar = a.jar.resolve(); rows = []
    for boxes in a.boxes:
        for mode in a.mode:
            case = f'{mode}-{a.hubs}h-{boxes}b'; dest = a.output/case
            source_path = a.output/f'{case}.cbl'; source_path.write_text(source(boxes, a.hubs, mode))
            cmd = [java, '-Xms16m', f'-Xmx{a.heap}m', '-XX:MaxMetaspaceSize=128m',
                   '-XX:MaxDirectMemorySize=32m']
            if not a.profile: cmd.append('-XX:+ExitOnOutOfMemoryError')
            if a.profile:
                cmd.extend([f'-XX:StartFlightRecording=settings=profile,duration=10s,maxsize=24m,filename={dest}/profile.jfr',
                            f'-Xlog:gc:file={dest}/gc.log'])
            cmd.extend(['-cp', str(a.overlay.resolve())+os.pathsep+str(jar), 'com.imd.cobolexplorer.DependencyMain'] if a.overlay else ['-jar', str(jar)])
            cmd.extend(['--source', str(source_path), '--output', str(dest/'dependencies.json'),
                        '--metrics', str(dest/'metrics.jsonl'), '--max-work', '1000000'])
            row = runner.execute(cmd, dest, timeout=a.timeout, rss_mib=576)
            expected = [f'PGM{b:05d}' for b in range(boxes)]
            if mode in ('value', 'hub-perform'): expected.append('BOOT0000')
            if mode in ('flags', 'perform-flags', 'hub-perform-flags'): expected.append('ZERO0000')
            target = dest/'dependencies.json'
            actual = sorted(d['name'] for d in json.loads(target.read_text())['dependencies'] if d['type']=='program') if target.exists() else None
            metrics = [json.loads(s) for s in (dest/'metrics.jsonl').read_text().splitlines()] if (dest/'metrics.jsonl').exists() else []
            row.update(id=case, boxes=boxes, hubs=a.hubs, mode=mode,
                       sourceSha256=hashlib.sha256(source_path.read_bytes()).hexdigest(),
                       jarSha256=hashlib.sha256(jar.read_bytes()).hexdigest(), expected=sorted(expected), actual=actual,
                       passed=row['exit'] in (0, 1) and actual==sorted(expected),
                       metrics=next((m['metrics'] for m in metrics if 'metrics' in m), None))
            rows.append(row); (a.output/'results.json').write_text(json.dumps(rows, indent=2)+'\n')
            print(case, row['exit'], row['guard'], 'PASS' if row['passed'] else 'INCOMPLETE/RED',
                  round(row['seconds'], 2), round(row['peakRssKiB']/1024, 1), row['metrics'], flush=True)


if __name__ == '__main__':
    main()
