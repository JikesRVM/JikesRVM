/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

/**
 * Constants describing vm object, stack, and register characteristics.
 * Some of these constants are architecture-specific
 * and some are (at the moment) architecture-neutral.
 *
 * @author Bowen Alpern
 * @author Stephen Fink
 * @author David Grove
 */
public interface VM_Constants
extends   VM_ThinLockConstants,         // architecture-neutral
          VM_TIBLayoutConstants,        // architecture-neutral
          VM_StackframeLayoutConstants, // architecture-neutral
          VM_SizeConstants,             // 'semi-'architecture-neutral
          VM_RegisterConstants,         // architecture-specific
          VM_TrapConstants              // architecture-specific
{
  /**
   * For assertion checking things that should never happen.
   */ 
  static final boolean NOT_REACHED = false;

  /**
   * Reflection uses an integer return from a function which logically
   * returns a triple.  The values are packed in the interger return value
   * by the following masks.
   */
  static final int REFLECTION_GPRS_BITS = 5;
  static final int REFLECTION_GPRS_MASK = (1 << REFLECTION_GPRS_BITS) - 1;
  static final int REFLECTION_FPRS_BITS = 5;
  static final int REFLECTION_FPRS_MASK = (1 << REFLECTION_FPRS_BITS) - 1;

}
