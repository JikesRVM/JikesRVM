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
 * This class provides tests for null checks for writing unresolved fields
 * where the object that is being written to is non-null.
 */
public class TestNullChecks_Unresolved_WriteToNonNullObject {

  public static void main(String[] args) {
    writeObjectField(new ClassWithObjectField(), WRITE_OBJECT);
    writeByteField(new ClassWithByteField(), WRITE_BYTE);
    writeBooleanField(new ClassWithBooleanField(), WRITE_BOOLEAN);
    writeCharField(new ClassWithCharField(), WRITE_CHAR);
    writeShortField(new ClassWithShortField(), WRITE_SHORT);
    writeIntField(new ClassWithIntField(), WRITE_INT);
    writeLongField(new ClassWithLongField(), WRITE_LONG);
    writeDoubleField(new ClassWithDoubleField(), WRITE_DOUBLE);
    writeFloatField(new ClassWithFloatField(), WRITE_FLOAT);
    writeWordField(new ClassWithWordField(), WRITE_WORD);
    writeAddressField(new ClassWithAddressField(), WRITE_ADDRESS);
    writeOffsetField(new ClassWithOffsetField(), WRITE_OFFSET);
    writeExtentField(new ClassWithExtentField(), WRITE_EXTENT);

    writeVolatileObjectField(new ClassWithVolatileObjectField(), WRITE_OBJECT);
    writeVolatileByteField(new ClassWithVolatileByteField(), WRITE_BYTE);
    writeVolatileBooleanField(new ClassWithVolatileBooleanField(), WRITE_BOOLEAN);
    writeVolatileCharField(new ClassWithVolatileCharField(), WRITE_CHAR);
    writeVolatileShortField(new ClassWithVolatileShortField(), WRITE_SHORT);
    writeVolatileIntField(new ClassWithVolatileIntField(), WRITE_INT);
    writeVolatileLongField(new ClassWithVolatileLongField(), WRITE_LONG);
    writeVolatileDoubleField(new ClassWithVolatileDoubleField(), WRITE_DOUBLE);
    writeVolatileFloatField(new ClassWithVolatileFloatField(), WRITE_FLOAT);
    writeVolatileWordField(new ClassWithVolatileWordField(), WRITE_WORD);
    writeVolatileAddressField(new ClassWithVolatileAddressField(), WRITE_ADDRESS);
    writeVolatileOffsetField(new ClassWithVolatileOffsetField(), WRITE_OFFSET);
    writeVolatileExtentField(new ClassWithVolatileExtentField(), WRITE_EXTENT);
  }

