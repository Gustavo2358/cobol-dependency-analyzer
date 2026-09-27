package io.github.gustavo2358.cobolexplorer;

import java.util.*;
import java.util.stream.Collectors;

/** Versioned CICS TS names, deliberately without values, pictures or storage claims.
 * Authority: https://www.ibm.com/docs/en/cics-ts/6.x?topic=reference-bms-constants
 * These are analysis models, not compiler/runtime replacements for IBM members. */
final class NominalCopybooks {
    private NominalCopybooks() { }
    private static final Map<String,List<String>> MEMBERS = catalogue();

    private static Map<String,List<String>> catalogue() {
        var aid = new ArrayList<>(List.of("DFHENTER", "DFHCLEAR"));
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
        var names=MEMBERS.get(name.toUpperCase(Locale.ROOT));
        if (names==null) return Optional.empty();
        String source=names.stream().map(n -> "       01 " + n + ".\n").collect(Collectors.joining());
        return Optional.of(SourceNormalizer.normalize(source, artifact(name), SourceNormalizer.SourceFormat.FIXED).sourceMap());
    }
    static String artifact(String name) { return "model:ibm-cics-ts/nominal-v1/"+name.toUpperCase(Locale.ROOT); }
}
