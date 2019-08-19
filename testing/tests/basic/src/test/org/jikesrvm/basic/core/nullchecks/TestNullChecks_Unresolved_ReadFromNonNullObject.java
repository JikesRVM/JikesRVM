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
package test.org.jikesrvm.basic.core.nullchecks;

import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Extent;
import org.vmmagic.unboxed.Offset;
import org.vmmagic.unboxed.Word;

/**
 * This class provides tests for null checks for reading unresolved fields
 * where the object that is being read from is non-null.
 */
public class TestNullChecks_Unresolved_ReadFromNonNullObject {

  public static void main(String[] args) {
    readObjectField(new ClassWithObjectField());
    readByteField(new ClassWithByteField());
    readBooleanField(new ClassWithBooleanField());
    readCharField(new ClassWithCharField());
    readShortField(new ClassWithShortField());
    readIntField(new ClassWithIntField());
    readLongField(new ClassWithLongField());
    readDoubleField(new ClassWithDoubleField());
    readFloatField(new ClassWithFloatField());
    readWordField(new ClassWithWordField());
    readAddressField(new ClassWithAddressField());
    readOffsetField(new ClassWithOffsetField());
    readExtentField(new ClassWithExtentField());

    readVolatileObjectField(new ClassWithVolatileObjectField());
    readVolatileByteField(new ClassWithVolatileByteField());
    readVolatileBooleanField(new ClassWithVolatileBooleanField());
    readVolatileCharField(new ClassWithVolatileCharField());
    readVolatileShortField(new ClassWithVolatileShortField());
    readVolatileIntField(new ClassWithVolatileIntField());
    readVolatileLongField(new ClassWithVolatileLongField());
    readVolatileDoubleField(new ClassWithVolatileDoubleField());
    readVolatileFloatField(new ClassWithVolatileFloatField());
    readVolatileWordField(new ClassWithVolatileWordField());
    readVolatileAddressField(new ClassWithVolatileAddressField());
    readVolatileOffsetField(new ClassWithVolatileOffsetField());
    readVolatileExtentField(new ClassWithVolatileExtentField());
  }

  private static byte readByteField(ClassWithByteField c) {
    try {
      byte read = c.b;
      System.out.println("Read value " + read + " from field");
      return read;
    } catch (NullPointerException npe) {
      System.out.println("Caught NPE when reading byte field");
      return NPE_BYTE;
    }
  }

  private static boolean readBooleanField(ClassWithBooleanField c) {
    try {
      boolean read = c.b;
      System.out.println("Read value " + read + " from field");
      return read;
    } catch (NullPointerException npe) {
      System.out.println("Caught NPE when reading boolean field");
      return NPE_BOOLEAN;
    }
  }

  private static char readCharField(ClassWithCharField c) {
    try {
      char read = c.c;
      System.out.println("Read value " + read + " from field");
      return read;
    } catch (NullPointerException npe) {
      System.out.println("Caught NPE when reading char field");
      return NPE_CHAR;
    }
  }

  private static short readShortField(ClassWithShortField c) {
    try {
      short read = c.s;
      System.out.println("Read value " + read + " from field");
      return read;
    } catch (NullPointerException npe) {
      System.out.println("Caught NPE when reading short field");
      return NPE_SHORT;
    }
  }

  private static int readIntField(ClassWithIntField c) {
    try {
      int read = c.i;
      System.out.println("Read value " + read + " from field");
      return read;
    } catch (NullPointerException npe) {
      System.out.println("Caught NPE when reading int field");
      return NPE_INT;
    }
  }

  private static long readLongField(ClassWithLongField c) {
    try {
      long read = c.l;
      System.out.println("Read value " + read + " from field");
      return read;
    } catch (NullPointerException npe) {
      System.out.println("Caught NPE when reading long field");
      return NPE_LONG;
    }
  }

  private static double readDoubleField(ClassWithDoubleField c) {
    try {
      double read = c.d;
      System.out.println("Read value " + read + " from field");
      return read;
    } catch (NullPointerException npe) {
      System.out.println("Caught NPE when reading double field");
      return NPE_DOUBLE;
    }
  }

