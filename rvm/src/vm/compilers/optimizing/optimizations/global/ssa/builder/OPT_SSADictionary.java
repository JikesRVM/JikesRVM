/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.classloader.*;
import com.ibm.JikesRVM.opt.ir.*;
import  java.util.*;

/*
 * This module tracks heap variables needed for Array SSA form.
 *
 * @author Stephen Fink
 */

/**
 * An <code> OPT_SSADictionary </code> structure holds lookaside
 * information regarding Heap Array SSA form for an IR.  The general idea
 * is that all Heap Array SSA form information resides in this lookaside
 * structure.  Note that this is not the case for scalar SSA information.
 *
 * <P> See our SAS 2000 paper
 * <a href="http://www.research.ibm.com/jalapeno/publication.html#sas00">
 *  Unified Analysis of Arrays and Object References in Strongly Typed
 *  Languages </a> for an overview of Array SSA form.  More implementation
 *  details are documented in {@link OPT_SSA <code> OPT_SSA.java </code>}.
 *
 * @see OPT_SSA
 */
public final class OPT_SSADictionary implements OPT_Operators {

  /**
   * Flag to turn on debugging
   */
  final static boolean DEBUG = false;

  /**
   * Initialize a structure to hold Heap Array SSA information.
   *
   * @param heapTypes only create heap arrays for these locations.
   *                  if null, create all heap arrays
   * @param uphi Should we use uphi functions? (ie. loads create a new
   *                             name for heap arrays)
   */
  OPT_SSADictionary (Set heapTypes, boolean uphi, boolean insertPEIDeps,
                     OPT_IR ir) {
    this.heapTypes = heapTypes;
    this.uphi = uphi;
    this.insertPEIDeps = insertPEIDeps;
    this.ir = ir;
  }

  /**
   * Does a particular instruction <em> use </em> any heap variable?
   * 
   * @param s the instruction in question
   * @return true iff the instruction uses any heap variable.  false
   * otherwise
   */
  public boolean usesHeapVariable (OPT_Instruction s) {
    // special case code for Phi instructions
    if (Phi.conforms(s)) {
      OPT_Operand result = Phi.getResult(s);
      return  (result instanceof OPT_HeapOperand);
    }
    Object o = uses.get(s);
    return  (o != null);
  }

  /**
   * Does a particular instruction <em> define </em> any heap variable?
   * 
   * @param s the instruction in question
   * @return true iff the instruction defs any heap variable.  false
   * otherwise
   */
  public boolean defsHeapVariable (OPT_Instruction s) {
    // special case code for Phi instructions
    if (s.operator == PHI) {
      OPT_Operand result = Phi.getResult(s);
      return  (result instanceof OPT_HeapOperand);
    }
    Object o = defs.get(s);
    return  (o != null);
  }

  /** 
   * Return the heap operands used by an instruction.
   * 
   * @param s the instruction in question
   * @return an array of the heap operands this instruction uses
   */
  public OPT_HeapOperand[] getHeapUses(OPT_Instruction s) {
    if (s.operator == PHI) {
      if (!usesHeapVariable(s)) return null;
      OPT_HeapOperand[] result = new OPT_HeapOperand[Phi.getNumberOfValues(s)];
      for (int i=0; i<result.length; i++) {
        result[i] = (OPT_HeapOperand)Phi.getValue(s,i);
      }
      return result;
    } else {
      return  (OPT_HeapOperand[])uses.get(s);
    }
  }

  /** 
   * Return the heap operands defined by an instruction.
   * 
   * @param s the instruction in question
   * @return an array of the heap operands this instruction defs
   */
  public OPT_HeapOperand[] getHeapDefs (OPT_Instruction s) {
    if (VM.VerifyAssertions) VM._assert(s.operator != PHI);
    return  (OPT_HeapOperand[])defs.get(s);
  }

  /**
   * Return the number of heap operands defined by an instruction
   * 
   * @param s the instruction in question
   * @return the number of heap operands this instruction defs
   */
  public int getNumberOfHeapDefs (OPT_Instruction s) {
    return  getHeapDefs(s).length;
  }

  /** 
   * Replace all heap variables that an instruction defs with
   * new heap variables.  Each new heap variable has the same
   * type as the old one, but a new number.  Essentially, this
   * function introduces new names required for translation
   * into SSA form.  This instruction will be the single static
   * definition for each new heap variable.
   *
   * @param s the instruction that writes to the new heap variables
   * @param b the basic block containing <code> s </code>
   * @return the new heap variables that the instruction defines
   */
  public OPT_HeapOperand[] replaceDefs (OPT_Instruction s, OPT_BasicBlock b) {
    if (s.operator() == PHI) {
      // Note that a PHI
      // instruction defs exactly one heap variable, newH[0]
      OPT_HeapOperand oldDef = (OPT_HeapOperand)Phi.getResult(s);
      int number = getNextHeapVariableNumber(oldDef.getHeapType());
      OPT_HeapOperand[] newH = new OPT_HeapOperand[1];
      newH[0] = new OPT_HeapOperand(new OPT_HeapVariable(oldDef.getHeapType(), number,ir));
      newH[0].setInstruction(s);
      Phi.setResult(s, newH[0]);
      // record the new heap definition
      newH[0].getHeapVariable().registerDef(b);
      if (DEBUG) System.out.println("New heap def " + newH[0] + " for " + s);
      // store the new heap variable in relevant data structures
      HeapKey key = new HeapKey(number, oldDef.getHeapType());
      heapVariables.put(key, newH[0].getHeapVariable());
      return newH;
    } else {
      // get old heap operands defined by this instruction
      OPT_HeapOperand[] oldH = (OPT_HeapOperand[])defs.get(s);
      OPT_HeapOperand[] newH = new OPT_HeapOperand[oldH.length];
      // for each old heap variable ..
      for (int i = 0; i < oldH.length; i++) {
        // create a new heap variable 
        int number = getNextHeapVariableNumber(oldH[i].getHeapType());
        newH[i] = new OPT_HeapOperand(new OPT_HeapVariable(oldH[i].getHeapType(), 
                                                           number,ir));
        newH[i].setInstruction(s);
        // record the new heap definition
        newH[i].getHeapVariable().registerDef(b);
        if (DEBUG) System.out.println("New heap def " + newH[i] + " for " + s);
        // store the new heap variable in relevant data structures
        HeapKey key = new HeapKey(number, oldH[i].getHeapType());
        heapVariables.put(key, newH[i].getHeapVariable());
      }
      // record the new heap variables this instruction defs
      defs.put(s, newH);
      return  newH;
    }
  }

