package net.hollowcube.mql.tree;

import org.jetbrains.annotations.NotNull;

public record MqlNumberExpr(double value) implements MqlExpr {

    @Override
    public <P, R> R visit(@NotNull MqlVisitor<P, R> visitor, P p) {
        return visitor.visitNumberExpr(this, p);
    }

}
