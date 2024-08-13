package net.hollowcube.mql.tree;

import org.jetbrains.annotations.NotNull;

public sealed interface MqlExpr permits MqlAccessExpr, MqlArgListExpr, MqlBinaryExpr, MqlBlockExpr, MqlBreakExpr, MqlCallExpr, MqlContinueExpr, MqlIdentExpr, MqlIndexExpr, MqlNumberExpr, MqlReturnExpr, MqlTernaryExpr, MqlThisExpr, MqlUnaryExpr {

    <P, R> R visit(@NotNull MqlVisitor<P, R> visitor, P p);

}
