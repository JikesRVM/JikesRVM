/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

import com.ibm.JikesRVM.classloader.VM_NormalMethod;
import com.ibm.JikesRVM.VM_CompiledMethod;

/**
 * @author Rajesh Bordawekar
 * @author Manish Gupta
 * @author Stephen Fink
 */
public interface OPT_SpecializationContext {

  /** 
   * Find or create a specialized version of source for this 
   * context.  Do NOT compile it immediately.  However, DO
   * allocate an spmd if needed
   */
  public OPT_SpecializedMethod findOrCreateSpecializedVersion(VM_NormalMethod source);

  /**
   * Generate code for a specialized version of source in this
   * context.
   */
  public VM_CompiledMethod specialCompile (VM_NormalMethod source);
}



