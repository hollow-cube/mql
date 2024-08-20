package net.hollowcube.mql.internal.tree;

import org.jetbrains.annotations.NotNull;

public sealed interface MqlExpr permits MqlAccessExpr, MqlArgListExpr, MqlAssignExpr, MqlBinaryExpr, MqlBlockExpr, MqlBreakExpr, MqlCallExpr, MqlContinueExpr, MqlIdentExpr, MqlIndexExpr, MqlNumberExpr, MqlReturnExpr, MqlStringExpr, MqlTernaryExpr, MqlThisExpr, MqlUnaryExpr {

    <P, R> R visit(@NotNull MqlVisitor<P, R> visitor, P p);

}
