/*
 * Copyright (c) 1996, 2006, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package org.jikesrvm.classlibrary.openjdk.replacements;

import org.jikesrvm.VM;
import org.jikesrvm.classlibrary.JavaLangReflectSupport;
import org.jikesrvm.classloader.RVMType;
import org.jikesrvm.runtime.Magic;
import org.vmmagic.pragma.ReplaceClass;
import org.vmmagic.pragma.ReplaceMember;

@ReplaceClass(className = "java.lang.reflect.Array")
public class java_lang_reflect_Array {

  @ReplaceMember
  private static Object newArray(Class componentType, int length) throws NegativeArraySizeException {
    return JavaLangReflectSupport.createArray(componentType, length);
  }

  @ReplaceMember
  private static Object multiNewArray(Class componentType, int[] dimensions) throws IllegalArgumentException, NegativeArraySizeException {
    return JavaLangReflectSupport.createArray(componentType, dimensions);
  }

  private static void checkThatArgumentIsNonNullArray(Object array) {
    if (array == null) {
      throw new NullPointerException();
    }
    RVMType objectType = Magic.getObjectType(array);
    if (!objectType.isArrayType()) {
      throw new IllegalArgumentException("The argument " + array + " is not an array!");
    }
  }

  @ReplaceMember
  public static int getLength(Object array) throws IllegalArgumentException {
    checkThatArgumentIsNonNullArray(array);
    return Magic.getArrayLength(array);
  }



  @ReplaceMember
  public static Object get(Object array, int index)  throws IllegalArgumentException, ArrayIndexOutOfBoundsException {
    checkThatArgumentIsNonNullArray(array);
    if (array instanceof Object[]) {
      Object[] objArray = (Object[]) array;
      return objArray[index];
    } else if (array instanceof int[]) {
      int[] intArray = (int[]) array;
      return intArray[index];
    } else if (array instanceof long[]) {
      long[] longArray = (long[]) array;
      return longArray[index];
    } else if (array instanceof float[]) {
      float[] floatArray = (float[]) array;
      return floatArray[index];
    } else if (array instanceof double[]) {
      double[] doubleArray = (double[]) array;
      return doubleArray[index];
    } else if (array instanceof short[]) {
      short[] shortArray = (short[]) array;
      return shortArray[index];
    } else if (array instanceof char[]) {
      char[] charArray = (char[]) array;
      return charArray[index];
    } else if (array instanceof byte[]) {
      byte[] byteArray = (byte[]) array;
      return byteArray[index];
    } else if (array instanceof boolean[]) {
      boolean[] booleanArray = (boolean[]) array;
      return booleanArray[index];
    } else {
      if (VM.VerifyAssertions) {
        VM._assert(VM.NOT_REACHED, "Unknown array type: " + array.getClass());
      }
      VM.sysFail("Unknown array type: " + array.getClass());
    }

    return null;
  }

  @ReplaceMember
  public static boolean getBoolean(Object array, int index) throws IllegalArgumentException, ArrayIndexOutOfBoundsException {
    checkThatArgumentIsNonNullArray(array);
    if (array instanceof boolean[]) {
      boolean[] booleanArray = (boolean[]) array;
      return booleanArray[index];
    } else {
      throw new IllegalArgumentException("Array " + array + " isn't compatible with boolean");
    }
  }

  @ReplaceMember
  public static byte getByte(Object array, int index) throws IllegalArgumentException, ArrayIndexOutOfBoundsException {
    checkThatArgumentIsNonNullArray(array);
    if (array instanceof byte[]) {
      byte[] byteArray = (byte[]) array;
      return byteArray[index];
    } else {
      throw new IllegalArgumentException("Array " + array + " isn't compatible with byte");
    }
  }

  @ReplaceMember
  public static char getChar(Object array, int index) throws IllegalArgumentException, ArrayIndexOutOfBoundsException {
    checkThatArgumentIsNonNullArray(array);
    if (array instanceof char[]) {
      char[] charArray = (char[]) array;
      return charArray[index];
    } else {
      throw new IllegalArgumentException("Array " + array + " isn't compatible with char");
    }
  }

  @ReplaceMember
  public static short getShort(Object array, int index) throws IllegalArgumentException, ArrayIndexOutOfBoundsException {
    checkThatArgumentIsNonNullArray(array);
    if (array instanceof short[]) {
      short[] charArray = (short[]) array;
      return charArray[index];
    } else {
      throw new IllegalArgumentException("Array " + array + " isn't compatible with short");
    }
  }

  @ReplaceMember
  public static int getInt(Object array, int index) throws IllegalArgumentException, ArrayIndexOutOfBoundsException {
    if (array instanceof int[]) {
      int[] intArray = (int[]) array;
      return intArray[index];
    } else if (array instanceof byte[]) {
      byte[] byteArray = (byte[]) array;
      return byteArray[index];
    } else  if (array instanceof char[]) {
      char[] charArray = (char[]) array;
      return charArray[index];
    } else if (array instanceof short[]) {
      short[] charArray = (short[]) array;
      return charArray[index];
    } else {
      throw new IllegalArgumentException("Array " + array + " isn't compatible with int");
    }
  }

  @ReplaceMember
  public static long getLong(Object array, int index) throws IllegalArgumentException, ArrayIndexOutOfBoundsException {
    if (array instanceof long[]) {
      long[] intArray = (long[]) array;
      return intArray[index];
    } else if (array instanceof int[]) {
      int[] intArray = (int[]) array;
      return intArray[index];
    } else if (array instanceof byte[]) {
      byte[] byteArray = (byte[]) array;
      return byteArray[index];
    } else  if (array instanceof char[]) {
      char[] charArray = (char[]) array;
      return charArray[index];
    } else if (array instanceof short[]) {
      short[] charArray = (short[]) array;
      return charArray[index];
    } else {
      throw new IllegalArgumentException("Array " + array + " isn't compatible with long");
    }
  }

  @ReplaceMember
  public static float getFloat(Object array, int index) throws IllegalArgumentException, ArrayIndexOutOfBoundsException {
    if (array instanceof float[]) {
      float[] floatArray = (float[]) array;
      return floatArray[index];
    } else if (array instanceof long[]) {
      long[] intArray = (long[]) array;
      return intArray[index];
    } else if (array instanceof int[]) {
      int[] intArray = (int[]) array;
      return intArray[index];
    } else if (array instanceof byte[]) {
      byte[] byteArray = (byte[]) array;
      return byteArray[index];
    } else  if (array instanceof char[]) {
      char[] charArray = (char[]) array;
      return charArray[index];
    } else if (array instanceof short[]) {
      short[] charArray = (short[]) array;
      return charArray[index];
    } else {
      throw new IllegalArgumentException("Array " + array + " isn't compatible with float");
    }
  }

  @ReplaceMember
  public static double getDouble(Object array, int index) throws IllegalArgumentException, ArrayIndexOutOfBoundsException {
    if (array instanceof double[]) {
      double[] doubleArray = (double[]) array;
      return doubleArray[index];
    } else if (array instanceof float[]) {
      float[] floatArray = (float[]) array;
      return floatArray[index];
    } else if (array instanceof long[]) {
      long[] intArray = (long[]) array;
      return intArray[index];
    } else if (array instanceof int[]) {
      int[] intArray = (int[]) array;
      return intArray[index];
    } else if (array instanceof byte[]) {
      byte[] byteArray = (byte[]) array;
      return byteArray[index];
    } else  if (array instanceof char[]) {
      char[] charArray = (char[]) array;
      return charArray[index];
    } else if (array instanceof short[]) {
      short[] charArray = (short[]) array;
      return charArray[index];
    } else {
      throw new IllegalArgumentException("Array " + array + " isn't compatible with float");
    }
  }

  @ReplaceMember
  public static void set(Object array, int index, Object value) throws IllegalArgumentException, ArrayIndexOutOfBoundsException {
    checkThatArgumentIsNonNullArray(array);
    if (array instanceof Object[]) {
      Object[] objArray = (Object[]) array;
      objArray[index] = value;
    } else if (array instanceof int[]) {
      int[] intArray = (int[]) array;
      try {
        int i = (Integer) value;
        intArray[index] = i;
      } catch (ClassCastException e) {
        throw new IllegalArgumentException(e);
      }
    } else if (array instanceof long[]) {
      long[] longArray = (long[]) array;
      try {
        long l = (Long) value;
        longArray[index] = l;
      } catch (ClassCastException e) {
        throw new IllegalArgumentException(e);
      }
    } else if (array instanceof float[]) {
      float[] floatArray = (float[]) array;
      try {
        float f = (Float) value;
        floatArray[index] = f;
      } catch (ClassCastException e) {
        throw new IllegalArgumentException(e);
      }
    } else if (array instanceof double[]) {
      double[] doubleArray = (double[]) array;
      try {
        double d = (Double) value;
        doubleArray[index] = d;
      } catch (ClassCastException e) {
        throw new IllegalArgumentException(e);
      }
    } else if (array instanceof short[]) {
      short[] shortArray = (short[]) array;
      try {
        short s = (Short) value;
        shortArray[index] = s;
      } catch (ClassCastException e) {
        throw new IllegalArgumentException(e);
      }
    } else if (array instanceof char[]) {
      char[] charArray = (char[]) array;
      try {
        char c = (Character) value;
        charArray[index] = c;
      } catch (ClassCastException e) {
        throw new IllegalArgumentException(e);
      }
    } else if (array instanceof byte[]) {
      byte[] byteArray = (byte[]) array;
      try {
        byte b = (Byte) value;
        byteArray[index] = b;
      } catch (ClassCastException e) {
        throw new IllegalArgumentException(e);
      }
    } else if (array instanceof boolean[]) {
      boolean[] booleanArray = (boolean[]) array;
      try {
        boolean b = (Boolean) value;
        booleanArray[index] = b;
      } catch (ClassCastException e) {
        throw new IllegalArgumentException(e);
      }
    } else {
      if (VM.VerifyAssertions) {
        VM._assert(VM.NOT_REACHED, "Unknown array type: " + array.getClass());
      }
      VM.sysFail("Unknown array type: " + array.getClass());
    }
  }

  @ReplaceMember
  public static void setBoolean(Object array, int index, boolean z) throws IllegalArgumentException, ArrayIndexOutOfBoundsException {
    checkThatArgumentIsNonNullArray(array);
    if (array instanceof boolean[]) {
      boolean[] booleanArray = (boolean[]) array;
      booleanArray[index] = z;
    } else {
      throw new IllegalArgumentException("Array " + array + " isn't compatible with boolean");
    }
  }

  @ReplaceMember
  public static void setByte(Object array, int index, byte value) throws IllegalArgumentException, ArrayIndexOutOfBoundsException {
    checkThatArgumentIsNonNullArray(array);
    if (array instanceof Object[]) {
      Object[] objArray = (Object[]) array;
      objArray[index] = value;
    } else if (array instanceof int[]) {
      int[] intArray = (int[]) array;
      try {
        int i = value;
        intArray[index] = i;
      } catch (ClassCastException e) {
        throw new IllegalArgumentException(e);
      }
    } else if (array instanceof long[]) {
      long[] longArray = (long[]) array;
      try {
        long l = value;
        longArray[index] = l;
      } catch (ClassCastException e) {
        throw new IllegalArgumentException(e);
      }
    } else if (array instanceof float[]) {
      float[] floatArray = (float[]) array;
      try {
        float f = value;
        floatArray[index] = f;
      } catch (ClassCastException e) {
        throw new IllegalArgumentException(e);
      }
    } else if (array instanceof double[]) {
      double[] doubleArray = (double[]) array;
      try {
        double d = value;
        doubleArray[index] = d;
      } catch (ClassCastException e) {
        throw new IllegalArgumentException(e);
      }
    } else if (array instanceof short[]) {
      short[] shortArray = (short[]) array;
      try {
        short s = value;
        shortArray[index] = s;
      } catch (ClassCastException e) {
        throw new IllegalArgumentException(e);
      }
    } else if (array instanceof byte[]) {
      byte[] byteArray = (byte[]) array;
      try {
        byte b = value;
        byteArray[index] = b;
      } catch (ClassCastException e) {
        throw new IllegalArgumentException(e);
      }
    } else {
      throw new IllegalArgumentException("Array " + array + " isn't compatible with byte");
    }
  }

  @ReplaceMember
  public static void setChar(Object array, int index, char value) throws IllegalArgumentException, ArrayIndexOutOfBoundsException {
    checkThatArgumentIsNonNullArray(array);
    if (array instanceof Object[]) {
      Object[] objArray = (Object[]) array;
      objArray[index] = value;
    } else if (array instanceof int[]) {
      int[] intArray = (int[]) array;
      try {
        int i = value;
        intArray[index] = i;
      } catch (ClassCastException e) {
        throw new IllegalArgumentException(e);
      }
    } else if (array instanceof long[]) {
      long[] longArray = (long[]) array;
      try {
        long l = value;
        longArray[index] = l;
      } catch (ClassCastException e) {
        throw new IllegalArgumentException(e);
      }
    } else if (array instanceof float[]) {
      float[] floatArray = (float[]) array;
      try {
        float f = value;
        floatArray[index] = f;
      } catch (ClassCastException e) {
        throw new IllegalArgumentException(e);
      }
    } else if (array instanceof double[]) {
      double[] doubleArray = (double[]) array;
      try {
        double d = value;
        doubleArray[index] = d;
      } catch (ClassCastException e) {
        throw new IllegalArgumentException(e);
      }
    } else {
      throw new IllegalArgumentException("Array " + array + " isn't compatible with char");
    }
  }

  @ReplaceMember
  public static void setShort(Object array, int index, short value) throws IllegalArgumentException, ArrayIndexOutOfBoundsException {
    checkThatArgumentIsNonNullArray(array);
    if (array instanceof Object[]) {
      Object[] objArray = (Object[]) array;
      objArray[index] = value;
    } else if (array instanceof int[]) {
      int[] intArray = (int[]) array;
      try {
        int i = value;
        intArray[index] = i;
      } catch (ClassCastException e) {
        throw new IllegalArgumentException(e);
      }
    } else if (array instanceof long[]) {
      long[] longArray = (long[]) array;
      try {
        long l = value;
        longArray[index] = l;
      } catch (ClassCastException e) {
        throw new IllegalArgumentException(e);
      }
    } else if (array instanceof float[]) {
      float[] floatArray = (float[]) array;
      try {
        float f = value;
        floatArray[index] = f;
      } catch (ClassCastException e) {
        throw new IllegalArgumentException(e);
      }
    } else if (array instanceof double[]) {
      double[] doubleArray = (double[]) array;
      try {
        double d = value;
        doubleArray[index] = d;
      } catch (ClassCastException e) {
        throw new IllegalArgumentException(e);
      }
    } else if (array instanceof short[]) {
      short[] shortArray = (short[]) array;
      try {
        short s = value;
        shortArray[index] = s;
      } catch (ClassCastException e) {
        throw new IllegalArgumentException(e);
      }
    } else {
      throw new IllegalArgumentException("Array " + array + " isn't compatible with short");
    }
  }

  @ReplaceMember
  public static void setInt(Object array, int index, int value) throws IllegalArgumentException, ArrayIndexOutOfBoundsException {
    checkThatArgumentIsNonNullArray(array);
    if (array instanceof Object[]) {
      Object[] objArray = (Object[]) array;
      objArray[index] = value;
    } else if (array instanceof int[]) {
      int[] intArray = (int[]) array;
      try {
        int i = value;
        intArray[index] = i;
      } catch (ClassCastException e) {
        throw new IllegalArgumentException(e);
      }
    } else if (array instanceof long[]) {
      long[] longArray = (long[]) array;
      try {
        long l = value;
        longArray[index] = l;
      } catch (ClassCastException e) {
        throw new IllegalArgumentException(e);
      }
    } else if (array instanceof float[]) {
      float[] floatArray = (float[]) array;
      try {
        float f = value;
        floatArray[index] = f;
      } catch (ClassCastException e) {
        throw new IllegalArgumentException(e);
      }
    } else if (array instanceof double[]) {
      double[] doubleArray = (double[]) array;
      try {
        double d = value;
        doubleArray[index] = d;
      } catch (ClassCastException e) {
        throw new IllegalArgumentException(e);
      }
    } else {
      throw new IllegalArgumentException("Array " + array + " isn't compatible with int");
    }
  }

  @ReplaceMember
  public static void setLong(Object array, int index, long value) throws IllegalArgumentException, ArrayIndexOutOfBoundsException {
    checkThatArgumentIsNonNullArray(array);
    if (array instanceof Object[]) {
      Object[] objArray = (Object[]) array;
      objArray[index] = value;
    } else if (array instanceof long[]) {
      long[] longArray = (long[]) array;
      try {
        long l = value;
        longArray[index] = l;
      } catch (ClassCastException e) {
        throw new IllegalArgumentException(e);
      }
    } else if (array instanceof float[]) {
      float[] floatArray = (float[]) array;
      try {
        float f = value;
        floatArray[index] = f;
      } catch (ClassCastException e) {
        throw new IllegalArgumentException(e);
      }
    } else if (array instanceof double[]) {
      double[] doubleArray = (double[]) array;
      try {
        double d = value;
        doubleArray[index] = d;
      } catch (ClassCastException e) {
        throw new IllegalArgumentException(e);
      }
    } else {
      throw new IllegalArgumentException("Array " + array + " isn't compatible with long");
    }
  }

  @ReplaceMember
  public static void setFloat(Object array, int index, float value) throws IllegalArgumentException, ArrayIndexOutOfBoundsException {
    checkThatArgumentIsNonNullArray(array);
    if (array instanceof Object[]) {
      Object[] objArray = (Object[]) array;
      objArray[index] = value;
    } else if (array instanceof float[]) {
      float[] floatArray = (float[]) array;
      try {
        float f = value;
        floatArray[index] = f;
      } catch (ClassCastException e) {
        throw new IllegalArgumentException(e);
      }
    } else if (array instanceof double[]) {
      double[] doubleArray = (double[]) array;
      try {
        double d = value;
        doubleArray[index] = d;
      } catch (ClassCastException e) {
        throw new IllegalArgumentException(e);
      }
    } else {
      throw new IllegalArgumentException("Array " + array + " isn't compatible with float");
    }
  }


  @ReplaceMember
  public static void setDouble(Object array, int index, double value) throws IllegalArgumentException, ArrayIndexOutOfBoundsException {
    checkThatArgumentIsNonNullArray(array);
    if (array instanceof Object[]) {
      Object[] objArray = (Object[]) array;
      objArray[index] = value;
    } else if (array instanceof double[]) {
      double[] doubleArray = (double[]) array;
      try {
        double d = value;
        doubleArray[index] = d;
      } catch (ClassCastException e) {
        throw new IllegalArgumentException(e);
      }
    } else {
      throw new IllegalArgumentException("Array " + array + " isn't compatible with double");
    }
  }

}
