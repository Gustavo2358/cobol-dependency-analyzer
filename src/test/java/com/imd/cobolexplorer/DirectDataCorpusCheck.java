package com.imd.cobolexplorer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.imd.cobolexplorer.antlr.CobolParser;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.ParseTree;
import java.nio.file.*;
import java.util.*;

/** Local differential gate for a complete manifest of already preprocessed programs.
 * Input is a JSON array of absolute source paths, never an allowlist in production.
 * Full CLI comparisons separately exercise real COPY/provenance and publications. */
public final class DirectDataCorpusCheck {
    record Parsed(CompilationUnitBuildResult ast, List<ExplorerMain.Node> nodes,
                  List<String> errors, DirectDataParser.Session session) { }
    static Parsed parse(String text, boolean direct) {
        var source = SourceMap.identity(text, "test.cbl"); var binding = Bindings.cobol(); var errors = new ArrayList<String>();
        var listener = new BaseErrorListener() {
            @Override public void syntaxError(Recognizer<?, ?> r, Object symbol, int line, int column, String message, RecognitionException e) { errors.add(line + ":" + column + ":" + message); }
        };
        var lexer = binding.cobolLexer(CharStreams.fromString(text)); lexer.removeErrorListeners(); lexer.addErrorListener(listener);
        var tokens = new CommonTokenStream(lexer); tokens.fill();
        var parser = (CobolParser) binding.cobolParser(tokens); parser.removeErrorListeners(); parser.addErrorListener(listener);
        if (direct) parser.directDataSession = new DirectDataParser.Session();
        ParseTree tree = parser.startRule();
        var nodes = new ArrayList<ExplorerMain.Node>(); var ids = new IdentityHashMap<ParseTree, Integer>(); var sizes = new IdentityHashMap<ParseTree, Integer>();
        ExplorerMain.walk(tree, -1, 0, parser, nodes, new TreeMap<>(), ids, sizes);
        var ast = new AstBuilder(parser, text, source, ids, sizes, errors.isEmpty()).buildCompilationUnit(tree, "test.cbl");
        return new Parsed(ast, nodes, errors, parser.directDataSession);
    }
    public static void main(String[] args) throws Exception {
        var mapper = new ObjectMapper(); var manifest = mapper.readTree(Path.of(args[0]).toFile());
        if (!manifest.isArray() || manifest.isEmpty()) throw new IllegalArgumentException("A nonempty array of source paths is required");
        Path out = Path.of(args[1]); Files.createDirectories(out); int failed = 0, accepted = 0, count = 0;
        var report = new ArrayList<String>();
        for (var item : manifest) {
            Path path = Path.of(item.asText()); count++; String state = "PASS";
            try {
                String source = Files.readString(path); Parsed base = parse(source, false), direct = parse(source, true);
                boolean nodes = base.nodes().equals(direct.nodes());
                boolean ast = base.ast().compilationUnit().programUnits().equals(direct.ast().compilationUnit().programUnits());
                boolean coverage = base.ast().coverageByProgramUnit().equals(direct.ast().coverageByProgramUnit());
                boolean diagnostics = base.errors().equals(direct.errors()) && base.ast().diagnosticsByProgramUnit().equals(direct.ast().diagnosticsByProgramUnit());
                boolean nativeOnly = direct.session().acceptedDivisions() > 0 && direct.session().fallbackDivisions() == 0;
                if (nativeOnly) accepted++;
                if (!nativeOnly || !nodes || !ast || !coverage || !diagnostics) {
                    failed++; state = "FAIL native=" + nativeOnly + " nodes=" + nodes + " ast=" + ast + " coverage=" + coverage + " diagnostics=" + diagnostics + " reasons=" + direct.session().fallbackReasons();
                    if (!nodes) {
                        int at = 0; while (at < Math.min(base.nodes().size(), direct.nodes().size()) && base.nodes().get(at).equals(direct.nodes().get(at))) at++;
                        state += " firstNode=" + at + " base=" + (at < base.nodes().size() ? base.nodes().get(at) : "EOF") + " direct=" + (at < direct.nodes().size() ? direct.nodes().get(at) : "EOF");
                    }
                    if (!ast) {
                        Files.writeString(out.resolve(count + "-base-ast.txt"), base.ast().compilationUnit().programUnits().toString());
                        Files.writeString(out.resolve(count + "-direct-ast.txt"), direct.ast().compilationUnit().programUnits().toString());
                    }
                }
            } catch (Exception e) { failed++; state = "ERROR " + e; }
            String row = count + "\t" + path + "\t" + state; report.add(row); System.out.println(row);
        }
        Files.write(out.resolve("results.tsv"), report);
        System.out.println("CORPUS total=" + count + " accepted=" + accepted + " failed=" + failed);
        if (failed != 0) throw new AssertionError("Corpus admission/equivalence failed: " + failed);
    }
}