  /**
   * Return an enumeration of the heap variables in this IR.
   *
   * @return the heap variables stored for this IR
   */
  public Enumeration getHeapVariables () {
    return new OPT_IteratorEnumerator(heapVariables.values().iterator());
  }

  /**
   * Return an enumeration of the control-phi functions for 
   * <em> Heap </em> variables at the beginning of a basic block.
   * 
   * @param bb the basic block in question
   * @return the phi instructions for heap variables at the beginning of
   * the basic block
   */
  public Enumeration getHeapPhiInstructions (OPT_BasicBlock bb) {
    ArrayList v = (ArrayList)heapPhi.get(bb);
    if (v == null)
      return new OPT_IteratorEnumerator(emptyArrayList.iterator());
    return new OPT_IteratorEnumerator(v.iterator());
  }

  /**
   * Return an enumeration of all instructions for a basic block, including
   * the control-PHI functions for <em> heap </em> variables stored 
   * implicitly here.
   * 
   * @param bb the basic block in question
   * @return an enumeration of all instructions in this basic block,
   * including heap phi instructions stored implicitly in this lookaside
   * structure
   */
  public Enumeration getAllInstructions (OPT_BasicBlock bb) {
    return  new AllInstructionEnumeration(bb);
  }

  /**
   * Return an enumeration of all uses of a particular heap variable.
   * 
   * @param A the heap variable in question
   * @return an iterator over all instructions that use this heap
   * variable
   */
  public Iterator iterateHeapUses (OPT_HeapVariable A) {
    HashSet u = (HashSet)UseChain.get(A);
    return  u.iterator();
  }

  /**
   * Return the operand that represents a heap variable's unique def.
   *
   * @param A the heap variable in question
   * @return the heap operand that represents this heap variable's unique
   * static definition
   */
  public OPT_HeapOperand getUniqueDef (OPT_HeapVariable A) {
    return  (OPT_HeapOperand)DefChain.get(A);
  }

  /**
   * Return an enumeration of all the original uses of a heap variable.
   * That is, return an iteration of all uses of the heap variable 
   * <em> before </em> we performed renaming for SSA.
   *
   * @param A the heap variable in question
   * @return an iteration of all instructions that used this heap
   * variable, prior to renaming for SSA
   */
  public Iterator iterateOriginalHeapUses (OPT_HeapVariable A) {
    Object type = A.getHeapType();
    HashSet set = findOrCreateOriginalUses(type);
    return  set.iterator();
  }

  /**
   * Return an enumeration of all the original definitions of a heap variable.
   * That is, return an iteration of all defs of the heap variable 
   * <em> before </em> we performed renaming for SSA.
   *
   * @param A the heap variable in question
   * @return an iteration of all instructions that defined this heap
   * variable, prior to renaming for SSA
   */
  public Iterator iterateOriginalHeapDefs (OPT_HeapVariable A) {
    Object type = A.getHeapType();
    HashSet set = findOrCreateOriginalDefs(type);
    return  set.iterator();
  }

  /**
   * Return a set of all the original uses of a heap variable.
   * That is, return the set of all uses of the heap variable 
   * <em> before </em> we performed renaming for SSA.
   *
   * @param A the heap variable in question
   * @return the set of all instructions that used this heap
   * variable, prior to renaming for SSA
   */
  public HashSet findOrCreateOriginalUses (Object type) {
    HashSet result = (HashSet)originalUses.get(type);
    if (result != null)
      return  result;
    // not found: create a new set
    result = new HashSet(2);
    for (Enumeration e = getHeapVariables(); e.hasMoreElements();) {
      OPT_HeapVariable B = (OPT_HeapVariable)e.nextElement();
      if (B.getHeapType().equals(type)) {
        HashSet u = (HashSet)UseChain.get(B);
        result.addAll(u);
      }
    }
    // store it in the hash set, and return
    originalUses.put(type, result);
    return  result;
  }

  /**
   * Return a set of all the original definitions of a heap variable.
   * That is, return the set of all uses of the heap variable 
   * <em> before </em> we performed renaming for SSA.
   *
   * @param A the heap variable in question
   * @return the set of all instructions that defined this heap
   * variable, prior to renaming for SSA
   */
  public HashSet findOrCreateOriginalDefs (Object type) {
    HashSet result = (HashSet)originalDefs.get(type);
    if (result != null)
      return  result;
    // not found: create a new set
    result = new HashSet(2);
    for (Enumeration e = getHeapVariables(); e.hasMoreElements();) {
      OPT_HeapVariable B = (OPT_HeapVariable)e.nextElement();
      if (B.getHeapType().equals(type)) {
        OPT_HeapOperand def = getUniqueDef(B);
        if (def != null)
          result.add(def);
      }
    }
    // store it in the hash set, and return
    originalDefs.put(type, result);
    return  result;
  }

  /**
   * Return the number of uses of a heap variable.
   * 
   * @param A the heap variable in question
   * @return the number of uses of the heap variable
   */
  public int getNumberOfUses (OPT_HeapVariable A) {
    HashSet u = (HashSet)UseChain.get(A);
    return  u.size();
  }

  /**
   * Return an enumeration of all heap variables that may be
   * exposed on procedure exit.
   * 
   * @return an enumeration of all heap variables that may be exposed on
   * procedure exit
   */
  public Iterator enumerateExposedHeapVariables () {
    ArrayList v = new ArrayList();
    for (Enumeration e = getHeapVariables(); e.hasMoreElements();) {
      OPT_HeapVariable H = (OPT_HeapVariable)e.nextElement();
      if (isExposedOnExit(H)) {
        v.add(H);
      }
    }
    return v.iterator();
  }

