/*
 * (C) Copyright IBM Corp. 2001, 2003
 */
//$Id$
package com.ibm.JikesRVM.opt.ir;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.classloader.*;
import com.ibm.JikesRVM.opt.*;
import java.util.Enumeration;

import org.vmmagic.pragma.*;

/**
 * A basic block in the 
 * {@link OPT_ControlFlowGraph Factored Control Flow Graph (FCFG)}.
 * <p>
 * Just like in a standard control flow graph (CFG), a FCFG basic block
 * contains a linear sequence of instructions. However, in the FCFG,
 * a Potentially Excepting Instruction (PEI) does not necessarily end its
 * basic block.  Therefore, although instructions within a FCFG basic block 
 * have the expected dominance relationships, they do <em>not</em> have the
 * same post-dominance relationships as they would under the traditional
 * basic block formulation used in a CFG.  
 * We chose to use an FCFG because doing so significantly reduces the
 * number of basic blocks and control flow graph edges, thus reducing
 * the time and space costs of representing the FCFG and also 
 * increasing the effectiveness of local (within a single basic block)
 * analysis.  However, using an FCFG does complicate flow-sensitive
 * global analaysis.  Many analyses can be easily extended to
 * work on the FCFG.  For those analyses that cannot, we provide utilities
 * ({@link OPT_IR#unfactor()}, {@link #unfactor(OPT_IR)})
 * to effectively convert the FCFG into a CFG.  
 * For a more detailed description of the FCFG and its implications for
 * program analysis see the PASTE'99 paper by Choi et al. 
 *   <a href="http://www.research.ibm.com/jalapeno/publication.html#paste99">
 *   Efficient and Precise Modeling of Exceptions for the Analysis of Java Programs </a>
 * <p>
 * The instructions in a basic block have the following structure
 * <ul>
 * <li> The block begins with a <code>LABEL</code>.
 * <li> Next come zero or more non-branch instructions.
 * <li> Next come zero or more conditional branches
 * <li> Next comes zero or one unconditional branch
 * <li> Finally the block ends with a <code>BBEND</code> 
 * </ul>
 * <code>CALL</code> instructions do not end their basic block.
 * <code>ATHROW</code> instructions do end their basic block.
 * Conventionally, we refer to the <em>real</em> instructions of 
 * the block as those that are between the LABEL and the BBEND.
 * We say that the block is empty if it contains no real instructions.
 * <p>
 * 
 * @see OPT_IR
 * @see OPT_Instruction
 * @see OPT_ControlFlowGraph
 * 
 * @author Stephen Fink
 * @author Dave Grove
 * @author Mauricio J. Serrano
 * @author John Whaley
 * @author Martin Trapp
 */

