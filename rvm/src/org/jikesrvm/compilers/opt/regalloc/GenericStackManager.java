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

import static org.jikesrvm.VM.NOT_REACHED;
import static org.jikesrvm.compilers.opt.ir.Operators.BBEND;
import static org.jikesrvm.compilers.opt.ir.Operators.IR_PROLOGUE;
import static org.jikesrvm.compilers.opt.ir.Operators.IR_PROLOGUE_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.LOWTABLESWITCH;
import static org.jikesrvm.compilers.opt.ir.Operators.YIELDPOINT_OSR;
import static org.jikesrvm.runtime.UnboxedSizeConstants.BYTES_IN_ADDRESS;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;

import org.jikesrvm.VM;
import org.jikesrvm.architecture.ArchConstants;
import org.jikesrvm.architecture.StackFrameLayout;
import org.jikesrvm.compilers.opt.OptimizingCompilerException;
import org.jikesrvm.compilers.opt.ir.BasicBlock;
import org.jikesrvm.compilers.opt.ir.GenericPhysicalRegisterSet;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.ir.IRTools;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ir.Register;
import org.jikesrvm.compilers.opt.ir.operand.Operand;
import org.jikesrvm.compilers.opt.ir.operand.RegisterOperand;
import org.jikesrvm.compilers.opt.ir.operand.UnknownConstantOperand;

/**
 * Class to manage the allocation of the "compiler-independent" portion of
 * the stackframe.
 */
public abstract class GenericStackManager extends IRTools {

  protected static final boolean DEBUG = false;
  protected static final boolean VERBOSE = false;
  protected static final boolean VERBOSE_DEBUG = false;

  /**
   * Size of a word, in bytes
   */
  protected static final int WORDSIZE = BYTES_IN_ADDRESS;

  protected IR ir;
  protected RegisterAllocatorState regAllocState;
  protected int frameSize;
  protected boolean allocFrame;

  /**
   * Object holding register preferences
   */
  protected final GenericRegisterPreferences pref;

  {
    if (VM.BuildForIA32) {
      pref = new org.jikesrvm.compilers.opt.regalloc.ia32.RegisterPreferences();
    } else {
      if (VM.VerifyAssertions) VM._assert(VM.BuildForPowerPC);
      pref = new org.jikesrvm.compilers.opt.regalloc.ppc.RegisterPreferences();
    }
  }

  GenericRegisterPreferences getPreferences() {
    return pref;
  }

  /**
   * Object holding register restrictions
   */
  protected GenericRegisterRestrictions restrict;

  GenericRegisterRestrictions getRestrictions() {
    return restrict;
  }

  /**
   * Spill pointer (in bytes) relative to the beginning of the
   * stack frame (starts after the header).
   */
  protected int spillPointer = StackFrameLayout.getStackFrameHeaderSize();

  /**
   * Have we decided that a stack frame is required for this method?
   */
  private boolean frameRequired;

  /**
   * Memory location (8 bytes) to be used for type conversions
   */
  private int conversionOffset;

  /**
   * Memory location (4 bytes) to be used for caughtExceptions
   */
  private int caughtExceptionOffset;

  /**
   * Is there a prologue yieldpoint in this method?
   */
  private boolean prologueYieldpoint;

  /**
   * We will have to save and restore all non-volatile registers around
   * system calls, to protect ourselve from malicious native code that may
   * bash these registers.
   *
   * This field, when non-zero,  holds the stack-frame offset reserved to
   * hold this data.
   */
  private int sysCallOffset = 0;

  /**
   * This field records whether or not the stack needs to be aligned.
   */
  private boolean aligned = false;

  /**
   * For each physical register, holds a ScratchRegister which records
   * the current scratch assignment for the physical register.
   */
  protected final ArrayList<ScratchRegister> scratchInUse = new ArrayList<ScratchRegister>(20);

  /**
   * An array which holds the spill location number used to stash nonvolatile
   * registers.
   */
  protected final int[] nonVolatileGPRLocation = new int[ArchConstants.getNumberOfGPRs()];
  protected final int[] nonVolatileFPRLocation = new int[ArchConstants.getNumberOfFPRs()];

  /**
   * An array which holds the spill location number used to stash volatile
   * registers in the SaveVolatile protocol.
   */
  protected final int[] saveVolatileGPRLocation = new int[ArchConstants.getNumberOfGPRs()];
  protected final int[] saveVolatileFPRLocation = new int[ArchConstants.getNumberOfFPRs()];

  /**
   * An object used to track adjustments to the GC maps induced by scratch
   * registers
   */
  protected ScratchMap scratchMap;

  ScratchMap getScratchMap() {
    return scratchMap;
  }

  /**
   * Perform some architecture-specific initialization.
   *
   * @param ir the IR
   */
  public abstract void initForArch(IR ir);

  /**
   * @param s the instruction to check
   * @return whether the instruction is a system call?
   */
  public abstract boolean isSysCall(Instruction s);

  /**
   * @param s the instruction to check
   * @return whether the instruction is a system call?
   */
  public abstract boolean isAlignedSysCall(Instruction s);

  /**
   * Given symbolic register r in instruction s, do we need to ensure that
   * r is in a scratch register is s (as opposed to a memory operand)
   *
   * @param r the symbolic register
   * @param s the instruction that has an occurrence of the register
   * @return {@code true} if the symbolic register needs to be a scratch
   *  register
   */
  public abstract boolean needScratch(Register r, Instruction s);

  /**
   * Allocates a new spill location and grows the
   * frame size to reflect the new layout.
   *
   * @param type the type to spill
   * @return the spill location
   */
  public abstract int allocateNewSpillLocation(int type);

  public abstract int getSpillSize(int type);

  /**
   * Cleans up some junk that's left in the IR after register allocation,
   * and adds epilogue code.
   */
  public abstract void cleanUpAndInsertEpilogue();

  /**
   * Returns the size of the fixed portion of the stack.
   * (in other words, the difference between the framepointer and
   * the stackpointer after the prologue of the method completes).
   * @return size in bytes of the fixed portion of the stackframe
   */
  public abstract int getFrameFixedSize();

  /**
   * Computes the number of stack words needed to hold nonvolatile
   * registers.
   *
   * Side effects:
   * <ul>
   * <li> updates the OptCompiler structure
   * <li> updates the <code>frameSize</code> field of this object
   * <li> updates the <code>frameRequired</code> field of this object
   * </ul>
   */
  public abstract void computeNonVolatileArea();

