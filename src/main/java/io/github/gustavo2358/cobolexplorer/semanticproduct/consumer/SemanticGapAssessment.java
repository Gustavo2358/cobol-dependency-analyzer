package io.github.gustavo2358.cobolexplorer.semanticproduct.consumer;

import io.github.gustavo2358.cobolexplorer.semanticproduct.*;
import java.util.*;
import static io.github.gustavo2358.cobolexplorer.semanticproduct.CobolSemanticProduct.*;

/** Diagnostic interpretation of published facts. Never changes semantic authority or readiness. */
public final class SemanticGapAssessment {
    private SemanticGapAssessment() { }
    public enum Status { SUPERSEDED, PARTIAL, OPEN }
    public record Evidence(String pointer, String authority, String description) { }
    public record Row(int gapIndex, String statement, GapScope scope, String code, String detail,
            Provenance provenance, Status status, String dimension, String rule,
            List<Evidence> evidence, String remaining) {
        public Row { evidence = List.copyOf(evidence); }
    }
    public record Counts(int raw, int superseded, int partial, int open, int pending) { }
    public record CodeCount(String code, Counts counts) { }
    public record UnitAssessment(UnitId unit, Counts counts, List<CodeCount> ranking, List<Row> gaps) {
        public UnitAssessment { ranking = List.copyOf(ranking); gaps = List.copyOf(gaps); }
    }
    public record Report(String version, String scope, Counts counts, List<CodeCount> ranking,
            List<UnitAssessment> units) {
        public Report { ranking = List.copyOf(ranking); units = List.copyOf(units); }
    }

    public static Report assess(List<CobolSemanticPort> products) {
        var units = products.stream().map(SemanticGapAssessment::assess).toList();
        var rows = units.stream().flatMap(u -> u.gaps().stream()).toList();
        return new Report("1.0.0", "SEMANTIC_PRODUCT_TOP_LEVEL_GAPS", counts(rows), ranking(rows), units);
    }

    public static UnitAssessment assess(CobolSemanticPort product) {
        var context = new Context(product);
        var rows = new ArrayList<Row>();
        for (int i = 0; i < product.gaps().size(); i++) rows.add(context.assess(i, product.gaps().get(i)));
        return new UnitAssessment(product.unit(), counts(rows), ranking(rows), rows);
    }

    private static Counts counts(List<Row> rows) {
        int superseded = 0, partial = 0;
        for (var row : rows) {
            if (row.status() == Status.SUPERSEDED) superseded++;
            if (row.status() == Status.PARTIAL) partial++;
        }
        return new Counts(rows.size(), superseded, partial, rows.size() - superseded - partial, rows.size() - superseded);
    }

    private static List<CodeCount> ranking(List<Row> rows) {
        var groups = new TreeMap<String, List<Row>>();
        for (var row : rows) groups.computeIfAbsent(row.code(), k -> new ArrayList<>()).add(row);
        return groups.entrySet().stream().map(e -> new CodeCount(e.getKey(), counts(e.getValue())))
                .sorted(Comparator.<CodeCount>comparingInt(c -> c.counts().pending()).reversed()
                        .thenComparing(CodeCount::code)).toList();
    }

    /** These restrictions belong to the retired isolated, linear paragraph profile. */
    private static final Set<String> LEGACY_PERFORM_PROFILE = Set.of(
            "PERFORM_ISOLATED_PRIMARY_NOT_PROVEN", "PERFORM_ISOLATED_PRIMARY_FLOW_NOT_PROVEN",
            "PERFORM_LINEAR_MOVE_BODY_NOT_PROVEN", "PERFORM_ORDINARY_INCOMING_NOT_EXCLUDED");
    private static final Set<String> PERFORM_CONTROL = Set.of("PERFORM_RANGE_CONTROL_NOT_PROVEN",
            "PERFORM_RESUME_NOT_PROVEN", "PERFORM_ORDERED_RANGE_NOT_PROVEN",
            "PERFORM_PROCEDURE_STRUCTURE_NOT_PROVEN", "PERFORM_ENDPOINT_NOT_UNIQUE_LOCAL_PARAGRAPH");

    private static final class Context {
        private final CobolSemanticPort product;
        private final Map<String, StatementFact> statements = new HashMap<>();
        private final Map<String, String> paths = new HashMap<>();
        private final Map<String, ControlTopology.Occurrence> occurrences = new HashMap<>();
        private final Map<String, ControlTopology.Region> regions = new HashMap<>();
        private final Map<String, ControlTopology.Outcome> outcomes = new HashMap<>();
        private final Map<String, ControlTopology.Binding> bindings = new HashMap<>();
        private final Map<String, ControlTopology.Proof> proofs = new HashMap<>();
        private final Map<String, List<Evidence>> assignments = new HashMap<>();
        private final Map<String, List<Evidence>> conditions = new HashMap<>();
        private String controlAuthority = "";

