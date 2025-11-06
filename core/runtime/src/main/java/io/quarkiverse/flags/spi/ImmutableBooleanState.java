package io.quarkiverse.flags.spi;

import io.quarkiverse.flags.Flag;

public class ImmutableBooleanState implements Flag.State {

    public static final ImmutableBooleanState from(boolean value) {
        return value ? TRUE : FALSE;
    }

    public static final ImmutableBooleanState TRUE = new ImmutableBooleanState(true);
    public static final ImmutableBooleanState FALSE = new ImmutableBooleanState(false);

    private final boolean value;

    private ImmutableBooleanState(boolean value) {
        this.value = value;
    }

    @Override
    public boolean getBoolean() {
        return value;
    }

    @Override
    public String getString() {
        return "" + value;
    }

    @Override
    public int getInteger() {
        return value ? 1 : 0;
    }

}
