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

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;

import org.jikesrvm.VM;
import org.jikesrvm.compilers.opt.ir.BasicBlock;
import org.jikesrvm.compilers.opt.ir.GenericPhysicalRegisterSet;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.ir.Instruction;

import static org.jikesrvm.compilers.opt.ir.Operators.YIELDPOINT_OSR;

import org.jikesrvm.compilers.opt.ir.Register;
import org.jikesrvm.compilers.opt.liveness.LiveInterval;
import org.jikesrvm.compilers.opt.util.BitSet;

/**
 * An instance of this class provides a mapping from symbolic register to
 * a set of restricted registers.
 * <p>
 * Each architecture will subclass this in a class
 * RegisterRestrictions.
 */
public abstract class GenericRegisterRestrictions {
  // for each symbolic register, the set of physical registers that are
  // illegal for assignment
  private final HashMap<Register, RestrictedRegisterSet> hash = new HashMap<Register, RestrictedRegisterSet>();

  // a set of symbolic registers that must not be spilled.
  private final HashSet<Register> noSpill = new HashSet<Register>();

  protected final GenericPhysicalRegisterSet phys;

  protected RegisterAllocatorState regAllocState;

  protected GenericRegisterRestrictions(GenericPhysicalRegisterSet phys) {
    this.phys = phys;
  }

  protected final void noteMustNotSpill(Register r) {
    noSpill.add(r);
  }

  public final boolean mustNotSpill(Register r) {
    return noSpill.contains(r);
  }

  /**
   * Records all the register restrictions dictated by an IR.
   *
   * PRECONDITION: the instructions in each basic block are numbered in
   * increasing order before calling this.
   *
   * @param ir the IR to process
   */
  public final void init(IR ir) {
    LiveInterval livenessInformation = ir.getLivenessInformation();
    this.regAllocState = ir.MIRInfo.regAllocState;

    // process each basic block
    for (Enumeration<BasicBlock> e = ir.getBasicBlocks(); e.hasMoreElements();) {
      BasicBlock b = e.nextElement();
      processBlock(b, livenessInformation);
    }
  }

