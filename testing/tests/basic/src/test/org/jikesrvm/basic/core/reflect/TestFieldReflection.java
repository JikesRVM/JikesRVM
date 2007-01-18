/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
//$Id: /jikesrvm/local/testing/tests/reflect/src/TestFieldReflection.java 10522 2006-11-14T22:42:56.816831Z dgrove-oss  $
package test.org.jikesrvm.basic.core.reflect;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Comparator;

/**
 * @author unascribed
 */
class TestFieldReflection {

  static class TypeWithPublicFields {
    public static boolean sboolean = true;
    public static byte sbyte = 127;
    public static short sshort = 1;
    public static int sint = 1;
    public static long slong = 1;
    public static float sfloat = 1.0F;
    public static double sdouble = 1.0;

    public boolean mboolean = true;
    public byte mbyte = 127;
    public short mshort = 1;
    public int mint = 1;
    public long mlong = 1;
    public float mfloat = 1.0F;
    public double mdouble = 1.0;
  }

  static class TypeWithDifferentAccessModifiers {
    public static boolean sboolean = true;
    protected static byte sbyte = 127;
    static short sshort = 1;
    private static int sint = 1;
    public static long slong = 1;
    protected static float sfloat = 1.0F;
    static double sdouble = 1.0;

    private boolean mboolean = true;
    byte mbyte = 127;
    protected short mshort = 1;
    public int mint = 1;
    protected long mlong = 1;
    float mfloat = 1.0F;
    private double mdouble = 1.0;
  }

  public static void main(String args[]) throws Exception {
    testFieldReflection(TypeWithPublicFields.class);
    testFieldReflection(TypeWithDifferentAccessModifiers.class);
  }

  private static void testFieldReflection(final Class<?> type) throws Exception {
    System.out.println("Testing on getFields");
    testFieldReflection(type.newInstance(), type, type.getFields());

    System.out.println("Testing on getDeclaredFields()");
    testFieldReflection(type.newInstance(), type, type.getDeclaredFields());
  }

  private static void testFieldReflection(final Object object, final Class<?> type, final Field[] fields) throws Exception {
    Arrays.sort(fields, StringComparator.COMPARATOR);

    printFields(object, fields);

    testBoolean(object, fields);
    printFields(object, fields);

    testByte(object, fields);
    printFields(object, fields);

    testShort(object, fields);
    printFields(object, fields);

    testInt(object, fields);
    printFields(object, fields);

    testLong(object, fields);
    printFields(object, fields);

    testFloat(object, fields);
    printFields(object, fields);

    testDouble(object, fields);
    printFields(object, fields);

    testType(type, "sboolean", boolean.class);
    testType(type, "sbyte", byte.class);
    testType(type, "sshort", short.class);
    testType(type, "sint", int.class);
    testType(type, "slong", long.class);
    testType(type, "sfloat", float.class);
    testType(type, "sdouble", double.class);
    testType(type, "mboolean", boolean.class);
    testType(type, "mbyte", byte.class);
    testType(type, "mshort", short.class);
    testType(type, "mint", int.class);
    testType(type, "mlong", long.class);
    testType(type, "mfloat", float.class);
    testType(type, "mdouble", double.class);
  }

  private static void testType(final Class<?> type, final String fieldName, final Class expected) throws Exception {
    try {
      final Class<?> f = type.getDeclaredField(fieldName).getType();
      if (!expected.equals(f)) System.out.println(expected + " is not equal to " + f);
    } catch (NoSuchFieldException e) {
      System.out.println("testType: Unable to retrieve field " + fieldName + " from class " + type.getName());
    }
  }

  private static void printFields(final Object object, final Field[] fields) throws Exception {
    System.out.println();
    System.out.println("** Current value of fields is **");
    System.out.println();
    for (int i = 0; i < fields.length; i++) {
      String value = "";
      final Field field = fields[i];
      try {
        value = String.valueOf(field.get(object));
      } catch (IllegalArgumentException e) {
        value = "<unknown> IllegalArgumentException when getting field.";
      } catch (IllegalAccessException e) {
        value = "<unknown> IllegalAccessException when getting field.";
      }
      final String message = i + "  " + describeField(field) + " = " + value;
      System.out.println(message);
    }
  }

  private static void testBoolean(final Object object, final Field[] fields) throws Exception {
    banner("boolean", "false");
    for (int i = 0; i < fields.length; i++) {
      final Field field = fields[i];
      try {
        field.setBoolean(object, false);
        changeSuccess(field, object);
      } catch (Exception e) { changeError(field, e); }
    }
  }

  private static void testByte(final Object object, final Field[] fields) throws Exception {
    banner("byte", "12");
    for (int i = 0; i < fields.length; i++) {
      final Field field = fields[i];
      try {
        field.setByte(object, (byte) 12);
        changeSuccess(field, object);
      } catch (Exception e) { changeError(field, e); }
    }
  }

  private static void testShort(final Object object, final Field[] fields) throws Exception {
    banner("short", "2");
    for (int i = 0; i < fields.length; i++) {
      final Field field = fields[i];
      try {
        field.setShort(object, (short) 2);
        changeSuccess(field, object);
      } catch (Exception e) { changeError(field, e); }
    }
  }

  private static void testInt(final Object object, final Field[] fields) throws Exception {
    banner("int", "3");
    for (int i = 0; i < fields.length; i++) {
      final Field field = fields[i];
      try {
        field.setInt(object, 3);
        changeSuccess(field, object);
      } catch (Exception e) { changeError(field, e); }
    }
  }

  private static void testLong(final Object object, final Field[] fields) throws Exception {
    banner("long", "4");
    for (int i = 0; i < fields.length; i++) {
      final Field field = fields[i];
      try {
        field.setLong(object, 4);
        changeSuccess(field, object);
      } catch (Exception e) { changeError(field, e); }
    }
  }

  private static void testFloat(final Object object, Field[] fields) throws Exception {
    banner("float", "5");
    for (int i = 0; i < fields.length; i++) {
      final Field field = fields[i];
      try {
        field.setFloat(object, 5.0F);
        changeSuccess(field, object);
      } catch (Exception e) { changeError(field, e); }
    }
  }

  private static void testDouble(final Object object, Field[] fields) throws Exception {
    banner("double", "6.0");
    for (int i = 0; i < fields.length; i++) {
      final Field field = fields[i];
      try {
        field.setDouble(object, 6.0);
        changeSuccess(field, object);
      } catch (Exception e) { changeError(field, e); }
    }
  }

  private static void banner(final String typename, final Object value) {
    System.out.println();
    System.out.println("** Set " + typename + "s to " + value + " **");
    System.out.println();
  }

  private static void changeSuccess(final Field field, final Object object) throws IllegalAccessException {
    System.out.println("OK: " + describeField(field) + " = " + field.get(object));
  }

  private static void changeError(final Field field, final Exception e) {
    System.out.println("ERR: " + describeField(field) + " due to " + e.getClass().getName());
  }

  //Classpath and JDK differ in how they display fields with package access thus this is used to display fields
  private static String describeField(final Field field) {
    final int modifiers = field.getModifiers();
    return (((modifiers == 0) ? "" : (Modifier.toString(modifiers) + " ")) +
        field.getType().getName() + " " + field.getDeclaringClass().getName() + "." + field.getName());
  }

  private static class StringComparator implements Comparator<Object> {
    static StringComparator COMPARATOR = new StringComparator();

    public int compare(final Object x, final Object y) {
      return x.toString().compareTo(y.toString());
    }
  }
}