  /**
   * Inserts the prologue for a normal method.
   */
  public abstract void insertNormalPrologue();

  /**
   * Walk over the currently available scratch registers.
   *
   * <p>For any scratch register r which is def'ed by instruction s,
   * spill r before s and remove r from the pool of available scratch
   * registers.
   *
   * <p>For any scratch register r which is used by instruction s,
   * restore r before s and remove r from the pool of available scratch
   * registers.
   *
   * <p>For any scratch register r which has current contents symb, and
   * symb is spilled to location M, and s defs M: the old value of symb is
   * dead.  Mark this.
   *
   * <p>Invalidate any scratch register assignments that are illegal in s.
   *
   * @param s the instruction to process
   */
  public abstract void restoreScratchRegistersBefore(Instruction s);

  /**
   * In instruction s, replace all appearances of a symbolic register
   * operand with uses of the appropriate spill location, as cached by the
   * register allocator.
   *
   * @param s the instruction to mutate.
   * @param symb the symbolic register operand to replace
   */
  public abstract void replaceOperandWithSpillLocation(Instruction s, RegisterOperand symb);

  /**
   * Should we use information from linear scan in choosing scratch
   * registers?
   */
  private static boolean USE_LINEAR_SCAN = true;

  /**
   * We may rely on information from linear scan to choose scratch registers.
   * If so, the following holds a pointer to some information from linear
   * scan analysis.
   */
  private ActiveSet activeSet = null;

  /**
   * Replaces all occurrences of register r1 in an instruction with register
   * r2.
   *
   * Also, for any register r3 that is spilled to the same location as
   * r1, replace r3 with r2.
   *
   * @param s instruction to process
   * @param r1 register to replace
   * @param r2 the replacement register
   */
  private void replaceRegisterWithScratch(Instruction s, Register r1, Register r2) {
    int spill1 = regAllocState.getSpill(r1);
    for (Enumeration<Operand> e = s.getOperands(); e.hasMoreElements();) {
      Operand op = e.nextElement();
      if (op != null) {
        if (op.isRegister()) {
          Register r3 = op.asRegister().getRegister();
          if (r3 == r1) {
            op.asRegister().setRegister(r2);
          } else if (regAllocState.getSpill(r3) == spill1) {
            op.asRegister().setRegister(r2);
          }
        }
      }
    }
  }

  /**
   * We will have to save and restore all non-volatile registers around
   * system calls, to protect ourselves from malicious native code that may
   * bash these registers.  Call this routine before register allocation
   * in order to allocate space on the stack frame to store these
   * registers.
   *
   * @param n the number of GPR registers to save and restore.
   * @return the offset into the stack where n*4 contiguous words are
   * reserved
   */
  public int allocateSpaceForSysCall(int n, boolean isAligned) {
    int bytes = n * WORDSIZE;
    if (sysCallOffset == 0) {
      sysCallOffset = allocateOnStackFrame(bytes);
      aligned = isAligned;
    }
    return sysCallOffset;
  }

  /**
   * @return whether the compiled method has to be aligned
   */
  public boolean isAligned() {
    return aligned;
  }

  /**
   * We will have to save and restore all non-volatile registers around
   * system calls, to protect ourselves from malicious native code that may
   * bash these registers.  Call this routine before register allocation
   * in order to get the stack-frame offset previously reserved for this
   * data.
   *
   * @return the offset into the stack where n*4 contiguous words are
   * reserved
   */
  public int getOffsetForSysCall() {
    return sysCallOffset;
  }

  /**
   * @return whether the compiled method has any syscalls
   */
  public boolean hasSysCall() {
    return sysCallOffset != 0;
  }

  /**
   * Spills the contents of a scratch register to memory before
   * instruction s.
   *
   * @param scratch the scratch register to spill
   * @param s the instruction before which the spill needs to occur
   */
  protected void unloadScratchRegisterBefore(Instruction s, ScratchRegister scratch) {
    // if the scratch register is not dirty, don't need to write anything,
    // since the stack holds the current value
    if (!scratch.isDirty()) return;

    // spill the contents of the scratch register
    Register scratchContents = scratch.getCurrentContents();
    if (scratchContents != null) {
      int location = regAllocState.getSpill(scratchContents);
      insertSpillBefore(s, scratch.scratch, scratchContents, location);
    }

  }

  /**
   * Restores the contents of a scratch register before instruction s if
   * necessary.
   *
   * @param scratch the scratch register whose contents may need to be restored
   * @param s the instruction before which the restores needs to occur
   */
  protected void reloadScratchRegisterBefore(Instruction s, ScratchRegister scratch) {
    if (scratch.hadToSpill()) {
      // Restore the live contents into the scratch register.
      int location = regAllocState.getSpill(scratch.scratch);
      insertUnspillBefore(s, scratch.scratch, scratch.scratch, location);
    }
  }

  /**
   * @param s the instruction whose reserved scratch registers are of interest
   * @return the scratch registers which are currently reserved
   * for use in the instruction (may be an empty list but will never be {@code null})
   */
  private ArrayList<Register> getReservedScratchRegisters(Instruction s) {
    ArrayList<Register> result = new ArrayList<Register>(3);

    for (ScratchRegister sr : scratchInUse) {
      if (sr.getCurrentContents() != null && appearsIn(sr.getCurrentContents(), s)) {
        result.add(sr.scratch);
      }
    }
    return result;
  }

  /**
   * If there is a scratch register available which currently holds the
   * value of symbolic register r, then return that scratch register.<p>
   *
   * Additionally, if there is a scratch register available which is
   * mapped to the same stack location as r, then return that scratch
   * register.<p>
   *
   * Else return {@code null}.
   *
   * @param r the symbolic register to hold
   * @param s the instruction for which we need r in a register
   * @return a register as described above or {@code null}
   */
  private ScratchRegister getCurrentScratchRegister(Register r, Instruction s) {
    for (ScratchRegister sr : scratchInUse) {
      if (sr.getCurrentContents() == r) {
        return sr;
      }
      int location = regAllocState.getSpill(sr.getCurrentContents());
      int location2 = regAllocState.getSpill(r);
      if (location == location2) {
        // OK. We're currently holding a different symbolic register r2 in
        // a scratch register, and r2 is mapped to the same spill location
        // as r.  So, coopt the scratch register for r, instead.
        Register r2 = sr.getCurrentContents();
        sr.setCurrentContents(r);
        scratchMap.endScratchInterval(sr.scratch, s);
        scratchMap.endSymbolicInterval(r2, s);
        scratchMap.beginScratchInterval(sr.scratch, s);
        scratchMap.beginSymbolicInterval(r, sr.scratch, s);
        return sr;
      }
    }
    return null;
  }

