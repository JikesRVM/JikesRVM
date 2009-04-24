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
package org.jikesrvm.runtime;

import org.vmmagic.pragma.Inline;
import org.vmmagic.pragma.NoInline;
import org.jikesrvm.classloader.RVMMethod;
import java.lang.reflect.InvocationTargetException;

/**
 * Base class for all reflective method invoker classes, contains utility
 * methods that are invoked to unwrap the reflective arguments. Also contains
 * {@link #invoke(Object, Object[])} that is called to perform the reflective
 * method call.
 */
public abstract class ReflectionBase {

  /** Throw error with argument */
  @NoInline
  private static void throwIllegalArgumentException() throws IllegalArgumentException {
    throw new IllegalArgumentException();
  }

  /** Unwrap boolean for call */
  @Inline
  protected static boolean unboxAsBoolean(Object obj) {
    if ((obj == null) || !(obj instanceof Boolean)) {
      throwIllegalArgumentException();
    }
    return ((Boolean)obj).booleanValue();
  }

  /** Wrap result as boolean */
  @Inline
  protected static Object boxAsBoolean(boolean b) {
    return b;
  }

  /** Unwrap boolean for call */
  @Inline
  protected static byte unboxAsByte(Object obj) {
    if ((obj == null) || !(obj instanceof Byte)) {
      throwIllegalArgumentException();
    }
    return ((Byte)obj).byteValue();
  }

  /** Wrap result as byte */
  @Inline
  protected static Object boxAsByte(byte b) {
    return b;
  }

  /** Unwrap short for call */
  @Inline
  protected static short unboxAsShort(Object obj) {
    if ((obj == null) || (!(obj instanceof Short) && !(obj instanceof Byte))) {
      throwIllegalArgumentException();
    }
    return ((Number)obj).shortValue();
  }

  /** Wrap result as short */
  @Inline
  protected static Object boxAsShort(short s) {
    return s;
  }

  /** Unwrap char for call */
  @Inline
  protected static char unboxAsChar(Object obj) {
    if ((obj == null) || !(obj instanceof Character)) {
      throwIllegalArgumentException();
    }
    return ((Character)obj).charValue();
  }

  /** Wrap result as char */
  @Inline
  protected static Object boxAsChar(char c) {
    return c;
  }

  /** Unwrap int for call */
  @Inline
  protected static int unboxAsInt(Object obj) {
    if ((obj == null) ||
        (!(obj instanceof Integer) && !(obj instanceof Character) &&
         !(obj instanceof Short) && !(obj instanceof Byte))) {
      throwIllegalArgumentException();
    }
    return ((Number)obj).intValue();
  }

  /** Wrap result as int */
  @Inline
  protected static Object boxAsInt(int i) {
    return i;
  }

  /** Unwrap long for call */
  @Inline
  protected static long unboxAsLong(Object obj) {
    if ((obj == null) ||
        (!(obj instanceof Long) && !(obj instanceof Integer) &&
         !(obj instanceof Character) && (obj instanceof Short) &&
         !(obj instanceof Byte))) {
      throwIllegalArgumentException();
    }
    return ((Number)obj).longValue();
  }

  /** Wrap result as long */
  @Inline
  protected static Object boxAsLong(long l) {
    return l;
  }

  /** Unwrap float for call */
  @Inline
  protected static float unboxAsFloat(Object obj) {
    if ((obj == null) || !(obj instanceof Float)) {
      throwIllegalArgumentException();
    }
    return ((Float)obj).floatValue();
  }

  /** Wrap result as float */
  @Inline
  protected static Object boxAsFloat(float f) {
    return f;
  }

  /** Unwrap double for call */
  @Inline
  protected static double unboxAsDouble(Object obj) {
    if ((obj == null) ||
        (!(obj instanceof Double) && !(obj instanceof Float))) {
      throwIllegalArgumentException();
    }
    return ((Number)obj).doubleValue();
  }

  /** Wrap result as double */
  @Inline
  protected static Object boxAsDouble(double d) {
    return d;
  }

  /**
   * Invoke reflective method being wrapped by this object, internal method
   * specific part.
   * @param obj object for virtual method invocation
   * @param args arguments to method call
   * @return the object that is the result of the invoke
   */
  public abstract Object invokeInternal(Object obj, Object[] args);

  /**
   * Invoke reflective method being wrapped by this object
   * @param obj object for virtual method invocation
   * @param args arguments to method call
   * @return the object that is the result of the invoke
   */
  public final Object invoke(RVMMethod method, Object obj, Object[] args) throws InvocationTargetException {
    int argsLength = args == null ? 0 : args.length;
    if (method.getParameterTypes().length != argsLength) {
      throwIllegalArgumentException();
    }
    Throwable x;
    try {
      return invokeInternal(obj, args);
    } catch (IllegalArgumentException e) {
      x = e;
    } catch (NullPointerException e) {
      x = e;
    } catch (ClassCastException e) {
      x = e;
    } catch (Exception e) {
      throw new InvocationTargetException(e);
    }
    // was this caused by casting an argument or in the invoked method?
    // TODO: this test can give false positives
    if (x.getStackTrace().length == (new Throwable()).getStackTrace().length+6) {
      // error in invocation
      if (x instanceof ClassCastException) {
        throw new IllegalArgumentException("argument type mismatch", x);
      } else if (x instanceof NullPointerException) {
        throw (NullPointerException)x;
      } else {
        throw (IllegalArgumentException)x;
      }
    } else {
      throw new InvocationTargetException(x);
    }
  }

  /**
   * Reflective method invoker that performs no invocation
   */
  public static final ReflectionBase nullInvoker = new ReflectionBase() {
    @Override
    public Object invokeInternal(Object obj, Object[] args) {return null;}
  };
}
