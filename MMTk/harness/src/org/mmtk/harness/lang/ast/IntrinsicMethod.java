/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.mmtk.harness.lang.ast;

import java.util.ArrayList;
import java.util.List;

import org.mmtk.harness.lang.Env;
import org.mmtk.harness.lang.Trace;
import org.mmtk.harness.lang.Visitor;
import org.mmtk.harness.lang.Trace.Item;
import org.mmtk.harness.lang.runtime.BoolValue;
import org.mmtk.harness.lang.runtime.IntValue;
import org.mmtk.harness.lang.runtime.ObjectValue;
import org.mmtk.harness.lang.runtime.StringValue;
import org.mmtk.harness.lang.runtime.Value;
import org.mmtk.harness.lang.type.Type;

/**
 * A method that is implemented directly in Java rather than in the scripting language.
 *
 * This class also contains definitions of the built-in intrinsic methods.
 */
public class IntrinsicMethod extends Method {

  /** The actual Java method */
  private final java.lang.reflect.Method method;
  /** The Java signature of the method */
  private final Class<?>[] signature;

  /************************************************************************
   *                helper methods for the constructors
   *
   */

  /**
   * Find the appropriate scripting language type for a given Java type
   *
   * @param externalType The java type
   * @return
   */
  private static Type internalType(Class<?> externalType) {
    if (externalType.equals(int.class) ||
        externalType.equals(Integer.class) ||
        externalType.equals(IntValue.class)) {
      return Type.INT;
    } else if (externalType.equals(String.class) ||
        externalType.equals(StringValue.class)) {
      return Type.STRING;
    } else if (externalType.equals(ObjectValue.class)) {
      return Type.OBJECT;
    } else if (externalType.equals(boolean.class) ||
        externalType.equals(Boolean.class) ||
        externalType.equals(BoolValue.class)) {
      return Type.BOOLEAN;
    } else if (externalType.equals(void.class)) {
      return Type.VOID;
    }
    return null;
  }

