package io.github.gustavo2358.cobolexplorer;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.gustavo2358.cobolexplorer.semanticproduct.CobolSemanticProduct.*;

class NominalCopybookTest {
    @TempDir Path library;
    private static String fixed(String raw) { return raw.lines().map(s -> "       " + s).collect(java.util.stream.Collectors.joining("\n", "", "\n")); }
    private PreprocessorEngine.Outcome preprocess(String data,String code) throws Exception {
        String raw=ScopedInputTest.unit("NOMINAL-PGM",data,code);
        var map=SourceNormalizer.normalize(fixed(raw),"nominal.cbl",SourceNormalizer.SourceFormat.FIXED).sourceMap();
        return new PreprocessorEngine(Bindings.cobol(),new CopybookLibrary(library)).process(map,"nominal.cbl");
    }
    @Test void namesResolveWithoutInventingValuesOrStorageProofs() throws Exception {
        var pre=preprocess("COPY DFHAID.\nCOPY DFHBMSCA.","IF DFHPF3 = DFHENTER\n MOVE DFHRED TO DFHBMFSE END-IF.");
        assertEquals(0,pre.errors());assertEquals(0,pre.unresolved());
        assertEquals(2,pre.diagnostics().stream().filter(d->d.code().name().equals("NOMINAL_COPYBOOK")).count());
        var a=AstBoundaryTestSupport.analyze(pre,"nominal.cbl");
        var data=AstBoundaryTestSupport.nodes(a,Ast.DataEntry.class);
        assertTrue(data.stream().anyMatch(d->d.name().equals("DFHPF3")));
        assertTrue(data.stream().anyMatch(d->d.name().equals("DFHRED")));
        assertTrue(data.stream().allMatch(d->d.meta().syntheticModel()));
        assertTrue(data.stream().filter(d->d.level().equals("02")).allMatch(d->d.clauses().stream().anyMatch(c->c instanceof Ast.PictureClause p&&p.picture().equals("X")&&p.textExtent().orElse(0)==1)));
        assertTrue(data.stream().filter(d->!d.level().equals("88")).noneMatch(d->d.clauses().stream().anyMatch(Ast.ValueClause.class::isInstance)));
        assertTrue(data.stream().allMatch(d->d.meta().provenance().original().file().startsWith("model:")));
        assertTrue(data.stream().allMatch(d->!d.meta().provenance().includeChain().isEmpty()));
        assertEquals(0,a.report().programUnits().get(0).unresolved());
        assertEquals(ExternalClassification.CopyInputCompleteness.INCOMPLETE_NOMINAL_COPYBOOK,a.report().frontendState().copyInputCompleteness());
        var unit=a.model().programUnits().get(0);
        assertFalse(CoverageSnapshot.from("nominal.cbl",unit.program(),a.build().coverageByProgramUnit().get(unit.id()),0,0,0).dependencyCoverageComplete());
        var p=ScopedInputTest.product(a,0);
        assertEquals(Availability.KNOWN,p.entries().get(0).start().availability());
        assertEquals(InventoryStatus.INPUT_MISSING,p.entryInventory().status());
        assertTrue(p.storage().logicalExactViews().isEmpty());
        assertTrue(p.storage().logicalTextViews().isEmpty());
        assertTrue(p.storage().entryState().conditions().stream().allMatch(c->c.kind()==InitialStorageKind.UNKNOWN));
        assertTrue(pre.sourceDependencies().stream().allMatch(d->d.resolution()==SourceDependencyFact.Resolution.RESOLVED&&d.artifact().startsWith("model:")));
    }
    @Test void realCopybookOverridesCatalogue() throws Exception {
        Files.writeString(library.resolve("DFHAID.cpy"),"       01 DFHPF3 PIC X(8) VALUE 'REALPGM'.\n");
        var pre=preprocess("COPY 'DFHAID'.","CALL DFHPF3.");
        assertEquals(0,pre.errors());assertEquals(0,pre.unresolved());assertTrue(pre.diagnostics().isEmpty());
        assertTrue(pre.text().contains("REALPGM"));assertFalse(pre.text().contains("DFHENTER"));
        assertEquals("DFHAID.cpy",pre.sourceDependencies().get(0).artifact());
    }
    @Test void unsupportedMembersQualificationsAndReplacementsStayExplicit() throws Exception {
        for(var directive:List.of("COPY DFHUNKNOWN.","COPY DFHAID OF PRIVATE.","COPY DFHAID REPLACING DFHPF3 BY LOCAL-KEY.")) {
            var pre=preprocess(directive,"CALL 'VISIBLE'.");
            assertEquals(0,pre.errors());assertEquals(1,pre.unresolved(),directive);
            assertFalse(pre.text().contains("01 DFHENTER"));
        }
    }
    @Test void modelGapDoesNotContaminateSeparateUnit() throws Exception {
        var raw=ScopedInputTest.unit("MODELLED","COPY DFHAID.","CALL DFHPF3.")+ScopedInputTest.unit("ORDINARY","01 PGM PIC X(8) VALUE 'PROGA'.","CALL PGM.");
        var map=SourceNormalizer.normalize(fixed(raw),"units.cbl",SourceNormalizer.SourceFormat.FIXED).sourceMap();
        var pre=new PreprocessorEngine(Bindings.cobol(),new CopybookLibrary(library)).process(map,"units.cbl");
        assertEquals(0,pre.unresolved());
        var a=AstBoundaryTestSupport.analyze(pre,"units.cbl");
        assertFalse(a.report().inputComplete(a.model().programUnits().get(0).id()));
        assertTrue(a.report().inputComplete(a.model().programUnits().get(1).id()));
        assertEquals(Availability.KNOWN,ScopedInputTest.product(a,1).entries().get(0).start().availability());
    }
    @Test void nestedCopyPreservesBothFramesAndReplacementCannotEnrichModel() throws Exception {
        Files.writeString(library.resolve("OUTER.cpy"),"       COPY DFHAID.\n");
        var nested=preprocess("COPY OUTER.","CALL DFHPF3.");
        var a=AstBoundaryTestSupport.analyze(nested,"nominal.cbl");
        assertEquals(0,nested.unresolved());
        assertTrue(AstBoundaryTestSupport.nodes(a,Ast.DataEntry.class).stream()
                .allMatch(d->d.meta().provenance().includeChain().size()==2));
        var replaced=preprocess("COPY OUTER REPLACING ==DFHPF3== BY ==DFHPF3 PIC X VALUE 'A'==.","CALL 'VISIBLE'.");
        assertEquals(1,replaced.unresolved());
        assertFalse(replaced.text().contains("01 DFHPF3"));
    }
    @Test void duplicateModelsRemainAmbiguousAndOutputIsDeterministic() throws Exception {
        var first=preprocess("COPY DFHAID.\nCOPY DFHAID.","CALL DFHPF3.");
        var second=preprocess("COPY DFHAID.\nCOPY DFHAID.","CALL DFHPF3.");
        assertEquals(first.text(),second.text());
        assertEquals(first.diagnostics(),second.diagnostics());
        assertEquals(first.sourceDependencies(),second.sourceDependencies());
        var a=AstBoundaryTestSupport.analyze(first,"nominal.cbl");
        assertTrue(a.report().programUnits().get(0).ambiguous()>0);
        assertEquals(2,first.diagnostics().size());
    }
    @Test void nominalDeclarationsNeverSupplyScalarOrCellProofs() throws Exception {
        var pre=preprocess("COPY DFHAID.\nCOPY DFHBMSCA.","MOVE 'CANDIDAT' TO DFHPF3.\nMOVE DFHRED TO DFHPF3.\nCALL DFHPF3.");
        var p=ScopedInputTest.product(AstBoundaryTestSupport.analyze(pre,"nominal.cbl"),0);
        assertTrue(p.dataDeclarations().stream().allMatch(d->d.scalarText().isEmpty()&&d.scalarNumber().isEmpty()));
        for(var name:List.of("DFHPF3","DFHRED")) {
            assertTrue(FactDependencyLocalityTest.known(p,name,io.github.gustavo2358.cobolexplorer.semanticproduct.FactDependencies.FactKind.SOURCE_IDENTITY));
            for(var kind:List.of(io.github.gustavo2358.cobolexplorer.semanticproduct.FactDependencies.FactKind.LOCAL_CELL,
                    io.github.gustavo2358.cobolexplorer.semanticproduct.FactDependencies.FactKind.LOGICAL_TEXT,
                    io.github.gustavo2358.cobolexplorer.semanticproduct.FactDependencies.FactKind.PHYSICAL_VIEW))
                assertFalse(FactDependencyLocalityTest.known(p,name,kind),name+" "+kind);
        }
    }


