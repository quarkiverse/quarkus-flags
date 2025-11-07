package io.quarkiverse.flags.qute;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import io.quarkiverse.flags.Flag;
import io.quarkiverse.flags.Flags;
import io.quarkus.qute.CompletedStage;
import io.quarkus.qute.EngineConfiguration;
import io.quarkus.qute.EvalContext;
import io.quarkus.qute.Expression;
import io.quarkus.qute.NamespaceResolver;
import io.quarkus.qute.Results;

/**
 *
 */
@Singleton
@EngineConfiguration
public class FlagNamespaceResolver implements NamespaceResolver {

    public static final String NAMESPACE = "flag";

    @Inject
    Flags flags;

    @Override
    public CompletionStage<Object> resolve(EvalContext ctx) {
        if ("flags".equals(ctx.getName())) {
            return CompletedStage.of(flags);
        }
        // flag:bool('delta.feat.open')
        // flag:string('delta.feat.open')
        // flag:int('delta.feat.open')
        List<Expression> params = ctx.getParams();
        if (params.isEmpty()) {
            return Results.notFound(ctx);
        }
        return ctx.evaluate(params.get(0)).thenCompose(f -> {
            Optional<Flag> flag = flags.find(f.toString());
            if (flag.isEmpty()) {
                return Results.notFound(ctx);
            }
            return switch (ctx.getName()) {
                case "bool" -> cast(flag.get().compute().map(v -> v.asBoolean()).subscribeAsCompletionStage());
                case "string" -> cast(flag.get().compute().map(v -> v.asString()).subscribeAsCompletionStage());
                case "int" -> cast(flag.get().compute().map(v -> v.asInt()).subscribeAsCompletionStage());
                default -> throw new IllegalArgumentException("Unexpected value: " + ctx.getName());
            };
        });
    }

    @Override
    public String getNamespace() {
        return NAMESPACE;
    }

    @SuppressWarnings("unchecked")
    static <T> T cast(Object obj) {
        return (T) obj;
    }

}
