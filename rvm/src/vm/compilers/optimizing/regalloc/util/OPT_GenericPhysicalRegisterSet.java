/*
 * (C) Copyright IBM Corp. 2001
 */
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
  abstract int getNumberOfPhysicalRegisters(); 

  /**
   * @return the FP register
   */
  abstract OPT_Register getFP();

  /**
   * @return the processor register
   */
  abstract OPT_Register getPR();

  /**
   * @return the nth physical GPR 
   */
  abstract OPT_Register getGPR(int n);

  /**
   * @return the first GPR return
   */
  abstract OPT_Register getFirstReturnGPR(); 

  /**
   * @return the nth physical FPR 
   */
  abstract OPT_Register getFPR(int n); 

  /**
   * @return the nth physical register in the pool. 
   */
  abstract OPT_Register get(int n);

  /**
   * Enumerate all the physical registers in this set.
   */
  abstract Enumeration enumerateAll();

  /**
   * Enumerate all the GPRs in this set.
   */
  abstract Enumeration enumerateGPRs(); 

  /**
   * Enumerate all the volatile GPRs in this set.
   */
  abstract Enumeration enumerateVolatileGPRs(); 

  /**
   * Enumerate all the nonvolatile GPRs in this set.
   */
  abstract Enumeration enumerateNonvolatileGPRs();

  /**
   * Enumerate all the volatile FPRs in this set.
   */
  abstract Enumeration enumerateVolatileFPRs();

  /**
   * Enumerate all the nonvolatile FPRs in this set.
   */
  abstract Enumeration enumerateNonvolatileFPRs();

  /**
   * Enumerate all the volatile physical registers
   */
  abstract Enumeration enumerateVolatiles(); 

  /**
   * Enumerate all the nonvolatile GPRs in this set, backwards
   */
  Enumeration enumerateNonvolatileGPRsBackwards() {
    return new OPT_ReverseEnumerator(enumerateNonvolatileGPRs());
  }

  /**
   * Enumerate all the nonvolatile FPRs in this set, backwards.
   */
  Enumeration enumerateNonvolatileFPRsBackwards() {
    return new OPT_ReverseEnumerator(enumerateNonvolatileFPRs());
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
