/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt.ir;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.opt.*;

import java.util.Enumeration;

/**
 * This class represents a set of OPT_Registers corresponding to the
 * PowerPC register set.
 *
 * <P> Implementation Notes:
 * <P> Due to some historical ugliness not yet cleaned up, the register
 * allocator depend on properties cached in the 
 * <code>next</code> field of OPT_Register.  The constructor sets the
 * following properties:
 * <ul>
 * <li> The volatile GPRs form a linked list, starting with
 * FIRST_VOLATILE_GPR and ending with LAST_SCRATCH_GPR
 * <li> The volatile FPRs form a linked list, starting with
 * FIRST_VOLATILE_FPR and ending with LAST_SCRATCH_FPR
 * <li> The non-volatile GPRs form a linked list, starting with
 * FIRST_NONVOLATILE_GPR and ending with LAST_NONVOLATILE_GPR
 * <li> The non-volatile FPRs form a linked list, starting with
 * FIRST_NONVOLATILE_FPR and ending with LAST_NONVOLATILE_FPR
 * <li> The condition registers from a linked list, starting with
 * FIRST_CONDITION and ending with LAST_CONDITION-1, which opt reserves for yieldpoints.
 * </ul>
 * <P> The register allocator allocates registers according to the order
 * in these lists.  For volatile registers, it traverses the lists in
 * order, starting with getFirstVolatile() and traversing with getNext().  
 * For non-volatiles, it traverses the lists
 * <STRONG> Backwards </STRONG>, starting with getLastNonvolatile() and 
 * using getPrev().
 * <P> TODO; clean up all this and provide appropriate enumerators
 * 
 * @author Stephen Fink
 * @author Mauricio J. Serrano
 */