  /**
   * Is heap variable H exposed on procedure exit?
   * 
   * @return true or false as appropriate
   */
  public boolean isExposedOnExit (OPT_HeapVariable H) {
    for (Iterator i = iterateHeapUses(H); i.hasNext();) {
      OPT_HeapOperand op = (OPT_HeapOperand)i.next();
      OPT_Instruction s = op.instruction;
      if (exits.contains(s)) {
        return  true;
      }
    }
    return false;
  }

  /**
   * Recompute the chain of uses for each heap variable.
   * NOTE: for now this procedure does <em> not </em> recompute
   * def chains
   */
  public void recomputeArrayDU () {
    UseChain = new HashMap(2);
    DefChain = new HashMap(2);
    // create a set of uses for each heap variable
    for (Enumeration e = getHeapVariables(); e.hasMoreElements();) {
      OPT_HeapVariable H = (OPT_HeapVariable)e.nextElement();
      HashSet u = new HashSet(2);
      UseChain.put(H, u);
    }
    // populate the use chain with each use registered
    for (Iterator e = uses.values().iterator(); e.hasNext();) {
      OPT_HeapOperand[] H = (OPT_HeapOperand[])e.next();
      if (H == null) continue;
      for (int i = 0; i < H.length; i++) {
        OPT_HeapVariable v = H[i].getHeapVariable();
        HashSet u = (HashSet)UseChain.get(v);
        u.add(H[i]);
      }
    }
    // record the unique def for each heap variable
    for (Iterator e = defs.values().iterator(); e.hasNext();) {
      OPT_HeapOperand[] H = (OPT_HeapOperand[])e.next();
      if (H == null) continue;
      for (int i = 0; i < H.length; i++) {
        OPT_HeapVariable v = H[i].getHeapVariable();
        DefChain.put(v, H[i]);
      }
    }
    // handle each HEAP PHI function.
    for (Enumeration e = ir.getBasicBlocks(); e.hasMoreElements(); ) {
      OPT_BasicBlock bb = (OPT_BasicBlock)e.nextElement();
      for (Enumeration hp = getHeapPhiInstructions(bb); hp.hasMoreElements() ; ) {
        OPT_Instruction phi = (OPT_Instruction)hp.nextElement();
        OPT_HeapOperand H = (OPT_HeapOperand)Phi.getResult(phi);
        OPT_HeapVariable v = H.getHeapVariable();
        DefChain.put(v,H);
        for (int i = 0; i < Phi.getNumberOfValues(phi); i++) {
          OPT_HeapOperand Hu = (OPT_HeapOperand)Phi.getValue(phi,i);
          OPT_HeapVariable vu = Hu.getHeapVariable();
          HashSet u = (HashSet)UseChain.get(vu);
          u.add(Hu);
        }
      }
    }
  }


  /** 
   * Delete an HeapOperand from the use chain of its heap variable
   *
   * @param op the heap operand to be deleted 
   */
  public void deleteFromUseChain (OPT_HeapOperand op)
  {
    OPT_HeapVariable hv = op.getHeapVariable();
    HashSet u = (HashSet)UseChain.get(hv);
    u.remove (op);
  }
  
  /** 
   * Add an HeapOperand to the use chain of its heap variable
   *
   * @param op the heap operand to be added 
   */
  public void addToUseChain (OPT_HeapOperand op)
  {
    OPT_HeapVariable hv = op.getHeapVariable();
    HashSet u = (HashSet)UseChain.get(hv);
    u.add (op);
  }


  /** 
   * Create a heap control phi instruction, and store it at the
   * beginning of a basic block.
   *
   * @param bb the basic block
   * @param H the heap variable to merge
   */
  void createHeapPhiInstruction (OPT_BasicBlock bb, OPT_HeapVariable H) {
    OPT_Instruction s = makePhiInstruction(H, bb);
    /*
    OPT_HeapOperand[] Hprime = new OPT_HeapOperand[1];
    Hprime[0] = new OPT_HeapOperand(H);
    Hprime[0].setInstruction(s);
    defs.put(s, Hprime);
    */
    ArrayList heapPhis = (ArrayList)heapPhi.get(bb);
    if (heapPhis == null) {
      heapPhis = new ArrayList(2);
      heapPhi.put(bb, heapPhis);
    }
    heapPhis.add(s);
    registerInstruction(s, bb);
  }

  /** 
   * Register that an instruction now uses the set of heap operands
   * 
   * @param s the instruction in question
   * @param H the set of heap operands which s uses
   */
  public void replaceUses (OPT_Instruction s, OPT_HeapOperand[] H) {
    if (VM.VerifyAssertions) VM._assert(s.operator != PHI);
    uses.put(s, H);
    for (int i = 0; i < H.length; i++) {
      H[i].setInstruction(s);
    }
  }

  /**
   * Return the total number of heap variables allocated for the IR
   * 
   * @return the total number of heap variables allocated for the IR
   */
  public int getNumberOfHeapVariables () {
    return  heapVariables.size();
  }

  /**
   * Register that an instruction s can potentially leave the procedure.
   * We conservatively record that s uses all heap variables.
   * <p> NOTE: This function should be called before renaming.
   * <p> NOTE: Only need to use this for backwards analyses
   *
   * @param s the instruction in question
   * @param b s's basic block
   */
  public void registerExit (OPT_Instruction s, OPT_BasicBlock b) {
    // setup an array of all heap variables
    // TODO: for efficiency, cache a copy of 'all' 
    Iterator vars = heapVariables.values().iterator();
    OPT_HeapOperand[] all = new OPT_HeapOperand[heapVariables.size()];
    for (int i = 0; i < all.length; i++) {
      all[i] = new OPT_HeapOperand((OPT_HeapVariable)vars.next());
      all[i].setInstruction(s);
      // record that all[i] is def'ed in b
      all[i].getHeapVariable().registerDef(b);
    }
    // record that s uses all heap variables
    uses.put(s, all);
    // record that instruction s can exit the procedure
    exits.add(s);
  }