    @Test void documentedCatalogueIsCompleteWithGroupsPicturesAndConditionNames() throws Exception {
        // Independent oracle transcribed from IBM CICS Primer, AID list and BMS constants.
        var expectedAid=Set.of(("DFHNULL DFHENTER DFHCLEAR DFHPA1 DFHPA2 DFHPA3 "
            +"DFHPF1 DFHPF2 DFHPF3 DFHPF4 DFHPF5 DFHPF6 DFHPF7 DFHPF8 DFHPF9 DFHPF10 DFHPF11 DFHPF12 "
            +"DFHPF13 DFHPF14 DFHPF15 DFHPF16 DFHPF17 DFHPF18 DFHPF19 DFHPF20 DFHPF21 DFHPF22 DFHPF23 DFHPF24 "
            +"DFHOPID DFHMSRE DFHTRIG DFHPEN DFHCLRP DFHSTRF").split(" "));
        var expectedBms=Set.of(("DFHBMPEM DFHBMPNL DFHBMPFF DFHBMPCR DFHBMASK DFHBMUNP DFHBMUNN DFHBMPRO "
            +"DFHBMBRY DFHBMDAR DFHBMFSE DFHBMPRF DFHBMASF DFHBMASB DFHBMPSO DFHBMPSI DFHBMEOF DFHBMCUR DFHBMEC DFHBMFLG DFHBMDET "
            +"DFHSA DFHERROR DFHCOLOR DFHPS DFHHLT DFH3270 DFHVAL DFHOUTLN DFHBKTRN DFHALL DFHDFT DFHDFCOL "
            +"DFHBLUE DFHRED DFHPINK DFHGREEN DFHTURQ DFHYELLO DFHNEUTR DFHBASE DFHDFHI DFHBLINK DFHREVRS DFHUNDLN "
            +"DFHMFIL DFHMENT DFHMFE DFHMT DFHMFT DFHMET DFHMFET DFHUNNOD DFHUNIMD DFHUNNUM DFHUNNUB DFHUNINT DFHUNNON "
            +"DFHPROTI DFHPROTN DFHDFFR DFHUNDER DFHRIGHT DFHOVER DFHLEFT DFHBOX DFHSOSI DFHTRANS DFHOPAQ").split(" "));
        var pre=preprocess("COPY DFHAID.\nCOPY DFHBMSCA.",
            "IF DFHNULL OF DFHAID = DFHENTER OF DFHAID CONTINUE END-IF.\n"
            +"IF DFHERASE OF DFHBMFLG OF DFHBMSCA CONTINUE END-IF.\n"
            +"IF DFHCURSR OF DFHBMFLG OF DFHBMSCA CONTINUE END-IF.\nCALL DFHNULL OF DFHAID.");
        var a=AstBoundaryTestSupport.analyze(pre,"nominal.cbl");
        assertEquals(0,pre.errors());assertEquals(0,a.report().programUnits().get(0).unresolved());
        var data=AstBoundaryTestSupport.nodes(a,Ast.DataEntry.class);
        for(var name:List.of("DFHAID","DFHBMSCA")) {
            var group=data.stream().filter(d->d.name().equals(name)).findFirst().orElseThrow();
            assertEquals("01",group.level());
            assertEquals(name.equals("DFHAID")?expectedAid:expectedBms,group.children().stream().map(Ast.DataEntry::name).collect(java.util.stream.Collectors.toSet()));
        }
        var flag=data.stream().filter(d->d.name().equals("DFHBMFLG")).findFirst().orElseThrow();
        assertEquals(Set.of("DFHERASE","DFHCURSR"),flag.children().stream().map(Ast.DataEntry::name).collect(java.util.stream.Collectors.toSet()));
        assertTrue(flag.children().stream().allMatch(d->d.level().equals("88")&&DeclarativeValueEvidence.extract(d,StorageLayoutSemantics.Profile.IBM_ENTERPRISE_6_4_FIXED_DISPLAY_1047,Map.of(),Optional.of(java.math.BigInteger.ONE)).isEmpty()));
        var p=ScopedInputTest.product(a,0);
        assertTrue(p.storage().logicalTextViews().isEmpty());
        var nominal=p.nominalValues().orElseThrow();
        assertEquals("NOMINAL_TEXT_SOURCE_V2",nominal.authority());
        assertEquals(105,nominal.symbols().size());
        assertTrue(nominal.symbols().stream().allMatch(x->x.extent()==1&&x.modelAssumed()));
    }
}
