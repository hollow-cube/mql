package net.hollowcube.mql.tree;

import org.jetbrains.annotations.NotNull;

public record MqlIdentExpr(@NotNull String value) implements MqlExpr {

    @Override
    public <P, R> R visit(@NotNull MqlVisitor<P, R> visitor, P p) {
        return visitor.visitRefExpr(this, p);
    }

}