public class OPT_BasicBlock extends OPT_SortedGraphNode 
    implements OPT_Operators, OPT_Constants {

  /** Bitfield used in flag encoding */
  static final int CAN_THROW_EXCEPTIONS = 0x01;
  /** Bitfield used in flag encoding */
  static final int IMPLICIT_EXIT_EDGE   = 0x02;
  /** Bitfield used in flag encoding */
  static final int EXCEPTION_HANDLER    = 0x04;     
  /** Bitfield used in flag encoding */
  static final int REACHABLE_FROM_EXCEPTION_HANDLER    = 0x08;     
  /** Bitfield used in flag encoding */
  static final int UNSAFE_TO_SCHEDULE   = 0x10; 
  /** Bitfield used in flag encoding */
  static final int INFREQUENT           = 0x20;
  /** Bitfield used in flag encoding */
  static final int SCRATCH              = 0x40;
  /** Bitfield used in flag encoding */
  static final int LANDING_PAD          = 0x80;


  /**
   * Encodes exception handler info for this block.  
   * May be shared if multiple blocks have exactly the same chain
   * of exception handlers.
   */
  protected OPT_ExceptionHandlerBasicBlockBag exceptionHandlers;

  /**
   * First instruction of the basic block (LABEL).
   */
  OPT_Instruction start;

  /**
   * Last instruction of the basic block (BBEND).
   */
  OPT_Instruction end;

  /**
   * Used to encode various properties of the block.
   */
  protected int flags;

  /**
   * Relative execution frequency of this basic block.
   * The entry block to a CFG has weight 1.0;
   */
  protected float freq;


  /**
   * Creates a new basic block at the specified location.
   * It initially contains a single label instruction pointed to 
   * by start and a BBEND instruction pointed to by end.
   * 
   * @param i         bytecode index to create basic block at
   * @param position  the inline context for this basic block
   * @param cfg       the FCFG that will contain the basic block
   */
  public OPT_BasicBlock(int i, 
                        OPT_InlineSequence position, 
                        OPT_ControlFlowGraph cfg) {
    this(i, position, cfg.allocateNodeNumber());
  }

  /**
   * Creates a new basic block at the specified location with
   * the given basic block number. 
   * It initially contains a single label instruction pointed to 
   * by start and a BBEND instruction pointed to by end.
   * WARNING: Don't call this constructor directly if the created basic 
   * block is going to be in some control flow graph, since it
   * may not get assigned a unique number. 
   * 
   * @param i         bytecode index to create basic block at
   * @param position  the inline context for this basic block
   * @param num       the number to assign the basic block
   */
  protected OPT_BasicBlock(int i, 
                           OPT_InlineSequence position, 
                           int num) {
    setNumber(num);
    start = Label.create(LABEL, new OPT_BasicBlockOperand(this));
    start.bcIndex = i;

    start.position = position;
    end = BBend.create(BBEND, new OPT_BasicBlockOperand(this));
    // NOTE: we have no idea where this block will end so it
    // makes no sense to set its bcIndex or position.
    // In fact, the block may end in a different method entirely,
    // so setting its position to the same as start may silently
    // get us into all kinds of trouble. --dave.
    end.bcIndex = OPT_Constants.UNKNOWN_BCI;
    start.linkWithNext(end);
    initInOutSets();
  }

  /**
   * This constructor is only used for creating an EXIT node
   */
  private OPT_BasicBlock() {
    setNumber(1);
  } 

  final void initInOutSets() { }

  /**
   * Make an EXIT node.
   */
  static OPT_BasicBlock makeExit() { 
    return new OPT_BasicBlock(); 
  }


  /**
   * Returns the string representation of this basic block.
   * @return a String that is the name of the block.
   */
  public String toString() {
    String result = "";
    int number = getNumber();
    if (isExit()) return "EXIT"+number;
    if (number==0) return "BB0 (ENTRY)";
    return "BB"+number;
  }


  /**
   * Print a detailed dump of the block to the sysWrite stream
   */
  public final void printExtended() {
    VM.sysWrite("Basic block "+toString()+"\n");

    // print in set.
    OPT_BasicBlock bb2;
    OPT_BasicBlockEnumeration e2 = getIn();
    VM.sysWrite("\tin: ");
    if (!e2.hasMoreElements()) VM.sysWrite("<none>\n");
    else {
      bb2 = e2.next();
      VM.sysWrite(bb2.toString());
      while (e2.hasMoreElements()) {
        bb2 = e2.next();
        VM.sysWrite(", "+bb2.toString());
      }
      VM.sysWrite("\n");
    }

    // print out set.
    e2 = getNormalOut();
    VM.sysWrite("\tnormal out: ");
    if (!e2.hasMoreElements()) VM.sysWrite("<none>\n");
    else {
      bb2 = e2.next();
      VM.sysWrite(bb2.toString());
      while (e2.hasMoreElements()) {
        bb2 = e2.next();
        VM.sysWrite(", "+bb2.toString());
      }
      VM.sysWrite("\n");
    }

    e2 = getExceptionalOut();
    VM.sysWrite("\texceptional out: ");
    if (!e2.hasMoreElements()) VM.sysWrite("<none>\n");
    else {
      bb2 = e2.next();
      VM.sysWrite(bb2.toString());
      while (e2.hasMoreElements()) {
        bb2 = e2.next();
        VM.sysWrite(", "+bb2.toString());
      }
      VM.sysWrite("\n");
    }
    
    if (mayThrowUncaughtException()) {
      VM.sysWrite("\tMay throw uncaught exceptions, implicit edge to EXIT\n");
    }

    if (hasExceptionHandlers()) {
      VM.sysWrite("\tIn scope exception handlers: ");
      e2 = getExceptionHandlers();
      if (e2.hasMoreElements()) {
          bb2 = e2.next();
          VM.sysWrite(bb2.toString());
          while (e2.hasMoreElements()) {
              bb2 = e2.next();
              VM.sysWrite(", "+bb2.toString());
          }
      } else
          VM.sysWrite("<none>");
      VM.sysWrite("\n");
    }

    if (getNext() != null) {
      VM.sysWrite("\tNext in code order: "+getNext().toString()+"\n");
    }
    
    if (start != null) {
      VM.sysWrite("\tInstructions:\n");
      OPT_Instruction inst = start;
      while (inst != end) {
        VM.sysWrite(inst.bcIndex+":\t"+inst+"\n");
        inst = inst.getNext();
      }
      VM.sysWrite(inst.bcIndex+":\t"+inst+"\n");
    }
    VM.sysWrite("\n");
  }

  
  /**
   * Clear the scratch object from previous uses
   * (rename scratchObject manipulations for GCMaps/RegAlloc).
   */
  public final void initializeLiveRange() {
    scratchObject = null;
  }


  /**
   * @return an enumeration of the live interval elements for this basic
   * block.
   */
  public final OPT_LiveIntervalEnumeration enumerateLiveIntervals() {
    return new
      OPT_LiveIntervalEnumeration((OPT_LiveIntervalElement)scratchObject);
  }

  /**
   * Returns NULL or an OPT_LiveIntervalElement (GCMaps/RegAlloc).
   * @return scratchObject cast as an OPT_LiveIntevalElement
   */
  public final OPT_LiveIntervalElement getFirstLiveIntervalElement() {
    if (scratchObject != null)
      return (OPT_LiveIntervalElement) scratchObject;
    else
      return null;
  }

  
  /**
   * Prepend a live interval element to the list being maintained
   * in scratchObject (GCMaps/RegAlloc).
   * 
   * @param li the live interval element to add
   */
  public final void prependLiveIntervalElement(OPT_LiveIntervalElement li) {
    li.setNext((OPT_LiveIntervalElement)scratchObject); 
    scratchObject = li;
  }


  /**
   * Can this block possibly throw an exception?
   * May conservatively return true even if the block
   * does not contain a PEI.
   * 
   * @return <code>true</code> if the block might raise an 
   *         exception or <code>false</code> if it cannot
   */
  public final boolean canThrowExceptions() {  
    return (flags & CAN_THROW_EXCEPTIONS) != 0; 
  }
  
  /**
   * Can a PEI in this block possibly raise an uncaught exception?
   * May conservatively return true even if the block
   * does not contain a PEI. When this is true it implies
   * that there is an implicit edge from this node to the
   * exit node in the FCFG. 
   * <p>
   * NOTE: This method says nothing about the presence/absence
   * of an explicit throw of an uncaught exception, and thus does
   * not rule out the block having an <em>explicit</em>
   * edge to the exit node caused by a throw of an uncaught exception.
   * 
   * @return <code>true</code> if the block might raise an 
   *         exception uncaught or <code>false</code> if it cannot
   */
  public final boolean  mayThrowUncaughtException() {  
    return (flags & IMPLICIT_EXIT_EDGE) != 0; 
  }

  /**
   * Is this block the first basic block in an exception handler?
   * NOTE: This doesn't seem particularly useful to me anymore,
   * since it is the same as asking if the block is an instanceof
   * and ExceptionHandlerBasicBlock. Perhaps we should phase this out?
   * 
   * @return <code>true</code> if the block is the first block in
   *         an exception hander or <code>false</code> if it is not
   */
  public final boolean isExceptionHandlerBasicBlock() {  
    return (flags & EXCEPTION_HANDLER) != 0; 
  }

  /**
   * Has the block been marked as being reachable from an
   * exception handler?
   * 
   * @return <code>true</code> if the block is reachable from 
   *         an exception hander or <code>false</code> if it is not
   */
  public final boolean isReachableFromExceptionHandler() {  
    return (flags & REACHABLE_FROM_EXCEPTION_HANDLER) != 0; 
  }


  /**
   * Compare the in scope exception handlers of two blocks. 
   *
   * @param other block to be compared to this.
   * @return <code>true</code> if this and other have equivalent in
   * scope exception handlers.
   */
  public final boolean isExceptionHandlerEquivalent (OPT_BasicBlock other) {
    // We might be able to do something,
    // by considering the (subset) of reachable exception handlers, 
    // but it would be awfully tricky to get it right, 
    // so just give up if they aren't equivalent.
    if (exceptionHandlers != other.exceptionHandlers) {
      // Even if not pointer ==, they still may be equivalent
      OPT_BasicBlockEnumeration e1 = getExceptionHandlers();
      OPT_BasicBlockEnumeration e2 = other.getExceptionHandlers();
      while (e1.hasMoreElements()) {
        if (!e2.hasMoreElements()) return false;
        if (e1.next() != e2.next()) return false;
      }
      if (e2.hasMoreElements()) return false;
    }
    return true;
  }

  /**
   * Has the block been marked as being unsafe to schedule
   * (due to the presence of VM_Magic)?
   * 
   * @return <code>true</code> if the block is marked as unsafe
   *         to schedule or <code>false</code> if it is not
   */
  public final boolean isUnsafeToSchedule() { 
    return (flags & UNSAFE_TO_SCHEDULE) != 0; 
  }

  /**
   * Has the block been marked as being infrequently executed?
   * NOTE: Only blocks that are truly icy cold should be marked
   * as infrequent.
   * 
   * @return <code>true</code> if the block is marked as infrequently
   *         executed or <code>false</code> if it is not
   */
  public final boolean getInfrequent() {
    return (flags & INFREQUENT) != 0; 
  }

  /**
   * Is the scratch flag set on the block?
   * 
   * @return <code>true</code> if the block scratch flag is set
   *         or <code>false</code> if it is not
   */
  public final boolean getScratchFlag() {  
    return (flags & SCRATCH  ) != 0; 
  }

  /**
   * Has the block been marked as landing pad?
   * 
   * @return <code>true</code> if the block is marked as landing pad
   *         or <code>false</code> if it is not
   */
  public final boolean getLandingPad() {
    return (flags & LANDING_PAD) != 0; 
  }

  /**
   * Mark the block as possibly raising an exception.
   */
  public final void setCanThrowExceptions() {  
    flags |= CAN_THROW_EXCEPTIONS;
  }
  
  /**
   * Mark the block as possibly raising an uncaught exception.
   */ 
  public final void setMayThrowUncaughtException() {
    flags |= IMPLICIT_EXIT_EDGE;
  }

  /**
   * Mark the block as the first block in an exception handler.
   */
  public final void setExceptionHandlerBasicBlock() {
    flags |= EXCEPTION_HANDLER;
  }

  /**
   * Mark the block as being reachable from an exception handler.
   */
  public final void setReachableFromExceptionHandler() {
    flags |= REACHABLE_FROM_EXCEPTION_HANDLER;
  }

  /**
   * Mark the block as being unsafe to schedule.
   */
  public final void setUnsafeToSchedule() {
    flags |= UNSAFE_TO_SCHEDULE;
  }

  /**
   * Mark the block as being infrequently executed.
   */
  public final void setInfrequent() { 
    flags |= INFREQUENT;
  }

  /**
   * Set the scratch flag
   */
  public final void setScratchFlag() {  
    flags |= SCRATCH;
  }

  /**
   * Mark the block as a landing pad for loop invariant code motion.
   */
  public final void setLandingPad() { 
    flags |= LANDING_PAD;
  }

  /**
   * Clear the may raise an exception property of the block
   */
  public final void clearCanThrowExceptions() {
    flags &= ~CAN_THROW_EXCEPTIONS;
  }

  /**
   * Clear the may raise uncaught exception property of the block
   */
  public final void clearMayThrowUncaughtException() {
    flags &= ~IMPLICIT_EXIT_EDGE;
  }

  /**
   * Clear the block is the first one in an exception handler 
   * property of the block.
   */
  public final void clearExceptionHandlerBasicBlock() {
    flags &= ~EXCEPTION_HANDLER;
  }    

  /**
   * Clear the block is reachable from an exception handler 
   * property of the block.
   */
  public final void clearReachableFromExceptionHandler() {
    flags &= ~REACHABLE_FROM_EXCEPTION_HANDLER;
  }    

  /**
   * Clear the unsafe to schedule property of the block
   */
  public final void clearUnsafeToSchedule() {
    flags &= ~UNSAFE_TO_SCHEDULE;
  }    

  /**
   * Clear the infrequently executed property of the block
   */
  public final void clearInfrequent() { 
    flags &= ~INFREQUENT;
  }    

  /**
   * Clear the scratch flag.
   */
  public final void clearScratchFlag() {
    flags &= ~SCRATCH;                   
  }    

  /**
   * Clear the landing pad property of the block
   */
  public final void clearLandingPad() { 
    flags &= ~LANDING_PAD;
  }    

  private final void setCanThrowExceptions(boolean v) {
    if (v) 
      setCanThrowExceptions();
    else
      clearCanThrowExceptions();
  }
  private final void setMayThrowUncaughtException(boolean v) {
    if (v) 
      setMayThrowUncaughtException();
    else   
      clearMayThrowUncaughtException();
  }
  private final void setIsExceptionHandlerBasicBlock(boolean v) {
    if (v) 
      setExceptionHandlerBasicBlock();
    else   
      clearExceptionHandlerBasicBlock();
  }
  private final void setUnsafeToSchedule(boolean v) {
    if (v) 
      setUnsafeToSchedule();
    else   
      clearUnsafeToSchedule();
  }
  final void setInfrequent(boolean v) {
    if (v) 
      setInfrequent();
    else   clearInfrequent();
  }


  /** 
   * Make a branch operand with the label instruction 
   * of this block.
   *
   * @return an OPT_BranchOperand holding this blocks label
   */
  public final OPT_BranchOperand makeJumpTarget() {
    return new OPT_BranchOperand(firstInstruction());
  }


  /** 
   * Make a GOTO instruction, branching to the first instruction of 
   * this basic block.
   *
   * @return a GOTO instruction that jumps to this block
   */
  public final OPT_Instruction makeGOTO() {
    return Goto.create(GOTO, makeJumpTarget());
  }


  /**
   * @return the first instruciton of the basic block (the label)
   */
  public final OPT_Instruction firstInstruction() {
    return start;
  }

  /**
   * @return the first 'real' instruction of the basic block; 
   *         null if the block is empty
   */
  public final OPT_Instruction firstRealInstruction() {
    if (isEmpty()) {
      return null;
    } else {
      return start.getNext();
    }
  }

  /**
   * @return the last instruction of the basic block (the BBEND)
   */
  public final OPT_Instruction lastInstruction() {
    return end;
  }


  /**
   * @return the last 'real' instruction of the basic block; 
   *         null if the block is empty
   */
  public final OPT_Instruction lastRealInstruction() {
    if (isEmpty()) {
      return null;
    } else {
      return end.getPrev();
    }
  }

  /**
   * Return the estimated relative execution frequency of the block
   */
  public final float getExecutionFrequency() {
    return freq;
  }

  /**
   * Set the estimated relative execution frequency of this block.
   */
  public final void setExecutionFrequency(float f) {
    freq = f;
  }

  /**
   * Scale the estimated relative execution frequency of this block.
   */
  public final void scaleExecutionFrequency(float f) {
    freq *= f;
  }

  /**
   * Augment the estimated relative execution frequency of this block.
   */
  public final void augmentExecutionFrequency(float f) {
    freq += f;
  }

  /**
   * Is this block the exit basic block?
   * 
   * @return <code>true</code> if this block is the EXIT or
   *         <code>false</code> if it is not
   */
  public final boolean isExit() {
    return start == null;
  }

  /**
   * Forward enumeration of all the instruction in the block.
   * @return a forward enumeration of the block's instructons.
   */
  public final OPT_InstructionEnumeration forwardInstrEnumerator() {
    return OPT_IREnumeration.forwardIntraBlockIE(firstInstruction(), 
                                                 lastInstruction());
  }

  /**
   * Reverse enumeration of all the instruction in the block.
   * @return a reverse enumeration of the block's instructons.
   */
  public final OPT_InstructionEnumeration reverseInstrEnumerator() {
    return OPT_IREnumeration.reverseIntraBlockIE(lastInstruction(), 
                                                 firstInstruction());
  }


  /**
   * Forward enumeration of all the real instruction in the block.
   * @return a forward enumeration of the block's real instructons.
   */
  public final OPT_InstructionEnumeration forwardRealInstrEnumerator() {
    return OPT_IREnumeration.forwardIntraBlockIE(firstRealInstruction(), 
                                                 lastRealInstruction());
  }

  /**
   * Reverse enumeration of all the real instruction in the block.
   * @return a reverse enumeration of the block's real instructons.
   */
  public final OPT_InstructionEnumeration reverseRealInstrEnumerator() {
    return OPT_IREnumeration.reverseIntraBlockIE(lastRealInstruction(), 
                                                 firstRealInstruction());
  }

  /**
    * How many real instructions does the block contain?
    * WARNING: This method actually counts the instructions,
    * thus it has a linear time complexity!
    *
    * @return the number of "real" instructions in this basic block.
    */
  public int getNumberOfRealInstructions() {
    int count = 0;
    for (OPT_InstructionEnumeration e = forwardRealInstrEnumerator();
         e.hasMoreElements(); e.next()) {
      count++;
    }
        
    return count;
  }

  /**
   * Does this basic block end in a GOTO instruction? 
   *
   * @return <code>true</code> if the block ends in a GOTO
   *         or <code>false</code> if it does not
   */
  public final boolean hasGoto() {
    if (isEmpty()) return false;
    return Goto.conforms(lastRealInstruction())
      || MIR_Branch.conforms(lastRealInstruction());
  }

  /**
   * Does this basic block end in a RETURN instruction? 
   *
   * @return <code>true</code> if the block ends in a RETURN
   *         or <code>false</code> if it does not
   */
  public final boolean hasReturn() {
    if (isEmpty()) return false;
    return Return.conforms(lastRealInstruction())
      || MIR_Return.conforms(lastRealInstruction());
  }

  /**
   * Does this basic block end in a SWITCH instruction? 
   *
   * @return <code>true</code> if the block ends in a SWITCH
   *         or <code>false</code> if it does not
   */
  public final boolean hasSwitch() {
    if (isEmpty()) return false;
    OPT_Instruction s = lastRealInstruction();
    return TableSwitch.conforms(s) || 
      LowTableSwitch.conforms(s) ||
      LookupSwitch.conforms(s);
  }

  /**
   * Does this basic block contain an explicit athrow instruction?  
   *
   * @return <code>true</code> if the block ends in an explicit Athrow
   *         instruction or <code>false</code> if it does not 
   */
  public final boolean hasAthrowInst() {
    if (isEmpty()) return false;
    OPT_Instruction s = lastRealInstruction();

    //-#if RVM_FOR_IA32
    if (s.operator == ADVISE_ESP) {
      s = s.getPrev();
    }
    //-#endif

    if (Athrow.conforms(s)) {
      return true;
    } 
    OPT_MethodOperand mop = null;
    if (MIR_Call.conforms(s)) {
      mop = MIR_Call.getMethod(s);
    } else if (Call.conforms(s)) {
      mop = Call.getMethod(s);
    }
    return mop != null && mop.getTarget() == VM_Entrypoints.athrowMethod;
  }

  /**
   * Does this basic block end in an explicit trap?  
   *
   * @return <code>true</code> if the block ends in a an explicit trap
   *         or <code>false</code> if it does not
   */
  public final boolean hasTrap() {
    if (isEmpty()) return false;
    OPT_Instruction s = lastRealInstruction();

    return Trap.conforms(s);
  }

  /**
   * Does this basic block end in a call that never returns?
   * (For example, a call to athrow())
   *
   * @return <code>true</code> if the block ends in a call that never
   *         returns or <code>false</code> if it does not
   */
  public final boolean hasNonReturningCall() {
    if (isEmpty()) return false;
    OPT_Instruction s = lastRealInstruction();

    if (Call.conforms(s)) {
      OPT_MethodOperand methodOp = Call.getMethod(s);
      if (methodOp != null && methodOp.isNonReturningCall())
        return true;
    }

    return false;
  }


  //-#if RVM_WITH_OSR
  public final boolean hasNonReturningOsr() {
    if (isEmpty()) return false;
    OPT_Instruction s = lastRealInstruction();
    if (OsrPoint.conforms(s)) 
      return true;
    else 
      return false;
  }
  //-#endif

  /**
   * If there is a fallthrough FCFG successor of this node 
   * return it.
   * 
   * @return the fall-through successor of this node or 
   *         <code>null</code> if none exists
   */
  public final OPT_BasicBlock getFallThroughBlock() {
    if (hasGoto()) return null;
    if (hasSwitch()) return null;
    if (hasReturn()) return null;
    if (hasAthrowInst()) return null;
    if (hasTrap()) return null;
    if (hasNonReturningCall()) return null;
    
    //-#if RVM_WITH_OSR
    if (hasNonReturningOsr()) return null;
    //-#endif

    return nextBasicBlockInCodeOrder();
  }

  /**
   * @return the FCFG successor if all conditional branches in this are
   * <em> not </em> taken
   */
  public final OPT_BasicBlock getNotTakenNextBlock() {
    OPT_Instruction last = lastRealInstruction();
    if (Goto.conforms(last) || MIR_Branch.conforms(last)) {
      return last.getBranchTarget();
    } else {
      return nextBasicBlockInCodeOrder();
    }
  }


  /**
   * Replace fall through in this block by an explicit goto
   */
  public void killFallThrough () {
    OPT_BasicBlock fallThrough = getFallThroughBlock();
    if (fallThrough != null) {
      lastInstruction().insertBefore
        (Goto.create(GOTO,fallThrough.makeJumpTarget()));
    }
  }

  
  /**
   * Prepend instruction to this basic block by inserting it right after
   * the LABEL instruction in the instruction list.
   * 
   * @param i instruction to append
   */
  public final void prependInstruction(OPT_Instruction i) {
    start.insertAfter(i);
  }

  /**
   * Prepend instruction to this basic block but respect the prologue
   * instruction, which must come first.
   * 
   * @param i instruction to append 
   */
  public final void prependInstructionRespectingPrologue(OPT_Instruction i) {
    OPT_Instruction first = firstRealInstruction();
    if ((first != null) && 
        (first.getOpcode() == IR_PROLOGUE_opcode)) {
      first.insertAfter(i);
    }
    else {
      start.insertAfter(i);
    }
  }

  /**
   * Append instruction to this basic block by inserting it right before
   * the BBEND instruction in the instruction list.
   * 
   * @param i instruction to append
   */
  public final void appendInstruction(OPT_Instruction i) {
    end.insertBefore(i);
  }

  /**
   * Append instruction to this basic block by inserting it right before
   * the BBEND instruction in the instruction list. However, if
   * the basic block ends in a sequence of branch instructions, insert
   * the instruction before the first branch instruction.
   *
   * @param i instruction to append
   */
  public final void appendInstructionRespectingTerminalBranch(OPT_Instruction i){
    OPT_Instruction s = end;
    while (s.getPrev().operator().isBranch()) s = s.getPrev();
    s.insertBefore(i);
  }

  /**
   * Append instruction to this basic block by inserting it right before
   * the BBEND instruction in the instruction list. However, if
   * the basic block ends in a sequence of branch instructions, insert
   * the instruction before the first branch instruction. If the block
   * ends in a PEI, insert the instruction before the PEI.
   * This function is meant to be used when the block has
   * been {@link #unfactor(OPT_IR) unfactored} and thus is in CFG form.
   *
   * @param i instruction to append
   */
  public final void appendInstructionRespectingTerminalBranchOrPEI(OPT_Instruction i) {
    OPT_Instruction s = end;
    while (s.getPrev().operator().isBranch() || 
           (s.getPrev().isPEI() && 
            getApplicableExceptionalOut(s.getPrev()).hasMoreElements())) {
      s = s.getPrev();
    }
    s.insertBefore(i);
  }
  
  /**
   * Return an enumeration of the branch instructions in this
   * basic block.
   * @return an forward enumeration of this blocks branch instruction
   */
  public final OPT_InstructionEnumeration enumerateBranchInstructions() {
    OPT_Instruction start = firstBranchInstruction();
    OPT_Instruction end = (start == null) ? null : lastRealInstruction();
    return OPT_IREnumeration.forwardIntraBlockIE(start, end); 
  }

  /**
   * Return the first branch instruction in the block.
   * 
   * @return the first branch instruction in the block
   *         or <code>null</code> if there are none.
   */
  public final OPT_Instruction firstBranchInstruction() {
    OPT_Instruction s = lastRealInstruction();
    if (s == null) return null;
    if (!s.operator().isBranch()) return null;
    while (s.getPrev().isBranch()) {
      s = s.getPrev(); 
    }
    return s;
  }

  /**
   * Return the next basic block in with respect to the current 
   * code linearization order.
   *
   * @return the next basic block in the code order or 
   *         <code>null</code> if no such block exists
   */
  public final OPT_BasicBlock nextBasicBlockInCodeOrder() {
    return (OPT_BasicBlock)getNext();
  }

  /**
   * Return the previous basic block in with respect to the current 
   * code linearization order.
   *
   * @return the previous basic block in the code order or 
   *         <code>null</code> if no such block exists
   */
  public final OPT_BasicBlock prevBasicBlockInCodeOrder() {
    return (OPT_BasicBlock)getPrev();
  }

  /**
   * Returns true if the block contains no real instructions
   *
   * @return <code>true</code> if the block contains no real instructions
   *         or <code>false</code> if it does.
   */
  public final boolean isEmpty() {
    return start.getNext() == end;
  }

  /**
   * Are there any exceptional out edges that are applicable
   * to the given instruction (assumed to be in instruction in 'this')
   *
   * @param instr the instruction in question
   * @return true or false;
   */
  public final boolean hasApplicableExceptionalOut(OPT_Instruction instr) {
    return getApplicableExceptionalOut(instr).hasMoreElements();
  }

  /**
   * An enumeration of the subset of exceptional out edges that are applicable
   * to the given instruction (assumed to be in instruction in 'this')
   *
   * @param instr the instruction in question
   * @return an enumeration of the exceptional out edges applicable to instr
   */
  public final OPT_BasicBlockEnumeration getApplicableExceptionalOut
                                         (OPT_Instruction instr) {
    if (instr.isPEI()) {
      int numPossible = getNumberOfExceptionalOut();
      if (numPossible == 0) return OPT_BasicBlockEnumeration.Empty;
      ComputedBBEnum e = new ComputedBBEnum(numPossible);
      switch (instr.getOpcode()) {
      case ATHROW_opcode:
        VM_TypeReference type = Athrow.getValue(instr).getType();
        addTargets(e, type);
        break;
      case CHECKCAST_opcode:
      case CHECKCAST_NOTNULL_opcode:
      case CHECKCAST_UNRESOLVED_opcode:
        addTargets(e, VM_TypeReference.JavaLangClassCastException);
        break;
      case NULL_CHECK_opcode:
        addTargets(e, VM_TypeReference.JavaLangNullPointerException);
        break;
      case BOUNDS_CHECK_opcode:
        addTargets(e, VM_TypeReference.JavaLangArrayIndexOutOfBoundsException);
        break;
      case INT_ZERO_CHECK_opcode:
      case LONG_ZERO_CHECK_opcode:
        addTargets(e, VM_TypeReference.JavaLangArithmeticException);
        break;
      case OBJARRAY_STORE_CHECK_opcode:
        addTargets(e, VM_TypeReference.JavaLangArrayStoreException);
        break;
      default:
        // Not an operator for which we have a more refined notion of 
        // its exception semantics, so assume that any reachable exception
        // handler might be a potential target of this PEI
        return getExceptionalOut();
      }
      return e;
    } else {
      return OPT_BasicBlockEnumeration.Empty;
    }
  }

  // add all handler blocks in this's out set that might possibly catch
  // an exception of static type throwException 
  // (may dynamically be any subtype of thrownException)
  private void addTargets(ComputedBBEnum e, VM_TypeReference thrownException) {
    for (OPT_SpaceEffGraphEdge ed = _outEdgeStart; ed != null; ed = ed.getNextOut()) {
      OPT_BasicBlock bb = (OPT_BasicBlock)ed.toNode();
      if (bb.isExceptionHandlerBasicBlock()) {
        OPT_ExceptionHandlerBasicBlock eblock = 
          (OPT_ExceptionHandlerBasicBlock)bb;
        if (eblock.mayCatchException(thrownException) != NO) {
          e.addPossiblyDuplicateElement(eblock);
        }
      }
    }
  }


  /**
   * An enumeration of the in scope exception handlers for this basic block.
   * Note that this may be a superset of the exception handlers 
   * actually included in the out set of this basic block.
   * 
   * @return an enumeration of all inscope exception handlers
   */
  public final OPT_BasicBlockEnumeration getExceptionHandlers () {
    if (exceptionHandlers == null) {
      return OPT_BasicBlockEnumeration.Empty;
    } else {
      return exceptionHandlers.enumerator();
    }
  }

  /**
   * Is this block in the scope of at least exception handler?
   * 
   * @return <code>true</code> if there is at least one in scope 
   *         exception handler, <code>false</code> otherwise
   */
  public final boolean hasExceptionHandlers() {
    return exceptionHandlers != null;
  }

  /**
   * Returns an Enumeration of the in scope exception handlers that are 
   * actually reachable from this basic block in the order that they are 
   * applicable (which is semantically meaningful). 
   * IE, this is those blocks in getExceptionalOut ordered as 
   * in getExceptionHandlers.
   *
   * @return an enumeration of the reachable exception handlers
   */
  public final OPT_BasicBlockEnumeration getReachableExceptionHandlers() {
    if (hasExceptionHandlers()) {
      int count = 0;
      for (OPT_BasicBlockEnumeration inScope = getExceptionHandlers(); 
           inScope.hasMoreElements(); 
           inScope.next()) {
          count++;
      }

      ComputedBBEnum ans = new ComputedBBEnum( count );
   
      for (OPT_BasicBlockEnumeration inScope = getExceptionHandlers(); 
           inScope.hasMoreElements(); ) {
        OPT_BasicBlock cand = inScope.next();
        if (pointsOut(cand)) ans.addPossiblyDuplicateElement(cand);
      }
      return ans;
    } else {
      return OPT_BasicBlockEnumeration.Empty;
    }
  }

  /** 
   * Delete all the non-exceptional out edges.
   * A useful primitive routine for some CFG manipulations.
   */
  public final void deleteNormalOut() {
    for (OPT_SpaceEffGraphEdge e = _outEdgeStart; e!=null; e = e.getNextOut()) {
      OPT_BasicBlock out = (OPT_BasicBlock)e.toNode(); 
      if (!out.isExceptionHandlerBasicBlock())
        deleteOut(e);
    }
  }

  /**
   * Recompute the normal out edges of 'this' based on the
   * semantics of the branch instructions in the block.
   *
   * WARNING: Use this method with caution.  It does not update the
   * CFG edges correctly if the method contains certain instructions
   * such as throws and returns.  Incorrect liveness info and GC maps
   * result, causing crashes during GC.  CMVC Defect 171189 
   *
   */
  public final void recomputeNormalOut(OPT_IR ir) {
    deleteNormalOut();
    for (OPT_InstructionEnumeration e = enumerateBranchInstructions();
         e.hasMoreElements(); ) {
      OPT_Instruction branch = e.next();
      OPT_BasicBlockEnumeration targets = branch.getBranchTargets();
      while (targets.hasMoreElements()) {
        insertOut(targets.next());
      }
    }
    // Check for fallthrough edge
    OPT_BasicBlock fallThrough = getFallThroughBlock();
    if (fallThrough != null) {
      insertOut(fallThrough);
    }

    // Check special cases that require edge to exit
    if (hasReturn()) {
      insertOut(ir.cfg.exit());
    } else if (hasAthrowInst() ||
             hasNonReturningCall()) {
      if (mayThrowUncaughtException())
        insertOut(ir.cfg.exit());
    }
    //-#if RVM_WITH_OSR
    else if (hasNonReturningOsr()) {
      insertOut(ir.cfg.exit());
    }
    //-#endif
  }
        
  /**
   * Ensure that the target instruction is the only real instruction
   * in its basic block and that it has exactly one successor and 
   * one predecessor basic blocks that are linked to it by fall through edges.
   * 
   * @param target the OPT_Instruction that must be placed in its own BB
   * @param ir the containing OPT_IR object
   * @return the OPT_BasicBlock containing target
   */
  public final OPT_BasicBlock segregateInstruction(OPT_Instruction target, 
                                                   OPT_IR ir) {
    if (OPT_IR.PARANOID) VM._assert(this == target.getBasicBlock());
    
    OPT_BasicBlock BB1 = splitNodeAt(target.getPrev(), ir);
    this.insertOut(BB1);
    ir.cfg.linkInCodeOrder(this, BB1);
    OPT_BasicBlock BB2 = BB1.splitNodeAt(target, ir);
    BB1.insertOut(BB2);
    ir.cfg.linkInCodeOrder(BB1, BB2);
    return BB1;
  }


  /**
   * Splits a node at an instruction point.  All the instructions up to and 
   * including the argument instruction remain in the original basic block.
   * All instructions in this basic block but after s in the instruction list
   * are moved to the new basic block.
   * <ul>
   * <li> does not establish control flow edge out of B1 -- caller responsibility
   * <li> does not establish control flow edge into B2 -- caller responsibility
   * <li> Leaves a break in the code order -- caller responsibility 
   *      to patch back together. If the original code order was 
   *      BB_before -> BB1 -> BB_after 
   *      then the new code order is 
   *      BB_before -> BB1 <break> BB2 -> BB_after.
   *      Note that if BB_after == null, splitNodeAt does handle 
   *      updating ir.cfg._lastNode to point to BB2.
   * </ul>
   *
   * @param last_instr_BB1 the instr that is to become the last instruction 
   *                       in this basic block
   * @param ir             the containing IR object
   * @return the newly created basic block which is the successor to this 
   */
  public final OPT_BasicBlock splitNodeAt(OPT_Instruction last_instr_BB1, 
                                          OPT_IR ir) {
    if (OPT_IR.PARANOID) VM._assert(this == last_instr_BB1.getBasicBlock());

    OPT_BasicBlock BB1 = this;
    OPT_BasicBlock BB2 = new OPT_BasicBlock(last_instr_BB1.bcIndex, 
                                            last_instr_BB1.position, 
                                            ir.cfg);
    OPT_BasicBlock BB3 = (OPT_BasicBlock)BB1.next;

    // move last_instr_BB1 ... BB1.end.prev into BB2
    if (last_instr_BB1 == BB1.end || last_instr_BB1.getNext() == BB1.end) {
      // there are no such instructions; nothing to do
    } else {
      OPT_Instruction first_instr_BB2 = last_instr_BB1.getNext();
      OPT_Instruction last_instr_BB2 = BB1.end.getPrev();
      last_instr_BB1.linkWithNext(BB1.end);
      BB2.start.linkWithNext(first_instr_BB2);
      last_instr_BB2.linkWithNext(BB2.end);
    }

    // Update code ordering (see header comment above)
    if (BB3 == null) {
      ir.cfg.addLastInCodeOrder(BB2);
      if (OPT_IR.PARANOID) VM._assert(BB1.next == BB2 && BB2.prev == BB1);
      ir.cfg.breakCodeOrder(BB1, BB2);
    } else {
      ir.cfg.breakCodeOrder(BB1, BB3);
      ir.cfg.linkInCodeOrder(BB2, BB3);
    }      

    // Update control flow graph to transfer BB1's out edges to BB2.
    // But it's not as simple as that.  Any edge that is present to represent
    // potential exception behavior (out.isExceptionHandlerBasicBlock == true)
    // needs to be both left in BB1's out set and transfered to BB2's out set
    // Note this may be overly conservative, but will be correct.
    for (OPT_BasicBlockEnumeration e = getOut(); e.hasMoreElements();) {
        OPT_BasicBlock out = e.next();
        BB2.insertOut(out);
    }
    
    // Initialize the rest of BB2's exception related state to match BB1
    BB2.exceptionHandlers = BB1.exceptionHandlers;
    BB2.setCanThrowExceptions(BB1.canThrowExceptions());
    BB2.setMayThrowUncaughtException(BB1.mayThrowUncaughtException());
    BB2.setUnsafeToSchedule(BB1.isUnsafeToSchedule());
    BB2.setExecutionFrequency(BB1.getExecutionFrequency());

    BB1.deleteNormalOut();
    
    return BB2;
  }

  /**
   * Splits a node at an instruction point. All the instructions up to and 
   * including the argument instruction remain in the original basic block 
   * all instructions in this basic block but after s in the instruction list
   * are moved to the new basic block. The blocks are linked together in
   * the FCFG and the code order.
   * The key difference between this function and 
   * {@link #splitNodeAt(OPT_Instruction, OPT_IR)} is that it does
   * establish the FCFG edges and code order such that B1 falls into B2.
   *
   * @param last_instr_BB1 the instr that is to become 
   *                       the last instruction in this basic block
   * @param ir             the containing IR object
   * @return the newly created basic block which is the successor to this 
   */
  public final OPT_BasicBlock splitNodeWithLinksAt(OPT_Instruction last_instr_BB1,
                                                   OPT_IR ir) {
    
    if (OPT_IR.PARANOID) VM._assert(this == last_instr_BB1.getBasicBlock());
    
    OPT_BasicBlock BB2 = splitNodeAt(last_instr_BB1, ir);
    this.insertOut(BB2);
    ir.cfg.linkInCodeOrder(this, BB2);
    return BB2;
  }


  /**
   * Copies a basic block. The copy differs from the original as follows:
   * <ul>
   * <li> the copy's number and labels are new, and will be unique in the
   *      containing IR
   * <li> the copy is NOT linked into the IR's bblist
   * <li> the copy does NOT appear in the IR's cfg.
   * </ul>
   * The copy
   * <ul>
   * <li> inherits the original block's exception handlers
   * <li> inherits the original block's bytecode index
   * <li> has NEW copies of each instruction.
   *
   * @param ir the containing IR
   * @return the copy
   */
  public final OPT_BasicBlock copyWithoutLinks(OPT_IR ir) {
    // create a new block with the same bytecode index and exception handlers
    int bytecodeIndex = -1;
    OPT_InlineSequence position = null;

    // Make the label instruction of the new block have the same
    // bc info as the label of the original block.
    if (firstInstruction() != null) {
      bytecodeIndex = firstInstruction().bcIndex;
    }
    
    OPT_BasicBlock newBlock = createSubBlock(bytecodeIndex,ir, 1f);
    
    // copy each instruction from the original block.
    for (OPT_Instruction s=firstInstruction().getNext(); 
         s != lastInstruction(); 
         s=s.getNext())
      newBlock.appendInstruction(s.copyWithoutLinks());

    // copy other properties of the block.
    newBlock.flags = flags;
    
    return newBlock;
  }

  /**
   * For each basic block b which is a "normal" successor of this,
   * make a copy of b, and set up the CFG so that this block has
   * normal out edges to the copies.
   *
   * WARNING: Use this method with caution.  See comment on 
   * BasicBlock.recomputeNormalOut() 
   *
   * @param ir the containing IR
   */
  public final void replicateNormalOut(OPT_IR ir) {
    // for each normal out successor (b) of 'this' ....
    for (OPT_BasicBlockEnumeration e = getNormalOut(); 
         e.hasMoreElements();) {
      OPT_BasicBlock b = e.next();
      replicateThisOut (ir, b);
    }
  }


  /**
   * For basic block b which has to be a "normal" successor of this,
   * make a copy of b, and set up the CFG so that this block has
   * normal out edges to the copy.
   *
   * WARNING: Use this method with caution.  See comment on 
   * BasicBlock.recomputeNormalOut() 
   *
   * @param ir the governing IR
   * @param b the block to replicate
   */
  public final OPT_BasicBlock replicateThisOut (OPT_IR ir, OPT_BasicBlock b) {
    return replicateThisOut (ir, b, this);
  }
  
  /**
   * For basic block b which has to be a "normal" successor of this,
   * make a copy of b, and set up the CFG so that this block has
   * normal out edges to the copy.
   *
   * WARNING: Use this method with caution.  See comment on 
   * BasicBlock.recomputeNormalOut() 
   *
   * @param ir the governing IR
   * @param b the block to replicate
   * @param pred code order predecessor for new block
   */
  public final OPT_BasicBlock replicateThisOut (OPT_IR ir, OPT_BasicBlock b,
                                         OPT_BasicBlock pred) {
    // don't replicate the exit node
    if (b.isExit()) return null;
      
    // 1. create the replicated block (bCopy)
    OPT_BasicBlock bCopy = b.copyWithoutLinks(ir);

    // 2. If b has a fall-through edge, insert the appropriate GOTO at
    // the end of bCopy
    OPT_BasicBlock bFallThrough = b.getFallThroughBlock();
    if (bFallThrough != null) {
      OPT_Instruction g = Goto.create(GOTO,bFallThrough.makeJumpTarget());
      bCopy.appendInstruction(g);
    }
    bCopy.recomputeNormalOut(ir);
    
    // 3. update the branch instructions in 'this' to point to bCopy
    redirectOuts (b, bCopy,ir);
    
    // 4. link the new basic into the code order, immediately following pred
    pred.killFallThrough();
    OPT_BasicBlock next = pred.nextBasicBlockInCodeOrder();
    if (next != null) {
      ir.cfg.breakCodeOrder(pred,next);
      ir.cfg.linkInCodeOrder(bCopy,next);
    }
    ir.cfg.linkInCodeOrder(pred,bCopy);
    
    return bCopy;
  }
  
  
  /**
   * Move me behind `pred'.
   *
   * @param pred my desired code order predecessor
   * @param ir the governing IR
   */
  public void moveBehind (OPT_BasicBlock pred, OPT_IR ir) {
    killFallThrough();
    pred.killFallThrough();
    OPT_BasicBlock thisPred = prevBasicBlockInCodeOrder();
    OPT_BasicBlock thisSucc = nextBasicBlockInCodeOrder();
    if (thisPred != null) {
      thisPred.killFallThrough();
      ir.cfg.breakCodeOrder(thisPred, this);
    } 
    if (thisSucc != null) ir.cfg.breakCodeOrder(this, thisSucc);
    
    if (thisPred != null && thisSucc != null)
      ir.cfg.linkInCodeOrder (thisPred, thisSucc);
    
    thisPred = pred;
    thisSucc = pred.nextBasicBlockInCodeOrder();
    
    if (thisSucc != null) {
      ir.cfg.breakCodeOrder (thisPred, thisSucc);
      ir.cfg.linkInCodeOrder (this, thisSucc);
    }
    ir.cfg.linkInCodeOrder (thisPred, this);
  }

  
  /**
   * Change all branches from this to b to branches that go to bCopy instead.
   * This method also handles this.fallThrough, so `this' should still be in
   * the code order when this method is called.
   *
   * WARNING: Use this method with caution.  See comment on 
   * BasicBlock.recomputeNormalOut() 
   *
   * @param b     the original target
   * @param bCopy the future target   
   */
  public final void redirectOuts (OPT_BasicBlock b, OPT_BasicBlock bCopy, 
                           OPT_IR ir) {
    OPT_BranchOperand copyTarget = bCopy.makeJumpTarget();
    OPT_BranchOperand bTarget = b.makeJumpTarget();
    // 1. update the branch instructions in 'this' to point to bCopy
    for (OPT_InstructionEnumeration ie = enumerateBranchInstructions();
         ie.hasMoreElements(); ) {
      OPT_Instruction s = ie.next();
      s.replaceSimilarOperands(bTarget, copyTarget);
    }

    // 2. if this falls through to b, make it jump to bCopy
    if (getFallThroughBlock() == b) {
      OPT_Instruction g = Goto.create(GOTO, copyTarget); //no copy needed.
      appendInstruction(g);
    }
      
    // 3. recompute normal control flow edges.
    recomputeNormalOut(ir);
  }

  /*
   * TODO: work on eliminating this method by converting callers to
   *       three argument form
   */
  public final OPT_BasicBlock createSubBlock(int bc, OPT_IR ir) {
    return createSubBlock(bc, ir, 1f);
  }

  /**
   * Creates a new basic block that inherits its exception handling, 
   * etc from 'this'. This method is intended to be used in conjunction 
   * with splitNodeAt when splitting instructions in one original block 
   * into a sequence of sublocks
   *
   * @param bc the bytecode index to start the block
   * @param ir the containing IR
   * @param wf the fraction of this's execution frequency that should be 
   *           inherited by the new block. In the range [0.0, 1.0]
   * @return the new empty BBlock
   */
  public final OPT_BasicBlock createSubBlock(int bc, OPT_IR ir, float wf) {
    // For now, give the basic block the same inline context as the
    // original block.  
    // TODO: This won't always work. (In fact, in the presence of inlining
    //       it will be wrong quite often). --dave
    //       We really have to pass the position in if we except this to work.
    OPT_BasicBlock temp = 
      new OPT_BasicBlock(bc, firstInstruction().position, ir.cfg);

    // Conservatively transfer all exception handling behavior of the 
    // parent block  (this) to the new child block (temp)
    temp.exceptionHandlers = exceptionHandlers;
    temp.setCanThrowExceptions(canThrowExceptions());
    temp.setMayThrowUncaughtException(mayThrowUncaughtException());
    temp.setUnsafeToSchedule(isUnsafeToSchedule());
    temp.setExecutionFrequency(getExecutionFrequency() * wf);
    for (OPT_BasicBlockEnumeration e = getOut(); e.hasMoreElements(); ) {
      OPT_BasicBlock out = e.next();
      if (out.isExceptionHandlerBasicBlock()) {
        temp.insertOut(out);
      }
    }

    return temp;
  }


  /**
   * If this block has a single non-Exception successor in the CFG
   * then we may be able to merge the two blocks together.
   * In order for this to be legal, it must be the case that:
   *  (1) The successor block has no other in edges than the one from this.
   *  (2) Both blocks have the same exception handlers.
   * Merging the blocks is always desirable when
   *  (a) the successor block is the next block in code order
   *  (b) the successor block is not the next block in the code order,
   *      but ends in an unconditional branch (ie it doesn't have a
   *      fallthrough successor in the code order that we could be screwing up).
   *
   * @param ir the IR object containing the basic block to be merged
   * @return <code>true</code> if  the block was merged or
   *         <code>false</code> otherwise
   */
  public final boolean mergeFallThrough(OPT_IR ir) {
    if (getNumberOfNormalOut() != 1) return false; // this has other out edges.
    OPT_BasicBlock succBB = (OPT_BasicBlock)next;
    if (succBB == null || !pointsOut(succBB)) { 
      // get the successor from the CFG rather than the code order (case (b))
      succBB = getNormalOut().next();
      if (succBB.isExit()) return false;
      if (succBB.lastRealInstruction() == null ||
          !succBB.lastRealInstruction().isUnconditionalBranch())
        return false;
    }

    if (succBB.isExceptionHandlerBasicBlock()) return false; // must preserve special exception info!
    if (succBB.getNumberOfIn() != 1) return false; // succBB has other in edges

    // Different in scope Exception handlers?
    if (!isExceptionHandlerEquivalent (succBB)) return false;
    
    // There may be a redundant goto at the end of this -- remove it.
    // There may also be redundant conditional branches (also to succBB).
    // Remove them as well.
    // Branch instructions to blocks other than succBB are errors.
    if (VM.VerifyAssertions) {
      for (OPT_InstructionEnumeration e = enumerateBranchInstructions();
           e.hasMoreElements(); ) {
        OPT_BasicBlockEnumeration targets = e.next().getBranchTargets();
        while (targets.hasMoreElements()) {
          VM._assert(targets.next() == succBB);
        }
      }
    }
    OPT_Instruction s = this.end.getPrev();
    while (s.isBranch()) {
      s = s.remove();
    }

    // splice together the instruction lists of the two basic blocks into
    // a single list and update this's BBEND info
    this.end.getPrev().linkWithNext(succBB.start.getNext());
    end = succBB.end;
    BBend.setBlock(end, new OPT_BasicBlockOperand(this));


    // Add succBB's CFG sucessors to this's CFG out edges 
    for (OutEdgeEnum e = succBB.getOut(); e.hasMoreElements(); ) {
      OPT_BasicBlock out = e.next();
      this.insertOut(out);
    }
    

    // Blow away sucBB.
    ir.cfg.removeFromCFGAndCodeOrder(succBB);

    // Merge misc BB state
    setCanThrowExceptions(canThrowExceptions() || succBB.canThrowExceptions());
    setMayThrowUncaughtException(mayThrowUncaughtException() || 
                                 succBB.mayThrowUncaughtException());
    setUnsafeToSchedule(isUnsafeToSchedule() || succBB.isUnsafeToSchedule());
    if (succBB.getInfrequent()) setInfrequent();

    return true;
  }
  


  /**
   * Convert a block in the FCFG into the equivalent set of 
   * CFG blocks by splitting the original block into sub-blocks
   * at each PEI that reaches at least one exception handelr.
   * NOTE: This is sufficient for intraprocedural analysis, since the
   * only program point at which the "wrong" answers will 
   * be computed is the exit node, but is not good enough for
   * interprocedural analyses.  To do an interprocedural analysis,
   * either the analysis needs to deal with the FCFG or all nodes
   * that modify globally visible state must be unfactored.
   * @see OPT_IR#unfactor
   * @param ir the containing IR object
   */
  final void unfactor(OPT_IR ir) {
    for (OPT_InstructionEnumeration e = forwardRealInstrEnumerator();
         e.hasMoreElements();){
      OPT_Instruction s = e.next();
      Enumeration expOuts = getApplicableExceptionalOut(s);
      if (expOuts.hasMoreElements() && e.hasMoreElements()) {
        OPT_BasicBlock next = splitNodeWithLinksAt(s, ir);
        next.unfactor(ir);
        pruneExceptionalOut(ir);
        return;
      }
    }
  }
  /**
   * Prune away exceptional out edges that are not reachable given this
   * block's instructions.
   */
  final void pruneExceptionalOut(OPT_IR ir) {
    int n = getNumberOfExceptionalOut();
    if (n > 0) {
      ComputedBBEnum handlers = new ComputedBBEnum(n);
      OPT_InstructionEnumeration e = forwardRealInstrEnumerator();
      while (e.hasMoreElements()) {
        OPT_Instruction x = e.next();
        OPT_BasicBlockEnumeration bbs = getApplicableExceptionalOut(x);
        while (bbs.hasMoreElements()) {
          OPT_BasicBlock bb = bbs.next();
          handlers.addPossiblyDuplicateElement(bb);
        }
      }
      
      deleteExceptionalOut();

      for(int i = 0; handlers.hasMoreElements(); i++) {
        OPT_ExceptionHandlerBasicBlock b = 
          (OPT_ExceptionHandlerBasicBlock) handlers.next();
        insertOut(b);
      }
    }
    
    // Since any edge to an exception handler is an "exceptional" edge,
    // the previous procedure has thrown away any "normal" CFG edges to
    // exception handlers.  So, recompute normal edges to recover them.
    recomputeNormalOut(ir);

  }
  // helper function for unfactor
  private final void deleteExceptionalOut() {
    for (OPT_SpaceEffGraphEdge e = _outEdgeStart; e!=null; e = e.getNextOut()) {
      OPT_BasicBlock out = (OPT_BasicBlock)e.toNode(); 
      if (out.isExceptionHandlerBasicBlock())
        deleteOut(e);
    }
  }
    

  /**
   * An enumeration of the FCFG in nodes.
   *
   * @return an enumeration of the in nodes
   */
  public final OPT_BasicBlockEnumeration getIn() {
    return new InEdgeEnum(this);
  }

  /**
   * An enumeration of the FCFG in nodes.
   *
   * @return an enumeration of the in nodes
   */
  public final Enumeration getInNodes() {
    return new InEdgeEnum(this);
  }

  /**
   * Is there an in edge from the given basic block?
   *
   * @param bb basic block in question
   * @return <code>true</code> if an in edge exists from bb
   *         <code>false</code> otherwise
   */
  public final boolean isIn(OPT_BasicBlock bb) {
    InEdgeEnum iee = new InEdgeEnum(this);
    for (; iee.hasMoreElements(); )
      if (iee.next() == bb)
        return true;
    return false;
  }

  /**
   * An enumeration of the FCFG out nodes.
   *
   * @return an enumeration of the out nodes
   */
  public final /*OPT_BasicBlockEnumeration*/ OutEdgeEnum getOut () {
    return new OutEdgeEnum(this);
  }

  /**
   * An enumeration of the FCFG out nodes.
   *
   * @return an enumeration of the out nodes
   */
  public final Enumeration getOutNodes() {
    return new OutEdgeEnum(this);
  }

  /**
   * Is there an out edge to the given basic block?
   *
   * @param bb basic block in question
   * @return <code>true</code> if an out edge exists to bb
   *         <code>false</code> otherwise
   */
  public final boolean isOut(OPT_BasicBlock bb) {
    OutEdgeEnum oee = new OutEdgeEnum(this);
    for (; oee.hasMoreElements(); )
      if (oee.next() == bb)
        return true;
    return false;
  }

  /**
   * An enumeration of the 'normal' (not reached via exceptional control flow)
   * out nodes of the block.
   * 
   * @return an enumeration of the out nodes that are not
   *         reachable via as a result of exceptional control flow
   */
  public final OPT_BasicBlockEnumeration getNormalOut() {
    return new NormalOutEdgeEnum(this);
  }

  /**
   * Is there a 'normal' out edge to the given basic block?
   *
   * @param bb basic block in question
   * @return <code>true</code> if a normal out edge exists to bb
   *         <code>false</code> otherwise
   */
  public final boolean isNormalOut(OPT_BasicBlock bb) {
    NormalOutEdgeEnum noee = new NormalOutEdgeEnum(this);
    for (; noee.hasMoreElements(); )
      if (noee.next() == bb)
        return true;
    return false;
  }

  /**
   * An enumeration of the 'exceptional' (reached via exceptional control flow)
   * out nodes of the block.
   * 
   * @return an enumeration of the out nodes that are
   *         reachable via as a result of exceptional control flow
   */
  public final OPT_BasicBlockEnumeration getExceptionalOut() {
    if (canThrowExceptions()) {
      return new ExceptionOutEdgeEnum(this);
    } else {
      return OPT_BasicBlockEnumeration.Empty;
    }
  }         

  /**
   * Is there an 'exceptional' out edge to the given basic block?
   *
   * @param bb basic block in question
   * @return <code>true</code> if an exceptional out edge exists to bb
   *         <code>false</code> otherwise
   */
  public final boolean isExceptionalOut(OPT_BasicBlock bb) {
    if (!canThrowExceptions()) return false;
    ExceptionOutEdgeEnum eoee = new ExceptionOutEdgeEnum(this);
    for (; eoee.hasMoreElements(); )
      if (eoee.next() == bb)
        return true;
    return false;
  }


  /**
   * Get the number of out nodes that are to "normal" basic blocks
   *
   * @return the number of out nodes that are not the start of 
   *         exception handlers
   */
  public final int getNumberOfNormalOut() {
    int count = 0;
    for (OPT_SpaceEffGraphEdge e = _outEdgeStart; e != null; e = e.getNextOut()) {
      OPT_BasicBlock bb = (OPT_BasicBlock)e.toNode();
      if (!bb.isExceptionHandlerBasicBlock()) {
        count++;
      }
    }
    return count;
  }    

  /**
   * Get the number of out nodes that are to exception handler basic blocks
   *
   * @return the number of out nodes that are exception handlers
   */
  public final int getNumberOfExceptionalOut() {
    int count = 0;
    if (canThrowExceptions())
      for (OPT_SpaceEffGraphEdge e = _outEdgeStart; e != null; e = e.getNextOut()) {
        OPT_BasicBlock bb = (OPT_BasicBlock)e.toNode(); 
        if (bb.isExceptionHandlerBasicBlock()) {
          count++;
        }
      }
    return count;
  }

  /**
   * Are there exceptinal handlers that are reachable via 
   * exceptional control flow from this basic block?
   *
   * @return <code>true</code> if an exceptional handler
   *          is reachable from this block or
   *          <code>false</code> otherwise.
   */
  public final boolean hasReachableExceptionHandlers() {
    if (canThrowExceptions())
      for (OPT_SpaceEffGraphEdge e = _outEdgeStart; e != null; e = e.getNextOut()) {
        OPT_BasicBlock bb = (OPT_BasicBlock)e.toNode();
        if (bb.isExceptionHandlerBasicBlock()) {
          return true;
        }
      }
    return false;
  }

  
  /*
   * Primitive BasicBlock enumerators.
   * We don't really intend clients to directly instantiate these, but rather to 
   * call the appropriate utility function that creates/initializes one of these
   */
  static abstract class BBEnum implements OPT_BasicBlockEnumeration {
    protected OPT_BasicBlock current;  
    public final boolean hasMoreElements() { return current != null; }
    public final Object nextElement() { return next(); }
    public final OPT_BasicBlock next() {
      if (current == null) fail();
      OPT_BasicBlock value = current;
      current = advance();
      return value;
    }      
    protected abstract OPT_BasicBlock advance();
    protected static void fail() throws java.util.NoSuchElementException, NoInlinePragma {
      throw new java.util.NoSuchElementException("Basic Block Enumeration");
    }
  }
  // Arbitrary constructed enumeration of some set of basic blocks
  static final class ComputedBBEnum implements OPT_BasicBlockEnumeration {
    private OPT_BasicBlock[] blocks;
    private int numBlocks;
    private int current;
    protected ComputedBBEnum(int maxBlocks) {
      blocks = new OPT_BasicBlock[maxBlocks];
    }
    protected void addElement(OPT_BasicBlock b) {
      blocks[numBlocks++] = b;
    }
    protected void addPossiblyDuplicateElement(OPT_BasicBlock b) {
      for (int i = 0; i< numBlocks; i++) {
        if (blocks[i] == b) return;
      }
      addElement(b);
    }
    public int totalCount() { return numBlocks; }
    public boolean hasMoreElements() { return current < numBlocks; }
    public Object nextElement() { return next(); }
    public OPT_BasicBlock next() {
      if (current >=  numBlocks) fail();
      return blocks[current++];
    }
    protected static void fail() throws java.util.NoSuchElementException, NoInlinePragma {
      throw new java.util.NoSuchElementException("Basic Block Enumeration");
    }
  }

  // this class needs to be implemented efficiently, as it is used heavily.
  static final class InEdgeEnum implements OPT_BasicBlockEnumeration {
    private OPT_SpaceEffGraphEdge _edge;
    public InEdgeEnum(OPT_SpaceEffGraphNode n) { _edge = n.firstInEdge(); }
    public boolean hasMoreElements() { return _edge != null; }
    public Object nextElement()      { return next();        }
    public OPT_BasicBlock next() {
      OPT_SpaceEffGraphEdge e = _edge;
      _edge = e.getNextIn();
      return (OPT_BasicBlock)e.fromNode();
    }
  } 

  // this class needs to be implemented efficiently, as it is used heavily.
  static final class OutEdgeEnum implements OPT_BasicBlockEnumeration {
    private OPT_SpaceEffGraphEdge _edge;
    public OutEdgeEnum(OPT_SpaceEffGraphNode n) { _edge = n.firstOutEdge(); }
    public boolean hasMoreElements() { return _edge != null; }
    public Object nextElement()      { return next();        }
    public OPT_BasicBlock next() {
      OPT_SpaceEffGraphEdge e = _edge;
      _edge = e.getNextOut();
      return (OPT_BasicBlock)e.toNode();
    }
  } 

  // Enumerate the non-handler blocks in the edge set
  static final class NormalOutEdgeEnum extends BBEnum {
    private OPT_SpaceEffGraphEdge _edge;
    protected NormalOutEdgeEnum(OPT_SpaceEffGraphNode n) { 
      _edge = n.firstOutEdge(); 
      current=advance();
    }
    protected OPT_BasicBlock advance() {
      while (_edge != null) {
        OPT_BasicBlock cand = (OPT_BasicBlock)_edge.toNode();
        _edge = _edge.getNextOut();
        if (!cand.isExceptionHandlerBasicBlock())
          return cand;
      }
      return null;
    }
  }
 
  // Enumerate the non-handler blocks in the edge set
  static final class ExceptionOutEdgeEnum extends BBEnum {
    private OPT_SpaceEffGraphEdge _edge;
    protected ExceptionOutEdgeEnum(OPT_SpaceEffGraphNode n) { 
      _edge = n.firstOutEdge(); 
      current=advance();
    }
    protected OPT_BasicBlock advance() {
      while (_edge != null) {
        OPT_BasicBlock cand = (OPT_BasicBlock)_edge.toNode();
        _edge = _edge.getNextOut();
        if (cand.isExceptionHandlerBasicBlock())
          return cand;
      }
      return null;
    }
  }  
}
