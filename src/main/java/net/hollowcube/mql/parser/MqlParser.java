package net.hollowcube.mql.parser;

import net.hollowcube.mql.internal.tree.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class MqlParser {
    private final MqlLexer lexer;
    private final boolean isSimpleExpr;

    public MqlParser(@NotNull String source, boolean isSimpleExpr) {
        this.lexer = new MqlLexer(source);
        this.isSimpleExpr = isSimpleExpr;
    }

    public @NotNull MqlExpr parse() {
        if (isSimpleExpr) {
            return expr(0);
        }

        var exprs = exprList();
        if (lexer.peek() != null) // We should always be at EOF.
            throw new MqlParseError("unexpected token " + lexer.peek());
        return new MqlBlockExpr(exprs);
    }

    private @NotNull MqlExpr expr(int minBindingPower) {
        MqlExpr lhs = lhs();

        while (true) {
            Operator op = operator();
            if (op == null) break;

            var postfixBindingPower = op.postfixBindingPower();
            if (postfixBindingPower != -1) {
                if (postfixBindingPower < minBindingPower) break;
                lexer.next(); // Operator token

                lhs = switch (op) {
                    case TERNARY -> {
                        MqlExpr trueExpr = expr(0), falseExpr = null;

                        var next = lexer.peek(); // Optional false expr
                        if (next != null && next.type() == MqlToken.Type.COLON) {
                            lexer.expect(MqlToken.Type.COLON);
                            falseExpr = expr(postfixBindingPower);
                        }

                        yield new MqlTernaryExpr(lhs, trueExpr, falseExpr);
                    }
                    case LPAREN -> {
                        // Get argument list
                        List<MqlExpr> args = new ArrayList<>();
                        var next = lexer.peek();

                        if (next != null && next.type() != MqlToken.Type.RPAREN) {
                            do {
                                args.add(expr(0));
                                next = lexer.peek();
                            } while (next != null && next.type() == MqlToken.Type.COMMA && lexer.next() != null);
                        }

                        lexer.expect(MqlToken.Type.RPAREN);
                        yield args.isEmpty() ? lhs : new MqlCallExpr(lhs, new MqlArgListExpr(args));
                    }
                    case LBRACK -> {
                        var target = expr(0);
                        lexer.expect(MqlToken.Type.RBRACK);
                        yield new MqlIndexExpr(lhs, target);
                    }
                    default -> throw new IllegalStateException("Unexpected rhs: " + op);
                };

                continue;
            }

            // Stop if operator left binding power is less than the current min
            if (op.lbp < minBindingPower) break;

            lexer.next(); // Operator token

            // Parse right side expression
            MqlExpr rhs = expr(op.rbp);
            lhs = switch (op) {
                case ASSIGN -> {
                    if (!(lhs instanceof MqlAccessExpr target))
                        throw new MqlParseError("left side of assignment must be a variable access, was " + lhs);
                    yield new MqlAssignExpr(target, rhs);
                }
                case MEMBER_ACCESS -> {
                    if (!(rhs instanceof MqlIdentExpr ident))
                        throw new MqlParseError("rhs of member target must be an ident, was " + rhs);
                    yield new MqlAccessExpr(lhs, ident.value());
                }
                default -> new MqlBinaryExpr(op.op, lhs, rhs);
            };
        }

        return lhs;
    }

    /**
     * Parses a possible left side expression.
     */
    private @NotNull MqlExpr lhs() {
        MqlToken token = lexer.next();
        if (token == null) throw new MqlParseError("unexpected end of input");

        return switch (token.type()) {
            case NUMBER -> new MqlNumberExpr(Double.parseDouble(lexer.span(token)));
            case STRING -> {
                final String quotedValue = lexer.span(token);
                yield new MqlStringExpr(quotedValue.substring(1, quotedValue.length() - 1));
            }
            case IDENT -> {
                var span = lexer.span(token);
                yield switch (span) {
                    case "this" -> MqlThisExpr.INSTANCE;
                    case "continue" -> MqlContinueExpr.INSTANCE;
                    case "break" -> MqlBreakExpr.INSTANCE;
                    case "return" -> {
                        var next = lexer.peek();
                        if (next != null && next.type() != MqlToken.Type.SEMICOLON)
                            yield new MqlReturnExpr(expr(0));
                        yield new MqlReturnExpr(null);
                    }
                    default -> new MqlIdentExpr(span);
                };
            }
            case MINUS -> {
                var rhs = expr(Operator.MINUS.prefixBindingPower());
                yield new MqlUnaryExpr(MqlUnaryExpr.Op.NEGATE, rhs);
            }
            case LPAREN -> {
                var expr = expr(0);
                lexer.expect(MqlToken.Type.RPAREN);
                yield expr;
            }
            case LBRACE -> {
                var exprs = exprList();
                lexer.expect(MqlToken.Type.RBRACE);
                yield new MqlBlockExpr(exprs);
            }
            //todo better error handling
            default -> throw new MqlParseError("unexpected token " + token);
        };
    }

    private @NotNull List<MqlExpr> exprList() {
        var exprs = new ArrayList<MqlExpr>();
        MqlToken next = lexer.peek();
        while (next != null && next.type() != MqlToken.Type.RBRACE) {
            exprs.add(expr(0));

            // There should be a semicolon here, although if this was the last expression then its valid
            // to exclude the semicolon. Note however that this does NOT behave as an implicit return as
            // in some languages like Rust.
            next = lexer.peek();
            if (next != null && next.type() != MqlToken.Type.RBRACE) {
                lexer.expect(MqlToken.Type.SEMICOLON);
                next = lexer.peek();
            }
        }
        return exprs;
    }

    private @Nullable Operator operator() {
        MqlToken token = lexer.peek();
        if (token == null) return null;
        return switch (token.type()) {
            case PLUS -> Operator.PLUS;
            case MINUS -> Operator.MINUS;
            case SLASH -> Operator.DIV;
            case STAR -> Operator.MUL;
            case DOT -> Operator.MEMBER_ACCESS;
            case QUESTION -> Operator.TERNARY;
            case QUESTIONQUESTION -> Operator.NULL_COALESCE;
            case LPAREN -> Operator.LPAREN;
            case LBRACK -> Operator.LBRACK;
            case GTE -> Operator.GTE;
            case GE -> Operator.GE;
            case LTE -> Operator.LTE;
            case LE -> Operator.LE;
            case EQEQ -> Operator.EQ;
            case NEQ -> Operator.NEQ;
            case EQ -> Operator.ASSIGN;
            default -> null;
        };
    }

    private enum Operator {
        // Note that we do higher on left first because in this case we want to
        // left associate the operator. That means that a chained assignment
        // like t.x = t.y = 5 will evaluate `t.x = (t.y = 5)` instead of `(t.x = t.y) = 5`
        ASSIGN(4, 3, null),

        NULL_COALESCE(5, 6, MqlBinaryExpr.Op.NULL_COALESCE),
        PLUS(25, 26, MqlBinaryExpr.Op.PLUS),
        MINUS(25, 26, MqlBinaryExpr.Op.MINUS),
        DIV(27, 28, MqlBinaryExpr.Op.DIV),
        MUL(27, 28, MqlBinaryExpr.Op.MUL),
        LPAREN(30, 30, null),
        LBRACK(30, 30, null),

        GTE(30, 31, MqlBinaryExpr.Op.GTE),
        GE(30, 31, MqlBinaryExpr.Op.GE),
        LTE(30, 31, MqlBinaryExpr.Op.LTE),
        LE(30, 31, MqlBinaryExpr.Op.LE),
        EQ(30, 31, MqlBinaryExpr.Op.EQ),
        NEQ(30, 31, MqlBinaryExpr.Op.NEQ),

        MEMBER_ACCESS(35, 36, null),
        TERNARY(0, 0, null); // Open of a ternary expression (?), only a postfix operator

        private final int lbp;
        private final int rbp;
        private final MqlBinaryExpr.Op op;

        Operator(int lbp, int rbp, MqlBinaryExpr.Op op) {
            this.lbp = lbp;
            this.rbp = rbp;
            this.op = op;
        }

        public int prefixBindingPower() {
            return switch (this) {
                case MINUS -> 30;
                default -> -1;
            };
        }

        public int postfixBindingPower() {
            return switch (this) {
                case TERNARY -> 1;
                case LPAREN, LBRACK -> 34;
                default -> -1;
            };
        }
    }

}
