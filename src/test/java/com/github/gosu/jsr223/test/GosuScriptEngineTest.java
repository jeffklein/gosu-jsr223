package com.github.gosu.jsr223.test;


import com.github.gosu.jsr223.GosuScriptEngineFactory;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

/**
 * Junit4 test to test the GosuScriptEngine.
 * TODO: (jklein 9/19/2013): write more tests!
 */
public class GosuScriptEngineTest {

    private static ScriptEngine engine;

    @BeforeClass
    public static void initScriptEngine() {
        ScriptEngineManager mgr = new ScriptEngineManager();
        engine = mgr.getEngineByName(GosuScriptEngineFactory.ENGINE_NAME);
    }

    @Test
    public void testPrintHelloWorld() throws Exception {
        try {
            // This will correctly print HELLO WORLD
            // On my machine, the initial evaluation takes about 2 seconds,
            // and subsequent calls to eval (once the Gosu internals are
            // initialized), run in between 14 to 25 milliseconds
            Object ret = engine.eval("print(\"HELLO WORLD\")");
        } catch (ScriptException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testInstantiateHelloWorld() throws Exception {
        try {
            Object ret = engine.eval("new com.github.gosu.jsr223.test.HelloWorld().printHelloWorld()");
        } catch (ScriptException e) {
            e.printStackTrace();
        }
    }
}
