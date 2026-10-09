import java.nio.file.*;
import java.util.*;
import jdk.jfr.consumer.*;
public class JfrPhaseStats {
 static Map<String,Long> cpu=new HashMap<>(),leaf=new HashMap<>(),alloc=new HashMap<>(),classes=new HashMap<>(),nodeOrigin=new HashMap<>(),old=new HashMap<>(),oldOrigin=new HashMap<>();
 static void add(Map<String,Long> m,String k,long n){m.merge(k,n,Long::sum);}
 static String category(List<String> frames){
  if(frames.contains("com.imd.cobolexplorer.DependencyFlow.resolve"))return "query-resolution";
  if(frames.contains("com.imd.cobolexplorer.DependencyFlow.workRank"))return "queue-rank";
  if(frames.stream().anyMatch(s->s.startsWith("com.imd.cobolexplorer.DependencyControl.")))return "control-summary";
  if(frames.stream().anyMatch(s->s.startsWith("com.imd.cobolexplorer.DependencyRelevance.")))return "input-relevance";
  if(frames.contains("com.imd.cobolexplorer.DependencyFlow.join"))return "state-join";
  if(frames.contains("com.imd.cobolexplorer.DependencyFlow.subscribe"))return "summary-input-projection";
  if(frames.contains("com.imd.cobolexplorer.DependencyFlow.enqueue"))return "enqueue-and-join";
  if(frames.contains("com.imd.cobolexplorer.DependencyFlow.step"))return "local-step";
  if(frames.contains("com.imd.cobolexplorer.DependencyFlow$State.join"))return "state-join";
  if(frames.stream().anyMatch(s->s.startsWith("com.imd.cobolexplorer.DependencyFlow.write")))return "state-write";
  if(frames.stream().anyMatch(s->s.startsWith("com.imd.cobolexplorer.DependencyRelevance.")))return "input-relevance";
  if(frames.stream().anyMatch(s->s.startsWith("com.imd.cobolexplorer.DependencyFlow.")))return "other-dataflow";
  if(frames.stream().anyMatch(s->s.startsWith("com.imd.cobolexplorer.")))return "frontend-or-other-product";
  return "other-or-truncated";
 }
 static String map(Map<String,Long> values){var sorted=new ArrayList<>(values.entrySet());sorted.sort(Map.Entry.<String,Long>comparingByValue().reversed());var out=new StringJoiner(",","{","}");for(var e:sorted)out.add("\""+e.getKey()+"\":"+e.getValue());return out.toString();}
 public static void main(String[] args)throws Exception {
  long events=0;try(var recording=new RecordingFile(Path.of(args[0]))){while(recording.hasMoreEvents()){
   var e=recording.readEvent();String type=e.getEventType().getName();if(!Set.of("jdk.ExecutionSample","jdk.ObjectAllocationSample","jdk.OldObjectSample").contains(type))continue;
   events++;var frames=new ArrayList<String>();var trace=e.getStackTrace();if(trace!=null)for(var f:trace.getFrames())frames.add(f.getMethod().getType().getName()+"."+f.getMethod().getName());String cat=category(frames);
   if(type.equals("jdk.ExecutionSample")){var t=e.getThread("sampledThread");if(t!=null&&"main".equals(t.getJavaName())){add(cpu,cat,1);add(leaf,frames.stream().filter(s->s.startsWith("com.imd.cobolexplorer.")).findFirst().orElse("no-product-frame"),1);}}
   else if(type.equals("jdk.ObjectAllocationSample")){var t=e.getThread("eventThread");if(t!=null&&"main".equals(t.getJavaName())){long weight=e.getLong("weight");String cl=e.getClass("objectClass").getName();add(alloc,cat,weight);add(classes,cl,weight);if(cl.equals("com.imd.cobolexplorer.DependencyEnvironment$Node"))add(nodeOrigin,cat,weight);}}
   else {RecordedObject object=e.getValue("object");String cl=object.getClass("type").getName();add(old,cl,1);if(cl.equals("com.imd.cobolexplorer.DependencyEnvironment$Node"))add(oldOrigin,cat,1);}
  }}
  System.out.println("{\"events\":"+events+",\"executionSamplesMain\":"+cpu.values().stream().mapToLong(Long::longValue).sum()+",\"cpuCategories\":"+map(cpu)+",\"firstProductMethods\":"+map(leaf)+",\"estimatedAllocatedBytesMain\":"+alloc.values().stream().mapToLong(Long::longValue).sum()+",\"allocationCategories\":"+map(alloc)+",\"allocationClasses\":"+map(classes)+",\"environmentNodeAllocationOrigins\":"+map(nodeOrigin)+",\"oldObjectSamplesClasses\":"+map(old)+",\"environmentOldObjectOrigins\":"+map(oldOrigin)+"}");
 }
}
