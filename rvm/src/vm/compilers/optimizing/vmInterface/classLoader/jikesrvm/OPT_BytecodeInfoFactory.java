/*
 * (C) Copyright IBM Corp. 2002
 */
//$Id$
package com.ibm.JikesRVM.opt;

import com.ibm.JikesRVM.VM_Method;

/**
 * @author Julian Dolby
 * @date May 20, 2002
 */
public class OPT_BytecodeInfoFactory {

  public static OPT_BytecodeInfo create(VM_Method method) {
    return new OPT_BytecodeInfo(method);
  }
}
