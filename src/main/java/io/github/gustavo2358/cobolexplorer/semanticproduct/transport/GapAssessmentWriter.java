package io.github.gustavo2358.cobolexplorer.semanticproduct.transport;

import com.fasterxml.jackson.databind.json.JsonMapper;
import io.github.gustavo2358.cobolexplorer.semanticproduct.consumer.SemanticGapAssessment.Report;
import io.github.gustavo2358.cobolexplorer.transport.JsonFiles;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** Separate diagnostic artifact: no field or version change in the Semantic Product. */
public final class GapAssessmentWriter {
    private GapAssessmentWriter() { }
    public static void write(Report report, Path json, Path javascript) throws IOException {
        var bytes = new JsonMapper().writerWithDefaultPrettyPrinter().writeValueAsBytes(report);
        try (var out = JsonFiles.output(Files.newOutputStream(json), json)) { out.write(bytes); }
        Files.writeString(javascript, "window.SEMANTIC_GAP_ASSESSMENT=" + new String(bytes, StandardCharsets.UTF_8) + ";\n", StandardCharsets.UTF_8);
    }
}
