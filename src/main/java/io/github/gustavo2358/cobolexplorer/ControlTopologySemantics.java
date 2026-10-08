package io.github.gustavo2358.cobolexplorer;

import java.util.*;
import java.util.function.Function;
import io.github.gustavo2358.cobolexplorer.semanticproduct.ControlTopology;
import io.github.gustavo2358.cobolexplorer.semanticproduct.CobolSemanticProduct;
import static io.github.gustavo2358.cobolexplorer.semanticproduct.ControlTopology.*;

/** Grammar-owned region composition. No values, dataflow or execution-context
 * expansion. The projector supplies handles, not control decisions. */
public final class ControlTopologySemantics {
    private final CompilationUnitModel.ProgramUnit unit;
    private final Ast.Division division;
    private final Map<Ast.Statement,String> ids;
    private final Map<Integer,Ast.Node> nodes=new HashMap<>();
    private final Map<Integer,Integer> targetDeclarations=new HashMap<>();
    private final List<Ast.Paragraph> paragraphs=new ArrayList<>();
    private final Map<Integer,String> paragraphIds=new HashMap<>();
    private final Map<Integer,Ast.Section> sections=new LinkedHashMap<>();
    private final Map<Integer,String> sectionIds=new HashMap<>();
    private final List<Integer> procedureOrder=new ArrayList<>();
    private final Map<Integer,String> paragraphOwners=new HashMap<>();
    private final Map<String,Ast.Section> declarativeRegions=new TreeMap<>();
    private final Map<String,Occurrence> occurrences=new TreeMap<>();
    private final Map<String,Region> regions=new TreeMap<>();
    private final Map<String,Boundary> boundaries=new TreeMap<>();
    private final Map<String,Outcome> outcomes=new TreeMap<>();
    private final Map<String,Binding> bindings=new TreeMap<>();
    private final List<FileFlow> fileFlows=new ArrayList<>();
    private final List<SourceContinuation> sourceContinuations=new ArrayList<>();
    private final List<EntryPoint> entryPoints=new ArrayList<>();
    private final List<ExceptionalEvent> exceptionalEvents=new ArrayList<>();
    private final List<ConditionRegistration> conditionRegistrations=new ArrayList<>();
    private final List<ConditionEvent> conditionEvents=new ArrayList<>();
    private final Map<String,Proof> proofs=new TreeMap<>();
    private final Map<String,List<String>> subregions=new HashMap<>();
    private final CobolSemanticProduct.FileInventory files;
    private final CicsProgramControlAnalyzer.Contribution cics;
    private final boolean independent;
    private final String root;
    private final Map<Integer,List<SqlWheneverSemantics.Route>> sqlDispatch;