  /**
   * @param r the register to check
   * @return the scratch register for r if it's currently in use as a scratch register,
   *  {@code null} otherwise
   */
  private ScratchRegister getPhysicalScratchRegister(Register r) {
    for (ScratchRegister sr : scratchInUse) {
      if (sr.scratch == r) {
        return sr;
      }
    }
    return null;
  }

  /**
   * Walk over the currently available scratch registers.<p>
   *
   * For any register which is dirty, note this in the scratch map for
   * instruction s.
   *
   * @param s the instruction which needs an update in the scratch map
   */
  private void markDirtyScratchRegisters(Instruction s) {
    for (ScratchRegister scratch : scratchInUse) {
      if (scratch.isDirty()) {
        scratchMap.markDirty(s, scratch.getCurrentContents());
      }
    }
  }

  /**
   * Walk over the currently available scratch registers, and spill their
   * contents to memory before instruction s.  Also restore the correct live
   * value for each scratch register. Normally, s should end a
   * basic block.<p>
   *
   * SPECIAL CASE: If s is a return instruction, only restore the scratch
   * registers that are used by s.  The others are dead.
   *
   * @param s the instruction before which the scratch registers need to be
   *  restored
   */
  private void restoreAllScratchRegistersBefore(Instruction s) {
    for (Iterator<ScratchRegister> i = scratchInUse.iterator(); i.hasNext();) {
      ScratchRegister scratch = i.next();

      // SPECIAL CASE: If s is a return instruction, only restore the
      // scratch
      // registers that are used by s.  The others are dead.
      if (!s.isReturn() || usedIn(scratch.scratch, s)) {
        unloadScratchRegisterBefore(s, scratch);
        reloadScratchRegisterBefore(s, scratch);
      }
      // update the scratch maps, even if the scratch registers are now
      // dead.
      if (VERBOSE_DEBUG) {
        System.out.println("RALL: End scratch interval " + scratch.scratch + " " + s);
      }
      i.remove();
      scratchMap.endScratchInterval(scratch.scratch, s);
      Register scratchContents = scratch.getCurrentContents();
      if (scratchContents != null) {
        if (VERBOSE_DEBUG) {
          System.out.println("RALL: End symbolic interval " + scratchContents + " " + s);
        }
        scratchMap.endSymbolicInterval(scratchContents, s);
      }
    }
  }

  /**
   * @param r the register
   * @param s the instruction
   * @return {@code true} if the register is dead immediately before
   *  the instruction
   */
  public boolean isDeadBefore(Register r, Instruction s) {

    BasicInterval bi = activeSet.getBasicInterval(r, s);
    // If there is no basic interval containing s, then r is dead before
    // s.
    if (bi == null) {
      return true;
    } else {
      // If the basic interval begins at s, then r is dead before
      // s.
      return bi.getBegin() == regAllocState.getDFN(s);
    }
  }

  /**
   * Inserts code as needed so that after instruction s, the value of
   * a symbolic register will be held in a particular scratch physical
   * register.
   *
   * @param s the instruction after which the value will be held in scratch
   * @param symb the register whose value needs to be held in scratch
   * @param beCheap don't expend much effort optimizing scratch
   * assignments
   * @return the physical scratch register that holds the value
   *         after instruction s
   */
  private ScratchRegister holdInScratchAfter(Instruction s, Register symb, boolean beCheap) {

    // Get a scratch register.
    ScratchRegister sr = getScratchRegister(symb, s, beCheap);

    // make the scratch register available to hold the new
    // symbolic register
    Register current = sr.getCurrentContents();

    if (current != null && current != symb) {
      int location = regAllocState.getSpill(current);
      int location2 = regAllocState.getSpill(symb);
      if (location != location2) {
        insertSpillBefore(s, sr.scratch, current, location);
      }
    }

    // Record the new contents of the scratch register
    sr.setCurrentContents(symb);

    return sr;
  }

  /**
   * @param symb the symbolic register that we want to assign
   * @param phys the scratch register
   * @param s the instruction where the assignment would take place
   * @return whether it's legal to assign the symbolic register to the scratch register
   *  in the given instruction
   */
  protected boolean isLegal(Register symb, Register phys, Instruction s) {
    // If the physical scratch register already appears in s, so we can't
    // use it as a scratch register for another value.
    if (appearsIn(phys, s)) return false;

    // Check register restrictions for symb.
    if (getRestrictions().isForbidden(symb, phys, s)) return false;

    // Further assure legality for all other symbolic registers in symb
    // which are mapped to the same spill location as symb.
    int location = regAllocState.getSpill(symb);
    for (Enumeration<Operand> e = s.getOperands(); e.hasMoreElements();) {
      Operand op = e.nextElement();
      if (op.isRegister()) {
        Register r = op.asRegister().getRegister();
        if (r.isSymbolic()) {
          if (location == regAllocState.getSpill(r)) {
            if (getRestrictions().isForbidden(r, phys, s)) {
              return false;
            }
          }
        }
      }
    }

    // Otherwise, all is kosher.
    return true;
  }

  /**
   * Gets a scratch register to hold symbolic register symb in instruction
   * s.
   *
   * @param symb the symbolic register to hold
   * @param s the instruction where the scratch register is needed
   * @param beCheap don't expend too much effort
   * @return a scratch register, never {@code null}
   */
  private ScratchRegister getScratchRegister(Register symb, Instruction s, boolean beCheap) {

    ScratchRegister r = getCurrentScratchRegister(symb, s);
    if (r != null) {
      // symb is currently assigned to scratch register r
      if (isLegal(symb, r.scratch, s)) {
        if (r.getCurrentContents() != symb) {
          // we're reusing a scratch register based on the fact that symb
          // shares a spill location with r.currentContents.  However,
          // update the mapping information.
          if (r.getCurrentContents() != null) {
            if (VERBOSE_DEBUG) {
              System.out.println("GSR: End symbolic interval " + r.getCurrentContents() + " " + s);
            }
            scratchMap.endSymbolicInterval(r.getCurrentContents(), s);
          }
          if (VERBOSE_DEBUG) {
            System.out.println("GSR: Begin symbolic interval " + symb + " " + r.scratch + " " + s);
          }
          scratchMap.beginSymbolicInterval(symb, r.scratch, s);
        }
        return r;
      }
    }

    // if we get here, either there is no current scratch assignment, or
    // the current assignment is illegal.  Find a new scratch register.
    ScratchRegister result = null;
    if (beCheap || activeSet == null) {
      result = getFirstAvailableScratchRegister(symb, s);
    } else {
      result = getScratchRegisterUsingIntervals(symb, s);
    }

    // Record that we will touch the scratch register.
    result.scratch.touchRegister();
    return result;
  }

