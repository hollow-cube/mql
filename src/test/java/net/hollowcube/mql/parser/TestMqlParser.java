package net.hollowcube.mql.parser;

import net.hollowcube.mql.internal.visitor.MqlPrinter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/*
WEIRD MOLANG NOTES
* Everything but strings are case insensitive
* Array indices are 0-based. Negative indices are clamped to 0. Out of bounds indices are wrapped around.

* Strings only support == and !=


Reminder: the standing rule of thumb in Molang is that if something would error or be a bad rhs, it is converted to 0.0 (and generally throw a content error on screen in non-publish builds. Note that content errors may prevent uploading content to the marketplace, so please ensure expressions aren't going to do bad things such as dividing by zero).
 */
public class TestMqlParser {

    @MethodSource("inputPairs")
    @ParameterizedTest(name = "{0}")
    public void testInputPairs(String name, String input, String expected) {
        var expr = new MqlParser(input, true).parse();
        var actual = new MqlPrinter().visit(expr, null);

        assertEquals(expected, actual, "input: " + input);
    }

    private static Stream<Arguments> inputPairs() {
        // Note that all of these are simple expressions.
        return Stream.of(
                Arguments.of("basic number",
                        "1", "1.0"),
                Arguments.of("string",
                        "'hello'", "'hello'"),
                Arguments.of("basic ref",
                        "abc", "abc"),
                Arguments.of("basic add",
                        "1 + 2", "(+ 1.0 2.0)"),
                Arguments.of("nested add",
                        "1 + 2 + 3", "(+ (+ 1.0 2.0) 3.0)"),
                Arguments.of("negate simple",
                        "-1", "(- 1.0)"),
                Arguments.of("negate nested",
                        "---1", "(- (- (- 1.0)))"),
                Arguments.of("negate precedence",
                        "-2 + 1", "(+ (- 2.0) 1.0)"),
                Arguments.of("basic target",
                        "a.b", "(. a b)"),
                Arguments.of("target/add precedence",
                        "a.b + 1", "(+ (. a b) 1.0)"),
                Arguments.of("null coalesce precedence",
                        "a.b ?? 1", "(?? (. a b) 1.0)"),
                Arguments.of("null coalesce precedence 2",
                        "1 + 2 ?? 1", "(?? (+ 1.0 2.0) 1.0)"),
                Arguments.of("normalize case 1",
                        "q.is_alive()", "(. q is_alive)"),
                Arguments.of("normalize case 2",
                        "q.is_alive", "(. q is_alive)"),
                Arguments.of("call with args",
                        "sin(25)", "(C sin (25.0))"),
                Arguments.of("call with args 2",
                        "m.sin(25)", "(C (. m sin) (25.0))"),
                Arguments.of("single ternary simple",
                        "1 ? 2 : 3", "(? 1.0 2.0 3.0)"),
                Arguments.of("ternary no false",
                        "1 ? 2", "(? 1.0 2.0)"),
                Arguments.of("nested ternary",
                        "1 ? 2 : 3 ? 4 : 5", "(? 1.0 2.0 (? 3.0 4.0 5.0))"),
                Arguments.of("ternary add precedence",
                        "1 ? 2 + 3 : 4", "(? 1.0 (+ 2.0 3.0) 4.0)"),
                Arguments.of("ternary add precedence",
                        "1 ? 2 + 3 : 4", "(? 1.0 (+ 2.0 3.0) 4.0)"),
                Arguments.of("index",
                        "a[1]", "([ a 1.0)"),
                Arguments.of("index then call",
                        "a[1](2)", "(C ([ a 1.0) (2.0))"),
                Arguments.of("call then index",
                        "a(2)[1]", "([ (C a (2.0)) 1.0)"),
                Arguments.of("empty block",
                        "{}", "{ }"),
                Arguments.of("single statement block",
                        "{\n1.0;\n}", "{ 1.0 }"),
                Arguments.of("multi statement block",
                        "{\n1.0;\n2.0;\n}", "{ 1.0 2.0 }"),
                Arguments.of("loop longer",
                        "loop(5, {\nv.x;})", "(C loop (5.0 { (. v x) }))"),
                Arguments.of("variable assign",
                        "t.x = 5", "(= (. t x) 5.0)"),
                Arguments.of("chained variable assign",
                        "t.x = t.y = 5", "(= (. t x) (= (. t y) 5.0))")
        );
    }

    @Test
    void testComplexExpr() {
        var input = "t.x = 5;\nt.y = 4;";
        var expr = new MqlParser(input, false).parse();
        var actual = new MqlPrinter().visit(expr, null);

        assertEquals("{ (= (. t x) 5.0) (= (. t y) 4.0) }", actual, "input: " + input);
    }


    @Test
    void testComplexExprNoTrailingSemicolon() {
        var input = "t.x = 5;\nt.y = 4";
        var expr = new MqlParser(input, false).parse();
        var actual = new MqlPrinter().visit(expr, null);

        assertEquals("{ (= (. t x) 5.0) (= (. t y) 4.0) }", actual, "input: " + input);
    }

}
