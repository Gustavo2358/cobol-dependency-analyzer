package com.imd.cobolexplorer;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.imd.cobolexplorer.DependencyRelevance.Point;
import static com.imd.cobolexplorer.DependencyFlow.Exit;
import static com.imd.cobolexplorer.semanticproduct.ControlTopology.TargetKind;

class DependencyControlTest {
    private Point point(String node){return new Point(new Exit(TargetKind.OCCURRENCE,node),"scope");}
    private DependencyControl.Effect call(Point entry,String binding){return new DependencyControl.Effect(List.of(),List.of(new DependencyControl.Call(entry,binding)),List.of(),Set.of());}
    @Test void aRecursiveCycleCannotInventAReturnOrReachTheResume() {
        var built=new HashSet<Point>();var root=point("root");var body=point("body");var dead=point("dead");
        var summary=new DependencyControl(List.of(root),p->{built.add(p);return call(body,"resume");},
            (p,c,e)->DependencyControl.Effect.next(dead),p->p.equals(dead),100);
        assertEquals(0,summary.resultFacts);
        assertFalse(built.contains(dead));assertFalse(summary.reachable("dead"));assertFalse(summary.observed("root"));
    }
    @Test void aBaseReturnUnlocksRecursiveAndCallerContinuations() {
        var root=point("root");var body=point("body");var base=point("base");var query=point("query");
        var done=new Exit(TargetKind.COMPLETE,"scope");
        var summary=new DependencyControl(List.of(root),p->{
            if(p.equals(root))return call(body,"outer");
            if(p.equals(body))return new DependencyControl.Effect(List.of(base),List.of(new DependencyControl.Call(body,"recursive")),List.of(),Set.of());
            return DependencyControl.Effect.exit(done);
        },(p,c,e)->DependencyControl.Effect.next(c.binding().equals("outer")?query:base),p->p.equals(query),100);
        assertTrue(summary.resultFacts>0);assertTrue(summary.reachable("query"));
        assertTrue(summary.observed("body"));assertTrue(summary.observed("base"));
    }
    @Test void manyCallersUseOneBodyEquationAndPreserveEveryResume() {
        var body=point("body");var done=new Exit(TargetKind.COMPLETE,"scope");var built=new HashMap<Point,Integer>();
        var summary=new DependencyControl(List.of(point("0")),p->{
            built.merge(p,1,Integer::sum);
            if(p.equals(body)||p.equals(point("100")))return DependencyControl.Effect.exit(done);
            return call(body,p.exit().reference());
        },(p,c,e)->DependencyControl.Effect.next(point(Integer.toString(Integer.parseInt(c.binding())+1))),p->p.equals(point("100")),10000);
        assertEquals(1,built.get(body));assertTrue(summary.observed("body"));
        for(int i=0;i<=100;i++)assertTrue(summary.reachable(Integer.toString(i)));
    }
    @Test void independentEntriesDoNotReturnToTheirSpawner() {
        var root=point("root");var handler=point("handler");var halt=new Exit(TargetKind.PROGRAM_HALT,"halt");
        var summary=new DependencyControl(List.of(root),p->p.equals(root)
            ?new DependencyControl.Effect(List.of(),List.of(),List.of(handler),Set.of())
            :DependencyControl.Effect.exit(halt),(p,c,e)->{throw new AssertionError();},p->p.equals(handler),100);
        assertEquals(1,summary.resultFacts);
        assertTrue(summary.reachable("handler"));assertTrue(summary.observed("root"));
    }
    @Test void compactResultsPreserveMoreThanOneMachineWordOfDistinctExits() {
        var root=point("root");var body=point("body");var exits=new HashSet<Exit>();
        for(int i=0;i<150;i++)exits.add(new Exit(TargetKind.ESCAPE,"boundary-"+i));
        var received=new HashSet<Exit>();
        var summary=new DependencyControl(List.of(root),p->p.equals(root)?call(body,"resume")
            :new DependencyControl.Effect(List.of(),List.of(),List.of(),exits),
            (p,c,e)->{assertTrue(received.add(e));return DependencyControl.Effect.exit(e);},p->false,1000);
        assertEquals(exits,received);assertEquals(150,summary.resultPairs);
        assertEquals(300,summary.resultFacts);assertEquals(2,summary.summaryCount);
    }
    @Test void structuralWorkHonorsTheExplicitBudget() {
        var error=assertThrows(IllegalStateException.class,()->new DependencyControl(List.of(point("0")),
            p->DependencyControl.Effect.next(point(Integer.toString(Integer.parseInt(p.exit().reference())+1))),
            (p,c,e)->{throw new AssertionError();},p->false,10));
        assertTrue(error.getMessage().startsWith("RESOURCE_LIMIT:"));
    }
}
