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

  /**
   * The object field.
   */
  @SuppressWarnings("unused")
  private Object data;

  /**
   * Convert from an object to a reference.
   * @param obj The object
   * @return The corresponding reference
   */
  public static ObjectReference fromObject(Object obj) {
    return null;
  }

  /**
   * Return a {@code null} reference
   */
  public static ObjectReference nullReference() {
    return null;
  }

  /**
   * Convert from an reference to an object. Note: this is a JikesRVM
   * specific extension to vmmagic.
   * @return The object
   */
  public Object toObject() {
    return null;
  }

  /**
   * Get a heap address for the object.
   */
  public Address toAddress() {
    return null;
  }

  /**
   * Is this a null reference?
   */
  public boolean isNull() {
    return false;
  }
}
