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
package org.mmtk.harness.lang.type;

import org.mmtk.harness.lang.runtime.ObjectValue;
import org.mmtk.harness.lang.runtime.Value;

/**
 * The built-in <code>object</code> type.
 */
public class ObjectType extends AbstractType {

  ObjectType() {
    super("object");
  }

  /**
   * @see org.mmtk.harness.lang.type.Type#initialValue()
   */
  @Override
  public Value initialValue() {
    return ObjectValue.NULL;
  }

  /**
   * @see org.mmtk.harness.lang.type.AbstractType#isObject()
   */
  @Override
  public boolean isObject() {
    return true;
  }

  /**
   * @see org.mmtk.harness.lang.type.AbstractType#isCompatibleWith(org.mmtk.harness.lang.type.Type)
   */
  @Override
  public boolean isCompatibleWith(Type rhs) {
    if (rhs == this) {
      return true;
    }
    if (rhs.isObject()) {
      return true;
    }
    return false;
  }
}
