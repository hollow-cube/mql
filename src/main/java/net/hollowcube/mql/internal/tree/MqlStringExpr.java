package net.hollowcube.mql.internal.tree;

import org.jetbrains.annotations.NotNull;

public record MqlStringExpr(@NotNull String value) implements MqlExpr {
    @Override
    public <P, R> R visit(@NotNull MqlVisitor<P, R> visitor, P p) {
        return visitor.visitStringExpr(this, p);
    }
}
