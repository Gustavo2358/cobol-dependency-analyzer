package io.github.gustavo2358.cobolexplorer;

import java.util.*;

/** IBM command-condition interpretation; destinations remain grammar-owned by ControlTopology. */
final class CicsCommandControl {
    record Qualification(String ordinaryProof,List<String> unresolvedConditions,boolean programReturn,boolean conditionReturn) {
        Qualification {unresolvedConditions=List.copyOf(unresolvedConditions);}
    }
    static Optional<Qualification> qualify(CicsCommandSemantics.Fact fact) {
        if(!fact.supported())return Optional.empty();
        // Successful source completion is independent of buffer materialization.
        // Only the supported command subset reaches this rule; NOHANDLE is not
        // permission to qualify an opaque command or an unsupported SEND form.
        boolean local=fact.options().stream().anyMatch(o->o.name().equals("RESP")||o.name().equals("NOHANDLE"));
        var remainder=new ArrayList<String>();
        boolean exits=fact.command()==CicsCommandSemantics.Kind.RETURN;
        boolean returnConditions=exits&&fact.options().stream().anyMatch(o->Set.of("TRANSID","COMMAREA","IMMEDIATE").contains(o.name()));
        if(!local&&(!exits||returnConditions))remainder.add("handler-or-default-condition");
        // SEND MAP OVERFLOW is explicitly exempt from generic RESP/NOHANDLE rules.
        if(fact.command()==CicsCommandSemantics.Kind.SEND_MAP)remainder.add("overflow");
        String command=switch(fact.command()) {case RETURN->"return";case SYNCPOINT->"syncpoint";case SYNCPOINT_ROLLBACK->"syncpoint-rollback";case RECEIVE_MAP->"receive-map";case SEND_MAP->"send-map";case SEND_TERMINAL->"send-terminal";case RETRIEVE->"retrieve";};
        return Optional.of(new Qualification("cics-command-"+command+(exits?"-program-return":"-ordinary-return"),remainder,exits,returnConditions&&local));
    }
}
