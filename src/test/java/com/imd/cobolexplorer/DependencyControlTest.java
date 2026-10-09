package com.imd.cobolexplorer;

import java.util.*;
import java.util.function.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.imd.cobolexplorer.DependencyRelevance.Point;
import static com.imd.cobolexplorer.DependencyFlow.Exit;
import static com.imd.cobolexplorer.semanticproduct.ControlTopology.TargetKind;

class DependencyControlTest {
    private Point point(String node){return new Point(new Exit(TargetKind.OCCURRENCE,node),"scope");}
    private DependencyControl.Effect call(Point entry,String binding){return new DependencyControl.Effect(List.of(),List.of(new DependencyControl.Call(entry,binding)),List.of(),Set.of());}
    private DependencyControl control(List<Point> roots,Function<Exit,DependencyControl.Effect> effects,
            DependencyControl.Delivery delivery,Predicate<Exit> observation,long maxWork) {
        return new DependencyControl(roots,p->DependencyControl.Rule.flow(effects.apply(p)),delivery,observation,maxWork);
    }
    @Test void aRecursiveCycleCannotInventAReturnOrReachTheResume() {
        var built=new HashSet<Exit>();var root=point("root");var body=point("body");var dead=point("dead");
        var summary=control(List.of(root),p->{built.add(p);return call(body,"resume");},
            (c,e)->DependencyControl.Effect.next(dead.exit()),p->p.equals(dead.exit()),100);
        assertEquals(0,summary.resultFacts);
        assertFalse(built.contains(dead.exit()));assertFalse(summary.reachable("dead"));assertFalse(summary.observed("root"));
    }
    @Test void aBaseReturnUnlocksRecursiveAndCallerContinuations() {
        var root=point("root");var body=point("body");var base=point("base");var query=point("query");
        var done=new Exit(TargetKind.COMPLETE,"scope");
        var summary=control(List.of(root),p->{
            if(p.equals(root.exit()))return call(body,"outer");
            if(p.equals(body.exit()))return new DependencyControl.Effect(List.of(base.exit()),List.of(new DependencyControl.Call(body,"recursive")),List.of(),Set.of());
            return DependencyControl.Effect.exit(done);
        },(c,e)->DependencyControl.Effect.next((c.binding().equals("outer")?query:base).exit()),p->p.equals(query.exit()),100);
        assertTrue(summary.resultFacts>0);assertTrue(summary.reachable("query"));
        assertTrue(summary.observed("body"));assertTrue(summary.observed("base"));
    }
    @Test void manyCallersUseOneBodyEquationAndPreserveEveryResume() {
        var body=point("body");var done=new Exit(TargetKind.COMPLETE,"scope");var built=new HashMap<Exit,Integer>();
        var summary=control(List.of(point("0")),p->{
            built.merge(p,1,Integer::sum);
            if(p.equals(body.exit())||p.equals(point("100").exit()))return DependencyControl.Effect.exit(done);
            return call(body,p.reference());
        },(c,e)->DependencyControl.Effect.next(point(Integer.toString(Integer.parseInt(c.binding())+1)).exit()),p->p.equals(point("100").exit()),10000);
        assertEquals(1,built.get(body.exit()));assertTrue(summary.observed("body"));
        for(int i=0;i<=100;i++)assertTrue(summary.reachable(Integer.toString(i)));
    }
    @Test void independentEntriesDoNotReturnToTheirSpawner() {
        var root=point("root");var handler=point("handler");var halt=new Exit(TargetKind.PROGRAM_HALT,"halt");
        var summary=control(List.of(root),p->p.equals(root.exit())
            ?new DependencyControl.Effect(List.of(),List.of(),List.of(handler),Set.of())
            :DependencyControl.Effect.exit(halt),(c,e)->{throw new AssertionError();},p->p.equals(handler.exit()),100);
        assertEquals(1,summary.resultFacts);
        assertTrue(summary.reachable("handler"));assertTrue(summary.observed("root"));
    }
    @Test void compactResultsPreserveMoreThanOneMachineWordOfDistinctExits() {
        var root=point("root");var body=point("body");var exits=new HashSet<Exit>();
        for(int i=0;i<150;i++)exits.add(new Exit(TargetKind.ESCAPE,"boundary-"+i));
        var received=new HashSet<Exit>();
        var summary=control(List.of(root),p->p.equals(root.exit())?call(body,"resume")
            :new DependencyControl.Effect(List.of(),List.of(),List.of(),exits),
            (c,e)->{assertTrue(received.add(e));return DependencyControl.Effect.exit(e);},p->false,1000);
        assertEquals(exits,received);assertEquals(150,summary.resultPairs);
        assertEquals(300,summary.resultFacts);assertEquals(2,summary.summaryCount);
    }
    @Test void aLateCallerReceivesAllExistingExitsExactlyOnce() {
        var root=point("root");var body=point("body");var query=point("query");
        var x=new Exit(TargetKind.ESCAPE,"x");var y=new Exit(TargetKind.ESCAPE,"y");
        var received=new HashMap<String,Set<Exit>>();
        var summary=control(List.of(root),p->p.equals(root.exit())?call(body,"first")
            :new DependencyControl.Effect(List.of(),List.of(),List.of(),Set.of(x,y)),(c,e)->{
                assertTrue(received.computeIfAbsent(c.binding(),k->new HashSet<>()).add(e));
                return c.binding().equals("first")?call(body,"late"):DependencyControl.Effect.next(query.exit());
            },p->p.equals(query.exit()),1000);
        assertEquals(Map.of("first",Set.of(x,y),"late",Set.of(x,y)),received);
        assertTrue(summary.reachable("query"));assertEquals(4,summary.resultPairs);
    }
    @Test void aLateSuccessorPropagatesResultsThatAlreadyExist() {
        var root=point("root");var body=point("body");var bridge=point("bridge");var query=point("query");
        var done=new Exit(TargetKind.COMPLETE,"scope");
        var summary=control(List.of(root),p->{
            if(p.equals(root.exit()))return call(bridge,"outer");
            if(p.equals(bridge.exit()))return call(body,"discover-edge");
            return DependencyControl.Effect.exit(done);
        },(c,e)->DependencyControl.Effect.next((c.binding().equals("outer")?query:body).exit()),p->p.equals(query.exit()),1000);
        assertTrue(summary.reachable("query"));assertEquals(2,summary.resultPairs);
    }
    @Test void structuralWorkHonorsTheExplicitBudget() {
        var error=assertThrows(IllegalStateException.class,()->control(List.of(point("0")),
            p->DependencyControl.Effect.next(point(Integer.toString(Integer.parseInt(p.reference())+1)).exit()),
            (c,e)->{throw new AssertionError();},p->false,10));
        assertTrue(error.getMessage().startsWith("RESOURCE_LIMIT:"));
    }
    @Test void equalEndpointsDoNotMergeDifferentEscapePolicies() {
        var exit=new Exit(TargetKind.OCCURRENCE,"body");
        var a=new Point(exit,"same-endpoint","ancestor-a");var b=new Point(exit,"same-endpoint","ancestor-b");
        var halt=new Exit(TargetKind.PROGRAM_HALT,"halt");var escaped=new Exit(TargetKind.ESCAPE,"ancestor-a");
        var summary=new DependencyControl(List.of(a,b),p->p.equals(exit)
            ?DependencyControl.Rule.boundary(escaped,halt,scope->scope.escapeScope().equals("ancestor-a"))
            :DependencyControl.Rule.flow(DependencyControl.Effect.exit(halt)),
            (c,e)->{throw new AssertionError();},p->p.equals(halt),100);
        int ai=summary.graph.find(a),bi=summary.graph.find(b);
        assertNotEquals(ai,bi);assertNotEquals(summary.graph.component(ai),summary.graph.component(bi));
        assertEquals(-1,summary.graph.outgoing(ai));assertTrue(summary.graph.outgoing(bi)>=0);
        assertTrue(summary.reachable("halt"));assertEquals(3,summary.summaryCount);assertEquals(2,summary.graph.physicalSize());
    }
    @Test void onePhysicalChainServesEveryObligationWithoutCloningItsEdges() {
        int obligations=128,length=200;var roots=new ArrayList<Point>();var built=new HashMap<Exit,Integer>();
        for(int i=0;i<obligations;i++)roots.add(new Point(point("0").exit(),"endpoint-"+i,"ancestor-"+i));
        var summary=new DependencyControl(roots,p->{
            built.merge(p,1,Integer::sum);int node=Integer.parseInt(p.reference());
            return DependencyControl.Rule.flow(node==length?DependencyControl.Effect.exit(new Exit(TargetKind.PROGRAM_RETURN,"done"))
                :DependencyControl.Effect.next(point(Integer.toString(node+1)).exit()));
        },(c,e)->{throw new AssertionError();},p->p.reference().equals(Integer.toString(length)),100000);
        assertEquals(length+1,summary.graph.physicalSize());assertEquals(length,summary.graph.edgeCount());
        assertEquals((length+1)*obligations,summary.summaryCount);assertEquals((length+1)*obligations,summary.resultFacts);
        assertTrue(built.values().stream().allMatch(count->count==1));
        for(var root:roots)assertTrue(summary.graph.outgoing(summary.graph.find(root))>=0);
    }
    @Test void aSharedCallDeliversOnceButPreservesEveryCallerObligation() {
        var body=point("body");var done=new Exit(TargetKind.COMPLETE,"callee");var rootExit=point("root").exit();
        var roots=List.of(new Point(rootExit,"a"),new Point(rootExit,"b"),new Point(rootExit,"c"));var received=new ArrayList<Exit>();
        var summary=new DependencyControl(roots,p->DependencyControl.Rule.flow(p.equals(rootExit)?call(body,"resume")
            :p.equals(body.exit())?DependencyControl.Effect.exit(done):DependencyControl.Effect.exit(new Exit(TargetKind.PROGRAM_RETURN,"end"))),
            (c,e)->{received.add(e);return DependencyControl.Effect.next(point("resume").exit());},p->p.reference().equals("resume"),100);
        assertEquals(List.of(done),received);assertEquals(3,summary.resultPairs);assertEquals(3,summary.graph.physicalSize());
        for(var root:roots) {
            var resume=new Point(point("resume").exit(),root.endpoint());assertTrue(summary.graph.find(resume)>=0);
            assertFalse(summary.graph.find(new Point(point("resume").exit(),"scope"))>=0);
        }
    }
    @Test void unrelatedSparseScopesDoNotAllocateTheirCartesianProduct() {
        var roots=new ArrayList<Point>();for(int i=0;i<512;i++)roots.add(new Point(point(Integer.toString(i)).exit(),"scope-"+i));
        var summary=new DependencyControl(roots,p->DependencyControl.Rule.flow(DependencyControl.Effect.exit(new Exit(TargetKind.PROGRAM_RETURN,"done"))),
            (c,e)->{throw new AssertionError();},p->false,10000);
        assertEquals(512,summary.graph.physicalSize());assertEquals(512,summary.graph.scopeCount());assertEquals(512,summary.summaryCount);assertEquals(0,summary.graph.edgeCount());
        assertEquals(-1,summary.graph.find(new Point(roots.get(0).exit(),roots.get(1).endpoint())));
    }
}
