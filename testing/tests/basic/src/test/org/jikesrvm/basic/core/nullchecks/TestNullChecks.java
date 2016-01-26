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
 * This class provides a few simple tests for null checks.
 */
public class TestNullChecks {

  public static void main(String[] args) {
    invokeHashCodeOnObject(new Object());
    invokeHashCodeOnObject(null);
    invokeHashCodeOnObject(new ClassWithByteField());
    invokeHashCodeOnObject(new int[0]);
    readByteField(null);
    readByteField(new ClassWithByteField());
    writeByteField(null, WRITE_BYTE);
    writeByteField(new ClassWithByteField(), WRITE_BYTE);
    readBooleanField(null);
    readBooleanField(new ClassWithBooleanField());
    writeBooleanField(null, WRITE_BOOLEAN);
    writeBooleanField(new ClassWithBooleanField(), WRITE_BOOLEAN);
    readCharField(null);
    readCharField(new ClassWithCharField());
    writeCharField(null, WRITE_CHAR);
    writeCharField(new ClassWithCharField(), WRITE_CHAR);
    readShortField(null);
    readShortField(new ClassWithShortField());
    writeShortField(null, WRITE_SHORT);
    writeShortField(new ClassWithShortField(), WRITE_SHORT);
    readIntField(null);
    readIntField(new ClassWithIntField());
    writeIntField(null, WRITE_INT);
    writeIntField(new ClassWithIntField(), WRITE_INT);
    readLongField(null);
    readLongField(new ClassWithLongField());
    writeLongField(null, WRITE_LONG);
    writeLongField(new ClassWithLongField(), WRITE_LONG);
    readDoubleField(null);
    readDoubleField(new ClassWithDoubleField());
    writeDoubleField(null, WRITE_DOUBLE);
    writeDoubleField(new ClassWithDoubleField(), WRITE_DOUBLE);
    readFloatField(null);
    readFloatField(new ClassWithFloatField());
    writeFloatField(null, WRITE_FLOAT);
    writeFloatField(new ClassWithFloatField(), WRITE_FLOAT);
    readWordField(null);
    readWordField(new ClassWithWordField());
    writeWordField(null, WRITE_WORD);
    writeWordField(new ClassWithWordField(), WRITE_WORD);
    readAddressField(null);
    readAddressField(new ClassWithAddressField());
    writeAddressField(null, WRITE_ADDRESS);
    writeAddressField(new ClassWithAddressField(), WRITE_ADDRESS);
    readOffsetField(null);
    readOffsetField(new ClassWithOffsetField());
    writeOffsetField(null, WRITE_OFFSET);
    writeOffsetField(new ClassWithOffsetField(), WRITE_OFFSET);
    readExtentField(null);
    readExtentField(new ClassWithExtentField());
    writeExtentField(null, WRITE_EXTENT);
    writeExtentField(new ClassWithExtentField(), WRITE_EXTENT);
    synchronizeOnObject(null);
    synchronizeOnObject(new Object());
  }

