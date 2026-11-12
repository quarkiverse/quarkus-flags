package io.quarkiverse.flags.qute.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.NoSuchElementException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.flags.RegisterFlag;
import io.quarkiverse.flags.WithMetadata;
import io.quarkus.qute.Qute;
import io.quarkus.test.QuarkusExtensionTest;

public class FlagNamespaceResolverTest {

    @RegisterExtension
    static final QuarkusExtensionTest config = new QuarkusExtensionTest()
            .withApplicationRoot(root -> root.addClasses(QuteFlags.class))
            .overrideRuntimeConfigKey("quarkus.flags.runtime.alpha.value", "true")
            .overrideRuntimeConfigKey("quarkus.flags.runtime.bravo.value", "true")
            .overrideRuntimeConfigKey("quarkus.flags.runtime.charlie.value", "5")
            .overrideRuntimeConfigKey("quarkus.flags.runtime.charlie.meta.foo", "true")
            .overrideRuntimeConfigKey("quarkus.flags.runtime.delta.value", "3.14");

    @Test
    public void testFlag() {
        assertEquals("true", Qute.fmt("{flag:bool('alpha')}").render());
        assertEquals("false", Qute.fmt("{flag:disabled('alpha')}").render());
        assertEquals("true", Qute.fmt("{flag:on('alpha')}").render());
        assertEquals("false", Qute.fmt("{flag:off('alpha')}").render());
        assertEquals("true", Qute.fmt("{flag:string(data.0)}", "bravo"));
        assertEquals("5", Qute.fmt("{flag:int(\"charlie\")}").render());
        assertEquals("ok", Qute.fmt("{#if flag:enabled('alpha')}ok{/if}").render());
        assertEquals("true", Qute.fmt("{flag:meta('charlie').get('foo')}").render());
        assertEquals("true", Qute.fmt("{flag:find('charlie').metadata.get('foo')}").render());
        assertEquals("3.14", Qute.fmt("{flag:decimal('delta')}").render());
        // non-existent flag - not found; Qute default values can be used
        assertEquals("none", Qute.fmt("{flag:bool('nonexistent').or('none')}").render());
        assertEquals("none", Qute.fmt("{flag:enabled('nonexistent').or('none')}").render());
        assertEquals("none", Qute.fmt("{flag:disabled('nonexistent').or('none')}").render());
        assertEquals("none", Qute.fmt("{flag:on('nonexistent').or('none')}").render());
        assertEquals("none", Qute.fmt("{flag:off('nonexistent').or('none')}").render());
        assertEquals("none", Qute.fmt("{flag:string('nonexistent').or('none')}").render());
        assertEquals("none", Qute.fmt("{flag:int('nonexistent').or('none')}").render());
        assertEquals("none", Qute.fmt("{flag:decimal('nonexistent').or('none')}").render());
        assertEquals("none", Qute.fmt("{flag:find('nonexistent').or('none')}").render());
        assertEquals("none", Qute.fmt("{flag:meta('nonexistent').or('none')}").render());
        // unknown method name - not found
        assertEquals("none", Qute.fmt("{flag:unknown('alpha').or('none')}").render());
        // conversion error - "true" cannot be converted to int
        assertThrows(NoSuchElementException.class,
                () -> Qute.fmt("{flag:int('alpha')}").render());
        // default values - conversion fails, default returned
        assertEquals("42", Qute.fmt("{flag:int('alpha', 42)}").render());
        assertEquals("true", Qute.fmt("{flag:bool('charlie', true)}").render());
        // default values - conversion succeeds, actual value returned
        assertEquals("true", Qute.fmt("{flag:bool('alpha', false)}").render());
        assertEquals("5", Qute.fmt("{flag:int('charlie', 99)}").render());
        assertEquals("true", Qute.fmt("{flag:string('alpha', 'fallback')}").render());
        // default values - non-existent flag, default returned
        assertEquals("99", Qute.fmt("{flag:int('nonexistent', 99)}").render());
        assertEquals("false", Qute.fmt("{flag:bool('nonexistent', false)}").render());
        assertEquals("false", Qute.fmt("{flag:on('nonexistent', false)}").render());
        assertEquals("true", Qute.fmt("{flag:off('nonexistent', true)}").render());
        String allFlags = Qute.fmt("""
                {#for flag in flag:flags}{flag.feature}{#if flag_hasNext}:{/if}{/for}
                """).render();
        assertTrue(allFlags.contains("alpha"));
        assertTrue(allFlags.contains("bravo"));
        assertTrue(allFlags.contains("charlie"));
    }

    @Test
    public void testRegisteredFlag() {
        assertEquals("true", Qute.fmt("{flag:bool('foxtrot')}").render());
        assertEquals("42", Qute.fmt("{flag:int('foxtrot-int')}").render());
        assertEquals("baz", Qute.fmt("{flag:meta('foxtrot').get('foo')}").render());
    }

    public static class QuteFlags {

        @RegisterFlag
        @WithMetadata(key = "foo", value = "baz")
        static volatile boolean foxtrot = true;

        @RegisterFlag(name = "foxtrot-int")
        static volatile int foxtrotInt = 42;
    }

}
