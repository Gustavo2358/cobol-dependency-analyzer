package com.imd.cobolexplorer;

import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class IbmCopybookCatalogueTest {
    @TempDir Path library;
    private PreprocessorEngine.Outcome preprocess(String data,String code) throws Exception {
        String source=ScopedInputTest.unit("IBM-MODELS",data,code).lines()
            .map(s->"       "+s).collect(Collectors.joining("\n","","\n"));
        var map=SourceNormalizer.normalize(source,"ibm-models.cbl",SourceNormalizer.SourceFormat.FIXED).sourceMap();
        return new PreprocessorEngine(Bindings.cobol(),new CopybookLibrary(library)).process(map,"ibm-models.cbl");
    }
    @Test void allDocumentedNamesLevelsPicturesUsagesAndParentsSurviveTheFrontend() throws Exception {
        assertEquals(Set.of("DFHAID","DFHBMSCA","CMQGMOV","CMQMDV","CMQODV","CMQPMOV","CMQTML","CMQV","SQLCA"),NominalCopybooks.members());
        var oracle=Files.readAllLines(Path.of("src/test/resources/cobol/nominal-copybooks/ibm-shapes.tsv"));
        for(var member:List.of("CMQGMOV","CMQMDV","CMQODV","CMQPMOV","CMQTML","CMQV","SQLCA")) {
            var pre=preprocess(member.equals("SQLCA")?"EXEC SQL INCLUDE SQLCA END-EXEC.":"01 USER-GROUP.\nCOPY "+member+".","CALL "+Map.of("CMQGMOV","MQGMO-STRUCID","CMQMDV","MQMD-FORMAT","CMQODV","MQOD-OBJECTNAME","CMQPMOV","MQPMO-RESOLVEDQNAME","CMQTML","MQTM-APPLID","CMQV","MQFMT-STRING","SQLCA","SQLCAID").get(member)+".\nGOBACK.");
            assertEquals(0,pre.errors());assertEquals(0,pre.unresolved());
            assertEquals(1,pre.diagnostics().size());assertEquals(Diagnostic.Code.NOMINAL_COPYBOOK,pre.diagnostics().get(0).code());
            var a=AstBoundaryTestSupport.analyze(pre,"ibm-models.cbl");
            var entries=AstBoundaryTestSupport.nodes(a,Ast.DataEntry.class).stream().filter(d->d.meta().syntheticModel()).toList();
            var byName=entries.stream().collect(Collectors.toMap(Ast.DataEntry::name,d->d));
            var parents=new HashMap<String,String>();for(var d:entries)for(var child:d.children())parents.put(child.name(),d.name());
            var rows=oracle.stream().filter(s->s.startsWith(member+"\t")).map(s->s.split("\t")).toList();
            assertEquals(rows.size(),entries.size(),member);assertFalse(rows.isEmpty());
            for(var row:rows) {
                var d=byName.get(row[2]);assertNotNull(d,member+" "+row[2]);
                assertEquals(row[1],d.level(),d.name());assertEquals(row[3],parents.getOrDefault(d.name(),"-"),d.name());
                assertEquals(row[4],d.clauses().stream().filter(Ast.PictureClause.class::isInstance).map(Ast.PictureClause.class::cast).map(Ast.PictureClause::picture).findFirst().orElse("-"),d.name());
                assertEquals(row[5],d.clauses().stream().filter(Ast.UsageClause.class::isInstance).map(Ast.UsageClause.class::cast).map(Ast.UsageClause::usage).findFirst().orElse(row[4].equals("-")?"-":"DISPLAY"),d.name());
                if(!row[6].equals("-"))assertTrue(d.clauses().stream().anyMatch(c->c instanceof Ast.OccursClause o&&o.writtenText().contains("OCCURS "+row[6]+" TIMES")),d.name());
                assertTrue(d.clauses().stream().noneMatch(Ast.ValueClause.class::isInstance));
                assertEquals(NominalCopybooks.artifact(member),d.meta().provenance().original().file());
                assertEquals(1,d.meta().provenance().includeChain().size());
            }
            var product=ScopedInputTest.product(a,0);
            assertTrue(product.dataDeclarations().stream().filter(d->byName.containsKey(d.canonicalName())).allMatch(d->d.scalarText().isEmpty()&&d.scalarNumber().isEmpty()));
            assertTrue(product.storage().logicalTextViews().isEmpty());
            assertTrue(product.nominalValues().isPresent(),member+" nominal missing");
            assertTrue(product.nominalValues().orElseThrow().symbols().stream().allMatch(s->s.modelAssumed()));
        }
    }
    @Test void mqStructuresRemainQualifiedByEachUserWrapper() throws Exception {
        var pre=preprocess("01 REQUEST-DESCRIPTOR.\nCOPY CMQMDV.\n01 REPLY-DESCRIPTOR.\nCOPY CMQMDV.",
            "MOVE MQMD-FORMAT OF MQMD OF REQUEST-DESCRIPTOR\n TO MQMD-FORMAT OF MQMD OF REPLY-DESCRIPTOR.\nGOBACK.");
        var a=AstBoundaryTestSupport.analyze(pre,"ibm-models.cbl");
        assertEquals(0,a.report().programUnits().get(0).unresolved());
        var uses=a.resolution().entries().stream().filter(e->e.occurrence().writtenText().startsWith("MQMD-FORMAT OF")).toList();
        assertEquals(2,uses.size());
        assertTrue(uses.stream().allMatch(e->e.status()==ResolutionContracts.ResolutionStatus.RESOLVED));
        assertNotEquals(uses.get(0).selectedCandidate().orElseThrow().entityId(),uses.get(1).selectedCandidate().orElseThrow().entityId());
    }
    @Test void sqlcaQualificationsArrayAndExtensionResolve() throws Exception {
        var pre=preprocess("EXEC SQL INCLUDE SQLCA END-EXEC.","DISPLAY SQLERRD OF SQLCA (6).\nDISPLAY SQLSTATE OF SQLEXT OF SQLCA.\nDISPLAY SQLERRMC OF SQLERRM OF SQLCA.\nGOBACK.");
        var a=AstBoundaryTestSupport.analyze(pre,"ibm-models.cbl");
        assertEquals(0,a.report().programUnits().get(0).unresolved());
        var f=pre.sourceDependencies().get(0);assertEquals(SourceDependencyFact.Kind.SQL_INCLUDE,f.kind());
        assertEquals("BUILTIN_SQL_INCLUDE",f.authority());assertEquals(SourceDependencyFact.Resolution.RESOLVED,f.resolution());
    }
    @Test void realMembersOverrideEveryCatalogueEntry() throws Exception {
        for(var member:NominalCopybooks.members()) {
            Files.writeString(library.resolve(member+".cpy"),"       01 REAL-ITEM PIC X(8) VALUE 'REALPGM'.\n");
            var pre=preprocess("COPY "+member+".","CALL REAL-ITEM.");
            assertTrue(pre.diagnostics().isEmpty(),member);assertTrue(pre.text().contains("REALPGM"));
        }
        var sql=preprocess("EXEC SQL INCLUDE SQLCA END-EXEC.","CALL REAL-ITEM.");
        assertTrue(sql.diagnostics().isEmpty());assertEquals("SQLCA.cpy",sql.sourceDependencies().get(0).artifact());
    }
    @Test void missingSqlcaCopyRemainsUnresolvedWithoutSyntheticDeclarations() throws Exception {
        for(var operand:List.of("SQLCA", "sqlca", "'SQLCA'")) {
            var pre=preprocess("COPY "+operand+".","CALL SQLCAID.\nGOBACK.");
            assertEquals(1,pre.unresolved(),operand);
            assertTrue(pre.diagnostics().stream().anyMatch(d->d.code()==Diagnostic.Code.UNRESOLVED_COPY));
            assertFalse(pre.diagnostics().stream().anyMatch(d->d.code()==Diagnostic.Code.NOMINAL_COPYBOOK));
            var fact=pre.sourceDependencies().get(0);
            assertEquals(SourceDependencyFact.Kind.COPYBOOK,fact.kind());
            assertEquals(SourceDependencyFact.Resolution.UNRESOLVED,fact.resolution());
            var analysis=AstBoundaryTestSupport.analyze(pre,"ibm-models.cbl");
            assertTrue(AstBoundaryTestSupport.nodes(analysis,Ast.DataEntry.class).isEmpty());
        }
        assertTrue(NominalCopybooks.resolve("SQLCA").isEmpty());
        assertTrue(NominalCopybooks.resolveSqlInclude("sqlca").isPresent());
    }
    @Test void sqlModelCannotOverrideExplicitMissingArtifactOrNestedIncludeRule() throws Exception {
        var inventory=library.resolve("inventory.json");
        Files.writeString(inventory,"{\"version\":\"1.0.0\",\"artifacts\":[{\"name\":\"SQLCA\",\"kind\":\"SQL_INCLUDE\",\"artifact\":\"missing.cpy\"}]}");
        var engine=new PreprocessorEngine(Bindings.cobol(),new CopybookLibrary(library),SourceArtifactInventory.read(inventory));
        var outcome=engine.process(SourceMap.identity("EXEC SQL INCLUDE SQLCA END-EXEC\n","input.cbl"),"input.cbl");
        assertTrue(outcome.diagnostics().isEmpty());assertTrue(outcome.text().contains("*>EXECSQL"));
        assertEquals(SourceDependencyFact.Resolution.UNRESOLVED,outcome.sourceDependencies().get(0).resolution());
        Files.writeString(library.resolve("OUTER.cpy"),"           EXEC SQL INCLUDE SQLCA END-EXEC.\n");
        assertThrows(UnsupportedOperationException.class,()->preprocess("EXEC SQL INCLUDE OUTER END-EXEC.","GOBACK."));
        var replaced=preprocess("COPY OUTER REPLACING ==SQLCODE== BY ==LOCAL-CODE==.","GOBACK.");
        assertFalse(replaced.diagnostics().stream().anyMatch(d->d.code()==Diagnostic.Code.NOMINAL_COPYBOOK));
        assertTrue(replaced.text().contains("*>EXECSQL"));
        assertTrue(NominalCopybooks.resolveSqlInclude("CMQV").isEmpty());
    }
}
