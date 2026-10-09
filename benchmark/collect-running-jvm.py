#!/usr/bin/env python3
"""Collect bounded diagnostics from an existing Linux JVM without restarting it."""
import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess
import time
import uuid


def identity(pid):
    # Fields after the last ')' avoid spaces in the process name.
    fields = Path(f'/proc/{pid}/stat').read_text().rsplit(')', 1)[1].split()
    return fields[19]  # starttime (field 22)


def counters(pid):
    ticks = os.sysconf('SC_CLK_TCK')
    fields = Path(f'/proc/{pid}/stat').read_text().rsplit(')', 1)[1].split()
    status = Path(f'/proc/{pid}/status').read_text().splitlines()
    rss = next((int(s.split()[1]) for s in status if s.startswith('VmRSS:')), None)
    threads = []
    for path in Path(f'/proc/{pid}/task').glob('*/stat'):
        try:
            f = path.read_text().rsplit(')', 1)[1].split()
            threads.append({'tid': int(path.parent.name),
                            'cpuSeconds': (int(f[11]) + int(f[12])) / ticks})
        except (OSError, ValueError):
            pass  # A thread may finish while /proc is being sampled.
    return {'cpuSeconds': (int(fields[11]) + int(fields[12])) / ticks,
            'rssKiB': rss, 'threads': threads}


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('pid', type=int)
    p.add_argument('output', type=Path)
    p.add_argument('--jdk', type=Path, help='JDK home of the target JVM')
    p.add_argument('--duration', type=int, default=60)
    p.add_argument('--snapshots', type=int, default=4)
    a = p.parse_args()
    if a.pid <= 0 or not 4 <= a.duration <= 120 or not 2 <= a.snapshots <= 6:
        p.error('PID must be positive; duration must be 4..120 s; snapshots 2..6')
    try:
        original_identity = identity(a.pid)
    except OSError as e:
        p.error(f'cannot read target process: {e}')
    jcmd = str(a.jdk / 'bin/jcmd') if a.jdk else shutil.which('jcmd')
    jstat = str(a.jdk / 'bin/jstat') if a.jdk else shutil.which('jstat')
    if not jcmd or not Path(jcmd).is_file():
        p.error('jcmd not found; provide --jdk')
    dest = a.output.resolve()
    dest.mkdir(parents=True, exist_ok=False)
    commands = []

    def run(name, cmd):
        begin = time.monotonic()
        with (dest / name).open('w') as out:
            try:
                result = subprocess.run(cmd, stdout=out, stderr=subprocess.STDOUT, timeout=15)
                code = result.returncode
            except subprocess.TimeoutExpired:
                code = 'CLIENT_TIMEOUT'
        commands.append({'file': name, 'command': cmd, 'exit': code,
                         'seconds': time.monotonic() - begin})
        print(name, code, flush=True)
        return code

    def alive():
        try:
            return identity(a.pid) == original_identity
        except OSError:
            return False

    if run('heap-initial.txt', [jcmd, '-J-Xmx64m', str(a.pid), 'GC.heap_info']) != 0:
        (dest / 'manifest.json').write_text(json.dumps({
            'pid': a.pid, 'processStartTicks': original_identity,
            'reason': 'initial_attach_failed', 'commands': commands}, indent=2) + '\n')
        raise SystemExit('Attach failed; see heap-initial.txt. Target was left running.')
    if not alive():
        raise SystemExit('Target exited after initial snapshot.')
    # This recording stops automatically even if the collector is interrupted.
    recording = 'dependency_probe_' + uuid.uuid4().hex
    run('jfr-start.txt', [jcmd, '-J-Xmx64m', str(a.pid), 'JFR.start',
                         f'name={recording}', 'settings=profile', 'disk=true',
                         f'duration={a.duration}s', 'maxsize=32m',
                         f'filename={dest / "profile.jfr"}'])
    begin = time.monotonic()
    next_snapshot = 0
    reason = 'collection_interrupted'
    try:
        with (dest / 'telemetry.jsonl').open('w') as out:
            while time.monotonic() - begin <= a.duration + 1:
                if not alive():
                    reason = 'target_exited_or_pid_reused'
                    break
                elapsed = time.monotonic() - begin
                try:
                    row = {'seconds': elapsed, **counters(a.pid)}
                except OSError:
                    reason = 'target_unavailable'
                    break
                out.write(json.dumps(row) + '\n')
                out.flush()
                if next_snapshot < a.snapshots and elapsed >= next_snapshot * a.duration / (a.snapshots - 1):
                    tag = f'{next_snapshot:02d}'
                    run(f'threads-{tag}.txt', [jcmd, '-J-Xmx64m', str(a.pid), 'Thread.print', '-l'])
                    if alive():
                        run(f'heap-{tag}.txt', [jcmd, '-J-Xmx64m', str(a.pid), 'GC.heap_info'])
                    if alive() and jstat and Path(jstat).is_file():
                        run(f'gc-{tag}.txt', [jstat, '-J-Xmx64m', '-gc', str(a.pid)])
                    next_snapshot += 1
                time.sleep(1)
            else:
                reason = 'duration_completed' if next_snapshot == a.snapshots else 'duration_completed_missing_snapshots'
    finally:
        manifest = {'pid': a.pid, 'processStartTicks': original_identity,
                    'durationRequestedSeconds': a.duration,
                    'elapsedSeconds': time.monotonic() - begin,
                    'reason': reason, 'snapshotsCollected': next_snapshot,
                    'jfrPresent': (dest / 'profile.jfr').is_file(), 'commands': commands}
        (dest / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
    print('Saved to', dest, flush=True)


if __name__ == '__main__':
    main()
