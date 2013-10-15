package com.github.gosu.jsr223;

import gw.lang.GosuShop;
import gw.lang.parser.ExternalSymbolMapSymbolTableWrapper;
import gw.lang.parser.GosuParserFactory;
import gw.lang.parser.IGosuProgramParser;
import gw.lang.parser.IParseResult;
import gw.lang.parser.ISymbolTable;
import gw.lang.parser.ParserOptions;
import gw.lang.parser.exceptions.ParseResultsException;
import gw.lang.reflect.IPropertyInfo;
import gw.lang.reflect.TypeSystem;
import gw.lang.reflect.gs.IGosuProgram;
import gw.lang.reflect.gs.IProgramInstance;

import javax.script.Bindings;
import javax.script.CompiledScript;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Gosu implementation of CompiledScript, part of JSR-223.
 */
public class GosuCompiledScript extends CompiledScript {
  private final IGosuProgram     _gosuProgram;
  private final IProgramInstance _programInstance;
  private final ISymbolTable     _symbolTable;
  private final ScriptEngine     _engine;

  GosuCompiledScript(String script, ScriptContext scriptContext, ScriptEngine engine) throws ScriptException {
    try {
      _engine = engine;

      // splitting to multiple lines to ease stack inspection
      ParserOptions parserOptions = new ParserOptions();

      _symbolTable = getSymbolTable(scriptContext);

      // TODO: if parser construction is expensive, create a thread-safe pool.
      IGosuProgramParser parser = GosuParserFactory.createProgramParser();

      // TODO: redirect parse errors to scriptContext.getErrWriter()
      IParseResult parseResult = parser.parseExpressionOrProgram(script, _symbolTable, parserOptions);
      _gosuProgram = parseResult.getProgram();
      _programInstance = _gosuProgram.getProgramInstance();
    } catch (ParseResultsException pre) {
      throw new ScriptException(pre);
    }
  }

  @Override
  public ScriptEngine getEngine() {
    return _engine;
  }

  @Override
  public Object eval(ScriptContext context) throws ScriptException {
    ISymbolTable localSymbolTable = getSymbolTable(context);

    Object ret = _programInstance.evaluate(new ExternalSymbolMapSymbolTableWrapper(localSymbolTable));
    unloadSymbolTable(context);

    return ret;
  }

  Object invokeFunction(String name, Object... args) throws ScriptException, NoSuchMethodException {
    try {
      args = functionArgs(args);
      final Method method = findMethodByNameAndArgs(_programInstance.getClass(), name, args);
      return method.invoke(_programInstance, args);
    } catch (NoSuchMethodException nsme) {
      throw nsme;
    } catch (Exception e) {
      throw new ScriptException(e);
    }
  }

  Object invokeMethod(Object thiz, String name, Object... args) throws ScriptException, NoSuchMethodException {
    try {
      final Method method = findMethodByNameAndArgs(thiz.getClass(), name, args);
      return method.invoke(thiz, args);
    } catch (NoSuchMethodException nsme) {
      throw nsme;
    } catch (Exception e) {
      throw new ScriptException(e);
    }
  }

  <T> T getInterface(Class<T> clasz) {
    return (T) Proxy.newProxyInstance(clasz.getClassLoader(),
           new Class[]{clasz},
           new InvocableHandler(_programInstance));
  }

  <T> T getInterface(Object thiz, Class<T> clasz) {
    return (T) Proxy.newProxyInstance(clasz.getClassLoader(),
           new Class[] {clasz},
           new InvocableHandler(thiz));
  }

  private static Method findMethodByNameAndArgs(Class target, String name, Object[] args) throws NoSuchMethodException {
    final int numArgs = args == null ? 0 : args.length;
    for( Method m : target.getDeclaredMethods() ) {
      if( m.getName().equals( name ) ) {
        // match length and types in parameter list
        final Class<?>[] formalParams = m.getParameterTypes();
        if (formalParams.length != numArgs) continue;

        for (int i = 0; i < formalParams.length; ++i) {
          if (! formalParams[i].isAssignableFrom(args[i].getClass())) {
            continue;
          }
        }

        return m;
      }
    }

    throw new NoSuchMethodException("Could not find method named " +name);
  }

  class InvocableHandler implements InvocationHandler {
    final Object _target;

    InvocableHandler(Object target) {
      _target = target;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
      if (_target == _programInstance) {
        return invokeFunction(method.getName(), args);
      } else {
        return invokeMethod(_target, method.getName(), args);
      }
    }
  };

  private Object[] functionArgs(Object[] args) {
    if (args == null) {
      return new Object[] { new ExternalSymbolMapSymbolTableWrapper(_symbolTable) };
    }
    Object[] args2 = new Object[args.length + 1];
    System.arraycopy( args, 0, args2, 1, args.length );
    args2[0] = new ExternalSymbolMapSymbolTableWrapper(_symbolTable);
    return args2;
  }


  private ISymbolTable getSymbolTable(ScriptContext context) {
    ISymbolTable result = GosuShop.createSymbolTable();

    if (context != null) {
      loadSymbolTable(context, result);
    }

    return result;
  }

  private void loadSymbolTable(ScriptContext context, ISymbolTable result) {
    Bindings globalBindings = context.getBindings(ScriptContext.GLOBAL_SCOPE);
    if (globalBindings != null) {
      loadSymbolsFromBindings(result, globalBindings);
      result.pushScope();
    }
    Bindings bindings = context.getBindings(ScriptContext.ENGINE_SCOPE);
    if (bindings != null) {
      loadSymbolsFromBindings(result, bindings);
    }
  }

  private void loadSymbolsFromBindings(ISymbolTable result, Bindings bindings) {
    for (String k : bindings.keySet()) {
      Object v = bindings.get(k);
      result.putSymbol(GosuShop.createSymbol(k, TypeSystem.get(v.getClass()), v));
    }
  }

  // These show up in the propertyinfo list but shouldn't be returned.
  private static final Set<String> filterSymbols = new HashSet<String>();
  static {
    filterSymbols.add("IntrinsicType");
    filterSymbols.add("Class");
  }

  private void unloadSymbolTable(ScriptContext context) {
    if (context == null) return;

    final List<? extends IPropertyInfo> properties = _gosuProgram.getTypeInfo().getProperties(_gosuProgram);
    for (IPropertyInfo p : properties) {
      if (filterSymbols.contains(p.getName())) continue;

      // TODO: is it possible to overwrite something that is in the global scope?  Or does var foo always go to the local scope?
      context.getBindings(ScriptContext.ENGINE_SCOPE).put(p.getDisplayName(), p.getAccessor().getValue(_programInstance));
    }
  }
}
