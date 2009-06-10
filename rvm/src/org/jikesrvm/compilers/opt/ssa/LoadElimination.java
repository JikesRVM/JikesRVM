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
package org.jikesrvm.compilers.opt.ssa;

import static org.jikesrvm.compilers.opt.ir.Operators.BYTE_ALOAD_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.BYTE_ASTORE_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.DOUBLE_ALOAD_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.DOUBLE_ASTORE_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.FLOAT_ALOAD_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.FLOAT_ASTORE_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.GETFIELD_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.GETSTATIC_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.INT_ALOAD_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.INT_ASTORE_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.LONG_ALOAD_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.LONG_ASTORE_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.PUTFIELD_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.PUTSTATIC_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.REF_ALOAD_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.REF_ASTORE_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.SHORT_ALOAD_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.SHORT_ASTORE_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.UBYTE_ALOAD_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.USHORT_ALOAD_opcode;

import java.lang.reflect.Constructor;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;

import org.jikesrvm.ArchitectureSpecificOpt.RegisterPool;
import org.jikesrvm.classloader.RVMField;
import org.jikesrvm.classloader.FieldReference;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.compilers.opt.OptOptions;
import org.jikesrvm.compilers.opt.OptimizingCompilerException;
import org.jikesrvm.compilers.opt.dfsolver.DF_Solution;
import org.jikesrvm.compilers.opt.driver.CompilerPhase;
import org.jikesrvm.compilers.opt.driver.OptimizationPlanAtomicElement;
import org.jikesrvm.compilers.opt.driver.OptimizationPlanCompositeElement;
import org.jikesrvm.compilers.opt.driver.OptimizationPlanElement;
import org.jikesrvm.compilers.opt.ir.ALoad;
import org.jikesrvm.compilers.opt.ir.AStore;
import org.jikesrvm.compilers.opt.ir.BasicBlock;
import org.jikesrvm.compilers.opt.ir.GetField;
import org.jikesrvm.compilers.opt.ir.GetStatic;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.ir.IRTools;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ir.InstructionEnumeration;
import org.jikesrvm.compilers.opt.ir.Move;
import org.jikesrvm.compilers.opt.ir.PutField;
import org.jikesrvm.compilers.opt.ir.PutStatic;
import org.jikesrvm.compilers.opt.ir.Register;
import org.jikesrvm.compilers.opt.ir.ResultCarrier;
import org.jikesrvm.compilers.opt.ir.operand.HeapOperand;
import org.jikesrvm.compilers.opt.ir.operand.Operand;
import org.jikesrvm.compilers.opt.ir.operand.RegisterOperand;
import org.jikesrvm.compilers.opt.ssa.IndexPropagation.ArrayCell;
import org.jikesrvm.compilers.opt.ssa.IndexPropagation.ObjectCell;

/**
 * This class implements the redundant load elimination by
 * Fink, Knobe && Sarkar.  See SAS 2000 paper for details.
 */
public final class LoadElimination extends OptimizationPlanCompositeElement {

  /**
   * @param round which round of load elimination is this?
   */
  public LoadElimination(int round) {
    super("Load Elimination",
          new OptimizationPlanElement[]{new OptimizationPlanAtomicElement(new GVNPreparation(round)),
                                            new OptimizationPlanAtomicElement(new EnterSSA()),
                                            new OptimizationPlanAtomicElement(new GlobalValueNumber()),
                                            new OptimizationPlanAtomicElement(new LoadEliminationPreparation(round)),
                                            new OptimizationPlanAtomicElement(new EnterSSA()),
                                            new OptimizationPlanAtomicElement(new IndexPropagation()),
                                            new OptimizationPlanAtomicElement(new LoadEliminator())});
    this.round = round;
  }

  static final boolean DEBUG = false;

  public boolean shouldPerform(OptOptions options) {
    return options.SSA_LOAD_ELIMINATION;
  }

  public String getName() {
    return "Array SSA Load Elimination, round " + round;
  }

