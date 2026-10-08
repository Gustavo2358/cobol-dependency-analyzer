#!/usr/bin/env python3
"""Recheck original explosion sources and exercise larger dependency-bearing variants.
Usage: python3 -B benchmark/run-explosion-review.py OUTPUT_DIR [--heap MiB] [--case ID] [--max-work N] [--timeout SECONDS]
Original artifacts are read only; each case runs sequentially in a fresh JVM.
"""
import argparse, hashlib, json, runpy, subprocess, sys, time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
WORKSPACE = ROOT.parent
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('output')
parser.add_argument('--heap', type=int, default=512)
parser.add_argument('--case', action='append', default=[])
parser.add_argument('--max-work', type=int, default=1000000)
parser.add_argument('--timeout', type=int, default=45)
parser.add_argument('--extended', action='store_true', help='Include larger AND/OR and dynamic overlap cases')
args = parser.parse_args()
assert args.heap > 0 and args.max_work > 0 and args.timeout > 0
OUT = Path(args.output).resolve()
OUT.mkdir(parents=True, exist_ok=False)
JAVA = '/home/gustavo/.sdkman/candidates/java/21.0.12+1.1-tem/bin/java'
JAR = ROOT / 'target/cobol-dependency-analyzer.jar'
GENERATOR = WORKSPACE / 'artefatos-e2e/memory-general-discovery-20261005/generate.py'
generator = runpy.run_path(str(GENERATOR))

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

cases = []
def original(identifier, relative, expected):
    source = WORKSPACE / relative
    cases.append(dict(id=identifier, source=str(source), sourceSha256=sha(source),
                      sourceKind='original', expected=expected))

def generated(identifier, text, expected, basis, dimension):
    directory = OUT / identifier
    directory.mkdir()
    source = directory / 'input.cbl'
    source.write_text(text)
    cases.append(dict(id=identifier, source=str(source), sourceSha256=sha(source),
                      sourceKind='derived', basis=basis, dimension=dimension,
                      expected=expected))

original('plain-original-oom09', 'artefatos-e2e/dataflow-context-explosion-20261002/runs/plain-stress-512m/plain-09/CTXBOOM.cbl', [])
original('escape-original-oom08', 'artefatos-e2e/dataflow-context-explosion-20261002/runs/escape-growth/escape-08/CTXBOOM.cbl', [])
original('cics-original-oom09', 'artefatos-e2e/dataflow-context-explosion-20261002/runs/cics-effects-growth/cics-09/CTXBOOM.cbl', ['DUMMY'])
original('empty-paragraphs10000', 'artefatos-e2e/memory-general-fix-20261005/review-f1-f2-20261006/long-empty.cbl', ['AFTER', 'BODY'])
original('inline-original20', 'artefatos-e2e/lower-unification-20261003/inline-stress-11-20/20/INLINEBOOM.cbl', [])
original('and-control76', 'artefatos-e2e/memory-general-discovery-20261005/runs/activation-and-control-512m/and-0076/input.cbl', ['SYNROOT', 'SYNTGTA', 'SYNTGTB'])
original('overlap-compact900', 'artefatos-e2e/memory-general-discovery-20261005/runs/overlap900-compact-8g/overlap-0900/input.cbl', [])

fixtures = ROOT / 'src/test/resources/dependency-regression/resource-stress'
for family, size in [('plain', 9), ('escape', 8), ('escape', 20)]:
    source = fixtures / f'context-{family}-{size:02d}/CTXBOOM.cbl'
    text = source.read_text()
    # Keep the recursive dispatcher, but observe the same existing value.
    text = text.replace("MOVE 'A' TO WS-OBS", "MOVE 'A' TO WS-OBS\n           CALL WS-OBS", 1)
    generated(f'{family}-dynamic{size:02d}', text, ['A'],
              dict(source=str(source), sha256=sha(source), change='CALL WS-OBS after its existing MOVE'), size)

inline = Path(cases[4]['source'])
generated('inline-observed20', inline.read_text().replace('MAIN.\n', "MAIN.\n           CALL 'INLINEOK'.\n", 1),
          ['INLINEOK'], dict(source=str(inline), sha256=sha(inline), change='reachable literal query'), 20)

basis = dict(source=str(GENERATOR), sha256=sha(GENERATOR))
for family in ['and', 'or']:
    for n in [76, 152, 304, 608] + ([1216] if args.extended else []):
        generated(f'{family}{n}', generator['activation'](n, union=family == 'or'),
                  ['SYNROOT', 'SYNTGTA', 'SYNTGTB'], basis, n)
for n in [400, 900] + ([1800] if args.extended else []):
    text = generator['overlap'](n)
    text = text.replace('           GOBACK.', '           CALL WS-TEXT.\n           GOBACK.', 1)
    assert 'CALL WS-TEXT.' in text
    generated(f'overlap-dynamic{n}', text, ['H'], basis, n)

