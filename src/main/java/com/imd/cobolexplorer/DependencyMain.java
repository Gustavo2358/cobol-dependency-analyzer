package com.imd.cobolexplorer;

import java.nio.file.*;
import java.nio.charset.Charset;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Atomic minimal JSON output. Diagnostics/metrics never enter dependencies.json. */
public final class DependencyMain {
    public static void main(String[] args) {System.exit(run(args));}
    static int run(String[] args) {
        try {
            Path source=null,output=null,inventory=null,metrics=null;var copies=new ArrayList<Path>();
            var format=SourceNormalizer.SourceFormat.FIXED;var charset=Charset.forName("UTF-8");String parser="direct-ast-lab";long maxWork=1_000_000;
            for(int i=0;i<args.length;i++) {
                if(args[i].equals("--help")){System.out.println("java -jar cobol-dependency-analyzer.jar --source FILE_OR_DIR --output dependencies.json [--copy-dir DIR] [--source-format fixed] [--source-inventory FILE] [--charset UTF-8] [--parser direct|antlr] [--max-work N] [--metrics FILE]");return 0;}
                if(i+1==args.length)throw new IllegalArgumentException("Missing value for "+args[i]);String option=args[i++],value=args[i];
                switch(option) {
                    case "--source"->source=Path.of(value);case "--output"->output=Path.of(value);case "--copy-dir"->copies.add(Path.of(value));
                    case "--source-inventory"->inventory=Path.of(value);case "--metrics"->metrics=Path.of(value);case "--charset"->charset=Charset.forName(value);
                    case "--max-work"->maxWork=Long.parseLong(value);
                    case "--source-format"->format=switch(value){case "fixed"->SourceNormalizer.SourceFormat.FIXED;default->throw new IllegalArgumentException("Only fixed source format is currently supported");};
                    case "--parser"->parser=switch(value){case "direct"->"direct-ast-lab";case "antlr"->"antlr";default->throw new IllegalArgumentException("Expected direct or antlr");};
                    default->throw new IllegalArgumentException("Unknown option "+option);
                }
            }
            if(source==null||output==null)throw new IllegalArgumentException("--source and --output are required");
            var options=new DependencyAnalyzer.Options(copies,format,charset,inventory,parser,maxWork);
            var files=new ArrayList<Path>();
            if(Files.isDirectory(source))try(var stream=Files.walk(source)){stream.filter(Files::isRegularFile).filter(p->p.getFileName().toString().toLowerCase(Locale.ROOT).matches(".*\\.(cbl|cob|cobol)")).sorted().forEach(files::add);}else files.add(source);
            if(files.isEmpty())throw new IllegalArgumentException("No COBOL sources");
            var mapper=new ObjectMapper();var destination=output.toAbsolutePath().normalize();
            if(destination.equals(source.toAbsolutePath().normalize())||metrics!=null&&(destination.equals(metrics.toAbsolutePath().normalize())||source.toAbsolutePath().normalize().equals(metrics.toAbsolutePath().normalize())))
                throw new IllegalArgumentException("Source, dependencies and metrics must use distinct paths");
            Files.createDirectories(destination.getParent());
            Path temporary=Files.createTempFile(destination.getParent(),"dependencies-",".tmp");
            boolean partial=false;var timings=new ArrayList<String>();
            long started=System.nanoTime();
            try {
                try(var json=mapper.getFactory().createGenerator(Files.newOutputStream(temporary))) {
                    json.setCodec(mapper);
                    var single=Files.isDirectory(source)?null:new DependencyAnalyzer().analyze(files.get(0),options);
                    boolean array=single==null||single.programs().size()!=1;if(array)json.writeStartArray();
                    long publicationNanos=0;
                    for(Path file:files) {
                        var result=single==null?new DependencyAnalyzer().analyze(file,options):single;
                        for(String d:result.diagnostics()){System.err.println(file+": "+d);partial=true;}
                        for(var program:result.programs()) {
                            long writing=System.nanoTime();mapper.writeValue(json,program);json.flush();publicationNanos+=System.nanoTime()-writing;
                        }
                        timings.add(mapper.writeValueAsString(Map.of("source",file.toString(),"metrics",result.metrics())));
                    }
                    if(array)json.writeEndArray();
                    json.flush();
                    if(metrics!=null)timings.add(mapper.writeValueAsString(Map.of("publicationNanos",publicationNanos)));
                }
                // Sidecar errors are global failures too: validate/write it before
                // committing the product, so the previous dependencies survive.
                if(metrics!=null)Files.write(metrics,timings);
                long publish=System.nanoTime();Files.move(temporary,destination,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
                if(metrics!=null)System.err.println("totalMs="+(System.nanoTime()-started)/1_000_000+" atomicPublishNanos="+(System.nanoTime()-publish));
            }finally{Files.deleteIfExists(temporary);}
            return partial?1:0;
        }catch(Exception|OutOfMemoryError|StackOverflowError error) {System.err.println("ANALYSIS_FAILED: "+error.getClass().getSimpleName()+": "+error.getMessage());return 2;}
    }
}
