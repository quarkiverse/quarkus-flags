package io.quarkiverse.flags.spi;

import io.quarkiverse.flags.Flag;
import io.quarkiverse.flags.Flag.ComputationContext;
import io.quarkiverse.flags.Flag.Value;
import io.smallrye.mutiny.Uni;

/**
 * An evaluator can be used to compute a value of a feature flag.
 * <p>
 * Implementation classes must be CDI beans. Qualifiers are ignored. {@link jakarta.enterprise.context.Dependent} beans are
 * reused.
 */
public interface FlagEvaluator {

    /**
     * This key can be used to obtain the evaluator id from flag metadata.
     *
     * @see Flag#metadata()
     */
    static String META_KEY = "evaluator";

    /**
     * The identifier must be unique.
     *
     * @return the identifier
     */
    String id();

    /**
     * @param flag
     * @param initialValue
     * @param computationContext
     * @return the evaluated value
     */
    Uni<Value> evaluate(Flag flag, Flag.Value initialValue, ComputationContext computationContext);

}
