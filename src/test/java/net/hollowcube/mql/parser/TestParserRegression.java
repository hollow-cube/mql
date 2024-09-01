package net.hollowcube.mql.parser;

import net.hollowcube.mql.internal.visitor.MqlPrinter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestParserRegression {

    @Test
    void animatedJavaScript1() {
        var input = "1 - math.sin(q.life_time * v.walk_speed + 90) * 12.25\n";
        var expected = "(- 1.0 (* (C (. math sin) ((+ (* (. q life_time) (. v walk_speed)) 90.0))) 12.25))";

        var expr = new MqlParser(input, true).parse();
        var actual = new MqlPrinter().visit(expr, null);

        assertEquals(expected, actual, "input: " + input);
    }
}