  /**
   * Register that an instruction s has unknown side effects.  That is,
   * we conservatively record that s defs and uses all heap variables.
   * <p> NOTE: This function should be called before renaming.
   *
   * @param s the instruction in question
   * @param b the basic block containing s
   */
  public void registerUnknown (OPT_Instruction s, OPT_BasicBlock b) {
    if (VM.VerifyAssertions) VM._assert(s.operator != PHI);
    // setup an array of all heap variables
    // TODO: for efficiency, cache a copy of 'all' 
    Iterator vars = heapVariables.values().iterator();
    OPT_HeapOperand[] all = new OPT_HeapOperand[heapVariables.size()];
    for (int i = 0; i < all.length; i++) {
      all[i] = new OPT_HeapOperand((OPT_HeapVariable)vars.next());
      all[i].setInstruction(s);
      // record that all[i] is def'ed in b
      all[i].getHeapVariable().registerDef(b);
    }
    // record that s uses and defs all heap variables
    uses.put(s, all);
    defs.put(s, all);
    // record that instruction s can exit the procedure
    exits.add(s);
  }

  /**
   * Record the heap variables that instruction s defines and uses.
   *
   * @param s the instruction in question
   * @param b the basic block containing s
   */
  public void registerInstruction (OPT_Instruction s, OPT_BasicBlock b) {
    if (!s.isImplicitLoad() && !s.isImplicitStore() && !s.isAllocation()
        && s.operator() != PHI
        && !(insertPEIDeps
             && (s.isPEI()
                 || Label.conforms(s)
                 || BBend.conforms(s)
                 || s.operator.opcode == UNINT_BEGIN_opcode
                 || s.operator.opcode == UNINT_END_opcode)))
      return;
    // handled by registerUnknown
    if (s.isDynamicLinkingPoint()) 
      return;
    switch (s.getOpcode()) {
      case LABEL_opcode: // only reached if insertPEIDeps
        labelHelper (s, b);
        break;
      case BBEND_opcode: // only reached if insertPEIDeps
        bbendHelper (s, b);
        break;
      case UNINT_BEGIN_opcode: // only reached if insertPEIDeps
      case UNINT_END_opcode: // only reached if insertPEIDeps
        registerUse (s, exceptionState);
        registerDef (s, b, exceptionState);
        break;
      case GETFIELD_opcode:
        getFieldHelper(s, b);
        break;
      case PUTFIELD_opcode:
        putFieldHelper(s, b);
        break;
      case GETSTATIC_opcode:
        getStaticHelper(s, b);
        break;
      case PUTSTATIC_opcode:
        putStaticHelper(s, b);
        break;
      case NEW_opcode:case NEW_UNRESOLVED_opcode:
        newHelper(s, b);
        break;
      case NEWARRAY_opcode:case NEWARRAY_UNRESOLVED_opcode:
        newArrayHelper(s, b);
        break;
      case NEWOBJMULTIARRAY_opcode:
        /* SJF: after talking with Martin, I'm not sure what the 
         correct Array SSA representation for an allocation should
         be.  Since we do not yet use these heap variables, do
         nothing for now.  
         Future: this should probably def the heap variable for every
         field of the object type allocated.
         // treat this opcode like a CALL
         registerUnknown(s,b);
         */
        break;
      case INT_ALOAD_opcode:case LONG_ALOAD_opcode:case FLOAT_ALOAD_opcode:
      case DOUBLE_ALOAD_opcode:case REF_ALOAD_opcode:case BYTE_ALOAD_opcode:
      case UBYTE_ALOAD_opcode:case USHORT_ALOAD_opcode:case SHORT_ALOAD_opcode:
        aloadHelper(s, b);
        break;
      case INT_ASTORE_opcode:case LONG_ASTORE_opcode:case FLOAT_ASTORE_opcode:
      case DOUBLE_ASTORE_opcode:case REF_ASTORE_opcode:case BYTE_ASTORE_opcode:
      case SHORT_ASTORE_opcode:
        astoreHelper(s, b);
        break;
      case ARRAYLENGTH_opcode:
        arraylengthHelper(s, b);
        break;
      case CALL_opcode:case SYSCALL_opcode:
      case MONITORENTER_opcode:case MONITOREXIT_opcode:
      case PREPARE_INT_opcode:case PREPARE_LONG_opcode:
      case ATTEMPT_INT_opcode:case ATTEMPT_LONG_opcode:
      case READ_CEILING_opcode:case WRITE_FLOOR_opcode:
        //-#if RVM_FOR_POWERPC
      case DCBST_opcode:case ICBI_opcode:
        //-#endif
        // do nothing: these cases handled by registerUnknown
        break;
      case UBYTE_LOAD_opcode: case BYTE_LOAD_opcode: 
      case USHORT_LOAD_opcode: case SHORT_LOAD_opcode: 
      case INT_LOAD_opcode: case LONG_LOAD_opcode: case DOUBLE_LOAD_opcode:case REF_LOAD_opcode:
        // !!TODO: how to handle this special case?
        break;
      case BYTE_STORE_opcode: case SHORT_STORE_opcode: case REF_STORE_opcode:
      case INT_STORE_opcode: case LONG_STORE_opcode: case DOUBLE_STORE_opcode:
        // !!TODO: how to handle this special case?
        break;
      case PHI_opcode:
        phiHelper(s, b);
        break;
      default:
        if (!s.isPEI()) {
          System.out.println("SSA dictionary failed on " + s.toString());
          throw  new OPT_OperationNotImplementedException(
                                                          "OPT_SSADictionary: Unsupported opcode " + s);
        }
    }           // switch
    if (insertPEIDeps) {
      if (s.isImplicitStore()) addExceptionStateToUses(s);
      if (s.isPEI()) addExceptionStateToDefs(s, b);
    }
  }

  /**
   * A mapping from <code> OPT_Instruction </code> to the set of heap
   * operands (an <code> OPT_HeapOperand[]</code>) that this instruction
   * uses.  Note that PHI instructions <STRONG> do not </STRONG> appear in
   * this mapping.
   */
  private HashMap uses = new HashMap();            

  /**
   * A mapping from <code> OPT_Instruction </code> to the set of heap
   * operands (an <code> OPT_HeapOperand[]</code>) that this instruction
   * defines.  Note that PHI instructions <STRONG> do not </STRONG> appear in
   * this mapping.
   */
  private HashMap defs = new HashMap(); 

