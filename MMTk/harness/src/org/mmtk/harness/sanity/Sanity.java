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
package org.mmtk.harness.sanity;

import org.mmtk.harness.vm.ObjectModel;
import org.vmmagic.unboxed.ObjectReference;
import org.vmmagic.unboxed.Word;

/**
 * Facade class for the general run-time sanity subsystem.
 */
public class Sanity {

  public static final boolean ENFORCE_OBJECT_SANITY = true;

  private static final ObjectTable objectTable = new ObjectTable();

  /**
   * @return
   */
  public static ObjectTable getObjectTable() {
    return objectTable;
  }

  /**
   * Assert that an object reference refers to a valid object.
   *
   * @param ref The reference
   */
  public static void assertValid(ObjectReference ref) {
    if (ref.isNull()) return;
    assert ref.toAddress().toWord().and(Word.fromIntZeroExtend(0x3)).EQ(Word.zero()) :
      ref+" is incorrectly aligned for an object reference";
    assert ObjectModel.hasValidId(ref) :
      ref+" does not have a valid Object ID";
    if (ENFORCE_OBJECT_SANITY) {
      getObjectTable().assertValid(ref);
    }
  }
}
