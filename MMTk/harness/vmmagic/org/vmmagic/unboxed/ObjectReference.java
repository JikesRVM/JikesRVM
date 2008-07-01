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
package org.vmmagic.unboxed;

import org.vmmagic.Unboxed;

/**
 * The object reference type is used by the runtime system and collector to
 * represent a type that holds a reference to a single object.
 * We use a separate type instead of the Java Object type for coding clarity,
 * to make a clear distinction between objects the VM is written in, and
 * objects that the VM is managing. No operations that can not be completed in
 * pure Java should be allowed on Object.
 */
@Unboxed
public final class ObjectReference {

  final int value;

  ObjectReference(int value) {
    this.value = value;
  }

  /**
   * Return a null reference
   */
  public static ObjectReference nullReference() {
    return new ObjectReference(0);
  }

  /**
   * Get a heap address for the object.
   */
  public Address toAddress() {
    return new Address(value);
  }

  /**
   * Object equality.
   */
  public boolean equals(Object other) {
    return other instanceof ObjectReference && ((ObjectReference)other).value == value;
  }

  /**
   * Object hashCode
   */
  public int hashCode() {
    return value >>> 2;
  }

  /**
   * Is this a null reference?
   */
  public boolean isNull() {
    return value == 0;
  }

  public String toString() {
    return Address.formatInt(value);
  }
}
