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

import org.vmmagic.pragma.NoInline;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Extent;
import org.vmmagic.unboxed.Offset;
import org.vmmagic.unboxed.Word;

/**
 * This class provides tests for null checks for resolved fields.
 */
public class TestNullChecks_Resolved {

  public static void main(String[] args) {
    testExplicitNullChecks();
    resolveClassesAndFieldsForNullChecks();
    testNullChecksForResolvedFields();
  }

  private static void printCode(String code) {
    System.out.print(code);
    System.out.print(": ");
  }

  private static void testExplicitNullChecks() {
    printCode("null == null");
    System.out.println(null == null);
    printCode("null != null");
    System.out.println(null != null);

    Object obj = null;
    printCode("obj == null");
    System.out.println(obj == null);
    ClassWithUnsetObjectField cwuof = null;
    printCode("cwuof == null");
    System.out.println(cwuof == null);
    cwuof = new ClassWithUnsetObjectField();
    printCode("cwuof == null");
    System.out.println(cwuof == null);
    printCode("cwuof.obj == null");
    System.out.println(cwuof.obj == null);
    cwuof.obj = new Object();
    printCode("cwuof.obj == null");
    System.out.println(cwuof.obj == null);

    ClassWithArrayField cwuaf = new ClassWithArrayField();
    printCode("cwuaf.array[0] == null");
    System.out.println(cwuaf.array[0] == null);
    cwuaf.array[0] = new Object();
    printCode("cwuaf.array[0] == null");
    System.out.println(cwuaf.array[0] == null);
  }



  private static void resolveClassesAndFieldsForNullChecks() {
    ClassWithObjectField cwobjf = new ClassWithObjectField();
    cwobjf.obj = new Object();
    ClassWithByteField cwbf = new ClassWithByteField();
    cwbf.b = (byte) 123;
    ClassWithBooleanField cwbf2 = new ClassWithBooleanField();
    cwbf2.b = true;
    ClassWithCharField cwcf = new ClassWithCharField();
    cwcf.c = 'u';
    ClassWithShortField cwsf = new ClassWithShortField();
    cwsf.s = (short) 12345;
    ClassWithIntField cwif = new ClassWithIntField();
    cwif.i = 1245678910;
    ClassWithLongField cwlf = new ClassWithLongField();
    cwlf.l = 124567891011121314L;
    ClassWithDoubleField cwdf = new ClassWithDoubleField();
    cwdf.d = 1234.5678910111213141516d;
    ClassWithFloatField cwff = new ClassWithFloatField();
    cwff.f = 1.2345678910111213f;
    ClassWithWordField cwwf = new ClassWithWordField();
    cwwf.w = Word.fromIntSignExtend(-1234);
    ClassWithAddressField cwaf = new ClassWithAddressField();
    cwaf.a = Address.fromIntZeroExtend(0xF00F);
    ClassWithOffsetField cwof = new ClassWithOffsetField();
    cwof.o = Offset.fromIntSignExtend(-3456);
    ClassWithExtentField cwef = new ClassWithExtentField();
    cwef.e = Extent.fromIntZeroExtend(123456789);

    ClassWithVolatileObjectField cwvobjf = new ClassWithVolatileObjectField();
    cwvobjf.obj = new Object();
    ClassWithVolatileByteField cwvbf = new ClassWithVolatileByteField();
    cwvbf.b = (byte) 123;
    ClassWithVolatileBooleanField cwvbf2 = new ClassWithVolatileBooleanField();
    cwvbf2.b = true;
    ClassWithVolatileCharField cwvcf = new ClassWithVolatileCharField();
    cwvcf.c = 'u';
    ClassWithVolatileShortField cwvsf = new ClassWithVolatileShortField();
    cwvsf.s = (short) 12345;
    ClassWithVolatileIntField cwvif = new ClassWithVolatileIntField();
    cwvif.i = 1245678910;
    ClassWithVolatileLongField cwvlf = new ClassWithVolatileLongField();
    cwvlf.l = 124567891011121314L;
    ClassWithVolatileDoubleField cwvdf = new ClassWithVolatileDoubleField();
    cwvdf.d = 1234.5678910111213141516d;
    ClassWithVolatileFloatField cwvff = new ClassWithVolatileFloatField();
    cwvff.f = 1.2345678910111213f;
    ClassWithVolatileWordField cwvwf = new ClassWithVolatileWordField();
    cwvwf.w = Word.fromIntSignExtend(-1234);
    ClassWithVolatileAddressField cwvaf = new ClassWithVolatileAddressField();
    cwvaf.a = Address.fromIntZeroExtend(0xF00F);
    ClassWithVolatileOffsetField cwvof = new ClassWithVolatileOffsetField();
    cwvof.o = Offset.fromIntSignExtend(-3456);
    ClassWithVolatileExtentField cwvef = new ClassWithVolatileExtentField();
    cwvef.e = Extent.fromIntZeroExtend(123456789);
  }

