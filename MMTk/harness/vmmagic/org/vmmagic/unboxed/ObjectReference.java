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
package org.vmmagic.unboxed;

import org.vmmagic.Unboxed;
import org.vmmagic.unboxed.harness.ArchitecturalWord;

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

  final ArchitecturalWord value;

  ObjectReference(ArchitecturalWord value) {
    this.value = value;
  }

  /**
   * @return a {@code null} reference
   */
  public static ObjectReference nullReference() {
    return new ObjectReference(ArchitecturalWord.fromLong(0));
  }

  /**
   * Get a heap address for the object.
   * @return The address of the referenced object
   */
  public Address toAddress() {
    return new Address(value);
  }

  /**
   * Object equality.
   */
  @Override
  public boolean equals(Object other) {
    return other instanceof ObjectReference && ((ObjectReference)other).value.equals(value);
  }

  /**
   * Object hashCode
   */
  @Override
  public int hashCode() {
    long val = value.toLongZeroExtend();
    int high = (int)(val >>> 32);
    int low = (int)(val & 0xFFFFFFFFL);
    return high ^ (low >>> 2);
  }

  /**
   * @return Is this a {@code null} reference?
   */
  public boolean isNull() {
    return value.isZero();
  }

  /**
   * @see java.lang.Object#toString()
   */
  @Override
  public String toString() {
    return value.toString();
  }
}
