/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.compilers.opt.ir;

import java.util.Enumeration;
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
   * Return the total number of physical registers.
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

  /**
   * @return the nth physical GPR
   */
  public abstract Register getGPR(int n);

  /**
   * @return the first GPR return
   */
  public abstract Register getFirstReturnGPR();

  /**
   * @return the nth physical FPR
   */
  public abstract Register getFPR(int n);

  /**
   * @return the nth physical register in the pool.
   */
  public abstract Register get(int n);

  /**
   * Enumerate all the physical registers in this set.
   */
  public abstract Enumeration<Register> enumerateAll();

  /**
   * Enumerate all the GPRs in this set.
   */
  public abstract Enumeration<Register> enumerateGPRs();

  /**
   * Enumerate all the volatile GPRs in this set.
   */
  public abstract Enumeration<Register> enumerateVolatileGPRs();

  /**
   * Enumerate all the nonvolatile GPRs in this set.
   */
  public abstract Enumeration<Register> enumerateNonvolatileGPRs();

  /**
   * Enumerate all the volatile FPRs in this set.
   */
  public abstract Enumeration<Register> enumerateVolatileFPRs();

  /**
   * Enumerate all the nonvolatile FPRs in this set.
   */
  public abstract Enumeration<Register> enumerateNonvolatileFPRs();

  /**
   * Enumerate all the volatile physical registers
   */
  public abstract Enumeration<Register> enumerateVolatiles();

  /**
   * Enumerate all the nonvolatile GPRs in this set, backwards
   */
  public Enumeration<Register> enumerateNonvolatileGPRsBackwards() {
    return new ReverseEnumerator<Register>(enumerateNonvolatileGPRs());
  }

  /**
   * Enumerate all the nonvolatile FPRs in this set, backwards.
   */
  public Enumeration<Register> enumerateNonvolatileFPRsBackwards() {
    return new ReverseEnumerator<Register>(enumerateNonvolatileFPRs());
  }

  /**
   * Implementation of the BitSetMapping interface.
   */
  public final Object getMappedObject(int n) {
    return get(n);
  }

  /**
   * Implementation of the BitSetMapping interface.
   */
  public final int getMappedIndex(Object o) {
    Register r = (Register) o;
    return r.number;
  }

  /**
   * Implementation of the BitSetMapping interface.
   */
  public final int getMappingSize() {
    return getNumberOfPhysicalRegisters();
  }
}
