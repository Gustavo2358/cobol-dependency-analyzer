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
    private final List<ExceptionalEvent> exceptionalEvents=new ArrayList<>();
    private final Map<String,Proof> proofs=new TreeMap<>();
    private final Map<String,List<String>> subregions=new HashMap<>();
    private final CobolSemanticProduct.FileInventory files;
    private final CicsProgramControlAnalyzer.Contribution cics;
    private final boolean independent;
    private final String root;

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
            statements(direct,id,tail,isolation);
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
            statements(direct,id,complete(id,ps),isolation);
        }
        var roots=new ArrayList<Ast.Statement>();
        for(var child:division.children())if(child instanceof Ast.Sentence s)roots.addAll(s.statements());
        var end=paragraphs.stream().filter(p->paragraphOwners.get(p.meta().id()).equals(root)).findFirst().map(p->entry(paragraphIds.get(p.meta().id()),rp)).orElse(unknown(root,rp));
        statements(roots,root,end,isolation);
        var start=division.procedureEntry().flatMap(Ast.ProcedureEntry::startStatementId).map(nodes::get)
            .filter(Ast.Statement.class::isInstance).map(Ast.Statement.class::cast).filter(ids::containsKey).map(s->occ(s,rp)).orElse(unknown(root,rp));
        putRegion(root,RegionKind.PROCEDURE,"",start,roots.stream().map(ids::get).filter(Objects::nonNull).toList(),unknown(root,rp),rp);
        // Occurrences under unsupported constructs remain inventoried with a local
        // unknown outcome; they are never silently assigned a normal successor.
        ids.entrySet().stream().sorted(Map.Entry.comparingByValue()).forEach(e->{if(!occurrences.containsKey(e.getValue())){
            var p=proof(e.getValue()+"/unplaced",ProofKind.PARTIAL_UNKNOWN,"unsupported-region-membership",e.getKey().meta().provenance(),List.of(isolation));
            add(e.getKey(),root,OutcomeKind.UNKNOWN_LOCAL,"unknown",unknown(root,p),"",p);}});
        fileFlows();
        fileRoutes();
        for(var r:new ArrayList<>(regions.values()))regions.put(r.id(),new Region(r.id(),r.kind(),r.parent(),r.entry(),occurrences.values().stream().filter(o->o.region().equals(r.id())).map(Occurrence::statement).toList(),
            r.kind()==RegionKind.RANGE?r.regions():subregions.getOrDefault(r.id(),List.of()).stream().sorted().toList(),r.boundary(),r.proofs()));
        return new ControlTopology(fileFlows.isEmpty()?"FRONTEND_CONTROL_TOPOLOGY_R1":"FRONTEND_CONTROL_TOPOLOGY_R2",List.copyOf(occurrences.values()),List.copyOf(regions.values()),
            List.copyOf(boundaries.values()),List.copyOf(outcomes.values()),List.copyOf(bindings.values()),List.copyOf(proofs.values()),exceptionalEvents,fileFlows);
    }
    private void statements(List<Ast.Statement> list,String owner,Target end,String isolation) {
        for(int i=0;i<list.size();i++) {
            var s=list.get(i);if(!ids.containsKey(s))continue;
            String id=ids.get(s);var p=proof(id,ProofKind.LOCAL_GRAMMAR,"statement-scope",s.meta().provenance(),List.of(isolation));
            var next=i+1<list.size()&&ids.containsKey(list.get(i+1))?occ(list.get(i+1),p):end;
            if(!independent){add(s,owner,OutcomeKind.UNKNOWN_LOCAL,"unknown",unknown(owner,p),"",p);continue;}
            exceptionalEvent(s,p);
            if(s instanceof Ast.ModeledStatement m&&m.exitKind().isPresent()) {
                var kind=m.exitKind().orElseThrow();String scope=owner;
                var wanted=kind==Ast.ExitKind.PARAGRAPH?RegionKind.PARAGRAPH:RegionKind.INLINE_BODY;
                while(!scope.isEmpty()&&(!regions.containsKey(scope)||regions.get(scope).kind()!=wanted))
                    scope=regions.containsKey(scope)?regions.get(scope).parent():"";
                if(!scope.isEmpty()) {
                    var exitProof=proof(id+"/exit",ProofKind.LOCAL_GRAMMAR,"exit-"+kind.name().toLowerCase(Locale.ROOT),s.meta().provenance(),List.of(p));
                    var target=kind==Ast.ExitKind.PERFORM_CYCLE?complete(scope,exitProof):new Target(TargetKind.ESCAPE,scope,List.of(exitProof));
                    add(s,owner,OutcomeKind.EXPLICIT_TRANSFER,"exit",target,"",exitProof);
                } else if(kind!=Ast.ExitKind.PARAGRAPH) add(s,owner,OutcomeKind.EXPLICIT_TRANSFER,"exit-ignored",next,"",p);
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
                var normal=proof(id+"/sql-normal",ProofKind.LOCAL_GRAMMAR,"db2-select-into-successful-return",s.meta().provenance(),List.of(p));
                add(s,owner,OutcomeKind.NORMAL,"normal",next,"",normal);
                var other=proof(id+"/sql-other",ProofKind.PARTIAL_UNKNOWN,"db2-select-into-other-outcomes",s.meta().provenance(),List.of(normal));
                add(s,owner,OutcomeKind.UNKNOWN_LOCAL,"sql/other",unknown(owner,other),"",other);continue;
            }
            boolean completion=division.normalCompletionStatements().contains(s.meta().id());
            if(s instanceof Ast.EmbeddedLanguageStatement)completion=cics!=null&&cics.boundedLocal(unit.id(),s);
            boolean registration=cics!=null&&cics.handlerOrdinaryCompletion(unit.id(),s);
            var completionProof=registration?proof(id+"/handler-normal",ProofKind.LOCAL_GRAMMAR,
                "cics-handle-abend-ordinary-return",s.meta().provenance(),List.of(p)):p;
            completion|=registration;
            add(s,owner,completion?OutcomeKind.NORMAL:OutcomeKind.UNKNOWN_LOCAL,completion?"normal":"unknown",completion?next:unknown(owner,p),"",completionProof);
        }
    }
    private String procedureRegion(Integer id) { return sectionIds.getOrDefault(id,paragraphIds.get(id)); }
    private String procedureOwner(Integer id) {
        if(sectionIds.containsKey(id))return root;
        String owner=paragraphOwners.get(id);return sectionIds.containsValue(owner)?root:owner;
    }
    private void exceptionalEvent(Ast.Statement statement,String premise) {
        if(cics==null)return;
        var abend=cics.abendFact(unit.id(),statement.meta().id()).orElse(null);
        EventOrigin origin;EventEligibility eligibility;List<EventPremise> guards;
        if(abend!=null&&abend.eligibility()!=CicsAbendSemantics.Eligibility.UNAVAILABLE) {
            origin=EventOrigin.EXPLICIT_ABEND;eligibility=EventEligibility.valueOf(abend.eligibility().name());guards=List.of();
        } else {
            var command=cics.fact(unit.id(),statement.meta().id()).orElse(null);
            if(command==null||command.command()!=CicsProgramControlAnalyzer.Command.XCTL||!command.gaps().isEmpty()
                ||command.options().stream().anyMatch(o->!Set.of("PROGRAM","COMMAREA","LENGTH","RESP2").contains(o.name()))
                ||command.options().stream().filter(o->o.name().equals("PROGRAM")).count()!=1
                ||command.options().stream().map(CicsProgramControlAnalyzer.Option::name).distinct().count()!=command.options().size())return;
            origin=EventOrigin.XCTL_PGMIDERR;eligibility=EventEligibility.HANDLER_ELIGIBLE;
            guards=List.of(EventPremise.CONDITION_RAISED,EventPremise.DEFAULT_DISPOSITION_APPLIES);
        }
        var id="event:"+ids.get(statement)+"/"+origin;
        var proof=proof(id,ProofKind.LOCAL_GRAMMAR,origin==EventOrigin.EXPLICIT_ABEND?
            "cics-explicit-abend-event":"cics-xctl-pgmiderr-default-abend-event",statement.meta().provenance(),List.of(premise));
        exceptionalEvents.add(new ExceptionalEvent(id,ids.get(statement),origin,"TASK_ABEND",eligibility,
            "CURRENT_EXECUTION_LOGICAL_LEVEL","UNAVAILABLE",guards,List.of(proof)));
    }
    private void publishInvocation(Ast.PerformStatement perform,String owner,String range,String binding,String id,String endpoint,Target resume,String premise) {
        var literal=perform.controls().size()==1&&perform.controls().get(0).expression() instanceof Ast.LiteralExpression l
            ?l.integerValue():Optional.<java.math.BigInteger>empty();
        if(perform.repetition()==Ast.PerformRepetition.TIMES&&literal.isPresent()&&literal.get().signum()<=0) {
            var partial=proof(id+"/count-capability",ProofKind.PARTIAL_UNKNOWN,
                "nonpositive-literal-count-outside-qualified-profile",perform.meta().provenance(),List.of(premise));
            add(perform,owner,OutcomeKind.UNKNOWN_LOCAL,"invoke-unavailable",unknown(range,partial),"",partial);
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
        return new Binding(binding,id,range,endpoint,resume,entry,completion,phases,List.of(proof));
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
        return new Binding(binding,id,range,endpoint,resume,"initial-1",(before?"update-":"test-")+levels,phases,List.of(proof));
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
            for(var route:use.control().routes())for(int i=0;i<route.destinations().size();i++) {
                var destination=route.destinations().get(i);Target target;
                if(destination.kind()==CobolSemanticProduct.FileDestinationKind.HANDLER) {
                    String region="region:"+id+"/file/handler-"+destination.handler().orElseThrow();
                    target=entry(region,p);
                } else if(destination.kind()==CobolSemanticProduct.FileDestinationKind.USE) {
                    // Callback resolution is explicit; no hidden FILE edge may escape inventory.
                    var declaration=files.declaratives().stream().filter(d->d.id().equals(destination.declarative().orElseThrow())).findFirst().orElseThrow();
                    var partial=proof(id+"/use-callback",ProofKind.PARTIAL_UNKNOWN,"declarative-callback-binding-not-published",s.meta().provenance(),List.of(p));
                    target=unknown(occurrence.region(),partial);
                } else target=continuations.getOrDefault(id+"/"+use.ordinal(),outcomes.get("outcome:"+id+"/normal").target());
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
