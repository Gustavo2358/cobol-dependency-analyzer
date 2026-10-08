package com.imd.cobolexplorer;

import org.junit.jupiter.api.Test;
import java.math.BigInteger;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SourceStorageGeometryTest {
    record Fixture(SourceStorageGeometry geometry,Map<String,Integer> nodes) {
        SourceStorageGeometry.Span span(String name){return geometry.spans().get(nodes.get(name));}
    }
    private Fixture geometry(String data) {
        var f=StorageAccessTest.fixture(data,"CONTINUE.",StorageLayoutSemantics.Profile.UNSPECIFIED);
        var a=f.source();var id=a.model().programUnits().get(0).id();
        var nodes=new HashMap<String,Integer>();
        for(var d:AstBoundaryTestSupport.nodes(a,Ast.DataEntry.class))nodes.put(d.name(),d.meta().id());
        return new Fixture(f.effects().layout().sourceGeometry(id),nodes);
    }
    @Test void numericOverlaySharesTheSameIntervalAndSubordinateOffsets() {
        var f=geometry("01 DATE-PARTS.\n05 YEAR-A PIC 9(4).\n05 MONTH-A PIC 99.\n05 DAY-A PIC 99.\n01 DATE-N REDEFINES DATE-PARTS PIC 9(8).");
        assertEquals(f.span("DATE-PARTS").region(),f.span("DATE-N").region());
        assertEquals(BigInteger.valueOf(8),f.span("DATE-N").length());
        assertEquals(BigInteger.valueOf(4),f.span("MONTH-A").start());
        assertEquals(BigInteger.valueOf(6),f.span("DAY-A").start());
    }
    @Test void physicalWidthAndValuePrecisionAreDifferentFacts() {
        var f=geometry("01 REC.\n05 PACKED-A PIC S9(5)V99 COMP-3.\n05 BIN-A PIC S9(4) COMP.\n05 TXT PIC X(8).");
        assertEquals(BigInteger.valueOf(4),f.span("PACKED-A").length());
        assertEquals(BigInteger.valueOf(4),f.span("BIN-A").start());
        assertEquals(BigInteger.valueOf(2),f.span("BIN-A").length());
        assertEquals(BigInteger.valueOf(6),f.span("TXT").start());
        assertEquals(BigInteger.valueOf(14),f.span("REC").length());
    }
    @Test void fixedTableUsesOneIntervalRegardlessOfOccurrenceCount() {
        var f=geometry("01 REC.\n05 ITEMS PIC X(3) OCCURS 1000000000.\n05 TAIL-A PIC XX.");
        assertEquals(new BigInteger("3000000000"),f.span("ITEMS").length());
        assertEquals(new BigInteger("3000000000"),f.span("TAIL-A").start());
        assertEquals(3,f.geometry().spans().size());
    }
    @Test void unsupportedRepresentationDoesNotBecomeZeroWidth() {
        var f=geometry("01 REC.\n05 NATIONAL-A PIC N(4) USAGE NATIONAL.\n05 TAIL-A PIC XX.");
        assertNull(f.span("REC"));assertNull(f.span("TAIL-A"));
    }
}
