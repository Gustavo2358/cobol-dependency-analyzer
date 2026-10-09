#!/usr/bin/env python3
"""Replay a frozen 73-source CLI report, sequentially with host/RSS guards."""
import argparse, hashlib, importlib.util, json, shutil
from pathlib import Path
from normalize import load, product, reference

ROOT = Path(__file__).resolve().parents[1]


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('baseline', type=Path)
    parser.add_argument('output', type=Path)
    parser.add_argument('--jar', type=Path, required=True)
    parser.add_argument('--reference', type=Path, required=True)
    parser.add_argument('--input-hashes', type=Path, required=True)
    args = parser.parse_args()
    rows = json.loads(args.baseline.read_text())
    references = {r['id']: r for r in json.loads(args.reference.read_text())}
    assert len(rows) == len(references) == 73
    spec = importlib.util.spec_from_file_location('bounded', ROOT/'benchmark/run-sparse-occurs.py')
    runner = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(runner)
    jar = args.jar.resolve()
    args.output.mkdir(parents=True, exist_ok=False)
    inputs = {}
    frozen_inputs = json.loads(args.input_hashes.read_text())
    assert all(sha(Path(path)) == digest for path, digest in frozen_inputs.items()), 'Frozen inputs changed'
    inputs.update(frozen_inputs)
    for row in rows:
        cmd = row['command']
        source = Path(cmd[cmd.index('--source')+1])
        assert sha(source) == row['sourceSha256'] == references[row['id']]['sourceSha256']
        inputs[str(source)] = sha(source)
        for i, option in enumerate(cmd):
            if option == '--copy-dir':
                for path in sorted(Path(cmd[i+1]).rglob('*')):
                    if path.is_file():
                        inputs[str(path)] = sha(path)
    (args.output/'input-hashes.json').write_text(json.dumps(inputs, indent=2)+'\n')
    results = []
    for row in rows:
        old_cmd = row['command']
        source = old_cmd[old_cmd.index('--source')+1]
        dest = args.output/row['id']
        cmd = [shutil.which('java'), '-Xms16m', '-Xmx256m', '-XX:MaxMetaspaceSize=128m',
               '-XX:MaxDirectMemorySize=32m', '-XX:+ExitOnOutOfMemoryError', '-jar', str(jar),
               '--source', source, '--output', str(dest/'dependencies.json'),
               '--metrics', str(dest/'metrics.jsonl'), '--max-work', '100000000']
        for i, option in enumerate(old_cmd):
            if option == '--copy-dir':
                cmd += [option, old_cmd[i+1]]
        result = runner.execute(cmd, dest, timeout=45, rss_mib=512,
                                telemetry_period=1, min_available_mib=2048)
        baseline_path = args.baseline.parent/row['id']/'dependencies.json'
        reference_path = args.reference.parent/row['id']/'dependencies.json.zst'
        expected = product(load(baseline_path))
        assert expected == reference(load(reference_path)) == {tuple(d) for d in row['equal']}
        output = dest/'dependencies.json'
        document = load(output) if output.exists() else None
        actual = product(document) if document is not None else set()
        programs = document if isinstance(document, list) else [document] if document else []
        identity = {p['program'] for p in programs} == {row['program']}
        result.update(id=row['id'], source=row['source'], sourceSha256=sha(Path(source)),
                      program=row['program'], jarSha256=sha(jar),
                      baselineSha256=sha(baseline_path), referenceSha256=sha(reference_path),
                      outputSha256=sha(output) if output.exists() else None,
                      equal=sorted(expected & actual), missing=sorted(expected-actual),
                      additional=sorted(actual-expected), identityMatches=identity,
                      passed=result['exit'] in (0, 1) and identity and actual == expected)
        results.append(result)
        (args.output/'results.json').write_text(json.dumps(results, indent=2)+'\n')
        print(len(results), '/73', row['source'], result['exit'], result['guard'],
              'PASS' if result['passed'] else 'RED', round(result['seconds'], 2), flush=True)
    assert all(sha(Path(path)) == digest for path, digest in inputs.items()), 'Inputs changed during run'
    summary = dict(sources=len(results), passed=sum(r['passed'] for r in results),
                   equal=sum(len(r['equal']) for r in results), missing=sum(len(r['missing']) for r in results),
                   additional=sum(len(r['additional']) for r in results),
                   partial=sum(r['exit'] == 1 for r in results),
                   seconds=sum(r['seconds'] for r in results), peakRssKiB=max(r['peakRssKiB'] for r in results),
                   heapMiB=256, rssGuardMiB=512, systemReserveMiB=2048, timeoutSeconds=45,
                   maxWork=100000000, jarSha256=sha(jar), inputHashesSha256=sha(args.output/'input-hashes.json'))
    summary['frozenInputHashesSha256'] = sha(args.input_hashes)
    (args.output/'summary.json').write_text(json.dumps(summary, indent=2)+'\n')
    print(summary, flush=True)
    return 0 if all(r['passed'] for r in results) else 1


if __name__ == '__main__':
    raise SystemExit(main())
