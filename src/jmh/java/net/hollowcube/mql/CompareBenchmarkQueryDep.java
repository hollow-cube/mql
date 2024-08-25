package net.hollowcube.mql;

import com.bedrockk.molang.MoLang;
import com.bedrockk.molang.parser.Expression;
import com.bedrockk.molang.runtime.MoLangRuntime;
import com.bedrockk.molang.runtime.struct.QueryStruct;
import gg.moonflower.molangcompiler.api.MolangCompiler;
import gg.moonflower.molangcompiler.api.MolangEnvironment;
import gg.moonflower.molangcompiler.api.MolangExpression;
import gg.moonflower.molangcompiler.api.MolangRuntime;
import gg.moonflower.molangcompiler.api.object.MolangLibrary;
import gg.moonflower.molangcompiler.core.node.MolangFunctionNode;
import net.hollowcube.mql.foreign.MqlEnv;
import net.hollowcube.mql.foreign.Query;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import team.unnamed.mocha.MochaEngine;
import team.unnamed.mocha.runtime.MochaFunction;
import team.unnamed.mocha.runtime.binding.Binding;
import team.unnamed.mocha.runtime.compiled.MochaCompiledFunction;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

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
public class CompareBenchmarkQueryDep {
    @Binding("q")
    class MyQuery {
        @Query
        @Binding("inc")
        public double inc(double value) {
            return value + 1;
        }
    }

    interface MqlScript extends MochaCompiledFunction {
        double eval(@MqlEnv("q") MyQuery query);
    }

    private MochaEngine<?> mocha; // unnamed's mocha
    private MolangEnvironment environment; // moonflower's molang-compiler
    private MoLangRuntime mlRuntime; // bedrockk's Molang

    private MochaFunction function; // unnamed's mocha
    private MolangExpression mlExpression; // moonflower's molang-compiler
    private List<Expression> mlExpressions; // bedrockk's MoLang
    private MqlScript mqlScript; // hollowcube's mql

    private final MyQuery query = new MyQuery();

    @Setup(Level.Trial)
    public void prepare() throws Exception {
//        mocha = MochaEngine.createStandard();
//        mocha.bindInstance(MyQuery.class, query, "q");
        environment = MolangRuntime.runtime().create();
        environment.loadLibrary("q", new MolangLibrary() {
            @Override
            protected void populate(BiConsumer<String, MolangExpression> biConsumer) {
                biConsumer.accept("inc", new MolangFunctionNode(1, args -> args.get(0) + 1));
            }

            @Override
            protected String getName() {
                return "q";
            }
        });
        mlRuntime = MoLang.newRuntime();
        var mlQuery = new QueryStruct(Map.of(
                "inc", params -> params.getDouble(0) + 1
        ));
        mlRuntime.getEnvironment().setValue("q", mlQuery);

        final String expr = "v.t = q.inc(33); return 3*v.t*q.inc(1) - 2*v.t*v.t*v.t;";

//        function = mocha.compile(expr);
        mlExpression = MolangCompiler.create(MolangCompiler.DEFAULT_FLAGS, getClass().getClassLoader()).compile(expr);
        mlExpressions = MoLang.newParser(expr).parse();
        mqlScript = MqlCompiler.compile(MqlScript.class, expr, false).get();
    }

    @Benchmark
    public void bedrockk_MoLang(Blackhole bh) {
        bh.consume(mlRuntime.execute(mlExpressions));
    }

//    @Benchmark
//    public void unnamed_mocha(Blackhole bh) {
//        bh.consume(function.evaluate());
//    }

    @Benchmark
    public void moonflower_molang_compiler(Blackhole bh) throws Exception {
        //noinspection OverrideOnly
        bh.consume(mlExpression.get(environment));
    }

    @Benchmark
    public void mql(Blackhole bh) throws Exception {
        bh.consume(mqlScript.eval(query));
    }

}