  /**
   * A mapping from <code> HeapKey </code> to the set of heap
   * variables introduced for this IR
   */
  private HashMap heapVariables = new HashMap();

  /**
   * A mapping from heap variable type to <code> Integer </code>
   * This structure holds the next number to assign when creating
   * a new heap variable name for a given type
   */
  private HashMap nextNumber = new HashMap();         

  /**
   * A mapping from <code> OPT_BasicBlock </code> to <code> ArrayList
   * </code> of <code> OPT_Instruction </code>.  
   * This map holds the list of heap phi instructions stored as
   * lookaside for each basic block.
   */
  private HashMap heapPhi = new HashMap(10);          

  /**
   * An empty vector, used for utility.
   */
  private ArrayList emptyArrayList = new ArrayList(0);

  /**
   * A mapping from <code> OPT_HeapVariable </code> to <code> HashSet
   * </code> of <code> OPT_HeapOperand </code>.  
   * This map holds the set of heap operands which use each heap
   * variable.
   */
  private HashMap UseChain = new HashMap(10);

  /**
   * A mapping from <code> OPT_HeapVariable </code> to <code> HashSet
   * </code> of <code> OPT_HeapOperand </code>.  
   * This map holds the set of heap operands which define each heap
   * variable.
   */
  private HashMap DefChain = new HashMap(10);     

  /**
   * The set of instructions which have been registered to potentially
   * exit the procedure 
   */
  private HashSet exits = new HashSet(10);     // set of registered

  /**
   * A mapping from a heap variable type to a <code> HashSet
   * </code> of <code> OPT_Instruction </code>.  
   * The set of all uses of a heap variable type
   * <em> before </em> we performed renaming for SSA.
   */
  private HashMap originalUses = new HashMap(10);

  /**
   * A mapping from a heap variable type to a <code> HashSet
   * </code> of <code> OPT_Instruction </code>.  
   * The set of all definitions of a heap variable type
   * <em> before </em> we performed renaming for SSA.
   */
  private HashMap originalDefs = new HashMap(10);

  /**
   * The set of type to build heap array variables for
   */
  private Set heapTypes;   // the set of types to build heap arrays for
  
  /**
   * Should the heap array SSA form constructed include uphi functions?
   * That is, does a <em> use </em> create a new name for a heap variable.
   */
  private boolean uphi;         // use uphi functions?

  /**
   * Should PEIs and stores to the heap be modelled via an explicit exception
   * state heap variable?
   */
  private boolean insertPEIDeps;

  /**
   * The name of the heap variable
   * that is used for modelling explicit exception dependencies
   */
  String exceptionState = "X-State";
  
  /**
   * A pointer to the governing IR
   */
  private OPT_IR ir;

  /** 
   * Record the effects of a getfield instruction on the heap array
   * SSA form.  Register the heap variables that this instruction uses and
   * defs.  Note that if inserting uphi functions, a getfield defs a new
   * heap variable name.
   * 
   * @param s the getfield instruction
   * @param b the basic block containing s
   */
  private void getFieldHelper (OPT_Instruction s, OPT_BasicBlock b) {
    OPT_LocationOperand locOp = GetField.getLocation(s);
    VM_FieldReference field = locOp.getFieldRef();
    registerUse(s, field);
    if (uphi)
      registerDef(s, b, field);
  }

  /** 
   * Record the effects of a putfield instruction on the heap array
   * SSA form.  Register the heap variables that this instruction uses and
   * defs.  
   * 
   * @param s the getfield instruction
   * @param b the basic block containing s
   */
  private void putFieldHelper (OPT_Instruction s, OPT_BasicBlock b) {
    OPT_LocationOperand locOp = PutField.getLocation(s);
    VM_FieldReference field = locOp.getFieldRef();
    registerUse(s, field);
    registerDef(s, b, field);
  }

  /** 
   * Record the effects of a getstatic instruction on the heap array
   * SSA form.  Register the heap variables that this instruction uses and
   * defs.  Note that if inserting uphi functions, a getstatic defs a new
   * heap variable name.
   * 
   * @param s the getstatic instruction
   * @param b the basic block containing s
   */
  private void getStaticHelper (OPT_Instruction s, OPT_BasicBlock b) {
    OPT_LocationOperand locOp = GetStatic.getLocation(s);
    VM_FieldReference field = locOp.getFieldRef();
    registerUse(s, field);
    if (uphi)
      registerDef(s, b, field);
  }

  /** 
   * Record the effects of a putstatic instruction on the heap array
   * SSA form.  Register the heap variables that this instruction uses and
   * defs.  
   * 
   * @param s the putstatic instruction
   * @param b the basic block containing s
   */
  private void putStaticHelper (OPT_Instruction s, OPT_BasicBlock b) {
    OPT_LocationOperand locOp = PutStatic.getLocation(s);
    VM_FieldReference field = locOp.getFieldRef();
    registerUse(s, field);
    registerDef(s, b, field);
  }

  /**
   * Update the heap array SSA form for an allocation instruction
   *
   * @param s the allocation instruction
   * @param b the basic block containing the allocation
   */
  private void newHelper (OPT_Instruction s, OPT_BasicBlock b) {  
  /* SJF: after talking with Martin, I'm not sure what the 
   correct Array SSA representation for an allocation should
   be.  Since we do not yet use these heap variables, do
   nothing for now.
   Future: this should probably def the heap variable for every
   field of the object type allocated.
   OPT_TypeOperand typeOp = New.getType(s);
   VM_Type type = typeOp.type;
   registerUse(s,type);
   registerDef(s,b,type);
   */
  }

  /**
   * Update the heap array SSA form for an array allocation instruction
   *
   * @param s the allocation instruction
   * @param b the basic block containing the allocation
   */
  private void newArrayHelper (OPT_Instruction s, OPT_BasicBlock b) {
  // TODO: use some class hierarchy analysis
  /* SJF: after talking with Martin, I'm not sure what the 
   correct Array SSA representation for an allocation should
   be.  Since we do not yet use these heap variables, do
   nothing for now.
   Future: this should probably def the heap variable for every
   field of the object type allocated.
   OPT_TypeOperand typeOp = NewArray.getType(s);
   VM_Type type = typeOp.type;
   if (!type.asArray().getElementType().isPrimitiveType())
   type = OPT_ClassLoaderProxy.JavaLangObjectArrayType;
   registerUse(s,type);
   registerDef(s,b,type);
   */
  }

