/*
 * Copyright 2011 Greg Orlowski
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.gosu.jsr223;

import gw.lang.Gosu;

import javax.script.AbstractScriptEngine;
import javax.script.Bindings;
import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.Invocable;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptException;
import javax.script.SimpleBindings;
import java.io.IOException;
import java.io.Reader;

/**
 * A partial implementation of a JSR 223 {@link ScriptEngine} for the Gosu language.
 * 
 * @author Greg Orlowski
 */
public class GosuScriptEngine extends AbstractScriptEngine implements Invocable, Compilable {

  private boolean _initialized = false;
  private GosuCompiledScript _compiledScript = null;
  private final GosuScriptEngineFactory _factory;

  public GosuScriptEngine(GosuScriptEngineFactory factory) {
    super();
    _factory = factory;
  }

  private synchronized void init() {
    if (!_initialized) {
      Gosu.init();
      _initialized = true;
    }
  }


  /**
   * @param script
   *            A String representation of the script that you want to execute
   * @param scriptContext
   *            not used because manipulation of Gosu's symbol table is veiled behind some closed
   *            source code
   * @return the return value of the script
   */
  private Object parseAndExecute(String script, ScriptContext scriptContext) throws ScriptException {
    init();
    _compiledScript = new GosuCompiledScript(script, scriptContext, this);

    return _compiledScript.eval(scriptContext);
  }

  @Override
  public Object eval(String script, ScriptContext context) throws ScriptException {
    return parseAndExecute(script, context);
  }

  @Override
  public Object eval(Reader reader, ScriptContext context) throws ScriptException {
    return eval(fromReader(reader), context);
  }

  private static final String fromReader(Reader reader) throws ScriptException {
    StringBuilder sb = new StringBuilder();
    char[] cbuf = new char[1024];
    try {
      for (int numRead; (numRead = reader.read(cbuf)) > 0; sb.append(cbuf, 0, numRead)) ;
    } catch (IOException e) {
      throw new ScriptException(e);
    }
    return sb.toString();
  }

  @Override
  public Bindings createBindings() {
    return new SimpleBindings();
  }

  @Override
  public ScriptEngineFactory getFactory() {
    return _factory;
  }

  @Override
  public Object invokeMethod(Object thiz, String name, Object... args) throws ScriptException, NoSuchMethodException {
    return _compiledScript.invokeMethod(thiz, name, args);
  }

  @Override
  public Object invokeFunction(String name, Object... args) throws ScriptException, NoSuchMethodException {
    return _compiledScript.invokeFunction(name, args);
  }

  @Override
  public <T> T getInterface(Class<T> clasz) {
    return _compiledScript.getInterface(clasz);
  }

  @Override
  public <T> T getInterface(Object thiz, Class<T> clasz) {
    return _compiledScript.getInterface(thiz, clasz);
  }

  @Override
  public CompiledScript compile(String script) throws ScriptException {
    return new GosuCompiledScript(script, getScriptContext(createBindings()), this);
  }

  @Override
  public CompiledScript compile(Reader script) throws ScriptException {
    return new GosuCompiledScript(fromReader(script), getScriptContext(createBindings()), this);
  }
}
