package net.hollowcube.mql.internal;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.util.TraceClassVisitor;

import java.io.PrintWriter;
import java.io.StringWriter;

public class TestMqlCodeBuilder {


    @Test
    void test1() {
        var builder = new MqlCodeBuilder();
        builder.addInitializer("t.x = 1", "init1");
        var bytecode = builder.lowerCode();

        StringWriter sw = new StringWriter();
        var cp = new TraceClassVisitor(new PrintWriter(sw));
        var cr = new ClassReader(bytecode);
        cr.accept(cp, 0);
        System.out.println(sw.toString());
    }
}