  /** 
   * Record the effects of a aload instruction on the heap array
   * SSA form.  Register the heap variables that this instruction uses and
   * defs.  Note that if inserting uphi functions, a aload defs a new
   * heap variable name.
   * 
   * @param s the aload instruction
   * @param b the basic block containing s
   */
  private void aloadHelper (OPT_Instruction s, OPT_BasicBlock b) {
    // TODO: use some class hierarchy analysis
    VM_TypeReference type = ALoad.getArray(s).getType();

    // After cond branch splitting, operand may be a Null constant
    // filter out it now  -- Feng
    if (type.isArrayType()) { 
      if (!type.getArrayElementType().isPrimitiveType())
        type = VM_TypeReference.JavaLangObjectArray;
      registerUse(s, type);
      if (uphi)
        registerDef(s, b, type);
    }
  }

  /** 
   * Record the effects of an astore instruction on the heap array
   * SSA form.  Register the heap variables that this instruction uses and
   * defs.  
   * 
   * @param s the astore instruction
   * @param b the basic block containing s
   */
  private void astoreHelper (OPT_Instruction s, OPT_BasicBlock b) {
    // TODO: use some class hierarchy analysis
    VM_TypeReference type = AStore.getArray(s).getType();

    // After cond branch splitting, operand may be a Null constant
    // filter out it now  -- Feng
    if (type.isArrayType()) {
      if (!type.getArrayElementType().isPrimitiveType())
        type = VM_TypeReference.JavaLangObjectArray;
      registerUse(s, type);
      registerDef(s, b, type);
    }
  }

  /** 
   * Record the effects of an arraylength instruction on the heap array
   * SSA form.  Register the heap variable that this instruction uses.
   * 
   * @param s the arraylength instruction
   * @param b the basic block containing s
   */
  private void arraylengthHelper (OPT_Instruction s, OPT_BasicBlock b) {
    // TODO: use some class hierarchy analysis
    VM_TypeReference type = GuardedUnary.getVal(s).getType();
    
    // After cond branch splitting, operand may be a Null constant
    // filter out it now  -- Feng
    if (type.isArrayType()) {
      if (!type.getArrayElementType().isPrimitiveType())
        type = VM_TypeReference.JavaLangObjectArray;
      registerUse(s, type);
    }
  }

  /** 
   * Record the effects of a phi instruction on the heap array
   * SSA form.  Register the heap variables that this instruction uses
   * and defs.
   * 
   * @param s the phi instruction
   * @param b the basic block containing s
   */
  private void phiHelper (OPT_Instruction s, OPT_BasicBlock b) {
    // for phi function, we dont' register implicit defs and uses
    // since they're explicit in the instruction
    /*
    Object result = Phi.getResult(s);
    if (!(result instanceof OPT_HeapOperand))
      return;
    OPT_HeapOperand H = (OPT_HeapOperand)Phi.getResult(s);
    Object Htype = H.getHeapType();
    if (Htype instanceof VM_Type) {
      VM_Type t = (VM_Type)Htype;
      registerDef(s, b, t);
    } 
    else if (Htype instanceof VM_Field) {
      VM_Field f = (VM_Field)Htype;
      registerDef(s, b, f);
    }
    else {
      String a = (String)Htype;
      registerDef(s, b, a);
    }
    for (int i = 0; i < Phi.getNumberOfValues(s); i++) {
      OPT_HeapOperand U = (OPT_HeapOperand)Phi.getValue(s, i);
      Object Utype = U.getHeapType();
      if (Utype instanceof VM_Type) {
        VM_Type t = (VM_Type)Utype;
        registerUse(s, t);
      } 
      else if (Utype instanceof VM_Field) {
        VM_Field f = (VM_Field)Utype;
        registerUse(s, f);
      } else {
        String a = (String)Utype;
        registerUse(s, a);
      }
    }
  */
  }


  /** 
   * Record the effects of a label instruction on the heap array
   * SSA form.  Register the exception state that this instruction defs.
   * 
   * @param s the label instruction
   * @param b the basic block containing s
   */
  private void labelHelper (OPT_Instruction s, OPT_BasicBlock b) {
    OPT_BasicBlockEnumeration e = b.getIn();
    boolean newHandler = !e.hasMoreElements();
    while (!newHandler && e.hasMoreElements()) {
      if (!(e.next().isExceptionHandlerEquivalent(b))) newHandler = true;
    }
    if (newHandler) registerDef(s, b, exceptionState);
  }

  
  /** 
   * Record the effects of a bbend instruction on the heap array
   * SSA form.  Register the exception state that this instruction uses.
   * 
   * @param s the label instruction
   * @param b the basic block containing s
   */
  private void bbendHelper (OPT_Instruction s, OPT_BasicBlock b) {
    OPT_BasicBlockEnumeration e = b.getOut();
    boolean newHandler = !e.hasMoreElements();
    while (!newHandler && e.hasMoreElements()) {
      if (!(e.next().isExceptionHandlerEquivalent(b))) newHandler = true;
    }
    if (newHandler) registerUse(s, exceptionState);
  }

  
  /**
   * Register that an instruction uses a heap variable of a given
   * type.
   *
   * @param s the instruction in question
   * @param t the type of the heap variable the instruction uses
   */
  private void registerUse (OPT_Instruction s, VM_TypeReference t) {
    // if the heapTypes set is defined, then we only build Array
    // SSA for these types.  So, ignore uses of types that are
    // not included in the set
    if (heapTypes != null) {
      if (!heapTypes.contains(t)) {
        return;
      }
    }
    OPT_HeapVariable H = findOrCreateHeapVariable(t);
    OPT_HeapOperand[] Hprime = new OPT_HeapOperand[1];
    Hprime[0] = new OPT_HeapOperand(H);
    Hprime[0].setInstruction(s);
    uses.put(s, Hprime);
  }