        Context(CobolSemanticPort product) {
            this.product = product;
            int effectIndex = 0;
            for (int i = 0; i < product.statements().size(); i++) {
                var s = product.statements().get(i);
                var id = "statement:" + s.header().id().localId();
                statements.put(id, s); paths.put(id, "/statements/" + i);
                if (s instanceof ObservedStatement o && o.effects().isPresent())
                    paths.put("effects/" + id, "/statementEffects/" + effectIndex++);
            }
            product.controlTopology().ifPresent(t -> {
                controlAuthority = t.authority();
                for (int i = 0; i < t.occurrences().size(); i++) {
                    var o = t.occurrences().get(i); occurrences.put(o.statement(), o);
                    paths.put("occurrence/" + o.statement(), "/controlTopology/occurrences/" + i);
                }
                for (int i = 0; i < t.regions().size(); i++) {
                    var r = t.regions().get(i); regions.put(r.id(), r);
                    paths.put("region/" + r.id(), "/controlTopology/regions/" + i);
                }
                t.outcomes().forEach(o -> outcomes.put(o.id(), o));
                for (int i = 0; i < t.bindings().size(); i++) {
                    var b = t.bindings().get(i); bindings.put(b.caller(), b);
                    paths.put("binding/" + b.caller(), "/controlTopology/bindings/" + i);
                }
                t.proofs().forEach(p -> proofs.put(p.id(), p));
            });
            product.nominalValues().ifPresent(n -> {
                for (int i = 0; i < n.assignments().size(); i++) {
                    var a = n.assignments().get(i);
                    assignments.computeIfAbsent(a.statement(), k -> new ArrayList<>()).add(new Evidence(
                            "/nominalValues/assignments/" + i, n.authority(), "Atribuição nominal de texto publicada."));
                }
                for (int i = 0; i < n.conditions().size(); i++) {
                    var c = n.conditions().get(i);
                    conditions.computeIfAbsent(c.statement(), k -> new ArrayList<>()).add(new Evidence(
                            "/nominalValues/conditions/" + i, n.authority(), "Condição nominal publicada."));
                }
            });
        }

        Row assess(int index, Gap gap) {
            String id = "statement:" + gap.statement().localId();
            var s = statements.get(id);
            // Never allow local identifiers from a different program unit to discharge a gap.
            if (!gap.statement().unit().equals(product.unit()) || s == null)
                return open(index, gap);
            String code = gap.code();
            if (code.equals("CONTAINMENT_NOT_PROJECTED") && gap.scope() == GapScope.STRUCTURE) {
                var o = occurrences.get(id);
                var r = o == null ? null : regions.get(o.region());
                if (r != null && r.members().contains(id) && positive(o.proofs()) && positive(r.proofs()))
                    return row(index, gap, Status.SUPERSEDED, "STRUCTURAL_OWNERSHIP", "TOPOLOGY_MEMBERSHIP",
                            List.of(control("occurrence/" + id), control("region/" + r.id())),
                            "Posição estrutural publicada. Esta regra não avalia controle, valores ou efeitos.");
            }
            if ((s instanceof PerformFact || s instanceof ProcedurePerformFact) && gap.scope() == GapScope.CAPABILITY && boundInvocation(id)) {
                if (LEGACY_PERFORM_PROFILE.contains(code))
                    return row(index, gap, Status.SUPERSEDED, "LEGACY_PERFORM_PROFILE", "COMPOSITION_REPLACES_ISOLATION",
                            List.of(control("binding/" + id), control("occurrence/" + id)),
                            "Restrição do perfil isolado substituída pela composição. Efeitos, predicados, input e recursão conservam seus diagnósticos próprios.");
                if (PERFORM_CONTROL.contains(code))
                    return row(index, gap, Status.PARTIAL, "PERFORM_CONTROL", "INVOCATION_PUBLISHED",
                            List.of(control("binding/" + id)),
                            "Invocação publicada; fechamento do corpo, retomada e efeitos exigem avaliação específica.");
            }
            if (s instanceof ObservedStatement observed && gap.scope() == GapScope.CAPABILITY
                    && Set.of("OBSERVED_STATEMENT_UNSUPPORTED", "OBSERVED_STATEMENT_PARTIAL").contains(code)
                    && observed.effects().isPresent()) {
                var e = observed.effects().orElseThrow();
                var evidence = new ArrayList<Evidence>();
                evidence.add(new Evidence(paths.get("effects/" + id), e.proof().name(), "Resumo de efeitos publicado."));
                if (e.proof() == EffectProof.NO_OP && closedLocalControl(id)) {
                    evidence.add(control("occurrence/" + id));
                    return row(index, gap, Status.SUPERSEDED, "STATEMENT_SUPPORT", "NO_OP_WITH_CONTROL", evidence,
                            "Comando sem efeito e controle local publicados. Outros gaps da ocorrência permanecem independentes.");
                }
                return row(index, gap, Status.PARTIAL, "STATEMENT_SUPPORT", "EFFECT_SUMMARY_PUBLISHED", evidence,
                        "Resumo de efeitos não encerra valores, ambiente, operandos desconhecidos ou controle.");
            }
            if (s instanceof CicsCommandFact cics && code.equals("CICS_COMMAND_EFFECTS_NOT_MODELED")
                    && gap.scope() == GapScope.CAPABILITY && cics.hostEffects().isPresent())
                return row(index, gap, Status.PARTIAL, "CICS_EFFECTS", "CICS_HOST_EFFECTS_PUBLISHED",
                        List.of(new Evidence(paths.get(id) + "/hostEffects", "CICS_HOST_EFFECTS", "Efeitos sobre operandos host publicados.")),
                        "Permanecem resultados de runtime, estado CICS, handlers e requisitos de storage do consumidor.");
            if (s instanceof MoveFact && gap.scope() == GapScope.CAPABILITY
                    && Set.of("MOVE_IDENTITY_NOT_PROVEN", "SCALAR_WHOLE_ITEM_NOT_PROVEN").contains(code)
                    && assignments.containsKey(id))
                return row(index, gap, Status.PARTIAL, "MOVE_STORAGE", "NOMINAL_ASSIGNMENT_PUBLISHED", assignments.get(id),
                        "Atribuição nominal não prova identidade física, alias, conversão nem todos os destinos do MOVE.");
            if (s instanceof IfFact condition) {
                boolean predicateGap = code.equals("CONDITION_SEMANTICS_NOT_AVAILABLE") && gap.scope() == GapScope.CONDITION_SEMANTICS;
                boolean profileGap = code.equals("IF_OUTSIDE_SIMPLE_PROFILE") && gap.scope() == GapScope.CAPABILITY;
                if ((predicateGap || profileGap) && condition.condition().textPredicate().isPresent())
                    return row(index, gap, predicateGap ? Status.SUPERSEDED : Status.PARTIAL, "CONDITION", "TEXT_PREDICATE_PUBLISHED",
                            List.of(new Evidence(paths.get(id) + "/condition/textPredicate", "TEXT_PREDICATE", "Árvore textual validada publicada.")),
                            predicateGap ? "Predicado textual publicado; os gaps de controle e braços do IF são independentes."
                                    : "Predicado publicado; conteúdo dos braços e conclusão do IF permanecem fora desta regra.");
                if ((predicateGap || profileGap) && conditions.containsKey(id))
                    return row(index, gap, Status.PARTIAL, "CONDITION", "NOMINAL_CONDITION_PUBLISHED", conditions.get(id),
                            "Condição nominal não encerra a prova física nem o controle e os efeitos dos braços.");
            }
            return open(index, gap);
        }

