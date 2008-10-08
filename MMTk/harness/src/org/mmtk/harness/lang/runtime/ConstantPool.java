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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.mmtk.harness.lang.compiler.Register;
import org.vmmagic.unboxed.ObjectReference;

public class ConstantPool {

  private static int next = -1;
  private static final Map<Value,Register> constants = new HashMap<Value,Register>();
  private static final ArrayList<Value> values = new ArrayList<Value>();

  public static final Register NULL = create(new ObjectValue(ObjectReference.nullReference()));
  public static final Register ZERO = create(IntValue.ZERO);
  public static final Register ONE = create(IntValue.ONE);
  public static final Register TRUE = create(BoolValue.TRUE);
  public static final Register FALSE = create(BoolValue.FALSE);

  public static Register acquire(Value constant) {
    Register result = constants.get(constant);
    if (result != null) {
      return result;
    }
    return create(constant);
  }

  private static Register create(Value constant) {
    Register result = Register.createConstant(next--);
    constants.put(constant, result);
    values.add(constant);
    return result;
  }

  public static Value get(int index) {
    return values.get((-index)-1);
  }
}
