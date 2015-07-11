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

/**
 * This class provides a few simple tests for null checks.
 * <p>
 * TODO More tests would be useful, e.g. for all data types for putfield/getfield.
 */
public class TestNullChecks {

  static final byte DEFAULT_BYTE = 1;
  private static final byte NPE_BYTE = -2;
  private static final byte WRITE_BYTE = 7;

  private static class ClassWithByteField {
    public ClassWithByteField() {
      this.b = DEFAULT_BYTE;
    }
    public byte b;
  }

  public static void main(String[] args) {
    invokeHashCodeOnObject(new Object());
    invokeHashCodeOnObject(null);
    invokeHashCodeOnObject(new ClassWithByteField());
    invokeHashCodeOnObject(new int[0]);
    readByteField(null);
    readByteField(new ClassWithByteField());
    writeByteField(null, WRITE_BYTE);
    writeByteField(new ClassWithByteField(), WRITE_BYTE);
    synchronizeOnObject(null);
    synchronizeOnObject(new Object());
  }

  private static void invokeHashCodeOnObject(Object o) {
    try {
      int hc = o.hashCode();
      System.out.println("Successfully invoked hashCode() on object");
    } catch (NullPointerException npe) {
      System.out.println("Caugt NPE while trying to invoke hashCode()");
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
      System.out.println("Wrote value " + newValue + " to field");
    } catch (NullPointerException npe) {
      System.out.println("Caught NPE when writing byte field");
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
}
