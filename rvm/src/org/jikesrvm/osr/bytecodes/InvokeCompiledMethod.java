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
package org.jikesrvm.osr.bytecodes;

import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.compilers.common.CompiledMethod;
import org.jikesrvm.compilers.common.CompiledMethods;

/**
 * invoke a compiled method
 */

public class InvokeCompiledMethod extends PseudoBytecode {

  private static int bsize = 10;
  private int cmid;

  // the bc index of referred call site
  private int origIdx;

  public InvokeCompiledMethod(int cmethId, int origBCIndex) {
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
    CompiledMethod cm = CompiledMethods.getCompiledMethod(cmid);
    RVMMethod callee = cm.getMethod();

    int psize = callee.getParameterWords();
    int schanges = -psize;

    // pop receiver
    if (!callee.isStatic()) {
      schanges--;
    }

    TypeReference rtype = callee.getReturnType();
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
    //CompiledMethod cm = CompiledMethods.getCompiledMethod(cmid);
    return "InvokeCompiledMethod (0x" + Integer.toHexString(cmid) + ") " + "@" + origIdx;
  }
}
