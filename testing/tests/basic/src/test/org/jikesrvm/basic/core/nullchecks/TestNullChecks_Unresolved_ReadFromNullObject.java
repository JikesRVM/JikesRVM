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
 * where the object that is being read from is {@code null}.
 */
public class TestNullChecks_Unresolved_ReadFromNullObject {

  public static void main(String[] args) {
    readObjectField(null);
    readByteField(null);
    readBooleanField(null);
    readCharField(null);
    readShortField(null);
    readIntField(null);
    readLongField(null);
    readDoubleField(null);
    readFloatField(null);
    readWordField(null);
    readAddressField(null);
    readOffsetField(null);
    readExtentField(null);

    readVolatileObjectField(null);
    readVolatileByteField(null);
    readVolatileBooleanField(null);
    readVolatileCharField(null);
    readVolatileShortField(null);
    readVolatileIntField(null);
    readVolatileLongField(null);
    readVolatileDoubleField(null);
    readVolatileFloatField(null);
    readVolatileWordField(null);
    readVolatileAddressField(null);
    readVolatileOffsetField(null);
    readVolatileExtentField(null);
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
    public Object obj;
  }
  private static class ClassWithByteField {
    public byte b;
  }
  private static class ClassWithBooleanField {
    public boolean b;
  }
  private static class ClassWithCharField {
    public char c;
  }
  private static class ClassWithShortField {
    public short s;
  }
  private static class ClassWithIntField {
    public int i;
  }
  private static class ClassWithLongField {
    public long l;
  }
  private static class ClassWithDoubleField {
    public double d;
  }
  private static class ClassWithFloatField {
    public float f;
  }
  private static class ClassWithWordField {
    public Word w;
  }
  private static class ClassWithAddressField {
    public Address a;
  }
  private static class ClassWithOffsetField {
    public Offset o;
  }
  private static class ClassWithExtentField {
    public Extent e;
  }
  private static class ClassWithVolatileObjectField {
    public volatile Object obj;
  }
  private static class ClassWithVolatileByteField {
    public volatile byte b;
  }
  private static class ClassWithVolatileBooleanField {
    public volatile boolean b;
  }
  private static class ClassWithVolatileCharField {
    public volatile char c;
  }
  private static class ClassWithVolatileShortField {
    public volatile short s;
  }
  private static class ClassWithVolatileIntField {
    public volatile int i;
  }
  private static class ClassWithVolatileLongField {
    public volatile long l;
  }
  private static class ClassWithVolatileDoubleField {
    public volatile double d;
  }
  private static class ClassWithVolatileFloatField {
    public volatile float f;
  }
  private static class ClassWithVolatileWordField {
    public volatile Word w;
  }
  private static class ClassWithVolatileAddressField {
    public volatile Address a;
  }
  private static class ClassWithVolatileOffsetField {
    public volatile Offset o;
  }
  private static class ClassWithVolatileExtentField {
    public volatile Extent e;
  }

}
