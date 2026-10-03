package io.github.gustavo2358.cobolexplorer;

import java.util.*;
import io.github.gustavo2358.cobolexplorer.antlr.*;
import org.antlr.v4.runtime.*;

/** Source command/option qualification. Does not compute control, runtime values or handlers. */
public final class CicsCommandSemantics {
    public enum Kind { SYNCPOINT, SYNCPOINT_ROLLBACK, RETURN, RECEIVE_MAP, SEND_MAP, SEND_TERMINAL, RETRIEVE, ASKTIME, FORMATTIME, ASSIGN, INQUIRE_PROGRAM, SEND_TEXT, WRITEQ_TD }
    public enum SyntaxStatus { SUPPORTED, UNAVAILABLE }
    public record HostEffects(List<Integer> literalOptions) { public HostEffects { literalOptions=List.copyOf(literalOptions); } }
    public record Fact(Kind command,SyntaxStatus syntaxStatus,String raw,List<CicsCommandSyntax.Option> options,List<String> gaps,Optional<HostEffects> hostEffects) {
        public Fact { Objects.requireNonNull(command);Objects.requireNonNull(syntaxStatus);options=List.copyOf(options);gaps=List.copyOf(gaps); }
        public Fact(Kind c,SyntaxStatus s,String r,List<CicsCommandSyntax.Option> o,List<String> g){this(c,s,r,o,g,Optional.empty());}
        public boolean supported(){return syntaxStatus==SyntaxStatus.SUPPORTED;}
    }

    static boolean extended(Kind k) {return switch(k){case ASKTIME,FORMATTIME,ASSIGN,INQUIRE_PROGRAM,SEND_TEXT,WRITEQ_TD->true;default->false;};}
    static Set<String> extra(Kind k) {return switch(k) {
        case ASKTIME->Set.of("ABSTIME");
        case FORMATTIME->Set.of("ABSTIME","DATESEP","TIMESEP","TIME","MILLISECONDS","YYDDD","YYMMDD","MMDDYY","MMDDYYYY","YYYYMMDD");
        case ASSIGN->Set.of("APPLID","SYSID");case INQUIRE_PROGRAM->Set.of("PROGRAM");
        case SEND_TEXT->Set.of("TEXT","FROM","LENGTH","ERASE","FREEKB");case WRITEQ_TD->Set.of("TD","QUEUE","FROM","LENGTH","SYSID");default->Set.of();};}
    static boolean required(Kind k,Set<String> n) {return switch(k) {
        case ASKTIME,SYNCPOINT,RETURN->true;case FORMATTIME->n.contains("ABSTIME");case ASSIGN->n.contains("APPLID")||n.contains("SYSID");
        case INQUIRE_PROGRAM->n.contains("PROGRAM");case SEND_TEXT->n.containsAll(Set.of("TEXT","FROM"));case WRITEQ_TD->n.containsAll(Set.of("TD","QUEUE","FROM","LENGTH"));
        case SYNCPOINT_ROLLBACK->n.contains("ROLLBACK");case RETRIEVE->n.contains("INTO");case SEND_TERMINAL->n.contains("FROM");case RECEIVE_MAP,SEND_MAP->n.contains("MAP");};}
    static boolean flag(Kind k,String name,boolean operand) {return Set.of("NOHANDLE","CURSOR","ERASE","FREEKB","ROLLBACK","IMMEDIATE","TEXT","TD").contains(name)||k==Kind.FORMATTIME&&Set.of("DATESEP","TIMESEP").contains(name)&&!operand;}
    static boolean writes(Kind k,String name) {return Set.of("RESP","RESP2","INTO").contains(name)||k==Kind.ASKTIME&&name.equals("ABSTIME")||k==Kind.ASSIGN&&Set.of("APPLID","SYSID").contains(name)||k==Kind.FORMATTIME&&Set.of("TIME","MILLISECONDS","YYDDD","YYMMDD","MMDDYY","MMDDYYYY","YYYYMMDD").contains(name);}
    static boolean length(Kind k) {return k==Kind.SEND_TERMINAL||k==Kind.RETURN||k==Kind.SEND_TEXT||k==Kind.WRITEQ_TD;}

