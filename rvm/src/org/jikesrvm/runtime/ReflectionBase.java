/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.runtime;

import org.vmmagic.pragma.Inline;
import org.vmmagic.pragma.NoInline;

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
    if (!(obj instanceof Boolean)) {
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
    if (!(obj instanceof Byte)) {
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
    if (!(obj instanceof Short) && !(obj instanceof Byte)) {
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
    if (!(obj instanceof Character)) {
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
    if (!(obj instanceof Integer) && !(obj instanceof Character) &&
        !(obj instanceof Short) && !(obj instanceof Byte)) {
      throw new IllegalArgumentException();
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
    if (!(obj instanceof Long) && !(obj instanceof Integer) &&
        !(obj instanceof Character) && (obj instanceof Short) &&
        !(obj instanceof Byte)) {
      throw new IllegalArgumentException();
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
    if (!(obj instanceof Float)) {
      throw new IllegalArgumentException();
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
    if (!(obj instanceof Double) && !(obj instanceof Float)) {
      throw new IllegalArgumentException();
    }
    return ((Number)obj).doubleValue();
  }

  /** Wrap result as double */
  @Inline
  protected static Object boxAsDouble(double d) {
    return d;
  }

  /**
   * Invoke reflective method being wrapped by this object
   * @param obj object for virtual method invocation
   * @param args arguments to method call
   * @return the object that is the result of the invoke
   */
  public abstract Object invoke(Object obj, Object[] args);

  /**
   * Reflective method invoker that performs no invocation
   */
  public static final ReflectionBase nullInvoker = new ReflectionBase() {
    @Override
    public Object invoke(Object obj, Object[] args) {return null;}
  };
}
