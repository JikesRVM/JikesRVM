/*
 * (C) Copyright IBM Corp 2002
 */
//$Id$

package com.ibm.JikesRVM.OSR;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.classloader.*;
/**
 * invokestatic 
 * 
 * @author Feng Qian
 */

public class BC_InvokeStatic extends OSR_PseudoBytecode {

  private final static int bsize = 6;   
  private final int mid;

  public BC_InvokeStatic(int methId) {
    this.mid = methId;
    
  }

  public byte[] getBytes() {
    byte[] codes = initBytes(bsize, PSEUDO_InvokeStatic);
    int2bytes(codes, 2, mid);
    return codes;
  }

  public int getSize() {
    return bsize;
  }

  public int stackChanges() {
    VM_Method callee = VM_MethodDictionary.getValue(mid);
    int psize = callee.getParameterWords();
    int schanges = -psize;
    
    VM_Type rtype = callee.getReturnType();
    byte tcode = rtype.getDescriptor().parseForTypeCode();
    
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
    return "InvokeStatic "+mid;
  }
}
