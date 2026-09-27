package io.github.gustavo2358.cobolexplorer;

import io.github.gustavo2358.cobolexplorer.semanticproduct.CobolSemanticPort;
import io.github.gustavo2358.cobolexplorer.semanticproduct.CobolSemanticProduct.*;
import java.util.*;
import io.github.gustavo2358.cobolexplorer.semanticproduct.projection.CobolSemanticProductProjector;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StorageBoundaryRegressionTest {
    private static CobolSemanticPort publish(String data, StorageLayoutSemantics.Profile profile) {
        var a=AstBoundaryTestSupport.analyze(ScalarMoveCheckpoint4ATest.program(data,"GOBACK."),"storage-boundary.cbl");
        return ExplorerMain.publishSemanticProduct(a.model().programUnits().get(0).id(),a.build(),a.tables(),a.occurrences(),a.resolution(),a.report(),profile);
    }
    @Test void invalidRenamesOfElementaryRecordKeepsUnknownAliasWithoutInventingPhysicalChild() {
        for(var profile:StorageLayoutSemantics.Profile.values())for(var extent:List.of(1,8,41)) {
            var p=publish("01 ORIGINAL PIC X("+extent+").\n01 OVERLAY REDEFINES ORIGINAL PIC X("+extent+").\n66 INVALID-RANGE RENAMES ORIGINAL THRU OVERLAY.\n01 INDEPENDENT PIC X.",profile);
            var storage=p.storage();var rename=storage.renames().get(0);
            assertEquals(StorageRelationStatus.UNPROVEN,rename.status());
            var node=storage.nodes().stream().filter(n->n.id().equals(rename.owner())).findFirst().orElseThrow();
            assertTrue(node.parent().isEmpty(),"an elementary record cannot be a physical container for a level-66 alias");
            var view=storage.views().stream().filter(v->v.node().equals(node.id())).findFirst().orElseThrow();
            assertTrue(view.extent().value().isEmpty());assertTrue(view.offset().value().isEmpty());assertTrue(view.codec().isEmpty());
            assertEquals(2,storage.bases().size(),"RENAMES must not allocate a new base");
            assertEquals(1,storage.relations().size(),"the valid overlay remains present");
        }
    }
    @Test void unprovedSubordinateOverlayCannotCertifyCompleteLogicalChainOrScalarCell() {
        for(var profile:StorageLayoutSemantics.Profile.values())for(var missing:List.of("UNDECLARED","FOREIGN-FIELD")) {
            var p=publish("01 OTHER-RECORD.\n05 FOREIGN-FIELD PIC X(8).\n01 AFFECTED-RECORD.\n05 BAD-VIEW REDEFINES "+missing+" PIC X(8).\n01 INDEPENDENT PIC X(8).",profile);
            var bad=p.dataDeclarations().stream().filter(d->d.canonicalName().equals("BAD-VIEW")).findFirst().orElseThrow();
            assertTrue(bad.scalarText().isEmpty());assertTrue(bad.scalarInteger().isEmpty());
            var node=p.storage().nodes().stream().filter(n->n.data().equals(Optional.of(bad.id()))).findFirst().orElseThrow();
            assertTrue(p.storage().logicalExactViews().stream().noneMatch(v->v.node().equals(node.id())));
            assertTrue(p.storage().relations().stream().anyMatch(r->r.status()==StorageRelationStatus.UNPROVEN));
            assertTrue(p.dataDeclarations().stream().filter(d->d.canonicalName().equals("INDEPENDENT")).findFirst().orElseThrow().scalarText().isPresent(),"unrelated root stays usable");
        }
    }

 private static CobolSemanticPort publishLogical(String data) {
   var a=AstBoundaryTestSupport.analyze(ScalarMoveCheckpoint4ATest.program(data,"GOBACK."),"logical-boundary.cbl");
   var components=StorageComponents.analyze(a.build(),a.tables(),a.resolution());
   var layout=StorageLayoutSemantics.analyze(a.build(),a.tables(),a.resolution(),a.report(),StorageLayoutSemantics.Profile.UNSPECIFIED,components,true);
   var storage=StorageAccessSemantics.analyze(a.build(),a.resolution(),layout);
   var moves=ScalarMoveSemantics.analyze(a.build(),a.tables(),a.resolution(),a.report(),components,Optional.of(storage));
   return CobolSemanticProductProjector.open(new CobolSemanticProductProjector.FrontendProducts(a.build(),a.tables(),a.occurrences(),a.resolution(),a.report(),moves,Optional.of(storage)),a.model().programUnits().get(0).id());
 }
 @Test void completeLogicalRenamesSharesCellButPartialAndInvalidRangesCannotClaimIt() {
   for(var clause:List.of("RENAMES TARGET-NAME", "RENAMES MISSING-NAME")) {
     var p=publishLogical("01 RECORD-NAME.\n05 TARGET-NAME PIC X(8).\n66 ALIAS-NAME "+clause+".");
     var graph=p.factDependencies().orElseThrow();
     var alias=p.storage().renames().get(0).owner();
     var binding=graph.bindings().stream().filter(b->b.node().equals("storage-node:"+alias.localId())).findFirst().orElseThrow();
     if(clause.equals("RENAMES TARGET-NAME")) {
       assertFalse(binding.exactCell().isEmpty(),"complete positive logical alias shares the existing value Cell");
       assertTrue(graph.bindings().stream().allMatch(b->b.exactCell().equals(binding.exactCell())));
     } else assertTrue(binding.exactCell().isEmpty(),"missing endpoint is not an exact Cell proof");
   }
   var p=publishLogical("01 RECORD-NAME.\n05 PREFIX-NAME PIC X(4).\n05 SUFFIX-NAME PIC X(4).\n66 PART-NAME RENAMES PREFIX-NAME.");
   assertTrue(p.factDependencies().orElseThrow().bindings().stream().allMatch(b->b.exactCell().isEmpty()),"partial alias cannot certify whole-record identity");
 }
}
