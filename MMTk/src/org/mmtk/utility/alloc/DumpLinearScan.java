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
package org.mmtk.utility.alloc;

import org.mmtk.vm.VM;
import org.mmtk.utility.Log;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * Simple linear scan to dump object information.
 */
@Uninterruptible
public final class DumpLinearScan extends LinearScan {
  @Override
  @Inline
  public void scan(ObjectReference object) {
    Log.write("[");
    Log.write(object.toAddress());
    Log.write("], SIZE = ");
    Log.writeln(VM.objectModel.getCurrentSize(object));
  }
}