    public static ControlTopology analyze(CompilationUnitModel.ProgramUnit unit, SymbolTable symbols,
            ReferenceResolution resolution, ResolutionAnalysisReport report,
            Map<Ast.Statement,CobolSemanticProduct.StatementId> handles,
            CobolSemanticProduct.FileInventory files, CicsProgramControlAnalyzer.Contribution cics) {
        if(unit.program().divisions().stream().noneMatch(d->d.divisionKind()==Ast.DivisionKind.PROCEDURE)) {
            if(!handles.isEmpty())throw new IllegalArgumentException("executable occurrences without procedure syntax");
            return new ControlTopology("FRONTEND_CONTROL_TOPOLOGY_R1",List.of(),List.of(),List.of(),List.of(),List.of(),List.of());
        }
        var ids=new IdentityHashMap<Ast.Statement,String>();handles.forEach((s,id)->ids.put(s,"statement:"+id.localId()));
        return new ControlTopologySemantics(unit,symbols,resolution,report,ids,files,cics).build();
    }
    private ControlTopologySemantics(CompilationUnitModel.ProgramUnit unit,SymbolTable symbols,
            ReferenceResolution resolution,ResolutionAnalysisReport report,Map<Ast.Statement,String> ids,
            CobolSemanticProduct.FileInventory files,CicsProgramControlAnalyzer.Contribution cics) {
        this.unit=unit;this.ids=ids;this.files=files;this.cics=cics;
        division=unit.program().divisions().stream().filter(d->d.divisionKind()==Ast.DivisionKind.PROCEDURE).findFirst().orElseThrow();
        root="region:procedure:"+division.meta().id();
        sqlDispatch=SqlWheneverSemantics.analyze(division);
        independent=division.procedureEntry().filter(e->e.inputProof().unaffectedBy(report.frontendState())).isPresent();
        var todo=new ArrayDeque<Ast.Node>();todo.push(division);
        while(!todo.isEmpty()){var n=todo.pop();nodes.put(n.meta().id(),n);Ast.children(n).forEach(todo::push);}
        var bySymbol=new HashMap<Integer,SymbolTable.Symbol>();symbols.symbols().forEach(s->bySymbol.put(s.id(),s));
        for(var ref:resolution.entries())if(ref.occurrence().programUnitId().equals(unit.id())
                &&ref.status()==ResolutionContracts.ResolutionStatus.RESOLVED&&ref.candidates().size()==1) {
            ref.selectedCandidate().ifPresent(c->{var id=c.entityId();var symbol=bySymbol.get(id.localId());
                if(id.programUnitId().equals(unit.id())&&id.domain()==ResolutionContracts.SemanticEntityDomain.PROCEDURE_SYMBOL
                        &&symbol!=null&&(symbol.kind()==SymbolTable.SymbolKind.PARAGRAPH||symbol.kind()==SymbolTable.SymbolKind.PROCEDURE_SECTION))
                    targetDeclarations.put(ref.occurrence().referenceAstNodeId(),symbol.declarationAstNodeId());});
        }
        collectParagraphs(division,root);
        paragraphs.forEach(p->paragraphIds.put(p.meta().id(),"region:paragraph:"+p.meta().id()));
    }
    private void collectParagraphs(Ast.Node node,String owner) {
        if(node instanceof Ast.Section section&&section.children().stream().anyMatch(Ast.UseClause.class::isInstance)) {
            owner="region:declarative:"+section.meta().id();declarativeRegions.put(owner,section);
        } else if(node instanceof Ast.Section section) {
            owner="region:section:"+section.meta().id();sections.put(section.meta().id(),section);
            sectionIds.put(section.meta().id(),owner);procedureOrder.add(section.meta().id());
        }
        for(var child:Ast.children(node)) {
            if(child instanceof Ast.Paragraph paragraph){paragraphs.add(paragraph);paragraphOwners.put(paragraph.meta().id(),owner);procedureOrder.add(paragraph.meta().id());}
            else if(!(child instanceof Ast.Statement))collectParagraphs(child,owner);
        }
    }
    private ControlTopology build() {
        var rp=proof(root,ProofKind.LOCAL_GRAMMAR,"procedure-region",division.meta().provenance(),List.of());
        var isolation=proof(root+"/input",independent?ProofKind.INPUT_REGION_ISOLATION:ProofKind.PARTIAL_UNKNOWN,
            independent?"procedure-syntax-independent-of-missing-data-input":"procedure-input-not-isolated",division.meta().provenance(),List.of(rp));
        for(var entry:declarativeRegions.entrySet()) {
            var id=entry.getKey();var premise=proof(id,ProofKind.LOCAL_GRAMMAR,"declarative-section-boundary",entry.getValue().meta().provenance(),List.of(isolation));
            var first=paragraphs.stream().filter(p->paragraphOwners.get(p.meta().id()).equals(id)).findFirst();
            putRegion(id,RegionKind.DECLARATIVE,root,first.map(p->entry(paragraphIds.get(p.meta().id()),premise)).orElse(unknown(id,premise)),List.of(),unknown(id,premise),premise);
        }
        var sectionList=new ArrayList<>(sections.values());
        for(int i=0;i<sectionList.size();i++) {
            var section=sectionList.get(i);String id=sectionIds.get(section.meta().id());
            var ps=proof(id,ProofKind.LOCAL_GRAMMAR,"section-boundary",section.meta().provenance(),List.of(isolation));
            var direct=section.children().stream().filter(Ast.Sentence.class::isInstance).map(Ast.Sentence.class::cast).flatMap(x->x.statements().stream()).toList();
            var first=paragraphs.stream().filter(p->paragraphOwners.get(p.meta().id()).equals(id)).findFirst();
            Target after=i+1<sectionList.size()?entry(sectionIds.get(sectionList.get(i+1).meta().id()),ps):unknown(root,ps);
            Target tail=first.map(p->entry(paragraphIds.get(p.meta().id()),ps)).orElse(complete(id,ps));
            putRegion(id,RegionKind.SECTION,root,direct.isEmpty()?tail:occ(direct.get(0),ps),List.of(),after,ps);
            sentences(section.children().stream().filter(Ast.Sentence.class::isInstance).map(Ast.Sentence.class::cast).toList(),id,tail,isolation);
        }
        // Each paragraph default is the canonical ordinary relation, not transported list order.
        for(var p:paragraphs) {
            String id=paragraphIds.get(p.meta().id());var ps=proof(id,ProofKind.LOCAL_GRAMMAR,"paragraph-boundary",p.meta().provenance(),List.of(isolation));
            var direct=p.sentences().stream().flatMap(s->s.statements().stream()).toList();
            String owner=paragraphOwners.get(p.meta().id());
            Target after=unknown(owner,ps);
            int ordinal=paragraphs.indexOf(p);
            if(ordinal+1<paragraphs.size()&&paragraphOwners.get(paragraphs.get(ordinal+1).meta().id()).equals(owner))
                after=entry(paragraphIds.get(paragraphs.get(ordinal+1).meta().id()),ps);
            else if(sectionIds.containsValue(owner))after=complete(owner,ps);
            else if(owner.equals(root)&&!sectionList.isEmpty())after=entry(sectionIds.get(sectionList.get(0).meta().id()),ps);
            makeRegion(id,RegionKind.PARAGRAPH,owner,direct,after,ps);
            sentences(p.sentences(),id,complete(id,ps),isolation);
        }
        var roots=new ArrayList<Ast.Statement>();
        for(var child:division.children())if(child instanceof Ast.Sentence s)roots.addAll(s.statements());
        var end=paragraphs.stream().filter(p->paragraphOwners.get(p.meta().id()).equals(root)).findFirst().map(p->entry(paragraphIds.get(p.meta().id()),rp)).orElse(unknown(root,rp));
        sentences(division.children().stream().filter(Ast.Sentence.class::isInstance).map(Ast.Sentence.class::cast).toList(),root,end,isolation);
        var start=division.procedureEntry().flatMap(Ast.ProcedureEntry::startStatementId).map(nodes::get)
            .filter(Ast.Statement.class::isInstance).map(Ast.Statement.class::cast).filter(ids::containsKey).map(s->occ(s,rp)).orElse(unknown(root,rp));
        putRegion(root,RegionKind.PROCEDURE,"",start,roots.stream().map(ids::get).filter(Objects::nonNull).toList(),unknown(root,rp),rp);
        // Occurrences under unsupported constructs remain inventoried with a local
        // unknown outcome; they are never silently assigned a normal successor.
        ids.entrySet().stream().sorted(Map.Entry.comparingByValue()).forEach(e->{if(!occurrences.containsKey(e.getValue())){
            var p=proof(e.getValue()+"/unplaced",ProofKind.PARTIAL_UNKNOWN,"unsupported-region-membership",e.getKey().meta().provenance(),List.of(isolation));
            add(e.getKey(),root,OutcomeKind.UNKNOWN_LOCAL,"unknown",unknown(root,p),"",p);}});
        alternateEntries(isolation);
        fileFlows();
        fileRoutes();
        openControlDestinations();
        openConditionRestorations();
        for(var r:new ArrayList<>(regions.values()))regions.put(r.id(),new Region(r.id(),r.kind(),r.parent(),r.entry(),occurrences.values().stream().filter(o->o.region().equals(r.id())).map(Occurrence::statement).toList(),
            r.kind()==RegionKind.RANGE?r.regions():subregions.getOrDefault(r.id(),List.of()).stream().sorted().toList(),r.boundary(),r.proofs()));
        return new ControlTopology(fileFlows.isEmpty()?"FRONTEND_CONTROL_TOPOLOGY_R1":"FRONTEND_CONTROL_TOPOLOGY_R2",List.copyOf(occurrences.values()),List.copyOf(regions.values()),
            List.copyOf(boundaries.values()),List.copyOf(outcomes.values()),List.copyOf(bindings.values()),List.copyOf(proofs.values()),exceptionalEvents,fileFlows,sourceContinuations,entryPoints,conditionRegistrations,conditionEvents);
    }
    private void alternateEntries(String isolation) {
        var byHandle=new HashMap<String,Ast.Statement>();ids.forEach((source,id)->byHandle.put(id,source));
        for(var declaration:AlternateEntrySemantics.analyze(unit))if(declaration.supported()) {
            String id=ids.get(declaration.source());if(id==null)continue;
            var dependencies=new LinkedHashSet<String>();dependencies.add(isolation);
            var outcome=outcomes.get("outcome:"+id+"/normal");
            Target start=outcome==null||!independent?unknown(root,isolation):outcome.target();
            var seen=new HashSet<String>();
            while(true) {
                dependencies.addAll(start.proofs());
                if(!seen.add(start.kind()+"/"+start.reference())){start=unknown(root,isolation);break;}
                if(start.kind()==TargetKind.REGION_ENTRY){start=regions.get(start.reference()).entry();continue;}
                if(start.kind()==TargetKind.COMPLETE){start=boundaries.get(regions.get(start.reference()).boundary()).ordinaryDefault();continue;}
                if(start.kind()==TargetKind.OCCURRENCE&&byHandle.get(start.reference()) instanceof Ast.PreservedStatement next&&next.entrySurface().isPresent()) {
                    var step=outcomes.get("outcome:"+start.reference()+"/normal");if(step==null){start=unknown(root,isolation);break;}start=step.target();continue;
                }
                break;
            }
            var premise=proof(declaration.entry()+"/start",ProofKind.LOCAL_GRAMMAR,"alternate-entry-start",declaration.source().meta().provenance(),List.copyOf(dependencies));
            entryPoints.add(new EntryPoint(declaration.entry(),id,new Target(start.kind(),start.reference(),List.of(premise)),List.of(premise)));
        }
    }
    /** The period frontier is represented only when a source NEXT SENTENCE needs it. */
    private void sentences(List<Ast.Sentence> sentences,String owner,Target end,String isolation) {
        Target tail=end;
        for(int i=sentences.size()-1;i>=0;i--) {
            var sentence=sentences.get(i);var body=sentence.statements();if(body.isEmpty())continue;
            var pending=new ArrayDeque<Ast.Node>();pending.add(sentence);boolean escape=false;
            while(!pending.isEmpty()){var n=pending.removeFirst();escape|=n instanceof Ast.NextSentenceStatement;pending.addAll(Ast.children(n));}
            if(escape) {
                String region="region:sentence:"+sentence.meta().id();
                var p=proof(region,ProofKind.LOCAL_GRAMMAR,"sentence-period-frontier",sentence.meta().provenance(),List.of(isolation));
                makeRegion(region,RegionKind.SENTENCE,owner,body,tail,p);
                statements(body,region,complete(region,p),isolation);
            }else statements(body,owner,tail,isolation);
            tail=occ(body.get(0),isolation);
        }
    }
    private void statements(List<Ast.Statement> list,String owner,Target end,String isolation) {
        for(int i=0;i<list.size();i++) {
            var s=list.get(i);if(!ids.containsKey(s))continue;
            String id=ids.get(s);var p=proof(id,ProofKind.LOCAL_GRAMMAR,"statement-scope",s.meta().provenance(),List.of(isolation));
            var next=i+1<list.size()&&ids.containsKey(list.get(i+1))?occ(list.get(i+1),p):end;
            if(!independent){add(s,owner,OutcomeKind.UNKNOWN_LOCAL,"unknown",unknown(owner,p),"",p);continue;}
            exceptionalEvent(s,p);
            conditionEvent(s,next,p);
            if(s instanceof Ast.NextSentenceStatement) {
                String scope=owner;while(!scope.isEmpty()&&regions.get(scope).kind()!=RegionKind.SENTENCE)scope=regions.get(scope).parent();
                var target=scope.isEmpty()?unknown(owner,p):new Target(TargetKind.ESCAPE,scope,List.of(p));
                add(s,owner,scope.isEmpty()?OutcomeKind.UNKNOWN_LOCAL:OutcomeKind.EXPLICIT_TRANSFER,"next-sentence",target,"",p);continue;
            }
            if(s instanceof Ast.ModeledStatement m&&m.exitKind().filter(k->k==Ast.ExitKind.STOP_RUN||k==Ast.ExitKind.PROGRAM).isPresent()) {
                if(m.exitKind().get()==Ast.ExitKind.STOP_RUN) {
                    var stop=proof(id+"/halt",ProofKind.LOCAL_GRAMMAR,"stop-run-terminates-run-unit",s.meta().provenance(),List.of(p));
                    add(s,owner,OutcomeKind.PROGRAM_HALT,"halt",new Target(TargetKind.PROGRAM_HALT,root,List.of(stop)),"",stop);
                } else {
                    var exit=proof(id+"/program-exit",ProofKind.LOCAL_GRAMMAR,unit.parentId()!=null?"exit-program-contained-return":"exit-program-called-return-alternative",s.meta().provenance(),List.of(p));
                    add(s,owner,OutcomeKind.PROGRAM_RETURN,"return",new Target(TargetKind.PROGRAM_RETURN,root,List.of(exit)),"",exit);
                    if(unit.parentId()==null) {
                        var main=proof(id+"/main-exit",ProofKind.LOCAL_GRAMMAR,"exit-program-main-continues-runtime-role-unavailable",s.meta().provenance(),List.of(p));
                        add(s,owner,OutcomeKind.NORMAL,"normal",next,"",main);
                    }
                }
                continue;
            }
            if(s instanceof Ast.PreservedStatement entry&&entry.grammarRule().equals("entryStatement")&&entry.effects().filter(e->e.proof()==StatementEffectSummary.Proof.NO_OP).isPresent()) {
                var declared=proof(id+"/entry",ProofKind.LOCAL_GRAMMAR,"entry-declaration-in-sequential-flow",s.meta().provenance(),List.of(p));
                add(s,owner,OutcomeKind.NORMAL,"normal",next,"",declared);continue;
            }
            if(s instanceof Ast.SearchStatement search&&search.all()&&search.varying()==null&&search.whens().size()==1) {
                var region="region:"+id+"/search";putRegion(region,RegionKind.SEARCH,owner,occ(s,p),List.of(),next,p);
                var match=arm(region,"when-0",RegionKind.SEARCH_ARM,search.whens().get(0).statements(),p,isolation);
                var miss=search.atEnd()==null?complete(region,p):arm(region,"at-end",RegionKind.SEARCH_ARM,search.atEnd().nestedStatements(),p,isolation);
                add(s,region,OutcomeKind.BRANCH,"when-0",match,"",p);add(s,region,OutcomeKind.BRANCH,"at-end",miss,"",p);continue;
            }
            if(s instanceof Ast.ModeledStatement m&&m.exitKind().isPresent()) {
                var kind=m.exitKind().orElseThrow();String scope=owner;
                var wanted=kind==Ast.ExitKind.PARAGRAPH?RegionKind.PARAGRAPH:kind==Ast.ExitKind.SECTION?RegionKind.SECTION:RegionKind.INLINE_BODY;
                while(!scope.isEmpty()&&(!regions.containsKey(scope)||regions.get(scope).kind()!=wanted))
                    scope=regions.containsKey(scope)?regions.get(scope).parent():"";
                if(!scope.isEmpty()) {
                    var exitProof=proof(id+"/exit",ProofKind.LOCAL_GRAMMAR,"exit-"+kind.name().toLowerCase(Locale.ROOT),s.meta().provenance(),List.of(p));
                    var target=kind==Ast.ExitKind.PERFORM_CYCLE?complete(scope,exitProof):new Target(TargetKind.ESCAPE,scope,List.of(exitProof));
                    add(s,owner,OutcomeKind.EXPLICIT_TRANSFER,"exit",target,"",exitProof);
                } else if(kind!=Ast.ExitKind.PARAGRAPH&&kind!=Ast.ExitKind.SECTION) add(s,owner,OutcomeKind.EXPLICIT_TRANSFER,"exit-ignored",next,"",p);
                else add(s,owner,OutcomeKind.UNKNOWN_LOCAL,"exit-scope-unavailable",unknown(owner,p),"",p);
                continue;
            }
            if(s instanceof Ast.GobackStatement){add(s,owner,OutcomeKind.PROGRAM_RETURN,"return",new Target(TargetKind.PROGRAM_RETURN,root,List.of(p)),"",p);continue;}
            if(s instanceof Ast.IfStatement f) {
                var region="region:"+id+"/if";putRegion(region,RegionKind.IF,owner,occ(s,p),List.of(),next,p);
                var yes=arm(region,"then",RegionKind.IF_ARM,f.thenBranch(),p,isolation);
                var no=f.elsePresence()==Ast.BranchPresence.ABSENT?complete(region,p):
                    f.elsePresence()==Ast.BranchPresence.PRESENT?arm(region,"else",RegionKind.IF_ARM,f.elseBranch(),p,isolation):unknown(region,p);
                add(s,region,OutcomeKind.BRANCH,"then",yes,"",p);add(s,region,OutcomeKind.BRANCH,"else",no,"",p);continue;
            }
            if(s instanceof Ast.EvaluateStatement e) {
                var region="region:"+id+"/evaluate";putRegion(region,RegionKind.EVALUATE,owner,occ(s,p),List.of(),next,p);int ordinal=0;boolean other=false;
                for(var a:e.branches()) {String role=a.other()?"other":"when-"+(ordinal++);other|=a.other();
                    var target=arm(region,role,RegionKind.EVALUATE_ARM,a.statements(),p,isolation);
                    add(s,region,OutcomeKind.BRANCH,role,target,"",p);}
                if(!other)add(s,region,OutcomeKind.BRANCH,"other",complete(region,p),"",p);continue;
            }
            if(s instanceof Ast.PerformStatement perform&&(perform.repetition()==Ast.PerformRepetition.UNKNOWN)) {
                var unknownProof=proof(id+"/repetition",ProofKind.PARTIAL_UNKNOWN,"unresolved-perform-repetition",s.meta().provenance(),List.of(p));
                add(s,owner,OutcomeKind.UNKNOWN_LOCAL,"unknown",unknown(owner,unknownProof),"",unknownProof);continue;
            }
            if(s instanceof Ast.PerformStatement perform&&perform.performKind()==Ast.PerformKind.PROCEDURE) {
                var from=perform.fromReference()==null?null:targetDeclarations.get(perform.fromReference().meta().id());
                var to=perform.throughReference()==null?from:targetDeclarations.get(perform.throughReference().meta().id());
                int first=procedureOrder.indexOf(from),last=procedureOrder.indexOf(to);
                // A SECTION endpoint is its completion, after its paragraphs;
                // its header may precede the starting paragraph of a valid THRU.
                if(sectionIds.containsKey(to))while(last+1<procedureOrder.size()
                        &&sectionIds.get(to).equals(paragraphOwners.get(procedureOrder.get(last+1))))last++;
                if(first>=0&&last>=first&&procedureOwner(from).equals(procedureOwner(to))) {
                    String range="region:"+id+"/range",binding="binding:"+id;
                    var resolution=proof(binding,ProofKind.RESOLVED_TARGET,"resolved-ordered-paragraph-range",perform.meta().provenance(),List.of(p));
                    var parts=new ArrayList<>(procedureOrder.subList(first,last+1).stream().map(this::procedureRegion).toList());
                    String endRegion=procedureRegion(to);parts.remove(endRegion);parts.add(endRegion);
                    putRegion(range,RegionKind.RANGE,owner,entry(procedureRegion(from),resolution),List.of(),next,resolution);
                    var r=regions.get(range);regions.put(range,new Region(r.id(),r.kind(),r.parent(),r.entry(),r.members(),parts,r.boundary(),r.proofs()));
                    publishInvocation(perform,owner,range,binding,id,"boundary:"+parts.get(parts.size()-1),next,resolution);
                }else add(s,owner,OutcomeKind.UNKNOWN_LOCAL,"invoke-unresolved",unknown(owner,p),"",p);
                continue;
            }
            if(s instanceof Ast.PerformStatement perform) {
                var region="region:"+id+"/inline";makeRegion(region,RegionKind.INLINE_BODY,owner,perform.inlineBody(),next,p);
                statements(perform.inlineBody(),region,complete(region,p),isolation);
                String range="region:"+id+"/range",binding="binding:"+id;
                putRegion(range,RegionKind.RANGE,owner,entry(region,p),List.of(),next,p);
                var r=regions.get(range);regions.put(range,new Region(r.id(),r.kind(),r.parent(),r.entry(),r.members(),List.of(region),r.boundary(),r.proofs()));
                publishInvocation(perform,owner,range,binding,id,"boundary:"+region,next,p);continue;
            }
            if(s instanceof Ast.GoToStatement g) {
                int ordinal=0;
                for(var ref:g.targets()) {var declaration=targetDeclarations.get(ref.meta().id());
                    boolean resolved=declaration!=null&&paragraphIds.containsKey(declaration);
                    var resolution=proof(id+"/target-"+ordinal,resolved?ProofKind.RESOLVED_TARGET:ProofKind.PARTIAL_UNKNOWN,
                        resolved?"resolved-goto-target":"unresolved-goto-target",ref.meta().provenance(),List.of(p));
                    var target=resolved?entry(paragraphIds.get(declaration),resolution):unknown(owner,resolution);
                    add(s,owner,OutcomeKind.EXPLICIT_TRANSFER,"target-"+(ordinal++),target,"",resolution);}
                if(g.goToKind()==Ast.GoToKind.DEPENDING_ON)add(s,owner,OutcomeKind.NORMAL,"normal",next,"",p);
                if(g.targets().isEmpty())add(s,owner,OutcomeKind.UNKNOWN_LOCAL,"transfer-unresolved",unknown(owner,p),"",p);continue;
            }
            if(s instanceof Ast.CallStatement call&&!call.handlerClauses().isEmpty()) {
                String region="region:"+id+"/call";putRegion(region,RegionKind.CALL,owner,occ(s,p),List.of(),next,p);
                var hypothesis=proof(id+"/call-outcomes",ProofKind.CONTROL_POSSIBILITY,"external-call-success-or-local-handler",s.meta().provenance(),List.of(p));
                boolean success=false;
                for(var handler:call.handlerClauses()) {
                    var target=arm(region,handler.grammarRule(),RegionKind.CALL_HANDLER,handler.nestedStatements(),p,isolation);
                    sourceContinuations.add(new SourceContinuation(id,target,List.of(hypothesis)));
                    success|=handler.grammarRule().equals("notOnExceptionClause");
                }
                if(!success)sourceContinuations.add(new SourceContinuation(id,complete(region,p),List.of(hypothesis)));
                add(s,region,OutcomeKind.UNKNOWN_LOCAL,"external-outcome",unknown(region,p),"",p);continue;
            }
            var surface=s instanceof Ast.ModeledStatement m?m.fileIo():s instanceof Ast.PreservedStatement v?v.fileIo():Optional.<Ast.FileIoSurface>empty();
            if(surface.isPresent()) {
                String region="region:"+id+"/file";putRegion(region,RegionKind.FILE,owner,occ(s,p),List.of(),next,p);
                for(var handler:surface.get().handlers())arm(region,"handler-"+handler.kind(),RegionKind.FILE_HANDLER,handler.clause().nestedStatements(),p,isolation);
                add(s,region,OutcomeKind.NORMAL,"normal",complete(region,p),"",p);continue;
            }
            var command=cics==null?Optional.<CicsCommandControl.Qualification>empty():cics.commandFact(unit.id(),s.meta().id()).flatMap(CicsCommandControl::qualify);
            if(command.isPresent()) {
                var q=command.get();var normal=proof(id+"/command-normal",ProofKind.LOCAL_GRAMMAR,q.ordinaryProof(),s.meta().provenance(),List.of(p));
                if(q.programReturn()) {
                    add(s,owner,OutcomeKind.PROGRAM_RETURN,"return",new Target(TargetKind.PROGRAM_RETURN,root,List.of(normal)),"",normal);
                    if(q.conditionReturn()) {
                        var error=proof(id+"/command-condition-return",ProofKind.LOCAL_GRAMMAR,"cics-return-local-condition",s.meta().provenance(),List.of(normal));
                        add(s,owner,OutcomeKind.NORMAL,"condition-return",next,"",error);
                    }
                } else add(s,owner,OutcomeKind.NORMAL,"normal",next,"",normal);
                for(var condition:q.unresolvedConditions()) {
                    var remainder=proof(id+"/command-"+condition,ProofKind.PARTIAL_UNKNOWN,"cics-command-"+condition,s.meta().provenance(),List.of(normal));
                    add(s,owner,OutcomeKind.UNKNOWN_LOCAL,"cics/"+condition,unknown(owner,remainder),"",remainder);
                }
                continue;
            }
            if(CicsConditionSyntax.effects(s).isPresent()&&s instanceof Ast.EmbeddedLanguageStatement embedded
                    &&embedded.procedureOperands().stream().allMatch(r->targetDeclarations.containsKey(r.meta().id()))) {
                var registrationProof=proof(id+"/condition-state",ProofKind.LOCAL_GRAMMAR,"cics-condition-registration",s.meta().provenance(),List.of(p));
                boolean ignore=CicsCommandSyntax.parse(embedded.rawText()).orElseThrow().name().equals("IGNORE");int labelOrdinal=0;
                for(var option:CicsConditionSyntax.parse(embedded.rawText()).orElseThrow()) {
                    var targets=new ArrayList<Target>();
                    if(option.operand().isPresent()) {
                        var ref=embedded.procedureOperands().get(labelOrdinal++);var declaration=targetDeclarations.get(ref.meta().id());
                        var resolved=proof(id+"/condition-target/"+option.name(),ProofKind.RESOLVED_TARGET,"cics-condition-label",ref.meta().provenance(),List.of(registrationProof));
                        targets.add(entry(procedureRegion(declaration),resolved));
                    }
                    conditionRegistrations.add(new ConditionRegistration(id,option.name(),ignore?ConditionAction.IGNORE:targets.isEmpty()?ConditionAction.DEFAULT:ConditionAction.LABEL,targets,List.of(registrationProof)));
                }
                var normal=proof(id+"/condition-registration",ProofKind.LOCAL_GRAMMAR,"cics-handle-condition-ordinary-return",s.meta().provenance(),List.of(p));
                add(s,owner,OutcomeKind.NORMAL,"normal",next,"",normal);continue;
            }
            if(DliCommandSemantics.effects(s).isPresent()) {
                var normal=proof(id+"/dli-normal",ProofKind.LOCAL_GRAMMAR,"dli-command-ordinary-return",s.meta().provenance(),List.of(p));
                add(s,owner,OutcomeKind.NORMAL,"normal",next,"",normal);
                var failure=proof(id+"/dli-failure",ProofKind.PARTIAL_UNKNOWN,"dli-command-failure",s.meta().provenance(),List.of(normal));
                add(s,owner,OutcomeKind.UNKNOWN_LOCAL,"dli/failure",unknown(owner,failure),"",failure);
                continue;
            }
            if(SqlNormalCompletion.proved(s)) {
                boolean declaration=SqlCommandSyntax.parse(((Ast.EmbeddedLanguageStatement)s).rawText()).orElseThrow().declaration();
                var normal=proof(id+"/sql-normal",ProofKind.LOCAL_GRAMMAR,declaration?"db2-nonexecutable-directive":SqlNormalCompletion.selectInto(((Ast.EmbeddedLanguageStatement)s).rawText())?"db2-select-into-successful-return":"db2-command-possible-return",s.meta().provenance(),List.of(p));
                add(s,owner,OutcomeKind.NORMAL,"normal",next,"",normal);
                if(declaration)continue;
                for(var route:sqlDispatch.getOrDefault(s.meta().id(),List.of())) {
                    var target=route.target().map(ref->targetDeclarations.get(ref.meta().id())).map(this::procedureRegion);
                    var scopeProof=proof(id+"/sql-scope/"+route.condition(),ProofKind.LOCAL_GRAMMAR,"db2-whenever-lexical-scope",route.directive().meta().provenance(),List.of(normal));
                    var dispatch=proof(id+"/sql-dispatch/"+route.condition(),target.isPresent()?ProofKind.RESOLVED_TARGET:ProofKind.PARTIAL_UNKNOWN,
                        target.isPresent()?"db2-whenever-resolved-dispatch":"db2-whenever-target-unavailable",route.target().map(ref->ref.meta().provenance()).orElse(route.directive().meta().provenance()),List.of(scopeProof));
                    add(s,owner,target.isPresent()?OutcomeKind.EXPLICIT_TRANSFER:OutcomeKind.UNKNOWN_LOCAL,"sql/"+route.condition(),
                        target.map(region->entry(region,dispatch)).orElseGet(()->unknown(owner,dispatch)),"",dispatch);
                }
                var other=proof(id+"/sql-other",ProofKind.PARTIAL_UNKNOWN,"db2-select-into-other-outcomes",s.meta().provenance(),List.of(normal));
                add(s,owner,OutcomeKind.UNKNOWN_LOCAL,"sql/other",unknown(owner,other),"",other);continue;
            }
            if(conditionEvents.stream().anyMatch(e->e.statement().equals(id))
                    &&cics.fact(unit.id(),s.meta().id()).filter(f->f.command()==CicsProgramControlAnalyzer.Command.LINK).isPresent()) {
                var returned=proof(id+"/link-normal",ProofKind.LOCAL_GRAMMAR,"cics-link-successful-return",s.meta().provenance(),List.of(p));
                add(s,owner,OutcomeKind.NORMAL,"normal",next,"",returned);continue;
            }
            if (s instanceof Ast.PreservedStatement json && json.grammarRule().equals("jsonGenerateStatement")) {
                // Generation/effects remain unknown. The grammar still proves which
                // bodies may run on success/failure, and where each body completes.
                add(s,owner,OutcomeKind.UNKNOWN_LOCAL,"unknown",unknown(owner,p),"",p);
                jsonSourceAlternatives(json, owner, next, p, isolation);
                continue;
            }
            boolean completion=division.normalCompletionStatements().contains(s.meta().id());
            if(s instanceof Ast.EmbeddedLanguageStatement)completion=cics!=null&&cics.boundedLocal(unit.id(),s);
            boolean registration=cics!=null&&cics.handlerOrdinaryCompletion(unit.id(),s);
            var completionProof=registration?proof(id+"/handler-normal",ProofKind.LOCAL_GRAMMAR,
                "cics-handle-abend-ordinary-return",s.meta().provenance(),List.of(p)):p;
            completion|=registration;
            add(s,owner,completion?OutcomeKind.NORMAL:OutcomeKind.UNKNOWN_LOCAL,completion?"normal":"unknown",completion?next:unknown(owner,p),"",completionProof);
            if(!completion&&sourceMayComplete(s)) {
                var hypothesis=proof(id+"/source-completion",ProofKind.CONTROL_POSSIBILITY,
                    "completion-not-refuted-in-source",s.meta().provenance(),List.of(p));
                sourceContinuations.add(new SourceContinuation(id,next,List.of(hypothesis)));
            }
        }
    }
    /** Recognize loss of control-state knowledge without interpreting any ALTER target or update.
     * Source qualification requires both a mutation occurrence and a qualified transfer.
     * Destinations remain unproved alternatives in the local procedure namespace. */
    private void openControlDestinations() {
        if(!independent)return;
        var mutations=ids.keySet().stream().filter(s->s instanceof Ast.PreservedStatement p&&p.grammarRule().equals("alterStatement")
            ||s instanceof Ast.ModeledStatement m&&m.grammarRule().equals("alterStatement")).sorted(Comparator.comparing(ids::get)).toList();
        if(mutations.isEmpty())return;
        var transfers=ids.keySet().stream().filter(s->s instanceof Ast.GoToStatement g&&g.goToKind()==Ast.GoToKind.SIMPLE).sorted(Comparator.comparing(ids::get)).toList();
        for(var transfer:transfers)for(var mutation:mutations) {
            var id=ids.get(transfer);var prerequisite=ids.get(mutation);
            var premise=proof(id+"/source-control/"+prerequisite,ProofKind.CONTROL_POSSIBILITY,"local-control-destination-not-refuted-after-unmodeled-mutation",
                mutation.meta().provenance(),java.util.stream.Stream.concat(occurrences.get(id).proofs().stream(),occurrences.get(prerequisite).proofs().stream()).distinct().toList());
            for(var paragraph:paragraphs)if(!declarativeRegions.containsKey(paragraphOwners.get(paragraph.meta().id())))
                sourceContinuations.add(new SourceContinuation(id,entry(paragraphIds.get(paragraph.meta().id()),premise),List.of(premise),List.of(prerequisite)));
        }
    }
    /** Unknown stack restoration grants source possibilities only, never executable edges. */
    private void openConditionRestorations() {
        for(var source:ids.keySet())if(source instanceof Ast.EmbeddedLanguageStatement embedded
                &&embedded.language()==Ast.EmbeddedLanguage.CICS) {
            var command=CicsCommandSyntax.parse(embedded.rawText()).orElse(null);
            if(command==null||!command.name().equals("POP")||!command.ended()||!command.gaps().isEmpty()
                    ||command.options().size()!=1||!command.options().get(0).name().equals("HANDLE")
                    ||command.options().get(0).operand().isPresent())continue;
            var prerequisite=ids.get(source);
            for(var event:conditionEvents)if(event.eligibility()==EventEligibility.HANDLER_ELIGIBLE) {
                var key=event.id()+"/unknown-restoration/"+prerequisite;
                var hypothesis=proof(key,ProofKind.CONTROL_POSSIBILITY,"cics-condition-disposition-open-after-unmodeled-restoration",
                    source.meta().provenance(),event.proofs());
                var targets=new ArrayList<Target>();targets.add(event.continuation());
                conditionRegistrations.stream().filter(r->Set.of(event.condition(),"ERROR").contains(r.condition()))
                    .flatMap(r->r.target().stream()).forEach(targets::add);
                var seen=new HashSet<String>();
                for(var target:targets)if(seen.add(target.kind()+"/"+target.reference()))
                    sourceContinuations.add(new SourceContinuation(event.statement(),new Target(target.kind(),target.reference(),java.util.stream.Stream.concat(target.proofs().stream(),java.util.stream.Stream.of(hypothesis)).distinct().toList()),List.of(hypothesis),List.of(prerequisite)));
            }
        }
    }
    private void jsonSourceAlternatives(Ast.PreservedStatement json, String owner, Target next, String premise, String isolation) {
        String id = ids.get(json);
        var alternatives = new LinkedHashMap<String,SourceContinuation>();
        for (String role : List.of("onExceptionClause", "notOnExceptionClause")) {
            var body = json.clauses().stream().filter(c -> c.grammarRule().equals(role))
                .findFirst().map(Ast.StatementClause::nestedStatements).orElse(List.of());
            var hypothesis = proof(id + "/json/" + role, ProofKind.CONTROL_POSSIBILITY,
                "json-generate-" + (role.equals("onExceptionClause") ? "exception" : "success") + "-source-possibility",
                json.meta().provenance(), List.of(premise));
            Target target = body.isEmpty() ? next : occ(body.get(0), hypothesis);
            var alternative = new SourceContinuation(id, target, List.of(hypothesis));
            alternatives.merge(alternative.identity(), alternative, (left, right) ->
                new SourceContinuation(id, left.target(), java.util.stream.Stream.concat(left.proofs().stream(), right.proofs().stream()).distinct().toList()));
            statements(body, owner, next, isolation);
        }
        sourceContinuations.addAll(alternatives.values());
    }
    private boolean sourceMayComplete(Ast.Statement statement) {
        // The grammar owns the hypothetical continuation. Terminal success does not rule
        // out a local CICS condition return. This is never execution proof.
        if(statement instanceof Ast.NextSentenceStatement)return false;
        if(statement instanceof Ast.ModeledStatement m&&Set.of("stopStatement","exitStatement","entryStatement").contains(m.grammarRule()))return false;
        if(statement instanceof Ast.EmbeddedLanguageStatement&&cics!=null) {
            if(cics.abendFact(unit.id(),statement.meta().id()).isPresent())return false;
            var control=cics.fact(unit.id(),statement.meta().id());
            if(control.isPresent()&&control.get().command()==CicsProgramControlAnalyzer.Command.XCTL)
                return control.get().options().stream().anyMatch(o->localConditionEvidence(o.name()));
            var command=cics.commandFact(unit.id(),statement.meta().id());
            if(command.isPresent()&&command.get().command()==CicsCommandSemantics.Kind.RETURN)
                return command.get().options().stream().anyMatch(o->localConditionEvidence(o.name()));
        }
        return true;
    }
    private static boolean localConditionEvidence(String name) {
        // Recognized source option only. Operand qualification belongs to executable authority.
        return name.equals("NOHANDLE")||name.equals("RESP");
    }
    private String procedureRegion(Integer id) { return sectionIds.getOrDefault(id,paragraphIds.get(id)); }
    private String procedureOwner(Integer id) {
        if(sectionIds.containsKey(id))return root;
        String owner=paragraphOwners.get(id);return sectionIds.containsValue(owner)?root:owner;
    }
    private void conditionEvent(Ast.Statement statement,Target continuation,String premise) {
        if(cics==null)return;var command=cics.fact(unit.id(),statement.meta().id()).orElse(null);
        if(command==null||!command.gaps().isEmpty()
            ||command.options().stream().anyMatch(o->!Set.of("PROGRAM","COMMAREA","LENGTH","RESP","RESP2","NOHANDLE").contains(o.name()))
            ||command.options().stream().filter(o->o.name().equals("PROGRAM")).count()!=1
            ||command.options().stream().map(CicsProgramControlAnalyzer.Option::name).distinct().count()!=command.options().size())return;
        boolean bypass=command.options().stream().anyMatch(o->Set.of("RESP","NOHANDLE").contains(o.name()));
        var id="condition-event:"+ids.get(statement)+"/PGMIDERR";
        var authority=proof(id,ProofKind.LOCAL_GRAMMAR,"cics-pgmiderr-condition-event",statement.meta().provenance(),List.of(premise));
        var defaultEvent=bypass?"":"event:"+ids.get(statement)+"/"+(command.command()==CicsProgramControlAnalyzer.Command.XCTL?EventOrigin.XCTL_PGMIDERR:EventOrigin.LINK_PGMIDERR);
        conditionEvents.add(new ConditionEvent(id,ids.get(statement),"PGMIDERR",bypass?EventEligibility.HANDLERS_BYPASSED:EventEligibility.HANDLER_ELIGIBLE,continuation,defaultEvent,List.of(authority)));
    }
    private void exceptionalEvent(Ast.Statement statement,String premise) {
        if(cics==null)return;
        var abend=cics.abendFact(unit.id(),statement.meta().id()).orElse(null);
        EventOrigin origin;EventEligibility eligibility;List<EventPremise> guards;
        if(abend!=null&&abend.eligibility()!=CicsAbendSemantics.Eligibility.UNAVAILABLE) {
            origin=EventOrigin.EXPLICIT_ABEND;eligibility=EventEligibility.valueOf(abend.eligibility().name());guards=List.of();
        } else {
            var command=cics.fact(unit.id(),statement.meta().id()).orElse(null);
            if(command==null||!command.gaps().isEmpty()
                ||command.options().stream().anyMatch(o->!Set.of("PROGRAM","COMMAREA","LENGTH","RESP2").contains(o.name()))
                ||command.options().stream().filter(o->o.name().equals("PROGRAM")).count()!=1
                ||command.options().stream().map(CicsProgramControlAnalyzer.Option::name).distinct().count()!=command.options().size())return;
            origin=command.command()==CicsProgramControlAnalyzer.Command.XCTL?EventOrigin.XCTL_PGMIDERR:EventOrigin.LINK_PGMIDERR;eligibility=EventEligibility.HANDLER_ELIGIBLE;
            guards=List.of(EventPremise.CONDITION_RAISED,EventPremise.DEFAULT_DISPOSITION_APPLIES);
        }
        var id="event:"+ids.get(statement)+"/"+origin;
        var proof=proof(id,ProofKind.LOCAL_GRAMMAR,origin==EventOrigin.EXPLICIT_ABEND?
            "cics-explicit-abend-event":origin==EventOrigin.LINK_PGMIDERR?"cics-link-pgmiderr-default-abend-event":"cics-xctl-pgmiderr-default-abend-event",statement.meta().provenance(),List.of(premise));
        exceptionalEvents.add(new ExceptionalEvent(id,ids.get(statement),origin,"TASK_ABEND",eligibility,
            "CURRENT_EXECUTION_LOGICAL_LEVEL","UNAVAILABLE",guards,List.of(proof)));
    }
    private void publishInvocation(Ast.PerformStatement perform,String owner,String range,String binding,String id,String endpoint,Target resume,String premise) {
        var literal=perform.controls().size()==1&&perform.controls().get(0).expression() instanceof Ast.LiteralExpression l
            ?l.integerValue():Optional.<java.math.BigInteger>empty();
        if(perform.repetition()==Ast.PerformRepetition.TIMES&&literal.isPresent()&&literal.get().signum()<=0) {
            var zero=proof(id+"/zero-count",ProofKind.LOCAL_GRAMMAR,"nonpositive-perform-count-skips-body",perform.meta().provenance(),List.of(premise));
            add(perform,owner,OutcomeKind.NORMAL,"zero-count",resume,"",zero);
            return;
        }
        bindings.put(binding,invocation(binding,id,range,endpoint,resume,perform,premise));
        add(perform,owner,OutcomeKind.LOCAL_INVOKE,"invoke",entry(range,premise),binding,premise);
    }
    private Binding invocation(String binding,String id,String range,String endpoint,Target resume,Ast.PerformStatement p,String proof) {
        var phases=new ArrayList<Phase>();String entry="BODY",completion="RESUME";
        boolean before=p.testMode()==Ast.PerformTestMode.BEFORE;
        int levels=p.controls().stream().mapToInt(Ast.PerformControl::varyingLevel).max().orElse(1);
        if(p.repetition()==Ast.PerformRepetition.VARYING&&levels>1) {
            return varyingInvocation(binding,id,range,endpoint,resume,p,proof,levels);
        }
        if(p.repetition()==Ast.PerformRepetition.UNTIL||p.repetition()==Ast.PerformRepetition.VARYING) {
            String repeat="BODY";completion="test";if(before)entry="test";
            if(p.repetition()==Ast.PerformRepetition.VARYING) {
                phases.add(phase("initial",PhaseKind.EFFECT,"VARY_INITIAL",proof,"next",before?"test":"BODY"));
                phases.add(phase("update",PhaseKind.EFFECT,"VARY_UPDATE",proof,"next",before?"test":"BODY"));
                entry="initial";if(before)completion="update";else repeat="update";
            }
            phases.add(phase("test",PhaseKind.PREDICATE,"UNTIL_PREDICATE",proof,"true","RESUME","false",repeat));
        } else if(p.repetition()==Ast.PerformRepetition.TIMES) {
            phases.add(phase("repeat",PhaseKind.PREDICATE,"COUNT_REPEAT",proof,"true","RESUME","false","BODY"));completion="repeat";
            var literal=p.controls().size()==1&&p.controls().get(0).expression() instanceof Ast.LiteralExpression l?l.integerValue():Optional.<java.math.BigInteger>empty();
            if(literal.isEmpty()) {entry="count-entry";phases.add(phase("count-entry",PhaseKind.PREDICATE,"COUNT_ENTRY",proof,"true","RESUME","false","BODY"));}
        }
        return new Binding(binding,id,range,endpoint,resume,entry,completion,phases,List.of(proof),ReentryPolicy.SOURCE_UNDEFINED);
    }
    /** IBM 6.4 pp. 420–423: innermost increment, outward carry, and current FROM resets. */
    private Binding varyingInvocation(String binding,String id,String range,String endpoint,Target resume,Ast.PerformStatement p,String proof,int levels) {
        var phases=new ArrayList<Phase>();boolean before=p.testMode()==Ast.PerformTestMode.BEFORE;
        for(int level=1;level<=levels;level++) {
            phases.add(levelPhase("initial-"+level,PhaseKind.EFFECT,"VARY_INITIAL",proof,level,"next",
                level<levels?"initial-"+(level+1):before?"test-1":"BODY"));
            String yes=level==1?"RESUME":before?"update-"+(level-1):"test-"+(level-1);
            String no=before?(level<levels?"test-"+(level+1):"BODY"):"update-"+level;
            phases.add(levelPhase("test-"+level,PhaseKind.PREDICATE,"UNTIL_PREDICATE",proof,level,"true",yes,"false",no));
            String updated=before?(level<levels?"reset-"+level+"-"+(level+1):"test-"+level)
                :(level<levels?"initial-"+(level+1):"BODY");
            phases.add(levelPhase("update-"+level,PhaseKind.EFFECT,"VARY_UPDATE",proof,level,"next",updated));
            if(before)for(int inner=level+1;inner<=levels;inner++)
                phases.add(levelPhase("reset-"+level+"-"+inner,PhaseKind.EFFECT,"VARY_INITIAL",proof,inner,"next",
                    inner<levels?"reset-"+level+"-"+(inner+1):"test-"+level));
        }
        return new Binding(binding,id,range,endpoint,resume,"initial-1",(before?"update-":"test-")+levels,phases,List.of(proof),ReentryPolicy.SOURCE_UNDEFINED);
    }
    private static Phase levelPhase(String id,PhaseKind kind,String operation,String proof,int level,String... edges) {
        var result=new ArrayList<PhaseEdge>();for(int i=0;i<edges.length;i+=2)result.add(new PhaseEdge(edges[i],edges[i+1]));
        return new Phase(id,kind,operation,result,List.of(proof),level);
    }
    private Phase phase(String id,PhaseKind kind,String operation,String proof,String... routing) {
        var edges=new ArrayList<PhaseEdge>();for(int i=0;i<routing.length;i+=2)edges.add(new PhaseEdge(routing[i],routing[i+1]));
        return new Phase(id,kind,operation,edges,List.of(proof));
    }
    private int index(Integer node){if(node==null)return -1;for(int i=0;i<paragraphs.size();i++)if(paragraphs.get(i).meta().id()==node)return i;return -1;}
    private Target arm(String parent,String role,RegionKind kind,List<Ast.Statement> body,String p,String isolation) {
        String region=parent+"/"+role;makeRegion(region,kind,parent,body,complete(parent,p),p);
        statements(body,region,complete(region,p),isolation);return entry(region,p);
    }
    private void makeRegion(String id,RegionKind kind,String parent,List<Ast.Statement> body,Target after,String p) {
        putRegion(id,kind,parent,body.isEmpty()?complete(id,p):occ(body.get(0),p),body.stream().map(ids::get).filter(Objects::nonNull).toList(),after,p);
    }
    private void putRegion(String id,RegionKind kind,String parent,Target entry,List<String> members,Target after,String p) {
        regions.put(id,new Region(id,kind,parent,entry,members,List.of(),"boundary:"+id,List.of(p)));
        boundaries.put("boundary:"+id,new Boundary("boundary:"+id,id,after,List.of(p)));
        if(!parent.isEmpty())subregions.computeIfAbsent(parent,k->new ArrayList<>()).add(id);
    }
    private void add(Ast.Statement s,String owner,OutcomeKind kind,String role,Target target,String binding,String p) {
        String id=ids.get(s),edge="outcome:"+id+"/"+role;
        outcomes.put(edge,new Outcome(edge,id,kind,role,target,binding,List.of(p)));
        var previous=occurrences.get(id);var edges=new ArrayList<String>();if(previous!=null)edges.addAll(previous.outcomes());edges.add(edge);
        occurrences.put(id,new Occurrence(id,owner,edges,List.of(p)));
    }
    /** Publish internal control once, before binding per-event destinations. */
    private void fileFlows() {
        var groups=new TreeMap<String,List<CobolSemanticProduct.FileUse>>();
        files.operations().uses().forEach(u->groups.computeIfAbsent("statement:"+u.statement().localId(),k->new ArrayList<>()).add(u));
        var byId=new HashMap<String,Ast.Statement>();ids.forEach((statement,id)->byId.put(id,statement));
        var sorts=new HashMap<String,CobolSemanticProduct.FileSortPlan>();files.sortPlans().forEach(plan->sorts.put("statement:"+plan.statement().localId(),plan));
        for(var group:groups.entrySet()) {
            var id=group.getKey();var uses=group.getValue();uses.sort(Comparator.comparingInt(CobolSemanticProduct.FileUse::ordinal));
            var normal=outcomes.get("outcome:"+id+"/normal");
            if(normal==null||normal.target().kind()==TargetKind.UNKNOWN_LOCAL)continue;
            var sort=sorts.get(id);
            boolean linear=uses.size()>1&&uses.stream().allMatch(u->u.command()==CobolSemanticProduct.FileCommand.OPEN||u.command()==CobolSemanticProduct.FileCommand.CLOSE);
            if(!linear&&(sort==null||sort.work()<0||!sort.procedures().isEmpty()))continue;
            var statement=byId.get(id);var p=proof(id+"/file-flow",ProofKind.LOCAL_GRAMMAR,
                linear?"file-sequential-operands":"file-input-work-output-phases",statement.meta().provenance(),normal.proofs());
            var points=new ArrayList<FilePoint>();Target first;
            if(linear) {
                for(int i=0;i<uses.size();i++)points.add(new FilePoint(id+"/file/use/"+uses.get(i).ordinal(),FilePointKind.USE,uses.get(i).ordinal(),
                    List.of(i+1<uses.size()?filePoint(id+"/file/use/"+uses.get(i+1).ordinal(),p):normal.target()),List.of(p)));
                first=filePoint(points.get(0).id(),p);
            } else {
                var output=filePhase(id,"output",sort.outputs(),normal.target(),p,points);
                var work=id+"/file/use/"+sort.work();points.add(new FilePoint(work,FilePointKind.USE,sort.work(),List.of(output),List.of(p)));
                first=filePhase(id,"input",sort.inputs(),filePoint(work,p),p,points);
            }
            fileFlows.add(new FileFlow(id,first,points,List.of(p)));
        }
    }
    private Target filePhase(String owner,String phase,List<Integer> ordinals,Target next,String p,List<FilePoint> points) {
        if(ordinals.isEmpty())return next;
        var selector=owner+"/file/"+phase;var alternatives=new ArrayList<Target>();
        for(var ordinal:ordinals) {
            var id=owner+"/file/use/"+ordinal;alternatives.add(filePoint(id,p));
            points.add(new FilePoint(id,FilePointKind.USE,ordinal,List.of(filePoint(selector,p)),List.of(p)));
        }
        alternatives.add(next);points.add(new FilePoint(selector,FilePointKind.CHOICE,-1,alternatives,List.of(p)));
        return filePoint(selector,p);
    }
    private static Target filePoint(String id,String proof){return new Target(TargetKind.FILE_POINT,id,List.of(proof));}
    private void fileRoutes() {
        var byId=new HashMap<String,Ast.Statement>();ids.forEach((s,id)->byId.put(id,s));
        var continuations=new HashMap<String,Target>();
        fileFlows.forEach(f->f.points().stream().filter(p->p.kind()==FilePointKind.USE)
            .forEach(p->continuations.put(f.statement()+"/"+p.ordinal(),p.targets().get(0))));
        for(var use:files.operations().uses()) {
            String id="statement:"+use.statement().localId();var s=byId.get(id);if(s==null)continue;
            var occurrence=occurrences.get(id);var p=occurrence.proofs().get(0);
            var normal=outcomes.get("outcome:"+id+"/normal");
            for(var route:use.control().routes())for(int i=0;i<route.destinations().size();i++) {
                // Recovery can retain FILE uses without proving the statement's control scope.
                // Keep every role, but do not invent continuation or entry to an unbuilt handler.
                if(normal==null||normal.target().kind()==TargetKind.UNKNOWN_LOCAL) {
                    var partial=proof(id+"/file/"+use.ordinal()+"/"+route.event()+"/"+i,ProofKind.PARTIAL_UNKNOWN,
                        "file-statement-control-not-proven",s.meta().provenance(),List.of(p));
                    add(s,occurrence.region(),OutcomeKind.UNKNOWN_LOCAL,"file/"+use.ordinal()+"/"+route.event()+"/"+i,
                        unknown(occurrence.region(),partial),"",partial);
                    continue;
                }
                var destination=route.destinations().get(i);Target target;
                if(destination.kind()==CobolSemanticProduct.FileDestinationKind.HANDLER) {
                    String region="region:"+id+"/file/handler-"+destination.handler().orElseThrow();
                    target=entry(region,p);
                } else if(destination.kind()==CobolSemanticProduct.FileDestinationKind.USE) {
                    // Callback resolution is explicit; no hidden FILE edge may escape inventory.
                    var declaration=files.declaratives().stream().filter(d->d.id().equals(destination.declarative().orElseThrow())).findFirst().orElseThrow();
                    var partial=proof(id+"/use-callback",ProofKind.PARTIAL_UNKNOWN,"declarative-callback-binding-not-published",s.meta().provenance(),List.of(p));
                    target=unknown(occurrence.region(),partial);
                } else target=continuations.getOrDefault(id+"/"+use.ordinal(),normal.target());
                var premise=proof(id+"/file/"+use.ordinal()+"/"+route.event()+"/"+i,ProofKind.LOCAL_GRAMMAR,
                    "file-event-"+route.event()+"/"+destination.kind(),s.meta().provenance(),List.of(p));
                add(s,occurrence.region(),OutcomeKind.BRANCH,"file/"+use.ordinal()+"/"+route.event()+"/"+i,target,"",premise);
            }
        }
        for(var sort:files.sortPlans())if(!sort.procedures().isEmpty()) {
            String id="statement:"+sort.statement().localId();var s=byId.get(id);var occurrence=occurrences.get(id);var p=occurrence.proofs().get(0);
            var partial=proof(id+"/sort-callback",ProofKind.PARTIAL_UNKNOWN,"sort-callback-binding-not-published",s.meta().provenance(),List.of(p));
            // A callback's entry/return order is not yet published by this slice.
            // Existing FILE event metadata cannot license a bypass around it.
            for(var edge:occurrence.outcomes()) {
                var old=outcomes.get(edge);
                outcomes.put(edge,new Outcome(old.id(),id,OutcomeKind.UNKNOWN_LOCAL,old.role(),unknown(occurrence.region(),partial),"",List.of(partial)));
            }
        }
    }
    private Target occ(Ast.Statement s,String p){return new Target(TargetKind.OCCURRENCE,Objects.requireNonNull(ids.get(s)),List.of(p));}
    private static Target entry(String r,String p){return new Target(TargetKind.REGION_ENTRY,r,List.of(p));}
    private static Target complete(String r,String p){return new Target(TargetKind.COMPLETE,r,List.of(p));}
    private static Target unknown(String r,String p){return new Target(TargetKind.UNKNOWN_LOCAL,r,List.of(p));}
    private String proof(String id,ProofKind kind,String rule,Ast.SourceProvenance origin,List<String> dependencies) {
        String key="proof:"+id;var premises=new ArrayList<String>(dependencies);
        if(!origin.includeChain().isEmpty()&&kind!=ProofKind.EXPANDED_INCLUDE) {
            String include=key+"/expanded-include";
            proofs.put(include,new Proof(include,ProofKind.EXPANDED_INCLUDE,"expanded-include-syntax",provenance(origin),List.of()));premises.add(include);
        }
        proofs.put(key,new Proof(key,kind,rule,provenance(origin),premises));return key;
    }
    private static CobolSemanticProduct.Provenance provenance(Ast.SourceProvenance p){return new CobolSemanticProduct.Provenance(location(p.expanded()),location(p.original()),
        p.includeChain().stream().map(f->new CobolSemanticProduct.IncludeFrame(f.includingFile(),f.requestedName(),f.includedFile(),f.includeLine())).toList(),p.exact());}
    private static CobolSemanticProduct.Location location(Ast.SourceLocation p){return new CobolSemanticProduct.Location(p.file(),p.startLine(),p.startColumn(),p.endLine(),p.endColumn());}
}