  /**
   * Finds a register which can serve as a scratch
   * register for symbolic register r in instruction s.
   *
   * <p> Inserts spills if necessary to ensure that the returned scratch
   * register is free for use.
   *
   * @param r the symbolic register that needs a scratch
   * @param s the instruction where the scratch register is needed
   * @return a scratch register, never {@code null}
   */
  private ScratchRegister getScratchRegisterUsingIntervals(Register r, Instruction s) {
    ArrayList<Register> reservedScratch = getReservedScratchRegisters(s);

    Register phys = null;
    if (r.isFloatingPoint()) {
      phys = getFirstDeadFPRNotUsedIn(r, s, reservedScratch);
    } else {
      phys = getFirstDeadGPRNotUsedIn(r, s, reservedScratch);
    }

    // if the version above failed, default to the dumber heuristics
    if (phys == null) {
      if (r.isFloatingPoint()) {
        phys = getFirstFPRNotUsedIn(r, s, reservedScratch);
      } else {
        phys = getFirstGPRNotUsedIn(r, s, reservedScratch);
      }
    }
    return createScratchBefore(regAllocState, s, phys, r);
  }

  /**
   * Finds the first available register which can serve as a scratch
   * register for symbolic register r in instruction s.
   *
   * <p> Inserts spills if necessary to ensure that the returned scratch
   * register is free for use.
   *
   * @param r the symbolic register that needs a scratch
   * @param s the instruction where the scratch register is needed
   * @return a scratch register, never {@code null}
   */
  private ScratchRegister getFirstAvailableScratchRegister(Register r, Instruction s) {
    ArrayList<Register> reservedScratch = getReservedScratchRegisters(s);

    Register phys = null;
    if (r.isFloatingPoint()) {
      phys = getFirstFPRNotUsedIn(r, s, reservedScratch);
    } else {
      phys = getFirstGPRNotUsedIn(r, s, reservedScratch);
    }
    return createScratchBefore(regAllocState, s, phys, r);
  }

  /**
   * Assigns symbolic register symb to a physical register, and inserts code
   * before instruction s to load the register from the appropriate stack
   * location.
   *
   * @param s the instruction before which the register needs to be loaded
   * @param symb the symbolic register to be assigned to a scratch
   * @param beCheap don't expend to much effort to optimize scratch
   * assignments
   * @return the physical register used to hold the value when it is
   * loaded from the spill location
   */
  private ScratchRegister moveToScratchBefore(Instruction s, Register symb, boolean beCheap) {

    ScratchRegister sr = getScratchRegister(symb, s, beCheap);

    Register scratchContents = sr.getCurrentContents();
    if (scratchContents != symb) {
      if (scratchContents != null) {
        // the scratch register currently holds a different
        // symbolic register.
        // spill the contents of the scratch register to free it up.
        unloadScratchRegisterBefore(s, sr);
      }

      // Now load up the scratch register.
      // since symbReg must have been previously spilled, get the spill
      // location previous assigned to symbReg
      int location = regAllocState.getSpill(symb);
      insertUnspillBefore(s, sr.scratch, symb, location);

      // we have not yet written to sr, so mark it 'clean'
      sr.setDirty(false);

    } else {
      // In this case the scratch register already holds the desired
      // symbolic register.  So: do nothing.
    }

    // Record the current contents of the scratch register
    sr.setCurrentContents(symb);

    return sr;
  }

  /**
   * Make physicals register r available to be used as a scratch register
   * before instruction s.  In instruction s, r will hold the value of
   * register symb.
   * @param regAllocState TODO
   * @param s the instruction before which the scratch register will be created
   * @param r the physical register to be used as scratch
   * @param symb the symbolic register which needs a scratch register
   *
   * @return the scratch register that will hold the value
   */
  private ScratchRegister createScratchBefore(RegisterAllocatorState regAllocState, Instruction s, Register r, Register symb) {
    int type = GenericPhysicalRegisterSet.getPhysicalRegisterType(r);
    int spillLocation = regAllocState.getSpill(r);
    if (spillLocation <= 0) {
      // no spillLocation yet assigned to the physical register.
      // allocate a new location and assign it for the physical register
      spillLocation = allocateNewSpillLocation(type);
      regAllocState.setSpill(r, spillLocation);
    }

    ScratchRegister sr = getPhysicalScratchRegister(r);
    if (sr == null) {
      sr = new ScratchRegister(r, null);
      scratchInUse.add(sr);
      // Since this is a new scratch register, spill the old contents of
      // r if necessary.
      if (activeSet == null) {
        insertSpillBefore(s, r, r, spillLocation);
        sr.setHadToSpill(true);
      } else {
        if (!isDeadBefore(r, s)) {
          insertSpillBefore(s, r, r, spillLocation);
          sr.setHadToSpill(true);
        }
      }
    } else {
      // update mapping information
      if (VERBOSE_DEBUG) {
        System.out.println("CSB: " + " End scratch interval " + sr.scratch + " " + s);
      }
      scratchMap.endScratchInterval(sr.scratch, s);
      Register scratchContents = sr.getCurrentContents();
      if (scratchContents != null) {
        if (VERBOSE_DEBUG) {
          System.out.println("CSB: " + " End symbolic interval " + sr.getCurrentContents() + " " + s);
        }
        scratchMap.endSymbolicInterval(sr.getCurrentContents(), s);
      }
    }

    // update mapping information
    if (VERBOSE_DEBUG) {
      System.out.println("CSB: Begin scratch interval " + r + " " + s);
    }
    scratchMap.beginScratchInterval(r, s);

    if (VERBOSE_DEBUG) {
      System.out.println("CSB: Begin symbolic interval " + symb + " " + r + " " + s);
    }
    scratchMap.beginSymbolicInterval(symb, r, s);

    return sr;
  }

  private boolean usesSpillLocation(Register r, Instruction s) {
    int location = regAllocState.getSpill(r);
    return usesSpillLocation(location, s);
  }

  private Register spillLocationUse(Register r, Instruction s) {
    int location = regAllocState.getSpill(r);
    return spillLocationUse(location, s);
  }

