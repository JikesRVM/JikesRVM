/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;
import com.ibm.JikesRVM.*;

import com.ibm.JikesRVM.opt.ir.*;
import java.util.Enumeration;
import java.util.ArrayList;
import java.util.Iterator;
/**
 * Class to manage the allocation of the "compiler-independent" portion of 
 * the stackframe.
 * <p>
 *
 * @author Dave Grove
 * @author Mauricio J. Serrano
 * @author Stephen Fink
 * @author Michael Hind
 */
public abstract class OPT_GenericStackManager extends OPT_IRTools 
implements OPT_Operators, OPT_PhysicalRegisterConstants {

  /**
   * Size of a word, in bytes
   */
  protected static final int WORDSIZE = BYTES_IN_ADDRESS;

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
   * For each physical register, holds a ScratchRegister which records
   * the current scratch assignment for the physical register.
   */
  protected ArrayList scratchInUse = new ArrayList(20);

  /**
   * An array which holds the spill location number used to stash nonvolatile
   * registers. 
   */
  protected int[] nonVolatileGPRLocation = new int[NUM_GPRS];
  protected int[] nonVolatileFPRLocation = new int[NUM_FPRS];

  /**
   * An array which holds the spill location number used to stash volatile
   * registers in the SaveVolatile protocol.
   */
  protected int[] saveVolatileGPRLocation = new int[NUM_GPRS];
  protected int[] saveVolatileFPRLocation = new int[NUM_FPRS];


  protected static final boolean debug = false;
  protected static final boolean verbose= false;
  protected static boolean verboseDebug = false;

  /**
   * Perform some architecture-specific initialization.
   */
  abstract void initForArch(OPT_IR ir);

  /**
   * Is a particular instruction a system call?
   */
  abstract boolean isSysCall(OPT_Instruction s);

  /**
   * Given symbolic register r in instruction s, do we need to ensure that
   * r is in a scratch register is s (as opposed to a memory operand)
   */
  abstract boolean needScratch(OPT_Register r, OPT_Instruction s);

  /**
   * Allocate a new spill location and grow the
   * frame size to reflect the new layout.
   *
   * @param type the type to spill
   * @return the spill location
   */
  abstract int allocateNewSpillLocation(int type); 

  /**
   * Clean up some junk that's left in the IR after register allocation,
   * and add epilogue code.
   */ 
  abstract void cleanUpAndInsertEpilogue();

  /**
   * Return the size of the fixed portion of the stack.
   * (in other words, the difference between the framepointer and
   * the stackpointer after the prologue of the method completes).
   * @return size in bytes of the fixed portion of the stackframe
   */
  abstract int getFrameFixedSize(); 

  /**
   * Compute the number of stack words needed to hold nonvolatile
   * registers.
   *
   * Side effects: 
   * <ul>
   * <li> updates the VM_OptCompiler structure 
   * <li> updates the <code>frameSize</code> field of this object
   * <li> updates the <code>frameRequired</code> field of this object
   * </ul>
   */
  abstract void computeNonVolatileArea();

  /**
   * Insert the prologue for a normal method.  
   *
   */
  abstract void insertNormalPrologue();

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
   */
  abstract void restoreScratchRegistersBefore(OPT_Instruction s);

  /**
   * In instruction s, replace all appearances of a symbolic register 
   * operand with uses of the appropriate spill location, as cached by the
   * register allocator.
   *
   * @param s the instruction to mutate.
   * @param symb the symbolic register operand to replace
   */
  abstract void replaceOperandWithSpillLocation(OPT_Instruction s, 
                                                OPT_RegisterOperand symb);

  // Get the spill location previously assigned to the symbolic
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
  private OPT_LinearScan.ActiveSet activeSet = null;

  /**
   * Replace all occurences of register r1 in an instruction with register
   * r2.
   *
   * Also, for any register r3 that is spilled to the same location as
   * r1, replace r3 with r2.
   */
  private void replaceRegisterWithScratch(OPT_Instruction s, 
                                          OPT_Register r1, OPT_Register r2) {
    int spill1 = OPT_RegisterAllocatorState.getSpill(r1);
    for (Enumeration e = s.getOperands(); e.hasMoreElements(); ) {
      OPT_Operand op = (OPT_Operand)e.nextElement();
      if (op != null) {
        if (op.isRegister()) {
          OPT_Register r3 = op.asRegister().register;
          if (r3 == r1) {
            op.asRegister().register = r2;
          } else if (OPT_RegisterAllocatorState.getSpill(r3) == spill1) {
            op.asRegister().register = r2;
          }
        }
      }
    }
  }

  /**
   * We will have to save and restore all non-volatile registers around
   * system calls, to protect ourselve from malicious native code that may
   * bash these registers.  Call this routine before register allocation
   * in order to allocate space on the stack frame to store these
   * registers.
   *
   * @param n the number of GPR registers to save and restore.
   * @return the offset into the stack where n*4 contiguous words are
   * reserved
   */
  int allocateSpaceForSysCall(int n) {
    int bytes = n * WORDSIZE;
    if (sysCallOffset == 0) {
      sysCallOffset = allocateOnStackFrame(bytes);
    }
    return sysCallOffset;
  }

  /**
   * We will have to save and restore all non-volatile registers around
   * system calls, to protect ourselve from malicious native code that may
   * bash these registers.  Call this routine before register allocation
   * in order to get the stack-frame offset previously reserved for this
   * data.
   *
   * @return the offset into the stack where n*4 contiguous words are
   * reserved
   */
  int getOffsetForSysCall() {
    return sysCallOffset;
  }

  /**
   * Spill the contents of a scratch register to memory before 
   * instruction s.  
   */
  protected void unloadScratchRegisterBefore(OPT_Instruction s, 
                                             ScratchRegister scratch) {
    // if the scratch register is not dirty, don't need to write anything, 
    // since the stack holds the current value
    if (!scratch.isDirty()) return;

    // spill the contents of the scratch register 
    OPT_Register scratchContents = scratch.currentContents;
    if (scratchContents != null) {
      int location = OPT_RegisterAllocatorState.getSpill(scratchContents);
      insertSpillBefore(s,scratch.scratch,getValueType(scratchContents),
                        location);
    }

  }
  /**
   * Restore the contents of a scratch register before instruction s.  
   */
  protected void reloadScratchRegisterBefore(OPT_Instruction s, 
                                             ScratchRegister scratch) {
    if (scratch.hadToSpill()) {
      // Restore the live contents into the scratch register.
      int location = OPT_RegisterAllocatorState.getSpill(scratch.scratch);
      insertUnspillBefore(s,scratch.scratch,getValueType(scratch.scratch),
                          location);
    }
  }

  /**
   * Return the set of scratch registers which are currently reserved
   * for use in instruction s.
   */
  private ArrayList getReservedScratchRegisters(OPT_Instruction s) {
    ArrayList result = new ArrayList(3);

    for (Iterator i = scratchInUse.iterator(); i.hasNext(); ) {
      ScratchRegister sr = (ScratchRegister)i.next();
      if (sr.currentContents != null && appearsIn(sr.currentContents,s)) {
        result.add(sr.scratch);
      }
    }
    return result;
  }

  /**
   * If there is a scratch register available which currently holds the
   * value of symbolic register r, then return that scratch register.
   *
   * Additionally, if there is a scratch register available which is
   * mapped to the same stack location as r, then return that scratch
   * register.
   *
   * Else return null.
   *
   * @param r the symbolic register to hold
   * @param s the instruction for which we need r in a register
   */
  private ScratchRegister getCurrentScratchRegister(OPT_Register r,OPT_Instruction s) {
    ScratchRegister result = null;

    for (Iterator i = scratchInUse.iterator(); i.hasNext(); ) {
      ScratchRegister sr = (ScratchRegister)i.next();
      if (sr.currentContents == r) {
        return sr;
      }
      int location = OPT_RegisterAllocatorState.getSpill(sr.currentContents);
      int location2 = OPT_RegisterAllocatorState.getSpill(r); 
      if (location == location2) {
        // OK. We're currently holding a different symbolic register r2 in
        // a scratch register, and r2 is mapped to the same spill location
        // as r.  So, coopt the scratch register for r, instead.
        OPT_Register r2 = sr.currentContents;
        sr.currentContents = r;
        scratchMap.endScratchInterval(sr.scratch,s);
        scratchMap.endSymbolicInterval(r2,s);
        scratchMap.beginScratchInterval(sr.scratch,s);
        scratchMap.beginSymbolicInterval(r,sr.scratch,s);
        return sr;
      }
    }
    return null;
  }
  /**
   * If register r is currently in use as a scratch register, 
   * then return that scratch register.
   * Else return null.
   */
  private ScratchRegister getPhysicalScratchRegister(OPT_Register r) {
    ScratchRegister result = null;

    for (Iterator i = scratchInUse.iterator(); i.hasNext(); ) {
      ScratchRegister sr = (ScratchRegister)i.next();
      if (sr.scratch == r) {
        return sr;
      }
    }
    return null;
  }


  /**
   * Walk over the currently available scratch registers. 
   *
   * For any register which is dirty, note this in the scratch map for
   * instruction s.
   */
  private void markDirtyScratchRegisters(OPT_Instruction s) {
    for (Iterator i = scratchInUse.iterator(); i.hasNext(); ) {
      ScratchRegister scratch = (ScratchRegister)i.next();
      if (scratch.isDirty()) {
        scratchMap.markDirty(s,scratch.currentContents);
      }
    }
  }

  /**
   * Walk over the currently available scratch registers, and spill their 
   * contents to memory before instruction s.  Also restore the correct live
   * value for each scratch register. Normally, s should end a 
   * basic block. 
   *
   * SPECIAL CASE: If s is a return instruction, only restore the scratch
   * registers that are used by s.  The others are dead.
   */
  private void restoreAllScratchRegistersBefore(OPT_Instruction s) {
    for (Iterator i = scratchInUse.iterator(); i.hasNext(); ) {
      ScratchRegister scratch = (ScratchRegister)i.next();

      // SPECIAL CASE: If s is a return instruction, only restore the 
      // scratch
      // registers that are used by s.  The others are dead.
      if (!s.isReturn() || usedIn(scratch.scratch,s)) {
        unloadScratchRegisterBefore(s,scratch);
        reloadScratchRegisterBefore(s,scratch);
      }
      // update the scratch maps, even if the scratch registers are now
      // dead.
      if (verboseDebug) System.out.println("RALL: End scratch interval " + 
                                           scratch.scratch + " " + s);
      i.remove();
      scratchMap.endScratchInterval(scratch.scratch,s);
      OPT_Register scratchContents = scratch.currentContents;
      if (scratchContents != null) {
        if (verboseDebug) System.out.println("RALL: End symbolic interval " + 
                                             scratchContents + " " + s);
        scratchMap.endSymbolicInterval(scratchContents,s);
      } 
    }
  }

  /**
   * Is a particular register dead immediately before instruction s.
   */
  boolean isDeadBefore(OPT_Register r, OPT_Instruction s) {

    OPT_LinearScan.BasicInterval bi = activeSet.getBasicInterval(r,s);
    // If there is no basic interval containing s, then r is dead before
    // s.
    if (bi == null) return true;
    // If the basic interval begins at s, then r is dead before
    // s.
    else if (bi.getBegin() == OPT_LinearScan.getDFN(s)) return true;
    else return false;
  }

  /**
   * Insert code as needed so that after instruction s, the value of
   * a symbolic register will be held in a particular scratch physical
   * register.
   * 
   * @param beCheap don't expend much effort optimizing scratch
   * assignments
   * @return the physical scratch register that holds the value 
   *         after instruction s
   */
  private ScratchRegister holdInScratchAfter(OPT_Instruction s, 
                                             OPT_Register symb,
                                             boolean beCheap) {

    // Get a scratch register.
    ScratchRegister sr = getScratchRegister(symb,s,beCheap);

    // make the scratch register available to hold the new 
    // symbolic register
    OPT_Register current = sr.currentContents;

    if (current != null && current != symb) {
      int location = OPT_RegisterAllocatorState.getSpill(current);
      int location2 = OPT_RegisterAllocatorState.getSpill(symb);
      if (location != location2) {
        insertSpillBefore(s,sr.scratch,getValueType(current),location);
      }
    }

    // Record the new contents of the scratch register
    sr.currentContents = symb;

    return sr;
  }

  /**
   * Is it legal to assign symbolic register symb to scratch register phys
   * in instruction s?
   */
  boolean isLegal(OPT_Register symb, OPT_Register phys, OPT_Instruction s) {
    // If the physical scratch register already appears in s, so we can't 
    // use it as a scratch register for another value.
    if (appearsIn(phys,s)) return false;

    // Check register restrictions for symb.
    if (getRestrictions().isForbidden(symb,phys,s)) return false;

    // Further assure legality for all other symbolic registers in symb
    // which are mapped to the same spill location as symb.
    int location = OPT_RegisterAllocatorState.getSpill(symb);
    for (Enumeration e = s.getOperands(); e.hasMoreElements(); ) {
      OPT_Operand op = (OPT_Operand)e.nextElement();
      if (op.isRegister()) {
        OPT_Register r = op.asRegister().register;
        if (r.isSymbolic()) {
          if (location == OPT_RegisterAllocatorState.getSpill(r)) {
            if (getRestrictions().isForbidden(r,phys,s)) 
              return false;
          }
        }
      }
    }

    // Otherwise, all is kosher.
    return true;
  }
  /**
   * Get a scratch register to hold symbolic register symb in instruction
   * s.
   *
   * @param beCheap don't expend too much effort
   */
  private ScratchRegister getScratchRegister(OPT_Register symb,
                                             OPT_Instruction s,
                                             boolean beCheap) {

    ScratchRegister r = getCurrentScratchRegister(symb,s);
    if (r != null) {
      // symb is currently assigned to scratch register r
      if (isLegal(symb,r.scratch,s)) {
        if (r.currentContents != symb) {
          // we're reusing a scratch register based on the fact that symb
          // shares a spill location with r.currentContents.  However,
          // update the mapping information.
          if (r.currentContents != null) {
            if (verboseDebug) System.out.println("GSR: End symbolic interval " + 
                                                 r.currentContents + " " 
                                                 + s);
            scratchMap.endSymbolicInterval(r.currentContents,s);
          } 
          if (verboseDebug) System.out.println("GSR: Begin symbolic interval " + 
                                               symb + " " + r.scratch + 
                                               " " + s);
          scratchMap.beginSymbolicInterval(symb,r.scratch,s);
        }
        return r;
      }
    }

    // if we get here, either there is no current scratch assignment, or
    // the current assignment is illegal.  Find a new scratch register.
    ScratchRegister result = null;
    if (beCheap || activeSet == null) {
      result = getFirstAvailableScratchRegister(symb,s);
    } else {
      result = getScratchRegisterUsingIntervals(symb,s);
    }

    // Record that we will touch the scratch register.
    result.scratch.touchRegister(); 
    return result;
  }

  /**
   * Find a register which can serve as a scratch
   * register for symbolic register r in instruction s.
   *
   * <p> Insert spills if necessary to ensure that the returned scratch
   * register is free for use.
   */
  private ScratchRegister getScratchRegisterUsingIntervals(OPT_Register r,
                                                           OPT_Instruction s){
    ArrayList reservedScratch = getReservedScratchRegisters(s);

    OPT_Register phys = null;
    if (r.isFloatingPoint()) {
      phys = getFirstDeadFPRNotUsedIn(r,s,reservedScratch);
    } else {
      phys = getFirstDeadGPRNotUsedIn(r,s,reservedScratch);
    }

    // if the version above failed, default to the dumber heuristics
    if (phys == null) {
      if (r.isFloatingPoint()) {
        phys = getFirstFPRNotUsedIn(r,s,reservedScratch);
      } else {
        phys = getFirstGPRNotUsedIn(r,s,reservedScratch);
      }
    }
    return createScratchBefore(s,phys,r);
  }

  /**
   * Find the first available register which can serve as a scratch
   * register for symbolic register r in instruction s.
   *
   * <p> Insert spills if necessary to ensure that the returned scratch
   * register is free for use.
   */
  private ScratchRegister getFirstAvailableScratchRegister(OPT_Register r,
                                                           OPT_Instruction s){
    ArrayList reservedScratch = getReservedScratchRegisters(s);

    OPT_Register phys = null;
    if (r.isFloatingPoint()) {
      phys = getFirstFPRNotUsedIn(r,s,reservedScratch);
    } else {
      phys = getFirstGPRNotUsedIn(r,s,reservedScratch);
    }
    return createScratchBefore(s,phys,r);
  }

  /**
   * Assign symbolic register symb to a physical register, and insert code
   * before instruction s to load the register from the appropriate stack
   * location.
   *
   * @param beCheap don't expend to much effort to optimize scratch
   * assignments
   * @return the physical register used to hold the value when it is
   * loaded from the spill location
   */
  private ScratchRegister moveToScratchBefore(OPT_Instruction s, 
                                              OPT_Register symb,
                                              boolean beCheap) {

    ScratchRegister sr = getScratchRegister(symb,s,beCheap);

    OPT_Register scratchContents = sr.currentContents;
    if (scratchContents != symb) {
      if (scratchContents != null) {
        // the scratch register currently holds a different 
        // symbolic register.
        // spill the contents of the scratch register to free it up.
        unloadScratchRegisterBefore(s,sr);
      }

      // Now load up the scratch register.
      // since symbReg must have been previously spilled, get the spill
      // location previous assigned to symbReg
      int location = OPT_RegisterAllocatorState.getSpill(symb);
      insertUnspillBefore(s,sr.scratch,getValueType(symb),location);

      // we have not yet written to sr, so mark it 'clean'
      sr.setDirty(false);

    } else { 
      // In this case the scratch register already holds the desired
      // symbolic register.  So: do nothing. 
    }    

    // Record the current contents of the scratch register
    sr.currentContents = symb;

    return sr;
  }

  /**
   * Make physical register r available to be used as a scratch register
   * before instruction s.  In instruction s, r will hold the value of
   * register symb.
   */
  private ScratchRegister createScratchBefore(OPT_Instruction s, 
                                              OPT_Register r,
                                              OPT_Register symb) {
    OPT_PhysicalRegisterSet pool = ir.regpool.getPhysicalRegisterSet();
    int type = OPT_PhysicalRegisterSet.getPhysicalRegisterType(r);
    int spillLocation = OPT_RegisterAllocatorState.getSpill(r);
    if (spillLocation <= 0) {
      // no spillLocation yet assigned to the physical register.
      // allocate a new location and assign it for the physical register
      spillLocation = allocateNewSpillLocation(type);      
      OPT_RegisterAllocatorState.setSpill(r,spillLocation);
    }

    ScratchRegister sr = getPhysicalScratchRegister(r);
    if (sr == null) {
      sr = new ScratchRegister(r,null);
      scratchInUse.add(sr);
      // Since this is a new scratch register, spill the old contents of
      // r if necessary.
      if (activeSet == null) {
        insertSpillBefore(s, r, (byte)type, spillLocation);
        sr.setHadToSpill(true);
      } else {
        if (!isDeadBefore(r,s)) {
          insertSpillBefore(s, r, (byte)type, spillLocation);
          sr.setHadToSpill(true);
        }
      }
    } else {
      // update mapping information
      if (verboseDebug) System.out.println("CSB: " + 
                                           " End scratch interval " + 
                                           sr.scratch + " " + s);
      scratchMap.endScratchInterval(sr.scratch,s);
      OPT_Register scratchContents = sr.currentContents;
      if (scratchContents != null) {
        if (verboseDebug) System.out.println("CSB: " + 
                                             " End symbolic interval " + 
                                             sr.currentContents + " " 
                                             + s);
        scratchMap.endSymbolicInterval(sr.currentContents,s);
      } 
    }

    // update mapping information
    if (verboseDebug) System.out.println("CSB: Begin scratch interval " + r + 
                                         " " + s);
    scratchMap.beginScratchInterval(r,s);

    if (verboseDebug) System.out.println("CSB: Begin symbolic interval " + 
                                         symb + " " + r + 
                                         " " + s);
    scratchMap.beginSymbolicInterval(symb,r,s);

    return sr;
  }

  /**
   * Does instruction s use the spill location for a given register?
   */
  private boolean usesSpillLocation(OPT_Register r, OPT_Instruction s) {
    int location = OPT_RegisterAllocatorState.getSpill(r);
    return usesSpillLocation(location,s);
  }

  /**
   * Assuming instruction s uses the spill location for a given register, 
   * return the symbolic register that embodies that use.
   */
  private OPT_Register spillLocationUse(OPT_Register r, OPT_Instruction s) {
    int location = OPT_RegisterAllocatorState.getSpill(r);
    return spillLocationUse(location,s);
  }

  /**
   * Does instruction s define the spill location for a given register?
   */
  private boolean definesSpillLocation(OPT_Register r, OPT_Instruction s) {
    int location = OPT_RegisterAllocatorState.getSpill(r);
    return definesSpillLocation(location,s);
  }
  /**
   * Does instruction s define spill location loc?
   */
  private boolean definesSpillLocation(int loc, OPT_Instruction s) {
    for (Enumeration e = s.getDefs(); e.hasMoreElements(); ) {
      OPT_Operand op = (OPT_Operand)e.nextElement();
      if (op != null && op.isRegister()) {
        OPT_Register r= op.asRegister().register;
        if (OPT_RegisterAllocatorState.getSpill(r) == loc) {
          return true;
        }
      }
    }
    return false;
  }
  /**
   * Does instruction s use spill location loc?
   */
  private boolean usesSpillLocation(int loc, OPT_Instruction s) {
    for (Enumeration e = s.getUses(); e.hasMoreElements(); ) {
      OPT_Operand op = (OPT_Operand)e.nextElement();
      if (op != null && op.isRegister()) {
        OPT_Register r= op.asRegister().register;
        if (OPT_RegisterAllocatorState.getSpill(r) == loc) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Assuming instruction s uses the spill location loc,
   * return the symbolic register that embodies that use.
   * Note that at most one such register can be used, since at most one
   * live register can use a given spill location.
   */
  private OPT_Register spillLocationUse(int loc, OPT_Instruction s) {
    for (Enumeration e = s.getUses(); e.hasMoreElements(); ) {
      OPT_Operand op = (OPT_Operand)e.nextElement();
      if (op != null && op.isRegister()) {
        OPT_Register r= op.asRegister().register;
        if (OPT_RegisterAllocatorState.getSpill(r) == loc) {
          return r;
        }
      }
    }
    OPT_OptimizingCompilerException.UNREACHABLE("NO Matching use");
    return null;
  }

  /**
   * Return a FPR that does not appear in instruction s, to be used as a
   * scratch register to hold register r
   * Except, do NOT return any register that is a member of the reserved set.
   *
   * Throw an exception if none found.
   */ 
  private OPT_Register getFirstFPRNotUsedIn(OPT_Register r, 
                                            OPT_Instruction s, 
                                            ArrayList reserved) {
    OPT_PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();

    // first try the volatiles
    for (Enumeration e = phys.enumerateVolatileFPRs(); e.hasMoreElements(); ) {
      OPT_Register p = (OPT_Register)e.nextElement();
      if (!appearsIn(p,s) && !p.isPinned() &&
          !reserved.contains(p) && isLegal(r,p,s)) {
        return p;
      }
    }

    OPT_OptimizingCompilerException.TODO("Could not find a free FPR in spill situation");
    return null;
  }

  /**
   * Return a FPR that does not appear in instruction s, and is dead
   * before instruction s, to hold symbolic register r.
   * Except, do NOT
   * return any register that is a member of the reserved set.
   *
   * Return null if none found
   */ 
  private OPT_Register getFirstDeadFPRNotUsedIn(OPT_Register r, 
                                                OPT_Instruction s, 
                                                ArrayList reserved) {
    OPT_PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();

    // first try the volatiles
    for (Enumeration e = phys.enumerateVolatileFPRs(); e.hasMoreElements(); ) {
      OPT_Register p = (OPT_Register)e.nextElement();
      if (!appearsIn(p,s) && !p.isPinned() && !reserved.contains(p)) {
        if (isDeadBefore(p,s) && isLegal(r,p,s)) return p;
      }
    }

    return null;
  }

  /**
   * Return a GPR that does not appear in instruction s, to hold symbolic
   * register r.
   * Except, do NOT
   * return any register that is a member of the reserved set.
   *
   * Throw an exception if none found.
   */ 
  private OPT_Register getFirstGPRNotUsedIn(OPT_Register r, 
                                            OPT_Instruction s, 
                                            ArrayList reserved) {
    OPT_PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
    // first try the volatiles
    for (Enumeration e = phys.enumerateVolatileGPRs();
         e.hasMoreElements(); ) {
      OPT_Register p= (OPT_Register)e.nextElement();
      if (!appearsIn(p,s) && !p.isPinned() && !reserved.contains(p)
          && isLegal(r,p,s)) {
        return p;
      }
    }
    // next try the non-volatiles
    for (Enumeration e = phys.enumerateNonvolatileGPRs(); 
         e.hasMoreElements(); ) {
      OPT_Register p = (OPT_Register)e.nextElement();
      if (!appearsIn(p,s) && !p.isPinned() && !reserved.contains(p) && 
          isLegal(r,p,s)) {
        return p;
      }
    }
    OPT_OptimizingCompilerException.TODO(
                                         "Could not find a free GPR in spill situation");
    return null;
  }

  /**
   * Return a GPR that does not appear in instruction s, and is dead
   * before instruction s, to hold symbolic register r. 
   * Except, do NOT
   * return any register that is a member of the reserved set.
   *
   * return null if none found
   */ 
  private OPT_Register getFirstDeadGPRNotUsedIn(OPT_Register r,
                                                OPT_Instruction s, 
                                                ArrayList reserved) {
    OPT_PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
    // first try the volatiles
    for (Enumeration e = phys.enumerateVolatileGPRs();
         e.hasMoreElements(); ) {
      OPT_Register p = (OPT_Register)e.nextElement();
      if (!appearsIn(p,s) && !p.isPinned() && !reserved.contains(p)) {
        if (isDeadBefore(p,s) && isLegal(r,p,s)) return p;
      }
    }
    // next try the non-volatiles
    for (Enumeration e = phys.enumerateNonvolatileGPRs(); 
         e.hasMoreElements(); ) {
      OPT_Register p = (OPT_Register)e.nextElement();
      if (!appearsIn(p,s) && !p.isPinned() && !reserved.contains(p)) {
        if (isDeadBefore(p,s) && isLegal(r,p,s)) return p;
      }
    }
    return null;
  }

  /**
   * Does register r appear in instruction s?
   */
  private boolean appearsIn(OPT_Register r, OPT_Instruction s) {
    for (Enumeration e = s.getOperands(); e.hasMoreElements(); ) {
      OPT_Operand op = (OPT_Operand)e.nextElement();
      if (op != null && op.isRegister()) {
        if (op.asRegister().register.number == r.number) {
          return true;
        }
      }
    }
    //-#if RVM_FOR_IA32
    // FNINIT and FCLEAR use/kill all floating points 
    if (r.isFloatingPoint() && 
        (s.operator == IA32_FNINIT || s.operator == IA32_FCLEAR)) {
      return true;
    }
    //-#endif

    // Assume that all volatile registers 'appear' in all call 
    // instructions
    if (s.isCall() && s.operator != CALL_SAVE_VOLATILE && r.isVolatile()) {
      return true;
    }

    return false;
  }


  /**
   * Is s a PEI with a reachable catch block?
   */
  private boolean isPEIWithCatch(OPT_Instruction s) {
    if (s.isPEI())  {
      // TODO: optimize this away by passing the basic block in.
      OPT_BasicBlock b = s.getBasicBlock();

      // TODO: add a more efficient accessor on OPT_BasicBlock to
      // determine whether there's a catch block for a particular
      // instruction.
      if (b.getApplicableExceptionalOut(s).hasMoreElements()) {
        return true;
      }
    }
    return false;
  }

  /**
   * Return the offset from the frame pointer for the place to store the
   * nth nonvolatile GPR.
   */
  protected int getNonvolatileGPROffset(int n) {
    return nonVolatileGPRLocation[n];
  }

  /**
   * Return the offset from the frame pointer for the place to store the
   * nth nonvolatile FPR.
   */
  protected int getNonvolatileFPROffset(int n) {
    return nonVolatileFPRLocation[n];
  }

  /**
   * PROLOGUE/EPILOGUE. must be done after register allocation
   */
  final void insertPrologueAndEpilogue() {
    insertPrologue();
    cleanUpAndInsertEpilogue();
  }

  /**
   *  Insert end prologue to show debugger where the end of the
   *  prologue is
   */
  private void insertEndPrologue() {
    OPT_Instruction inst = ir.firstInstructionInCodeOrder().nextInstructionInCodeOrder();
    if (VM.VerifyAssertions) VM._assert(inst.getOpcode() == IR_PROLOGUE_opcode);
    inst.insertBefore(Empty.create(IR_ENDPROLOGUE));
    inst.remove();
    ir.MIRInfo.gcIRMap.delete(inst);
  }

  /**
   * Insert the prologue.
   */
  private void insertPrologue() {
    // compute the number of stack words needed to hold nonvolatile
    // registers
    computeNonVolatileArea();

    if (frameIsRequired()) {
      insertNormalPrologue();
    } else {
      insertEndPrologue();
    }
  }


  /**
   * After register allocation, go back through the IR and insert
   * compensating code to deal with spills.
   */
  void insertSpillCode() {
    insertSpillCode(null);
  }

  /**
   * After register allocation, go back through the IR and insert
   * compensating code to deal with spills.
   *
   * @param set information from linear scan analysis
   */
  void insertSpillCode(OPT_LinearScan.ActiveSet set) {
    if (USE_LINEAR_SCAN) {
      activeSet = set;
    }

    if (verboseDebug) {
      System.out.println("INSERT SPILL CODE:");
    }

    // walk over each instruction in the IR
    for (Enumeration blocks = ir.getBasicBlocks(); blocks.hasMoreElements(); ) {
      OPT_BasicBlock bb = (OPT_BasicBlock)blocks.nextElement();
      for (Enumeration e = bb.forwardInstrEnumerator(); e.hasMoreElements();) {

        // If the following is true, don't expend effort trying to
        // optimize scratch assignements
        boolean beCheap = (ir.options.FREQ_FOCUS_EFFORT && bb.getInfrequent());

        OPT_Instruction s = (OPT_Instruction)e.nextElement();
        if (verboseDebug) {
          System.out.println(s);
        }

        // If any scratch registers are currently in use, but use physical
        // registers that appear in s, then free the scratch register.
        restoreScratchRegistersBefore(s);  

        // we must spill all scratch registers before leaving this basic block
        if (s.operator == BBEND || isPEIWithCatch(s) || s.isBranch() || s.isReturn()) {
          restoreAllScratchRegistersBefore(s);
        }

        // SJF: This is a bad hack which avoids bug 2642.  For some reason,
        // if we cache a reference value in ECX across the prologue_yieldpoint in
        // method java.Hashtable.put(), bad things happen and the program
        // non-deterministically crashes with apparent bad GC Maps.  
        // I have not figured out what's really going on, despite great
        // effort.  I'm giving up for now, and instead resorting to this
        // woeful hack to avoid the problem.
        // To reproduce the bug, comment out the following and run SPECjbb
        // using the night-sanity parameters on OptOptSemispace; the program
        // should crash with a bad GC map about half the time.
        if (s.operator == YIELDPOINT_PROLOGUE) {
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
        for (Enumeration ops = s.getOperands(); ops.hasMoreElements(); ) {
          OPT_Operand op = (OPT_Operand)ops.nextElement();
          if (op != null && op.isRegister()) {
            OPT_Register r = op.asRegister().register;
            if (!r.isPhysical()) {
              // Is r currently assigned to a scratch register?
              // Note that if we're being cheap, the answer is always no (null)
              ScratchRegister scratch = getCurrentScratchRegister(r,s);
              if (verboseDebug) {
                System.out.println(r + " SCRATCH " + scratch);
              }
              if (scratch != null) {
                // r is currently assigned to a scratch register.  Continue to
                // use the same scratch register.
                boolean defined = definedIn(r,s) || definesSpillLocation(r,s);
                if (defined) {
                  scratch.setDirty(true);
                }
                replaceRegisterWithScratch(s,r,scratch.scratch);
              } else {
                // r is currently NOT assigned to a scratch register.
                // Do we need to create a new scratch register to hold r?
                // Note that we never need scratch floating point register
                // for FMOVs, since we already have a scratch stack location
                // reserved.
                // If we're being cheap, then always create a new scratch register.
                if (needScratch(r,s)) {
                  // We must create a new scratch register.
                  boolean used = usedIn(r,s) || usesSpillLocation(r,s);
                  boolean defined = definedIn(r,s) || definesSpillLocation(r,s);
                  if (used) {
                    if (!usedIn(r,s)) {
                      OPT_Register r2 = spillLocationUse(r,s);
                      scratch = moveToScratchBefore(s,r2,beCheap);
                      if (verboseDebug) {
                        System.out.println("MOVED TO SCRATCH BEFORE " + r2 + 
                                           " " + scratch);
                      }
                    } else {
                      scratch = moveToScratchBefore(s,r,beCheap);
                      if (verboseDebug) {
                        System.out.println("MOVED TO SCRATCH BEFORE " + r + 
                                           " " + scratch);
                      }
                    }
                  }   
                  if (defined) {
                    scratch = holdInScratchAfter(s,r,beCheap);
                    scratch.setDirty(true);
                    if (verboseDebug) {
                      System.out.println("HELD IN SCRATCH AFTER" + r + 
                                         " " + scratch);
                    }
                  }
                  // replace the register in the target instruction.
                  replaceRegisterWithScratch(s,r,scratch.scratch);
                } else {
                  //-#if RVM_WITH_OSR
                  if (s.operator != YIELDPOINT_OSR) {
                  //-#endif
                  
                  //-#if RVM_FOR_IA32
                  // No need to use a scratch register here.
                  replaceOperandWithSpillLocation(s,op.asRegister());
                  //-#else
                  VM._assert(NOT_REACHED);
                  //-#endif
                  //-#if RVM_WITH_OSR
                  }
                  //-#endif
                }
              }
            }
          }
        }

        // deal with sys calls that may bash non-volatiles
        //-#if RVM_FOR_IA32
        if (isSysCall(s)) {
          OPT_CallingConvention.saveNonvolatilesAroundSysCall(s,ir);
        }
        //-#endif
      }
    }
  }

  /**
   * An object used to track adjustments to the GC maps induced by scrach
   * registers
   */
  protected OPT_ScratchMap scratchMap = new OPT_ScratchMap();
  OPT_ScratchMap getScratchMap() { return scratchMap; }

  /**
   * Insert a spill of a physical register before instruction s.
   *
   * @param s the instruction before which the spill should occur
   * @param r the register (should be physical) to spill
   * @param type one of INT_VALUE, FLOAT_VALUE, DOUBLE_VALUE, or
   *                    CONDITION_VALUE
   * @param location the spill location
   */
  abstract void insertSpillBefore(OPT_Instruction s, OPT_Register r,
                                  byte type, int location);
  /**
   * Insert a spill of a physical register after instruction s.
   *
   * @param s the instruction after which the spill should occur
   * @param r the register (should be physical) to spill
   * @param type one of INT_VALUE, FLOAT_VALUE, DOUBLE_VALUE, or
   *                    CONDITION_VALUE
   * @param location the spill location
   */
  final void insertSpillAfter(OPT_Instruction s, OPT_Register r,
                              byte type, int location) {
    insertSpillBefore(s.nextInstructionInCodeOrder(),r,type,location);
  }

  /**
   * Insert a load of a physical register from a spill location before 
   * instruction s.
   *
   * @param s the instruction before which the spill should occur
   * @param r the register (should be physical) to spill
   * @param type one of INT_VALUE, FLOAT_VALUE, DOUBLE_VALUE, or
   *                    CONDITION_VALUE
   * @param location the spill location
   */
  abstract void insertUnspillBefore(OPT_Instruction s, OPT_Register r, 
                                    byte type, int location);
  /**
   * Insert a load of a physical register from a spill location before 
   * instruction s.
   *
   * @param s the instruction before which the spill should occur
   * @param r the register (should be physical) to spill
   * @param type one of INT_VALUE, FLOAT_VALUE, DOUBLE_VALUE, or
   *                    CONDITION_VALUE
   * @param location the spill location
   */
  final void insertUnspillAfter(OPT_Instruction s, OPT_Register r, 
                                byte type, int location) {
    insertUnspillBefore(s.nextInstructionInCodeOrder(),r,type,location);
  }

  /**
   * Object holding register preferences
   */
  protected OPT_RegisterPreferences pref = new OPT_RegisterPreferences();
  OPT_RegisterPreferences getPreferences() { return pref; }

  /**
   * Object holding register restrictions
   */
  protected OPT_RegisterRestrictions restrict;
  OPT_RegisterRestrictions getRestrictions() { return restrict; }

  /**
   * Spill pointer (in bytes) relative to the beginning of the 
   * stack frame (starts after the header).
   */
  protected int spillPointer = VM_Constants.STACKFRAME_HEADER_SIZE; 

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
   * Are we required to allocate a stack frame for this method?
   */
  boolean frameIsRequired() { return frameRequired; }

  /**
   * Record that we need a stack frame for this method.
   */
  void setFrameRequired() {
    frameRequired = true;
  }

  /**
   * Does this IR have a prologue yieldpoint?
   */
  boolean hasPrologueYieldpoint() { return prologueYieldpoint; }

  /**
   * Ensure param passing area of size - STACKFRAME_HEADER_SIZE bytes
   */
  void allocateParameterSpace(int s) {
    if (spillPointer < s) {
      spillPointer = s;
      frameRequired = true;
    }
  }

  /**
   * Allocate the specified number of bytes in the stackframe,
   * returning the offset to the start of the allocated space.
   * 
   * @param size the number of bytes to allocate
   * @return offset to the start of the allocated space.
   */
  int allocateOnStackFrame(int size) {
    int free = spillPointer;
    spillPointer += size;
    frameRequired = true;
    return free;
  }

  /**
   * We encountered a magic (get/set framepointer) that is going to force
   * us to acutally create the stack frame.
   */
  public void forceFrameAllocation() { frameRequired = true; }

  /**
   * We encountered a float/int conversion that uses
   * the stack as temporary storage.
   */
  int allocateSpaceForConversion() {
    if (conversionOffset == 0) {
      conversionOffset = allocateOnStackFrame(8);
    } 
    return conversionOffset;
  }

  /**
   * We encountered a catch block that actually uses its caught
   * exception object; allocate a stack slot for the exception delivery
   * code to use to pass the exception object to us.
   */
  int allocateSpaceForCaughtException() {
    if (caughtExceptionOffset == 0) {
      caughtExceptionOffset = allocateOnStackFrame(BYTES_IN_ADDRESS);
    } 
    return caughtExceptionOffset;
  }

  /**
   * Called as part of the register allocator startup.
   * (1) examine the IR to determine whether or not we need to 
   *     allocate a stack frame
   * (2) given that decison, determine whether or not we need to have
   *     prologue/epilogue yieldpoints.  If we don't need them, remove them.
   *     Set up register preferences.
   * (3) initialization code for the old OPT_RegisterManager.
   * (4) save caughtExceptionOffset where the exception deliverer can find it
   * (5) initialize the restrictions object
   * @param ir the IR
   */
  void prepare(OPT_IR ir) {
    // (1) if we haven't yet committed to a stack frame we 
    //     will look for operators that would require a stack frame
    //        - LOWTABLESWITCH
    //        - a GC Point, except for YieldPoints or IR_PROLOGUE 
    boolean preventYieldPointRemoval = false;
    if (!frameRequired) {
      for (OPT_Instruction s = ir.firstInstructionInCodeOrder();
           s != null;
           s = s.nextInstructionInCodeOrder()) {
        if (s.operator() == LOWTABLESWITCH) {
          // uses BL to get pc relative addressing.
          frameRequired = true;
          preventYieldPointRemoval = true;
          break;
        } else if (s.isGCPoint() && !s.isYieldPoint() &&
                   s.operator() != IR_PROLOGUE) {
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

    OPT_BasicBlock firstBB = ir.cfg.entry();
    boolean isSingleBlock = firstBB.hasZeroIn() && 
      firstBB.hasOneOut() && firstBB.pointsOut(ir.cfg.exit());
    boolean removeYieldpoints = isSingleBlock && ! preventYieldPointRemoval;

    //-#if RVM_WITH_ADAPTIVE_SYSTEM
    // In adaptive systems if we require a frame, we don't remove 
    //  any yield poits
    if (frameRequired) {
      removeYieldpoints = false;
    }
    //-#endif

    if (removeYieldpoints) {
      for (OPT_Instruction s = ir.firstInstructionInCodeOrder();
           s != null;
           s = s.nextInstructionInCodeOrder()) {
        if (s.isYieldPoint()) {
          OPT_Instruction save = s;
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
    pref.initialize(ir);
    frameSize = spillPointer;
    initForArch(ir);

    // (4) save caughtExceptionOffset where the exception deliverer can find it
    ir.compiledMethod.setUnsignedExceptionOffset(caughtExceptionOffset);

    // (5) initialize the restrictions object
    restrict = new OPT_RegisterRestrictions(ir.regpool.getPhysicalRegisterSet());
  }


  protected OPT_IR ir;
  protected int frameSize;      // = 0;  (by default)
  protected boolean allocFrame; // = false;  (by default)

  /**
   * Set up register restrictions
   */
  final void computeRestrictions(OPT_IR ir) {
    restrict.init(ir);
  }

  /**
   *  Find an volatile register to allocate starting at the reg corresponding
   *  to the symbolic register passed
   *  @param symbReg the place to start the search
   *  @return the allocated register or null
   */
  final OPT_Register allocateVolatileRegister(OPT_Register symbReg) {
    OPT_PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();

    int physType = OPT_PhysicalRegisterSet.getPhysicalRegisterType(symbReg);
    for (Enumeration e = phys.enumerateVolatiles(physType);
         e.hasMoreElements(); ) {
      OPT_Register realReg = (OPT_Register)e.nextElement();
      if (realReg.isAvailable()) {
        realReg.allocateToRegister(symbReg);
        if (debug) VM.sysWrite(" volat."+realReg+" to symb "+symbReg+'\n');
        return realReg;
      }
    }
    return null;
  }


  /**
   * Given a symbolic register, return a code that indicates the type
   * of the value stored in the register.
   * Note: This routine returns INT_VALUE for longs
   *
   * @return one of INT_VALUE, FLOAT_VALUE, DOUBLE_VALUE, CONDITION_VALUE
   */
  final byte getValueType(OPT_Register r) {
    if (r.isInteger() || r.isLong() || r.isAddress()) {
      return INT_VALUE;
    } else if (r.isCondition()) {
      return CONDITION_VALUE;
    } else if (r.isDouble()) {
      return DOUBLE_VALUE;
    } else if (r.isFloat()) {
      return FLOAT_VALUE;
    } else {
      throw new OPT_OptimizingCompilerException("getValueType: unsupported "
                                                + r);
    }
  }

  static int align(int number, int alignment) {
    alignment--;
    return (number + alignment) & ~alignment;
  }

  /**
   *  Find a nonvolatile register to allocate starting at the reg corresponding
   *  to the symbolic register passed
   *
   *  TODO: Clean up this interface.
   *
   *  @param symbReg the place to start the search
   *  @return the allocated register or null
   */
  final OPT_Register allocateNonVolatileRegister(OPT_Register symbReg) {
    OPT_PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
    int physType = OPT_PhysicalRegisterSet.getPhysicalRegisterType(symbReg);
    for (Enumeration e = phys.enumerateNonvolatilesBackwards(physType);
         e.hasMoreElements(); ) {
      OPT_Register realReg = (OPT_Register)e.nextElement();
      if (realReg.isAvailable()) {
        realReg.allocateToRegister(symbReg);
        return realReg;
      }
    }
    return null;
  }

  /**
   * Class to represent a physical register currently allocated as a
   * scratch register.
   */
  protected static class ScratchRegister {
    /**
     * The physical register used as scratch.
     */
    OPT_Register scratch;

    /**
     * The current contents of scratch
     */
    OPT_Register currentContents;

    /**
     * Is this physical register currently dirty? (Must be written back to
     * memory?)
     */
    private boolean dirty = false;

    boolean isDirty() { return dirty; }
    void setDirty(boolean b) { dirty = b; }

    /**
     * Did we spill a value in order to free up this scratch register?
     */
    private boolean spilledIt = false;
    boolean hadToSpill() { return spilledIt; }
    void setHadToSpill(boolean b) { spilledIt = b; }


    ScratchRegister(OPT_Register scratch, OPT_Register currentContents) {
      this.scratch = scratch;
      this.currentContents = currentContents;
    }

    public String toString() { 
      String dirtyString = dirty ? "D" : "C";
      return "SCRATCH<" + scratch + "," + currentContents + "," +
        dirtyString + ">";
    }
  }
}