  @NoInline
  private static void testNullChecksForResolvedFields() {
    readObjectField(null);
    readObjectField(new ClassWithObjectField());
    writeObjectField(null, WRITE_OBJECT);
    writeObjectField(new ClassWithObjectField(), WRITE_OBJECT);
    invokeHashCodeOnObject(new Object());
    invokeHashCodeOnObject(null);
    invokeHashCodeOnObject(new ClassWithByteField());
    invokeHashCodeOnObject(new int[0]);
    synchronizeOnObject(null);
    synchronizeOnObject(new Object());
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

    readVolatileObjectField(null);
    readVolatileObjectField(new ClassWithVolatileObjectField());
    writeVolatileObjectField(null, WRITE_OBJECT);
    writeVolatileObjectField(new ClassWithVolatileObjectField(), WRITE_OBJECT);
    readVolatileByteField(null);
    readVolatileByteField(new ClassWithVolatileByteField());
    writeVolatileByteField(null, WRITE_BYTE);
    writeVolatileByteField(new ClassWithVolatileByteField(), WRITE_BYTE);
    readVolatileBooleanField(null);
    readVolatileBooleanField(new ClassWithVolatileBooleanField());
    writeVolatileBooleanField(null, WRITE_BOOLEAN);
    writeVolatileBooleanField(new ClassWithVolatileBooleanField(), WRITE_BOOLEAN);
    readVolatileCharField(null);
    readVolatileCharField(new ClassWithVolatileCharField());
    writeVolatileCharField(null, WRITE_CHAR);
    writeVolatileCharField(new ClassWithVolatileCharField(), WRITE_CHAR);
    readVolatileShortField(null);
    readVolatileShortField(new ClassWithVolatileShortField());
    writeVolatileShortField(null, WRITE_SHORT);
    writeVolatileShortField(new ClassWithVolatileShortField(), WRITE_SHORT);
    readVolatileIntField(null);
    readVolatileIntField(new ClassWithVolatileIntField());
    writeVolatileIntField(null, WRITE_INT);
    writeVolatileIntField(new ClassWithVolatileIntField(), WRITE_INT);
    readVolatileLongField(null);
    readVolatileLongField(new ClassWithVolatileLongField());
    writeVolatileLongField(null, WRITE_LONG);
    writeVolatileLongField(new ClassWithVolatileLongField(), WRITE_LONG);
    readVolatileDoubleField(null);
    readVolatileDoubleField(new ClassWithVolatileDoubleField());
    writeVolatileDoubleField(null, WRITE_DOUBLE);
    writeVolatileDoubleField(new ClassWithVolatileDoubleField(), WRITE_DOUBLE);
    readVolatileFloatField(null);
    readVolatileFloatField(new ClassWithVolatileFloatField());
    writeVolatileFloatField(null, WRITE_FLOAT);
    writeVolatileFloatField(new ClassWithVolatileFloatField(), WRITE_FLOAT);
    readVolatileWordField(null);
    readVolatileWordField(new ClassWithVolatileWordField());
    writeVolatileWordField(null, WRITE_WORD);
    writeVolatileWordField(new ClassWithVolatileWordField(), WRITE_WORD);
    readVolatileAddressField(null);
    readVolatileAddressField(new ClassWithVolatileAddressField());
    writeVolatileAddressField(null, WRITE_ADDRESS);
    writeVolatileAddressField(new ClassWithVolatileAddressField(), WRITE_ADDRESS);
    readVolatileOffsetField(null);
    readVolatileOffsetField(new ClassWithVolatileOffsetField());
    writeVolatileOffsetField(null, WRITE_OFFSET);
    writeVolatileOffsetField(new ClassWithVolatileOffsetField(), WRITE_OFFSET);
    readVolatileExtentField(null);
    readVolatileExtentField(new ClassWithVolatileExtentField());
    writeVolatileExtentField(null, WRITE_EXTENT);
    writeVolatileExtentField(new ClassWithVolatileExtentField(), WRITE_EXTENT);
  }

