/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

import instructionFormats.*;
import java.util.Enumeration;
/**
 * Class to manage the allocation of the "compiler-independent" portion of 
 * the stackframe.
 * <p>
 *
 * TODO: Much clean-up still required.
 *
 * @author Dave Grove
 * @author Mauricio J. Serrano
 * @author Stephen Fink
 * @author Michael Hind
 */
abstract class OPT_GenericStackManager extends OPT_RVMIRTools 
implements OPT_Operators, OPT_PhysicalRegisterConstants {

  protected static final boolean debug = false;
  protected static final boolean verbose= false;

  abstract void insertPrologueAndEpilogue();

  abstract void insertSpillCode();

  abstract void initForArch(OPT_IR ir);

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
    insertSpillBefore(s.getNext(),r,type,location);
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
    insertUnspillBefore(s.getNext(),r,type,location);
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
  private int spillPointer = VM_Constants.STACKFRAME_HEADER_SIZE; 

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
  void forceFrameAllocation() { frameRequired = true; }

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
      caughtExceptionOffset = allocateOnStackFrame(4);
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

      pref.initialize(ir);
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

    //-#if RVM_WITH_ADAPTIVE_COMPILER
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
      prologueYieldpoint = true;
      frameRequired = true;
    }

    // (3) initialization code for the old OPT_RegisterManager.
    //     TODO: clean this up
    initPools(ir);
    initForArch(ir);

    // (4) save caughtExceptionOffset where the exception deliverer can find it
    ir.MIRInfo.info.setUnsignedExceptionOffset(caughtExceptionOffset);

    // (5) initialize the restrictions object
    restrict = new OPT_RegisterRestrictions(ir.regpool.getPhysicalRegisterSet());
  }


  protected OPT_IR ir;
  protected int spillLocNumber; // = 0;  (by default)
  protected int frameSize;      // = 0;  (by default)
  protected boolean allocFrame; // = false;  (by default)
  private boolean catchSeen;  // = false;  (by default)

  SpillRecord spillList[] = new SpillRecord[NUMBER_TYPE];

  /**
   * Set up register restrictions
   */
  final void computeRestrictions(OPT_IR ir) {
    restrict.init(ir);
  }
  
  /**
   * NOTE: calling conventions must be expanded before calling this.
   * @param ir the IR
   */
  final protected void initPools(OPT_IR ir) {
    this.ir = ir;
    
    // mapping from symbolic register to register/spill/param
    spillLocNumber = spillPointer;
    frameSize      = spillLocNumber;
    for (Enumeration e = ir.regpool.getPhysicalRegisterSet().enumerateAll();
         e.hasMoreElements(); ) {
      OPT_Register reg = (OPT_Register)e.nextElement();
      reg.clearAllocationFlags();
      reg.mapsToRegister = null; // mapping from real to symbolic
      OPT_RegisterAllocatorState.putPhysicalRegResurrectList(reg, null);
      OPT_RegisterAllocatorState.setSpill(reg, 0);
      reg.useCount       = Integer.MAX_VALUE;
    }
    for (OPT_Register symbReg = ir.regpool.getFirstRegister();
         symbReg != null; symbReg = symbReg.getNext()) {
      symbReg.clearSpill();
      symbReg.mapsToRegister = null;
    }
  }

  /**
   *  Find an volatile register to allocate starting at the reg corresponding
   *  to the symbolic register passed
   *  @param symbReg the place to start the search
   *  @return the allocated register or null
   */
  final OPT_Register allocateVolatileRegister(OPT_Register symbReg) {
    OPT_PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
    
    int physType = phys.getPhysicalRegisterType(symbReg);
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
   *  Find a nonvolatile register to allocate starting at the reg corresponding
   *  to the symbolic register passed
   *  @param symbReg the place to start the search
   *  @return the allocated register or null
   */
  abstract OPT_Register allocateNonVolatileRegister(OPT_Register symbReg);

  /**
   *  Try to allocate a specific register if possible 
   *  @param realReg 
   *  @param symbReg 
   *  @return the allocated register or null
   */
  final OPT_Register allocateStartingFromRegister(OPT_Register realReg, 
                                                  OPT_Register symbReg) {
    if (realReg.isVolatile()) {
      for (; realReg != null; realReg = realReg.getNext()) {
        if (realReg.isAvailable()) {
          realReg.allocateToRegister(symbReg);
          if (debug) { VM.sysWrite(" volat."+realReg+" to symb "+symbReg+'\n'); }
          return realReg;
        }
      }
    }
    return allocateNonVolatileRegister(symbReg);
  }


  /**
   *  This version is used to support LinearScan, and is only called
   *  from OPT_LinearScanLiveInterval.java
   *
   *  TODO: refactor this some more.
   *
   *  @param symbReg the symbolic register to allocate
   *  @param li the linear scan live interval
   *  @return the allocated register or null
   */
  abstract OPT_Register allocateRegister(OPT_Register symbReg,
                                      OPT_LinearScanLiveInterval live);


  /**
   * Restores the spill counter
   */
  final void restoreSpillCounter() {
    spillLocNumber = frameSize;
  }

  /**
   * Get a spill location for the type passed.  Reuse an old location
   * if one is available.  Else, allocate a new location and grow the
   * frame size to reflect the new layout.
   *
   * @param type the type to spill
   * @return the spill location
   */
  final int getSpillLocationNumber(int type) {
    // check the spill locations list by type
    SpillRecord sr = spillList[type];
    if (sr != null) { // reuse a spill location
      spillList[type] = sr.next;
      return sr.location;
    }
    // else allocate a new spill location
    return allocateNewSpillLocationNumber(type);    
  }

  /**
   * Allocate a new spill location and grow the
   * frame size to reflect the new layout.
   *
   * @param type the type to spill
   * @return the spill location
   */
  abstract int allocateNewSpillLocation(int type);

  /**
   * Allocate a new spill location and grow the
   * frame size to reflect the new layout.
   *
   * @param type the type to spill
   * @return the spill location
   */
  final int allocateNewSpillLocationNumber(int type) {

    // align spill location
    spillLocNumber = align(spillLocNumber,
                           OPT_PhysicalRegisterSet.getSpillAlignment(type));

    int spill = spillLocNumber;
    // increment by the spill size
    spillLocNumber += OPT_PhysicalRegisterSet.getSpillSize(type);
    if (spillLocNumber > frameSize) {
      frameSize = spillLocNumber;
    }
    return spill;
  }

  /**
   * Is this symbolic register passed allocated and not spilled?
   * @param symbReg the symbolic register
   * @return if the passed register is allocated and not spilled
   */
  final boolean allocated(OPT_Register symbReg) {
    return !symbReg.isSpilled() &&
      (OPT_RegisterAllocatorState.getMapping(symbReg) != null);
  }

  /**
   * @param symbReg the symbolic register
   * @return 
   */
  final boolean spilledParam(OPT_Register symbReg) {
    return OPT_RegisterAllocatorState.getSpill(symbReg) < 0;
  }

  /**
   * Gets a spill location for the symbolic register and
   *  marks it as spilled at that location
   * @param symbReg the symbolic register
   * @return the spill location for the register
   */
  final int assignToSpillLocation(OPT_Register symbReg) {
    OPT_PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
    int type = phys.getPhysicalRegisterType(symbReg);
    if (type == -1) {
      type = DOUBLE_REG;
    }
    int spill = getSpillLocationNumber(type);
    putSpillLocation(spill, symbReg);
    return spill;
  }

  /**
   * Gets a spill location for the symbolic register and
   *  marks it as spilled at that location
   * @param symbReg the symbolic register
   * @return the spill location for the register
   */
  final int getSpillLocation(OPT_Register symbReg) {
    OPT_PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
    int type = phys.getPhysicalRegisterType(symbReg);
    if (type == -1) {
      type = DOUBLE_REG;
    }
    int spill = getSpillLocationNumber(type);
    putSpillLocation(spill, symbReg);
    return spill;
  }

  /**
   * @symbReg the symbolic register
   * @return a new spill location
   */
  final int getNewSpillLocation(OPT_Register symbReg) {
    OPT_PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
    int type = phys.getPhysicalRegisterType(symbReg);
    if (type == -1)
      type = DOUBLE_REG;

    // allocate a new spill location

    // align spill location
    spillLocNumber = align(spillLocNumber, phys.getSpillAlignment(type));
    int spill = spillLocNumber;
    // increment by the spill size
    spillLocNumber += phys.getSpillSize(type);
    if (spillLocNumber > frameSize) {
      frameSize = spillLocNumber;
    }

    putSpillLocation(spill, symbReg);
    return spill;
  }

  /**
   * Frees the spill location associated with the passed symbolic register
   * @param symbReg the symbolic register
   */
  final void freeSpillLocation(OPT_Register symbReg)  {
    OPT_PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
    int type = phys.getPhysicalRegisterType(symbReg);
    if (type == -1) {
      type = DOUBLE_REG;
    }
    int spill = symbReg.getSpillAllocated();  
    spillList[type] = new SpillRecord(spill, spillList[type]);
  }

  /**
   * Marks the passed symbolic register as being spilled at the location
   *  passed
   * @param spill a spill location
   * @param symbReg a symbolic register
   */
  final void putSpillLocation(int spill, OPT_Register symbReg) {
    symbReg.spillRegister();
    symbReg.mapsToRegister = null;
    OPT_RegisterAllocatorState.setSpill(symbReg, spill);
  }

  /**
   * Given a symbolic register, return a code that indicates the type
   * of the value stored in the register.
   * Note: This routine returns INT_VALUE for longs
   *
   * @return one of INT_VALUE, FLOAT_VALUE, DOUBLE_VALUE, CONDITION_VALUE
   */
  final byte getValueType(OPT_Register r) {
    if (r.isInteger() || r.isLong()) {
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
   * An element in a list of free spill locations.
   */
  static class SpillRecord {
    int location;
    SpillRecord next;

    SpillRecord(int Location, SpillRecord Next) {
      location = Location;
      next     = Next;
    }
  }
}

