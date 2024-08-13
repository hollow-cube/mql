package net.hollowcube.mql.tree;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public record MqlBlockExpr(@NotNull List<MqlExpr> exprs) implements MqlExpr {

    @Override
    public <P, R> R visit(@NotNull MqlVisitor<P, R> visitor, P p) {
        return visitor.visitBlockExpr(this, p);
    }

}
