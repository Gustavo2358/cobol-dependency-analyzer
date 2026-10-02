package io.github.gustavo2358.cobolexplorer;

import io.github.gustavo2358.cobolexplorer.antlr.CobolParser;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.ParseTree;
import java.util.*;
import static io.github.gustavo2358.cobolexplorer.antlr.CobolParser.*;

/** Experimental recursive-descent DATA parser. It emits typed declaration drafts,
 * then immutable Ast nodes, never ANTLR contexts for the accepted DATA body.
 * The flat origin ledger preserves the existing presentation/provenance contract;
 * it is not used to discover semantic structure. Admission is transactional. */
public final class DirectDataParser {
    private DirectDataParser() { }

    public static final class Session {
        private final IdentityHashMap<ParseTree, Result> results = new IdentityHashMap<>();
        private final Map<String, Integer> reasons = new TreeMap<>();
        private int fallbacks;
        public void read(CobolParser parser, ParserRuleContext division) {
            TokenStream input = parser.getInputStream();
            int checkpoint = input.index();
            if (parser.getNumberOfSyntaxErrors() != 0) { fallback("prior syntax error"); return; }
            try {
                Result result = new Reader(input).read();
                if (!result.sections.isEmpty()) results.put(division, result);
            } catch (Unsupported inputOutsideSlice) {
                input.seek(checkpoint);
                fallback(inputOutsideSlice.getMessage());
            }
        }
        private void fallback(String reason) { fallbacks++; reasons.merge(reason, 1, Integer::sum); }
        Result result(ParseTree context) { return results.get(context); }
        public int acceptedDivisions() { return results.size(); }
        public int fallbackDivisions() { return fallbacks; }
        public int entries() { return results.values().stream().mapToInt(r -> r.entryCount).sum(); }
        public Map<String, Integer> fallbackReasons() { return Map.copyOf(reasons); }
    }

    /** One preorder syntax-origin row, with no children or ANTLR context. */
    static final class Event {
        final String rule;
        final Token start;
        final int parent, depth;
        Token stop;
        int children, size;
        Event(String rule, Token start, int parent, int depth) {
            this.rule = rule; this.start = start; this.parent = parent; this.depth = depth;
        }
        String rule() { return rule; }
        Token start() { return start; }
        Token stop() { return stop; }
        int parent() { return parent; }
        int depth() { return depth; }
        int children() { return children; }
    }
    record Coverage(Ast.Node node, String writtenText) { }
    record Built(List<Ast.Node> sections, int nextId, List<Coverage> coverage,
                 List<SemanticCoverage.Diagnostic> diagnostics) { }
    private record Section(int origin, Ast.DataSectionKind kind, String name, List<Entry> entries) { }
    private record Entry(int origin, String level, String name, boolean filler, List<Clause> clauses) { }
    private sealed interface Clause permits Picture, Usage, Value, Redefines, Preserved { int origin(); }
    private record Picture(int origin, int pictureOrigin, String spelling) implements Clause { }
    private record Usage(int origin, boolean display) implements Clause { }
    private record Value(int origin, List<Integer> intervals, String basicToken) implements Clause { }
    private record Redefines(int origin, int nameOrigin, String name) implements Clause { }
    private record Preserved(int origin, boolean external, boolean global) implements Clause { }

    static final class Result {
        final List<Event> events;
        final List<Section> sections;
        final int entryCount;
        Result(List<Event> events, List<Section> sections, int entryCount) {
            this.events = List.copyOf(events); this.sections = List.copyOf(sections); this.entryCount = entryCount;
        }
        List<Event> events() { return events; }
        int sectionCount() { return sections.size(); }
        Built build(int firstAstId, int firstOriginId, SourceMap sourceMap) {
            return new Materializer(this, firstAstId, firstOriginId, sourceMap).build();
        }
    }

