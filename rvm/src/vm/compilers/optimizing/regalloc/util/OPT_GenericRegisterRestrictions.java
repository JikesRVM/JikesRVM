/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.opt.ir.*;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Enumeration;

/**
 * An instance of this class provides a mapping from symbolic register to
 * a set of restricted registers.
 *
 * Each architcture will subclass this in a class
 * OPT_RegisterRestrictions.
 * 
 * @author Stephen Fink
 */
abstract class OPT_GenericRegisterRestrictions implements OPT_Operators {
  // for each symbolic register, the set of physical registers that are
  // illegal for assignment
  private HashMap hash = new HashMap();

  // a set of symbolic registers that must not be spilled.
  private HashSet noSpill = new HashSet();

  protected OPT_PhysicalRegisterSet phys;

  /**
   * Default Constructor
   */
  OPT_GenericRegisterRestrictions(OPT_PhysicalRegisterSet phys) {
    this.phys = phys;
  }

  /**
   * Record that the register allocator must not spill a symbolic
   * register.
   */
  final void noteMustNotSpill(OPT_Register r) {
    noSpill.add(r);
  }

  /**
   * Is spilling a register forbidden?
   */
  final boolean mustNotSpill(OPT_Register r) {
    return noSpill.contains(r);
  }

  /**
   * Record all the register restrictions dictated by an IR.
   *
   * PRECONDITION: the instructions in each basic block are numbered in
   * increasing order before calling this.  The number for each
   * instruction is stored in its <code>scratch</code> field.
   */
  final void init(OPT_IR ir) {
    // process each basic block
    for (Enumeration e = ir.getBasicBlocks(); e.hasMoreElements(); ) {
      OPT_BasicBlock b = (OPT_BasicBlock)e.nextElement();
      processBlock(b);
    }
  }

  /**
   * Record all the register restrictions dictated by live ranges on a
   * particular basic block.
   *
   * PRECONDITION: the instructions in each basic block are numbered in
   * increasing order before calling this.  The number for each
   * instruction is stored in its <code>scratch</code> field.
   */
  final private void processBlock(OPT_BasicBlock bb) {
    ArrayList symbolic = new ArrayList(20);
    ArrayList physical = new ArrayList(20);
    
    // 1. walk through the live intervals and identify which correspond to
    // physical and symbolic registers
    for (Enumeration e = bb.enumerateLiveIntervals(); e.hasMoreElements();) {
      OPT_LiveIntervalElement li = (OPT_LiveIntervalElement)e.nextElement();
      OPT_Register r = li.getRegister();
      if (r.isPhysical()) {
        if (r.isVolatile() || r.isNonVolatile()) {
          physical.add(li);
        }
      } else {
        symbolic.add(li);
      }
    }

    // 2. walk through the live intervals for physical registers.  For
    // each such interval, record the conflicts where the live range
    // overlaps a live range for a symbolic register.
    for (Iterator p = physical.iterator(); p.hasNext(); ) {
      OPT_LiveIntervalElement phys = (OPT_LiveIntervalElement)p.next();
      for (Iterator s = symbolic.iterator(); s.hasNext(); ) {
        OPT_LiveIntervalElement symb = (OPT_LiveIntervalElement)s.next();
        if (overlaps(phys,symb)) {
          addRestriction(symb.getRegister(),phys.getRegister());
        }
      }
    }

    // 3. Volatile registers used by CALL instructions do not appear in
    // the liveness information.  Handle CALL instructions as a special
    // case.
    for (OPT_InstructionEnumeration ie = bb.forwardInstrEnumerator();
         ie.hasMoreElements(); ) {
      OPT_Instruction s = ie.next();
      if (s.operator.isCall() && s.operator != CALL_SAVE_VOLATILE) {
        for (Iterator sym = symbolic.iterator(); sym.hasNext(); ) {
          OPT_LiveIntervalElement symb = (OPT_LiveIntervalElement)sym.next();
          if (contains(symb,s.scratch)) {
            forbidAllVolatiles(symb.getRegister());
          }
        }
      }

      //-#if RVM_WITH_OSR
      // Before OSR points, we need to save all FPRs, 
      // On OptExecStateExtractor, all GPRs have to be recovered, 
      // but not FPRS.
      //
      if (s.operator == YIELDPOINT_OSR) {
        for (Iterator sym = symbolic.iterator(); sym.hasNext(); ) {
          OPT_LiveIntervalElement symb = (OPT_LiveIntervalElement) sym.next();
          if (symb.getRegister().isFloatingPoint()) {
            if (contains(symb,s.scratch)) {
              forbidAllVolatiles(symb.getRegister());
            }
          }
        }       
      }
      //-#endif
    }

    // 3. architecture-specific restrictions
    addArchRestrictions(bb,symbolic);
  }

  /**
   * Add architecture-specific register restrictions for a basic block.
   * Override as needed.
   *
   * @param bb the basic block 
   * @param symbolics the live intervals for symbolic registers on this
   * block
   */
  void addArchRestrictions(OPT_BasicBlock bb, ArrayList symbolics) {}

