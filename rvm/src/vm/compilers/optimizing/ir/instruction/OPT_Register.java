/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * Represents a symbolic or physical register. 
 * OPT_Registers are shared among all OPT_Operands -- for a given register 
 * pool, there is only one instance of an OPT_Register with each number.
 * 
 * @see OPT_RegisterOperand
 * @see OPT_RegisterPool
 * 
 * @author Mauricio Serrano
 * @author John Whaley
 * @modified Stephen Fink
 * @modified Dave Grove
 * @modified Vivek Sarkar
 */
final class OPT_Register {

  /**
   * Index number relative to register pool.
   */
  int number;

  /**
   * Encoding of register properties & scratch bits
   */
  int flags;

  static private final int LOCAL            = 0x00001;  /* local variable */
  static private final int SPAN_BASIC_BLOCK = 0x00002;  /* live on a basic block boundary */
  static private final int SSA              = 0x00004;  /* only one assignment to this register */
  static private final int SEEN_USE         = 0x00008;  /* seen use */
  static private final int PHYSICAL         = 0x00010;  /* physical (real) register - not symbolic */

  /*  register type  for both physical and symbolic */
  static private final int TYPE_SHIFT       = 7;        /* # bits to shift */
  static private final int INTEGER          = 0x00080;  /* integer */
  static private final int FLOAT            = 0x00100;  /* floating-point single precision */
  static private final int DOUBLE           = 0x00200;  /* floating-point double precision */
  static private final int CONDITION        = 0x00400;  /* condition: PPC,x86*/
  static private final int LONG             = 0x00800;  /* long (two ints)*/
  static private final int VALIDATION       = 0x01000;  /* validation pseudo-register */


  /* this for physical register only */
  static private final int VOLATILE         = 0x02000;  
  static private final int NON_VOLATILE     = 0x04000;

  /* used with live analysis */
  static private final int EXCLUDE_LIVEANAL = 0x08000; /* reg is excluded from live analysis */

  /* used by the register allocator */
  static private final int  SPILLED         = 0x10000; /* spilled into a memory location */
  static private final int  TOUCHED         = 0x20000; /* register touched */
  static private final int  ALLOCATED       = 0x40000; /* allocated to some register */
  static private final int  PINNED          = 0x80000; /* pinned, unavailable for allocation */


  /* derived constants to be exported */
  static private final int TYPE_MASK = (INTEGER|FLOAT|DOUBLE|CONDITION|LONG|VALIDATION);
  static final int NUMBER_TYPE    = (TYPE_MASK>>>TYPE_SHIFT)+1;
  static final int INTEGER_TYPE   = INTEGER     >>> TYPE_SHIFT;
  static final int FLOAT_TYPE     = FLOAT       >>> TYPE_SHIFT;
  static final int DOUBLE_TYPE    = DOUBLE      >>> TYPE_SHIFT;
  static final int CONDITION_TYPE = CONDITION   >>> TYPE_SHIFT;
  static final int LONG_TYPE      = LONG        >>> TYPE_SHIFT;
  static final int VALIDATION_TYPE= VALIDATION  >>> TYPE_SHIFT;

  boolean isTemp()          { return (flags & LOCAL           ) == 0; }
  boolean isLocal()         { return (flags & LOCAL           ) != 0; }
  boolean spansBasicBlock() { return (flags & SPAN_BASIC_BLOCK) != 0; }
  boolean isSSA()           { return (flags & SSA             ) != 0; }
  boolean seenUse()         { return (flags & SEEN_USE        ) != 0; }
  boolean isPhysical()      { return (flags & PHYSICAL        ) != 0; }
  boolean isSymbolic()      { return (flags & PHYSICAL        ) == 0; }

  boolean isInteger()       { return (flags & INTEGER         ) != 0; }
  boolean isFloat()         { return (flags & FLOAT           ) != 0; } 
  boolean isDouble()        { return (flags & DOUBLE          ) != 0; }
  boolean isFloatingPoint() { return (flags & (FLOAT|DOUBLE)  ) != 0; }
  boolean isLong()          { return (flags & LONG            ) != 0; }
  boolean isCondition()     { return (flags & CONDITION       ) != 0; }
  boolean isValidation()    { return (flags & VALIDATION      ) != 0; }
  boolean isExcludedLiveA() { return (flags & EXCLUDE_LIVEANAL) != 0; }

  int     getType()         { return (flags>>>TYPE_SHIFT)&(NUMBER_TYPE-1); }  

  boolean isVolatile()      { return (flags & VOLATILE        ) != 0; }
  boolean isNonVolatile()   { return (flags & NON_VOLATILE    ) != 0; }

  void setLocal()           { flags |= LOCAL;            }
  void setSpansBasicBlock() { flags |= SPAN_BASIC_BLOCK; }
  void setSSA()             { flags |= SSA;              }
  void setSeenUse()         { flags |= SEEN_USE;         }
  void setPhysical()        { flags |= PHYSICAL;         }

  void setInteger()         { flags |= INTEGER;          }
  void setFloat()           { flags |= FLOAT;            }
  void setDouble()          { flags |= DOUBLE;           }
  void setLong()            { flags |= LONG;             }
  void setCondition()       { flags =  (flags & ~TYPE_MASK) | CONDITION; }
  void setValidation()      { flags |= VALIDATION;       }
  void setExcludedLiveA()   { flags |= EXCLUDE_LIVEANAL; }

  void setVolatile()        { flags |= VOLATILE;         }
  void setNonVolatile()     { flags |= NON_VOLATILE;     }

