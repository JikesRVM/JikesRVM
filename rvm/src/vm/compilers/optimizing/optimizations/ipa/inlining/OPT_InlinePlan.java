/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.classloader.*;

/**
 * The profile-directed inline oracle requires that
 * inline plans implement this interface.
 *
 * @author Stephen Fink
 * @author Dave Grove
 */
public interface OPT_InlinePlan {

  /**
   * Return the set of methods to inline at a call site.
   *
   * @param caller the caller
   * @param bcX bytecodeIndex of the call site
   */
  public VM_Method[] getTargets (VM_Method caller, int bcX); 
  
}