  /**
   * which round of load elimination is this?
   */
  private final int round;

  static final class LoadEliminator extends CompilerPhase {
    public String getName() {
      return "Load Eliminator";
    }

    /**
     * Return this instance of this phase. This phase contains no
     * per-compilation instance fields.
     * @param ir not used
     * @return this
     */
    public CompilerPhase newExecution(IR ir) {
      return this;
    }

    /**
     * main driver for redundant load elimination
     * Preconditions: Array SSA form and Global Value Numbers computed
     * @param ir the governing IR
     */
    public void perform(IR ir) {

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
  static boolean eliminateLoads(IR ir, DF_Solution available) {
    // maintain a mapping from value number to temporary register
    HashMap<UseRecord, Register> registers = new HashMap<UseRecord, Register>();
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
  static UseRecordSet replaceLoads(IR ir, DF_Solution available, HashMap<UseRecord, Register> registers) {
    UseRecordSet result = new UseRecordSet();
    SSADictionary ssa = ir.HIRInfo.dictionary;
    GlobalValueNumberState valueNumbers = ir.HIRInfo.valueNumbers;
    for (Enumeration<Instruction> e = ir.forwardInstrEnumerator(); e.hasMoreElements();) {
      Instruction s = e.nextElement();
      if (!GetField.conforms(s) && !GetStatic.conforms(s) && !ALoad.conforms(s)) {
        continue;
      }
      // this instruction is a USE of heap variable H.
      // get the lattice cell that holds the available indices
      // for this heap variable
      HeapOperand<?>[] H = ssa.getHeapUses(s);
      if (H == null) {
        // this case can happen due to certain magics that insert
        // INT_LOAD instructions in HIR
        // TODO: clean up HIR representation of these magics
        continue;
      }
      if (H.length != 1) {
        throw new OptimizingCompilerException("LoadElimination: load with wrong number of heap uses");
      }
      if (GetField.conforms(s) || GetStatic.conforms(s)) {
        int valueNumber = -1;
        if (GetField.conforms(s)) {
          Object address = GetField.getRef(s);
          valueNumber = valueNumbers.getValueNumber(address);
        } else {
          // for getStatic, always use the value number 0
          valueNumber = 0;
        }
        ObjectCell cell = (ObjectCell) available.lookup(H[0].getHeapVariable());
        if (cell == null) {
          continue;           // nothing available
        }

        // .. if H{valueNumber} is available ...
        if (cell.contains(valueNumber)) {
          result.add(H[0].getHeapVariable(), valueNumber);
          TypeReference type = ResultCarrier.getResult(s).getType();
          Register r = findOrCreateRegister(H[0].getHeapType(), valueNumber, registers, ir.regpool, type);
          if (DEBUG) {
            System.out.println("ELIMINATING LOAD " + s);
          }
          replaceLoadWithMove(r, s);
        }
      } else {                  // ALoad.conforms(s)
        Object array = ALoad.getArray(s);
        Object index = ALoad.getIndex(s);
        ArrayCell cell = (ArrayCell) available.lookup(H[0].getHeapVariable());
        if (cell == null) {
          continue;           // nothing available
        }
        int v1 = valueNumbers.getValueNumber(array);
        int v2 = valueNumbers.getValueNumber(index);
        // .. if H{<v1,v2>} is available ...
        if (cell.contains(v1, v2)) {
          result.add(H[0].getHeapVariable(), v1, v2);
          TypeReference type = ALoad.getResult(s).getType();
          Register r =
              findOrCreateRegister(H[0].getHeapVariable().getHeapType(), v1, v2, registers, ir.regpool, type);
          if (DEBUG) {
            System.out.println("ELIMINATING LOAD " + s);
          }
          replaceLoadWithMove(r, s);
        }
      }
    }
    return result;
  }

  /**
   * Replace a Load instruction s with a load from a scalar register r
   * TODO: factor this functionality out elsewhere
   */
  static void replaceLoadWithMove(Register r, Instruction load) {
    RegisterOperand dest = ResultCarrier.getResult(load);
    RegisterOperand rop = new RegisterOperand(r, dest.getType());
    load.replace(Move.create(IRTools.getMoveOp(dest.getType()), dest, rop));
  }

  /**
   * Perform scalar replacement actions for a Def of a heap variable.
   * NOTE: Even loads can def a heap variable.
   *
   * @param UseRepSet stores the uses(loads) that have been eliminated
   * @param registers mapping from valueNumber -> temporary register
   */
  static void replaceDefs(IR ir, UseRecordSet UseRepSet, HashMap<UseRecord, Register> registers) {
    SSADictionary ssa = ir.HIRInfo.dictionary;
    for (Enumeration<Instruction> e = ir.forwardInstrEnumerator(); e.hasMoreElements();) {
      Instruction s = e.nextElement();
      if (!GetField.conforms(s) &&
          !GetStatic.conforms(s) &&
          !PutField.conforms(s) &&
          !PutStatic.conforms(s) &&
          !ALoad.conforms(s) &&
          !AStore.conforms(s)) {
        continue;
      }
      if (!ssa.defsHeapVariable(s)) {
        continue;
      }
      // this instruction is a DEF of heap variable H.
      // Check if UseRepSet needs the scalar assigned by this def
      HeapOperand<?>[] H = ssa.getHeapDefs(s);
      if (H.length != 1) {
        throw new OptimizingCompilerException("LoadElimination: encountered a store with more than one def? " +
                                                  s);
      }
      int valueNumber = -1;
      Object index = null;
      if (AStore.conforms(s)) {
        Object address = AStore.getArray(s);
        index = AStore.getIndex(s);
        valueNumber = ir.HIRInfo.valueNumbers.getValueNumber(address);
      } else if (GetField.conforms(s)) {
        Object address = GetField.getRef(s);
        valueNumber = ir.HIRInfo.valueNumbers.getValueNumber(address);
      } else if (PutField.conforms(s)) {
        Object address = PutField.getRef(s);
        valueNumber = ir.HIRInfo.valueNumbers.getValueNumber(address);
      } else if (GetStatic.conforms(s)) {
        valueNumber = 0;
      } else if (PutStatic.conforms(s)) {
        valueNumber = 0;
      } else if (ALoad.conforms(s)) {
        Object address = ALoad.getArray(s);
        valueNumber = ir.HIRInfo.valueNumbers.getValueNumber(address);
        index = ALoad.getIndex(s);
      }
      if (index == null) {
        // Load/Store
        if (UseRepSet.containsMatchingUse(H[0].getHeapVariable(), valueNumber)) {
          Operand value = null;
          if (PutField.conforms(s)) {
            value = PutField.getValue(s);
          } else if (PutStatic.conforms(s)) {
            value = PutStatic.getValue(s);
          } else if (GetField.conforms(s) || GetStatic.conforms(s)) {
            value = ResultCarrier.getResult(s);
          }
          TypeReference type = value.getType();
          Register r = findOrCreateRegister(H[0].getHeapType(), valueNumber, registers, ir.regpool, type);
          appendMove(r, value, s);
        }
      } else {
        // ALoad / AStore
        int v1 = valueNumber;
        int v2 = ir.HIRInfo.valueNumbers.getValueNumber(index);
        if (UseRepSet.containsMatchingUse(H[0].getHeapVariable(), v1, v2)) {
          Operand value = null;
          if (AStore.conforms(s)) {
            value = AStore.getValue(s);
          } else if (ALoad.conforms(s)) {
            value = ALoad.getResult(s);
          }
          TypeReference type = value.getType();
          Register r = findOrCreateRegister(H[0].getHeapType(), v1, v2, registers, ir.regpool, type);
          appendMove(r, value, s);
        }
      }
    }
  }

  /**
   * Append an instruction after a store instruction that caches
   * value in register r.
   */
  static void appendMove(Register r, Operand src, Instruction store) {
    TypeReference type = src.getType();
    RegisterOperand rop = new RegisterOperand(r, type);
    store.insertAfter(Move.create(IRTools.getMoveOp(type), rop, src.copy()));
  }

  /**
   * Given a value number, return the temporary register allocated
   * for that value number.  Create one if necessary.
   *
   * @param heapType a TypeReference or RVMField identifying the array SSA
   *                    heap type
   * @param valueNumber
   * @param registers a mapping from value number to temporary register
   * @param pool register pool to allocate new temporaries from
   * @param type the type to store in the new register
   */
  static Register findOrCreateRegister(Object heapType, int valueNumber, HashMap<UseRecord, Register> registers,
                                           RegisterPool pool, TypeReference type) {
    UseRecord key = new UseRecord(heapType, valueNumber);
    Register result = registers.get(key);
    if (result == null) {
      // create a new temp and insert it in the mapping
      result = pool.makeTemp(type).getRegister();
      registers.put(key, result);
    }
    return result;
  }

  /**
   * Given a pair of value numbers, return the temporary register
   * allocated for that pair.  Create one if necessary.
   *
   * @param heapType a TypeReference identifying the array SSA
   *                    heap type
   * @param v1 first value number
   * @param v2 second value number
   * @param registers a mapping from value number to temporary register
   * @param pool register pool to allocate new temporaries from
   * @param type the type to store in the new register
   */
  static Register findOrCreateRegister(Object heapType, int v1, int v2, HashMap<UseRecord, Register> registers,
                                           RegisterPool pool, TypeReference type) {
    UseRecord key = new UseRecord(heapType, v1, v2);
    Register result = registers.get(key);
    if (result == null) {
      // create a new temp and insert it in the mapping
      result = pool.makeTemp(type).getRegister();
      registers.put(key, result);
    }
    return result;
  }

  // A UseRecord represents a load that will be eliminated
  static class UseRecord {
    final Object type;        // may be either a TypeReference or a RVMField
    final int v1;             // first value number (object pointer)
    final int v2;             // second value number (array index)
    static final int NONE = -2;

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
      if (o instanceof UseRecord) {
        UseRecord u = (UseRecord) o;
        return ((u.type.equals(type)) && (u.v1 == v1) && (u.v2 == v2));
      }
      return false;
    }

    public int hashCode() {
      return type.hashCode() + v1 + v2;
    }
  }

  static final class UseRecordSet {
    final HashSet<UseRecord> set = new HashSet<UseRecord>(10);

    // Does this set contain a use that has the same type as H and
    // the given value number?
    boolean containsMatchingUse(HeapVariable<?> H, int valueNumber) {
      Object type = H.getHeapType();
      UseRecord u = new UseRecord(type, valueNumber);
      return (set.contains(u));
    }

    // Does this set contain a use that has the same type as H and
    // the given value number pair <v1,v2>?
    boolean containsMatchingUse(HeapVariable<?> H, int v1, int v2) {
      Object type = H.getHeapType();
      UseRecord u = new UseRecord(type, v1, v2);
      return (set.contains(u));
    }

    // add a USE to the set
    void add(HeapVariable<?> H, int valueNumber) {
      UseRecord u = new UseRecord(H.getHeapType(), valueNumber);
      set.add(u);
    }

    void add(HeapVariable<?> H, int v1, int v2) {
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
  private static <T> HashSet<T> findOrCreateIndexSet(HashMap<Object, HashSet<T>> map, Object key) {
    HashSet<T> result = map.get(key);
    if (result == null) {
      result = new HashSet<T>(5);
      map.put(key, result);
    }
    return result;
  }

  /**
   * Do a quick pass over the IR, and return types that are candidates
   * for redundant load elimination.
   * Algorithm: return those types T where
   *    1) there's a load L(i) of type T
   *    2) there's another load or store M(j) of type T, M!=L and V(i) == V(j)
   *
   * The result contains objects of type RVMField and TypeReference, whose
   * narrowest common ancestor is Object.
   */
  @SuppressWarnings("unchecked")
  public static HashSet<Object> getCandidates(IR ir) {
    GlobalValueNumberState valueNumbers = ir.HIRInfo.valueNumbers;
    // which types have we seen loads for?
    HashSet<Object> seenLoad = new HashSet<Object>(10);
    // which static fields have we seen stores for?
    HashSet<RVMField> seenStore = new HashSet<RVMField>(10);
    HashSet<Object> resultSet = new HashSet<Object>(10);
    HashSet<FieldReference> forbidden = new HashSet<FieldReference>(10);
    // for each type T, indices(T) gives the set of value number (pairs)
    // that identify the indices seen in memory accesses to type T.
    HashMap indices = new HashMap(10);

    for (Enumeration be = ir.getBasicBlocks(); be.hasMoreElements();) {
      BasicBlock bb = (BasicBlock) be.nextElement();
      if (!ir.options.FREQ_FOCUS_EFFORT || !bb.getInfrequent()) {
        for (InstructionEnumeration e = bb.forwardInstrEnumerator(); e.hasMoreElements();) {
          Instruction s = e.next();
          switch (s.operator().opcode) {
            case GETFIELD_opcode: {
              Operand ref = GetField.getRef(s);
              FieldReference fr = GetField.getLocation(s).getFieldRef();
              RVMField f = fr.peekResolvedField();
              if (f == null) {
                forbidden.add(fr);
              } else {
                HashSet<Integer> numbers = findOrCreateIndexSet(indices, f);
                int v = valueNumbers.getValueNumber(ref);
                Integer V = v;
                if (numbers.contains(V)) {
                  resultSet.add(f);
                } else {
                  numbers.add(V);
                }
                seenLoad.add(f);
              }
            }
            break;
            case PUTFIELD_opcode: {
              Operand ref = PutField.getRef(s);
              FieldReference fr = PutField.getLocation(s).getFieldRef();
              RVMField f = fr.peekResolvedField();
              if (f == null) {
                forbidden.add(fr);
              } else {
                HashSet<Integer> numbers = findOrCreateIndexSet(indices, f);
                int v = valueNumbers.getValueNumber(ref);
                Integer V = v;
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
            case GETSTATIC_opcode: {
              FieldReference fr = GetStatic.getLocation(s).getFieldRef();
              RVMField f = fr.peekResolvedField();
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
            case PUTSTATIC_opcode: {
              FieldReference fr = PutStatic.getLocation(s).getFieldRef();
              RVMField f = fr.peekResolvedField();
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
            case INT_ALOAD_opcode:
            case LONG_ALOAD_opcode:
            case FLOAT_ALOAD_opcode:
            case DOUBLE_ALOAD_opcode:
            case REF_ALOAD_opcode:
            case BYTE_ALOAD_opcode:
            case UBYTE_ALOAD_opcode:
            case USHORT_ALOAD_opcode:
            case SHORT_ALOAD_opcode: {
              Operand ref = ALoad.getArray(s);
              TypeReference type = ref.getType();
              if (type.isArrayType()) {
                if (!type.getArrayElementType().isPrimitiveType()) {
                  type = TypeReference.JavaLangObjectArray;
                }
              }
              Operand index = ALoad.getIndex(s);

              HashSet<ValueNumberPair> numbers = findOrCreateIndexSet(indices, type);
              int v1 = valueNumbers.getValueNumber(ref);
              int v2 = valueNumbers.getValueNumber(index);
              ValueNumberPair V = new ValueNumberPair(v1, v2);
              if (numbers.contains(V)) {
                resultSet.add(type);
              } else {
                numbers.add(V);
              }
              seenLoad.add(type);
            }

            break;

            case INT_ASTORE_opcode:
            case LONG_ASTORE_opcode:
            case FLOAT_ASTORE_opcode:
            case DOUBLE_ASTORE_opcode:
            case REF_ASTORE_opcode:
            case BYTE_ASTORE_opcode:
            case SHORT_ASTORE_opcode:

            {
              Operand ref = AStore.getArray(s);
              TypeReference type = ref.getType();
              if (type.isArrayType()) {
                if (!type.getArrayElementType().isPrimitiveType()) {
                  type = TypeReference.JavaLangObjectArray;
                }
              }
              Operand index = AStore.getIndex(s);

              HashSet<ValueNumberPair> numbers = findOrCreateIndexSet(indices, type);
              int v1 = valueNumbers.getValueNumber(ref);
              int v2 = valueNumbers.getValueNumber(index);
              ValueNumberPair V = new ValueNumberPair(v1, v2);

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
    for (final FieldReference fieldReference : forbidden) {
      for (Iterator i2 = resultSet.iterator(); i2.hasNext();) {
        Object it = i2.next();
        if (it instanceof RVMField) {
          final RVMField field = (RVMField) it;
          if (!fieldReference.definitelyDifferent(field.getMemberRef().asFieldReference())) {
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
  private static class LoadEliminationPreparation extends CompilerPhase {
    /**
     * Cosntructor
     */
    public LoadEliminationPreparation(int round) {
      super(new Object[]{round});
      this.round = round;
    }

    /**
     * Constructor for this compiler phase
     */
    private static final Constructor<CompilerPhase> constructor =
        getCompilerPhaseConstructor(LoadEliminationPreparation.class, new Class[]{Integer.TYPE});

    /**
     * Get a constructor object for this compiler phase
     * @return compiler phase constructor
     */
    public Constructor<CompilerPhase> getClassConstructor() {
      return constructor;
    }

    public final boolean shouldPerform(OptOptions options) {
      return options.SSA_LOAD_ELIMINATION;
    }

    public final String getName() {
      return "Load Elimination Preparation";
    }

    private final int round;

    public final void perform(IR ir) {
      // register in the IR the SSA properties we need for load
      // elimination
      ir.desiredSSAOptions = new SSAOptions();
      ir.desiredSSAOptions.setScalarsOnly(false);
      ir.desiredSSAOptions.setBackwards(false);
      ir.desiredSSAOptions.setInsertUsePhis(true);
      ir.desiredSSAOptions.setHeapTypes(LoadElimination.getCandidates(ir));
      ir.desiredSSAOptions.setAbort((round > ir.options.SSA_LOAD_ELIMINATION_ROUNDS) ||
                                    (!ir.HIRInfo.loadEliminationDidSomething));
    }
  }

  /**
   * This class sets up the IR state prior to entering SSA for GVN.
   */
  private static class GVNPreparation extends CompilerPhase {

    public final boolean shouldPerform(OptOptions options) {
      return options.SSA_LOAD_ELIMINATION;
    }

    public final String getName() {
      return "GVN Preparation";
    }

    private final int round;

    /**
     * Constructor
     */
    public GVNPreparation(int round) {
      super(new Object[]{round});
      this.round = round;
    }

    /**
     * Constructor for this compiler phase
     */
    private static final Constructor<CompilerPhase> constructor =
        getCompilerPhaseConstructor(GVNPreparation.class, new Class[]{Integer.TYPE});

    /**
     * Get a constructor object for this compiler phase
     * @return compiler phase constructor
     */
    public Constructor<CompilerPhase> getClassConstructor() {
      return constructor;
    }

    public final void perform(IR ir) {
      // register in the IR the SSA properties we need for load
      // elimination
      ir.desiredSSAOptions = new SSAOptions();
      ir.desiredSSAOptions.setScalarsOnly(true);
      ir.desiredSSAOptions.setBackwards(false);
      ir.desiredSSAOptions.setInsertUsePhis(false);
      ir.desiredSSAOptions.setAbort((round > ir.options.SSA_LOAD_ELIMINATION_ROUNDS) ||
                                    (!ir.HIRInfo.loadEliminationDidSomething));
    }
  }
}
