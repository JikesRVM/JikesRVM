/*
 * (C) Copyright IBM Corp 2002
 */
//$Id$

package com.ibm.JikesRVM.OSR;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.classloader.*;

/**
 * Special invokestatic, with only two possible target
 * OSR_ObjectHolder.getRefAt and OSR_ObjectHolder.cleanRefs
 * indiced by GETREFAT and CLEANREFS.
 * 
 * @author Feng Qian
 */

public class BC_InvokeStatic extends OSR_PseudoBytecode {

  private final static int bsize = 6;   
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
