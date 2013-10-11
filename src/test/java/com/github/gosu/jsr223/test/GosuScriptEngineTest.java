package com.github.gosu.jsr223.test;


import com.github.gosu.jsr223.GosuScriptEngineFactory;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.script.Bindings;
import javax.script.Invocable;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.io.FileWriter;
import java.io.StringWriter;

import static org.fest.assertions.Assertions.assertThat;

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
          System.out.println(ret);
        } catch (ScriptException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testInstantiateHelloWorldExplicitly() throws Exception {
        try {
            Object ret = engine.eval("new com.github.gosu.jsr223.test.HelloWorld().printHelloWorld()");
          System.out.println(ret);
        } catch (ScriptException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testInstantiateHelloWorldWithUses() throws Exception {
        try {
            Object ret = engine.eval(
                    "uses com.github.gosu.jsr223.test.HelloWorld\n" +
                            "new HelloWorld().printHelloWorld()");
          System.out.println(ret);
        } catch (ScriptException e) {
            e.printStackTrace();
        }
    }

  /*
   * Tests derived from http://docs.oracle.com/javase/6/docs/technotes/guides/scripting/programmer_guide/
   */

    @Test
    public void executeScriptFromReader() throws Exception {
      // eventually want to do this properly, by redirecting the Writer that is part of the ScriptContext
      FileWriter out = new FileWriter("hello.gs");
      out.write("print(\"Hello from Gosu\")");
      out.close();
      Object ret = engine.eval(new java.io.FileReader("hello.gs"));
      System.out.println(ret);
    }

    @Test
    public void testPassValuesInAndOutOfEngine() throws Exception {
      engine.put("filename", "test.txt");

      // Use a value that was passed in, and create a new one.
      Object ret = engine.eval("var file = new java.io.File(filename); print(file.getAbsolutePath())");

      System.out.println(ret);
      System.out.println(engine.get("file"));

      assertThat(engine.get("file")).isNotNull();
    }

    @Test
    public void testInvokeFunction() throws Exception {
      String script = "function hello(name : String) : String {\n" +
          "var value = \"Hello, \" + name\n" +
          "return value\n" +
      "}";
      // evaluate script
      engine.eval(script);

      Invocable inv = (Invocable) engine;

      // invoke the global function named "hello"
      Object result = inv.invokeFunction("hello", "Scripting!!" );
      assertThat(result).as("Result should be return value of function").isEqualTo("Hello, Scripting!!");
      assertThat(engine.get("value")).as("Local variable should not be in symbol table").isNull();
    }

    @Test
    public void testInvokeMethod() throws Exception {
       String script =
          "class Doit {\n" +
            "function hello(name : String) {\n" +
              "print(\"Hello, \" +name)\n" +
            "}\n" +
          "}\n" +
          "var obj = new Doit()";

      engine.eval(script);

      Invocable inv = (Invocable) engine;

      Object obj = engine.get("obj");

      Object result = inv.invokeMethod(obj, "hello", "Script Method !!" );
      System.out.println(result);
    }

    @Test
    public void testInvokeFunctionViaInterface() throws Exception {
      // JavaScript code in a String
      String script = "function run() { print(\"run called\") }";

      // evaluate script
      engine.eval(script);

      Invocable inv = (Invocable) engine;

      // get Runnable interface object from engine. This interface methods
      // are implemented by script functions with the matching name.
      Runnable r = inv.getInterface(Runnable.class);

      // start a new thread that runs the script implemented
      // runnable interface
      Thread th = new Thread(r);
      th.start();

      // TODO: prove that the function ran
    }

    @Test
    public void testInvokeMethodViaInterface() throws Exception {
      // JavaScript code in a String
      String script =
          "class Doit {\n" +
            "function run() {\n" +
              "print(\"run method called\")\n" +
            "}\n" +
          "}\n" +
          "var obj = new Doit()";

      // evaluate script
      engine.eval(script);

      // get script object on which we want to implement the interface with
      Object obj = engine.get("obj");

      Invocable inv = (Invocable) engine;

      // get Runnable interface object from engine. This interface methods
      // are implemented by script methods of object 'obj'
      Runnable r = inv.getInterface(obj, Runnable.class);

      // start a new thread that runs the script implemented
      // runnable interface
      Thread th = new Thread(r);
      th.start();

      // TODO: prove that the function ran
    }

    @Test
    public void testMultipleScopes() throws Exception {
      StringWriter writer = new StringWriter();
      engine.put("out", writer);
      engine.put("x", "hello");
      // print global variable "x"
      engine.eval("out.append(x)");
      // the above line prints "hello"

      // Now, pass a different script context
      ScriptContext newContext = engine.getContext();
      Bindings engineScope = newContext.getBindings(ScriptContext.ENGINE_SCOPE);

      // add new variable "x" to the new engineScope
      engineScope.put("x", "world");
      engineScope.put("out", writer);

      // execute the same script - but this time pass a different script context
      engine.eval("out.append(x)", newContext);

      // the above line prints "world"
      // why doesn't this test expose assertion handling -- do we need to import something?
      assertThat(writer.toString()).as("Should have produced result 'helloworld'").isEqualTo("helloworld");
    }
}
