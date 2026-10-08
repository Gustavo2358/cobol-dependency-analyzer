package io.github.gustavo2358.cobolexplorer;

import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/** Imported independent source/oracle cases; no sibling repositories at runtime. */
class DependencyRegressionTest {
    @TestFactory Stream<DynamicTest> importedOracles()throws Exception {
        var tests=new ArrayList<DynamicTest>();var mapper=new com.fasterxml.jackson.databind.ObjectMapper();
        Path root=Path.of("src/test/resources/dependency-regression");
        for(String family:List.of("perform-completion","logical-alias-move","cics-control-completion")) {
            var path=root.resolve(family);
            for(var c:mapper.readTree(path.resolve("expected.json").toFile()).get("cases"))tests.add(DynamicTest.dynamicTest(family+"/"+c.get("id").asText(),()->{
                var result=new DependencyAnalyzer().analyze(path.resolve(c.get("source").asText()),new DependencyAnalyzer.Options(List.of(path),SourceNormalizer.SourceFormat.FIXED,java.nio.charset.StandardCharsets.UTF_8,null,"direct-ast-lab",1_000_000));
                var required=new TreeSet<String>();var allowed=new TreeSet<String>();var actual=new TreeSet<String>();var lines=new HashSet<Integer>();
                for(var call:c.get("calls")){lines.add(call.get("line").asInt());for(var v:call.get("required"))required.add(v.asText());for(var v:call.get("allowed"))allowed.add(v.asText());}
                // These inherited oracles describe COBOL CALL sites only; CICS
                // PROGRAM operands in the same input have separate unit oracles.
                for(var p:result.programs())for(var d:p.dependencies())if(d.type().equals("program")&&lines.contains(d.at().line()))actual.add(d.name());
                var missing=new TreeSet<>(required);missing.removeAll(actual);var extra=new TreeSet<>(actual);extra.removeAll(allowed);
                assertTrue(missing.isEmpty()&&extra.isEmpty(),"missing="+missing+" extra="+extra+" diagnostics="+result.diagnostics());
            }));
        }
        return tests.stream();
    }
    @TestFactory Stream<DynamicTest> logicalTextOracles()throws Exception {
        var tests=new ArrayList<DynamicTest>();var mapper=new com.fasterxml.jackson.databind.ObjectMapper();
        var path=Path.of("src/test/resources/dependency-regression/logical-text-w2");
        var entries=mapper.readTree(path.resolve("expected.json").toFile()).fields();
        while(entries.hasNext()) {var c=entries.next();tests.add(DynamicTest.dynamicTest("text/"+c.getKey(),()->{
            var r=new DependencyAnalyzer().analyze(path.resolve(c.getKey()+".cbl"),DependencyAnalyzer.Options.defaults());
            var expected=new TreeSet<String>();c.getValue().get("targets").forEach(x->expected.add(x.asText()));
            var actual=new TreeSet<String>();r.programs().forEach(p->p.dependencies().stream().filter(d->d.type().equals("program")).forEach(d->actual.add(d.name())));
            assertEquals(expected,actual,r.diagnostics().toString());
        }));}return tests.stream();
    }
    @TestFactory Stream<DynamicTest> sourcePossibilityOracles()throws Exception {
        var tests=new ArrayList<DynamicTest>();var mapper=new com.fasterxml.jackson.databind.ObjectMapper();
        var path=Path.of("src/test/resources/dependency-regression/source-possibility");
        try(var files=Files.list(path)) {for(var source:files.filter(p->p.toString().endsWith(".cbl")).sorted().toList())tests.add(DynamicTest.dynamicTest("source/"+source.getFileName(),()->{
            var old=mapper.readTree(path.resolve("expected-programs.json").toFile());var expected=new TreeSet<String>();
            old.get(source.getFileName().toString()).forEach(v->expected.add(v.asText()));
            // Frozen reference omits both NEXT SENTENCE successors. The grammar
            // and IBM rule establish AFTERIO after the next separator period.
            var corrections=mapper.readTree(path.resolve("validated-divergences.json").toFile());
            if(corrections.has(source.getFileName().toString())) {
                expected.clear();corrections.get(source.getFileName().toString()).get("validTargets").forEach(x->expected.add(x.asText()));
            }
            var r=new DependencyAnalyzer().analyze(source,DependencyAnalyzer.Options.defaults());var actual=new TreeSet<String>();
            r.programs().forEach(p->p.dependencies().stream().filter(d->d.type().equals("program")).forEach(d->actual.add(d.name())));
            assertEquals(expected,actual,r.diagnostics().toString());
        }));}return tests.stream();
    }
}