        private boolean boundInvocation(String id) {
            var b = bindings.get(id);
            var o = occurrences.get(id);
            return b != null && o != null && regions.containsKey(b.region()) && positive(b.proofs())
                    && positive(o.proofs()) && b.resume().kind() != ControlTopology.TargetKind.UNKNOWN_LOCAL
                    && positive(b.resume().proofs()) && o.outcomes().stream().map(outcomes::get).anyMatch(out -> out != null
                        && out.kind() == ControlTopology.OutcomeKind.LOCAL_INVOKE && out.binding().equals(b.id())
                        && positive(out.proofs()));
        }
        private boolean closedLocalControl(String id) {
            var o = occurrences.get(id);
            return o != null && positive(o.proofs()) && o.outcomes().stream().map(outcomes::get).allMatch(out -> out != null
                    && out.kind() != ControlTopology.OutcomeKind.UNKNOWN_LOCAL
                    && out.target().kind() != ControlTopology.TargetKind.UNKNOWN_LOCAL
                    && positive(out.proofs()) && positive(out.target().proofs()));
        }
        private boolean positive(List<String> ids) {
            if (ids.isEmpty()) return false;
            var seen = new HashSet<String>();
            var pending = new ArrayDeque<>(ids);
            while (!pending.isEmpty()) {
                var id = pending.removeFirst();
                if (!seen.add(id)) continue;
                var proof = proofs.get(id);
                if (proof == null || proof.kind() == ControlTopology.ProofKind.PARTIAL_UNKNOWN
                        || proof.kind() == ControlTopology.ProofKind.CONTROL_POSSIBILITY) return false;
                pending.addAll(proof.dependencies());
            }
            return true;
        }
        private Evidence control(String key) {
            return new Evidence(Objects.requireNonNull(paths.get(key)), controlAuthority, "Prova estrutural de controle publicada.");
        }
    }

    private static Row row(int index, Gap g, Status status, String dimension, String rule, List<Evidence> evidence, String remaining) {
        return new Row(index, "statement:" + g.statement().localId(), g.scope(), g.code(), g.detail(), g.provenance(),
                status, dimension, rule, evidence, remaining);
    }
    private static Row open(int index, Gap gap) {
        return row(index, gap, Status.OPEN, gap.scope().name(), "NO_MATCHING_RULE", List.of(),
                "Sem prova substituta admitida nesta avaliação. O diagnóstico permanece pendente de investigação.");
    }
}