  /**
   * Does a live range R contain an instruction with number n?
   * 
   * PRECONDITION: the instructions in each basic block are numbered in
   * increasing order before calling this.  The number for each
   * instruction is stored in its <code>scratch</code> field.
   */
  final protected boolean contains(OPT_LiveIntervalElement R, int n) {
    int begin = -1;
    int end = Integer.MAX_VALUE;
    if (R.getBegin() != null) {
      begin = R.getBegin().scratch;
    }
    if (R.getEnd() != null) {
      end= R.getEnd().scratch;
    }

    return ((begin<=n) && (n<=end));
  }

  /**
   * Do two live ranges overlap?
   *
   * PRECONDITION: the instructions in each basic block are numbered in
   * increasing order before calling this.  The number for each
   * instruction is stored in its <code>scratch</code> field.
   */
  private boolean overlaps(OPT_LiveIntervalElement li1,
                           OPT_LiveIntervalElement li2) {
    // Under the following conditions: the live ranges do NOT overlap:
    // 1. begin2 >= end1 > -1
    // 2. begin1 >= end2 > -1
    // Under all other cases, the ranges overlap

    int begin1  = -1;
    int end1    = -1;
    int begin2  = -1;
    int end2    = -1;

    if (li1.getBegin() != null) {
      begin1 = li1.getBegin().scratch;
    }
    if (li2.getEnd() != null) {
      end2 = li2.getEnd().scratch;
    }
    if (end2 <= begin1 && end2 > -1) return false;

    if (li1.getEnd() != null) {
      end1 = li1.getEnd().scratch;
    }
    if (li2.getBegin() != null) {
      begin2 = li2.getBegin().scratch;
    }
    if (end1 <= begin2 && end1 > -1) return false;

    return true;
  }

  /**
   * Record that it is illegal to assign a symbolic register symb to any
   * volatile physical registers 
   */
  final void forbidAllVolatiles(OPT_Register symb) {
    RestrictedRegisterSet r = (RestrictedRegisterSet)hash.get(symb);
    if (r == null) {
      r = new RestrictedRegisterSet(phys);
      hash.put(symb,r);
    }
    r.setNoVolatiles();
  }

  /**
   * Record that it is illegal to assign a symbolic register symb to any
   * of a set of physical registers 
   */
  final void addRestrictions(OPT_Register symb, OPT_BitSet set) {
    RestrictedRegisterSet r = (RestrictedRegisterSet)hash.get(symb);
    if (r == null) {
      r = new RestrictedRegisterSet(phys);
      hash.put(symb,r);
    }
    r.addAll(set);
  }

  /**
   * Record that it is illegal to assign a symbolic register symb to a
   * physical register p
   */
  final void addRestriction(OPT_Register symb, OPT_Register p) {
    RestrictedRegisterSet r = (RestrictedRegisterSet)hash.get(symb);
    if (r == null) {
      r = new RestrictedRegisterSet(phys);
      hash.put(symb,r);
    }
    r.add(p);
  }

  /**
   * Return the set of restricted physical register for a given symbolic
   * register. Return null if no restrictions.
   */
  final RestrictedRegisterSet getRestrictions(OPT_Register symb) {
    return (RestrictedRegisterSet)hash.get(symb);
  }

  /**
   * Is it forbidden to assign symbolic register symb to any volatile
   * register?
   * @return true :yes, all volatiles are forbidden
   *         false :maybe, maybe not
   */
  final boolean allVolatilesForbidden(OPT_Register symb) {
    if (VM.VerifyAssertions) {
      VM._assert(symb != null);
    }
    RestrictedRegisterSet s = getRestrictions(symb);
    if (s == null) return false;
    return s.getNoVolatiles();
  }

  /**
   * Is it forbidden to assign symbolic register symb to physical register
   * phys?
   */
  final boolean isForbidden(OPT_Register symb, OPT_Register phys) {
    if (VM.VerifyAssertions) {
      VM._assert(symb != null);
      VM._assert(phys != null);
    }
    RestrictedRegisterSet s = getRestrictions(symb);
    if (s == null) return false;
    return s.contains(phys);
  }

  /**
   * Is it forbidden to assign symbolic register symb to physical register r
   * in instruction s?
   */
  abstract boolean isForbidden(OPT_Register symb, OPT_Register r,
                               OPT_Instruction s);

  /**
   * An instance of this class represents restrictions on physical register 
   * assignment.
   * 
   * @author Stephen Fink
   */
  private static final class RestrictedRegisterSet {
    /**
     * The set of registers to which assignment is forbidden.
     */
    private OPT_BitSet bitset;

    /**
     * additionally, are all volatile registers forbidden?
     */
    private boolean noVolatiles = false;
    boolean getNoVolatiles() { return noVolatiles; }
    void setNoVolatiles() { noVolatiles = true; }

    /**
     * Default constructor
     */
    RestrictedRegisterSet(OPT_PhysicalRegisterSet phys) {
      bitset = new OPT_BitSet(phys);
    }

    /**
     * Add a particular physical register to the set.
     */
    void add(OPT_Register r) {
      bitset.add(r);
    }

    /**
     * Add a set of physical registers to this set.
     */
    void addAll(OPT_BitSet set) {
      bitset.addAll(set);
    }

    /**
     * Does this set contain a particular register?
     */
    boolean contains(OPT_Register r) {
      if (r.isVolatile() && noVolatiles) return true;
      else return bitset.contains(r);
    }
  }
}
