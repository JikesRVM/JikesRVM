/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

/**
 * Extend java.util.Enumeration to avoid downcasts from object.
 * Also provide a preallocated empty instruction enumeration.
 *
 * @author Dave Grove
 */
public interface OPT_InstructionEnumeration extends java.util.Enumeration {
  /**
   * Same as nextElement but avoid the need to downcast from Object
   */
  public OPT_Instruction next();

  /**
   * Single preallocated empty OPT_InstructionEnumeration.
   * WARNING: Think before you use this; getting two possible concrete
   * types may prevent inlining of hasMoreElements and next(), thus
   * blocking scalar replacement.  Only use Empty when we have no hope
   * of scalar replacing the alternative (real) enumeration object.
   */
  public static final OPT_InstructionEnumeration Empty = new OPT_EmptyInstructionEnumeration();
}

