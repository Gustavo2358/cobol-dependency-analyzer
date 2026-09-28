package io.github.gustavo2358.cobolexplorer;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.gustavo2358.cobolexplorer.semanticproduct.ControlTopology.*;
class SqlDispatchTest {
    static io.github.gustavo2358.cobolexplorer.semanticproduct.CobolSemanticPort publish(String code) {
        return CicsMemoryLocalityTest.publish("01 SQL-TEXT.\n49 TEXT-LEN PIC S9(4) COMP-5.\n49 TEXT-DATA PIC X(80).\n01 IN-X PIC X(8).",code,false);
    }
    @Test void adjacentSqlCommandsRemainDistinctOccurrences() {
        var p=publish("EXEC SQL OPEN CURSOR-X END-EXEC\nEXEC SQL CLOSE CURSOR-X END-EXEC\nCALL 'AFTERSQL'.\nGOBACK.");
        assertEquals(4,p.statements().size());
        assertEquals(2,p.statements().stream().filter(s->s instanceof io.github.gustavo2358.cobolexplorer.semanticproduct.CobolSemanticProduct.ObservedStatement).count());
    }
    @Test void dynamicSqlHasNormalAndUnresolvedOutcomes() {
        for(var code:List.of("PREPARE STMT-X FROM :SQL-TEXT","EXECUTE STMT-X USING :IN-X","EXECUTE IMMEDIATE :SQL-TEXT")) {
            var p=publish("EXEC SQL "+code+" END-EXEC.\nCALL 'AFTERSQL'.\nGOBACK.");
            var sql=p.statements().stream().filter(s->s instanceof io.github.gustavo2358.cobolexplorer.semanticproduct.CobolSemanticProduct.ObservedStatement).findFirst().orElseThrow();
            var handle="statement:"+sql.header().id().localId();
            var outcomes=p.controlTopology().orElseThrow().outcomes().stream().filter(o->o.statement().equals(handle)).toList();
            assertTrue(outcomes.stream().anyMatch(o->o.kind()==OutcomeKind.NORMAL),code);
            assertTrue(outcomes.stream().anyMatch(o->o.kind()==OutcomeKind.UNKNOWN_LOCAL),code);
        }
    }
    @Test void wheneverIsLexicalAndIndependentOfRuntimeReachability() {
        var p=publish("GO TO RUN-SQL.\nEXEC SQL WHENEVER SQLERROR GO TO SQL-ERR END-EXEC.\nRUN-SQL.\nEXEC SQL DELETE FROM T END-EXEC.\nCALL 'NORMAL'.\nGOBACK.\nSQL-ERR.\nCALL 'ERRORPGM'.\nGOBACK.");
        var outcomes=p.controlTopology().orElseThrow().outcomes();
        assertTrue(outcomes.stream().anyMatch(o->o.role().equals("sql/SQLERROR")&&o.kind()==OutcomeKind.EXPLICIT_TRANSFER));
    }
    @Test void wheneverReplacementAndCategoriesDoNotLeak() {
        var p=publish("EXEC SQL WHENEVER SQLERROR GO TO SQL-ERR END-EXEC\nEXEC SQL WHENEVER NOT FOUND GO TO NO-DATA END-EXEC\nEXEC SQL WHENEVER SQLERROR CONTINUE END-EXEC\nEXEC SQL DELETE FROM T END-EXEC.\nGOBACK.\nSQL-ERR.\nCALL 'OLDERR'.\nGOBACK.\nNO-DATA.\nCALL 'NODATA'.\nGOBACK.");
        var outcomes=p.controlTopology().orElseThrow().outcomes();
        assertTrue(outcomes.stream().anyMatch(o->o.role().equals("sql/NOT_FOUND")&&o.kind()==OutcomeKind.EXPLICIT_TRANSFER));
        assertTrue(outcomes.stream().noneMatch(o->o.role().equals("sql/SQLERROR")&&o.kind()==OutcomeKind.EXPLICIT_TRANSFER));
    }
}
