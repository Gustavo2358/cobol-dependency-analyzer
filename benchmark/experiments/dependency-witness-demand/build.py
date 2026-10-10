#!/usr/bin/env python3
"""Isolated value-definition solver. Never edits production or the frozen JAR."""
import argparse,hashlib,json,shutil,subprocess,os
from pathlib import Path
ROOT=Path(__file__).resolve().parents[3]
def overlay(text):
 start=text.index('        var cells=new LinkedHashSet<Element>();',text.index('    static DependencyFlow analyze('))
 end=text.index('\n    private DependencyFlow(',start)
 text=text[:start]+'''        return new DependencyFlow(unit,declarations,control,queries,maxWork,cics,conditions,Set.of());
    }
'''+text[end:]
 start=text.index('        var observations=new HashSet<String>();',text.index('    private DependencyFlow('))
 end=text.index('\n    static String handle(',start)
 text=text[:start]+'''        // Empty adapters serve only the existing CLI metric shape. No canonical
        // control, relevance, contexts or demand passes are executed here.
        controlSummary=new DependencyControl(List.of(),this::controlEffect,this::controlDelivery,p->false,maxWork);
        relevance=new DependencyRelevance(controlSummary.graph,this::inputEffect,maxWork);
        SparseDefinitions.solve(this,roots);
    }
    State definitionInitial(){return initial();}
    State definitionTransfer(Ast.Statement s,State input){try{return transfer(s,input);}catch(OutOfMemoryError e){e.printStackTrace();throw e;}}
    WriteSupport definitionSupport(Ast.Statement s){return writeSupport(s);}
    Set<Integer> definitionWrites(Ast.Statement s){return writes(s);}
    Set<Integer> definitionKills(Ast.Statement s){var result=new HashSet<Integer>();for(var input:localEffect(new Exit(TargetKind.OCCURRENCE,handle(s))).kills())if(input instanceof Value v)result.add(v.declaration());return result;}
    DependencyControl.Rule definitionEffect(Exit e){return controlEffect(e);}
    Exit definitionPhase(Binding b,String phase){return controlPhase(b,phase);}
'''+text[end:]
 old='return slot==null?(tables.containsKey(id)&&tables.get(id).contains(indexes)?value.open():DependencyValues.UNKNOWN):state.get(slot);'
 assert text.count(old)==1
 text=text.replace(old,'return value.open(); // Experiment: union of all positions, no table cells.')
 old='declarations.leaves(id).stream().anyMatch(tableValueIds::containsKey)'
 assert text.count(old)==1
 text=text.replace(old,'declarations.leaves(id).stream().anyMatch(leaf -> demand.contains(leaf)&&declarations.repeated.contains(leaf))')
 return text

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('output',type=Path);p.add_argument('--jar',type=Path,required=True);p.add_argument('--ref',default='52c1b82dc6accbb615818cf5b5298843a85b0f01');a=p.parse_args();a.output=a.output.resolve();a.output.mkdir(parents=True,exist_ok=False)
 original=subprocess.check_output(['git','-C',str(ROOT),'show',a.ref+':src/main/java/com/imd/cobolexplorer/DependencyFlow.java'],text=True)
 analyzerOriginal=subprocess.check_output(['git','-C',str(ROOT),'show',a.ref+':src/main/java/com/imd/cobolexplorer/DependencyAnalyzer.java'],text=True)
 start=analyzerOriginal.index('                    values.answers.forEach((q,v)->v.values().forEach(name->{')
 end=analyzerOriginal.index('\n                    }));',start)+len('\n                    }));')
 analyzerText=analyzerOriginal[:start]+'                    DefinitionsPublication.publish(values.answers,(type,name,at)->add(deps,type,name,at),notices);'+analyzerOriginal[end:]
 src=a.output/'source/com/imd/cobolexplorer';src.mkdir(parents=True);flow=src/'DependencyFlow.java';flow.write_text(overlay(original));solver=src/'SparseDefinitions.java';shutil.copy2(Path(__file__).with_name('SparseDefinitions.java'),solver)
 analyzer=src/'DependencyAnalyzer.java';analyzer.write_text(analyzerText);publication=src/'DefinitionsPublication.java';shutil.copy2(Path(__file__).with_name('DefinitionsPublication.java'),publication)
 logical=src/'DefinitionValues.java';shutil.copy2(Path(__file__).with_name('DefinitionValues.java'),logical)
 classes=a.output/'classes';classes.mkdir();empty=a.output/'empty';empty.mkdir();cmd=[str(Path(os.environ['JAVA_HOME'])/'bin/javac'),'-J-Xmx128m','--release','17','-implicit:none','-sourcepath',str(empty),'-cp',str(a.jar.resolve()),'-d',str(classes),str(flow),str(solver),str(analyzer),str(publication),str(logical)]
 with (a.output/'compile.log').open('w') as log:r=subprocess.run(cmd,stdout=log,stderr=subprocess.STDOUT,timeout=60)
 sha=lambda b:hashlib.sha256(b).hexdigest()
 (a.output/'manifest.json').write_text(json.dumps({'baselineCommit':a.ref,'jarSha256':sha(a.jar.read_bytes()),'originalFlowSha256':sha(original.encode()),'overlayFlowSha256':sha(flow.read_bytes()),'solverSha256':sha(solver.read_bytes()),'originalAnalyzerSha256':sha(analyzerOriginal.encode()),'overlayAnalyzerSha256':sha(analyzer.read_bytes()),'publicationSha256':sha(publication.read_bytes()),'logicalSha256':sha(logical.read_bytes()),'command':cmd,'exit':r.returncode,'productionQualified':False,'semanticInterventions':['context-insensitive physical control with shared boundary returns','sparse reaching definitions with identity-chain bypass','join-only SCC sharing','predicates and caller correlations conservatively ignored','table cells summarized by declaration'],'exactInterventions':['enumerate shared candidate sets once per dependency kind/validation rule with earliest provenance']},indent=2)+'\n')
 if r.returncode:print((a.output/'compile.log').read_text())
 return r.returncode
if __name__=='__main__':raise SystemExit(main())