public final class OPT_PhysicalRegisterSet extends OPT_GenericPhysicalRegisterSet 
  implements VM_RegisterConstants, OPT_PhysicalRegisterConstants{

  /**
   * This array holds a pool of objects representing physical registers
   */
  private OPT_Register[] reg = new OPT_Register[getSize()];

  /**
   * The set of volatile registers; cached for efficiency
   */
  private OPT_BitSet volatileSet;

  /**
   * The condition registers that we allocate
   * To avoid expensive save/restores when
   * making a JNI transition Jikes RVM only uses the
   * CR that AIX defines to be volatile
   * 
   * We reserve one of the volatiles, CR7 for use only in yieldpoints.
   * This ensures that a yieldpoint won't bash an allocated CR.
   */
  private static final int[] CR_NUMS = new int[] {0, 1, 5, 6};
  private static final int TSR_REG = 7;

  /**
   * Return the total number of physical registers.
   */
  public static final int getSize() {
    return NUM_GPRS + NUM_FPRS + NUM_CRS + NUM_SPECIALS;
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
  public static final int getNumberOfNonvolatileGPRs() {
    return NUM_NONVOLATILE_GPRS;
  }

  /**
   * Return the total number of nonvolatile FPRs.
   */
  public static final int getNumberOfNonvolatileFPRs() {
    return NUM_NONVOLATILE_FPRS;
  }

  /**
   * Constructor: set up a pool of physical registers.
   */
  OPT_PhysicalRegisterSet() {
    
    // 1. Create all the physical registers in the pool.
    for (int i = 0; i < reg.length ; i++) {
      OPT_Register r = new OPT_Register(i);
      r.setPhysical();
      reg[i] = r;
    }
    
    // 2. Set the 'integer' attribute on each GPR
    for (int i = FIRST_INT; i < FIRST_DOUBLE; i++) {
      //-#if RVM_FOR_32_ADDR
      reg[i].setInteger();
      //-#endif
      //-#if RVM_FOR_64_ADDR
      reg[i].setLong();
      //-#endif
    }
    
    // 3. Set the 'double' attribute on each FPR
    for (int i = FIRST_DOUBLE; i < FIRST_CONDITION; i++) {
      reg[i].setDouble();
    }

    // 4. Set the 'condition' attribute on each CR
    for (int i = FIRST_CONDITION; i < FIRST_SPECIAL; i++) {
      reg[i].setCondition();
    }

    // 5. set up the volatile GPRs
    for (int i = FIRST_VOLATILE_GPR; i < LAST_VOLATILE_GPR; i++) {
      OPT_Register r= reg[i];
      r.setVolatile();
      r.linkWithNext(reg[i + 1]);
    }
    reg[LAST_VOLATILE_GPR].setVolatile();
    reg[LAST_VOLATILE_GPR].linkWithNext(reg[FIRST_SCRATCH_GPR]);
    for (int i = FIRST_SCRATCH_GPR; i < LAST_SCRATCH_GPR; i++) {
      OPT_Register r = reg[i];
      r.setVolatile();
      r.linkWithNext(reg[i + 1]);
    }
    reg[LAST_SCRATCH_GPR].setVolatile();
    
    // 6. set up the non-volatile GPRs
    for (int i = FIRST_NONVOLATILE_GPR; i < LAST_NONVOLATILE_GPR; i++) {
      OPT_Register r = reg[i];
      r.setNonVolatile();
      r.linkWithNext(reg[i + 1]);
    }

    // 7. set properties on some special registers
    reg[PROCESSOR_REGISTER].setSpansBasicBlock();
    reg[FRAME_POINTER].setSpansBasicBlock();
    reg[JTOC_POINTER].setSpansBasicBlock();

    // 8. set up the volatile FPRs
    for (int i = FIRST_DOUBLE + FIRST_VOLATILE_FPR; i < FIRST_DOUBLE + 
        LAST_VOLATILE_FPR; i++) {
      OPT_Register r = reg[i];
      r.setVolatile();
      r.linkWithNext(reg[i + 1]);
    }
    reg[FIRST_DOUBLE + LAST_VOLATILE_FPR].linkWithNext(reg[FIRST_DOUBLE
        + FIRST_SCRATCH_FPR]);
    reg[FIRST_DOUBLE + LAST_VOLATILE_FPR].setVolatile();
    if (FIRST_SCRATCH_FPR != LAST_SCRATCH_FPR)
      for (int i = FIRST_DOUBLE + FIRST_SCRATCH_FPR; i < FIRST_DOUBLE + 
          LAST_SCRATCH_FPR; i++) {
        OPT_Register r = reg[i];
        r.setVolatile();
        r.linkWithNext(reg[i + 1]);
      }
    reg[FIRST_DOUBLE + LAST_SCRATCH_FPR].setVolatile();


    // 9. set up the non-volatile FPRs
    for (int i = FIRST_DOUBLE + FIRST_NONVOLATILE_FPR; i < FIRST_DOUBLE
        + LAST_NONVOLATILE_FPR; i++) {
      OPT_Register r = reg[i];
      r.setNonVolatile();
      r.linkWithNext(reg[i + 1]);
    }

    // 10. set up the condition registers
    int firstCR = -1;
    int prevCR = -1;
    for (int idx = 0; idx < CR_NUMS.length; idx++) {
      int i = CR_NUMS[idx];
      reg[FIRST_CONDITION + i].setVolatile();
      if (prevCR != -1)
        reg[FIRST_CONDITION + prevCR].linkWithNext(reg[FIRST_CONDITION + i]);
      prevCR = i;
      if (firstCR == -1)
        firstCR = i;
    }

    // 11. cache the volatiles for efficiency
    volatileSet = new OPT_BitSet(this);
    for (Enumeration e = enumerateVolatiles(); e.hasMoreElements(); ) {
      OPT_Register r = (OPT_Register)e.nextElement();
      volatileSet.add(r);
    }

    // 12. Show which registers should be excluded from live analysis
    reg[CTR].setExcludedLiveA();
    reg[CR].setExcludedLiveA();
    reg[TU].setExcludedLiveA();
    reg[TL].setExcludedLiveA();
    reg[XER].setExcludedLiveA();
    reg[FRAME_POINTER].setExcludedLiveA();
    reg[JTOC_POINTER].setExcludedLiveA();
    reg[LR].setExcludedLiveA();
  }

  /**
   * Is a certain physical register allocatable?
   */
  public boolean isAllocatable(OPT_Register r) {
    switch(r.number) {
      case PROCESSOR_REGISTER:
      case FRAME_POINTER:
      case JTOC_POINTER:
        return false;
      default:
        return (r.number < FIRST_SPECIAL);
    }
  }

  /**
   * @return the XER register.
   */
  public OPT_Register getXER() {
    return reg[XER];
  }

  /**
   * @return the LR register;.
   */
  public OPT_Register getLR() {
    return reg[LR];
  }

  /**
   * @return the CTR register
   */
  public OPT_Register getCTR() {
    return reg[CTR];
  }

  /**
   * @return the TU register
   */
  public OPT_Register getTU() {
    return reg[TU];
  }

  /**
   * @return the TL register
   */
  public OPT_Register getTL() {
    return reg[TL];
  }

  /**
   * @return the CR register
   */
  public OPT_Register getCR() {
    return reg[CR];
  }

  /**
   * @return the JTOC register
   */
  public OPT_Register getJTOC() {
    return reg[JTOC_POINTER];
  }

  /**
   * @return the FP registers
   */
  public OPT_Register getFP() {
    return reg[FRAME_POINTER];
  }

  /**
   * @return the processor register
   */
  public OPT_Register getPR() {
    return reg[PROCESSOR_REGISTER];
  }

  /**
   * @return the thread-switch register
   */
  public OPT_Register getTSR() {
    return reg[FIRST_CONDITION + TSR_REG]; 
  }

  /**
   * @return the nth physical GPR 
   */
  public OPT_Register getGPR(int n) {
    return reg[FIRST_INT + n];
  }

  /**
   * @return the first scratch GPR
   */
  public OPT_Register getFirstScratchGPR() {
    return reg[FIRST_SCRATCH_GPR];
  }

  /**
   * @return the last scratch GPR
   */
  public OPT_Register getLastScratchGPR() {
    return reg[LAST_SCRATCH_GPR];
  }

  /**
   * @return the first volatile GPR
   */
  public OPT_Register getFirstVolatileGPR() {
    return reg[FIRST_INT + FIRST_VOLATILE_GPR];
  }

  /**
   * @return the first nonvolatile GPR
   */
  public OPT_Register getFirstNonvolatileGPR() {
    return reg[FIRST_INT + FIRST_NONVOLATILE_GPR];
  }

  /**
   * @return the last nonvolatile GPR
   */
  public OPT_Register getLastNonvolatileGPR() {
    return reg[FIRST_INT + LAST_NONVOLATILE_GPR];
  }

  /**
   * @return the first GPR return
   */
  public OPT_Register getFirstReturnGPR() {
    return reg[FIRST_INT_RETURN];
  }

  /**
   * @return the nth physical FPR 
   */
  public OPT_Register getFPR(int n) {
    return reg[FIRST_DOUBLE + n];
  }

  /**
   * @return the first scratch FPR
   */
  public OPT_Register getFirstScratchFPR() {
    return reg[FIRST_DOUBLE + FIRST_SCRATCH_FPR];
  }

  /**
   * @return the first volatile FPR
   */
  public OPT_Register getFirstVolatileFPR() {
    return reg[FIRST_DOUBLE + FIRST_VOLATILE_FPR];
  }

  /**
   * @return the last scratch FPR
   */
  public OPT_Register getLastScratchFPR() {
    return reg[FIRST_DOUBLE + LAST_SCRATCH_FPR];
  }

  /**
   * @return the first nonvolatile FPR
   */
  public OPT_Register getFirstNonvolatileFPR() {
    return reg[FIRST_DOUBLE + FIRST_NONVOLATILE_FPR];
  }

  /**
   * @return the last nonvolatile FPR
   */
  public OPT_Register getLastNonvolatileFPR() {
    return reg[FIRST_DOUBLE + LAST_NONVOLATILE_FPR];
  }


  /**
   * @return the nth physical condition register 
   */
  public OPT_Register getConditionRegister(int n) {
    return reg[FIRST_CONDITION + n];
  }

  /**
   * @return the first condition
   */
  public OPT_Register getFirstConditionRegister() {
    return reg[FIRST_CONDITION];
  }

  /**
   * @return the first volatile CR
   */
  public OPT_Register getFirstVolatileConditionRegister() {
    if (VM.VerifyAssertions) {
      VM._assert(getFirstConditionRegister() != getTSR());
    }
    return getFirstConditionRegister();
  }
  /**
   * @return the nth physical register in the pool. 
   */
  public OPT_Register get(int n) {
    return reg[n];
  }

  /**
   * @return the first volatile physical register of a given class
   * @param regClass one of INT_REG, DOUBLE_REG, CONDITION_REG, or
   * SPECIAL_REG
   */
  public OPT_Register getFirstVolatile(int regClass) {
    switch (regClass) {
      case INT_REG:
        return getFirstVolatileGPR();
      case DOUBLE_REG:
        return getFirstVolatileFPR();
      case CONDITION_REG:
        return getFirstVolatileConditionRegister();
      case SPECIAL_REG:
        return null;
      default:
        throw new OPT_OptimizingCompilerException("Unknown register class");
    }
  }

  /**
   * @return the first nonvolatile physical register of a given class
   * @param regClass one of INT_REG, DOUBLE_REG, CONDITION_REG, or
   * SPECIAL_REG
   */
  public OPT_Register getLastNonvolatile(int regClass) {
    switch (regClass) {
      case INT_REG:
        return getLastNonvolatileGPR();
      case DOUBLE_REG:
        return getLastNonvolatileFPR();
      case CONDITION_REG:
        return null;
      case SPECIAL_REG:
        return null;
      default:
        throw new OPT_OptimizingCompilerException("Unknown register class");
    }
  }

  /**
   * Given a symbolic register, return a cdoe that gives the physical
   * register type to hold the value of the symbolic register.
   * @param r a symbolic register
   * @return one of INT_REG, DOUBLE_REG, or CONDITION_REG
   */
  public static final int getPhysicalRegisterType(OPT_Register r) {
    if (r.isInteger() || r.isLong() || r.isAddress()) {
      return INT_REG;
    } else if (r.isFloatingPoint()) {
      return DOUBLE_REG;
    } else if (r.isCondition()) {
      return CONDITION_REG;
    } else {
      throw new OPT_OptimizingCompilerException("getPhysicalRegisterType "
                                                + " unexpected " + r);
    }
  }

  /**
   * Given a physical register (XER, LR, or CTR), return the integer that
   * denotes the PowerPC Special Purpose Register (SPR) in the PPC
   * instruction set.  See p.129 of PPC ISA book
   */
  public final byte getSPR(OPT_Register r) {
    if (VM.VerifyAssertions) {
      VM._assert ( (r == getXER()) || (r == getLR()) || (r == getCTR()) ); 
    } 
    if (r == getXER()) {
      return 1;
    } else if (r == getLR()) {
      return 8;
    } else if (r == getCTR()) {
      return 9;
    } else {
      throw new OPT_OptimizingCompilerException("Invalid SPR");
    }
  };
  /**
   * register names for each class. used in printing the IR
   * The indices for "FP" and "JTOC" should always match the
   * final static values of int FP and int JTOC defined below.
   */
  private static final String registerName[] = new String[getSize()];
  static {
    String regName[] = registerName;
    for (int i = 0; i < NUM_GPRS; i++)
      regName[i + FIRST_INT] = "R" + i;
    for (int i = 0; i < NUM_FPRS; i++)
      regName[i + FIRST_DOUBLE] = "F" + i;
    for (int i = 0; i < NUM_CRS; i++)
      regName[i + FIRST_CONDITION] = "C" + i;
    regName[JTOC_POINTER] = "JTOC";
    regName[FRAME_POINTER] = "FP";
    regName[PROCESSOR_REGISTER] = "PR";
    regName[XER] = "XER";
    regName[LR] = "LR";
    regName[CTR] = "CTR";
    regName[TU] = "TU";
    regName[TL] = "TL";
    regName[CR] = "CR";
  }
  static final int TEMP = FIRST_INT;   // temporary register (currently r0)
  /**
   * @return R0, a temporary register
   */
  public OPT_Register getTemp() {
    return reg[TEMP];
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
   * @param type one of INT_REG, DOUBLE_REG, CONDITION_REG, SPECIAL_REG
   */
  public static int getSpillSize(int type) {
    if (VM.VerifyAssertions) {
      VM._assert( (type == INT_REG) || (type == DOUBLE_REG) ||
                 (type == CONDITION_REG) || (type == SPECIAL_REG));
    }
    if (type == DOUBLE_REG) {
      return BYTES_IN_DOUBLE;
    } else {
      return BYTES_IN_ADDRESS;
    }
  }
  /**
   * Get the required spill alignment for a register with a particular type
   * @param type one of INT_REG, DOUBLE_REG, CONDITION_REG, SPECIAL_REG
   */
  public static int getSpillAlignment(int type) {
    if (VM.VerifyAssertions) {
      VM._assert( (type == INT_REG) || (type == DOUBLE_REG) ||
                 (type == CONDITION_REG) || (type == SPECIAL_REG));
    }
    if (type == DOUBLE_REG) {
      return BYTES_IN_DOUBLE;
    } else {
      return BYTES_IN_ADDRESS;
    }
  }

  /**
   * Enumerate all the physical registers in this set.
   */
  public Enumeration enumerateAll() {
    return new PhysicalRegisterEnumeration(0,getSize()-1);
  }

  /**
   * Enumerate all the GPRs in this set.
   */
  public Enumeration enumerateGPRs() {
    return new PhysicalRegisterEnumeration(FIRST_INT,FIRST_DOUBLE-1);
  }

  /**
   * Enumerate all the volatile GPRs in this set.
   * NOTE: This assumes the scratch GPRs are numbered immediately 
   * <em> after </em> the volatile GPRs
   */
  public Enumeration enumerateVolatileGPRs() {
    return new PhysicalRegisterEnumeration(FIRST_INT+FIRST_VOLATILE_GPR,
                                           FIRST_INT+LAST_SCRATCH_GPR);
  }

  static {
    // enumerateVolatileGPRs relies on volatiles & scratches being
    // contiguous; so let's make sure that is the case!
    if (VM.VerifyAssertions)
      VM._assert(LAST_VOLATILE_GPR+1 == FIRST_SCRATCH_GPR);
  }
  
  
  /**
   * Enumerate the first n GPR parameters.
   */
  public Enumeration enumerateGPRParameters(int n) {
    if (VM.VerifyAssertions)
      VM._assert(n <= NUMBER_INT_PARAM);
    return new PhysicalRegisterEnumeration(FIRST_INT_PARAM,
                                           FIRST_INT_PARAM + n - 1);
  }

  /**
   * Enumerate all the nonvolatile GPRs in this set.
   */
  public Enumeration enumerateNonvolatileGPRs() {
    return new
      PhysicalRegisterEnumeration(FIRST_INT+FIRST_NONVOLATILE_GPR,
                                  FIRST_INT+LAST_NONVOLATILE_GPR);
  }


  /**
   * Enumerate all the volatile FPRs in this set.
   * NOTE: This assumes the scratch FPRs are numbered immediately 
   * <em> before</em> the volatile FPRs
   */
  public Enumeration enumerateVolatileFPRs() {
    return new PhysicalRegisterEnumeration(FIRST_DOUBLE+FIRST_SCRATCH_FPR,
                                           FIRST_DOUBLE+LAST_VOLATILE_FPR);
  }

  /**
   * Enumerate the first n FPR parameters.
   */
  public Enumeration enumerateFPRParameters(int n) {
    if (VM.VerifyAssertions)
      VM._assert(n <= NUMBER_DOUBLE_PARAM);
    return new PhysicalRegisterEnumeration(FIRST_DOUBLE_PARAM,
                                           FIRST_DOUBLE_PARAM + n - 1);
  }

  /**
   * Enumerate all the nonvolatile FPRs in this set.
   */
  public Enumeration enumerateNonvolatileFPRs() {
    return new
      PhysicalRegisterEnumeration(FIRST_DOUBLE+FIRST_NONVOLATILE_FPR,
                                  FIRST_DOUBLE+LAST_NONVOLATILE_FPR);
  }

  /**
   * Enumerate the volatile physical condition registers.
   * Note that the TSR is non-volatile.
   */
  public Enumeration enumerateVolatileConditionRegisters() {
    return new Enumeration() {
        private int idx = 0;
        public Object nextElement() {
          return reg[FIRST_CONDITION + CR_NUMS[idx++]];
        }
        public boolean hasMoreElements() {
          return idx < CR_NUMS.length;
        }
      };
  }

  /**
   * Enumerate the non-volatile physical condition registers.
   * Note that only the TSR is non-volatile.
   */
  public Enumeration enumerateNonvolatileConditionRegisters() {
    return new PhysicalRegisterEnumeration(0, -1);
  }
  /** 
   * Enumerate the volatile physical registers of a given class.
   * @param regClass one of INT_REG, DOUBLE_REG, CONDITION_REG
   */
  public Enumeration enumerateVolatiles(int regClass) {
    switch (regClass) {
      case INT_REG:
        return enumerateVolatileGPRs();
      case DOUBLE_REG:
        return enumerateVolatileFPRs();
      case CONDITION_REG:
        return enumerateVolatileConditionRegisters();
      case SPECIAL_REG:
        return OPT_EmptyEnumerator.EMPTY;
      default:
        throw new OPT_OptimizingCompilerException("Unsupported volatile type");
    }
  }

  /**
   * Enumerate all the volatile physical registers
   */
  public Enumeration enumerateVolatiles() {
    Enumeration e1 = enumerateVolatileGPRs();
    Enumeration e2 = enumerateVolatileFPRs();
    Enumeration e3 = enumerateVolatileConditionRegisters();
    return new OPT_CompoundEnumerator(e1, new
                                      OPT_CompoundEnumerator(e2,e3));
  }

  /**
   * Return a set of all the volatile registers.
   */
  public OPT_BitSet getVolatiles() {
    return volatileSet;
  }

  /** 
   * Enumerate the nonvolatile physical registers of a given class.
   * @param regClass one of INT_REG, DOUBLE_REG, CONDITION_REG
   */
  public Enumeration enumerateNonvolatiles(int regClass) {
    switch (regClass) {
      case INT_REG:
        return enumerateNonvolatileGPRs();
      case DOUBLE_REG:
        return enumerateNonvolatileFPRs();
      case CONDITION_REG:
        return enumerateNonvolatileConditionRegisters();
      case SPECIAL_REG:
        return OPT_EmptyEnumerator.EMPTY;
      default:
        throw new OPT_OptimizingCompilerException
          ("Unsupported non-volatile type");
    }
  }

  /** 
   * Enumerate the nonvolatile physical registers of a given class,
   * backwards.
   */
  public Enumeration enumerateNonvolatilesBackwards(int regClass) {
    return new OPT_ReverseEnumerator(enumerateNonvolatiles(regClass));
  }


  /**
   * If the passed in physical register r is used as a GPR parameter register,
   * return the index into the GPR parameters for r.  Otherwise, return -1;
   */
  public int getGPRParamIndex(OPT_Register r) {
    if ( (r.number < FIRST_INT_PARAM) || (r.number > LAST_VOLATILE_GPR)) {
      return -1;
    } else {
      return r.number - FIRST_INT_PARAM;
    }
  }

  /**
   * If the passed in physical register r is used as an FPR parameter register,
   * return the index into the FPR parameters for r.  Otherwise, return -1;
   */
  public int getFPRParamIndex(OPT_Register r) {
    if ( (r.number < FIRST_DOUBLE_PARAM) || (r.number > LAST_VOLATILE_FPR)) {
      return -1;
    } else {
      return r.number - FIRST_DOUBLE_PARAM;
    }
  }

  /** 
   * If r is used as the first half of a (long) register pair, return 
   * the second half of the pair.
   */
  public OPT_Register getSecondHalf(OPT_Register r) {
    int n = r.number;
    return get(n+1);
  }

  /**
   * An enumerator for use by the physical register utilities.
   */
  final class PhysicalRegisterEnumeration implements Enumeration {
    private int start;
    private int end;
    private int index;

    PhysicalRegisterEnumeration(int start, int end) {
      this.start = start;
      this.end = end;
      this.index = start;
    }
    public Object nextElement() {
      return reg[index++];
    }
    public boolean hasMoreElements() {
      return (index <= end);
    }
  }
}
