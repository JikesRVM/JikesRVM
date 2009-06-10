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
package org.jikesrvm.ia32;

/**
 * Trap constants for IA32 platform.
 */
public interface TrapConstants {

  /**
   * This base is added to the numeric trap codes in RuntimeEntrypoints.java
   * to yield the intel trap number that is given to INT instructions
   */
  byte RVM_TRAP_BASE = (byte) 0x40;

}
