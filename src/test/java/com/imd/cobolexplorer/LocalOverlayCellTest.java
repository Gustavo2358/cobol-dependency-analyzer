package com.imd.cobolexplorer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import static com.imd.cobolexplorer.semanticproduct.CobolSemanticProduct.*;
class LocalOverlayCellTest {
    static final String DATA="01 INPUT-AREA.\n05 LENGTH-A PIC S9(4) COMP.\n05 FLAG-A PIC X.\n05 NAME-I PIC X(8).\n"
        +"01 OUTPUT-AREA REDEFINES INPUT-AREA.\n05 FILLER PIC X(3).\n05 NAME-O PIC X(8).";
    static com.imd.cobolexplorer.semanticproduct.CobolSemanticPort product(String data,String code) {
        var a=AstBoundaryTestSupport.analyze(ScalarMoveCheckpoint4ATest.program(data,code+"\nGOBACK."),"local-overlay.cbl");
        return EofUnitBoundaryTest.publish(a,0,StorageLayoutSemantics.Profile.UNSPECIFIED);
    }
    @Test void exactTextAliasesAndBinaryFieldShareOneClosedSourceInventory() {
        var p=product(DATA,"MOVE -1 TO LENGTH-A.\nMOVE 'PGM00001' TO NAME-O.\nMOVE NAME-I TO NAME-O.");
        assertEquals(1,p.moves().get(0).numericTransfers().size());
        assertEquals(CopySemantics.FULL_IDENTITY,p.moves().get(1).copySemantics());
        // Identical text extent and cell make this an identity assignment.
        assertEquals(CopySemantics.FULL_IDENTITY,p.moves().get(2).copySemantics());
        var graph=p.factDependencies().orElseThrow();
        var byName=new HashMap<String,String>();
        for(var d:p.dataDeclarations())for(var n:p.storage().nodes())if(n.data().filter(d.id()::equals).isPresent())byName.put(d.canonicalName(),"storage-node:"+n.id().localId());
        var bindings=new HashMap<String,com.imd.cobolexplorer.semanticproduct.FactDependencies.Binding>();graph.bindings().forEach(b->bindings.put(b.node(),b));
        assertEquals(bindings.get(byName.get("NAME-I")).exactCell(),bindings.get(byName.get("NAME-O")).exactCell());
        assertFalse(bindings.get(byName.get("NAME-I")).exactCell().isEmpty());
        // OUTPUT-AREA includes the unnamed bytes covering LENGTH-A and FLAG-A.
        assertTrue(bindings.get(byName.get("OUTPUT-AREA")).cells().contains(bindings.get(byName.get("LENGTH-A")).exactCell()));
        assertTrue(bindings.get(byName.get("OUTPUT-AREA")).cells().contains(bindings.get(byName.get("FLAG-A")).exactCell()));
    }
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory;
    @Test void copyExpansionPreservesLocalValueCells() throws Exception {
        java.nio.file.Files.writeString(directory.resolve("LOCAL-BMS.cpy"),DATA.lines().map(l->"       "+l).collect(java.util.stream.Collectors.joining("\n"))+"\n");
        java.nio.file.Files.writeString(directory.resolve("NEXT-DATA.cpy"),"       01 SENTINEL PIC X.\n");
        var raw=ScalarMoveCheckpoint4ATest.program("COPY LOCAL-BMS.\nCOPY NEXT-DATA.","MOVE -1 TO LENGTH-A.\nGOBACK.");
        var fixed=raw.lines().map(l->"       "+l).collect(java.util.stream.Collectors.joining("\n"))+"\n";
        var pre=new PreprocessorEngine(Bindings.cobol(),new CopybookLibrary(directory))
            .process(SourceNormalizer.normalize(fixed,"copy-boundary.cbl",SourceNormalizer.SourceFormat.FIXED).sourceMap(),"copy-boundary.cbl");
        var a=AstBoundaryTestSupport.analyze(pre,"copy-boundary.cbl");
        var p=EofUnitBoundaryTest.publish(a,0,StorageLayoutSemantics.Profile.UNSPECIFIED);
        assertEquals(1,p.moves().get(0).numericTransfers().size());
    }
    @Test void fixedTablesDoNotEraseDisjointNeighborCells() {
        for(String count:List.of("10","1000000000")) {
            var data="01 INPUT-AREA.\n05 LENGTH-A PIC S9(4) COMP.\n05 NAME-I PIC X(8).\n05 ROW-A OCCURS "+count+".\n10 ITEM-A PIC X(2).\n05 TAIL-I PIC X(4).\n"
                +"01 OUTPUT-AREA REDEFINES INPUT-AREA.\n05 FILLER PIC X(2).\n05 NAME-O PIC X(8).\n05 ROW-B OCCURS "+count+".\n10 ITEM-B PIC X(2).\n05 TAIL-O PIC X(4).";
            var p=product(data,"MOVE 'PGM00001' TO NAME-O.\nMOVE 'TAIL' TO TAIL-O.");
            assertTrue(p.moves().stream().allMatch(m->m.copySemantics()==CopySemantics.FULL_IDENTITY),count);
            assertTrue(p.factDependencies().orElseThrow().bindings().size()<20,"no occurrence enumeration");
        }
    }
    @Test void repeatedRangeBlocksOverlappingScalarAlias() {
        var p=product("01 A.\n05 ROW-A OCCURS 2.\n10 ITEM-A PIC X(4).\n01 B REDEFINES A PIC X(8).","MOVE 'PGM00001' TO B.");
        var d=p.dataDeclarations().stream().filter(x->x.canonicalName().equals("B")).findFirst().orElseThrow();
        assertTrue(d.scalarText().isPresent(),"a fixed whole alias retains its declared character domain");
        var node=p.storage().nodes().stream().filter(n->n.data().filter(d.id()::equals).isPresent()).findFirst().orElseThrow();
        var binding=p.factDependencies().orElseThrow().bindings().stream().filter(b->b.node().equals("storage-node:"+node.id().localId())).findFirst().orElseThrow();
        assertTrue(binding.exactCell().isEmpty(),"table range cannot gain an independent overlapping cell");
        assertFalse(binding.regions().isEmpty(),"whole alias must keep the shared table bound");
    }
    @Test void partialOrIncompatibleNamedOverlapCannotAcquireIndependentCells() {
        for(var data:List.of("01 A PIC X(8).\n01 B REDEFINES A PIC X(4).",
                "01 A PIC 9(8).\n01 B REDEFINES A PIC X(8).")) {
            var p=product(data,"MOVE '12345678' TO A.");
            assertTrue(p.factDependencies().orElseThrow().bindings().stream().allMatch(b->b.exactCell().isEmpty()));
        }
    }
}
