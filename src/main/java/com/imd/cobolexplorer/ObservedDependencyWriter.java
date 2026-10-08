package com.imd.cobolexplorer;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Separate observed-source product; absence of entry never becomes zero dependencies. */
public final class ObservedDependencyWriter {
    private ObservedDependencyWriter() { }
    public static void write(ObservedDependencyInventory inventory,Path destination) throws IOException {
        var json=JsonMapper.builder().enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY).build();
        try(var output=com.imd.cobolexplorer.transport.JsonFiles.output(Files.newOutputStream(destination),destination)) { json.writeValue(output,inventory); }
    }
}
