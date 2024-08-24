package net.hollowcube.mql.internal.visitor;

import net.hollowcube.mql.internal.tree.MqlAccessExpr;
import net.hollowcube.mql.internal.tree.MqlIdentExpr;
import net.hollowcube.mql.internal.tree.MqlVisitor;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public class VariableExtractionVisitor implements MqlVisitor<Void, Void> {
    public static final Set<String> VARIABLE_NAMES = Set.of("v", "variable");
    public static final Set<String> LOCAL_NAMES = Set.of("t", "temp");

    private final Set<String> variables;
    private final Set<String> locals;

    public VariableExtractionVisitor(Set<String> variables, Set<String> locals) {
        this.variables = variables;
        this.locals = locals;
    }

    // We only care about accesses which target v.xyz or variable.xyz. xyz would be the relevant variable
    @Override
    public Void visitAccessExpr(@NotNull MqlAccessExpr expr, Void v) {
        if (expr.lhs() instanceof MqlIdentExpr ident) {
            if (VARIABLE_NAMES.contains(ident.value()))
                variables.add(expr.target());
            else if (LOCAL_NAMES.contains(ident.value()))
                locals.add(expr.target());
        }
        return MqlVisitor.super.visitAccessExpr(expr, v);
    }
}