  private static void writeByteField(ClassWithByteField c, byte newValue) {
    try {
      c.b = newValue;
      System.out.println("Wrote value " + c.b + " to field");
    } catch (NullPointerException npe) {
      System.out.println("Caught NPE when writing byte field");
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

  private static void writeCharField(ClassWithCharField c, char newValue) {
    try {
      c.c = newValue;
      System.out.println("Wrote value " + c.c + " to field");
    } catch (NullPointerException npe) {
      System.out.println("Caught NPE when writing char field");
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

  private static void writeIntField(ClassWithIntField c, int newValue) {
    try {
      c.i = newValue;
      System.out.println("Wrote value " + c.i + " to field");
    } catch (NullPointerException npe) {
      System.out.println("Caught NPE when writing int field");
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

  private static void writeDoubleField(ClassWithDoubleField c, double newValue) {
    try {
      c.d = newValue;
      System.out.println("Wrote value " + c.d + " to field");
    } catch (NullPointerException npe) {
      System.out.println("Caught NPE when writing double field");
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

  private static void writeWordField(ClassWithWordField c, Word newValue) {
    try {
      c.w = newValue;
      System.out.println("Wrote value " + c.w.toLong() + " to field");
    } catch (NullPointerException npe) {
      System.out.println("Caught NPE when writing Word field");
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

  private static void writeOffsetField(ClassWithOffsetField c, Offset newValue) {
    try {
      c.o = newValue;
      System.out.println("Wrote value " + c.o.toLong() + " to field");
    } catch (NullPointerException npe) {
      System.out.println("Caught NPE when writing Offset field");
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

  private static void writeObjectField(ClassWithObjectField c, Object newValue) {
    try {
      c.obj = newValue;
      System.out.println("Wrote object value to field");
    } catch (NullPointerException npe) {
      System.out.println("Caught NPE when writing Object field");
    }
  }

  private static void writeVolatileByteField(ClassWithVolatileByteField c, byte newValue) {
    try {
      c.b = newValue;
      System.out.println("Wrote value " + c.b + " to field");
    } catch (NullPointerException npe) {
      System.out.println("Caught NPE when writing byte field");
    }
  }


  private static void writeVolatileBooleanField(ClassWithVolatileBooleanField c, boolean newValue) {
    try {
      c.b = newValue;
      System.out.println("Wrote value " + c.b + " to field");
    } catch (NullPointerException npe) {
      System.out.println("Caught NPE when writing boolean field");
    }
  }


  private static void writeVolatileCharField(ClassWithVolatileCharField c, char newValue) {
    try {
      c.c = newValue;
      System.out.println("Wrote value " + c.c + " to field");
    } catch (NullPointerException npe) {
      System.out.println("Caught NPE when writing char field");
    }
  }


  private static void writeVolatileShortField(ClassWithVolatileShortField c, short newValue) {
    try {
      c.s = newValue;
      System.out.println("Wrote value " + c.s + " to field");
    } catch (NullPointerException npe) {
      System.out.println("Caught NPE when writing short field");
    }
  }

  private static void writeVolatileIntField(ClassWithVolatileIntField c, int newValue) {
    try {
      c.i = newValue;
      System.out.println("Wrote value " + c.i + " to field");
    } catch (NullPointerException npe) {
      System.out.println("Caught NPE when writing int field");
    }
  }

  private static void writeVolatileLongField(ClassWithVolatileLongField c, long newValue) {
    try {
      c.l = newValue;
      System.out.println("Wrote value " + c.l + " to field");
    } catch (NullPointerException npe) {
      System.out.println("Caught NPE when writing long field");
    }
  }

  private static void writeVolatileDoubleField(ClassWithVolatileDoubleField c, double newValue) {
    try {
      c.d = newValue;
      System.out.println("Wrote value " + c.d + " to field");
    } catch (NullPointerException npe) {
      System.out.println("Caught NPE when writing double field");
    }
  }

  private static void writeVolatileFloatField(ClassWithVolatileFloatField c, float newValue) {
    try {
      c.f = newValue;
      System.out.println("Wrote value " + c.f + " to field");
    } catch (NullPointerException npe) {
      System.out.println("Caught NPE when writing float field");
    }
  }

  private static void writeVolatileWordField(ClassWithVolatileWordField c, Word newValue) {
    try {
      c.w = newValue;
      System.out.println("Wrote value " + c.w.toLong() + " to field");
    } catch (NullPointerException npe) {
      System.out.println("Caught NPE when writing Word field");
    }
  }

  private static void writeVolatileAddressField(ClassWithVolatileAddressField c, Address newValue) {
    try {
      c.a = newValue;
      System.out.println("Wrote value " + c.a.toLong() + " to field");
    } catch (NullPointerException npe) {
      System.out.println("Caught NPE when writing Address field");
    }
  }

  private static void writeVolatileOffsetField(ClassWithVolatileOffsetField c, Offset newValue) {
    try {
      c.o = newValue;
      System.out.println("Wrote value " + c.o.toLong() + " to field");
    } catch (NullPointerException npe) {
      System.out.println("Caught NPE when writing Offset field");
    }
  }

  private static void writeVolatileExtentField(ClassWithVolatileExtentField c, Extent newValue) {
    try {
      c.e = newValue;
      System.out.println("Wrote value " + c.e.toLong() + " to field");
    } catch (NullPointerException npe) {
      System.out.println("Caught NPE when writing Extent field");
    }
  }

  private static void writeVolatileObjectField(ClassWithVolatileObjectField c, Object newValue) {
    try {
      c.obj = newValue;
      System.out.println("Wrote object value to field");
    } catch (NullPointerException npe) {
      System.out.println("Caught NPE when writing Object field");
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

  private static final Object WRITE_OBJECT = new Object();
  private static final byte WRITE_BYTE = 7;
  private static final boolean WRITE_BOOLEAN = true;
  private static final char WRITE_CHAR = 'B';
  private static final short WRITE_SHORT = 101;
  private static final int WRITE_INT = 2500001;
  private static final long WRITE_LONG = 120010007600001L;
  private static final double WRITE_DOUBLE = 7.0078E100d;
  private static final float WRITE_FLOAT = 8.9E7f;
  private static final Word WRITE_WORD = Word.fromIntZeroExtend(10);
  private static final Address WRITE_ADDRESS = Address.fromIntZeroExtend(1240);
  private static final Offset WRITE_OFFSET = Offset.fromIntZeroExtend(12);
  private static final Extent WRITE_EXTENT = Extent.fromIntZeroExtend(32);

  private static class ClassWithObjectField {
    ClassWithObjectField() {
      this.obj = DEFAULT_OBJECT;
    }
    @SuppressWarnings("unused")
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
    @SuppressWarnings("unused")
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
