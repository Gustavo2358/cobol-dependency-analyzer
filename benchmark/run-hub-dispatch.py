#!/usr/bin/env python3
"""Sequential diagnostic matrix with explicit heap, RSS and host-memory guards."""
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
    p.add_argument('--heap', type=int, choices=(128, 256, 512, 768, 1024, 1536), default=512)
    p.add_argument('--max-work', type=int, default=1000000)
    p.add_argument('--rss', type=int, default=576)
    p.add_argument('--system-reserve', type=int, default=0)
    p.add_argument('--telemetry', action='store_true')
    p.add_argument('--profile-duration', type=int, default=10)
    p.add_argument('--overlay', type=Path)
    p.add_argument('--noescape', action='store_true', help='Diagnostic ablation; requires Flow overlay and may lose dependencies')
    p.add_argument('--release-control-results', action='store_true',
                   help='Diagnostic lifecycle intervention; requires wall-probes overlay')
    p.add_argument('--compact-control-results', action='store_true',
                   help='Diagnostic BitSet result storage; requires wall-probes overlay')
    p.add_argument('--timeout', type=int, default=35)
    a = p.parse_args()
    if a.max_work < 1 or a.timeout < 1 or a.rss < 1 or a.profile_duration < 1 or a.system_reserve < 0:
        p.error('budgets and durations must be positive; system reserve must be nonnegative')
    if a.noescape and (not a.overlay or not (a.overlay/'com/imd/cobolexplorer/DependencyFlow.class').is_file()):
        p.error('--noescape requires the --flow-phases diagnostic overlay')
    if a.release_control_results or a.compact_control_results:
        manifest = a.overlay.parent/'manifest.json' if a.overlay else None
        metadata = json.loads(manifest.read_text()) if manifest and manifest.is_file() else {}
        if not metadata.get('wallProbes') or metadata.get('exit') != 0 or not (a.overlay/'com/imd/cobolexplorer/DependencyControl.class').is_file():
            p.error('control-result interventions require the --wall-probes diagnostic overlay')
        if a.compact_control_results and 'probe.compactControlResults' not in metadata.get('diagnosticInterventions', []):
            p.error('--compact-control-results requires an overlay with compact result storage')
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
            if a.noescape: cmd.append('-Dprobe.noescape=true')
            if a.release_control_results: cmd.append('-Dprobe.releaseControlResults=true')
            if a.compact_control_results: cmd.append('-Dprobe.compactControlResults=true')
            if not a.profile: cmd.append('-XX:+ExitOnOutOfMemoryError')
            if a.profile:
                cmd.extend([f'-XX:StartFlightRecording=settings=profile,duration={a.profile_duration}s,maxsize=24m,filename={dest}/profile.jfr',
                            f'-Xlog:gc:file={dest}/gc.log'])
            cmd.extend(['-cp', str(a.overlay.resolve())+os.pathsep+str(jar), 'com.imd.cobolexplorer.DependencyMain'] if a.overlay else ['-jar', str(jar)])
            cmd.extend(['--source', str(source_path), '--output', str(dest/'dependencies.json'),
                        '--metrics', str(dest/'metrics.jsonl'), '--max-work', str(a.max_work)])
            row = runner.execute(cmd, dest, timeout=a.timeout, rss_mib=a.rss,
                                 telemetry_period=1 if a.telemetry else None,min_available_mib=a.system_reserve)
            expected = [f'PGM{b:05d}' for b in range(boxes)]
            if mode in ('value', 'hub-perform'): expected.append('BOOT0000')
            if mode in ('flags', 'perform-flags', 'hub-perform-flags'): expected.append('ZERO0000')
            target = dest/'dependencies.json'
            actual = sorted(d['name'] for d in json.loads(target.read_text())['dependencies'] if d['type']=='program') if target.exists() else None
            metrics = [json.loads(s) for s in (dest/'metrics.jsonl').read_text().splitlines()] if (dest/'metrics.jsonl').exists() else []
            stderr = (dest/'stderr.log').read_text()
            probes = {prefix: [json.loads(line[len(prefix)+1:]) for line in stderr.splitlines() if line.startswith(prefix+' ')]
                      for prefix in ('FLOW_PROBE', 'CONTROL_PROBE', 'RELEVANCE_PROBE', 'CYCLE_PROBE', 'CONTROL_STORAGE')}
            row.update(id=case, boxes=boxes, hubs=a.hubs, mode=mode,
                       sourceSha256=hashlib.sha256(source_path.read_bytes()).hexdigest(),
                       jarSha256=hashlib.sha256(jar.read_bytes()).hexdigest(), expected=sorted(expected), actual=actual,
                       passed=row['exit'] in (0, 1) and actual==sorted(expected),
                       diagnosticAblation=a.noescape,
                       diagnosticInterventions=(['release-control-results'] if a.release_control_results else [])+(['compact-control-results'] if a.compact_control_results else []),
                       productionQualified=False if a.noescape or a.release_control_results or a.compact_control_results else row['exit'] in (0, 1) and actual==sorted(expected),
                       probes=probes, failure=next((line for line in stderr.splitlines() if line.startswith('ANALYSIS_FAILED:')), None),
                       metrics=next((m['metrics'] for m in metrics if 'metrics' in m), None))
            rows.append(row); (a.output/'results.json').write_text(json.dumps(rows, indent=2)+'\n')
            verdict = ('DIAGNOSTIC: candidates match' if row['passed'] else 'DIAGNOSTIC: incomplete/different') if a.noescape or a.release_control_results or a.compact_control_results else ('PASS' if row['passed'] else 'INCOMPLETE/RED')
            print(case, row['exit'], row['guard'], verdict,
                  round(row['seconds'], 2), round(row['peakRssKiB']/1024, 1), row['metrics'], flush=True)
    return 0 if all(row['passed'] for row in rows) else 1


if __name__ == '__main__':
    raise SystemExit(main())
