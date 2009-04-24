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
package org.jikesrvm.compilers.opt.ir.ia32;

import java.util.Enumeration;
import org.jikesrvm.VM;
import org.jikesrvm.compilers.opt.OptimizingCompilerException;
import org.jikesrvm.compilers.opt.ir.GenericPhysicalRegisterSet;
import org.jikesrvm.compilers.opt.ir.Register;
import org.jikesrvm.compilers.opt.regalloc.ia32.PhysicalRegisterConstants;
import org.jikesrvm.compilers.opt.util.BitSet;
import org.jikesrvm.compilers.opt.util.CompoundEnumerator;
import org.jikesrvm.compilers.opt.util.EmptyEnumerator;
import org.jikesrvm.compilers.opt.util.ReverseEnumerator;
import org.jikesrvm.ia32.ArchConstants;
import org.jikesrvm.ia32.RegisterConstants;

/**
 * This class represents a set of Registers corresponding to the
 * IA32 register set.
 */
public abstract class PhysicalRegisterSet extends GenericPhysicalRegisterSet
    implements RegisterConstants, PhysicalRegisterConstants {

  /**
   * This array holds a pool of objects representing physical registers
   */
  private final Register[] reg = new Register[getSize()];

  /**
   * Cache the set of volatile registers for efficiency
   */
  private final BitSet volatileSet;

  /**
   * Cache the set of floating-point registers for efficiency
   */
  private final BitSet fpSet;

  /**
   * Return the total number of physical registers.
   */
  public static int getSize() {
    return NUM_GPRS + NUM_FPRS + NUM_SPECIALS;
  }

  /**
   * Return the total number of physical registers.
   */
  public final int getNumberOfPhysicalRegisters() {
    return getSize();
  }

  /**
   * Return the total number of nonvolatile GPRs.
   */
  public static int getNumberOfNonvolatileGPRs() {
    return NUM_NONVOLATILE_GPRS;
  }

  /**
   * Return the total number of GPRs that may hold parameters.
   */
  public static int getNumberOfGPRParams() {
    return NUM_PARAMETER_GPRS;
  }

  /**
   * Return the total number of FPRs that may hold parameters.
   */
  public static int getNumberOfFPRParams() {
    return NUM_PARAMETER_FPRS;
  }

  /**
   * Return the (zero-based indexed) nth GPR that may hold a parameter.
   */
  public final Register getGPRParam(int n) {
    if (VM.VerifyAssertions) VM._assert(n < 2);
    if (n == 0) {
      return getEAX();
    } else {
      return getEDX();
    }
  }

  /**
   * Return the (zero-based indexed) nth FPR that may hold a parameter.
   */
  public final Register getFPRParam(int n) {
    return getFPR(VOLATILE_FPRS[n]);
  }

  /**
   * Return the (zero-based indexed) nth GPR that may hold a return value.
   */
  public Register getReturnGPR(int n) {
    if (VM.VerifyAssertions) VM._assert(n < 2);
    if (n == 0) {
      return getEAX();
    } else {
      return getEDX();
    }
  }

  /**
   * Constructor: set up a pool of physical registers.
   */
  protected PhysicalRegisterSet() {

    // 1. Create all the physical registers in the pool.
    for (int i = 0; i < reg.length; i++) {
      Register r = new Register(i);
      r.setPhysical();
      reg[i] = r;
    }

    // 2. Set the 'integer' attribute on each GPR
    for (int i = FIRST_INT; i < FIRST_DOUBLE; i++) {
      reg[i].setInteger();
    }

    // 3. Set the 'double' attribute on each FPR
    for (int i = FIRST_DOUBLE; i < FIRST_SPECIAL; i++) {
      reg[i].setDouble();
    }

    // 4. set up the volatile GPRs
    for (Enumeration<Register> e = enumerateVolatileGPRs(); e.hasMoreElements();) {
      Register r = e.nextElement();
      r.setVolatile();
    }

    // 5. set up the non-volatile GPRs
    for (Enumeration<Register> e = enumerateNonvolatileGPRs(); e.hasMoreElements();) {
      Register r = e.nextElement();
      r.setNonVolatile();
    }

    // 6. set properties on some special registers
    reg[AF].setSpansBasicBlock();
    reg[CF].setSpansBasicBlock();
    reg[OF].setSpansBasicBlock();
    reg[PF].setSpansBasicBlock();
    reg[SF].setSpansBasicBlock();
    reg[ZF].setSpansBasicBlock();
    reg[C0].setSpansBasicBlock();
    reg[C1].setSpansBasicBlock();
    reg[C2].setSpansBasicBlock();
    reg[C3].setSpansBasicBlock();
    reg[THREAD_REGISTER.value()].setSpansBasicBlock();

    // For SSE2
    reg[ST0].setDouble();
    reg[ST1].setDouble();

    // 7. set up the volatile FPRs
    for (Enumeration<Register> e = enumerateVolatileFPRs(); e.hasMoreElements();) {
      Register r = e.nextElement();
      r.setVolatile();
    }

    // 8. set up the non-volatile FPRs
    for (Enumeration<Register> e = enumerateNonvolatileFPRs(); e.hasMoreElements();) {
      Register r = e.nextElement();
      r.setNonVolatile();
    }

    // 9. Cache the volatile registers for efficiency
    volatileSet = new BitSet(this);
    for (Enumeration<Register> e = enumerateVolatiles(); e.hasMoreElements();) {
      Register r = e.nextElement();
      volatileSet.add(r);
    }

    // 10. Cache the FPRs for efficiency
    fpSet = new BitSet(this);
    for (Enumeration<Register> e = enumerateFPRs(); e.hasMoreElements();) {
      Register r = e.nextElement();
      fpSet.add(r);
    }

    // Note no registers are excluded from live analysis (as is done for PPC)

  }

  /**
   * Is a particular register subject to allocation?
   */
  public boolean isAllocatable(Register r) {
    return (r.number < FIRST_SPECIAL && r != getTR() && r != getESP());
  }

  /**
   * @return the processor register
   */
  public Register getTR() {
    return getGPR(THREAD_REGISTER);
  }

  /**
   * @return the frame pointer register
   */
  public Register getFP() {
    throw new OptimizingCompilerException("Framepointer is not a register on IA32");
  }

  /**
   * @return the EAX register
   */
  public Register getEAX() {
    return getGPR(EAX);
  }

  /**
   * @return the ECX register
   */
  public Register getECX() {
    return getGPR(ECX);
  }

  /**
   * @return the EDX register
   */
  public Register getEDX() {
    return getGPR(EDX);
  }

  /**
   * @return the EBX register
   */
  public Register getEBX() {
    return getGPR(EBX);
  }

  /**
   * @return the ESP register
   */
  public Register getESP() {
    return getGPR(ESP);
  }

  /**
   * @return the EBP register
   */
  public Register getEBP() {
    return getGPR(EBP);
  }

  /**
   * @return the ESI register
   */
  public Register getESI() {
    return getGPR(ESI);
  }

  /**
   * @return the EDI register
   */
  public Register getEDI() {
    return getGPR(EDI);
  }

  /**
   * @return a register representing the AF bit of the EFLAGS register.
   */
  public Register getAF() {
    return reg[AF];
  }

  /**
   * @return a register representing the CF bit of the EFLAGS register.
   */
  public Register getCF() {
    return reg[CF];
  }

  /**
   * @return a register representing the OF bit of the EFLAGS register.
   */
  public Register getOF() {
    return reg[OF];
  }

  /**
   * @return a register representing the PF bit of the EFLAGS register.
   */
  public Register getPF() {
    return reg[PF];
  }

  /**
   * @return a register representing the SF bit of the EFLAGS register.
   */
  public Register getSF() {
    return reg[SF];
  }

  /**
   * @return a register representing the ZF bit of the EFLAGS register.
   */
  public Register getZF() {
    return reg[ZF];
  }

  /**
   * @return a register representing the C0 floating-point status bit
   */
  public Register getC0() {
    return reg[C0];
  }

  /**
   * @return a register representing the C1 floating-point status bit
   */
  public Register getC1() {
    return reg[C1];
  }

  /**
   * @return a register representing the C2 floating-point status bit
   */
  public Register getC2() {
    return reg[C2];
  }

  /**
   * @return a register representing the C3 floating-point status bit
   */
  public Register getC3() {
    return reg[C3];
  }

  /**
   * @return the nth physical GPR
   */
  public Register getGPR(GPR n) {
    return reg[FIRST_INT + n.value()];
  }

  /**
   * @return the nth physical GPR
   */
  public Register getGPR(int n) {
    return reg[FIRST_INT + n];
  }

  /**
   * @return the index into the GPR set corresponding to a given register.
   *
   * PRECONDITION: r is a physical GPR
   */
  public static int getGPRIndex(Register r) {
    return r.number - FIRST_INT;
  }

  /**
   * @return the first GPR register used to hold a return value
   */
  public Register getFirstReturnGPR() {
    if (VM.VerifyAssertions) VM._assert(NUM_RETURN_GPRS > 0);
    return getEAX();
  }

  /**
   * @return the second GPR register used to hold a return value
   */
  public Register getSecondReturnGPR() {
    if (VM.VerifyAssertions) VM._assert(NUM_RETURN_GPRS > 1);
    return getEDX();
  }

  /**
   * @return the FPR register used to hold a return value
   */
  public Register getST0() {
    if (VM.VerifyAssertions) VM._assert(NUM_RETURN_FPRS == 1);
    if (VM.VerifyAssertions) VM._assert(ArchConstants.SSE2_FULL);
    return reg[ST0];
  }
  /**
   * @return the special ST1 x87 register
   */
  public Register getST1() {
    if (VM.VerifyAssertions) VM._assert(ArchConstants.SSE2_FULL);
    return reg[ST1];
  }

  /**
   * @return the FPR register used to hold a return value
   */
  public Register getReturnFPR() {
    if (VM.VerifyAssertions) VM._assert(NUM_RETURN_FPRS == 1);
    return getFPR(0);
  }

  /**
   * @return the nth physical FPR
   */
  public Register getFPR(FloatingPointMachineRegister n) {
    return reg[FIRST_DOUBLE + n.value()];
  }

  /**
   * @return the nth physical FPR
   */
  public Register getFPR(int n) {
    return reg[FIRST_DOUBLE + n];
  }

  /**
   * @return the index into the GPR set corresponding to a given register.
   *
   * PRECONDITION: r is a physical GPR
   */
  public static int getFPRIndex(Register r) {
    return r.number - FIRST_DOUBLE;
  }

  /**
   * @return the nth physical register in the pool.
   */
  public Register get(int n) {
    return reg[n];
  }

  /**
   * Given a symbolic register, return a code that gives the physical
   * register type to hold the value of the symbolic register.
   * @param r a symbolic register
   * @return one of INT_REG, DOUBLE_REG
   */
  public static int getPhysicalRegisterType(Register r) {
    if (r.isInteger() || r.isLong() || r.isAddress()) {
      return INT_REG;
    } else if (r.isFloatingPoint()) {
      return DOUBLE_REG;
    } else {
      throw new OptimizingCompilerException("getPhysicalRegisterType " + " unexpected " + r);
    }
  }

  /**
   * Register names for each class. used in printing the IR
   */
  private static final String[] registerName = new String[getSize()];

  static {
    String[] regName = registerName;
    for (GPR r : GPR.values()) {
      regName[r.ordinal() + FIRST_INT] = r.toString();
    }
    if (ArchConstants.SSE2_FULL) {
      for (XMM r : XMM.values()) {
        regName[r.ordinal() + FIRST_DOUBLE] = r.toString();
      }
    } else {
      for (FPR r : FPR.values()) {
        regName[r.ordinal() + FIRST_DOUBLE] = r.toString();
      }
    }
    regName[THREAD_REGISTER.value()] = "TR";
    regName[AF] = "AF";
    regName[CF] = "CF";
    regName[OF] = "OF";
    regName[PF] = "PF";
    regName[SF] = "SF";
    regName[ZF] = "ZF";
    regName[ST0] = "ST0";
    regName[ST1] = "ST1";
  }

  /**
   * Get the register name for a register with a particular number in the
   * pool
   */
  public static String getName(int number) {
    return registerName[number];
  }

  /**
   * Get the spill size for a register with a particular type
   * @param type one of INT_REG, DOUBLE_REG, SPECIAL_REG
   */
  public static int getSpillSize(int type) {
    if (VM.VerifyAssertions) {
      VM._assert((type == INT_REG) || (type == DOUBLE_REG) || (type == SPECIAL_REG));
    }
    if (type == DOUBLE_REG) {
      return 8;
    } else {
      return 4;
    }
  }

  /**
   * Get the required spill alignment for a register with a particular type
   * @param type one of INT_REG, DOUBLE_REG,  SPECIAL_REG
   */
  public static int getSpillAlignment(int type) {
    if (VM.VerifyAssertions) {
      VM._assert((type == INT_REG) || (type == DOUBLE_REG) || (type == SPECIAL_REG));
    }
    if (type == DOUBLE_REG) {
      return 8;
    } else {
      return 4;
    }
  }

  /**
   * Enumerate all the physical registers in this set.
   */
  public Enumeration<Register> enumerateAll() {
    return new RangeEnumeration(0, getSize() - 1);
  }

  /**
   * Enumerate all the GPRs in this set.
   */
  public Enumeration<Register> enumerateGPRs() {
    return new RangeEnumeration(FIRST_INT, FIRST_DOUBLE - 1);
  }

  /**
   * Enumerate all the GPRs in this set.
   */
  public Enumeration<Register> enumerateFPRs() {
    return new RangeEnumeration(FIRST_DOUBLE, FIRST_SPECIAL - 1);
  }

  /**
   * Enumerate all the volatile GPRs in this set.
   */
  public PhysicalRegisterEnumeration enumerateVolatileGPRs() {
    Register[] r = new Register[NUM_VOLATILE_GPRS];
    for (int i = 0; i < NUM_VOLATILE_GPRS; i++) {
      r[i] = getGPR(VOLATILE_GPRS[i]);
    }
    return new PhysicalRegisterEnumeration(r);
  }

  /**
   * Enumerate all the nonvolatile GPRs in this set.
   */
  public PhysicalRegisterEnumeration enumerateNonvolatileGPRs() {
    Register[] r = new Register[NUM_NONVOLATILE_GPRS];
    for (int i = 0; i < NUM_NONVOLATILE_GPRS; i++) {
      r[i] = getGPR(NONVOLATILE_GPRS[i]);
    }
    return new PhysicalRegisterEnumeration(r);
  }

  /**
   * Enumerate the nonvolatile GPRS backwards
   */
  public Enumeration<Register> enumerateNonvolatileGPRsBackwards() {
    return new ReverseEnumerator<Register>(enumerateNonvolatileGPRs());
  }

  /**
   * Enumerate all the volatile FPRs in this set.
   */
  public PhysicalRegisterEnumeration enumerateVolatileFPRs() {
    Register[] r = new Register[NUM_VOLATILE_FPRS];
    for (int i = 0; i < NUM_VOLATILE_FPRS; i++) {
      r[i] = getFPR(VOLATILE_FPRS[i]);
    }
    return new PhysicalRegisterEnumeration(r);
  }

  /**
   * Enumerate all the nonvolatile FPRs in this set.
   */
  public PhysicalRegisterEnumeration enumerateNonvolatileFPRs() {
    Register[] r = new Register[NUM_NONVOLATILE_FPRS];
    for (int i = 0; i < NUM_NONVOLATILE_FPRS; i++) {
      r[i] = getFPR(NONVOLATILE_FPRS[i]);
    }
    return new PhysicalRegisterEnumeration(r);
  }

  /**
   * Enumerate the volatile physical registers of a given class.
   * @param regClass one of INT_REG, DOUBLE_REG, SPECIAL_REG
   */
  public Enumeration<Register> enumerateVolatiles(int regClass) {
    switch (regClass) {
      case INT_REG:
        return enumerateVolatileGPRs();
      case DOUBLE_REG:
        return enumerateVolatileFPRs();
      case SPECIAL_REG:
        return EmptyEnumerator.emptyEnumeration();
      default:
        throw new OptimizingCompilerException("Unsupported volatile type");
    }
  }

  /**
   * Enumerate all the volatile physical registers
   */
  public Enumeration<Register> enumerateVolatiles() {
    Enumeration<Register> e1 = enumerateVolatileGPRs();
    Enumeration<Register> e2 = enumerateVolatileFPRs();
    return new CompoundEnumerator<Register>(e1, e2);
  }

  /**
   * @return the set of volatile physical registers
   */
  public BitSet getVolatiles() {
    return volatileSet;
  }

  /**
   * @return the set of FPR physical registers
   */
  public BitSet getFPRs() {
    return fpSet;
  }

  /**
   * Enumerate the nonvolatile physical registers of a given class.
   * @param regClass one of INT_REG, DOUBLE_REG, SPECIAL_REG
   */
  public Enumeration<Register> enumerateNonvolatiles(int regClass) {
    switch (regClass) {
      case INT_REG:
        return enumerateNonvolatileGPRs();
      case DOUBLE_REG:
        return enumerateNonvolatileFPRs();
      case SPECIAL_REG:
        return EmptyEnumerator.emptyEnumeration();
      default:
        throw new OptimizingCompilerException("Unsupported non-volatile type");
    }
  }

  /**
   * Enumerate the nonvolatile physical registers of a given class,
   * backwards
   * @param regClass one of INT_REG, DOUBLE_REG, SPECIAL_REG
   */
  public Enumeration<Register> enumerateNonvolatilesBackwards(int regClass) {
    return new ReverseEnumerator<Register>(enumerateNonvolatiles(regClass));
  }

  /**
   * An enumerator for use by the physical register utilities.
   */
  static final class PhysicalRegisterEnumeration implements Enumeration<Register> {
    private int index;
    private final Register[] r;

    PhysicalRegisterEnumeration(Register[] r) {
      this.r = r;
      this.index = 0;
    }

    public Register nextElement() {
      return r[index++];
    }

    public boolean hasMoreElements() {
      return (index < r.length);
    }
  }

  /**
   * An enumerator for use by the physical register utilities.
   */
  final class RangeEnumeration implements Enumeration<Register> {
    private final int end;
    private int index;
    private final int exclude; // an index in the register range to exclude

    RangeEnumeration(int start, int end) {
      this.end = end;
      this.exclude = -1;
      this.index = start;
    }

    RangeEnumeration(int start, int end, int exclude) {
      this.end = end;
      this.exclude = exclude;
      this.index = start;
    }

    public Register nextElement() {
      if (index == exclude) index++;
      return reg[index++];
    }

    public boolean hasMoreElements() {
      if (index == exclude) index++;
      return (index <= end);
    }
  }
}
