/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package com.ibm.jikesrvm.ia32;

/**
 * Trap constants for IA32 platform.
 * 
 * @author Bowen Alpern
 * @author David Grove
 */
public interface VM_TrapConstants {

  /** 
   * This base is added to the numeric trap codes in VM_Runtime.java
   * to yield the intel trap number that is given to INT instructions
   */
  byte RVM_TRAP_BASE = (byte) 0x40;
  
}
