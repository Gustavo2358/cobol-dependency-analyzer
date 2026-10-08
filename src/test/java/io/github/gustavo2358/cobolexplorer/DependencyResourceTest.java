package io.github.gustavo2358.cobolexplorer;

import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/** Actual sources from the context/OOM campaigns, including their controls. */
class DependencyResourceTest {
    @TestFactory Stream<DynamicTest> previouslyExpensiveSources()throws Exception {
        var path=Path.of("src/test/resources/dependency-regression/resource-stress");
        var cases=new com.fasterxml.jackson.databind.ObjectMapper().readTree(path.resolve("expected.json").toFile());
        var tests=new ArrayList<DynamicTest>();
        for(var c:cases)tests.add(DynamicTest.dynamicTest(c.get("id").asText(),()->assertTimeout(Duration.ofSeconds(30),()->{
            var source=path.resolve(c.get("source").asText());
            assertEquals(c.get("sha256").asText(),HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(source))));
            if(c.has("expectedFailure")) {
                var failure=assertThrows(IllegalArgumentException.class,()->new DependencyAnalyzer().analyze(source,DependencyAnalyzer.Options.defaults()));
                assertTrue(failure.getMessage().contains(c.get("expectedFailure").asText()));
                return;
            }
            var result=new DependencyAnalyzer().analyze(source,DependencyAnalyzer.Options.defaults());
            var actual=new TreeSet<String>();result.programs().forEach(p->p.dependencies().forEach(d->actual.add(d.name())));
            var expected=new TreeSet<String>();c.get("expected").forEach(x->expected.add(x.asText()));
            assertEquals(expected,actual,result.diagnostics().toString());
            assertTrue(result.metrics().workItems()<1_000_000,result.metrics().toString());
        })));
        return tests.stream();
    }
}
