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
import gw.lang.GosuShop;
import gw.lang.parser.ExternalSymbolMapSymbolTableWrapper;
import gw.lang.parser.GosuParserFactory;
import gw.lang.parser.IGosuProgramParser;
import gw.lang.parser.IParseResult;
import gw.lang.parser.ISymbol;
import gw.lang.parser.ISymbolTable;
import gw.lang.parser.ParserOptions;
import gw.lang.parser.StandardSymbolTable;
import gw.lang.parser.ThreadSafeSymbolTable;
import gw.lang.parser.exceptions.ParseResultsException;
import gw.lang.reflect.IPropertyInfo;
import gw.lang.reflect.IRelativeTypeInfo;
import gw.lang.reflect.ReflectUtil;
import gw.lang.reflect.TypeSystem;
import gw.lang.reflect.gs.IGosuProgram;
import gw.lang.reflect.gs.IProgramInstance;

import javax.script.AbstractScriptEngine;
import javax.script.Bindings;
import javax.script.Invocable;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptException;
import javax.script.SimpleBindings;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Method;
import java.util.List;

/**
 * A partial implementation of a JSR 223 {@link ScriptEngine} for the Gosu language. This will work
 * if you do not need to execute Gosu scripts in a predefined ScriptContext. Passing the state of
 * {@link ScriptContext} to Gosu's {@link ISymbolTable} is not transparent because the Gosu parser
 * and {@link ISymbol} implementations are closed-source (gw.internal package).
 * 
 * Because complete implementation of a Gosu {@link ScriptEngine} runs into a licensing roadblock,
 * I'm not going to work out an optimal threading policy for this. I don't know how Gosu manages
 * state in its {@link StandardSymbolTable} implementation between invocations of a common
 * {@link IGosuProgramParser}. You can probably achieve a JSR 223 THREAD-ISOLATED policy if you
 * correctly extend and use {@link ThreadSafeSymbolTable} in this class.
 * 
 * @author Greg Orlowski
 */
public class GosuScriptEngine extends AbstractScriptEngine implements Invocable {

  private boolean initialized = false;
  private IGosuProgram _gosuProgram;
  private IProgramInstance _programInstance;
  private ISymbolTable _symbolTable;

  public GosuScriptEngine() {
        super();
  }

    private synchronized void init() {
        if (!initialized) {
            Gosu.init();
            initialized = true;
        }
    }


    private ISymbolTable getSymbolTable(ScriptContext context) {
      ISymbolTable result = GosuShop.createSymbolTable();
      if (context != null) {
        Bindings bindings = context.getBindings(ScriptContext.ENGINE_SCOPE);
        for (String k : bindings.keySet()) {
          Object v = bindings.get(k);
          result.putSymbol(GosuShop.createSymbol(k, TypeSystem.get(v.getClass()), v));
        }
      }
      return result;
    }

    private void unloadSymbolTable(ScriptContext context) {
      if (context == null) return;

      final List<? extends IPropertyInfo> properties = ((IRelativeTypeInfo) _gosuProgram.getTypeInfo()).getProperties(_gosuProgram);
      for (IPropertyInfo p : properties) {
        System.out.println("handling value of var " + p.getDisplayName());
        context.getBindings(ScriptContext.ENGINE_SCOPE).put(p.getDisplayName(), p.getAccessor().getValue(_programInstance));
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
    private Object parseAndExecute(String script, ScriptContext scriptContext) {
      init();
      Object ret = null;
      try {
        // splitting to multiple lines to ease stack inspection
        ParserOptions parserOptions = new ParserOptions();

        // TODO: this is where we could pass values from the ScriptContext to
          // the Gosu symbol table (I think)
        _symbolTable = getSymbolTable(scriptContext);
        IGosuProgramParser parser = getParser();// GosuParserFactory.createProgramParser();

        IParseResult parseResult = parser.parseExpressionOrProgram(script, _symbolTable, parserOptions);
        _gosuProgram = parseResult.getProgram();
        _programInstance = _gosuProgram.getProgramInstance();
        ret = _programInstance.evaluate(new ExternalSymbolMapSymbolTableWrapper(_symbolTable));

        unloadSymbolTable(scriptContext);
      } catch (ParseResultsException e) {
          // TODO FIX
          throw new RuntimeException(e);
      }
      return ret;
    }

    @Override
    public Object eval(String script, ScriptContext context) throws ScriptException {
      return parseAndExecute(script, context);
    }

    @Override
    public Object eval(Reader reader, ScriptContext context) throws ScriptException {
      return eval(fromReader(reader), context);
    }

    private static final String fromReader(Reader reader) {
      StringBuilder sb = new StringBuilder();
      char[] cbuf = new char[1024];
      try {
          for (int numRead = 0; (numRead = reader.read(cbuf)) > 0;)
              sb.append(String.valueOf(cbuf, 0, numRead));
      } catch (IOException e) {
          // TODO temporary:
          throw new RuntimeException(e);
      }
      return sb.toString();
    }

    @Override
    public Bindings createBindings() {
      return new SimpleBindings();
    }

    @Override
    public ScriptEngineFactory getFactory() {
      // TODO: should this be memoized? Should I keep a reference from the constructor?
      return new GosuScriptEngineFactory();
    }

    private static IGosuProgramParser getParser() {
      return ParserHolder.GOSU_PARSER;
    }

  @Override
  public Object invokeMethod(Object thiz, String name, Object... args) throws ScriptException, NoSuchMethodException {
    return ReflectUtil.invokeMethod( thiz, name, args );
  }

  @Override
  public Object invokeFunction(String name, Object... args) throws ScriptException, NoSuchMethodException {
   try {
     for( Method m : _programInstance.getClass().getDeclaredMethods() ) {
       if( m.getName().equals( name ) ) { // TODO match signature also?
         Object[] args2 = new Object[args.length + 1];
         System.arraycopy( args, 0, args2, 1, args.length );
         args2[0] = new ExternalSymbolMapSymbolTableWrapper(_symbolTable);
         m.setAccessible( true );
         return m.invoke( _programInstance, args2 );
       }
     }
    } catch (Exception e) {
      throw new ScriptException(e);
    }
    throw new NoSuchMethodException("Could not find method named " +name);
  }

  @Override
  public <T> T getInterface(Class<T> clasz) {
    throw new UnsupportedOperationException("Not yet implemented.");
  }

  @Override
  public <T> T getInterface(Object thiz, Class<T> clasz) {
    throw new UnsupportedOperationException("Not yet implemented.");
  }

  // TODO: can we reuse a parser?
  private static class ParserHolder {
    private static final IGosuProgramParser GOSU_PARSER = GosuParserFactory.createProgramParser();
  }

}