  private static float readFloatField(ClassWithFloatField c) {
    try {
      float read = c.f;
      System.out.println("Read value " + read + " from field");
      return read;
    } catch (NullPointerException npe) {
      System.out.println("Caught NPE when reading float field");
      return NPE_FLOAT;
    }
  }

  private static Word readWordField(ClassWithWordField c) {
    try {
      Word read = c.w;
      System.out.println("Read value " + read.toLong() + " from field");
      return read;
    } catch (NullPointerException npe) {
      System.out.println("Caught NPE when reading Word field");
      return NPE_WORD;
    }
  }

  private static Address readAddressField(ClassWithAddressField c) {
    try {
      Address read = c.a;
      System.out.println("Read value " + read.toLong() + " from field");
      return read;
    } catch (NullPointerException npe) {
      System.out.println("Caught NPE when reading Address field");
      return NPE_ADDRESS;
    }
  }

  private static Offset readOffsetField(ClassWithOffsetField c) {
    try {
      Offset read = c.o;
      System.out.println("Read value " + read.toLong() + " from field");
      return read;
    } catch (NullPointerException npe) {
      System.out.println("Caught NPE when reading Offset field");
      return NPE_OFFSET;
    }
  }

  private static Extent readExtentField(ClassWithExtentField c) {
    try {
      Extent read = c.e;
      System.out.println("Read value " + read.toLong() + " from field");
      return read;
    } catch (NullPointerException npe) {
      System.out.println("Caught NPE when reading Extent field");
      return NPE_EXTENT;
    }
  }

  private static Object readObjectField(ClassWithObjectField c) {
    try {
      Object read = c.obj;
      System.out.println("Read object value from field");
      return read;
    } catch (NullPointerException npe) {
      System.out.println("Caught NPE when reading Object field");
      return NPE_OBJECT;
    }
  }

  private static byte readVolatileByteField(ClassWithVolatileByteField c) {
    try {
      byte readVolatile = c.b;
      System.out.println("Read value " + readVolatile + " from field");
      return readVolatile;
    } catch (NullPointerException npe) {
      System.out.println("Caught NPE when reading byte field");
      return NPE_BYTE;
    }
  }

  private static boolean readVolatileBooleanField(ClassWithVolatileBooleanField c) {
    try {
      boolean readVolatile = c.b;
      System.out.println("Read value " + readVolatile + " from field");
      return readVolatile;
    } catch (NullPointerException npe) {
      System.out.println("Caught NPE when reading boolean field");
      return NPE_BOOLEAN;
    }
  }

  private static char readVolatileCharField(ClassWithVolatileCharField c) {
    try {
      char readVolatile = c.c;
      System.out.println("Read value " + readVolatile + " from field");
      return readVolatile;
    } catch (NullPointerException npe) {
      System.out.println("Caught NPE when reading char field");
      return NPE_CHAR;
    }
  }

 private static short readVolatileShortField(ClassWithVolatileShortField c) {
    try {
      short readVolatile = c.s;
      System.out.println("Read value " + readVolatile + " from field");
      return readVolatile;
    } catch (NullPointerException npe) {
      System.out.println("Caught NPE when reading short field");
      return NPE_SHORT;
    }
  }

  private static int readVolatileIntField(ClassWithVolatileIntField c) {
    try {
      int readVolatile = c.i;
      System.out.println("Read value " + readVolatile + " from field");
      return readVolatile;
    } catch (NullPointerException npe) {
      System.out.println("Caught NPE when reading int field");
      return NPE_INT;
    }
  }

  private static long readVolatileLongField(ClassWithVolatileLongField c) {
    try {
      long readVolatile = c.l;
      System.out.println("Read value " + readVolatile + " from field");
      return readVolatile;
    } catch (NullPointerException npe) {
      System.out.println("Caught NPE when reading long field");
      return NPE_LONG;
    }
  }

  private static double readVolatileDoubleField(ClassWithVolatileDoubleField c) {
    try {
      double readVolatile = c.d;
      System.out.println("Read value " + readVolatile + " from field");
      return readVolatile;
    } catch (NullPointerException npe) {
      System.out.println("Caught NPE when reading double field");
      return NPE_DOUBLE;
    }
  }