  /**
   * Records all the register restrictions dictated by live ranges on a
   * particular basic block.<p>
   *
   * PRECONDITION: the instructions in each basic block are numbered in
   * increasing order before calling this.
   *
   * @param bb the bb to process
   * @param liveness liveness information for the IR
   */
  private void processBlock(BasicBlock bb, LiveInterval liveness) {
    ArrayList<LiveIntervalElement> symbolic = new ArrayList<LiveIntervalElement>(20);
    ArrayList<LiveIntervalElement> physical = new ArrayList<LiveIntervalElement>(20);

    // 1. walk through the live intervals and identify which correspond to
    // physical and symbolic registers
    for (Enumeration<LiveIntervalElement> e = liveness.enumerateLiveIntervals(bb); e.hasMoreElements();) {
      LiveIntervalElement li = e.nextElement();
      Register r = li.getRegister();
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
    for (LiveIntervalElement phys : physical) {
      for (LiveIntervalElement symb : symbolic) {
        if (overlaps(phys, symb)) {
          addRestriction(symb.getRegister(), phys.getRegister());
        }
      }
    }

    // 3. Volatile registers used by CALL instructions do not appear in
    // the liveness information.  Handle CALL instructions as a special
    // case.
    for (Enumeration<Instruction> ie = bb.forwardInstrEnumerator(); ie.hasMoreElements();) {
      Instruction s = ie.nextElement();
      if (s.operator().isCall() && !s.operator().isCallSaveVolatile()) {
        for (LiveIntervalElement symb : symbolic) {
          if (contains(symb, regAllocState.getDFN(s))) {
            forbidAllVolatiles(symb.getRegister());
          }
        }
      }

      // Before OSR points, we need to save all FPRs,
      // On OptExecStateExtractor, all GPRs have to be recovered,
      // but not FPRS.
      //
      if (s.operator() == YIELDPOINT_OSR) {
        for (LiveIntervalElement symb : symbolic) {
          if (symb.getRegister().isFloatingPoint()) {
            if (contains(symb, regAllocState.getDFN(s))) {
              forbidAllVolatiles(symb.getRegister());
            }
          }
        }
      }
    }

    // 3. architecture-specific restrictions
    addArchRestrictions(bb, symbolic);
  }

  /**
   * Add architecture-specific register restrictions for a basic block.
   * Override as needed.
   *
   * @param bb the basic block
   * @param symbolics the live intervals for symbolic registers on this
   * block
   */
  public void addArchRestrictions(BasicBlock bb, ArrayList<LiveIntervalElement> symbolics) {}

  /**
   * Does a live range R contain an instruction with number n?<p>
   *
   * PRECONDITION: the instructions in each basic block are numbered in
   * increasing order before calling this.
   *
   * @param R the live range
   * @param n the instruction number
   *
   * @return {@code true} if and only if the live range contains an instruction
   *  with the given number
   */
  protected final boolean contains(LiveIntervalElement R, int n) {
    int begin = -1;
    int end = Integer.MAX_VALUE;
    if (R.getBegin() != null) {
      begin = regAllocState.getDFN(R.getBegin());
    }
    if (R.getEnd() != null) {
      end = regAllocState.getDFN(R.getEnd());
    }

    return ((begin <= n) && (n <= end));
  }

  /**
   * Do two live ranges overlap?<p>
   *
   * PRECONDITION: the instructions in each basic block are numbered in
   * increasing order before calling this.
   *
   * @param li1 first live range
   * @param li2 second live range
   * @return {@code true} if and only if the live ranges overlap
   */
  private boolean overlaps(LiveIntervalElement li1, LiveIntervalElement li2) {
    // Under the following conditions: the live ranges do NOT overlap:
    // 1. begin2 >= end1 > -1
    // 2. begin1 >= end2 > -1
    // Under all other cases, the ranges overlap

    int begin1 = -1;
    int end1 = -1;
    int begin2 = -1;
    int end2 = -1;

    if (li1.getBegin() != null) {
      begin1 = regAllocState.getDFN(li1.getBegin());
    }
    if (li2.getEnd() != null) {
      end2 = regAllocState.getDFN(li2.getEnd());
    }
    if (end2 <= begin1 && end2 > -1) return false;

    if (li1.getEnd() != null) {
      end1 = regAllocState.getDFN(li1.getEnd());
    }
    if (li2.getBegin() != null) {
      begin2 = regAllocState.getDFN(li2.getBegin());
    }
    return end1 > begin2 || end1 <= -1;

  }

  /**
   * Records that it is illegal to assign a symbolic register symb to any
   * volatile physical registerss.
   *
   * @param symb the register that must not be assigned to a volatile
   *  physical register
   */
  final void forbidAllVolatiles(Register symb) {
    RestrictedRegisterSet r = hash.get(symb);
    if (r == null) {
      r = new RestrictedRegisterSet(phys);
      hash.put(symb, r);
    }
    r.setNoVolatiles();
  }

  /**
   * Records that it is illegal to assign a symbolic register symb to any
   * of a set of physical registers.
   *
   * @param symb the symbolic register to be restricted
   * @param set the physical registers that the symbolic register
   *  must not be assigned to
   */
  protected final void addRestrictions(Register symb, BitSet set) {
    RestrictedRegisterSet r = hash.get(symb);
    if (r == null) {
      r = new RestrictedRegisterSet(phys);
      hash.put(symb, r);
    }
    r.addAll(set);
  }

  /**
   * Record thats it is illegal to assign a symbolic register symb to a
   * physical register p.
   *
   * @param symb the symbolic register to be restricted
   * @param p the physical register that the symbolic register
   *  must not be assigned to
   */
  protected final void addRestriction(Register symb, Register p) {
    RestrictedRegisterSet r = hash.get(symb);
    if (r == null) {
      r = new RestrictedRegisterSet(phys);
      hash.put(symb, r);
    }
    r.add(p);
  }

  /**
   * @param symb the register whose restrictions where interested in
   * @return the set of restricted physical register for a given symbolic
   * register, {@code null} if no restrictions.
   */
  final RestrictedRegisterSet getRestrictions(Register symb) {
    return hash.get(symb);
  }

  /**
   * Is it forbidden to assign symbolic register symb to any volatile
   * register?
   * @param symb symbolic register to check
   * @return {@code true}: yes, all volatiles are forbidden.
   *         {@code false} :maybe, maybe not
   */
  public final boolean allVolatilesForbidden(Register symb) {
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
   *
   * @param symb a symbolic register
   * @param phys a physical register
   * @return {@code true} if it's forbidden, false otherwise
   */
  public final boolean isForbidden(Register symb, Register phys) {
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
   *
   * @param symb a symbolic register
   * @param r a physical register
   * @param s the instruction that's the scope for the check
   * @return {@code true} if it's forbidden, false otherwise
   */
  public abstract boolean isForbidden(Register symb, Register r, Instruction s);

  /**
   * An instance of this class represents restrictions on physical register
   * assignment.
   */
  private static final class RestrictedRegisterSet {
    /**
     * The set of registers to which assignment is forbidden.
     */
    private final BitSet bitset;

    /**
     * additionally, are all volatile registers forbidden?
     */
    private boolean noVolatiles = false;

    boolean getNoVolatiles() {
      return noVolatiles;
    }

    void setNoVolatiles() {
      noVolatiles = true;
    }

    RestrictedRegisterSet(GenericPhysicalRegisterSet phys) {
      bitset = new BitSet(phys);
    }

    void add(Register r) {
      bitset.add(r);
    }

    void addAll(BitSet set) {
      bitset.addAll(set);
    }

    boolean contains(Register r) {
      if (r.isVolatile() && noVolatiles) {
        return true;
      } else {
        return bitset.contains(r);
      }
    }
  }
}
