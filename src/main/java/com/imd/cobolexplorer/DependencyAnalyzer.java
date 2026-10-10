package com.imd.cobolexplorer;

import java.nio.file.*;
import java.nio.charset.Charset;
import java.util.*;
import org.antlr.v4.runtime.*;
import com.imd.cobolexplorer.semanticproduct.CobolSemanticProduct;

/** Headless single-process analysis. Never builds/publishes the Semantic Product,
 * physical storage, AIR, presentation snapshots or intermediate files. */
public final class DependencyAnalyzer {
    public static final long DEFAULT_MAX_WORK=100_000_000L;
    public record At(String file,int line) { }
    public record Dependency(String type,String name,At at) { }
    public record Program(String program,List<Dependency> dependencies) { }
    public enum Solver {
        PRECISE("precise"),REACHING_DEFINITIONS("reaching-definitions");
        private final String cliName;
        Solver(String cliName){this.cliName=cliName;}
        public String cliName(){return cliName;}
        static Solver parse(String name){
            for(var solver:values())if(solver.cliName.equals(name))return solver;
            throw new IllegalArgumentException("Expected --solver precise or reaching-definitions");
        }
    }
    public record ReachingDefinitionsMetrics(int physicalNodes,long physicalEdges,int policies,long controlWorkItems,long deliveries,
            int definitionNodes,long definitionEdges,int components,int joinComponents,int operations,long evaluations,
            long propagations,long candidateSlots,long workItems,long bypassedIdentityCells,int lookupCells,long localAddressAlternatives,
            int tables,int cells,int tableDemandPasses) { }
    public record Metrics(long parseNanos,long bindingNanos,long cfgNanos,long dataflowNanos,long resolutionNanos,long totalNanos,long workItems,int contexts,int trackedDeclarations,long evaluations,long reusedEvaluations,long instantiationEvaluations,long resolutionWorkItems,int parametricCalculations,int specializedDeclarations,int controlSummaries,int physicalControlNodes,int physicalControlEdges,int controlObligations,int controlResultColumns,long controlWorkItems,long controlResultPairs,long controlResultFacts,long resultDeliveries,long decisionNodes,int predicateInputs,long decisionOperations,long liftedOperations,int materializedTableElements,int tableDemandPasses,String solver,List<ReachingDefinitionsMetrics> reachingDefinitions) { }
    public record Result(List<Program> programs,List<String> diagnostics,Metrics metrics) { }
    public record Options(List<Path> copyDirectories,SourceNormalizer.SourceFormat format,Charset charset,Path sourceInventory,String parser,long maxWork,Solver solver) {
        public Options {Objects.requireNonNull(solver);copyDirectories=List.copyOf(copyDirectories);if(maxWork<1)throw new IllegalArgumentException("positive --max-work required");}
        public Options(List<Path> copyDirectories,SourceNormalizer.SourceFormat format,Charset charset,Path sourceInventory,String parser,long maxWork){
            this(copyDirectories,format,charset,sourceInventory,parser,maxWork,Solver.PRECISE);
        }
        public static Options defaults(){return new Options(List.of(),SourceNormalizer.SourceFormat.FIXED,Charset.forName("UTF-8"),null,"direct-ast-lab",DEFAULT_MAX_WORK);}
    }
    public Result analyze(Path source,Options options)throws Exception {
        long started=System.nanoTime();var binding=Bindings.cobol();
        String raw=Files.readString(source,options.charset());String file=source.getFileName().toString();
        var normalization=SourceNormalizer.normalize(raw,file,new SourceNormalizer.Options(options.format(),SourceNormalizer.DebugLinePolicy.EXCLUDE));
        var includes=new ArrayList<>(options.copyDirectories());if(source.toAbsolutePath().getParent()!=null)includes.add(source.toAbsolutePath().getParent());
        var inventory=options.sourceInventory()==null?SourceArtifactInventory.empty():SourceArtifactInventory.read(options.sourceInventory());
        var prep=new PreprocessorEngine(binding,new CopybookLibrary(includes),inventory).process(normalization.sourceMap(),file);
        var diagnostics=new ArrayList<Diagnostic>(prep.diagnostics());
        Lexer lexer=binding.cobolLexer(CharStreams.fromString(prep.text(),file));lexer.removeErrorListeners();
        lexer.addErrorListener(new AntlrDiagnosticListener(binding.name(),Diagnostic.Phase.LEXER,file,diagnostics));
        var tokens=new CommonTokenStream(lexer);tokens.fill();
        int lexerErrors=(int)diagnostics.stream().filter(d->d.phase()==Diagnostic.Phase.LEXER).count();
        var frontend=CobolFrontend.parse(binding,tokens,prep.text(),prep.sourceMap(),file,diagnostics,options.parser(),prep.errors()==0,lexerErrors==0);
        int parserErrors=(int)diagnostics.stream().filter(d->d.phase()==Diagnostic.Phase.PARSER).count();
        if(lexerErrors!=0||parserErrors!=0)throw new IllegalArgumentException("INVALID_SOURCE: lexical/parser errors; no result published for "+file);
        long parse=System.nanoTime()-started,mark=System.nanoTime();
        var build=frontend.ast();var compilation=build.compilationUnit();
        if(compilation.programUnits().isEmpty())throw new IllegalArgumentException("No COBOL program in "+file);
        try(var nativeScope=frontend.route().equals("native")?DirectParseScope.enter():null) {
            var tables=new CompilationUnitSymbolTableBuilder().build(compilation);
            var occurrences=new LinkedHashMap<ResolutionContracts.ProgramUnitId,ReferenceOccurrences>();
            for(var unit:compilation.programUnits()) {var table=tables.forProgramUnit(unit.id()).orElseThrow().symbolTable();
                occurrences.put(unit.id(),new ReferenceOccurrenceCollector().collect(unit.id(),unit.program(),AstScopeIndex.build(unit.program(),table)));}
            var policy=ResolutionContracts.CobolResolutionPolicy.initial().withPgmnameMode(prep.pgmnameMode()).withDynamMode(prep.dynamMode()).withDllMode(prep.dllMode()).withTruncMode(prep.truncMode());
            var resolution=new CobolReferenceResolver(policy).resolve(compilation,tables,occurrences);
            var report=ResolutionAnalysisReport.compose(build,new ResolutionAnalysisReport.FrontendState(prep.errors(),lexerErrors,parserErrors,diagnostics),occurrences,resolution,ExternalClassification.empty());
            var cics=new CicsProgramControlAnalyzer().analyze(build,report).withHandlers(CicsHandlerSemantics.analyze(build,tables,resolution)).withAbendEvents(CicsAbendSemantics.analyze(build)).withCommands(CicsCommandSemantics.analyze(build));
            var conditions=ConditionNameSemantics.analyze(build,tables,resolution);
            long bind=System.nanoTime()-mark,cfg=0,flow=0,resolve=0,work=0,evaluations=0,reusedEvaluations=0,instantiationEvaluations=0,resolutionWorkItems=0,controlWorkItems=0,controlResultPairs=0,controlResultFacts=0,resultDeliveries=0,decisionNodes=0,decisionOperations=0,liftedOperations=0;int contexts=0,tracked=0,materializedTableElements=0,tableDemandPasses=0,parametricCalculations=0,specializedDeclarations=0,controlSummaries=0,physicalControlNodes=0,physicalControlEdges=0,controlObligations=0,controlResultColumns=0,predicateInputs=0;
            var sourceFacts=SourceDependencySemantics.associate(build,prep.sourceDependencies(),prep.sourceDependencyGaps());
            var notices=new TreeSet<String>();normalization.diagnostics().forEach(d->notices.add("NORMALIZATION: "+d));for(var d:diagnostics)notices.add(d.code()+": "+d.message());notices.addAll(prep.sourceDependencyGaps());
            if(frontend.route().equals("fallback"))notices.add("PARSER_FALLBACK: "+frontend.fallbackReason());
            var programs=new ArrayList<Program>();var definitionMetrics=new ArrayList<ReachingDefinitionsMetrics>();
            for(var unit:compilation.programUnits()) {
                mark=System.nanoTime();var table=tables.forProgramUnit(unit.id()).orElseThrow().symbolTable();
                var declarations=new DependencyDeclarations(unit,compilation,tables,resolution);
                var handles=new IdentityHashMap<Ast.Statement,CobolSemanticProduct.StatementId>();
                var nodes=new ArrayList<Ast.Node>();var todo=new ArrayDeque<Ast.Node>();todo.add(unit.program());
                var boundary=new CobolSemanticProduct.UnitId(unit.id().compilationUnitId(),unit.id().structuralPath(),unit.id().canonicalProgramName());
                while(!todo.isEmpty()) {var n=todo.removeFirst();if(n instanceof Ast.Program&&n!=unit.program())continue;nodes.add(n);
                    if(n instanceof Ast.Statement s)handles.put(s,new CobolSemanticProduct.StatementId(boundary,s.meta().id()));todo.addAll(Ast.children(n));}
                var queries=new ArrayList<DependencyFlow.Query>();
                for(var n:nodes) {
                    if(n instanceof Ast.CallStatement call)queries.add(new DependencyFlow.Query(call,"program",call.target(),call.literalText().map(Ast.LogicalText::value)));
                    if(n instanceof Ast.EmbeddedLanguageStatement e&&e.language()==Ast.EmbeddedLanguage.CICS) {
                        var pgm=cics.fact(unit.id(),e.meta().id());
                        if(pgm.isPresent())queries.add(new DependencyFlow.Query(e,"program",operand(e,"PROGRAM"),pgm.get().literal()));
                        var f=cics.fileFact(unit.id(),e.meta().id());
                        if(f.isPresent()&&f.get().targetMode()==CicsFileControlAnalyzer.TargetMode.INPUT)queries.add(new DependencyFlow.Query(e,"file",operand(e,"FILE"),f.get().literal()));
                    }
                }
                var deps=new TreeMap<String,Dependency>();
                // Source-only dependencies do not require runtime value/control
                // exploration. An empty query slice has no dataflow to solve.
                if(!queries.isEmpty()) {
                    var topology=ControlTopologySemantics.analyze(unit,table,resolution,report,handles,CobolSemanticProduct.FileInventory.unavailable(),cics);
                    cfg+=System.nanoTime()-mark;mark=System.nanoTime();
                    var values=DependencyFlow.analyze(unit,declarations,topology,queries,options.maxWork(),cics,conditions.uses(unit.id()),options.solver());
                    flow+=System.nanoTime()-mark;
                    work+=values.visits;evaluations+=values.evaluations;reusedEvaluations+=values.reusedEvaluations;
                    instantiationEvaluations+=values.instantiationEvaluations;resolutionWorkItems+=values.resolutionVisits;
                    parametricCalculations+=values.calculations.size();specializedDeclarations+=values.specialized.size();
                    if(values.controlSummary!=null) {
                        var summary=values.controlSummary;
                        controlSummaries+=summary.summaryCount;physicalControlNodes+=summary.physicalNodes;
                        physicalControlEdges+=summary.physicalEdges;controlObligations+=summary.obligations;
                        controlResultColumns+=summary.resultColumns;controlWorkItems+=summary.visits;
                        controlResultPairs+=summary.resultPairs;controlResultFacts+=summary.resultFacts;
                    } else {
                        var summary=values.definitionsMetrics;
                        physicalControlNodes+=summary.physicalNodes();physicalControlEdges+=summary.physicalEdges();
                        controlObligations+=summary.policies();controlWorkItems+=summary.controlWorkItems();
                        definitionMetrics.add(summary);
                    }
                    resultDeliveries+=values.resultDeliveries;decisionNodes+=values.decisionNodes;predicateInputs+=values.tests.size();
                    decisionOperations+=values.decisionOperations;liftedOperations+=values.liftedOperations;
                    contexts+=values.contexts.size();tracked+=values.demand.size();materializedTableElements+=values.elements.size();
                    tableDemandPasses+=values.tableDemandPasses;mark=System.nanoTime();notices.addAll(values.diagnostics);
                    DefinitionsPublication.publish(values.answers,(type,name,at)->add(deps,type,name,at),notices);
                }
                for(var f:sourceFacts.get(unit.id()).occurrences()) {
                    String type=switch(f.kind()){case COPYBOOK->"copybook";case DCLGEN->"dclgen";case SQL_INCLUDE->"sql-include";case DB2_TABLE->"db2-table";};
                    var at=f.provenance().original();String name=f.kind().name().equals("DB2_TABLE")&&!f.qualification().isBlank()?f.qualification()+"."+f.name():f.name();
                    add(deps,type,name,new Ast.SourceLocation(at.file(),at.startLine(),at.startColumn(),at.endLine(),at.endColumn()));
                    if(!Set.of("RESOLVED","NOT_APPLICABLE").contains(f.resolution().name()))notices.add("SOURCE_ARTIFACT_"+f.resolution()+": "+f.name());
                }
                for(var n:nodes) {
                    if(n instanceof Ast.FileBinding f) {
                        if(f.control()!=null&&f.control().assignment().form()==Ast.AssignmentForm.IBM_NAME)add(deps,"file",f.control().assignment().externalFileName(),f.meta().provenance().original());
                        else {add(deps,"logical-file",f.logicalName(),f.meta().provenance().original());notices.add("FILE_EXTERNAL_NAME_UNKNOWN: "+f.logicalName());}
                    }
                    if(n instanceof Ast.FileDescription f&&!nodes.stream().anyMatch(x->x instanceof Ast.FileBinding b&&b.logicalName().equalsIgnoreCase(f.fileName())))add(deps,"logical-file",f.fileName(),f.meta().provenance().original());
                }
                programs.add(new Program(unit.id().canonicalProgramName(),List.copyOf(deps.values())));resolve+=System.nanoTime()-mark;
            }
            programs.sort(Comparator.comparing(Program::program));
            return new Result(List.copyOf(programs),List.copyOf(notices),new Metrics(parse,bind,cfg,flow,resolve,System.nanoTime()-started,work,contexts,tracked,evaluations,reusedEvaluations,instantiationEvaluations,resolutionWorkItems,parametricCalculations,specializedDeclarations,controlSummaries,physicalControlNodes,physicalControlEdges,controlObligations,controlResultColumns,controlWorkItems,controlResultPairs,controlResultFacts,resultDeliveries,decisionNodes,predicateInputs,decisionOperations,liftedOperations,materializedTableElements,tableDemandPasses,options.solver().cliName(),List.copyOf(definitionMetrics)));
        }
    }
    private static Ast.Expression operand(Ast.EmbeddedLanguageStatement statement,String option) {
        if(option.equals("FILE") && statement.hostOperands().stream().noneMatch(o->o.option().equals("FILE")))option="DATASET";
        final String selected=option;
        return statement.expressionOperands().stream().filter(o->o.option().equals(selected)).map(Ast.EmbeddedExpressionOperand::expression).findFirst()
            .or(()->statement.hostOperands().stream().filter(o->o.option().equals(selected)).map(Ast.EmbeddedHostOperand::reference).map(Ast.Expression.class::cast).findFirst()).orElse(null);
    }
    private static void add(Map<String,Dependency> dependencies,String type,String raw,Ast.SourceLocation source) {
        String name=raw.stripTrailing();if(name.isBlank())return;
        var value=new Dependency(type,name,new At(source.file(),source.startLine()));
        dependencies.merge(type+"\0"+name,value,(a,b)->Comparator.comparing((Dependency d)->d.at().file()).thenComparingInt(d->d.at().line()).compare(a,b)<=0?a:b);
    }
}
