#!/usr/bin/env python3
"""Isolated value-definition solver. Never edits production or the frozen JAR."""
import argparse,hashlib,json,shutil,subprocess,os
from pathlib import Path
ROOT=Path(__file__).resolve().parents[3]
def overlay(text):
 allocation=sparse_table_allocation(text)
 # Reuse the same structural rules, but let the experiment's small handler
 # lattice decide event routes. Inventory of a condition is not its activation.
 old='    private DependencyControl.Rule controlEffect(Exit position) {'
 assert text.count(old)==1
 text=text.replace(old,'''    private DependencyControl.Rule controlEffect(Exit position){return controlEffect(position,true);}
    private DependencyControl.Rule controlEffect(Exit position,boolean eventsEnabled) {''')
 text=text.replace('for(var event:events.getOrDefault(node,List.of()))if(event.eligibility()==EventEligibility.HANDLER_ELIGIBLE) {','for(var event:events.getOrDefault(node,List.of()))if(eventsEnabled&&event.eligibility()==EventEligibility.HANDLER_ELIGIBLE) {',1)
 text=text.replace('if(exceptionalEvents.getOrDefault(node,List.of()).stream().anyMatch(e->e.eligibility()==EventEligibility.HANDLER_ELIGIBLE))','if(eventsEnabled&&exceptionalEvents.getOrDefault(node,List.of()).stream().anyMatch(e->e.eligibility()==EventEligibility.HANDLER_ELIGIBLE))',1)
 start=text.index('        var cells=new LinkedHashSet<Element>();',text.index('    static DependencyFlow analyze('))
 end=text.index('\n    private DependencyFlow(',start)
 text=text[:start]+'''        return new DependencyFlow(unit,declarations,control,queries,maxWork,cics,conditions,new LinkedHashSet<>());
    }
'''+text[end:]
 start=text.index('        var observations=new HashSet<String>();',text.index('    private DependencyFlow('))
 end=text.index('\n    static String handle(',start)
 text=text[:start]+'''        // Empty adapters serve only the existing CLI metric shape. No canonical
        // control, relevance or invocation contexts are executed here.
        controlSummary=new DependencyControl(List.of(),this::controlEffect,this::controlDelivery,p->false,maxWork);
        relevance=new DependencyRelevance(controlSummary.graph,this::inputEffect,maxWork);
        SparseDefinitions.solve(this,roots);
    }
    State definitionInitial(){return initial();}
    State definitionTransfer(Ast.Statement s,State input){try{return transfer(s,input);}catch(OutOfMemoryError e){e.printStackTrace();throw e;}}
    WriteSupport definitionSupport(Ast.Statement s){return writeSupport(s);}
    Set<Integer> definitionWrites(Ast.Statement s){return writes(s);}
    Set<Integer> definitionKills(Ast.Statement s){var result=new HashSet<Integer>();for(var input:localEffect(new Exit(TargetKind.OCCURRENCE,handle(s))).kills())if(input instanceof Value v)result.add(v.declaration());return result;}
    DependencyControl.Rule definitionEffect(Exit e){return controlEffect(e,false);}
    DependencyControl.Effect definitionDelivery(DependencyControl.Call c,Exit e){return controlDelivery(c,e);}
    DependencyControl.Call definitionCall(Binding b){return new DependencyControl.Call(new Point(Exit.of(regions.get(b.region()).entry()),b.endpoint(),escapeScope(b)),b.id());}
'''+text[end:]
 # Whole-table initialization replaces every proven equivalent logical view.
 # The scalar weak-write helper must not keep pre-initialization alias cells.
 old="""            var initialized=new DependencyEnvironment.Builder<>(state.values());initialized.put(id,value);
            for(int slot:tableValueIds.get(id))initialized.put(slot,value);state=state.withValues(initialized);"""
 assert text.count(old)==1
 text=text.replace(old,"""            var initialized=new DependencyEnvironment.Builder<>(state.values());
            for(int alias:declarations.equivalents.getOrDefault(id,Set.of(id))) {
                initialized.put(alias,fitField(alias,value));
                for(int slot:tableValueIds.getOrDefault(alias,List.of()))initialized.put(slot,fitField(alias,value));
            }
            state=state.withValues(initialized);""")
 # Reuse canonical sparse table allocation, after RD has discovered demand.
 text=text.replace('    State definitionInitial(){return initial();}',allocation+'\n    State definitionInitial(){return initial();}')
 return text

def sparse_table_allocation(original):
 start=original.index('        nextElementId=declarations.entries.keySet()')
 end=original.index('        var cyclic=controlSummary.cyclicNodes();',start)
 allocation=original[start:end]
 # Allocate tables once; subsequent rounds only append newly requested cells.
 allocation=allocation.replace('        nextElementId=', '        if(tables.isEmpty())nextElementId=',1)
 allocation=allocation.replace('if(declarations.repeated.contains(id)&&','if(!tables.containsKey(id)&&declarations.repeated.contains(id)&&',1)
 allocation=allocation.replace('if(tables.containsKey(cell.declaration())) {','if(tables.containsKey(cell.declaration())&&!elements.containsKey(cell)) {',1)
 return '    void definitionTables(){\n'+allocation+'        writeSupports.clear();accessEffects.clear();\n    }'

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
 structural=src/'DefinitionControl.java';shutil.copy2(Path(__file__).with_name('DefinitionControl.java'),structural)
 classes=a.output/'classes';classes.mkdir();empty=a.output/'empty';empty.mkdir();cmd=[str(Path(os.environ['JAVA_HOME'])/'bin/javac'),'-J-Xmx128m','--release','17','-implicit:none','-sourcepath',str(empty),'-cp',str(a.jar.resolve()),'-d',str(classes),str(flow),str(solver),str(analyzer),str(publication),str(logical),str(structural)]
 with (a.output/'compile.log').open('w') as log:r=subprocess.run(cmd,stdout=log,stderr=subprocess.STDOUT,timeout=60)
 sha=lambda b:hashlib.sha256(b).hexdigest()
 (a.output/'manifest.json').write_text(json.dumps({'baselineCommit':a.ref,'jarSha256':sha(a.jar.read_bytes()),'originalFlowSha256':sha(original.encode()),'overlayFlowSha256':sha(flow.read_bytes()),'solverSha256':sha(solver.read_bytes()),'controlSha256':sha(structural.read_bytes()),'originalAnalyzerSha256':sha(analyzerOriginal.encode()),'overlayAnalyzerSha256':sha(analyzer.read_bytes()),'publicationSha256':sha(publication.read_bytes()),'logicalSha256':sha(logical.read_bytes()),'command':cmd,'exit':r.returncode,'productionQualified':False,'semanticInterventions':['shared physical control with forward boundary-policy bits and handler dispositions','sparse reaching definitions with identity-chain bypass','join-only SCC sharing','predicates and caller/value correlations conservatively ignored','demanded sparse table positions plus unmaterialized remainder','whole-table INITIALIZE resets proven equivalent views'],'exactInterventions':['enumerate shared candidate sets once per dependency kind/validation rule with earliest provenance']},indent=2)+'\n')
 if r.returncode:print((a.output/'compile.log').read_text())
 return r.returncode
if __name__=='__main__':raise SystemExit(main())
