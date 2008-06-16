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
package org.jikesrvm.osr;

import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.classloader.VM_TypeReference;
import org.jikesrvm.compilers.common.VM_CompiledMethod;
import org.jikesrvm.compilers.common.VM_CompiledMethods;

/**
 * invoke a compiled method
 */

public class BC_InvokeCompiledMethod extends OSR_PseudoBytecode {

  private static int bsize = 10;
  private int cmid;

  // the bc index of referred call site
  private int origIdx;

  public BC_InvokeCompiledMethod(int cmethId, int origBCIndex) {
    this.cmid = cmethId;
    this.origIdx = origBCIndex;
  }

  public byte[] getBytes() {
    byte[] codes = initBytes(bsize, PSEUDO_InvokeCompiledMethod);
    int2bytes(codes, 2, cmid);
    int2bytes(codes, 6, origIdx);
    return codes;
  }

  public int getSize() {
    return bsize;
  }

  public int stackChanges() {
    VM_CompiledMethod cm = VM_CompiledMethods.getCompiledMethod(cmid);
    RVMMethod callee = cm.getMethod();

    int psize = callee.getParameterWords();
    int schanges = -psize;

    // pop receiver
    if (!callee.isStatic()) {
      schanges--;
    }

    VM_TypeReference rtype = callee.getReturnType();
    byte tcode = rtype.getName().parseForTypeCode();

    if (tcode == VoidTypeCode) {
      // do nothing
    } else {
      if ((tcode == LongTypeCode) || (tcode == DoubleTypeCode)) {
        schanges++;
      }
      schanges++;
    }

    return schanges;
  }

  public String toString() {
    //VM_CompiledMethod cm = VM_CompiledMethods.getCompiledMethod(cmid);
    return "InvokeCompiledMethod (0x" + Integer.toHexString(cmid) + ") " + "@" + origIdx;
  }
}