    private static final class Unsupported extends RuntimeException {
        Unsupported(String reason) { super(reason, null, false, false); }
    }
    private static final class Reader {
        final TokenStream input;
        final List<Event> events = new ArrayList<>();
        final Deque<Integer> scopes = new ArrayDeque<>();
        int entries;
        Reader(TokenStream input) { this.input = input; }
        Result read() {
            var sections = new ArrayList<Section>();
            while (is(WORKING_STORAGE, LINKAGE, LOCAL_STORAGE)) sections.add(section());
            // All other section forms, nested constructs and uncertain boundaries
            // return to the unmodified grammar at the original token position.
            if (!is(PROCEDURE, END, IDENTIFICATION, ID, Token.EOF)) reject("unsupported DATA boundary");
            return new Result(events, sections, entries);
        }
        Section section() {
            int wrapper = enter("dataDivisionSection");
            int type = la();
            String rule = type == WORKING_STORAGE ? "workingStorageSection" : type == LINKAGE ? "linkageSection" : "localStorageSection";
            int origin = enter(rule);
            take(); expect(SECTION); expect(DOT_FS);
            var declarations = new ArrayList<Entry>();
            while (is(INTEGERLITERAL, LEVEL_NUMBER_77, LEVEL_NUMBER_88)) declarations.add(entry());
            close(origin); close(wrapper);
            return new Section(origin, type == WORKING_STORAGE ? Ast.DataSectionKind.WORKING_STORAGE
                    : type == LINKAGE ? Ast.DataSectionKind.LINKAGE : Ast.DataSectionKind.LOCAL_STORAGE,
                    type == WORKING_STORAGE ? "Working Storage Section" : type == LINKAGE ? "Linkage Section" : "Local Storage Section", List.copyOf(declarations));
        }
        Entry entry() {
            int wrapper = enter("dataDescriptionEntry");
            boolean condition = la() == LEVEL_NUMBER_88;
            int origin = enter(condition ? "dataDescriptionEntryFormat3" : "dataDescriptionEntryFormat1");
            String level = take().getText();
            int numericLevel;
            try { numericLevel = Integer.parseInt(level); }
            catch (NumberFormatException outsideRange) { throw new Unsupported("level outside direct range"); }
            if (!level.chars().allMatch(c -> c >= '0' && c <= '9')
                    || !(numericLevel >= 1 && numericLevel <= 49 || level.equals("77") || level.equals("88")))
                reject("level outside direct range");
            boolean filler = la() == FILLER;
            String name;
            if (filler && !condition) name = take().getText();
            else { if (la() != IDENTIFIER) reject("ambiguous or absent data name"); name = name(condition ? "conditionName" : "dataName").getText(); }
            var clauses = new ArrayList<Clause>();
            if (condition) { if (!is(VALUE, VALUES)) reject("implicit condition VALUE"); clauses.add(value()); }
            else while (la() != DOT_FS) {
                if (is(PIC, PICTURE)) clauses.add(picture());
                else if (is(USAGE) || usageToken(la())) clauses.add(usage());
                else if (is(VALUE, VALUES)) clauses.add(value());
                else if (is(REDEFINES)) clauses.add(redefines());
                else if (is(EXTERNAL, GLOBAL, IS, JUST, JUSTIFIED, SYNC, SYNCHRONIZED, BLANK)) clauses.add(preserved());
                else reject("unsupported data clause: " + VOCABULARY.getSymbolicName(la()));
            }
            expect(DOT_FS); close(origin); close(wrapper); entries++;
            return new Entry(origin, level, filler ? "FILLER" : name, filler, List.copyOf(clauses));
        }
        Picture picture() {
            int origin = enter("dataPictureClause"); take(); optional(IS);
            int body = enter("pictureString"); var spelling = new StringBuilder(); int count = 0;
            while (pictureToken(la())) {
                int chars = enter("pictureChars");
                if (integerToken(la())) { int integer = enter("integerLiteral"); spelling.append(take().getText()); close(integer); }
                else spelling.append(take().getText());
                close(chars); count++;
            }
            if (count == 0) reject("empty PIC"); close(body); close(origin);
            return new Picture(origin, body, spelling.toString());
        }
        Usage usage() {
            int origin = enter("dataUsageClause");
            if (optional(USAGE)) optional(IS);
            int type = la(); if (!usageToken(type)) reject("unsupported USAGE"); take();
            if (type == BINARY && is(TRUNCATED, EXTENDED)) take();
            close(origin); return new Usage(origin, type == DISPLAY);
        }
        Value value() {
            int origin = enter("dataValueClause"); take(); if (is(IS, ARE)) take();
            var intervals = new ArrayList<Integer>(); String single = null;
            do {
                if (!intervals.isEmpty()) optional(COMMACHAR);
                int interval = enter("dataValueInterval"); int from = enter("dataValueIntervalFrom");
                String basic = la() == NONNUMERICLITERAL ? input.LT(1).getText() : null;
                literal(); close(from);
                if (is(THROUGH, THRU)) { int to = enter("dataValueIntervalTo"); take(); literal(); close(to); basic = null; }
                close(interval); intervals.add(interval); single = intervals.size() == 1 ? basic : null;
            } while (is(COMMACHAR) || literalToken(la()));
            // cobolWord overlaps many clause starters; leave these contexts to LL.
            if (la() != DOT_FS) reject("VALUE followed by another clause");
            close(origin); return new Value(origin, List.copyOf(intervals), single);
        }
        void literal() {
            int literal = enter("literal"); int type = la();
            if (type == NONNUMERICLITERAL) take();
            else if (figurative(type)) { int f = enter("figurativeConstant"); take(); close(f); }
            else if (integerToken(type) || type == NUMERICLITERAL) {
                int n = enter("numericLiteral");
                if (integerToken(type)) { int i = enter("integerLiteral"); take(); close(i); } else take(); close(n);
            } else if (is(TRUE, FALSE)) { int b = enter("booleanLiteral"); take(); close(b); }
            else reject("unsupported VALUE operand");
            close(literal);
        }
        Redefines redefines() {
            int origin = enter("dataRedefinesClause"); take();
            if (la() != IDENTIFIER) reject("ambiguous REDEFINES name");
            int nameOrigin = events.size(); String spelling = name("dataName").getText(); close(origin);
            return new Redefines(origin, nameOrigin, spelling);
        }
        Preserved preserved() {
            int token = la(), next = input.LA(2);
            String rule = token == GLOBAL || token == IS && next == GLOBAL ? "dataGlobalClause"
                    : token == EXTERNAL || token == IS && next == EXTERNAL ? "dataExternalClause"
                    : is(JUST, JUSTIFIED) ? "dataJustifiedClause" : is(SYNC, SYNCHRONIZED) ? "dataSynchronizedClause"
                    : token == BLANK ? "dataBlankWhenZeroClause" : null;
            if (rule == null) reject("unsupported IS clause");
            int origin = enter(rule);
            switch (rule) {
                case "dataGlobalClause" -> { optional(IS); expect(GLOBAL); }
                case "dataExternalClause" -> { optional(IS); expect(EXTERNAL); if (la() == BY) reject("EXTERNAL BY"); }
                case "dataJustifiedClause" -> { take(); optional(RIGHT); }
                case "dataSynchronizedClause" -> { take(); if (is(LEFT, RIGHT)) take(); }
                case "dataBlankWhenZeroClause" -> { take(); optional(WHEN); if (!is(ZERO, ZEROS, ZEROES)) reject("BLANK requires ZERO"); take(); }
                default -> throw new IllegalStateException(rule);
            }
            close(origin); return new Preserved(origin,
                    token == EXTERNAL || token == IS && next == EXTERNAL,
                    token == GLOBAL || token == IS && next == GLOBAL);
        }
        Token name(String rule) { int n = enter(rule), word = enter("cobolWord"); Token token = take(); close(word); close(n); return token; }
        int enter(String rule) {
            int id = events.size(), parent = scopes.isEmpty() ? -1 : scopes.peek();
            if (parent >= 0) events.get(parent).children++;
            events.add(new Event(rule, input.LT(1), parent, scopes.size())); scopes.push(id); return id;
        }
        void close(int id) {
            if (scopes.pop() != id) throw new IllegalStateException("unbalanced origin ledger");
            var event = events.get(id); event.stop = input.LT(-1); event.size = events.size() - id;
        }
        Token take() {
            if (la() == Token.EOF) reject("unexpected EOF"); Token token = input.LT(1);
            int parent = scopes.peek(); events.get(parent).children++;
            Event event = new Event(null, token, parent, scopes.size()); event.stop = token; event.size = 1; events.add(event);
            input.consume(); return token;
        }
        void expect(int type) { if (la() != type) reject("expected " + VOCABULARY.getSymbolicName(type)); take(); }
        boolean optional(int type) { if (la() != type) return false; take(); return true; }
        int la() { return input.LA(1); }
        boolean is(int... types) { for (int type : types) if (la() == type) return true; return false; }
        void reject(String reason) { throw new Unsupported(reason); }
    }
    private static boolean integerToken(int t) { return t == INTEGERLITERAL || t == LEVEL_NUMBER_66 || t == LEVEL_NUMBER_77 || t == LEVEL_NUMBER_88; }
    private static boolean pictureToken(int t) {
        return integerToken(t) || switch (t) {
            case DOLLARCHAR, IDENTIFIER, NUMERICLITERAL, SLASHCHAR, COMMACHAR, DOT, COLONCHAR, ASTERISKCHAR,
                    DOUBLEASTERISKCHAR, LPARENCHAR, RPARENCHAR, PLUSCHAR, MINUSCHAR, LESSTHANCHAR, MORETHANCHAR -> true;
            default -> false;
        };
    }
    private static boolean figurative(int t) {
        return switch (t) { case HIGH_VALUE, HIGH_VALUES, LOW_VALUE, LOW_VALUES, NULL, NULLS, QUOTE, QUOTES, SPACE, SPACES, ZERO, ZEROS, ZEROES -> true; default -> false; };
    }
    private static boolean literalToken(int t) { return integerToken(t) || figurative(t) || t == NONNUMERICLITERAL || t == NUMERICLITERAL || t == TRUE || t == FALSE; }
    private static boolean usageToken(int t) {
        return switch (t) {
            case BINARY, BIT, COMP, COMP_1, COMP_2, COMP_3, COMP_4, COMP_5, COMPUTATIONAL, COMPUTATIONAL_1,
                    COMPUTATIONAL_2, COMPUTATIONAL_3, COMPUTATIONAL_4, COMPUTATIONAL_5, CONTROL_POINT, DATE, DISPLAY,
                    DISPLAY_1, DOUBLE, EVENT, FUNCTION_POINTER, INDEX, KANJI, LOCK, NATIONAL, PACKED_DECIMAL,
                    POINTER, PROCEDURE_POINTER, REAL, SQL, TASK -> true;
            default -> false;
        };
    }

