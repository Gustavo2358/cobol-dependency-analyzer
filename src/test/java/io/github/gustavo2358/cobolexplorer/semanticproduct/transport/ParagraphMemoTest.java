package io.github.gustavo2358.cobolexplorer.semanticproduct.transport;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ParagraphMemoTest {
 private record Payload(String id,String facts) {
  @Override public int hashCode(){throw new AssertionError("whole payload hashing defeats cheap identity lookup");}
 }
 @Test void identityOnlySelectsBucketAndNeverHashesFullPayload() {
  int[] mappings={0};var memo=new ParagraphMemo<String,Payload,Object>(Payload::id,p->{mappings[0]++;return new Object();});
  var a=new Payload("Aa","first");var b=new Payload("BB","first");
  var first=memo.get(a);assertSame(first,memo.get(new Payload("Aa","first")));
  assertNotSame(first,memo.get(b));assertNotSame(first,memo.get(new Payload("Aa","changed completion or proof")));
  assertSame(first,memo.get(a));assertEquals(3,mappings[0]);
 }
}
