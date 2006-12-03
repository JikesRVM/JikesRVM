/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.jikesrvm.opt.ir;

import com.ibm.jikesrvm.opt.*;
import java.util.Enumeration;

/**
 * This class represents a set of OPT_Registers corresponding to the
 * physical register set. This class holds the architecture-independent
 * functionality
 *
 * <P> Implementation Note: Each register has an integer field
 * OPT_Register.number.  This class must number the physical registers so
 * that get(n) returns an OPT_Register r with r.number = n!
 *
 * @author Stephen Fink
 */
abstract class OPT_GenericPhysicalRegisterSet implements OPT_BitSetMapping {

  /**
   * Return the total number of physical registers.
   */
  public abstract int getNumberOfPhysicalRegisters(); 

  /**
   * @return the FP register
   */
  public abstract OPT_Register getFP();

  /**
   * @return the processor register
   */
  public abstract OPT_Register getPR();

  /**
   * @return the nth physical GPR 
   */
  public abstract OPT_Register getGPR(int n);

  /**
   * @return the first GPR return
   */
  public abstract OPT_Register getFirstReturnGPR(); 

  /**
   * @return the nth physical FPR 
   */
  public abstract OPT_Register getFPR(int n); 

  /**
   * @return the nth physical register in the pool. 
   */
  public abstract OPT_Register get(int n);

  /**
   * Enumerate all the physical registers in this set.
   */
  public abstract Enumeration<OPT_Register> enumerateAll();

  /**
   * Enumerate all the GPRs in this set.
   */
  public abstract Enumeration<OPT_Register> enumerateGPRs(); 

  /**
   * Enumerate all the volatile GPRs in this set.
   */
  public abstract Enumeration<OPT_Register> enumerateVolatileGPRs(); 

  /**
   * Enumerate all the nonvolatile GPRs in this set.
   */
  public abstract Enumeration<OPT_Register> enumerateNonvolatileGPRs();

  /**
   * Enumerate all the volatile FPRs in this set.
   */
  public abstract Enumeration<OPT_Register> enumerateVolatileFPRs();

  /**
   * Enumerate all the nonvolatile FPRs in this set.
   */
  public abstract Enumeration<OPT_Register> enumerateNonvolatileFPRs();

  /**
   * Enumerate all the volatile physical registers
   */
  public abstract Enumeration<OPT_Register> enumerateVolatiles(); 

  /**
   * Enumerate all the nonvolatile GPRs in this set, backwards
   */
  public Enumeration<OPT_Register> enumerateNonvolatileGPRsBackwards() {
    return new OPT_ReverseEnumerator<OPT_Register>(enumerateNonvolatileGPRs());
  }

  /**
   * Enumerate all the nonvolatile FPRs in this set, backwards.
   */
  public Enumeration<OPT_Register> enumerateNonvolatileFPRsBackwards() {
    return new OPT_ReverseEnumerator<OPT_Register>(enumerateNonvolatileFPRs());
  }

  /**
   * Implementation of the OPT_BitSetMapping interface.
   */
  public final Object getMappedObject(int n) {
    return get(n);
  }

  /**
   * Implementation of the OPT_BitSetMapping interface.
   */
  public final int getMappedIndex(Object o) {
    OPT_Register r = (OPT_Register)o;
    return r.number;
  }

  /**
   * Implementation of the OPT_BitSetMapping interface.
   */
  public final int getMappingSize() {
    return getNumberOfPhysicalRegisters();
  }
}
