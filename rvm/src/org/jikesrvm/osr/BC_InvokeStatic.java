/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp 2002
 */

package org.jikesrvm.osr;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.VM_Method;
import org.jikesrvm.classloader.VM_TypeReference;
import org.jikesrvm.runtime.VM_Entrypoints;

/**
 * Special invokestatic, with only two possible target
 * OSR_ObjectHolder.getRefAt and OSR_ObjectHolder.cleanRefs
 * indiced by GETREFAT and CLEANREFS.
 * 
 * @author Feng Qian
 */

public class BC_InvokeStatic extends OSR_PseudoBytecode {

  private static final int bsize = 6;
  private final int tid;  // target INDEX

  public BC_InvokeStatic(int targetId) {
    this.tid = targetId;
  }

  public byte[] getBytes() {
    byte[] codes = initBytes(bsize, PSEUDO_InvokeStatic);
    int2bytes(codes, 2, tid);
    return codes;
  }

  public int getSize() {
    return bsize;
  }

  public int stackChanges() {
    VM_Method callee = null;
    switch (tid) {
    case GETREFAT:
      callee = VM_Entrypoints.osrGetRefAtMethod;
      break;
    case CLEANREFS:
      callee = VM_Entrypoints.osrCleanRefsMethod;
      break;
    default:
      if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
      break;
    }
  
    int psize = callee.getParameterWords();
    int schanges = -psize;
    
    VM_TypeReference rtype = callee.getReturnType();
    byte tcode = rtype.getName().parseForTypeCode();
    
    if (tcode == VoidTypeCode) {
      // do nothing
    } else {
      if ( (tcode == LongTypeCode) ||
           (tcode == DoubleTypeCode) ) {
        schanges ++;
      }
      schanges ++;
    }
    
    return schanges;
  }
 
  public String toString() {
    return "InvokeStatic "+tid;
  }
}