  private static void invokeHashCodeOnObject(Object o) {
    try {
      int hc = o.hashCode();
      System.out.println("Successfully invoked hashCode() on object");
    } catch (NullPointerException npe) {
      System.out.println("Caught NPE while trying to invoke hashCode()");
    }
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

  private static void writeByteField(ClassWithByteField c, byte newValue) {
    try {
      c.b = newValue;
      System.out.println("Wrote value " + c.b + " to field");
    } catch (NullPointerException npe) {
      System.out.println("Caught NPE when writing byte field");
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

  private static void writeBooleanField(ClassWithBooleanField c, boolean newValue) {
    try {
      c.b = newValue;
      System.out.println("Wrote value " + c.b + " to field");
    } catch (NullPointerException npe) {
      System.out.println("Caught NPE when writing boolean field");
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

  private static void writeCharField(ClassWithCharField c, char newValue) {
    try {
      c.c = newValue;
      System.out.println("Wrote value " + c.c + " to field");
    } catch (NullPointerException npe) {
      System.out.println("Caught NPE when writing char field");
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

  private static void writeShortField(ClassWithShortField c, short newValue) {
    try {
      c.s = newValue;
      System.out.println("Wrote value " + c.s + " to field");
    } catch (NullPointerException npe) {
      System.out.println("Caught NPE when writing short field");
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

  private static void writeIntField(ClassWithIntField c, int newValue) {
    try {
      c.i = newValue;
      System.out.println("Wrote value " + c.i + " to field");
    } catch (NullPointerException npe) {
      System.out.println("Caught NPE when writing int field");
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

  private static void writeLongField(ClassWithLongField c, long newValue) {
    try {
      c.l = newValue;
      System.out.println("Wrote value " + c.l + " to field");
    } catch (NullPointerException npe) {
      System.out.println("Caught NPE when writing long field");
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

  private static void writeDoubleField(ClassWithDoubleField c, double newValue) {
    try {
      c.d = newValue;
      System.out.println("Wrote value " + c.d + " to field");
    } catch (NullPointerException npe) {
      System.out.println("Caught NPE when writing double field");
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

  private static void writeFloatField(ClassWithFloatField c, float newValue) {
    try {
      c.f = newValue;
      System.out.println("Wrote value " + c.f + " to field");
    } catch (NullPointerException npe) {
      System.out.println("Caught NPE when writing float field");
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

  private static void writeWordField(ClassWithWordField c, Word newValue) {
    try {
      c.w = newValue;
      System.out.println("Wrote value " + c.w.toLong() + " to field");
    } catch (NullPointerException npe) {
      System.out.println("Caught NPE when writing Word field");
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

  private static void writeAddressField(ClassWithAddressField c, Address newValue) {
    try {
      c.a = newValue;
      System.out.println("Wrote value " + c.a.toLong() + " to field");
    } catch (NullPointerException npe) {
      System.out.println("Caught NPE when writing Address field");
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

  private static void writeOffsetField(ClassWithOffsetField c, Offset newValue) {
    try {
      c.o = newValue;
      System.out.println("Wrote value " + c.o.toLong() + " to field");
    } catch (NullPointerException npe) {
      System.out.println("Caught NPE when writing Offset field");
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

  private static void writeExtentField(ClassWithExtentField c, Extent newValue) {
    try {
      c.e = newValue;
      System.out.println("Wrote value " + c.e.toLong() + " to field");
    } catch (NullPointerException npe) {
      System.out.println("Caught NPE when writing Extent field");
    }
  }

  private static void synchronizeOnObject(Object o) {
    try {
      synchronized (o) {
        System.out.println("Successfully synchronized on object");
      }
    } catch (NullPointerException npe) {
      System.out.println("Caugt NPE when trying to synchronize on object");
    }
  }

  static final byte DEFAULT_BYTE = 1;
  static final boolean DEFAULT_BOOLEAN = false;
  static final char DEFAULT_CHAR = 'h';
  static final short DEFAULT_SHORT = 30000;
  static final int DEFAULT_INT = 123456789;
  static final long DEFAULT_LONG = 123456789101112L;
  static final double DEFAULT_DOUBLE = 1.23456789E100d;
  static final float DEFAULT_FLOAT = 1.23456789E10f;
  static final Word DEFAULT_WORD = Word.fromIntZeroExtend(21);
  static final Address DEFAULT_ADDRESS = Address.fromIntZeroExtend(12345);
  static final Offset DEFAULT_OFFSET = Offset.fromIntZeroExtend(3);
  static final Extent DEFAULT_EXTENT = Extent.fromIntZeroExtend(7);

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

  private static final byte WRITE_BYTE = 7;
  private static final boolean WRITE_BOOLEAN = true;
  private static final char WRITE_CHAR = 'B';
  private static final short WRITE_SHORT = 101;
  private static final int WRITE_INT = 2500001;
  private static final long WRITE_LONG = 120010007600001L;
  private static final double WRITE_DOUBLE = 7.0078E100d;
  private static final float WRITE_FLOAT = 8.0005E10f;
  private static final Word WRITE_WORD = Word.fromIntZeroExtend(10);
  private static final Address WRITE_ADDRESS = Address.fromIntZeroExtend(1240);
  private static final Offset WRITE_OFFSET = Offset.fromIntZeroExtend(12);
  private static final Extent WRITE_EXTENT = Extent.fromIntZeroExtend(32);

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
}
