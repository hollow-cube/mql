package net.hollowcube.mql.internal.tree;

import org.jetbrains.annotations.NotNull;

public record MqlUnaryExpr(
        @NotNull Op operator,
        @NotNull MqlExpr rhs
) implements MqlExpr {
    public enum Op {
        NEGATE,
    }

    @Override
    public <P, R> R visit(@NotNull MqlVisitor<P, R> visitor, P p) {
        return visitor.visitUnaryExpr(this, p);
    }

}