  void putSSA(boolean a)    { if (a) setSSA(); else clearSSA(); }
  void putSpansBasicBlock(boolean a) { if (a) setSpansBasicBlock(); else clearSpansBasicBlock(); }

  void clearLocal()           { flags &= ~LOCAL;            }
  void clearSpansBasicBlock() { flags &= ~SPAN_BASIC_BLOCK; }
  void clearSSA()             { flags &= ~SSA;              }
  void clearSeenUse()         { flags &= ~SEEN_USE;         }
  void clearPhysical()        { flags &= ~PHYSICAL;         }

  void clearInteger()         { flags &= ~INTEGER;          }
  void clearFloat()           { flags &= ~FLOAT;            }
  void clearDouble()          { flags &= ~DOUBLE;           }
  void clearLong()            { flags &= ~LONG;             }
  void clearCondition()       { flags &= ~CONDITION;        }
  void clearType() 	      { flags &= ~TYPE_MASK;	    }
  void clearValidation()      { flags &= ~VALIDATION;       }


  Object scratchObject;

  /** 
   * Used in dependence graph construction.
   */
  void setdNode(OPT_DepGraphNode a) {
    scratchObject = a;
  }

  OPT_DepGraphNode dNode() {
    return (OPT_DepGraphNode)scratchObject;
  }


  /**
   * Used to store register lists.
   * Computed on demand by OPT_IR.computeDU().
   */
  OPT_RegisterOperand defList, useList;

  /** 
   * This accessor is only valid when register lists are valid 
   */
  OPT_Instruction getFirstDef() {
    if (defList == null) 
      return null;
    else 
      return defList.instruction;
  }


  /**
   * The number of uses; used by flow-insensitive optimizations
   */
  int useCount;

  /**
   * A field optimizations can use as they choose
   */
  int scratch;


  OPT_Register(int Number) {
    number = Number;
  }


  int getNumber() {
    int start = OPT_PhysicalRegisterSet.getSize();
    return number - start;
  }

  /**
   * TODO: This method is dangerous, and should be avoided and deprecated.
   */
  void cloneTo(OPT_Register reg, OPT_IR ir) {
    int newNumber = reg.number;
    OPT_PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
    if (newNumber < phys.getSize()) { 
      number = newNumber;
      flags  = phys.get(number).flags;
    }
  }

  /**
   * Returns the string representation of this register.
   */
  public String toString() {
    if (isPhysical())
      return OPT_PhysicalRegisterSet.getName(number);

    // Set s to descriptive letter for register type
    String s = isLocal() ? "l" : "t";
    s = s + getNumber() + (spansBasicBlock()?"p":"") + (isSSA()?"s":"") +typeName();
    return s;
  }


  String typeName() {
    String s = "";
    if (isCondition()) s += "c";
    if (isInteger()) s += "i"; 
    if (isDouble()) s += "d";
    if (isFloat()) s += "f";
    if (isLong()) s += "l";
    if (isValidation()) s += "v";
    if (s == null) s = "_";
    return s;
  }

  
  /* used by the register allocator */
  OPT_Register mapsToRegister;
  void clearAllocationFlags() {
    flags &= ~(PINNED | TOUCHED | ALLOCATED | SPILLED);
  }
  void pinRegister() {
    flags |= PINNED | TOUCHED;
  }
  void reserveRegister() {
    flags |= PINNED;
  }
  void touchRegister() {
    flags |= TOUCHED;
  }
  void allocateRegister() {
    flags = (flags & ~SPILLED) | (ALLOCATED | TOUCHED);
  }
  void allocateRegister(OPT_Register reg) {
    flags = (flags & ~SPILLED) | (ALLOCATED | TOUCHED);
    mapsToRegister = reg;
  }
  void allocateToRegister(OPT_Register reg) {
    this.allocateRegister(reg);
    reg.allocateRegister(this);
  }
  void deallocateRegister() {
    flags &= ~ALLOCATED;
    mapsToRegister = null;
  }
  void freeRegister() {
    deallocateRegister();
    OPT_Register symbReg = mapsToRegister;
    if (symbReg != null)
      symbReg.clearSpill();
  }
  void spillRegister() {
    flags = (flags & ~ALLOCATED) | SPILLED;
  }
  void clearSpill() {
    flags &= ~SPILLED;
  }
  void unpinRegister() {
    flags &= ~PINNED;
  }
  boolean isTouched() {
    return (flags & TOUCHED) != 0;
  }
  boolean isAllocated() {
    return (flags & ALLOCATED) != 0;
  }
  boolean isSpilled() {
    return (flags & SPILLED) != 0;
  }
  boolean isPinned() {
    return (flags & PINNED) != 0;
  }
  boolean isAvailable() {
    return (flags & (ALLOCATED | PINNED)) == 0;
  }
  OPT_Register getRegisterAllocated() {
    return mapsToRegister;
  }
  int getSpillAllocated() {
    return scratch;
  }

  final public int hashCode() {
    return number;
  }


  /* inlined behavior of DoublyLinkedListElement */
  OPT_Register next, prev;
  final OPT_Register getNext() { return next; }
  final void setNext(OPT_Register e) { next = e; }
  final OPT_Register getPrev() { return prev; }
  final void linkWithNext(OPT_Register Next) {
    next = Next;
    Next.prev = this;
  }

  final void append (OPT_Register l) {
    next = l;
    l.prev = this;
  }

  final OPT_Register remove() {
    OPT_Register Prev = prev, Next = next;
    if (Prev != null) Prev.next = Next;
    if (Next != null) Next.prev = Prev;
    return Next;
  }
  /* end of inlined behavior */
}
