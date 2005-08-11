/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.classloader.*;
import com.ibm.JikesRVM.opt.ir.*;
import java.util.*;
import com.ibm.JikesRVM.opt.OPT_IndexPropagation.*;

/**
 * This class implements the redundant load elimination by
 * Fink, Knobe && Sarkar.  See SAS 2000 paper for details.
 *
 * @author Stephen Fink
 */
final class OPT_LoadElimination extends
OPT_OptimizationPlanCompositeElement implements OPT_Operators {

  /**
   * @param round which round of load elimination is this?
   */
  OPT_LoadElimination(int round) {
    super("Load Elimination", new OPT_OptimizationPlanElement[] {
          new OPT_OptimizationPlanAtomicElement(new GVNPreparation(round)),
          new OPT_OptimizationPlanAtomicElement(new OPT_EnterSSA()),
          new OPT_OptimizationPlanAtomicElement(new OPT_GlobalValueNumber()),
          new OPT_OptimizationPlanAtomicElement(new LoadEliminationPreparation(round)),
          new OPT_OptimizationPlanAtomicElement(new OPT_EnterSSA()),
          new OPT_OptimizationPlanAtomicElement(new OPT_IndexPropagation()),
          new OPT_OptimizationPlanAtomicElement(new LoadEliminator())
          });
    this.round = round;
  }

  static final boolean DEBUG = false;

  public final boolean shouldPerform(OPT_Options options) {
    return options.LOAD_ELIMINATION;
  }

  public final String getName() {
    return  "Array SSA Load Elimination";
  }

  /**
   * which round of load elimination is this?
   */
  private int round;

  final static class LoadEliminator extends OPT_CompilerPhase implements OPT_Operators{
    public final String getName() {
      return  "Load Eliminator";
    }

    /** 
     * main driver for redundant load elimination 
     * Preconditions: Array SSA form and Global Value Numbers computed
     * @param ir the governing IR
     */
    final public void perform(OPT_IR ir) {

      // Horrible kludge to work around defect 1213816.
      // Disable load elimination on IA32 for a specific method in _227_mtrt that
      // causes some piece of our intel backend to go awry and generate code
      // that causes the program to hang.
      if (VM.BuildForIA32 && ir.method.toString().indexOf("spec.benchmarks._205_raytrace.OctNode.Intersect") != -1) {
		  if (ir.options.PRINT_METHOD) VM.sysWriteln("OPT_LoadElimination disabled to kludge around defect 1213816");
		  return;
	  }


      if (ir.desiredSSAOptions.getAbort()) return;

      boolean didSomething = eliminateLoads(ir, ir.HIRInfo.indexPropagationSolution);
      // Note that SSA is no longer valid!!!
      // This will force construction of SSA next time we call EnterSSA
      ir.actualSSAOptions.setScalarValid(false);
      ir.actualSSAOptions.setHeapValid(false);
      ir.HIRInfo.loadEliminationDidSomething = didSomething;

      // clear the following field to avoid excess memory retention
      ir.HIRInfo.indexPropagationSolution = null;
    }
  }

  /** 
   * Eliminate redundant loads with respect to prior defs and prior
   * uses.
   *
   * @return true if any load is eliminated.
   */
  final static boolean eliminateLoads(OPT_IR ir, OPT_DF_Solution available) {
    // maintain a mapping from value number to temporary register
    HashMap registers = new HashMap();
    UseRecordSet UseRepSet = replaceLoads(ir, available, registers);
    replaceDefs(ir, UseRepSet, registers);

    return (UseRepSet.size() > 0);
  }

  /**
   * Walk over each instruction.  If its a USE (load) of a heap
   * variable and the value is available, then replace the load
   * with a move from a register.
   *
   * POSTCONDITION: sets up the mapping 'registers' from value number
   *                 to temporary register
   * @param ir the IR
   * @param available information on which values are available
   * @param registers a place to store information about temp registers
   */
  final static UseRecordSet replaceLoads(OPT_IR ir, OPT_DF_Solution available, 
                                          HashMap registers) {
    UseRecordSet result = new UseRecordSet();
    OPT_SSADictionary ssa = ir.HIRInfo.SSADictionary;
    OPT_GlobalValueNumberState valueNumbers = ir.HIRInfo.valueNumbers;
    for (Enumeration e = ir.forwardInstrEnumerator(); e.hasMoreElements();) {
      OPT_Instruction s = (OPT_Instruction)e.nextElement();
      if (!GetField.conforms(s) 
          && !GetStatic.conforms(s) 
          && !ALoad.conforms(s))
        continue;
      // this instruction is a USE of heap variable H.
      // get the lattice cell that holds the available indices
      // for this heap variable
      OPT_HeapOperand[] H = ssa.getHeapUses(s);
      if (H == null) {
        // this case can happen due to certain magics that insert
        // INT_LOAD instructions in HIR
        // TODO: clean up HIR representation of these magics
        continue;
      }
      if (H.length != 1)
        throw  new OPT_OptimizingCompilerException("OPT_LoadElimination: load with wrong number of heap uses");
      if (GetField.conforms(s) || GetStatic.conforms(s)) {
        int valueNumber = -1;
        if (GetField.conforms(s)) {
          Object address = GetField.getRef(s);
          valueNumber = valueNumbers.getValueNumber(address);
        } 
        else {
          // for getStatic, always use the value number 0
          valueNumber = 0;
        }
        ObjectCell cell = (ObjectCell)available.lookup(H[0].getHeapVariable());
        if (cell == null)
          continue;           // nothing available

        // .. if H{valueNumber} is available ...
        if (cell.contains(valueNumber)) {
          result.add(H[0].getHeapVariable(), valueNumber);
          VM_TypeReference type = ResultCarrier.getResult(s).getType();
          OPT_Register r = findOrCreateRegister(H[0].getHeapType(), 
                                                valueNumber, registers, ir.regpool, type);
          if (DEBUG)
            System.out.println("ELIMINATING LOAD " + s);
          replaceLoadWithMove(r, s);
        }
      } else {                  // ALoad.conforms(s)
        Object array = ALoad.getArray(s);
        Object index = ALoad.getIndex(s);
        ArrayCell cell = (ArrayCell)available.lookup(H[0].getHeapVariable());
        if (cell == null)
          continue;           // nothing available
        int v1 = valueNumbers.getValueNumber(array);
        int v2 = valueNumbers.getValueNumber(index);
        // .. if H{<v1,v2>} is available ...
        if (cell.contains(v1, v2)) {
          result.add(H[0].getHeapVariable(), v1, v2);
          VM_TypeReference type = ALoad.getResult(s).getType();
          OPT_Register r = findOrCreateRegister(H[0].getHeapVariable().getHeapType(), 
                                                v1, v2, registers, ir.regpool, type);
          if (DEBUG)
            System.out.println("ELIMINATING LOAD " + s);
          replaceLoadWithMove(r, s);
        }
      }
    }
    return  result;
  }

  /**
   * Replace a Load instruction s with a load from a scalar register r
   * TODO: factor this functionality out elsewhere
   */
  static void replaceLoadWithMove(OPT_Register r, OPT_Instruction load) {
    OPT_RegisterOperand dest = ResultCarrier.getResult(load);
    OPT_RegisterOperand rop = new OPT_RegisterOperand(r, dest.type);
    load.replace(Move.create(OPT_IRTools.getMoveOp(dest.type), 
                             dest, rop));
  }

  /**
   * Perform scalar replacement actions for a Def of a heap variable.
   * NOTE: Even loads can def a heap variable.
   *
   * @param UseRepSet stores the uses(loads) that have been eliminated
   * @param registers mapping from valueNumber -> temporary register
   */
  final static void replaceDefs(OPT_IR ir, UseRecordSet UseRepSet, 
                                 HashMap registers) {
    OPT_SSADictionary ssa = ir.HIRInfo.SSADictionary;
    for (Enumeration e = ir.forwardInstrEnumerator(); e.hasMoreElements();) {
      OPT_Instruction s = (OPT_Instruction)e.nextElement();
      if (!GetField.conforms(s) && !GetStatic.conforms(s) 
          && !PutField.conforms(s)
          && !PutStatic.conforms(s) && !ALoad.conforms(s) 
          && !AStore.conforms(s))
        continue;
      if (!ssa.defsHeapVariable(s))
        continue;
      // this instruction is a DEF of heap variable H.
      // Check if UseRepSet needs the scalar assigned by this def
      OPT_HeapOperand H[] = ssa.getHeapDefs(s);
      if (H.length != 1)
        throw  new OPT_OptimizingCompilerException("OPT_LoadElimination: encountered a store with more than one def? "
                                                   + s);
      int valueNumber = -1;
      Object index = null;
      if (AStore.conforms(s)) {
        Object address = AStore.getArray(s);
        index = AStore.getIndex(s);
        valueNumber = ir.HIRInfo.valueNumbers.getValueNumber(address);
      } 
      else if (GetField.conforms(s)) {
        Object address = GetField.getRef(s);
        valueNumber = ir.HIRInfo.valueNumbers.getValueNumber(address);
      } 
      else if (PutField.conforms(s)) {
        Object address = PutField.getRef(s);
        valueNumber = ir.HIRInfo.valueNumbers.getValueNumber(address);
      } 
      else if (GetStatic.conforms(s)) {
        valueNumber = 0;
      } 
      else if (PutStatic.conforms(s)) {
        valueNumber = 0;
      } 
      else if (ALoad.conforms(s)) {
        Object address = ALoad.getArray(s);
        valueNumber = ir.HIRInfo.valueNumbers.getValueNumber(address);
        index = ALoad.getIndex(s);
      }
      if (index == null) {
        // Load/Store
        if (UseRepSet.containsMatchingUse(H[0].getHeapVariable(), valueNumber)) {
          OPT_Operand value = null;
          if (PutField.conforms(s))
            value = PutField.getValue(s); 
          else if (PutStatic.conforms(s))
            value = PutStatic.getValue(s); 
          else if (GetField.conforms(s) || GetStatic.conforms(s))
            value = ResultCarrier.getResult(s);
          VM_TypeReference type = value.getType();
          OPT_Register r = findOrCreateRegister(H[0].getHeapType(), 
                                                valueNumber, registers, ir.regpool, type);
          appendMove(r, value, s);
        }
      } 
      else {
        // ALoad / AStore
        int v1 = valueNumber;
        int v2 = ir.HIRInfo.valueNumbers.getValueNumber(index);
        if (UseRepSet.containsMatchingUse(H[0].getHeapVariable(), v1, v2)) {
          OPT_Operand value = null;
          if (AStore.conforms(s))
            value = AStore.getValue(s); 
          else if (ALoad.conforms(s))
            value = ALoad.getResult(s);
          VM_TypeReference type = value.getType();
          OPT_Register r = findOrCreateRegister(H[0].getHeapType(), 
                                                v1, v2, registers, ir.regpool, type);
          appendMove(r, value, s);
        }
      }
    }
  }

  /**
   * Append an instruction after a store instruction that caches
   * value in register r.
   */
  static void appendMove(OPT_Register r, OPT_Operand src, OPT_Instruction store) {
    VM_TypeReference type = src.getType();
    OPT_RegisterOperand rop = new OPT_RegisterOperand(r, type);
    store.insertAfter(Move.create(OPT_IRTools.getMoveOp(type), 
                                  rop, src));
  }

  /** 
   * Given a value number, return the temporary register allocated
   * for that value number.  Create one if necessary.
   *
   * @param heapType a VM_TypeReference or VM_Field identifying the array SSA
   *                    heap type
   * @param valueNumber
   * @param registers a mapping from value number to temporary register
   * @param pool register pool to allocate new temporaries from
   * @param type the type to store in the new register
   */
  static OPT_Register findOrCreateRegister(Object heapType, int valueNumber, 
                                            HashMap registers, 
                                            OPT_RegisterPool pool, VM_TypeReference type) {
    UseRecord key = new UseRecord(heapType, valueNumber);
    OPT_Register result = (OPT_Register)registers.get(key);
    if (result == null) {
      // create a new temp and insert it in the mapping
      result = pool.makeTemp(type).register;
      registers.put(key, result);
    }
    return  result;
  }

  /** 
   * Given a pair of value numbers, return the temporary register 
   * allocated for that pair.  Create one if necessary.
   *
   * @param heapType a VM_TypeReference identifying the array SSA
   *                    heap type
   * @param v1 first value number
   * @param v2 second value number
   * @param registers a mapping from value number to temporary register
   * @param pool register pool to allocate new temporaries from
   * @param type the type to store in the new register
   */
  static OPT_Register findOrCreateRegister(Object heapType, int v1, int v2, 
                                           HashMap registers, OPT_RegisterPool pool, 
                                           VM_TypeReference type) {
    UseRecord key = new UseRecord(heapType, v1, v2);
    OPT_Register result = (OPT_Register)registers.get(key);
    if (result == null) {
      // create a new temp and insert it in the mapping
      result = pool.makeTemp(type).register;
      registers.put(key, result);
    }
    return  result;
  }

  // A UseRecord represents a load that will be eliminated
  static class UseRecord {
    Object type;              // may be either a VM_TypeReference or a VM_Field
    int v1;                   // first value number (object pointer)
    int v2;                   // second value number (array index)
    final static int NONE = -2;

    UseRecord(Object type, int valueNumber) {
      this.type = type;
      this.v1 = valueNumber;
      this.v2 = NONE;
    }

    UseRecord(Object type, int v1, int v2) {
      this.type = type;
      this.v1 = v1;
      this.v2 = v2;
    }

    public boolean equals(Object o) {
      UseRecord u = (UseRecord)o;
      return  ((u.type.equals(type)) && (u.v1 == v1) && (u.v2 == v2));
    }

    public int hashCode() {
      return  type.hashCode() + v1 + v2;
    }
  }

  static class UseRecordSet {
    HashSet set = new HashSet(10);

    // Does this set contain a use that has the same type as H and
    // the given value number?
    boolean containsMatchingUse(OPT_HeapVariable H, int valueNumber) {
      Object type = H.getHeapType();
      UseRecord u = new UseRecord(type, valueNumber);
      return  (set.contains(u));
    }

    // Does this set contain a use that has the same type as H and
    // the given value number pair <v1,v2>?
    boolean containsMatchingUse(OPT_HeapVariable H, int v1, int v2) {
      Object type = H.getHeapType();
      UseRecord u = new UseRecord(type, v1, v2);
      return  (set.contains(u));
    }

    // add a USE to the set
    void add(OPT_HeapVariable H, int valueNumber) {
      UseRecord u = new UseRecord(H.getHeapType(), valueNumber);
      set.add(u);
    }

    void add(OPT_HeapVariable H, int v1, int v2) {
      UseRecord u = new UseRecord(H.getHeapType(), v1, v2);
      set.add(u);
    }

    int size() { return set.size(); }
  }

  /**
   * @param map a mapping from key to HashSet
   * @param key a key into the map
   * @return the set map(key).  create one if none exists.
   */
  private final static HashSet findOrCreateIndexSet(HashMap map, Object key) {
    HashSet result = (HashSet)map.get(key);
    if (result == null) {
      result = new HashSet(5);
      map.put(key,result);
    }
    return result;
  }
  /**
   * Do a quick pass over the IR, and return types that are candidates
   * for redundant load elimination.
   * Algorithm: return those types T where
   *    1) there's a load L(i) of type T 
   *    2) there's another load or store M(j) of type T, M!=L and V(i) == V(j)
   */
  final public static Set getCandidates(OPT_IR ir) {
    OPT_GlobalValueNumberState valueNumbers = ir.HIRInfo.valueNumbers;
    // which types have we seen loads for?
    HashSet seenLoad = new HashSet(10);
    // which static fields have we seen stores for?
    HashSet seenStore = new HashSet(10);
    HashSet resultSet = new HashSet(10);
    HashSet forbidden = new HashSet(10);
    // for each type T, indices(T) gives the set of value number (pairs)
    // that identify the indices seen in memory accesses to type T.
    HashMap indices = new HashMap(10);

    for (Enumeration be = ir.getBasicBlocks(); be.hasMoreElements(); ) {
      OPT_BasicBlock bb = (OPT_BasicBlock)be.nextElement();
      if (!ir.options.FREQ_FOCUS_EFFORT || !bb.getInfrequent()) {
        for (OPT_InstructionEnumeration e = bb.forwardInstrEnumerator(); e.hasMoreElements();) {
          OPT_Instruction s = e.next();
          switch (s.operator().opcode) {
            case GETFIELD_opcode:
              {
                OPT_Operand ref = GetField.getRef(s);
                VM_FieldReference fr = GetField.getLocation(s).getFieldRef();
                VM_Field f = fr.peekResolvedField();
                if (f == null) {
                  forbidden.add(fr);
                } else {
                  HashSet numbers = findOrCreateIndexSet(indices,f);
                  int v = valueNumbers.getValueNumber(ref);
                  Integer V = new Integer(v);
                  if (numbers.contains(V)) {
                    resultSet.add(f);
                  } else {
                    numbers.add(V);
                  }
                  seenLoad.add(f);
                }
              }
              break;
            case PUTFIELD_opcode:
              {
                OPT_Operand ref = PutField.getRef(s);
                VM_FieldReference fr = PutField.getLocation(s).getFieldRef();
                VM_Field f = fr.peekResolvedField();
                if (f == null) {
                  forbidden.add(fr);
                } else {
                  HashSet numbers = findOrCreateIndexSet(indices,f);
                  int v = valueNumbers.getValueNumber(ref);
                  Integer V = new Integer(v);
                  if (numbers.contains(V)) {
                    if (seenLoad.contains(f)) {
                      resultSet.add(f);
                    }
                  } else {
                    numbers.add(V);
                  }
                }
              }
              break;
            case GETSTATIC_opcode:
              {
                VM_FieldReference fr = GetStatic.getLocation(s).getFieldRef();
                VM_Field f = fr.peekResolvedField();
                if (f == null) {
                  forbidden.add(fr);
                } else {
                  if (seenLoad.contains(f) || seenStore.contains(f)) {
                    resultSet.add(f);
                  }
                  seenLoad.add(f);
                }
              }
              break;
            case PUTSTATIC_opcode:
              {
                VM_FieldReference fr = PutStatic.getLocation(s).getFieldRef();
                VM_Field f = fr.peekResolvedField();
                if (f == null) {
                  forbidden.add(fr);
                } else {
                  if (seenLoad.contains(f)) {
                    resultSet.add(f);
                  }
                  seenStore.add(f);
                }
              }
              break;
            case INT_ALOAD_opcode:case LONG_ALOAD_opcode:case FLOAT_ALOAD_opcode:
            case DOUBLE_ALOAD_opcode:case REF_ALOAD_opcode:case BYTE_ALOAD_opcode:
            case UBYTE_ALOAD_opcode:case USHORT_ALOAD_opcode:case SHORT_ALOAD_opcode:
              {
                OPT_Operand ref = ALoad.getArray(s);
                VM_TypeReference type = ref.getType();
                if (type.isArrayType()) {
                  if (!type.getArrayElementType().isPrimitiveType()) {
                    type = VM_TypeReference.JavaLangObjectArray;
                  }
                }
                OPT_Operand index = ALoad.getIndex(s);

                HashSet numbers = findOrCreateIndexSet(indices, type);
                int v1 = valueNumbers.getValueNumber(ref);
                int v2 = valueNumbers.getValueNumber(index);
                OPT_ValueNumberPair V = new OPT_ValueNumberPair(v1,v2);
                if (numbers.contains(V)) {
                  resultSet.add(type);
                } else {
                  numbers.add(V);
                }
                seenLoad.add(type);
              }

              break;

            case INT_ASTORE_opcode:case LONG_ASTORE_opcode:
            case FLOAT_ASTORE_opcode:case DOUBLE_ASTORE_opcode:
            case REF_ASTORE_opcode:case BYTE_ASTORE_opcode:
            case SHORT_ASTORE_opcode:

              {
                OPT_Operand ref = AStore.getArray(s);
                VM_TypeReference type = ref.getType();
                if (type.isArrayType()) {
                  if (!type.getArrayElementType().isPrimitiveType()) {
                    type = VM_TypeReference.JavaLangObjectArray;
                  }
                }
                OPT_Operand index = AStore.getIndex(s);

                HashSet numbers = findOrCreateIndexSet(indices,type);
                int v1 = valueNumbers.getValueNumber(ref);
                int v2 = valueNumbers.getValueNumber(index);
                OPT_ValueNumberPair V = new OPT_ValueNumberPair(v1,v2);

                if (numbers.contains(V)) {
                  if (seenLoad.contains(type)) {
                    resultSet.add(type);
                  }
                } else {
                  numbers.add(V);
                }
              }
              break;

            default:
              break;
          }
        }
      }
    }

    // If we have found an unresolved field reference, then conservatively
    // remove all fields that it might refer to from the resultSet.
    for (Iterator i1 = forbidden.iterator(); i1.hasNext(); ) {
      VM_FieldReference forbid = (VM_FieldReference)i1.next();
      for (Iterator i2 = resultSet.iterator(); i2.hasNext(); ) {
        Object it = i2.next();
        if (it instanceof VM_Field) {
          if (!forbid.definitelyDifferent(((VM_Field)it).getMemberRef().asFieldReference())) {
            i2.remove();
          }
        }
      }
    }

    return resultSet;
  }

  /**
   * This class sets up the IR state prior to entering SSA for load
   * elimination
   */
  private static class LoadEliminationPreparation extends OPT_CompilerPhase {

    public final boolean shouldPerform(OPT_Options options) {
      return  options.LOAD_ELIMINATION;
    }

    public final String getName() {
      return  "Load Elimination Preparation";
    }

    private final int round;

    LoadEliminationPreparation(int round) {
      this.round = round;
    }

    final public void perform(OPT_IR ir) {
      // register in the IR the SSA properties we need for load
      // elimination
      ir.desiredSSAOptions = new OPT_SSAOptions();
      ir.desiredSSAOptions.setScalarsOnly(false);
      ir.desiredSSAOptions.setBackwards(false);
      ir.desiredSSAOptions.setInsertUsePhis(true);
      ir.desiredSSAOptions.setHeapTypes(OPT_LoadElimination.getCandidates(ir));
      ir.desiredSSAOptions.setAbort((round > ir.options.LOAD_ELIMINATION_ROUNDS) 
                                    || (ir.HIRInfo.loadEliminationDidSomething == false));
    }
  }
  /**
   * This class sets up the IR state prior to entering SSA for GVN.
   */
  private static class GVNPreparation extends OPT_CompilerPhase {

    public final boolean shouldPerform(OPT_Options options) {
      return  options.LOAD_ELIMINATION;
    }

    public final String getName() {
      return  "GVN Preparation";
    }

    private final int round;

    GVNPreparation(int round) {
      this.round = round;
    }

    final public void perform(OPT_IR ir) {
      // register in the IR the SSA properties we need for load
      // elimination
      ir.desiredSSAOptions = new OPT_SSAOptions();
      ir.desiredSSAOptions.setScalarsOnly(true);
      ir.desiredSSAOptions.setBackwards(false);
      ir.desiredSSAOptions.setInsertUsePhis(false);
      ir.desiredSSAOptions.setAbort((round > ir.options.LOAD_ELIMINATION_ROUNDS) 
                                    || (ir.HIRInfo.loadEliminationDidSomething == false));
    }
  }
}