    private static final class Materializer {
        final Result result; final int originBase; final SourceMap sourceMap; final UnicodeText source;
        final List<Coverage> coverage = new ArrayList<>();
        final List<SemanticCoverage.Diagnostic> diagnostics = new ArrayList<>();
        int nextId;
        Materializer(Result result, int firstId, int originBase, SourceMap sourceMap) {
            this.result = result; nextId = firstId; this.originBase = originBase; this.sourceMap = sourceMap; source = new UnicodeText(sourceMap.text());
        }
        Built build() {
            var sections = new ArrayList<Ast.Node>();
            for (Section section : result.sections) {
                Ast.Meta meta = meta(section.origin()); var roots = new ArrayList<DataDraft>();
                Deque<Map.Entry<Integer, DataDraft>> stack = new ArrayDeque<>(); DataDraft previous = null;
                for (Entry entry : section.entries()) {
                    Ast.DataEntry node = entry(entry); int level = Integer.parseInt(entry.level()); var draft = new DataDraft(node);
                    if (level == 88 && previous != null) { previous.children.add(draft); continue; }
                    while (!stack.isEmpty() && stack.peek().getKey() >= level) stack.pop();
                    if (stack.isEmpty() || level == 77) roots.add(draft); else stack.peek().getValue().children.add(draft);
                    if (level >= 1 && level <= 49) stack.push(Map.entry(level, draft)); previous = draft;
                }
                sections.add(new Ast.Section(meta, section.name(), section.kind(), roots.stream().map(this::freeze).map(Ast.Node.class::cast).toList()));
            }
            return new Built(List.copyOf(sections), nextId, List.copyOf(coverage), List.copyOf(diagnostics));
        }
        private static final class DataDraft { final Ast.DataEntry entry; final List<DataDraft> children = new ArrayList<>(); DataDraft(Ast.DataEntry entry) { this.entry = entry; } }
        Ast.DataEntry freeze(DataDraft draft) {
            var e = draft.entry; return new Ast.DataEntry(e.meta(), e.level(), e.levelKind(), e.name(), e.filler(), e.visibility(), e.declaration(), e.clauses(), draft.children.stream().map(this::freeze).toList());
        }
        Ast.DataEntry entry(Entry entry) {
            Ast.Meta meta = meta(entry.origin()); var clauses = new ArrayList<Ast.DataClause>();
            boolean external = false, global = false;
            for (Clause draft : entry.clauses()) {
                clauses.add(clause(draft));
                if (draft instanceof Preserved preserved) {
                    external |= preserved.external(); global |= preserved.global();
                }
            }
            var visibility = external && global ? Ast.DeclarationVisibility.CONFLICTING : external ? Ast.DeclarationVisibility.EXTERNAL : global ? Ast.DeclarationVisibility.GLOBAL : Ast.DeclarationVisibility.LOCAL;
            if (external && global) diagnostics.add(new SemanticCoverage.Diagnostic("CONFLICTING_DECLARATION_VISIBILITY", "Declaration contains both GLOBAL and EXTERNAL visibility", meta));
            var kind = entry.level().equals("88") ? Ast.DataLevelKind.CONDITION_88 : entry.level().equals("77") ? Ast.DataLevelKind.STANDALONE_77 : Ast.DataLevelKind.GROUP_OR_ELEMENTARY;
            var node = new Ast.DataEntry(meta, entry.level(), kind, entry.name(), entry.filler(), visibility, compact(text(entry.origin())), clauses, List.of());
            coverage.add(new Coverage(node, text(entry.origin()))); return node;
        }
        Ast.DataClause clause(Clause draft) {
            Ast.Meta meta = meta(draft.origin()); String text = text(draft.origin()).strip(); Ast.DataClause node;
            if (draft instanceof Picture p) node = new Ast.PictureClause(meta, text(p.pictureOrigin()).strip(), text,
                    AstBuilder.elementaryTextExtent(p.spelling()), AstBuilder.elementaryIntegerDigits(p.spelling()));
            else if (draft instanceof Usage u) node = new Ast.UsageClause(meta, text.replaceFirst("(?i)^USAGE\\s+(IS\\s+)?", ""), text, u.display());
            else if (draft instanceof Value v) node = new Ast.ValueClause(meta, v.intervals().stream().map(this::text).map(String::strip).toList(), text,
                    v.basicToken() == null ? Optional.empty() : AstBuilder.basicLogicalTextToken(v.basicToken()));
            else if (draft instanceof Redefines r) node = new Ast.RedefinesClause(meta, new Ast.DataReference(meta(r.nameOrigin()), r.name(), text(r.nameOrigin()).strip(), List.of(), List.of(), null, Ast.ReferenceUnderstanding.STRUCTURED), text);
            else node = new Ast.PreservedDataClause(meta, result.events.get(draft.origin()).rule, text, List.of());
            coverage.add(new Coverage(node, text(draft.origin()))); return node;
        }
        Ast.Meta meta(int origin) {
            Event event = result.events.get(origin); Token start = event.start, stop = event.stop;
            var span = new Ast.SourceSpan(start.getLine(), start.getCharPositionInLine(), stop.getLine(), stop.getCharPositionInLine() + Math.max(0, stop.getText().codePointCount(0, stop.getText().length()) - 1), start.getTokenIndex(), stop.getTokenIndex());
            int begin = Math.max(0, start.getStartIndex()), end = Math.min(source.length(), stop.getStopIndex() + 1);
            return new Ast.Meta(nextId++, span, new Ast.ParseTreeOrigin(originBase + origin, event.rule, event.size), sourceMap.provenance(begin, end), sourceMap.syntheticModel(begin, end));
        }
        String text(int origin) { Event e = result.events.get(origin); return source.substring(Math.max(0, e.start.getStartIndex()), Math.min(source.length(), e.stop.getStopIndex() + 1)); }
        String compact(String text) { return text.replaceAll("\\s+", " ").trim(); }
    }
}