  /**
   * Register that an instruction writes a heap variable for a given
   * type.
   *
   * @param s the instruction in question
   * @param b s's basic block
   * @param t the type of the heap variable the instruction modifies
   */
  private void registerDef (OPT_Instruction s, 
                            OPT_BasicBlock b, 
                            VM_TypeReference t) {
    if (VM.VerifyAssertions) VM._assert(s.operator != PHI);
    // if the heapTypes set is defined, then we only build Array
    // SSA for these types.  So, ignore uses of types that are
    // not included in the set
    if (heapTypes != null) {
      if (!heapTypes.contains(t)) {
        return;
      }
    }
    OPT_HeapVariable H = findOrCreateHeapVariable(t);
    H.registerDef(b);
    OPT_HeapOperand[] Hprime = new OPT_HeapOperand[1];
    Hprime[0] = new OPT_HeapOperand(H);
    Hprime[0].setInstruction(s);
    defs.put(s, Hprime);
  }

  /**
   * Register that an instruction uses a heap variable for a given
   * field.
   *
   * @param s the instruction in question
   * @param t the field heap variable the instruction uses
   */
  private void registerUse (OPT_Instruction s, VM_FieldReference fr) {
    if (VM.VerifyAssertions) VM._assert(s.operator != PHI);
    VM_Field f = fr.peekResolvedField();
    OPT_HeapVariable H;
    if (f == null) {
      // can't resolve field at compile time.
      // This isn't quite correct, but is somewhat close.
      // See defect 3481.
      H = findOrCreateHeapVariable(fr);
    } else {
      // if the heapTypes set is defined, then we only build Array
      // SSA for these types.  So, ignore uses of types that are
      // not included in the set
      if (heapTypes != null) {
        if (!heapTypes.contains(f)) {
          return;
        }
      }
      H = findOrCreateHeapVariable(f);
    }
    OPT_HeapOperand[] Hprime = new OPT_HeapOperand[1];
    Hprime[0] = new OPT_HeapOperand(H);
    Hprime[0].setInstruction(s);
    uses.put(s, Hprime);
  }

  /**
   * Register that instruction an instruction writes a heap variable for 
   * a given field.
   *
   * @param s the instruction in question
   * @param b s's basic block
   * @param t the field heap variable the instruction modifies
   */
  private void registerDef (OPT_Instruction s, OPT_BasicBlock b, VM_FieldReference fr) {
    if (VM.VerifyAssertions) VM._assert(s.operator != PHI);
    VM_Field f = fr.peekResolvedField();
    OPT_HeapVariable H;
    if (f == null) {
      // can't resolve field at compile time.
      // This isn't quite correct, but is somewhat close.
      // See defect 3481.
      H = findOrCreateHeapVariable(fr);
    } else {
      // if the heapTypes set is defined, then we only build Array
      // SSA for these types.  So, ignore uses of types that are
      // not included in the set
      if (heapTypes != null) {
        if (!heapTypes.contains(f)) {
          return;
        }
      }
      H = findOrCreateHeapVariable(f);
    }
    H.registerDef(b);
    OPT_HeapOperand[] Hprime = new OPT_HeapOperand[1];
    Hprime[0] = new OPT_HeapOperand(H);
    Hprime[0].setInstruction(s);
    defs.put(s, Hprime);
  }

  /**
   * Register that an instruction uses a heap variable for a given
   * field.
   *
   * @param s the instruction in question
   * @param t the field heap variable the instruction uses
   */
  private void registerUse (OPT_Instruction s, String a) {
    if (VM.VerifyAssertions) VM._assert(s.operator != PHI);
    // if the heapTypes set is defined, then we only build Array
    // SSA for these types.  So, ignore uses of types that are
    // not included in the set
    if (heapTypes != null) {
      if (!heapTypes.contains(a))
        return;
    }
    OPT_HeapVariable H = findOrCreateHeapVariable(a);
    OPT_HeapOperand[] Hprime = new OPT_HeapOperand[1];
    Hprime[0] = new OPT_HeapOperand(H);
    Hprime[0].setInstruction(s);
    uses.put(s, Hprime);
  }

  /**
   * Register that instruction an instruction writes a heap variable for 
   * a given field.
   *
   * @param s the instruction in question
   * @param b s's basic block
   * @param t the field heap variable the instruction modifies
   */
  private void registerDef (OPT_Instruction s, OPT_BasicBlock b, String a) {
    if (VM.VerifyAssertions) VM._assert(s.operator != PHI);
    // if the heapTypes set is defined, then we only build Array
    // SSA for these types.  So, ignore uses of types that are
    // not included in the set
    if (heapTypes != null) {
      if (!heapTypes.contains(a))
        return;
    }
    OPT_HeapVariable H = findOrCreateHeapVariable(a);
    H.registerDef(b);
    OPT_HeapOperand[] Hprime = new OPT_HeapOperand[1];
    Hprime[0] = new OPT_HeapOperand(H);
    Hprime[0].setInstruction(s);
    defs.put(s, Hprime);
  }


  /**
   * Returns a copy of H with an additional free slot at position 0
   *
   * @param H the array of HeapOperands to be extended.
   */
  private static OPT_HeapOperand[] extendHArray (OPT_HeapOperand[] H) {

    OPT_HeapOperand[] res;
    
    if (H == null) {
      res = new OPT_HeapOperand[1];
    } else {
      res = new OPT_HeapOperand[H.length + 1];
      for (int i = 0;  i < H.length;  ++i) res[i+1] = H[i];
    }
    return res;
  }
  

  /**
   * Register that an instruction defines the exception state.
   *
   * @param s the instruction in question
   * @param b s's basic block
   */
  public void addExceptionStateToDefs (OPT_Instruction s, OPT_BasicBlock b) {
    if (VM.VerifyAssertions) VM._assert(s.operator != PHI);
    OPT_HeapVariable H = findOrCreateHeapVariable(exceptionState);
    H.registerDef(b);
    OPT_HeapOperand[] Hprime = extendHArray ((OPT_HeapOperand[]) defs.get(s));
    Hprime[0] = new OPT_HeapOperand(H);
    Hprime[0].setInstruction(s);
    defs.put(s, Hprime);
  }


