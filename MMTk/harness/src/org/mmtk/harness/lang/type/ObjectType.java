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
package org.mmtk.harness.lang.type;

import org.mmtk.harness.lang.runtime.ObjectValue;
import org.mmtk.harness.lang.runtime.Value;

public class ObjectType extends AbstractType {

  public ObjectType() {
    super("object");
  }

  @Override
  public Value initialValue() {
    return ObjectValue.NULL;
  }

  @Override
  public boolean isObject() {
    return true;
  }

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
