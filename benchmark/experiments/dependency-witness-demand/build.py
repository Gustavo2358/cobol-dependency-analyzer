#!/usr/bin/env python3
"""Build a research overlay against the pinned main JAR, without product edits."""
import argparse,hashlib,json,os,subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[3]
def main():
 p=argparse.ArgumentParser();p.add_argument('output',type=Path);p.add_argument('--jar',type=Path,required=True);a=p.parse_args();a.output=a.output.resolve();a.output.mkdir(parents=True,exist_ok=False);a.jar=a.jar.resolve()
 source=ROOT/'src/main/java/com/imd/cobolexplorer/DependencyAnalyzer.java';text=source.read_text().replace('DependencyFlow.Query','WitnessFlow.Query')
 old='DependencyFlow.analyze(unit,declarations,topology,queries,options.maxWork(),cics,conditions.uses(unit.id()))';assert text.count(old)==1;text=text.replace(old,'WitnessFlow.analyze(unit,declarations,topology,queries,options.maxWork())')
 start=text.index('                    flow+=System.nanoTime()-mark;work+=values.visits;');end=text.index('\n                    values.answers.forEach',start)
 text=text[:start]+'                    flow+=System.nanoTime()-mark;work+=values.visits;tracked+=values.demand.size();physicalControlNodes+=values.reachable.size();physicalControlEdges+=values.graphEdges;mark=System.nanoTime();notices.addAll(values.diagnostics);'+text[end:]
 target=a.output/'source/com/imd/cobolexplorer';target.mkdir(parents=True);(target/'DependencyAnalyzer.java').write_text(text);(target/'WitnessFlow.java').write_bytes(Path(__file__).with_name('WitnessFlow.java').read_bytes());classes=a.output/'classes';classes.mkdir();empty=a.output/'empty';empty.mkdir()
 cmd=[str(Path(os.environ['JAVA_HOME'])/'bin/javac'),'-J-Xmx128m','--release','17','-implicit:none','-sourcepath',str(empty),'-cp',str(a.jar),'-d',str(classes),*map(str,target.glob('*.java'))]
 r=subprocess.run(cmd,text=True,capture_output=True,timeout=60);(a.output/'compile.log').write_text(r.stdout+r.stderr);sha=lambda b:hashlib.sha256(b).hexdigest()
 (a.output/'manifest.json').write_text(json.dumps(dict(baseSha=subprocess.check_output(['git','-C',str(ROOT),'rev-parse','main'],text=True).strip(),jarSha256=sha(a.jar.read_bytes()),sources={f.name:sha(f.read_bytes()) for f in target.glob('*.java')},command=cmd,exit=r.returncode,productionQualified=False),indent=2)+'\n');print(r.stdout+r.stderr);r.check_returncode()
if __name__=='__main__':main()
