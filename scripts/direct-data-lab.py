#!/usr/bin/env python3
"""Measure both parser modes in fresh JVMs and compare every published artifact.
Build first; --classpath must describe that immutable build. No build is timed.
Outputs are intentionally outside Git. A new output directory is required.
"""
import argparse
import hashlib
import json
import os
import signal
from pathlib import Path
import re
import shutil
import statistics
import subprocess
import time


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--classpath', required=True)
    parser.add_argument('--source', action='append', required=True)
    parser.add_argument('--copybooks', required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--repetitions', type=int, default=5)
    parser.add_argument('--java', default='java')
    parser.add_argument('--frontend-arg', action='append', default=[])
    args = parser.parse_args()
    if args.repetitions < 3:
        parser.error('at least three repetitions are required')
    forbidden = {'--parser', '--source', '--output', '--copybooks', '--json-compression'}
    if any(value in forbidden for value in args.frontend_arg):
        parser.error('frontend arguments cannot override the compared modes or artifact destinations')
    root = Path(__file__).resolve().parents[1]
    out = args.output.resolve()
    out.mkdir(parents=True, exist_ok=False)
    sources = [Path(source).resolve() for source in args.source]
    if len(set(sources)) != len(sources):
        parser.error('duplicate source')
    time_binary = Path('/usr/bin/time')
    if not time_binary.exists():
        parser.error('GNU /usr/bin/time is required for process wall/CPU/RSS measurements')
    manifest = {
        'head': subprocess.check_output(['git', '-C', str(root), 'rev-parse', 'HEAD'], text=True).strip(),
        'java': subprocess.check_output([args.java, '-version'], stderr=subprocess.STDOUT, text=True),
        'sourceHashes': {str(p): digest(p) for p in sources},
        'productionHashes': {str(p.relative_to(root)): digest(p) for p in sorted((root / 'src/main').rglob('*')) if p.is_file()},
        'copybookHashes': {str(p.resolve()): digest(p) for d in args.copybooks.split(',') for p in sorted(Path(d).rglob('*')) if p.is_file()},
        'classpath': args.classpath,
        'compiledHashes': {str(p.relative_to(root)): digest(p) for p in sorted((root / 'target/classes').rglob('*')) if p.is_file()},
        'dependencyHashes': {str(Path(p).resolve()): digest(Path(p)) for p in args.classpath.split(os.pathsep) if Path(p).is_file()},
        'javaExecutable': shutil.which(args.java),
        'repetitions': args.repetitions,
        'definition': 'recognition + syntax-origin indexing + semantic AST construction; preprocessing, lexing and presentation export excluded in both modes',
    }
    (out / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
    runs = []
    for case_index, source in enumerate(sources):
        for trial in range(args.repetitions):
            modes = ['antlr', 'direct-data-lab'] if trial % 2 == 0 else ['direct-data-lab', 'antlr']
            for mode in modes:
                case = out / f'{case_index:03}-{mode}-{trial}'
                case.mkdir()
                command = [str(time_binary), '-f', '%e %U %S %M', '-o', str(case / 'process-time.txt'),
                           args.java, '-Xms256m', '-Xmx2g', '-XX:+UseG1GC', '-DANALYZER_LOG_LEVEL=DEBUG',
                           '-cp', args.classpath, 'io.github.gustavo2358.cobolexplorer.ExplorerMain',
                           '--parser', mode, '--source', str(source), '--copybooks', args.copybooks,
                           '--output', str(case / 'products'), '--json-compression', 'none'] + args.frontend_arg
                started = time.monotonic()
                with (case / 'stdout.log').open('w') as stdout, (case / 'stderr.log').open('w') as stderr:
                    process = subprocess.Popen(command, cwd=root, stdout=stdout, stderr=stderr, start_new_session=True)
                    try:
                        code = process.wait(timeout=300)
                    except subprocess.TimeoutExpired:
                        os.killpg(process.pid, signal.SIGKILL)
                        process.wait()
                        code = 'TIMEOUT'
                log = (case / 'stderr.log').read_text()
                spans = re.findall(r'event=syntax_to_ast_completed .*recognitionNanos=(\d+) indexingNanos=(\d+) astNanos=(\d+)', log)
                row = {'source': str(source), 'case': case_index, 'mode': mode, 'trial': trial,
                       'command': command, 'exitCode': code, 'supervisorSeconds': time.monotonic() - started,
                       'spansNanos': [[int(n) for n in span] for span in spans],
                       'admission': re.findall(r'event=direct_data_lab (.*)', log),
                       'artifacts': {p.name: digest(p) for p in (case / 'products').glob('*') if p.is_file()}}
                if code == 0:
                    wall, user, system, rss = map(float, (case / 'process-time.txt').read_text().split())
                    row.update(wallSeconds=wall, cpuSeconds=user + system, peakRssKiB=rss)
                runs.append(row)
                (out / 'runs.json').write_text(json.dumps(runs, indent=2) + '\n')
                if code != 0 or len(spans) != 1:
                    raise SystemExit(f'Failed run or missing timing: {case}; raw evidence preserved')
                print(f'{source.name} {mode} {trial}: syntax={sum(row["spansNanos"][0])/1e6:.1f} ms wall={wall:.2f} s', flush=True)
    results = []
    for index, source in enumerate(sources):
        group = [r for r in runs if r['case'] == index]
        variants = {}
        for mode in ['antlr', 'direct-data-lab']:
            samples = [r for r in group if r['mode'] == mode]
            syntax = [sum(r['spansNanos'][0]) / 1e6 for r in samples]
            variants[mode] = {'syntaxMsMedian': statistics.median(syntax), 'syntaxMsRange': [min(syntax), max(syntax)],
                              'wallSecondsMedian': statistics.median(r['wallSeconds'] for r in samples),
                              'cpuSecondsMedian': statistics.median(r['cpuSeconds'] for r in samples),
                              'peakRssKiBMedian': statistics.median(r['peakRssKiB'] for r in samples)}
        differences = []
        pairs = 0
        for trial in range(args.repetitions):
            a = next(r for r in group if r['mode'] == 'antlr' and r['trial'] == trial)['artifacts']
            b = next(r for r in group if r['mode'] == 'direct-data-lab' and r['trial'] == trial)['artifacts']
            pairs += len(a.keys() | b.keys())
            differences.extend({'trial': trial, 'file': key} for key in sorted(a.keys() | b.keys()) if a.get(key) != b.get(key))
        reduction = 100 * (1 - variants['direct-data-lab']['syntaxMsMedian'] / variants['antlr']['syntaxMsMedian'])
        results.append({'source': str(source), 'variants': variants, 'syntaxReductionPercent': reduction,
                        'target50PercentReached': reduction >= 50, 'artifactPairs': pairs, 'differences': differences,
                        'admission': [r['admission'] for r in group if r['mode'] == 'direct-data-lab']})
    (out / 'summary.json').write_text(json.dumps(results, indent=2) + '\n')
    if any(r['differences'] for r in results):
        raise SystemExit('Artifact differences found; inspect summary.json; no differences were normalized away')
    print(json.dumps(results, indent=2))


if __name__ == '__main__':
    main()
