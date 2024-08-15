package net.hollowcube.mql.internal.tree;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record MqlTernaryExpr(
        @NotNull MqlExpr condition,
        @NotNull MqlExpr trueCase,
        @Nullable MqlExpr falseCase
) implements MqlExpr {

    @Override
    public <P, R> R visit(@NotNull MqlVisitor<P, R> visitor, P p) {
        return visitor.visitTernaryExpr(this, p);
    }

}