  /**
   * Register that an instruction defines the exception state.
   *
   * @param s the instruction in question
   */
  public void addExceptionStateToUses (OPT_Instruction s) {
    if (VM.VerifyAssertions) VM._assert(s.operator != PHI);
    OPT_HeapVariable H = findOrCreateHeapVariable(exceptionState);
    OPT_HeapOperand[] Hprime = extendHArray ((OPT_HeapOperand[]) uses.get(s));
    Hprime[0] = new OPT_HeapOperand(H);
    Hprime[0].setInstruction(s);
    uses.put(s, Hprime);
  }

  
  /** 
   * Return the heap variable for a given type or field with
   * number 0.
   * If no heap variable yet exits for this type or field, create a new
   * one.
   *
   * @param type the <code> VM_TypeReference </code> or <code> VM_Field </code>
   * identifying the desired heap variable
   * @return the desired heap variable
   */
  private OPT_HeapVariable findOrCreateHeapVariable (Object type) {
    if (DEBUG)
      System.out.print("findOrCreateHeapVariable " + type);
    HeapKey key = new HeapKey(0, type);
    OPT_HeapVariable H = (OPT_HeapVariable)heapVariables.get(key);
    if (DEBUG)
      System.out.println("...  found " + H);
    if (H == null) {
      // note: if not found, then we have never created a heap
      // variable with the correct type. So, create one, with number
      // 0
      int number = getNextHeapVariableNumber(type);        // should return 0
      H = new OPT_HeapVariable(type, number,ir);
      heapVariables.put(key, H);
      if (DEBUG)
        System.out.println("...  created " + heapVariables.get(key));
    }
    return  H;
  }

  /** 
   * Get the next number to be assigned to a new heap variable
   * for a given type or field.
   *
   * @param type the <code> VM_TypeReference </code> or <code> VM_Field </code>
   * identifying the heap variable
   * @return the next integer (monotonically increasing) to identify a new
   * name for this heap variable
   */
  private int getNextHeapVariableNumber (Object type) {
    Integer current = (Integer)nextNumber.get(type);
    if (current == null) {
      // no number found. Create one.
      Integer one = new Integer(1);
      nextNumber.put(type, one);
      return  0;
    }
    // bump up the number
    Integer next = new Integer(current.intValue() + 1);
    nextNumber.put(type, next);
    return  current.intValue();
  }

  /** 
   * Create a phi-function instruction for a heap variable
   *
   * @param H a symbolic variable for a Heap variable
   * @param bb the basic block holding the new phi function
   * instruction
   * @return the instruction <code> H = phi H,H,..,H </code>
   */
  private static OPT_Instruction makePhiInstruction (OPT_HeapVariable H, 
                                                     OPT_BasicBlock bb) {
    int n = bb.getNumberOfIn();
    OPT_BasicBlockEnumeration in = bb.getIn();
    OPT_HeapOperand lhs = new OPT_HeapOperand(H);
    OPT_Instruction s = Phi.create(PHI, lhs, n);
    lhs.setInstruction(s);
    for (int i = 0; i < n; i++) {
      OPT_HeapOperand op = new OPT_HeapOperand(H);
      op.setInstruction(s);
      Phi.setValue(s, i, op);
      OPT_BasicBlock pred = in.next();
      Phi.setPred(s, i, new OPT_BasicBlockOperand(pred)); 
    }
    return  s;
  }

  /** 
   * This class represents the name of a heap variable in the heap array
   * SSA form.
   */
  final class HeapKey {
    /** 
     * The number and type comprise the name of a heap variable in array SSA
     * form 
     */
    int number;
    /** 
     * The number and type comprise the name of a heap variable in array SSA
     * form 
     */
    Object type;

    /**
     * Create a new name for a heap variable.
     * 
     * @param     number the number, a unique integer from SSA renaming
     * @param     type   the type (a <code> VM_Field </code> or <code>
     * VM_TypeReference </code>
     */
    HeapKey (int number, Object type) {
      this.number = number;
      this.type = type;
    }

    /**
     * Test against another key for equality.  This function is used to
     * retrive items from hashtables.
     *
     * @param key the object to compare with
     * @return true or false as appropriate
     */
    public boolean equals (Object key) {
      if (!(key instanceof HeapKey))
        return  false;
      HeapKey k = (HeapKey)key;
      return  ((type.equals(k.type)) && (number == k.number));
    }

    /**
     * Return a hash code for this name.
     *
     * TODO: come up with a better hash function.
     *
     * @return the hash code
     */
    public int hashCode () {
      return  type.hashCode() + 8192*number;
    }
    ;}

  /** 
   * This class implements an <code> Enumeration </code> over all 
   * instructions for a basic block. This enumeration includes 
   * explicit instructions in the IR and implicit phi instructions
   * for heap variables, which are stored only in this lookaside
   * structure.*/

  final class AllInstructionEnumeration
    implements Enumeration {

    /**
     * Construct an enumeration for all instructions, both implicit and
     * explicit in the IR, for a given basic block
     *
     * @param     bb the basic block whose instructions this enumerates
     */
    AllInstructionEnumeration (OPT_BasicBlock bb) {
      explicitInstructions = bb.forwardInstrEnumerator();
      implicitInstructions = getHeapPhiInstructions(bb);
      labelInstruction = explicitInstructions.nextElement();
    }

    /**
     * Are there more elements in the enumeration?
     *
     * @return true or false
     */
    public boolean hasMoreElements () {
      return  (implicitInstructions.hasMoreElements() 
               || explicitInstructions.hasMoreElements());
    }

    /**
     * Get the next instruction in the enumeration
     *
     * @return the next instruction
     */
    public Object nextElement () {
      if (labelInstruction != null) {
        Object temp = labelInstruction;
        labelInstruction = null;
        return  temp;
      }
      if (implicitInstructions.hasMoreElements())
        return  implicitInstructions.nextElement();
      return  explicitInstructions.nextElement();
    }

    /**
     * An enumeration of the explicit instructions in the IR for a basic
     * block
     */
    private Enumeration explicitInstructions;

    /**
     * An enumeration of the implicit instructions in the IR for a basic
     * block.  These instructions appear only in the SSA dictionary
     * lookaside structure.
     */
    private Enumeration implicitInstructions;

    /**
     * The label instruction for the basic block
     */
    private Object labelInstruction;
  }
}
