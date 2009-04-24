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

import org.mmtk.harness.lang.runtime.BoolValue;
import org.mmtk.harness.lang.runtime.Value;

public class BooleanType extends AbstractType {

  public BooleanType() {
    super("boolean");
  }

  @Override
  public boolean isCompatibleWith(Type rhs) {
    if (rhs == OBJECT) {
      return true;
    }
    return super.isCompatibleWith(rhs);
  }

  @Override
  public Value initialValue() {
    return BoolValue.FALSE;
  }


}
