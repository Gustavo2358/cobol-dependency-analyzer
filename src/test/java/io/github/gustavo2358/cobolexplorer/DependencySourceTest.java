package io.github.gustavo2358.cobolexplorer;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class DependencySourceTest {
    @TempDir Path temp;
    @TestFactory Stream<DynamicTest> inheritedSourceAndSqlOracles()throws Exception {
        var tests=new ArrayList<DynamicTest>();var mapper=new com.fasterxml.jackson.databind.ObjectMapper();
        for(String family:List.of("source-dependencies-w3","source-dependencies-w3-db2")) {
            var root=Path.of("src/test/resources/cobol",family);
            try(var dirs=Files.list(root)) {for(var path:dirs.filter(Files::isDirectory).sorted().toList())tests.add(DynamicTest.dynamicTest(family+"/"+path.getFileName(),()->{
                var result=new DependencyAnalyzer().analyze(path.resolve("program.cbl"),new DependencyAnalyzer.Options(List.of(path.resolve("copybooks")),SourceNormalizer.SourceFormat.FIXED,StandardCharsets.UTF_8,path.resolve("inventory.json"),"direct-ast-lab",1_000_000));
                var expected=mapper.readTree(path.resolve("expected.json").toFile());var required=new TreeSet<String>();
                var types=Map.of("COPYBOOK","copybook","DCLGEN","dclgen","SQL_INCLUDE","sql-include");
                for(var type:types.entrySet()) {var names=expected.path(type.getKey()).fieldNames();while(names.hasNext())required.add(type.getValue()+":"+names.next());}
                for(var table:expected.path("tables"))required.add("db2-table:"+table.asText().split(":")[0]);
                var actual=new TreeSet<String>();result.programs().forEach(p->p.dependencies().stream().filter(d->family.endsWith("-db2")?d.type().equals("db2-table"):Set.of("copybook","dclgen","sql-include").contains(d.type())).forEach(d->actual.add(d.type()+":"+d.name())));
                assertEquals(required,actual,result.diagnostics().toString());
            }));}
        }return tests.stream();
    }
    private Path source(String name,String text)throws Exception {
        var path=temp.resolve(name);Files.writeString(path,text.lines().map(s->"       "+s+"\n").reduce("",String::concat));return path;
    }
    @Test void fileNamesAndIoHandlerPaths()throws Exception {
        var p=source("files.cbl","""
            IDENTIFICATION DIVISION.
            PROGRAM-ID. FILES.
            ENVIRONMENT DIVISION.
            INPUT-OUTPUT SECTION.
            FILE-CONTROL.
            SELECT F ASSIGN TO INPUTDD.
            SELECT S ASSIGN TO SORTWK.
            DATA DIVISION.
            FILE SECTION.
            FD F.
            01 R PIC X(8).
            SD S.
            01 SR PIC X(8).
            PROCEDURE DIVISION.
            OPEN INPUT F.
            READ F AT END CALL 'ENDCALL' END-READ.
            CLOSE F.
            GOBACK.
            """);
        var result=new DependencyAnalyzer().analyze(p,DependencyAnalyzer.Options.defaults());
        assertEquals(Set.of("file:INPUTDD","file:SORTWK","program:ENDCALL"),new HashSet<>(result.programs().get(0).dependencies().stream().map(d->d.type()+":"+d.name()).toList()));
    }
    @Test void copyConsumerProvenanceAndMissingCopyAreExplicit()throws Exception {
        source("PROCCOPY.cpy","CALL TARGET.");
        var p=source("main.cbl","""
            IDENTIFICATION DIVISION.
            PROGRAM-ID. COPY-PROG.
            DATA DIVISION.
            WORKING-STORAGE SECTION.
            01 TARGET PIC X(8) VALUE 'PROGA'.
            PROCEDURE DIVISION.
            COPY PROCCOPY.
            COPY MISSING.
            GOBACK.
            """);
        var result=new DependencyAnalyzer().analyze(p,DependencyAnalyzer.Options.defaults());
        var dependency=result.programs().get(0).dependencies().stream().filter(d->d.type().equals("program")).findFirst().orElseThrow();
        assertEquals(new DependencyAnalyzer.At("PROCCOPY.cpy",1),dependency.at());
        assertTrue(result.diagnostics().stream().anyMatch(d->d.contains("MISSING")));
        assertTrue(result.programs().get(0).dependencies().stream().anyMatch(d->d.type().equals("copybook")&&d.name().equals("MISSING")));
    }
    @Test void singleSourceWithTwoProgramsProducesMinimalArray()throws Exception {
        var p=source("multi.cbl","""
            IDENTIFICATION DIVISION.
            PROGRAM-ID. ONE.
            PROCEDURE DIVISION.
            CALL 'FIRST'.
            GOBACK.
            END PROGRAM ONE.
            IDENTIFICATION DIVISION.
            PROGRAM-ID. TWO.
            PROCEDURE DIVISION.
            CALL 'SECOND'.
            GOBACK.
            END PROGRAM TWO.
            """);
        var out=temp.resolve("dependencies.json");assertEquals(0,DependencyMain.run(new String[]{"--source",p.toString(),"--output",out.toString()}));
        var json=new com.fasterxml.jackson.databind.ObjectMapper().readTree(out.toFile());assertTrue(json.isArray());assertEquals(2,json.size());
        assertEquals("ONE",json.get(0).get("program").asText());assertEquals("TWO",json.get(1).get("program").asText());
    }
    @TestFactory Stream<DynamicTest> cicsFileSourceOracles()throws Exception {
        var tests=new ArrayList<DynamicTest>();var mapper=new com.fasterxml.jackson.databind.ObjectMapper();
        var path=Path.of("src/test/resources/dependency-regression/cics-files");
        var entries=mapper.readTree(path.resolve("expected-files.json").toFile()).fields();
        while(entries.hasNext()) {var entry=entries.next();tests.add(DynamicTest.dynamicTest("cics-file/"+entry.getKey(),()->{
            var result=new DependencyAnalyzer().analyze(path.resolve(entry.getKey()),DependencyAnalyzer.Options.defaults());
            var expected=new TreeSet<String>();entry.getValue().forEach(v->expected.add(v.asText()));
            var actual=new TreeSet<String>();result.programs().forEach(p->p.dependencies().stream().filter(d->d.type().equals("file")).forEach(d->actual.add(d.name())));
            assertEquals(expected,actual,result.diagnostics().toString());
        }));}return tests.stream();
    }
    @Test void useDeclarativeReturnsToTheIoConsumer()throws Exception {
        var p=source("use.cbl","""
            IDENTIFICATION DIVISION.
            PROGRAM-ID. USE-PROG.
            ENVIRONMENT DIVISION.
            INPUT-OUTPUT SECTION.
            FILE-CONTROL.
            SELECT F ASSIGN TO INPUTDD.
            DATA DIVISION.
            FILE SECTION.
            FD F.
            01 R PIC X(8).
            WORKING-STORAGE SECTION.
            01 TARGET PIC X(8).
            PROCEDURE DIVISION.
            DECLARATIVES.
            ERROR-SECTION SECTION.
            USE AFTER STANDARD ERROR PROCEDURE ON F.
            ERROR-PARA.
            MOVE 'HANDLER' TO TARGET.
            END DECLARATIVES.
            MAIN.
            MOVE 'NORMAL' TO TARGET.
            READ F.
            CALL TARGET.
            GOBACK.
            """);
        var result=new DependencyAnalyzer().analyze(p,DependencyAnalyzer.Options.defaults());
        assertEquals(Set.of("NORMAL","HANDLER"),new HashSet<>(result.programs().get(0).dependencies().stream().filter(d->d.type().equals("program")).map(DependencyAnalyzer.Dependency::name).toList()));
    }
    @Test void sidecarFailureCannotReplaceThePreviousProduct()throws Exception {
        var p=source("atomic.cbl","IDENTIFICATION DIVISION.\nPROGRAM-ID. ATOMIC-PROG.\nPROCEDURE DIVISION.\nCALL 'PROGA'.\nGOBACK.");
        var out=temp.resolve("dependencies.json");Files.writeString(out,"PREVIOUS");
        assertEquals(2,DependencyMain.run(new String[]{"--source",p.toString(),"--output",out.toString(),"--metrics",temp.toString()}));
        assertEquals("PREVIOUS",Files.readString(out));
    }
    @Test void containedProgramReusesTheBinderForGlobalValueDeclarations()throws Exception {
        var p=source("global.cbl","""
            IDENTIFICATION DIVISION.
            PROGRAM-ID. OUTER-PROG.
            DATA DIVISION.
            WORKING-STORAGE SECTION.
            01 TARGET IS GLOBAL PIC X(8) VALUE 'PROGA'.
            PROCEDURE DIVISION.
            GOBACK.
            IDENTIFICATION DIVISION.
            PROGRAM-ID. INNER-PROG.
            PROCEDURE DIVISION.
            CALL TARGET.
            GOBACK.
            END PROGRAM INNER-PROG.
            END PROGRAM OUTER-PROG.
            """);
        var result=new DependencyAnalyzer().analyze(p,DependencyAnalyzer.Options.defaults());
        var inner=result.programs().stream().filter(x->x.program().equals("INNER-PROG")).findFirst().orElseThrow();
        assertEquals(List.of("PROGA"),inner.dependencies().stream().filter(d->d.type().equals("program")).map(DependencyAnalyzer.Dependency::name).toList());
        assertTrue(result.programs().stream().filter(x->x.program().equals("OUTER-PROG")).findFirst().orElseThrow().dependencies().isEmpty());
    }
}
