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
package org.mmtk.harness.lang.runtime;

import org.mmtk.harness.lang.type.Type;
import org.vmmagic.unboxed.ObjectReference;

/**
 * The NULL Object constant
 */
public final class NullValue extends ObjectValue {

  public static final NullValue NULL = new NullValue();

  /**
   * Construct an initially null object value
   */
  private NullValue() {
    super(ObjectReference.nullReference());
  }

  /**
   * Get this value as a boolean.
   */
  public boolean getBoolValue() {
    return false;
  }

  /**
   * Prints the address of the object.
   */
  @Override
  public String toString() {
    return "null";
  }

  /**
   * The type of this value
   */
  @Override
  public Type type() {
    return Type.NULL;
  }

  /**
   * Object equality.
   */
  @Override
  public boolean equals(Object other) {
    assert this == NULL; // This is a singleton!
    return this == other;
  }

}
