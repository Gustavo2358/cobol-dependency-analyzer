#!/usr/bin/env python3
"""Compare pinned stress artifacts, including source hashes and exact output bytes.
Usage: python3 -B benchmark/compare-suffix-results.py BEFORE_DIR AFTER_DIR REPORT_JSON
Only common cases are compared; unmatched cases are explicit in the report.
"""
import hashlib
import json
import sys
from pathlib import Path

before_dir, after_dir, output = map(Path, sys.argv[1:])
before = json.loads((before_dir / 'results.json').read_text())
after = json.loads((after_dir / 'results.json').read_text())
old = {r['id']: r for r in before['results']}
new = {r['id']: r for r in after['results']}
rows = []

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

for identifier in sorted(old.keys() & new.keys()):
    a, b = old[identifier], new[identifier]
    old_output = before_dir / identifier / 'dependencies.json'
    new_output = after_dir / identifier / 'dependencies.json'
    identical = old_output.exists() and new_output.exists() and old_output.read_bytes() == new_output.read_bytes()
    same_source = a['sourceSha256'] == b['sourceSha256']
    passed = (same_source and identical and a['status'] == b['status'] == 'PASS'
              and a['exit'] == b['exit'] and a['expected'] == b['expected'] and a['actual'] == b['actual'])
    rows.append(dict(id=identifier, passed=passed, sameSource=same_source, byteIdentical=identical,
                     outputSha256=sha(new_output) if new_output.exists() else None,
                     before=a, after=b, speedup=a['seconds'] / b['seconds'],
                     rssReductionPercent=100 * (1 - b['peakRssKiB'] / a['peakRssKiB'])
                     if a['peakRssKiB'] and b['peakRssKiB'] else None))

report = dict(beforeJarSha256=before['metadata']['jarSha256'], afterJarSha256=after['metadata']['jarSha256'],
              beforeResultsSha256=sha(before_dir / 'results.json'), afterResultsSha256=sha(after_dir / 'results.json'),
              beforeDirectory=str(before_dir), afterDirectory=str(after_dir),
              compared=len(rows), passed=sum(r['passed'] for r in rows),
              onlyBefore=sorted(old.keys() - new.keys()), onlyAfter=sorted(new.keys() - old.keys()), results=rows)
output.parent.mkdir(parents=True, exist_ok=True)
output.write_text(json.dumps(report, indent=2) + '\n')
print(json.dumps({k:report[k] for k in ['compared','passed','onlyBefore','onlyAfter']}))
sys.exit(0 if rows and all(r['passed'] for r in rows) else 1)
