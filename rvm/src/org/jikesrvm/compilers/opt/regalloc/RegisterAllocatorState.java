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
package org.jikesrvm.compilers.opt.regalloc;

import java.util.Enumeration;
import org.jikesrvm.ArchitectureSpecificOpt.PhysicalRegisterSet;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.ir.Register;

/**
 * The register allocator currently caches a bunch of state in the IR;
 * This class provides accessors to this state.
 * <ul>
 *   <li>TODO: Consider caching the state in a lookaside structure.
 *   <li>TODO: Currently, the physical registers are STATIC! fix this.
 * </ul>
 */
public class RegisterAllocatorState {

  /**
   * Resets the physical register info.
   *
   * @param ir the IR whose info is to be reset
   */
  static void resetPhysicalRegisters(IR ir) {
    PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
    for (Enumeration<Register> e = phys.enumerateAll(); e.hasMoreElements();) {
      Register reg = e.nextElement();
      reg.deallocateRegister();
      reg.mapsToRegister = null;  // mapping from real to symbolic
      reg.defList = null;
      reg.useList = null;
      setSpill(reg, 0);
    }
  }

  static void setSpill(Register reg, int spill) {
    reg.spillRegister();
    reg.setScratch(spill);
  }

  public static int getSpill(Register reg) {
    return reg.getScratch();
  }

  /**
   * Records that register A and register B are associated with each other
   * in a bijection.<p>
   *
   * The register allocator uses this state to indicate that a symbolic
   * register is presently allocated to a physical register.
   *
   * @param A first register
   * @param B second register
   */
  static void mapOneToOne(Register A, Register B) {
    Register aFriend = getMapping(A);
    Register bFriend = getMapping(B);
    if (aFriend != null) {
      aFriend.mapsToRegister = null;
    }
    if (bFriend != null) {
      bFriend.mapsToRegister = null;
    }
    A.mapsToRegister = B;
    B.mapsToRegister = A;
  }

  /**
   * @param r a register
   * @return the register currently mapped 1-to-1 to r
   */
  static Register getMapping(Register r) {
    return r.mapsToRegister;
  }

  /**
   * Clears any 1-to-1 mapping for a register.
   *
   * @param r the register whose mapping is to be cleared
   */
  static void clearOneToOne(Register r) {
    if (r != null) {
      Register s = getMapping(r);
      if (s != null) {
        s.mapsToRegister = null;
      }
      r.mapsToRegister = null;
    }
  }
}
