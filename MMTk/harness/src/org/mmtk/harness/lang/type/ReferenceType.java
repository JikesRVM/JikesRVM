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

import java.util.EnumMap;
import java.util.Map;

import org.mmtk.harness.lang.runtime.PhantomReferenceValue;
import org.mmtk.harness.lang.runtime.ReferenceValue;
import org.mmtk.harness.lang.runtime.SoftReferenceValue;
import org.mmtk.harness.lang.runtime.Value;
import org.mmtk.harness.lang.runtime.WeakReferenceValue;
import org.mmtk.vm.ReferenceProcessor.Semantics;

/**
 * The built-in <code>reference</code> types.
 */
public class ReferenceType extends AbstractType {

  private static final Map<Semantics,ReferenceValue> initialValues =
    new EnumMap<Semantics,ReferenceValue>(Semantics.class);
  static {
    initialValues.put(Semantics.WEAK, WeakReferenceValue.NULL);
    initialValues.put(Semantics.SOFT, SoftReferenceValue.NULL);
    initialValues.put(Semantics.PHANTOM, PhantomReferenceValue.NULL);
  }

  private final Semantics semantics;

  ReferenceType(Semantics semantics) {
    super(semantics.toString().toLowerCase() + "ref");
    this.semantics = semantics;
  }

  /**
   * @see org.mmtk.harness.lang.type.Type#initialValue()
   */
  @Override
  public Value initialValue() {
    return initialValues.get(semantics);
  }

  /**
   * @see org.mmtk.harness.lang.type.AbstractType#isObject()
   */
  @Override
  public boolean isObject() {
    return false;
  }

  /**
   * @see org.mmtk.harness.lang.type.AbstractType#isCompatibleWith(org.mmtk.harness.lang.type.Type)
   */
  @Override
  public boolean isCompatibleWith(Type rhs) {
    if (rhs == this) {
      return true;
    }
    return false;
  }
}
