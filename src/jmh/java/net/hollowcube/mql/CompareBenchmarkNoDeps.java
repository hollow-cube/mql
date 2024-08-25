package net.hollowcube.mql;

import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;

/**
 * <p>Benchmark comparing the performance of different Molang implementations.</p>
 * <p>Originally based on <a href="https://github.com/unnamed/mocha/blob/main/src/jmh/java/team/unnamed/mocha/CompareBenchmark.java">
 * Mocha CompareBenchmark</a>.</p>
 */
@State(Scope.Benchmark)
@Warmup(iterations = 1, time = 10)
@Measurement(iterations = 2, time = 10)
@Fork(value = 2, warmups = 2)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class CompareBenchmarkNoDeps {
//    interface MqlScript {
//        double eval();
//    }
//
//    private MochaEngine<?> mocha; // unnamed's mocha
//    private MolangEnvironment environment; // moonflower's molang-compiler
//    private MoLangRuntime mlRuntime; // bedrockk's Molang
//
//    private MochaFunction function; // unnamed's mocha
//    private MolangExpression mlExpression; // moonflower's molang-compiler
//    private List<Expression> mlExpressions; // bedrockk's MoLang
//    private MqlScript mqlScript; // hollowcube's mql
//
//    @Setup(Level.Trial)
//    public void prepare() throws Exception {
//        mocha = MochaEngine.createStandard();
//        environment = MolangRuntime.runtime().create();
//        mlRuntime = MoLang.newRuntime();
//
//        final String expr = "v.t = 3; return 3*v.t*v.t - 2*v.t*v.t*v.t;";
//
//        function = mocha.compile(expr);
//        mlExpression = MolangCompiler.create(MolangCompiler.DEFAULT_FLAGS, getClass().getClassLoader()).compile(expr);
//        mlExpressions = MoLang.newParser(expr).parse();
//        mqlScript = MqlCompiler.compile(MqlScript.class, expr, false).get();
//    }
//
//    @Benchmark
//    public void bedrockk_MoLang(Blackhole bh) {
//        bh.consume(mlRuntime.execute(mlExpressions));
//    }
//
//    @Benchmark
//    public void unnamed_mocha(Blackhole bh) {
//        bh.consume(function.evaluate());
//    }
//
//    @Benchmark
//    public void moonflower_molang_compiler(Blackhole bh) throws Exception {
//        //noinspection OverrideOnly
//        bh.consume(mlExpression.get(environment));
//    }
//
//    @Benchmark
//    public void mql(Blackhole bh) throws Exception {
//        bh.consume(mqlScript.eval());
//    }

}
