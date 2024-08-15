package net.hollowcube.mql.internal.tree;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record MqlReturnExpr(@Nullable MqlExpr value) implements MqlExpr {

    @Override
    public <P, R> R visit(@NotNull MqlVisitor<P, R> visitor, P p) {
        return visitor.visitReturnExpr(this, p);
    }

}
