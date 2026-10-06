package io.github.gustavo2358.cobolexplorer;

import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.gustavo2358.cobolexplorer.semanticproduct.transport.SemanticProductJsonWriter;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Physical retention oracle over the owned graph, not a second writer/evaluator. */
class TransportOwnershipTest {
    private static int retainedParagraphs(Object root)throws Exception {
        var visited=Collections.newSetFromMap(new IdentityHashMap<Object,Boolean>());
        var pending=new ArrayDeque<Object>();pending.add(root);int count=0;
        while(!pending.isEmpty()) {
            var value=pending.removeFirst();if(!visited.add(value))continue;
            var type=value.getClass();String name=type.getName();
            if(name.endsWith("$PerformParagraphDocument"))count++;
            if(value instanceof Map<?,?> map){map.forEach((k,v)->{if(k!=null)pending.add(k);if(v!=null)pending.add(v);});continue;}
            if(value instanceof Iterable<?> list&&!name.startsWith("io.github.gustavo2358")) {for(var item:list)if(item!=null)pending.add(item);continue;}
            if(!name.startsWith("io.github.gustavo2358"))continue;
            for(var field:type.getDeclaredFields())if(!java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                field.setAccessible(true);var item=field.get(value);if(item!=null)pending.add(item);
            }
        }
        return count;
    }
    @Test void overlappingMembershipRetainsOnlyDistinctParagraphPayloads()throws Exception {
        int size=40;var main=new StringBuilder();var body=new StringBuilder();
        for(int i=1;i<=size;i++){main.append("PERFORM P").append(i).append(" THRU P").append(size).append(".\n");body.append("P").append(i).append(".\nMOVE 'A' TO WS-PGM.\n");}
        var port=ScalarMoveCheckpoint4ATest.publish(PerformFamilyTest.source(main.toString(),body.toString()));
        var access=SemanticProductJsonWriter.class.getDeclaredMethod("documentValue",io.github.gustavo2358.cobolexplorer.semanticproduct.CobolSemanticPort.class);access.setAccessible(true);
        var owned=access.invoke(null,port);new ObjectMapper().writeValueAsBytes(owned);
        assertEquals(size,retainedParagraphs(owned),"one retained complete paragraph payload per published paragraph");
        var output=java.nio.file.Files.createTempFile("shared-transport-",".json");
        try{SemanticProductJsonWriter.write(port,output);assertArrayEquals(SemanticProductJsonWriter.serialize(port),java.nio.file.Files.readAllBytes(output));}
        finally{java.nio.file.Files.delete(output);}
    }
}
