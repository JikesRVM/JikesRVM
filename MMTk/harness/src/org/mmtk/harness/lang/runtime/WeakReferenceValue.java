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
package org.mmtk.harness.lang.runtime;

import org.mmtk.harness.lang.type.Type;
import org.mmtk.vm.ReferenceProcessor.Semantics;
import org.vmmagic.unboxed.ObjectReference;

/**
 * A weak reference in the MMTk Harness language
 */
public class WeakReferenceValue extends ReferenceValue {

  /**
   * The NULL weak reference
   */
  public static final ReferenceValue NULL = new WeakReferenceValue(ObjectReference.nullReference());

  /**
   * @param ref The referent
   */
  public WeakReferenceValue(ObjectReference ref) {
    super(ref,Semantics.WEAK);
  }

  /**
   * @see org.mmtk.harness.lang.runtime.Value#type()
   */
  @Override
  public Type type() {
    return Type.WEAKREF;
  }
}
