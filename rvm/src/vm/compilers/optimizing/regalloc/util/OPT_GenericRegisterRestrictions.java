/*
 * (C) Copyright IBM Corp. 2001
 */
import java.util.Vector;
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
class OPT_GenericRegisterRestrictions implements OPT_Operators {
  // for each symbolic register, the set of physical registers that are
  // illegal for assignment
  private java.util.HashMap hash = new java.util.HashMap();

  // a set of symbolic registers that must not be spilled.
  private java.util.HashSet noSpill = new java.util.HashSet();

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
  void noteMustNotSpill(OPT_Register r) {
    noSpill.add(r);
  }

  /**
   * Is spilling a register forbidden?
   */
  boolean mustNotSpill(OPT_Register r) {
    return noSpill.contains(r);
  }

  /**
   * Record all the register restrictions dictated by an IR.
   *
   * PRECONDITION: the instructions in each basic block are numbered in
   * increasing order before calling this.  The number for each
   * instruction is stored in its <code>scratch</code> field.
   */
  void init(OPT_IR ir) {
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
  protected void processBlock(OPT_BasicBlock bb) {
    Vector physical = new Vector(3);
    Vector symbolic = new Vector(10);
    
    // 1. walk through the live intervals and identify which correspond to
    // physical and symbolic registers
    for (Enumeration e = bb.enumerateLiveIntervals();
         e.hasMoreElements();) {
      OPT_LiveIntervalElement li = (OPT_LiveIntervalElement)e.nextElement();
      OPT_Register r = li.getRegister();
      if (r.isPhysical()) {
        if (r.isVolatile() || r.isNonVolatile()) {
          physical.addElement(li);
        }
      } else {
        symbolic.addElement(li);
      }
    }

    // 2. walk through the live intervals for physical registers.  For
    // each such interval, record the conflicts where the live range
    // overlaps a live range for a symbolic register.
    for (Enumeration p = physical.elements(); p.hasMoreElements(); ) {
      OPT_LiveIntervalElement phys = (OPT_LiveIntervalElement)p.nextElement();
      for (Enumeration s = symbolic.elements(); s.hasMoreElements(); ) {
        OPT_LiveIntervalElement symb = (OPT_LiveIntervalElement)s.nextElement();
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
        for (Enumeration sym = symbolic.elements(); sym.hasMoreElements(); ) {
          OPT_LiveIntervalElement symb = (OPT_LiveIntervalElement)
                                        sym.nextElement();
          if (contains(symb,s.scratch)) {
            addRestrictions(symb.getRegister(),phys.getVolatiles());
          }
        }
      }
    }

    // 4. architecture-specific restrictions
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
  void addArchRestrictions(OPT_BasicBlock bb, Vector symbolics) {}

  /**
   * Does a live range R contain an instruction with number n?
   * 
   * PRECONDITION: the instructions in each basic block are numbered in
   * increasing order before calling this.  The number for each
   * instruction is stored in its <code>scratch</code> field.
   */
  protected boolean contains(OPT_LiveIntervalElement R, int n) {
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
    int begin1  = -1;
    int end1    = -1;
    int begin2  = -1;
    int end2    = -1;
      
    if (li1.getBegin() != null) {
      begin1 = li1.getBegin().scratch;
    }
    if (li1.getEnd() != null) {
      end1 = li1.getEnd().scratch;
    }
    if (li2.getBegin() != null) {
      begin2 = li2.getBegin().scratch;
    }
    if (li2.getEnd() != null) {
      end2 = li2.getEnd().scratch;
    }

    // Under the following conditions: the live ranges do NOT overlap:
    // 1. begin2 >= end1 > -1
    // 2. begin1 >= end2 > -1
    // Under all other cases, the ranges overlap
    if (end2 <= begin1 && end2 > -1) return false;
    if (end1 <= begin2 && end1 > -1) return false;

    return true;
  }

  /**
   * Record that it is illegal to assign a symbolic register symb to any
   * of a set of physical registers 
   */
  void addRestrictions(OPT_Register symb, OPT_BitSet set) {
    OPT_RestrictedRegisterSet r = (OPT_RestrictedRegisterSet)hash.get(symb);
    if (r == null) {
      r = new OPT_RestrictedRegisterSet(phys);
      hash.put(symb,r);
    }
    r.addAll(set);
  }

  /**
   * Record that it is illegal to assign a symbolic register symb to a
   * physical register p
   */
  void addRestriction(OPT_Register symb, OPT_Register p) {
    OPT_RestrictedRegisterSet r = (OPT_RestrictedRegisterSet)hash.get(symb);
    if (r == null) {
      r = new OPT_RestrictedRegisterSet(phys);
      hash.put(symb,r);
    }
    r.add(p);
  }

  /**
   * Return the set of restricted physical register for a given symbolic
   * register. Return null if no restrictions.
   */
  OPT_RestrictedRegisterSet getRestrictions(OPT_Register symb) {
    return (OPT_RestrictedRegisterSet)hash.get(symb);
  }

  /**
   * Is it forbidden to assign symbolic register symb to physical register
   * phys?
   */
  boolean isForbidden(OPT_Register symb, OPT_Register phys) {
    if (VM.VerifyAssertions) {
      VM.assert(symb != null);
      VM.assert(phys != null);
    }
    OPT_RestrictedRegisterSet s = getRestrictions(symb);
    if (s == null) return false;
    return s.contains(phys);
  }
  
}