  private static void invokeHashCodeOnObject(Object o) {
    try {
      o.hashCode();
      System.out.println("Successfully invoked hashCode() on object");
    } catch (NullPointerException npe) {
      System.out.println("Caught NPE while trying to invoke hashCode()");
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

  private static void writeObjectField(ClassWithObjectField c, Object newValue) {
    try {
      c.obj = newValue;
      System.out.println("Wrote object value to field");
    } catch (NullPointerException npe) {
      System.out.println("Caught NPE when writing Object field");
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

  private static void writeVolatileByteField(ClassWithVolatileByteField c, byte newValue) {
    try {
      c.b = newValue;
      System.out.println("Wrote value " + c.b + " to field");
    } catch (NullPointerException npe) {
      System.out.println("Caught NPE when writing byte field");
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

  private static void writeVolatileBooleanField(ClassWithVolatileBooleanField c, boolean newValue) {
    try {
      c.b = newValue;
      System.out.println("Wrote value " + c.b + " to field");
    } catch (NullPointerException npe) {
      System.out.println("Caught NPE when writing boolean field");
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

  private static void writeVolatileCharField(ClassWithVolatileCharField c, char newValue) {
    try {
      c.c = newValue;
      System.out.println("Wrote value " + c.c + " to field");
    } catch (NullPointerException npe) {
      System.out.println("Caught NPE when writing char field");
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

  private static void writeVolatileShortField(ClassWithVolatileShortField c, short newValue) {
    try {
      c.s = newValue;
      System.out.println("Wrote value " + c.s + " to field");
    } catch (NullPointerException npe) {
      System.out.println("Caught NPE when writing short field");
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

  private static void writeVolatileIntField(ClassWithVolatileIntField c, int newValue) {
    try {
      c.i = newValue;
      System.out.println("Wrote value " + c.i + " to field");
    } catch (NullPointerException npe) {
      System.out.println("Caught NPE when writing int field");
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

  private static void writeVolatileLongField(ClassWithVolatileLongField c, long newValue) {
    try {
      c.l = newValue;
      System.out.println("Wrote value " + c.l + " to field");
    } catch (NullPointerException npe) {
      System.out.println("Caught NPE when writing long field");
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

  private static void writeVolatileDoubleField(ClassWithVolatileDoubleField c, double newValue) {
    try {
      c.d = newValue;
      System.out.println("Wrote value " + c.d + " to field");
    } catch (NullPointerException npe) {
      System.out.println("Caught NPE when writing double field");
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

  private static void writeVolatileFloatField(ClassWithVolatileFloatField c, float newValue) {
    try {
      c.f = newValue;
      System.out.println("Wrote value " + c.f + " to field");
    } catch (NullPointerException npe) {
      System.out.println("Caught NPE when writing float field");
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

  private static void writeVolatileWordField(ClassWithVolatileWordField c, Word newValue) {
    try {
      c.w = newValue;
      System.out.println("Wrote value " + c.w.toLong() + " to field");
    } catch (NullPointerException npe) {
      System.out.println("Caught NPE when writing Word field");
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

  private static void writeVolatileAddressField(ClassWithVolatileAddressField c, Address newValue) {
    try {
      c.a = newValue;
      System.out.println("Wrote value " + c.a.toLong() + " to field");
    } catch (NullPointerException npe) {
      System.out.println("Caught NPE when writing Address field");
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

  private static void writeVolatileOffsetField(ClassWithVolatileOffsetField c, Offset newValue) {
    try {
      c.o = newValue;
      System.out.println("Wrote value " + c.o.toLong() + " to field");
    } catch (NullPointerException npe) {
      System.out.println("Caught NPE when writing Offset field");
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

  private static void writeVolatileExtentField(ClassWithVolatileExtentField c, Extent newValue) {
    try {
      c.e = newValue;
      System.out.println("Wrote value " + c.e.toLong() + " to field");
    } catch (NullPointerException npe) {
      System.out.println("Caught NPE when writing Extent field");
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

  private static void writeVolatileObjectField(ClassWithVolatileObjectField c, Object newValue) {
    try {
      c.obj = newValue;
      System.out.println("Wrote object value to field");
    } catch (NullPointerException npe) {
      System.out.println("Caught NPE when writing Object field");
    }
  }

  static final Object DEFAULT_OBJECT = new Object();
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

  private static final Object WRITE_OBJECT = new Object();
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
  private static class ClassWithUnsetObjectField {
    public Object obj;
  }
  private static class ClassWithArrayField {
    public Object[] array = new Object[1];
  }

}