  private static float readVolatileFloatField(ClassWithVolatileFloatField c) {
    try {
      float readVolatile = c.f;
      System.out.println("Read value " + readVolatile + " from field");
      return readVolatile;
    } catch (NullPointerException npe) {
      System.out.println("Caught NPE when reading float field");
      return NPE_FLOAT;
    }
  }

  private static Word readVolatileWordField(ClassWithVolatileWordField c) {
    try {
      Word readVolatile = c.w;
      System.out.println("Read value " + readVolatile.toLong() + " from field");
      return readVolatile;
    } catch (NullPointerException npe) {
      System.out.println("Caught NPE when reading Word field");
      return NPE_WORD;
    }
  }

  private static void readVolatileAddressField(ClassWithVolatileAddressField c) {
    try {
      Address readVolatile = c.a;;
      System.out.println("Read value " + readVolatile.toLong() + " from field");
    } catch (NullPointerException npe) {
      System.out.println("Caught NPE when reading Address field");
    }
  }

  private static Offset readVolatileOffsetField(ClassWithVolatileOffsetField c) {
    try {
      Offset readVolatile = c.o;
      System.out.println("Read value " + readVolatile.toLong() + " from field");
      return readVolatile;
    } catch (NullPointerException npe) {
      System.out.println("Caught NPE when reading Offset field");
      return NPE_OFFSET;
    }
  }

  private static Extent readVolatileExtentField(ClassWithVolatileExtentField c) {
    try {
      Extent readVolatile = c.e;
      System.out.println("Read value " + readVolatile.toLong() + " from field");
      return readVolatile;
    } catch (NullPointerException npe) {
      System.out.println("Caught NPE when reading Extent field");
      return NPE_EXTENT;
    }
  }

  private static Object readVolatileObjectField(ClassWithVolatileObjectField c) {
    try {
      Object readVolatile = c.obj;
      System.out.println("Read object value from field");
      return readVolatile;
    } catch (NullPointerException npe) {
      System.out.println("Caught NPE when reading Object field");
      return NPE_OBJECT;
    }
  }

  private static final Object DEFAULT_OBJECT = new Object();
  private static final byte DEFAULT_BYTE = 1;
  private static final boolean DEFAULT_BOOLEAN = false;
  private static final char DEFAULT_CHAR = 'h';
  private static final short DEFAULT_SHORT = 30000;
  private static final int DEFAULT_INT = 123456789;
  private static final long DEFAULT_LONG = 123456789101112L;
  private static final double DEFAULT_DOUBLE = 1.23456789E100d;
  private static final float DEFAULT_FLOAT = 1.23E4f;
  private static final Word DEFAULT_WORD = Word.fromIntZeroExtend(21);
  private static final Address DEFAULT_ADDRESS = Address.fromIntZeroExtend(12345);
  private static final Offset DEFAULT_OFFSET = Offset.fromIntZeroExtend(3);
  private static final Extent DEFAULT_EXTENT = Extent.fromIntZeroExtend(7);

  private static final Object NPE_OBJECT = new Object();
  private static final byte NPE_BYTE = -2;
  private static final boolean NPE_BOOLEAN = true;
  private static final char NPE_CHAR = 'z';
  private static final short NPE_SHORT = 123;
  private static final int NPE_INT = 45678910;
  private static final long NPE_LONG = 121110987654321L;
  private static final double NPE_DOUBLE = 0.23456789E100d;
  private static final float NPE_FLOAT = 0.23456789E10f;
  private static final Word NPE_WORD = Word.fromIntZeroExtend(11);
  private static final Address NPE_ADDRESS = Address.fromIntZeroExtend(6455);
  private static final Offset NPE_OFFSET = Offset.fromIntZeroExtend(5);
  private static final Extent NPE_EXTENT = Extent.fromIntZeroExtend(13);

