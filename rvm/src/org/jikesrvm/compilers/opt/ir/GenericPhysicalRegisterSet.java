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