  private boolean definesSpillLocation(Register r, Instruction s) {
    int location = regAllocState.getSpill(r);
    return definesSpillLocation(location, s);
  }

  private boolean definesSpillLocation(int loc, Instruction s) {
    for (Enumeration<Operand> e = s.getDefs(); e.hasMoreElements();) {
      Operand op = e.nextElement();
      if (op != null && op.isRegister()) {
        Register r = op.asRegister().getRegister();
        if (regAllocState.getSpill(r) == loc) {
          return true;
        }
      }
    }
    return false;
  }

  private boolean usesSpillLocation(int loc, Instruction s) {
    for (Enumeration<Operand> e = s.getUses(); e.hasMoreElements();) {
      Operand op = e.nextElement();
      if (op != null && op.isRegister()) {
        Register r = op.asRegister().getRegister();
        if (regAllocState.getSpill(r) == loc) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Assuming instruction s uses the spill location loc,
   * return the symbolic register that embodies that use.<p>
   *
   * Note that at most one such register can be used, since at most one
   * live register can use a given spill location.
   *
   * @param s instruction to check
   * @param loc spill location
   * @return the symbolic register that belongs to the spill location
   */
  private Register spillLocationUse(int loc, Instruction s) {
    for (Enumeration<Operand> e = s.getUses(); e.hasMoreElements();) {
      Operand op = e.nextElement();
      if (op != null && op.isRegister()) {
        Register r = op.asRegister().getRegister();
        if (regAllocState.getSpill(r) == loc) {
          return r;
        }
      }
    }
    OptimizingCompilerException.UNREACHABLE("NO Matching use");
    return null;
  }

  /**
   * Returns a FPR that does not appear in instruction s, to be used as a
   * scratch register to hold register r.
   * Except, does NOT return any register that is a member of the reserved set.
   * <p>
   * @param r the register that needs a scratch register
   * @param s the instruction for which the scratch register is needed
   * @param reserved the registers that must not be used
   * @return a free FPR
   * @throws OptimizingCompilerException if no free FPR was found
   */
  private Register getFirstFPRNotUsedIn(Register r, Instruction s, ArrayList<Register> reserved) {
    GenericPhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();

    // first try the volatiles
    for (Enumeration<Register> e = phys.enumerateVolatileFPRs(); e.hasMoreElements();) {
      Register p = e.nextElement();
      if (!appearsIn(p, s) && !p.isPinned() && !reserved.contains(p) && isLegal(r, p, s)) {
        return p;
      }
    }

    OptimizingCompilerException.TODO("Could not find a free FPR in spill situation");
    return null;
  }

  /**
   * Return a FPR that does not appear in instruction s, and is dead
   * before instruction s, to hold symbolic register r.
   * Except, do NOT
   * return any register that is a member of the reserved set.
   *
   * @param r the register that needs a scratch register
   * @param s the instruction for which the scratch register is needed
   * @param reserved the registers that must not be used
   * @return {@code null} if no register found, a dead and unused FPR otherwise
   */
  private Register getFirstDeadFPRNotUsedIn(Register r, Instruction s, ArrayList<Register> reserved) {
    GenericPhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();

    // first try the volatiles
    for (Enumeration<Register> e = phys.enumerateVolatileFPRs(); e.hasMoreElements();) {
      Register p = e.nextElement();
      if (!appearsIn(p, s) && !p.isPinned() && !reserved.contains(p)) {
        if (isDeadBefore(p, s) && isLegal(r, p, s)) return p;
      }
    }

    return null;
  }

  /**
   * Return a GPR that does not appear in instruction s, to hold symbolic
   * register r.
   * Except, do NOT
   * return any register that is a member of the reserved set.
   * @param r the register that needs a scratch register
   * @param s the instruction for which the scratch register is needed
   * @param reserved the registers that must not be used
   * @return a free GPR
   */
  private Register getFirstGPRNotUsedIn(Register r, Instruction s, ArrayList<Register> reserved) {
    GenericPhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
    // first try the volatiles
    for (Enumeration<Register> e = phys.enumerateVolatileGPRs(); e.hasMoreElements();) {
      Register p = e.nextElement();
      if (!appearsIn(p, s) && !p.isPinned() && !reserved.contains(p) && isLegal(r, p, s)) {
        return p;
      }
    }
    // next try the non-volatiles. We allocate the nonvolatiles backwards
    for (Enumeration<Register> e = phys.enumerateNonvolatileGPRsBackwards(); e.hasMoreElements();) {
      Register p = e.nextElement();
      if (!appearsIn(p, s) && !p.isPinned() && !reserved.contains(p) && isLegal(r, p, s)) {
        return p;
      }
    }
    OptimizingCompilerException.TODO("Could not find a free GPR in spill situation");
    return null;
  }

  /**
   * Return a GPR that does not appear in instruction s, and is dead
   * before instruction s, to hold symbolic register r.
   * Except, do NOT
   * return any register that is a member of the reserved set.
   * @param r the register that needs a scratch register
   * @param s the instruction for which the scratch register is needed
   * @param reserved the registers that must not be used
   * @return {@code null} if no register found, a dead and unused GPR otherwise
   */
  private Register getFirstDeadGPRNotUsedIn(Register r, Instruction s, ArrayList<Register> reserved) {
    GenericPhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
    // first try the volatiles
    for (Enumeration<Register> e = phys.enumerateVolatileGPRs(); e.hasMoreElements();) {
      Register p = e.nextElement();
      if (!appearsIn(p, s) && !p.isPinned() && !reserved.contains(p)) {
        if (isDeadBefore(p, s) && isLegal(r, p, s)) return p;
      }
    }
    // next try the non-volatiles. We allocate the nonvolatiles backwards
    for (Enumeration<Register> e = phys.enumerateNonvolatileGPRsBackwards(); e.hasMoreElements();) {
      Register p = e.nextElement();
      if (!appearsIn(p, s) && !p.isPinned() && !reserved.contains(p)) {
        if (isDeadBefore(p, s) && isLegal(r, p, s)) return p;
      }
    }
    return null;
  }

  private boolean appearsIn(Register r, Instruction s) {
    for (Enumeration<Operand> e = s.getOperands(); e.hasMoreElements();) {
      Operand op = e.nextElement();
      if (op != null && op.isRegister()) {
        if (op.asRegister().getRegister().number == r.number) {
          return true;
        }
      }
    }
    if (VM
        .BuildForIA32 &&
                      r.isFloatingPoint() &&
                      (s.operator().isFNInit() || s.operator().isFClear())) {
      return true;
    }

    // Assume that all volatile registers 'appear' in all call
    // instructions
    return s.isCall() && !s.operator().isCallSaveVolatile() && r.isVolatile();
  }

  /**
   * @param s the instruction to check
   * @param instructionsBB the block that contains the instruction
   * @return whether the instruction is s a PEI (potentially excepting
   *  instruction, i.e. it can throw an exception) with a reachable catch
   *  block
   */
  private boolean isPEIWithCatch(Instruction s, BasicBlock instructionsBB) {
    if (s.isPEI()) {
      // TODO: add a more efficient accessor on BasicBlock to
      // determine whether there's a catch block for a particular
      // instruction.
      if (instructionsBB.getApplicableExceptionalOut(s).hasMoreElements()) {
        return true;
      }
    }
    return false;
  }

  /**
   * @param n number of the non-volatile GPR
   * @return the offset from the frame pointer for the place to store the
   * nth nonvolatile GPR.
   */
  protected int getNonvolatileGPROffset(int n) {
    return nonVolatileGPRLocation[n];
  }

  /**
   * @param n number of the non-volatile FPR
   * @return the offset from the frame pointer for the place to store the
   * nth nonvolatile FPR.
   */
  protected int getNonvolatileFPROffset(int n) {
    return nonVolatileFPRLocation[n];
  }

  /**
   * PROLOGUE/EPILOGUE. Note: This must be done after register allocation!
   */
  public final void insertPrologueAndEpilogue() {
    insertPrologue();
    cleanUpAndInsertEpilogue();
  }

  private void insertPrologue() {
    // compute the number of stack words needed to hold nonvolatile
    // registers
    computeNonVolatileArea();
    // insert values for getFrameSize magic, if present
    rewriteFrameSizeMagics();

    if (frameIsRequired()) {
      insertNormalPrologue();
    } else {
      Instruction inst = ir.firstInstructionInCodeOrder().nextInstructionInCodeOrder();
      if (VM.VerifyAssertions) VM._assert(inst.getOpcode() == IR_PROLOGUE_opcode);
      inst.remove();
      ir.MIRInfo.gcIRMap.delete(inst);
    }
  }

  private void rewriteFrameSizeMagics() {
    Enumeration<Instruction> instructions = ir.forwardInstrEnumerator();
    while (instructions.hasMoreElements()) {
      Instruction inst = instructions.nextElement();
      int frameSize = getFrameFixedSize();
      verifyArchSpecificFrameSizeConstraints(frameSize);
      inst.replaceSimilarOperands(new UnknownConstantOperand(), IC(frameSize));
    }
  }

  protected abstract void verifyArchSpecificFrameSizeConstraints(int frameSize);

  /**
   * After register allocation, go back through the IR and insert
   * compensating code to deal with spills.
   */
  public void insertSpillCode() {
    insertSpillCode(null);
  }

  /**
   * After register allocation, go back through the IR and insert
   * compensating code to deal with spills.
   *
   * @param set information from linear scan analysis (may be {@code null})
   */
  public void insertSpillCode(ActiveSet set) {
    if (USE_LINEAR_SCAN) {
      activeSet = set;
    }

    if (VERBOSE_DEBUG) {
      System.out.println("INSERT SPILL CODE:");
    }

    // walk over each instruction in the IR
    for (Enumeration<BasicBlock> blocks = ir.getBasicBlocks(); blocks.hasMoreElements();) {
      BasicBlock bb = blocks.nextElement();

      // If the following is true, don't expend effort trying to
      // optimize scratch assignements
      boolean beCheap = (ir.options.FREQ_FOCUS_EFFORT && bb.getInfrequent());

      for (Enumeration<Instruction> e = bb.forwardInstrEnumerator(); e.hasMoreElements();) {
        Instruction s = e.nextElement();
        if (VERBOSE_DEBUG) {
          System.out.println(s);
        }

        // If any scratch registers are currently in use, but use physical
        // registers that appear in s, then free the scratch register.
        restoreScratchRegistersBefore(s);

        // we must spill all scratch registers before leaving this basic block
        if (s.operator() == BBEND || isPEIWithCatch(s, bb) || s.isBranch() || s.isReturn()) {
          restoreAllScratchRegistersBefore(s);
        }

        // If s is a GC point, and scratch register r currently caches the
        // value of symbolic symb, and r is dirty: Then update the GC map to
        // account for the fact that symb's spill location does not
        // currently hold a valid reference.
        if (s.isGCPoint()) {
          // note that if we're being cheap, no scratch registers are
          // currently dirty, since we've restored them all.
          markDirtyScratchRegisters(s);
        }

        // Walk over each operand and insert the appropriate spill code.
        // for the operand.
        for (Enumeration<Operand> ops = s.getOperands(); ops.hasMoreElements();) {
          Operand op = ops.nextElement();
          if (op != null && op.isRegister()) {
            Register r = op.asRegister().getRegister();
            if (!r.isPhysical()) {
              // Is r currently assigned to a scratch register?
              // Note that if we're being cheap, the answer is always no (null)
              ScratchRegister scratch = getCurrentScratchRegister(r, s);
              if (VERBOSE_DEBUG) {
                System.out.println(r + " SCRATCH " + scratch);
              }
              if (scratch != null) {
                // r is currently assigned to a scratch register.  Continue to
                // use the same scratch register.
                boolean defined = definedIn(r, s) || definesSpillLocation(r, s);
                if (defined) {
                  scratch.setDirty(true);
                }
                replaceRegisterWithScratch(s, r, scratch.scratch);
              } else {
                // r is currently NOT assigned to a scratch register.
                // Do we need to create a new scratch register to hold r?
                // Note that we never need scratch floating point register
                // for FMOVs, since we already have a scratch stack location
                // reserved.
                // If we're being cheap, then always create a new scratch register.
                if (needScratch(r, s)) {
                  // We must create a new scratch register.
                  boolean used = usedIn(r, s) || usesSpillLocation(r, s);
                  boolean defined = definedIn(r, s) || definesSpillLocation(r, s);
                  if (used) {
                    if (!usedIn(r, s)) {
                      Register r2 = spillLocationUse(r, s);
                      scratch = moveToScratchBefore(s, r2, beCheap);
                      if (VERBOSE_DEBUG) {
                        System.out.println("MOVED TO SCRATCH BEFORE " + r2 + " " + scratch);
                      }
                    } else {
                      scratch = moveToScratchBefore(s, r, beCheap);
                      if (VERBOSE_DEBUG) {
                        System.out.println("MOVED TO SCRATCH BEFORE " + r + " " + scratch);
                      }
                    }
                  }
                  if (defined) {
                    scratch = holdInScratchAfter(s, r, beCheap);
                    scratch.setDirty(true);
                    if (VERBOSE_DEBUG) {
                      System.out.println("HELD IN SCRATCH AFTER" + r + " " + scratch);
                    }
                  }
                  // replace the register in the target instruction.
                  replaceRegisterWithScratch(s, r, scratch.scratch);
                } else {
                  if (VM.BuildForIA32) {
                    // No need to use a scratch register here.
                    replaceOperandWithSpillLocation(s, op.asRegister());
                  } else {
                    if (VM.VerifyAssertions) {
                      if (s.operator() != YIELDPOINT_OSR) {
                        VM._assert(NOT_REACHED);
                      }
                    }
                  }
                }
              }
            }
          }
        }

        // deal with sys calls that may bash non-volatiles
        if (isSysCall(s) || isAlignedSysCall(s)) {
          if (VM.BuildForIA32) {
            org.jikesrvm.compilers.opt.regalloc.ia32.CallingConvention.saveNonvolatilesAroundSysCall(s, ir);
          } else {
            if (VM.VerifyAssertions) VM._assert(VM.BuildForPowerPC);
            org.jikesrvm.compilers.opt.regalloc.ppc.CallingConvention.saveNonvolatilesAroundSysCall(s, ir);
          }
        }
      }
    }
  }

  /**
   * Insert a spill of a physical register before instruction s.
   *
   * @param s the instruction before which the spill should occur
   * @param r the register (should be physical) to spill
   * @param type the register that's contained in the physical register
   * @param location the spill location
   */
  public abstract void insertSpillBefore(Instruction s, Register r, Register type, int location);

  /**
   * Insert a spill of a physical register after instruction s.
   *
   * @param s the instruction after which the spill should occur
   * @param r the register (should be physical) to spill
   * @param type the register that's contained in the physical register
   * @param location the spill location
   */
  public final void insertSpillAfter(Instruction s, Register r, Register type, int location) {
    insertSpillBefore(s.nextInstructionInCodeOrder(), r, type, location);
  }

  /**
   * Insert a load of a physical register from a spill location before
   * instruction s.
   *
   * @param s the instruction before which the spill should occur
   * @param r the register (should be physical) to spill
   * @param type the register that's contained in the physical register
   * @param location the spill location
   */
  public abstract void insertUnspillBefore(Instruction s, Register r, Register type, int location);

  /**
   * Insert a load of a physical register from a spill location before
   * instruction s.
   *
   * @param s the instruction before which the spill should occur
   * @param r the register (should be physical) to spill
   * @param type the register that's contained in the physical register
   * @param location the spill location
   */
  public final void insertUnspillAfter(Instruction s, Register r, Register type, int location) {
    insertUnspillBefore(s.nextInstructionInCodeOrder(), r, type, location);
  }

  /**
   * @return {@code true} if and only if a stack frame
   *  must be allocated for this method
l   */
  protected boolean frameIsRequired() {
    return frameRequired;
  }

  /**
   * Records that we need a stack frame for this method.
   */
  protected void setFrameRequired() {
    frameRequired = true;
  }

  /**
   * @return {@code true} if and only if this IR has a prologue yieldpoint
   */
  protected boolean hasPrologueYieldpoint() {
    return prologueYieldpoint;
  }

  /**
   * Ensure that there's enough space for passing parameters. We need
   * {@code size - STACKFRAME_HEADER_SIZE} bytes.
   *
   * @param s space needed for parameters
   */
  public void allocateParameterSpace(int s) {
    if (spillPointer < s) {
      spillPointer = s;
      frameRequired = true;
    }
  }

  /**
   * Allocates the specified number of bytes in the stackframe,
   * returning the offset to the start of the allocated space.
   *
   * @param size the number of bytes to allocate
   * @return offset to the start of the allocated space.
   */
  public int allocateOnStackFrame(int size) {
    int free = spillPointer;
    spillPointer += size;
    frameRequired = true;
    return free;
  }

  /**
   * We encountered a magic (get/set framepointer) that is going to force
   * us to actually create the stack frame.
   */
  public void forceFrameAllocation() {
    frameRequired = true;
  }

  /**
   * We encountered a float/int conversion that uses
   * the stack as temporary storage.
   *
   * @return offset to the start of the allocated space
   */
  public int allocateSpaceForConversion() {
    if (conversionOffset == 0) {
      conversionOffset = allocateOnStackFrame(8);
    }
    return conversionOffset;
  }

  /**
   * We encountered a catch block that actually uses its caught
   * exception object; allocate a stack slot for the exception delivery
   * code to use to pass the exception object to us.
   *
   * @return offset to the start of the allocated space
   */
  public int allocateSpaceForCaughtException() {
    if (caughtExceptionOffset == 0) {
      caughtExceptionOffset = allocateOnStackFrame(BYTES_IN_ADDRESS);
    }
    return caughtExceptionOffset;
  }

  /**
   * Called as part of the register allocator startup.
   * <ol>
   *   <li>examine the IR to determine whether or not we need to
   *     allocate a stack frame</li>
   *   <li>given that decison, determine whether or not we need to have
   *     prologue/epilogue yieldpoints.  If we don't need them, remove them.
   *     Set up register preferences.</li>
   *   <li>initialization code for the old RegisterManager</li>
   *   <li>save caughtExceptionOffset where the exception deliverer can find it</li>
   *   <li>initialize the restrictions object</li>
   * </ol>
   * @param ir the IR
   */
  public void prepare(IR ir) {
    // (1) if we haven't yet committed to a stack frame we
    //     will look for operators that would require a stack frame
    //        - LOWTABLESWITCH
    //        - a GC Point, except for YieldPoints or IR_PROLOGUE
    boolean preventYieldPointRemoval = false;
    if (!frameRequired) {
      for (Instruction s = ir.firstInstructionInCodeOrder(); s != null; s = s.nextInstructionInCodeOrder()) {
        if (s.operator() == LOWTABLESWITCH) {
          // uses BL to get pc relative addressing.
          frameRequired = true;
          preventYieldPointRemoval = true;
          break;
        } else if (s.isGCPoint() && !s.isYieldPoint() && s.operator() != IR_PROLOGUE) {
          // frame required for GCpoints that are not yield points
          //  or IR_PROLOGUE, which is the stack overflow check
          frameRequired = true;
          preventYieldPointRemoval = true;
          break;
        }
      }
    }

    // (2)
    // In non-adaptive configurations we can omit the yieldpoint if
    // the method contains exactly one basic block whose only successor
    // is the exit node. (The method may contain calls, but we believe that
    // in any program that isn't going to overflow its stack there must be
    // some invoked method that contains more than 1 basic block, and
    // we'll insert a yieldpoint in its prologue.)
    // In adaptive configurations the only methods we eliminate yieldpoints
    // from are those in which the yieldpoints are the only reason we would
    // have to allocate a stack frame for the method.  Having more yieldpoints
    // gets us better sampling behavior.  Thus, in the adaptive configuration
    // we only omit the yieldpoint in leaf methods with no PEIs that contain
    // exactly one basic block whose only successor is the exit node.
    // TODO: We may want to force yieldpoints in "large" PEI-free
    // single-block leaf methods (if any exist).
    // TODO: This is a kludge. Removing the yieldpoint removes
    //       the adaptive system's ability to accurately sample program
    //       behavior.  Even if the method is absolutely trivial
    //       eg boolean foo() { return false; }, we may still want to
    //       sample it for the purposes of adaptive inlining.
    //       On the other hand, the ability to do this inlining in some cases
    //       may not be able to buy back having to create a stackframe
    //       for all methods.
    //
    // Future feature: always insert a pseudo yield point that when taken will
    //    create the stack frame on demand.

    BasicBlock firstBB = ir.cfg.entry();
    boolean isSingleBlock = firstBB.hasZeroIn() && firstBB.hasOneOut() && firstBB.pointsOut(ir.cfg.exit());
    boolean removeYieldpoints = isSingleBlock && !preventYieldPointRemoval;

    // In adaptive systems if we require a frame, we don't remove
    //  any yield points
    if (VM.BuildForAdaptiveSystem && frameRequired) {
      removeYieldpoints = false;
    }

    if (removeYieldpoints) {
      for (Instruction s = ir.firstInstructionInCodeOrder(); s != null; s = s.nextInstructionInCodeOrder()) {
        if (s.isYieldPoint()) {
          Instruction save = s;
          // get previous instruction, so we can continue
          // after we remove this instruction
          s = s.prevInstructionInCodeOrder();
          save.remove();
          ir.MIRInfo.gcIRMap.delete(save);
        }
      }
      prologueYieldpoint = false;
    } else {
      prologueYieldpoint = ir.method.isInterruptible();
      frameRequired = true;
    }

    // (3) initialization
    this.ir = ir;
    this.regAllocState = ir.MIRInfo.regAllocState;
    this.scratchMap = new ScratchMap(regAllocState);
    pref.initialize(ir);
    frameSize = spillPointer;
    initForArch(ir);

    // (4) save caughtExceptionOffset where the exception deliverer can find it
    ir.compiledMethod.setUnsignedExceptionOffset(caughtExceptionOffset);

    // (5) initialize the restrictions object
    if (VM.BuildForIA32) {
      restrict = new org.jikesrvm.compilers.opt.regalloc.ia32.RegisterRestrictions(ir.regpool.getPhysicalRegisterSet());
    } else {
      if (VM.VerifyAssertions) VM._assert(VM.BuildForPowerPC);
      restrict = new org.jikesrvm.compilers.opt.regalloc.ppc.RegisterRestrictions(ir.regpool.getPhysicalRegisterSet());
    }
  }

  /**
   * Sets up register restrictions.
   *
   * @param ir the IR which will get the restrictions
   */
  public final void computeRestrictions(IR ir) {
    restrict.init(ir);
  }

  /**
   *  Find an volatile register to allocate starting at the reg corresponding
   *  to the symbolic register passed
   *  @param symbReg the place to start the search
   *  @return the allocated register or null
   */
  public final Register allocateVolatileRegister(Register symbReg) {
    GenericPhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();

    int physType = GenericPhysicalRegisterSet.getPhysicalRegisterType(symbReg);
    for (Enumeration<Register> e = phys.enumerateVolatiles(physType); e.hasMoreElements();) {
      Register realReg = e.nextElement();
      if (realReg.isAvailable()) {
        realReg.allocateToRegister(symbReg);
        if (DEBUG) VM.sysWriteln(" volat." + realReg + " to symb " + symbReg);
        return realReg;
      }
    }
    return null;
  }

  protected static int align(int number, int alignment) {
    alignment--;
    return (number + alignment) & ~alignment;
  }

  /**
   * Find a nonvolatile register to allocate starting at the reg corresponding
   * to the symbolic register passed.
   * <p>
   * TODO: Clean up this interface.
   *
   *  @param symbReg the place to start the search
   *  @return the allocated register or null
   */
  public final Register allocateNonVolatileRegister(Register symbReg) {
    GenericPhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
    int physType = GenericPhysicalRegisterSet.getPhysicalRegisterType(symbReg);
    for (Enumeration<Register> e = phys.enumerateNonvolatilesBackwards(physType); e.hasMoreElements();) {
      Register realReg = e.nextElement();
      if (realReg.isAvailable()) {
        realReg.allocateToRegister(symbReg);
        return realReg;
      }
    }
    return null;
  }

  /**
   * Class to represent a physical register currently allocated as a
   * scratch register. A scratch register is a register that is reserved
   * for use in spills and unspills. It is not available as a normal register
   * for the register allocation.
   */
  protected static final class ScratchRegister {
    /**
     * The physical register used as scratch.
     */
    public final Register scratch;

    /**
     * The current contents of scratch
     */
    private Register currentContents;

    /**
     * Is this physical register currently dirty? (Must be written back to
     * memory?)
     */
    private boolean dirty = false;

    public boolean isDirty() {
      return dirty;
    }

    public void setDirty(boolean b) {
      dirty = b;
    }

    /**
     * Did we spill a value in order to free up this scratch register?
     */
    private boolean spilledIt = false;

    public boolean hadToSpill() {
      return spilledIt;
    }

    public void setHadToSpill(boolean b) {
      spilledIt = b;
    }

    public ScratchRegister(Register scratch, Register currentContents) {
      this.scratch = scratch;
      this.currentContents = currentContents;
    }

    public Register getCurrentContents() {
      return currentContents;
    }

    public void setCurrentContents(Register currentContents) {
      this.currentContents = currentContents;
    }

    @Override
    public String toString() {
      String dirtyString = dirty ? "D" : "C";
      return "SCRATCH<" + scratch + "," + getCurrentContents() + "," + dirtyString + ">";
    }

  }
}
