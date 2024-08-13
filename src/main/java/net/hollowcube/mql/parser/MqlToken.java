package net.hollowcube.mql.parser;

import org.jetbrains.annotations.NotNull;

record MqlToken(@NotNull Type type, int start, int end) {

    enum Type {
        PLUS, MINUS, STAR, SLASH,
        LPAREN, RPAREN, LBRACK, RBRACK, LBRACE, RBRACE,
        DOT, COMMA, COLON, SEMICOLON, QUESTION, QUESTIONQUESTION,
        GTE, GE, LTE, LE, EQ, NEQ,
        NUMBER, IDENT;
    }

}
