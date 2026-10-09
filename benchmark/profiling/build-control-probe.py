#!/usr/bin/env python3
"""Build a counter-only overlay of DependencyControl; production stays untouched."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[2]


def instrument(source):
    def replace(old, new):
        nonlocal source
        if source.count(old) != 1:
            raise ValueError('production source changed; review probe anchor: ' + old)
        source = source.replace(old, new)

    replace('    long visits;', '''    long visits;
    private static int probeInstances;
    private final int probeInstance=++probeInstances;
    private final long probeStart=System.nanoTime();
    private long pairsScanned,pairsDelivered,exitsScanned,exitsAdded,lastProbe;
    private void probe(String phase) {
        lastProbe=System.nanoTime();
        System.err.println("CONTROL_PROBE {\\"phase\\":\\""+phase+"\\",\\"instance\\":"+probeInstance+",\\"seconds\\":"+((lastProbe-probeStart)/1e9)+",\\"visits\\":"+visits+",\\"plans\\":"+plans.size()+",\\"pairsScanned\\":"+pairsScanned+",\\"pairsDelivered\\":"+pairsDelivered+",\\"exitsScanned\\":"+exitsScanned+",\\"retainedResults\\":"+exitsAdded+",\\"queue\\":"+work.size()+",\\"heapUsed\\":"+(Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory())+"}");
    }
    private void hotPlans() {
        var top=new ArrayList<Map.Entry<Point,Plan>>();long calls=0;int maxResults=0;
        for(var e:plans.entrySet()) {
            calls+=e.getValue().calls.size();maxResults=Math.max(maxResults,results.get(e.getKey()).size());
            top.add(e);top.sort(Comparator.comparingInt((Map.Entry<Point,Plan> p)->p.getValue().delivered.size()).reversed());
            if(top.size()>5)top.remove(5);
        }
        System.err.println("CONTROL_SHAPE calls="+calls+" maxResults="+maxResults);
        for(var e:top)System.err.println("CONTROL_HOTPLAN point="+e.getKey()+" calls="+e.getValue().calls.size()+" delivered="+e.getValue().delivered.size()+" results="+results.get(e.getKey()).size());
    }''')
    replace('        roots.forEach(this::discover);',
            '        probe("discovery-start");roots.forEach(this::discover);probe("discovered");')
    replace('            var point=work.removeFirst();',
            '            if(System.nanoTime()-lastProbe>1000000000L)probe("propagate");\n            var point=work.removeFirst();')
    replace('                if(plan.delivered.add(new Returned(call,exit)))add(point,delivery.apply(point,call,exit));',
            '                {pairsScanned++;if(plan.delivered.add(new Returned(call,exit))){pairsDelivered++;add(point,delivery.apply(point,call,exit));}}')
    replace('            for(var next:plan.next)exits.addAll(results.get(next));',
            '            for(var next:plan.next){exitsScanned+=results.get(next).size();exits.addAll(results.get(next));}')
    replace('            if(results.get(point).addAll(exits))parents.getOrDefault(point,Set.of()).forEach(this::schedule);',
            '            int before=results.get(point).size();boolean grew=results.get(point).addAll(exits);exitsAdded+=results.get(point).size()-before;\n            if(grew)parents.getOrDefault(point,Set.of()).forEach(this::schedule);')
    replace('        var pending=new ArrayDeque<>(roots);',
            '        probe("fixed-point");hotPlans();var pending=new ArrayDeque<>(roots);')
    replace('                    if(plans.size()>=maxWork)',
            '                    if(plans.size()%16384==0)probe("discovery");\n                    if(plans.size()>=maxWork)')
    return source


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('output', type=Path)
    p.add_argument('--jdk', type=Path)
    p.add_argument('--jar', type=Path, default=ROOT/'target/cobol-dependency-analyzer.jar')
    a = p.parse_args()
    prod = ROOT/'src/main/java/com/imd/cobolexplorer/DependencyControl.java'
    probe = instrument(prod.read_text())
    a.output = a.output.resolve()
    a.output.mkdir(parents=True, exist_ok=False)
    source = a.output/'DependencyControl.java'; source.write_text(probe)
    classes = a.output/'classes'; classes.mkdir()
    empty = a.output/'empty'; empty.mkdir()
    javac = str(a.jdk/'bin/javac') if a.jdk else shutil.which('javac')
    cmd = [javac, '-J-Xmx128m', '-implicit:none', '-sourcepath', str(empty), '-cp',
           str(a.jar.resolve()), '-d', str(classes), str(source)]
    sha = lambda path: hashlib.sha256(path.read_bytes()).hexdigest()
    manifest = {'productionSourceSha256': sha(prod), 'probeSourceSha256': sha(source),
                'jarSha256': sha(a.jar), 'command': cmd}
    with (a.output/'compile.log').open('w') as log:
        result = subprocess.run(cmd, stdout=log, stderr=subprocess.STDOUT, timeout=60)
    manifest['exit'] = result.returncode
    (a.output/'manifest.json').write_text(json.dumps(manifest, indent=2)+'\n')
    raise SystemExit(result.returncode)


if __name__ == '__main__':
    main()