    private final CompilationUnitBuildResult owner;
    private final Map<CicsProgramControlAnalyzer.Key,Fact> facts;
    private CicsCommandSemantics(CompilationUnitBuildResult owner,Map<CicsProgramControlAnalyzer.Key,Fact> facts) {
        this.owner=owner;this.facts=Map.copyOf(facts);
    }
    public boolean belongsTo(CompilationUnitBuildResult frontend){return frontend==owner;}
    public Optional<Fact> fact(ResolutionContracts.ProgramUnitId unit,int statement){return Optional.ofNullable(facts.get(new CicsProgramControlAnalyzer.Key(unit,statement)));}
    public static CicsCommandSemantics analyze(CompilationUnitBuildResult frontend) {
        var facts=new LinkedHashMap<CicsProgramControlAnalyzer.Key,Fact>();
        for(var unit:frontend.compilationUnit().programUnits()) {
            var todo=new ArrayDeque<Ast.Node>();todo.push(unit.program());
            while(!todo.isEmpty()) {
                var node=todo.pop();if(node instanceof Ast.Program&&node!=unit.program())continue;
                if(node instanceof Ast.EmbeddedLanguageStatement s&&s.language()==Ast.EmbeddedLanguage.CICS)
                    parse(s.rawText()).ifPresent(f->facts.put(new CicsProgramControlAnalyzer.Key(unit.id(),s.meta().id()),
                        new Fact(f.command(),f.syntaxStatus(),f.raw(),f.options(),f.gaps(),hostEffects(f,s))));
                var children=Ast.children(node);for(int i=children.size()-1;i>=0;i--)todo.push(children.get(i));
            }
        }
        return new CicsCommandSemantics(frontend,facts);
    }
    record ImplicitArea(String name,CicsCommandSyntax.Option map,Ast.EmbeddedHostRole role) { }
    static Optional<ImplicitArea> implicitArea(Fact fact) {
        if(!fact.supported()||fact.command()!=Kind.RECEIVE_MAP&&fact.command()!=Kind.SEND_MAP)return Optional.empty();
        boolean input=fact.command()==Kind.RECEIVE_MAP;
        if(fact.options().stream().anyMatch(o->o.name().equals(input?"INTO":"FROM")||o.name().equals("SET")))return Optional.empty();
        return fact.options().stream().filter(o->o.name().equals("MAP")).findFirst().flatMap(map->map.operand().flatMap(CicsCommandSyntax::literal)
            .filter(name->!name.isEmpty()&&name.length()<=7&&dataSyntax(name+(input?"I":"O")))
            .map(name->new ImplicitArea(name+(input?"I":"O"),map,input?Ast.EmbeddedHostRole.WRITE:Ast.EmbeddedHostRole.READ)));
    }
    private static Optional<HostEffects> hostEffects(Fact fact,Ast.EmbeddedLanguageStatement statement) {
        if(!fact.supported()||extended(fact.command()))return Optional.empty();
        if((fact.command()==Kind.RECEIVE_MAP||fact.command()==Kind.SEND_MAP)
                &&fact.options().stream().noneMatch(o->o.name().equals(fact.command()==Kind.RECEIVE_MAP?"INTO":"FROM"))
                &&statement.hostOperands().stream().noneMatch(h->h.option().equals("IMPLICIT_AREA")))return Optional.empty();
        var hosts=new HashMap<Integer,Ast.EmbeddedHostOperand>();statement.hostOperands().forEach(h->hosts.put(h.optionStart(),h));
        var expressions=new HashMap<Integer,Ast.Expression>();statement.expressionOperands().forEach(e->expressions.put(e.optionStart(),e.expression()));
        var literals=new ArrayList<Integer>();
        for(var option:fact.options()) {
            if(option.operand().isEmpty())continue;
            if(Set.of("MAP","MAPSET","TRANSID").contains(option.name())&&option.operand().flatMap(CicsCommandSyntax::literal).isPresent()) {
                literals.add(option.start());continue;
            }
            if(option.name().equals("LENGTH")) {
                var e=expressions.get(option.start());
                if(e instanceof Ast.LiteralExpression l&&l.integerValue().isPresent())continue;
                if(e instanceof Ast.SpecialRegisterExpression r&&r.registerName().equals("LENGTH")&&r.operands().size()==1)e=r.operands().get(0);
                if(!(e instanceof Ast.DataReference r)||!whole(r))return Optional.empty();
            } else {
                var host=hosts.get(option.start());if(host==null||!whole(host.reference()))return Optional.empty();
            }
        }
        return Optional.of(new HostEffects(literals));
    }
    private static boolean whole(Ast.DataReference ref) {
        return ref.understanding()==Ast.ReferenceUnderstanding.STRUCTURED&&ref.subscriptGroups().isEmpty()&&ref.referenceModification()==null;
    }
    static Optional<Fact> parse(String raw) {
        var syntax=CicsCommandSyntax.parse(raw);if(syntax.isEmpty())return Optional.empty();var s=syntax.get();
        Kind kind;
        if(s.name().equals("SYNCPOINT"))kind=s.options().stream().anyMatch(o->o.name().equals("ROLLBACK"))?Kind.SYNCPOINT_ROLLBACK:Kind.SYNCPOINT;
        else if(s.name().equals("RETURN"))kind=Kind.RETURN;
        else if(s.name().equals("RETRIEVE"))kind=Kind.RETRIEVE;
        else if(s.name().equals("ASKTIME"))kind=Kind.ASKTIME;
        else if(s.name().equals("FORMATTIME"))kind=Kind.FORMATTIME;
        else if(s.name().equals("ASSIGN"))kind=Kind.ASSIGN;
        else if(s.name().equals("INQUIRE")&&s.options().stream().anyMatch(o->o.name().equals("PROGRAM")))kind=Kind.INQUIRE_PROGRAM;
        else if(s.name().equals("SEND")&&s.options().stream().anyMatch(o->o.name().equals("TEXT")))kind=Kind.SEND_TEXT;
        else if(s.name().equals("WRITEQ")&&s.options().stream().anyMatch(o->o.name().equals("TD")))kind=Kind.WRITEQ_TD;
        else if(Set.of("SEND","RECEIVE").contains(s.name())&&s.options().stream().anyMatch(o->o.name().equals("MAP")))
            kind=s.name().equals("SEND")?Kind.SEND_MAP:Kind.RECEIVE_MAP;
        else if(s.name().equals("SEND")&&s.options().stream().noneMatch(o->Set.of("MAP","TEXT","CONTROL","PAGE","CONVID","SESSION","MRO","PARTN").contains(o.name())))kind=Kind.SEND_TERMINAL;
        else return Optional.empty();
        var gaps=new LinkedHashSet<>(s.gaps());boolean supported=s.gaps().isEmpty();var names=new HashSet<String>();
        var allowed=new HashSet<>(Set.of("RESP","RESP2","NOHANDLE"));
        if(kind==Kind.SYNCPOINT_ROLLBACK)allowed.add("ROLLBACK");
        if(kind==Kind.RETURN)allowed.addAll(Set.of("TRANSID","COMMAREA","LENGTH","IMMEDIATE"));
        if(kind==Kind.RETRIEVE)allowed.add("INTO");
        if(kind==Kind.SEND_TERMINAL)allowed.addAll(Set.of("FROM","LENGTH","ERASE"));
        if(kind==Kind.SEND_MAP||kind==Kind.RECEIVE_MAP)allowed.addAll(Set.of("MAP","MAPSET",kind==Kind.SEND_MAP?"FROM":"INTO"));
        if(kind==Kind.SEND_MAP)allowed.addAll(Set.of("CURSOR","ERASE","FREEKB"));
        allowed.addAll(extra(kind));
        for(var o:s.options()) {
            if(!allowed.contains(o.name())){supported=false;gaps.add("CICS_COMMAND_UNMODELED_OPTION");}
            if(!names.add(o.name())){if(o.name().equals("NOHANDLE")&&o.operand().isEmpty())gaps.add("CICS_COMMAND_DUPLICATE_FLAG_IGNORED");else {supported=false;gaps.add("CICS_COMMAND_DUPLICATE_OPTION");}}
            boolean flag=flag(kind,o.name(),o.operand().isPresent());
            if(flag?o.operand().isPresent():o.operand().filter(v->!v.isBlank()).isEmpty()){supported=false;gaps.add("CICS_COMMAND_OPERAND_SHAPE");}
            if(!flag&&allowed.contains(o.name())&&o.operand().isPresent()) {
                String v=o.operand().orElseThrow().strip();boolean name=Set.of("MAP","MAPSET","TRANSID","QUEUE","SYSID","PROGRAM","DATESEP","TIMESEP").contains(o.name());
                if(!(length(kind)&&o.name().equals("LENGTH")?EmbeddedExpressionSyntax.supported(v):name&&CicsCommandSyntax.literal(v).filter(x->!x.isEmpty()).isPresent()||dataSyntax(v))){supported=false;gaps.add("CICS_COMMAND_OPERAND_SHAPE");}
            }
        }
        if(!required(kind,names)){supported=false;gaps.add("CICS_COMMAND_REQUIRED_OPTION");}
        if(kind==Kind.RETURN&&(names.contains("LENGTH")&&!names.contains("COMMAREA")||names.contains("IMMEDIATE")&&!names.contains("TRANSID"))){supported=false;gaps.add("CICS_COMMAND_OPTION_COMBINATION");}
        if(kind==Kind.RETRIEVE&&!names.contains("INTO")){supported=false;gaps.add("CICS_COMMAND_REQUIRED_INTO");}
        if(kind==Kind.SEND_TERMINAL&&!names.contains("FROM")){supported=false;gaps.add("CICS_COMMAND_REQUIRED_FROM");}
        if(kind==Kind.SEND_MAP||kind==Kind.RECEIVE_MAP) {
            var map=s.options().stream().filter(o->o.name().equals("MAP")).findFirst().orElseThrow();
            if(map.operand().flatMap(v->CicsCommandSyntax.literal(v.strip())).isEmpty()&&!names.contains(kind==Kind.SEND_MAP?"FROM":"INTO"))
                {supported=false;gaps.add("CICS_COMMAND_IMPLICIT_AREA_UNAVAILABLE");}
        }
        return Optional.of(new Fact(kind,supported?SyntaxStatus.SUPPORTED:SyntaxStatus.UNAVAILABLE,raw,s.options(),List.copyOf(gaps)));
    }
    static boolean dataSyntax(String text) {
        var failed=new boolean[1];var listener=new BaseErrorListener(){@Override public void syntaxError(Recognizer<?,?> r,Object s,int l,int c,String m,RecognitionException e){failed[0]=true;}};
        var lexer=new CobolLexer(CharStreams.fromString(text));lexer.removeErrorListeners();lexer.addErrorListener(listener);
        if(DirectParseScope.active())try {var ref=((DirectSyntax.EmbeddedIdentifierFrame)DirectEmbeddedProcedureSyntax.parseRule(text,"embeddedIdentifier")).identifier();return ref.qualifiedDataName()!=null||ref.tableCall()!=null;}catch(DirectRecognizer.Unsupported e){return false;}
        var parser=new CobolParser(new CommonTokenStream(lexer));parser.removeErrorListeners();parser.setErrorHandler(new BailErrorStrategy());
        try {var ref=parser.embeddedIdentifier().identifier();return !failed[0]&&parser.getCurrentToken().getType()==Token.EOF&&(ref.qualifiedDataName()!=null||ref.tableCall()!=null);}
        catch(org.antlr.v4.runtime.misc.ParseCancellationException e){return false;}
    }
}