def fixed(lines):
    return '\n'.join('       ' + line for line in lines) + '\n'

for n in [800, 6400]:
    lines = ['IDENTIFICATION DIVISION.', 'PROGRAM-ID. DEADFRAMES.', 'PROCEDURE DIVISION.',
             'MAIN.', "CALL 'SYNPROG'.", 'GOBACK.', 'DEAD-CALLERS.']
    lines += ['PERFORM BODY.'] * n
    lines += ['GOBACK.', 'BODY.'] + ['CONTINUE.'] * n + ['EXIT.']
    generated(f'dead-callers{n}', fixed(lines), ['SYNPROG'],
              'COBOL counterpart of the AIR-only dead-frames mechanism; not the same original input', n)
for n in [16000, 32000, 64000, 117000]:
    lines = ['IDENTIFICATION DIVISION.', 'PROGRAM-ID. LINEARSCALE.', 'DATA DIVISION.',
             'WORKING-STORAGE SECTION.', '01 WS-TARGET PIC X(8).', 'PROCEDURE DIVISION.']
    lines += ["MOVE 'PROGA' TO WS-TARGET."] * n
    lines += ['CALL WS-TARGET.', 'GOBACK.']
    generated(f'linear-statements{n}', fixed(lines), ['PROGA'],
              'Additional synthetic size curve with a demanded target; not the unavailable corporate input', n)

if args.case:
    assert set(args.case) <= {c['id'] for c in cases}, 'Unknown case'
    cases = [c for c in cases if c['id'] in args.case]
metadata = dict(commit=subprocess.check_output(['git', '-C', str(ROOT), 'rev-parse', 'HEAD'], text=True).strip(),
                jarSha256=sha(JAR), java=JAVA, heapMiB=args.heap, timeoutSeconds=args.timeout,
                maxWork=args.max_work,
                cases=cases)
(OUT / 'inputs.json').write_text(json.dumps(metadata, indent=2) + '\n')
results = []
for case in cases:
    directory = OUT / case['id']
    directory.mkdir(exist_ok=True)
    target = directory / 'dependencies.json'
    command = [JAVA, '-Xms32m', f'-Xmx{args.heap}m', '-jar', str(JAR), '--source', case['source'],
               '--output', str(target), '--metrics', str(directory / 'metrics.jsonl'),
               '--max-work', str(args.max_work)]
    assert sha(Path(case['source'])) == case['sourceSha256']
    started = time.monotonic()
    timed_out = False
    with (directory / 'stdout.log').open('w') as stdout, (directory / 'stderr.log').open('w') as stderr:
        # A process group prevents GNU time leaving a Java child after timeout.
        proc = subprocess.Popen(['/usr/bin/time', '-f', '%e %M', '-o', str(directory / 'time.txt'), *command],
                                stdout=stdout, stderr=stderr, start_new_session=True)
        try:
            code = proc.wait(timeout=args.timeout)
        except subprocess.TimeoutExpired:
            import os, signal
            os.killpg(proc.pid, signal.SIGKILL)
            proc.wait()
            timed_out = True
            code = 'TIMEOUT'
    elapsed = time.monotonic() - started
    log = (directory / 'stderr.log').read_text()
    timing = (directory / 'time.txt').read_text().strip().splitlines()
    rss = None
    if timing:
        numbers = timing[-1].split()
        if len(numbers) == 2 and numbers[1].isdigit():
            rss = int(numbers[1])
    actual = []
    metrics = {}
    if code in (0, 1):
        document = json.loads(target.read_text())
        programs = document if isinstance(document, list) else [document]
        actual = sorted({d['name'] for p in programs for d in p['dependencies']})
        for line in (directory / 'metrics.jsonl').read_text().splitlines():
            item = json.loads(line)
            if 'metrics' in item:
                metrics = item['metrics']
    passed = code in (0, 1) and actual == sorted(case['expected'])
    status = ('PASS' if passed else 'TIMEOUT' if timed_out else 'OOM' if 'OutOfMemoryError' in log
              else 'RESOURCE_LIMIT' if 'RESOURCE_LIMIT' in log else 'FAIL')
    result = dict(id=case['id'], sourceKind=case['sourceKind'], sourceSha256=case['sourceSha256'],
                  lines=len(Path(case['source']).read_bytes().splitlines()), status=status, exit=code,
                  seconds=elapsed, peakRssKiB=rss, expected=case['expected'], actual=actual,
                  outputPublished=target.exists(), metrics=metrics, command=command)
    results.append(result)
    (OUT / 'results.json').write_text(json.dumps(dict(metadata=metadata, results=results), indent=2) + '\n')
    print(case['id'], status, round(elapsed, 3), rss, 'work', metrics.get('workItems'), 'contexts', metrics.get('contexts'), flush=True)
print('RESULTS', {s:sum(r['status']==s for r in results) for s in sorted({r['status'] for r in results})}, flush=True)
sys.exit(0 if all(r['status']=='PASS' for r in results) else 1)
