package com.imd.cobolexplorer;
/** Closed SQL syntax proves possible normal completion, never database success or output values. */
final class SqlNormalCompletion {
    static boolean proved(Ast.Statement statement) {
        return statement instanceof Ast.EmbeddedLanguageStatement s
            && s.language()==Ast.EmbeddedLanguage.SQL && SqlCommandSyntax.parse(s.rawText()).isPresent();
    }
    static boolean selectInto(String raw) {
        return SqlCommandSyntax.parse(raw).filter(c->c.kind()==SqlCommandSyntax.Kind.SELECT_INTO).isPresent();
    }
}
