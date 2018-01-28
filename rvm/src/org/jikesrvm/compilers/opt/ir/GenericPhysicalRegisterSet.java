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
package org.jikesrvm.compilers.opt.ir;

import java.util.Enumeration;
import org.jikesrvm.architecture.MachineRegister;
import org.jikesrvm.VM;
import org.jikesrvm.compilers.opt.util.BitSetMapping;
import org.jikesrvm.compilers.opt.util.ReverseEnumerator;

/**
 * This class represents a set of Registers corresponding to the
 * physical register set. This class holds the architecture-independent
 * functionality
 *
 * <P> Implementation Note: Each register has an integer field
 * Register.number.  This class must number the physical registers so
 * that get(n) returns an Register r with r.number = n!
 */
public abstract class GenericPhysicalRegisterSet implements BitSetMapping {
  /**
   * @return the total number of physical registers
   */
  public static int getSize() {
    if (VM.BuildForIA32) {
      return org.jikesrvm.compilers.opt.ir.ia32.PhysicalRegisterSet.getSize();
    } else {
      if (VM.VerifyAssertions) VM._assert(VM.BuildForPowerPC);
      return org.jikesrvm.compilers.opt.ir.ppc.PhysicalRegisterSet.getSize();
    }
  }

  /**
   * @param regnum the number of the register in question
   * @return the register name for a register with a particular number in the
   * pool
   */
  public static String getName(int regnum) {
    if (VM.BuildForIA32) {
      return org.jikesrvm.compilers.opt.ir.ia32.PhysicalRegisterSet.getName(regnum);
    } else {
      if (VM.VerifyAssertions) VM._assert(VM.BuildForPowerPC);
      return org.jikesrvm.compilers.opt.ir.ppc.PhysicalRegisterSet.getName(regnum);
    }
  }

  public static int getPhysicalRegisterType(Register symbReg) {
    if (VM.BuildForIA32) {
      return org.jikesrvm.compilers.opt.ir.ia32.PhysicalRegisterSet.getPhysicalRegisterType(symbReg);
    } else {
      if (VM.VerifyAssertions) VM._assert(VM.BuildForPowerPC);
      return org.jikesrvm.compilers.opt.ir.ppc.PhysicalRegisterSet.getPhysicalRegisterType(symbReg);
    }
  }

  public org.jikesrvm.compilers.opt.ir.ia32.PhysicalRegisterSet asIA32() {
    return (org.jikesrvm.compilers.opt.ir.ia32.PhysicalRegisterSet)this;
  }

  public org.jikesrvm.compilers.opt.ir.ppc.PhysicalRegisterSet asPPC() {
    return (org.jikesrvm.compilers.opt.ir.ppc.PhysicalRegisterSet)this;
  }

  /**
   * @param p the register in question
   * @return whether the register is subject to allocation
   */
  public abstract boolean isAllocatable(Register p);

  /**
   * @return the total number of physical registers.
   */
  public abstract int getNumberOfPhysicalRegisters();

  /**
   * @return the FP register
   */
  public abstract Register getFP();

  /**
   * @return the thread register
   */
  public abstract Register getTR();

  public abstract Register getGPR(int n);

  /**
   * @param n a register
   * @return the physical GPR corresponding to n
   */
  public abstract Register getGPR(MachineRegister n);

  /**
   * @return the first GPR return
   */
  public abstract Register getFirstReturnGPR();

  public abstract Register getFPR(int n);

  /**
   * @param n register number
   * @return the nth physical register in the pool.
   */
  public abstract Register get(int n);

  public abstract Enumeration<Register> enumerateAll();

  public abstract Enumeration<Register> enumerateGPRs();

  public abstract Enumeration<Register> enumerateVolatileGPRs();

  public abstract Enumeration<Register> enumerateNonvolatileGPRs();

  public abstract Enumeration<Register> enumerateVolatileFPRs();

  public abstract Enumeration<Register> enumerateNonvolatileFPRs();

  public abstract Enumeration<Register> enumerateVolatiles();

  public abstract Enumeration<Register> enumerateVolatiles(int type);

  public abstract Enumeration<Register> enumerateNonvolatilesBackwards(int type);

  public Enumeration<Register> enumerateNonvolatileGPRsBackwards() {
    return new ReverseEnumerator<Register>(enumerateNonvolatileGPRs());
  }

  public Enumeration<Register> enumerateNonvolatileFPRsBackwards() {
    return new ReverseEnumerator<Register>(enumerateNonvolatileFPRs());
  }

  @Override
  public final Object getMappedObject(int n) {
    return get(n);
  }

  @Override
  public final int getMappedIndex(Object o) {
    Register r = (Register) o;
    return r.number;
  }

  @Override
  public final int getMappingSize() {
    return getNumberOfPhysicalRegisters();
  }
}