  /**
   * Do a reflective method lookup.  We look for a method with a first parameter
   * of 'Env env', which is hidden from the script-language specification of the
   * method.
   *
   * @param className
   * @param methodName
   * @param signature
   * @return
   */
  private static java.lang.reflect.Method getJavaMethod(String className,
      String methodName, Class<?>[] signature) {
    try {
      Class<?> klass = Class.forName(className);
      Class<?>[] realSignature = new Class<?>[signature.length+1];
      realSignature[0] = Env.class;
      System.arraycopy(signature, 0, realSignature, 1, signature.length);
      return klass.getDeclaredMethod(methodName, realSignature);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Turn a string into a class object, applying some simple mappings for
   * primitives etc.
   * @param param
   * @return
   */
  private static Class<?> classForParam(String param) {
    Class<?> r;
    if (param.equals("int")) {
      r = int.class;
    } else if (param.equals("long")) {
      r = long.class;
    } else if (param.equals("byte")) {
      r = byte.class;
    } else if (param.equals("short")) {
      r = short.class;
    } else if (param.equals("char")) {
      r = char.class;
    } else if (param.equals("boolean")) {
      r = boolean.class;
    } else try {
      r = Class.forName(param);
    } catch (ClassNotFoundException e) {
      // As a last chance, try looking for the class in java.lang
      try {
        r = Class.forName("java.lang."+param);
      } catch (ClassNotFoundException f) {
        throw new RuntimeException(e);
      }
    }
    return r;
  }

  /**
   * Turn an array of strings (class names) into an array of Class objects
   * @param params
   * @return
   */
  private static Class<?>[] classesForParams(List<String> params) {
    Class<?>[] result = new Class<?>[params.size()];
    for (int i=0; i < params.size(); i++) {
      result[i] = classForParam(params.get(i));
    }
    return result;
  }


  /************************************************************************
   *
   *              Constructors
   *
   */

  /**
   * Constructor
   */
  public IntrinsicMethod(String name, String className, String methodName, List<String> params) {
    this(name,className, methodName,classesForParams(params));
  }

  /**
   * Create an intrinsic that calls a static method.
   * @param name Script language name of the method
   * @param className Java class in which the intrinsic occurs
   * @param methodName Java name of the method
   * @param signature Java types of the parameters, excluding the mandatory Env first parameter
   */
  public IntrinsicMethod(String name, String className, String methodName, Class<?>[] signature) {
    this(name,getJavaMethod(className, methodName,signature),signature);
  }

  /**
   * Create an intrinsic that calls a static method with no parameters
   *
   * @param name Script language name of the method
   * @param className Java class in which the intrinsic occurs
   * @param methodName Java name of the method
   */
  public IntrinsicMethod(String name, String className, String methodName) {
    this(name,className,methodName,new Class<?>[] {});
  }

  /**
   * Internal 'helper' constructor
   * @param name Script-language name of the method
   * @param method Java Method
   * @param signature Java signature (without the mandatory Env parameter)
   */
  private IntrinsicMethod(String name, java.lang.reflect.Method method, Class<?>[] signature) {
    this(name,method,internalType(method.getReturnType()),signature);
  }


  /**
   * Internal constructor - ultimately all constructors call this one.
   * @param name Script-language name of the method
   * @param method Java Method
   * @param returnType Script-language return type
   * @param signature Java signature (without the mandatory Env parameter)
   */
  private IntrinsicMethod(String name, java.lang.reflect.Method method, Type returnType, Class<?>[] signature) {
    super(name,signature.length,returnType);
    this.method = method;
    this.signature = signature;
  }

  /**
   * Marshall an array of values, adding the mandatory Env value.
   * @param env
   * @param values
   * @return
   */
  private Object[] marshall(Env env, Value[] values) {
    assert values.length == signature.length : "Signature doesn't match params";
    Object[] marshalled = new Object[values.length+1];
    marshalled[0] = env;
    for (int i=0; i < values.length; i++) {
      marshalled[i+1] = values[i].marshall(signature[i]);
    }
    return marshalled;
  }

  /**
   * Convert a return type from a Java type to a scripting language Value.
   * @param obj
   * @return
   */
  private Value unMarshall(Object obj) {
    if (returnType == Type.VOID) {
      return null;
    } else if (obj instanceof Integer) {
      assert returnType == Type.INT : "mismatched return types";
      return IntValue.valueOf(((Integer)obj).intValue());
    } else if (obj instanceof String) {
      assert returnType == Type.STRING : "mismatched return types";
      return new StringValue((String)obj);
    } else if (obj instanceof Boolean) {
      assert returnType == Type.BOOLEAN : "mismatched return types";
      return BoolValue.valueOf(((Boolean)obj).booleanValue());
    }
    throw new RuntimeException("Can't unmarshall a "+obj.getClass().getCanonicalName());
  }

  /**
   * Invoke the intrinsic method.
   * @param env
   * @param values
   * @return
   */
  Object invoke(Env env, Value[] values) {
    Trace.trace(Item.INTRINSIC,"Executing "+toString());
    try {
      Object result = method.invoke(null, marshall(env,values));
      return result;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Evaluate the method as an expression
   * @see Method{@link #eval(Env, Value...)}
   *
   * Also used by the visitor to evaluate ...
   */
  public Value eval(Env env, Value...values) {
    return unMarshall(invoke(env,values));
  }

  /**
   * Execute the method as a statement
   */
  public void exec(Env env, Value...values) {
    invoke(env,values);
  }

  /**
   * Convert to a string
   */
  @Override
  public String toString() {
    return method.getName();
  }

  /**
   * Accept visitors
   */
  @Override
  public Object accept(Visitor v) {
    return v.visit(this);
  }

  /**
   * Return parameter types, in script-language terms.
   */
  @Override
  public List<Type> getParamTypes() {
    List<Type> result = new ArrayList<Type>(params);
    for (Class<?> paramClass : signature) {
      result.add(internalType(paramClass));
    }
    return result;
  }

}
