package net.hollowcube.mql.internal.visitor;

import net.hollowcube.mql.internal.tree.MqlAccessExpr;
import net.hollowcube.mql.internal.tree.MqlIdentExpr;
import net.hollowcube.mql.internal.tree.MqlVisitor;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public class VariableExtractionVisitor implements MqlVisitor<@NotNull Set<String>, Void> {
    public static final VariableExtractionVisitor INSTANCE = new VariableExtractionVisitor();

    private static final Set<String> VARIABLE_NAMES = Set.of("v", "variable");

    private VariableExtractionVisitor() {
    }

    // We only care about accesses which target v.xyz or variable.xyz. xyz would be the relevant variable
    @Override
    public Void visitAccessExpr(@NotNull MqlAccessExpr expr, @NotNull Set<String> strings) {
        if (expr.lhs() instanceof MqlIdentExpr ident && VARIABLE_NAMES.contains(ident.value())) {
            strings.add(ident.value());
        }
        return MqlVisitor.super.visitAccessExpr(expr, strings);
    }

}