  private static class ClassWithObjectField {
    ClassWithObjectField() {
      this.obj = DEFAULT_OBJECT;
    }
    public Object obj;
  }
  private static class ClassWithByteField {
    ClassWithByteField() {
      this.b = DEFAULT_BYTE;
    }
    public byte b;
  }
  private static class ClassWithBooleanField {
    ClassWithBooleanField() {
      this.b = DEFAULT_BOOLEAN;
    }
    public boolean b;
  }
  private static class ClassWithCharField {
    ClassWithCharField() {
      this.c = DEFAULT_CHAR;
    }
    public char c;
  }
  private static class ClassWithShortField {
    ClassWithShortField() {
      this.s = DEFAULT_SHORT;
    }
    public short s;
  }
  private static class ClassWithIntField {
    ClassWithIntField() {
      this.i = DEFAULT_INT;
    }
    public int i;
  }
  private static class ClassWithLongField {
    ClassWithLongField() {
      this.l = DEFAULT_LONG;
    }
    public long l;
  }
  private static class ClassWithDoubleField {
    ClassWithDoubleField() {
      this.d = DEFAULT_DOUBLE;
    }
    public double d;
  }
  private static class ClassWithFloatField {
    ClassWithFloatField() {
      this.f = DEFAULT_FLOAT;
    }
    public float f;
  }
  private static class ClassWithWordField {
    ClassWithWordField() {
      this.w = DEFAULT_WORD;
    }
    public Word w;
  }
  private static class ClassWithAddressField {
    ClassWithAddressField() {
      this.a = DEFAULT_ADDRESS;
    }
    public Address a;
  }
  private static class ClassWithOffsetField {
    ClassWithOffsetField() {
      this.o = DEFAULT_OFFSET;
    }
    public Offset o;
  }
  private static class ClassWithExtentField {
    ClassWithExtentField() {
      this.e = DEFAULT_EXTENT;
    }
    public Extent e;
  }
  private static class ClassWithVolatileObjectField {
    ClassWithVolatileObjectField() {
      this.obj = DEFAULT_OBJECT;
    }
    public volatile Object obj;
  }
  private static class ClassWithVolatileByteField {
    ClassWithVolatileByteField() {
      this.b = DEFAULT_BYTE;
    }
    public volatile byte b;
  }
  private static class ClassWithVolatileBooleanField {
    ClassWithVolatileBooleanField() {
      this.b = DEFAULT_BOOLEAN;
    }
    public volatile boolean b;
  }
  private static class ClassWithVolatileCharField {
    ClassWithVolatileCharField() {
      this.c = DEFAULT_CHAR;
    }
    public volatile char c;
  }
  private static class ClassWithVolatileShortField {
    ClassWithVolatileShortField() {
      this.s = DEFAULT_SHORT;
    }
    public volatile short s;
  }
  private static class ClassWithVolatileIntField {
    ClassWithVolatileIntField() {
      this.i = DEFAULT_INT;
    }
    public volatile int i;
  }
  private static class ClassWithVolatileLongField {
    ClassWithVolatileLongField() {
      this.l = DEFAULT_LONG;
    }
    public volatile long l;
  }
  private static class ClassWithVolatileDoubleField {
    ClassWithVolatileDoubleField() {
      this.d = DEFAULT_DOUBLE;
    }
    public volatile double d;
  }
  private static class ClassWithVolatileFloatField {
    ClassWithVolatileFloatField() {
      this.f = DEFAULT_FLOAT;
    }
    public volatile float f;
  }
  private static class ClassWithVolatileWordField {
    ClassWithVolatileWordField() {
      this.w = DEFAULT_WORD;
    }
    public volatile Word w;
  }
  private static class ClassWithVolatileAddressField {
    ClassWithVolatileAddressField() {
      this.a = DEFAULT_ADDRESS;
    }
    public volatile Address a;
  }
  private static class ClassWithVolatileOffsetField {
    ClassWithVolatileOffsetField() {
      this.o = DEFAULT_OFFSET;
    }
    public volatile Offset o;
  }
  private static class ClassWithVolatileExtentField {
    ClassWithVolatileExtentField() {
      this.e = DEFAULT_EXTENT;
    }
    public volatile Extent e;
  }

}
