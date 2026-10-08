package com.imd.cobolexplorer;

import java.util.*;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

/** Documented IBM analysis declarations, with shape but no authoritative initial values.
 * Authority: https://www.ibm.com/docs/en/cics-ts/6.x?topic=reference-bms-constants
 * CICS follows the IBM CICS Primer; MQ/Db2 authority is in docs/work/carddemo-ibm-copybooks.md.
 * These are analysis models, not compiler/runtime replacements for IBM members. */
final class NominalCopybooks {
    private NominalCopybooks() { }
    private static final Map<String,List<String>> MEMBERS = catalogue();
    private static final Map<String,String> RESOURCE_PROFILES = Map.of(
        "CMQGMOV", "ibm-mq/9.4-structural-v1", "CMQMDV", "ibm-mq/9.4-structural-v1",
        "CMQODV", "ibm-mq/9.4-structural-v1", "CMQPMOV", "ibm-mq/9.4-structural-v1",
        "CMQTML", "ibm-mq/9.4-structural-v1", "CMQV", "ibm-mq/9.4-structural-v1",
        "SQLCA", "ibm-db2-zos/13-structural-v1");
    private static final Set<String> SQL_INCLUDE_MEMBERS = Set.of("SQLCA");
    // Each inclusion form has its own catalogue authority.
    static Optional<SourceMap> resolveSqlInclude(String name) {
        String canonical=name.toUpperCase(Locale.ROOT);
        return SQL_INCLUDE_MEMBERS.contains(canonical) ? load(canonical) : Optional.empty();
    }
    static Set<String> members() {
        var names=new TreeSet<>(MEMBERS.keySet());names.addAll(RESOURCE_PROFILES.keySet());
        return Collections.unmodifiableSet(names);
    }

    private static Map<String,List<String>> catalogue() {
        var aid = new ArrayList<>(List.of("DFHNULL", "DFHENTER", "DFHCLEAR"));
        for (int n=1;n<=3;n++) aid.add("DFHPA"+n);
        for (int n=1;n<=24;n++) aid.add("DFHPF"+n);
        aid.addAll(List.of("DFHOPID", "DFHMSRE", "DFHTRIG", "DFHPEN", "DFHCLRP", "DFHSTRF"));
        var bms = List.of(("DFHBMPEM DFHBMPNL DFHBMPFF DFHBMPCR "
            + "DFHBMASK DFHBMUNP DFHBMUNN DFHBMPRO DFHBMBRY DFHBMDAR "
            + "DFHBMFSE DFHBMPRF DFHBMASF DFHBMASB DFHBMPSO DFHBMPSI "
            + "DFHBMEOF DFHBMCUR DFHBMEC DFHBMFLG DFHBMDET "
            + "DFHSA DFHERROR DFHCOLOR DFHPS DFHHLT DFH3270 DFHVAL "
            + "DFHOUTLN DFHBKTRN DFHALL DFHDFT DFHDFCOL "
            + "DFHBLUE DFHRED DFHPINK DFHGREEN DFHTURQ DFHYELLO DFHNEUTR "
            + "DFHBASE DFHDFHI DFHBLINK DFHREVRS DFHUNDLN "
            + "DFHMFIL DFHMENT DFHMFE DFHMT DFHMFT DFHMET DFHMFET "
            + "DFHUNNOD DFHUNIMD DFHUNNUM DFHUNNUB DFHUNINT DFHUNNON "
            + "DFHPROTI DFHPROTN DFHDFFR DFHUNDER DFHRIGHT DFHOVER DFHLEFT DFHBOX "
            + "DFHSOSI DFHTRANS DFHOPAQ").split(" "));
        return Map.of("DFHAID", List.copyOf(aid), "DFHBMSCA", bms);
    }

    static Optional<SourceMap> resolve(String name) {
        String canonical=name.toUpperCase(Locale.ROOT);
        return SQL_INCLUDE_MEMBERS.contains(canonical) ? Optional.empty() : load(canonical);
    }

    private static Optional<SourceMap> load(String name) {
        if (RESOURCE_PROFILES.containsKey(name)) {
            String resource="/synthetic-copybooks/"+name+".cpy";
            try (var input=NominalCopybooks.class.getResourceAsStream(resource)) {
                if(input==null)throw new IllegalStateException("Missing catalogue resource: "+resource);
                String source=new String(input.readAllBytes(),StandardCharsets.UTF_8);
                return Optional.of(SourceNormalizer.normalize(source,artifact(name),SourceNormalizer.SourceFormat.FIXED).sourceMap());
            } catch(IOException failure) { throw new UncheckedIOException(failure); }
        }
        var names=MEMBERS.get(name);
        if (names==null) return Optional.empty();
        String source="       01 " + name.toUpperCase(Locale.ROOT) + ".\n"
                + names.stream().map(n -> "          02 " + n + " PIC X.\n"
                    + (n.equals("DFHBMFLG") ? "             88 DFHERASE VALUES X'80' X'82'.\n"
                        + "             88 DFHCURSR VALUES X'02' X'82'.\n" : ""))
                    .collect(Collectors.joining());
        return Optional.of(SourceNormalizer.normalize(source, artifact(name), SourceNormalizer.SourceFormat.FIXED).sourceMap());
    }
    static String artifact(String name) {
        String canonical=name.toUpperCase(Locale.ROOT);
        return "model:"+RESOURCE_PROFILES.getOrDefault(canonical,"ibm-cics/structural-v2")+"/"+canonical;
    }
}
