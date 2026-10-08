package com.imd.cobolexplorer;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.imd.cobolexplorer.semanticproduct.*;
import static org.junit.jupiter.api.Assertions.*;
class NominalTableTest {
    static final String MENU="""
        01 ITEMS.
         05 INITIAL-ROWS.
          10 FILLER PIC 9(2) VALUE 1.
          10 FILLER PIC X(8) VALUE 'NOTPROG1'.
          10 FILLER PIC X(8) VALUE 'PROGA001'.
          10 FILLER PIC 9(2) VALUE 2.
          10 FILLER PIC X(8) VALUE 'NOTPROG2'.
          10 FILLER PIC X(8) VALUE 'PROGB001'.
         05 ROWS REDEFINES INITIAL-ROWS.
          10 ITEM-ROW OCCURS 3.
           15 ITEM-NUM PIC 9(2).
           15 ITEM-DESC PIC X(8).
           15 ITEM-TARGET PIC X(8).
        01 CHOICE-NUM PIC 9.
        """;
    static CobolSemanticPort port(String data,String code){return CicsMemoryLocalityTest.publish(data,code,false);}
    static Set<String> values(NominalValues f){var node=f.queries().get(0).node();return f.tableFields().stream().filter(t->t.node().equals(node)).flatMap(t->t.initial().stream()).map(NominalValues.Initial::value).collect(java.util.stream.Collectors.toSet());}
    @Test void overlayUsesOnlySelectedColumnAndPreservesMissingRow() {
        var f=port(MENU,"CALL ITEM-TARGET(CHOICE-NUM).\nGOBACK.").nominalValues().orElseThrow();
        assertEquals("NOMINAL_TEXT_SOURCE_V4",f.authority());
        assertEquals(Set.of("PROGA001","PROGB001"),values(f));
        assertTrue(f.tableFields().stream().filter(t->t.node().equals(f.queries().get(0).node())).findFirst().orElseThrow().initial().stream().allMatch(i->!i.origin().equals(f.queries().get(0).node())));
    }
    @Test void directAndNestedOccursKeepRealDeclarationOrigins() {
        for(var declaration:List.of("01 TABLE-AREA.\n05 ITEM-TARGET PIC X(8) OCCURS 2 VALUE 'PROGA001'.", "01 TABLE-AREA.\n05 OUTER-ROW OCCURS 2.\n10 ITEM-TARGET PIC X(8) OCCURS 2 VALUE 'PROGA001'.")) {
            var nested=declaration.contains("OUTER-ROW");
            var f=port(declaration,"CALL ITEM-TARGET("+(nested?"1, 2":"2")+").\nGOBACK.").nominalValues().orElseThrow();
            assertEquals(Set.of("PROGA001"),values(f));
        }
    }
    @Test void conditionNamesDoNotAllocateTableStorageOrSeedInitializers() {
        for(var suffix:List.of("", "\n88 IS-PROGA VALUE 'PROGA001'.", "\n88 IS-OTHER VALUE 'POISON88'.\n88 IS-PROGA VALUE 'PROGA001'.")) {
            var f=port("01 TABLE-AREA.\n05 ITEM-TARGET PIC X(8) OCCURS 2 VALUE 'PROGA001'."+suffix,
                "CALL ITEM-TARGET(1).\nGOBACK.").nominalValues().orElseThrow();
            assertEquals(Set.of("PROGA001"),values(f));
            assertEquals(8,f.symbols().stream().filter(s->s.node().equals(f.queries().get(0).node())).findFirst().orElseThrow().extent());
            assertEquals(1,f.tableFields().size());
        }
    }
    @Test void siblingConditionNamesPreserveOffsetsAndOtherFields() {
        for(var condition:List.of("", "\n88 DESCRIPTION-SET VALUE 'POISON88'.")) {
            var f=port(MENU.replace("15 ITEM-DESC PIC X(8).","15 ITEM-DESC PIC X(8)."+condition),
                "CALL ITEM-TARGET(1).\nGOBACK.").nominalValues().orElseThrow();
            assertEquals(Set.of("PROGA001","PROGB001"),values(f));
        }
    }
    @Test void finiteSummaryDoesNotTurnAnIndexedWriteIntoStrongUpdate() {
        var f=port(MENU,"MOVE 'PROGC001' TO ITEM-TARGET(CHOICE-NUM).\nCALL ITEM-TARGET(CHOICE-NUM).\nGOBACK.").nominalValues().orElseThrow();
        var query=f.queries().get(0).node();
        assertEquals(Set.of("PROGA001","PROGB001"),values(f));
        var write=f.assignments().stream().filter(a->a.target().equals(query)).findFirst().orElseThrow();
        assertEquals(new NominalValues.Term("LITERAL","PROGC001"),write.source());
        assertTrue(f.tableFields().stream().anyMatch(t->t.node().equals(write.target())));
    }
    @Test void groupLiteralWriteProjectsItsOwnColumn() {
        var f=port(MENU,"MOVE '99NOTPROG3PROGC001' TO ITEM-ROW(1).\nCALL ITEM-TARGET(1).\nGOBACK.").nominalValues().orElseThrow();
        assertEquals(new NominalValues.Term("LITERAL","PROGC001"),f.assignments().stream().filter(a->a.target().equals(f.queries().get(0).node())).findFirst().orElseThrow().source());
    }
    @Test void invalidIndicesAndUnprovedGeometryDoNotProduceTableQuery() {
        for(var index:List.of("0","4","1, 2"))assertTrue(port(MENU,"CALL ITEM-TARGET("+index+").\nGOBACK.").nominalValues().isEmpty());
        for(var declaration:List.of(MENU.replace("OCCURS 3","OCCURS 1 TO 3 DEPENDING ON CHOICE-NUM"),MENU.replace("PIC 9(2).","PIC 9(2) COMP.")))
            assertTrue(port(declaration,"CALL ITEM-TARGET(1).\nGOBACK.").nominalValues().isEmpty());
    }
    @Test void sourceTableContractRejectsForeignOriginsAndOldAuthorities() {
        var f=port(MENU,"CALL ITEM-TARGET(1).\nGOBACK.").nominalValues().orElseThrow();
        assertThrows(IllegalArgumentException.class,()->new NominalValues("NOMINAL_TEXT_SOURCE_V3",f.symbols(),f.assignments(),f.conditions(),f.queries(),f.tableFields()));
        assertThrows(IllegalArgumentException.class,()->new NominalValues(f.authority(),f.symbols(),f.assignments(),f.conditions(),f.queries(),List.of(new NominalValues.TableField(f.queries().get(0).node(),List.of(new NominalValues.Initial("foreign","PROGC001"))))));
    }
    @Test void realTableOverlayCannotLaunderModelGeometry() {
        var p=port("COPY DFHAID.\n01 TABLE-AREA REDEFINES DFHAID.\n05 ITEM-TARGET PIC X OCCURS 37.","CALL ITEM-TARGET(1).\nGOBACK.");
        var f=p.nominalValues().orElseThrow();var query=f.queries().get(0).node();
        assertTrue(f.symbols().stream().filter(s->s.node().equals(query)).findFirst().orElseThrow().modelAssumed());
        assertTrue(f.tableFields().stream().filter(t->t.node().equals(query)).findFirst().orElseThrow().initial().isEmpty());
    }
}
