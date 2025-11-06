package io.quarkiverse.flags.spi;

import java.util.NoSuchElementException;

import io.quarkiverse.flags.Flag;

public class ImmutableStringState implements Flag.State {

    private final String value;

    public ImmutableStringState(String value) {
        this.value = value;
    }

    @Override
    public boolean getBoolean() {
        return Boolean.parseBoolean(value);
    }

    @Override
    public String getString() {
        return value;
    }

    @Override
    public int getInteger() {
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException e) {
            throw new NoSuchElementException();
        }
    }

}
