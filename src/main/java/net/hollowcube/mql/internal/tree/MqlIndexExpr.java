package net.hollowcube.mql.internal.tree;

import org.jetbrains.annotations.NotNull;

public record MqlIndexExpr(@NotNull MqlExpr lhs, @NotNull MqlExpr target) implements MqlExpr {

    @Override
    public <P, R> R visit(@NotNull MqlVisitor<P, R> visitor, P p) {
        return visitor.visitIndexExpr(this, p);
    }

}
