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

import java.util.Enumeration;
import java.util.HashMap;
import org.jikesrvm.VM;
import org.jikesrvm.compilers.opt.DefUse;
import org.jikesrvm.compilers.opt.OptimizingCompilerException;
import org.jikesrvm.compilers.opt.ir.ALoad;
import org.jikesrvm.compilers.opt.ir.AStore;
import org.jikesrvm.compilers.opt.ir.Attempt;
import org.jikesrvm.compilers.opt.ir.Binary;
import org.jikesrvm.compilers.opt.ir.CacheOp;
import org.jikesrvm.compilers.opt.ir.Call;
import org.jikesrvm.compilers.opt.ir.GuardedBinary;
import org.jikesrvm.compilers.opt.ir.GuardedUnary;
import org.jikesrvm.compilers.opt.ir.IfCmp;
import org.jikesrvm.compilers.opt.ir.InlineGuard;
import org.jikesrvm.compilers.opt.ir.MonitorOp;
import org.jikesrvm.compilers.opt.ir.Move;
import org.jikesrvm.compilers.opt.ir.New;
import org.jikesrvm.compilers.opt.ir.NewArray;
import org.jikesrvm.compilers.opt.ir.NullCheck;
import org.jikesrvm.compilers.opt.ir.BasicBlock;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ir.OperandEnumeration;
import static org.jikesrvm.compilers.opt.ir.Operators.IG_PATCH_POINT;
import static org.jikesrvm.compilers.opt.ir.Operators.IR_PROLOGUE;
import static org.jikesrvm.compilers.opt.ir.Operators.PI;
import org.jikesrvm.compilers.opt.ir.Register;
import org.jikesrvm.compilers.opt.ir.Phi;
import org.jikesrvm.compilers.opt.ir.Prepare;
import org.jikesrvm.compilers.opt.ir.PutField;
import org.jikesrvm.compilers.opt.ir.PutStatic;
import org.jikesrvm.compilers.opt.ir.Unary;
import org.jikesrvm.compilers.opt.ir.ZeroCheck;
import org.jikesrvm.compilers.opt.ir.operand.ConditionOperand;
import org.jikesrvm.compilers.opt.ir.operand.ConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.DoubleConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.FloatConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.IntConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.LongConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.MethodOperand;
import org.jikesrvm.compilers.opt.ir.operand.ObjectConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.Operand;
import org.jikesrvm.compilers.opt.ir.operand.RegisterOperand;
import org.jikesrvm.compilers.opt.ir.operand.TIBConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.TrueGuardOperand;
import org.jikesrvm.compilers.opt.ir.operand.TypeOperand;
import org.jikesrvm.compilers.opt.ir.operand.UnreachableOperand;
import org.jikesrvm.compilers.opt.util.GraphNode;
import org.jikesrvm.compilers.opt.util.SpaceEffGraph;

/**
 * This class implements the value graph used in global value numbering
 * a la Alpern, Wegman and Zadeck.  See Muchnick p.348 for a nice
 * discussion.
 *
 * From Muchnick, "the <em>value graph</em> of a procedure is a
 * labeled directed graph whose nodes are labeled with operators,
 * function symbols, or constants, and whose edges represent
 * generating assignments and point from an operator or function to
 * its operands; the edges are labeled with natural numbers that
 * indicate the operand postion that each operand has with repsect to
 * the given operator or function."
 */
final class ValueGraph {

  /**
   * Internal graph structure of the value graph.
   */
  private final SpaceEffGraph graph;
  /**
   * A mapping from name to value graph vertex.
   */
  private final HashMap<Object, ValueGraphVertex> nameMap;

  /**
   *  Construct a value graph from an IR.
   *
   * <p><b> PRECONDITION:</b> The IR <em> must </em> be in SSA form.
   * @param ir the IR
   */
  ValueGraph(IR ir) {
    graph = new SpaceEffGraph();
    nameMap = new HashMap<Object, ValueGraphVertex>();
    // TODO!!: compute register lists incrementally
    // we need register lists in order to call Register.getFirstDef()
    DefUse.computeDU(ir);
    // add value graph nodes for each symbolic register
    addRegisterNodes(ir);
    // go through the IR and add nodes and edges to the value graph
    // for each instruction, as needed
    for (Enumeration<Instruction> e = ir.forwardInstrEnumerator(); e.hasMoreElements();) {
      Instruction s = e.nextElement();
      processInstruction(s);
    }

    computeClosure();
  }

  /**
   * Due to PI nodes and Moves, the initial label for a register may be
   * another register.  Fix up the value graph for cases where the initial
   * register label was not removed.
   */
  private void computeClosure() {
    for (Enumeration<GraphNode> e = enumerateVertices(); e.hasMoreElements();) {
      ValueGraphVertex v = (ValueGraphVertex) e.nextElement();
      if (v.getName() instanceof Register) {
        if (v.getLabel() instanceof Register) {
          if (v.getName() != v.getLabel()) {
            ValueGraphVertex v2 = getVertex(v.getLabel());
            if (VM.VerifyAssertions) {
              if (v2.getName() instanceof Register &&
                  v2.getLabel() instanceof Register &&
                  v2.getLabel() != v2.getName()) {
                VM._assert(false);
              }
            }
            v.copyVertex(v2);
          }
        }
      }
    }
  }

  /**
   * Enumerate the vertices in the value graph.
   *
   * @return an enumeration of the vertices in the value graph
   */
  public Enumeration<GraphNode> enumerateVertices() {
    return graph.enumerateNodes();
  }

  /**
   * Return the vertex which has a given name.
   *
   * @param name the name of the vertex
   * @return the vertex with the name.  null if none found.
   */
  public ValueGraphVertex getVertex(Object name) {
    if (name instanceof RegisterOperand) {
      name = ((RegisterOperand) name).asRegister().getRegister();
    } else if (name instanceof IntConstantOperand) {
      name = ((IntConstantOperand) name).value;
    } else if (name instanceof FloatConstantOperand) {
      name = ((FloatConstantOperand) name).value;
    } else if (name instanceof LongConstantOperand) {
      name = ((LongConstantOperand) name).value;
    } else if (name instanceof DoubleConstantOperand) {
      name = ((DoubleConstantOperand) name).value;
    } else if (name instanceof ObjectConstantOperand) {
      name = ((ObjectConstantOperand) name).value;
    } else if (name instanceof TIBConstantOperand) {
      name = ((TIBConstantOperand) name).value;
    }
    return nameMap.get(name);
  }

  /**
   * Return a String representation of the value graph.
   *
   * @return a String representation of the value graph.
   */
  @Override
  public String toString() {
    // print the nodes
    StringBuilder s = new StringBuilder("VALUE GRAPH: \n");
    for (Enumeration<GraphNode> n = graph.enumerateNodes(); n.hasMoreElements();) {
      ValueGraphVertex node = (ValueGraphVertex) n.nextElement();
      s.append(node).append("\n");
    }
    return s.toString();
  }

  /**
   * Add a node to the value graph for every symbolic register.
   *
   * <p><b>PRECONDITION:</b> register lists are computed and valid
   *
   * @param ir the governing IR
   */
  private void addRegisterNodes(IR ir) {
    for (Register reg = ir.regpool.getFirstSymbolicRegister(); reg != null; reg = reg.getNext()) {
      findOrCreateVertex(reg);
    }
  }

  /**
   * Update the value graph to account for a given instruction.
   *
   * @param s the instruction in question
   */
  private void processInstruction(Instruction s) {
    // TODO: support all necessary types of instructions
    if (s.isDynamicLinkingPoint()) {
      processCall(s);
    } else if (Move.conforms(s)) {
      processMove(s);
    } else if (s.operator == PI) {
      processPi(s);
    } else if (New.conforms(s)) {
      processNew(s);
    } else if (NewArray.conforms(s)) {
      processNewArray(s);
    } else if (Unary.conforms(s)) {
      processUnary(s);
    } else if (GuardedUnary.conforms(s)) {
      processGuardedUnary(s);
    } else if (NullCheck.conforms(s)) {
      processNullCheck(s);
    } else if (ZeroCheck.conforms(s)) {
      processZeroCheck(s);
    } else if (Binary.conforms(s)) {
      processBinary(s);
    } else if (GuardedBinary.conforms(s)) {
      processGuardedBinary(s);
    } else if (InlineGuard.conforms(s)) {
      processInlineGuard(s);
    } else if (IfCmp.conforms(s)) {
      processIfCmp(s);
    } else if (Call.conforms(s)) {
      processCall(s);
    } else if (MonitorOp.conforms(s)) {
      processCall(s);
    } else if (Prepare.conforms(s)) {
      processCall(s);
    } else if (Attempt.conforms(s)) {
      processCall(s);
    } else if (CacheOp.conforms(s)) {
      processCall(s);
    } else if (ALoad.conforms(s)) {
      processALoad(s);
    } else if (PutField.conforms(s)) {
      processPutField(s);
    } else if (PutStatic.conforms(s)) {
      processPutStatic(s);
    } else if (AStore.conforms(s)) {
      processAStore(s);
    } else if (Phi.conforms(s)) {
      processPhi(s);
    } else if (s.operator() == IR_PROLOGUE) {
      processPrologue(s);
    }
  }

  /**
   * Update the value graph to account for a given MOVE instruction.
   *
   * <p><b>PRECONDITION:</b> <code> Move.conforms(s); </code>
   *
   * @param s the instruction in question
   */
  private void processMove(Instruction s) {
    // ignore instructions that define physical registers
    for (OperandEnumeration e = s.getDefs(); e.hasMoreElements();) {
      Operand current = e.next();
      if (current instanceof RegisterOperand && ((RegisterOperand) current).getRegister().isPhysical()) return;
    }

    Register result = Move.getResult(s).getRegister();
    ValueGraphVertex v = findOrCreateVertex(result);
    Operand val = Move.getVal(s);
    // bypass Move instructions that define the right-hand side
    val = bypassMoves(val);
    v.copyVertex(findOrCreateVertex(val));
  }

  /**
   * Update the value graph to account for a given PI instruction.
   *
   * <p><b>PRECONDITION:</b> <code> s.operator == PI </code>
   *
   * @param s the instruction in question
   */
  private void processPi(Instruction s) {
    Register result = GuardedUnary.getResult(s).getRegister();
    ValueGraphVertex v = findOrCreateVertex(result);
    Operand val = GuardedUnary.getVal(s);
    // bypass Move instructions that define the right-hand side
    val = bypassMoves(val);
    v.copyVertex(findOrCreateVertex(val));
  }

  /**
   * Update the value graph to account for a given NEW instruction.
   *
   * <p><b>PRECONDITION:</b> <code> New.conforms(s); </code>
   *
   * For a new instruction, we always create a new vertex.
   *
   * @param s the instruction in question
   */
  private void processNew(Instruction s) {
    RegisterOperand result = New.getResult(s);
    ValueGraphVertex v = findOrCreateVertex(result.getRegister());
    // set the label for a NEW instruction to be the instruction itself
    // so that no two NEW results get the same value number
    v.setLabel(s, 0);
  }

  /**
   * Update the value graph to account for a given NEWARRAY instruction.
   *
   * <p><b>PRECONDITION:</b> <code> NewArray.conforms(s); </code>
   *
   * For a newarray instruction, we always create a new vertex.
   *
   * @param s the instruction in question
   */
  private void processNewArray(Instruction s) {
    RegisterOperand result = NewArray.getResult(s);
    ValueGraphVertex v = findOrCreateVertex(result.getRegister());
    // set the label for a NEW instruction to be the instruction itself
    // so that no two NEW results get the same value number
    v.setLabel(s, 0);
  }

  /**
   * Update the value graph to account for a given PUTFIELD instruction.
   *
   * <p><b>PRECONDITION:</b> <code> PutField.conforms(s); </code>
   *
   *  Make sure we have value graph nodes for a constant value
   *
   * @param s the instruction in question
   */
  private void processPutField(Instruction s) {
    Operand value = PutField.getValue(s);
    if (value.isConstant()) {
      findOrCreateVertex((ConstantOperand) value);
    }
  }

  /**
   * Update the value graph to account for a given PUTSTATIC instruction.
   *
   * <p><b>PRECONDITION:</b> <code> PutStatic.conforms(s); </code>
   *
   * Make sure we have value graph nodes for a constant value
   *
   * @param s the instruction in question
   */
  private void processPutStatic(Instruction s) {
    Operand value = PutStatic.getValue(s);
    if (value.isConstant()) {
      findOrCreateVertex((ConstantOperand) value);
    }
  }

  /**
   * Update the value graph to account for a given ASTORE instruction.
   *
   * <p><b>PRECONDITION:</b> <code> AStore.conforms(s); </code>
   *
   * Make sure we have value graph nodes for a constant value
   *
   * @param s the instruction in question
   */
  private void processAStore(Instruction s) {
    Operand value = AStore.getValue(s);
    if (value.isConstant()) {
      findOrCreateVertex((ConstantOperand) value);
    }
    Operand index = AStore.getIndex(s);
    if (index.isConstant()) {
      findOrCreateVertex((ConstantOperand) index);
    }
  }

  /**
   * Update the value graph to account for a given ALOAD instruction.
   *
   * <p><b>PRECONDITION:</b> <code> ALoad.conforms(s); </code>
   *
   * Make sure we have value graph nodes for a constant value
   *
   * @param s the instruction in question
   */
  private void processALoad(Instruction s) {
    Operand index = ALoad.getIndex(s);
    if (index.isConstant()) {
      findOrCreateVertex((ConstantOperand) index);
    }
  }

  /**
   * Update the value graph to account for a given Unary instruction.
   *
   * <p><b>PRECONDITION:</b> <code> Unary.conforms(s); </code>
   *
   * @param s the instruction in question
   */
  private void processUnary(Instruction s) {
    // label the vertex corresponding to the result with the operator
    RegisterOperand result = Unary.getResult(s);
    ValueGraphVertex v = findOrCreateVertex(result.getRegister());
    v.setLabel(s.operator(), 1);
    // link node v to the operand it uses
    Operand val = Unary.getVal(s);
    // bypass Move instructions
    val = bypassMoves(val);
    link(v, findOrCreateVertex(val), 0);
  }

  /**
   * Update the value graph to account for a given GuardedUnary instruction.
   *
   * <p><b>PRECONDITION:</b> <code> GuardedUnary.conforms(s); </code>
   *
   * Careful: we define two Guarded Unaries to be equivalent regardless of
   * whether the guards are equivalent!
   *
   * @param s the instruction in question
   */
  private void processGuardedUnary(Instruction s) {
    // label the vertex corresponding to the result with the operator
    RegisterOperand result = GuardedUnary.getResult(s);
    ValueGraphVertex v = findOrCreateVertex(result.getRegister());
    v.setLabel(s.operator(), 1);
    // link node v to the operand it uses
    Operand val = GuardedUnary.getVal(s);
    // bypass Move instructions
    val = bypassMoves(val);
    link(v, findOrCreateVertex(val), 0);
  }

  /**
   * Update the value graph to account for a given NullCheck instruction.
   *
   * <p><b>PRECONDITION:</b> <code> NullCheck.conforms(s); </code>
   *
   * @param s the instruction in question
   */
  private void processNullCheck(Instruction s) {
    // label the vertex corresponding to the result with the operator
    RegisterOperand result = NullCheck.getGuardResult(s);
    ValueGraphVertex v = findOrCreateVertex(result.getRegister());
    v.setLabel(s.operator(), 1);
    // link node v to the operand it uses
    Operand val = NullCheck.getRef(s);
    // bypass Move instructions
    val = bypassMoves(val);
    link(v, findOrCreateVertex(val), 0);
  }

  /**
   * Update the value graph to account for a given NullCheck instruction.
   *
   * <p><b>PRECONDITION:</b> <code> ZeroCheck.conforms(s); </code>
   *
   * @param s the instruction in question
   */
  private void processZeroCheck(Instruction s) {
    // label the vertex corresponding to the result with the operator
    RegisterOperand result = ZeroCheck.getGuardResult(s);
    ValueGraphVertex v = findOrCreateVertex(result.getRegister());
    v.setLabel(s.operator(), 1);
    // link node v to the operand it uses
    Operand val = ZeroCheck.getValue(s);
    // bypass Move instructions
    val = bypassMoves(val);
    link(v, findOrCreateVertex(val), 0);
  }

  /**
   * Update the value graph to account for a given Binary instruction.
   *
   * <p><b>PRECONDITION:</b> <code> Binary.conforms(s); </code>
   *
   * @param s the instruction in question
   */
  private void processBinary(Instruction s) {
    // label the vertex corresponding to the result with the operator
    RegisterOperand result = Binary.getResult(s);
    ValueGraphVertex v = findOrCreateVertex(result.getRegister());
    v.setLabel(s.operator(), 2);
    // link node v to the two operands it uses
    // first link the first val
    Operand val = Binary.getVal1(s);
    val = bypassMoves(val);
    link(v, findOrCreateVertex(val), 0);
    Operand val2 = Binary.getVal2(s);
    val2 = bypassMoves(val2);
    link(v, findOrCreateVertex(val2), 1);
  }

  /**
   * Update the value graph to account for a given GuardedBinary instruction.
   *
   * <p><b>PRECONDITION:</b> <code> GuardedBinary.conforms(s); </code>
   *
   * Careful: we define two Guarded Binaries to be equivalent regardless of
   * whether the guards are equivalent!
   *
   * @param s the instruction in question
   */
  private void processGuardedBinary(Instruction s) {
    // label the vertex corresponding to the result with the operator
    RegisterOperand result = GuardedBinary.getResult(s);
    ValueGraphVertex v = findOrCreateVertex(result.getRegister());
    v.setLabel(s.operator(), 2);
    // link node v to the two operands it uses
    // first link the first val
    Operand val = GuardedBinary.getVal1(s);
    val = bypassMoves(val);
    link(v, findOrCreateVertex(val), 0);
    Operand val2 = GuardedBinary.getVal2(s);
    val2 = bypassMoves(val2);
    link(v, findOrCreateVertex(val2), 1);
  }

  /**
   * Update the value graph to account for a given InlineGuard instruction.
   *
   * <p><b>PRECONDITION:</b> <code> InlineGuard.conforms(s); </code>
   *
   * @param s the instruction in question
   */
  private void processInlineGuard(Instruction s) {
    ValueGraphVertex v = new ValueGraphVertex(s);
    graph.addGraphNode(v);
    nameMap.put(s, v);
    if (s.operator() == IG_PATCH_POINT) {
      // the 'goal' is irrelevant for patch_point guards.
      v.setLabel(s.operator(), 1);
      link(v, findOrCreateVertex(bypassMoves(InlineGuard.getValue(s))), 0);
    } else {
      v.setLabel(s.operator(), 2);
      link(v, findOrCreateVertex(bypassMoves(InlineGuard.getValue(s))), 0);
      link(v, findOrCreateVertex(InlineGuard.getGoal(s)), 1);
    }
  }

  /**
   * Update the value graph to account for a given IfCmp instruction.
   *
   * <p><b>PRECONDITION:</b> <code> IfCmp.conforms(s); </code>
   *
   * @param s the instruction in question
   */
  private void processIfCmp(Instruction s) {
    ValueGraphVertex v = new ValueGraphVertex(s);
    graph.addGraphNode(v);
    nameMap.put(s, v);
    v.setLabel(s.operator(), 3);
    link(v, findOrCreateVertex(bypassMoves(IfCmp.getVal1(s))), 0);
    link(v, findOrCreateVertex(bypassMoves(IfCmp.getVal2(s))), 1);
    link(v, findOrCreateVertex(IfCmp.getCond(s)), 2);
  }

  /**
   * Update the value graph to account for a given Phi instruction.
   *
   * <p><b>PRECONDITION:</b> <code> Phi.conforms(s); </code>
   *
   * @param s the instruction in question
   */
  private void processPhi(Instruction s) {
    // the label for a PHI instruction is the basic block
    // in which it appears
    Register result = Phi.getResult(s).asRegister().getRegister();
    ValueGraphVertex v = findOrCreateVertex(result);
    BasicBlock bb = s.getBasicBlock();
    v.setLabel(bb, bb.getNumberOfIn());
    // link node v to all operands it uses
    for (int i = 0; i < Phi.getNumberOfValues(s); i++) {
      Operand val = Phi.getValue(s, i);
      val = bypassMoves(val);
      ValueGraphVertex target = findOrCreateVertex(val);
      link(v, target, i);
    }
  }

  /**
   * Update the value graph to account for an IR_PROLOGUE instruction
   *
   * <p><b>PRECONDITION:</b> <code> Prologue.conforms(s); </code>
   *
   * @param s the instruction in question
   */
  private void processPrologue(Instruction s) {
    int numArgs = 0;
    for (OperandEnumeration e = s.getDefs(); e.hasMoreElements(); numArgs++) {
      Register formal = ((RegisterOperand) e.next()).getRegister();
      ValueGraphVertex v = findOrCreateVertex(formal);
      v.setLabel(new ValueGraphParamLabel(numArgs), 0);
    }
  }

  /**
   * Update the value graph to account for a given Call instruction.
   *
   * <p><b>PRECONDITION:</b> <code> Call.conforms(s); </code>
   *
   * @param s the instruction in question
   */
  private void processCall(Instruction s) {
    // do nothing.
    // TODO: someday, maybe exploit interprocedural information
  }

  /**
   * Find or create an ValueGraphVertex corresponding to a
   * given variable.
   *
   * @param var   The variable
   * @return A value graph vertex corresponding to this variable
   */
  private ValueGraphVertex findOrCreateVertex(Object var) {
    if (var instanceof Register) {
      return findOrCreateVertex((Register) var);
    } else if (var instanceof RegisterOperand) {
      return findOrCreateVertex(((RegisterOperand) var).getRegister());
    } else if (var instanceof ConstantOperand) {
      return findOrCreateVertex((ConstantOperand) var);
    } else if (var instanceof TypeOperand) {
      return findOrCreateVertex((TypeOperand) var);
    } else if (var instanceof MethodOperand) {
      return findOrCreateVertex((MethodOperand) var);
    } else if (var instanceof ConditionOperand) {
      return findOrCreateVertex((ConditionOperand) var);
    } else {
      throw new OptimizingCompilerException("ValueGraph.findOrCreateVertex: unexpected type " + var.getClass());
    }
  }

  /**
   * Find or create an ValueGraphVertex corresponding to a
   * given register
   *
   * @param r the register
   * @return a value graph vertex corresponding to this variable
   */
  private ValueGraphVertex findOrCreateVertex(Register r) {
    ValueGraphVertex v = getVertex(r);
    if (v == null) {
      v = new ValueGraphVertex(r);
      v.setLabel(r, 0);
      graph.addGraphNode(v);
      nameMap.put(r, v);
    }
    return v;
  }

  /**
   * Find or create an ValueGraphVertex corresponding to a
   * given constant operand
   *
   * @param op the constant operand
   * @return a value graph vertex corresponding to this variable
   */
  private ValueGraphVertex findOrCreateVertex(ConstantOperand op) {
    Object name;
    if (op.isAddressConstant()) {
      name = (VM.BuildFor32Addr) ? op.asAddressConstant().value.toInt() : op.asAddressConstant().value.toLong();
    } else if (op.isIntConstant()) {
      name = op.asIntConstant().value;
    } else if (op.isFloatConstant()) {
      name = op.asFloatConstant().value;
    } else if (op.isLongConstant()) {
      name = op.asLongConstant().value;
    } else if (op.isDoubleConstant()) {
      name = op.asDoubleConstant().value;
    } else if (op instanceof ObjectConstantOperand) {
      name = op.asObjectConstant().value;
    } else if (op instanceof TIBConstantOperand) {
      name = op.asTIBConstant().value;
    } else if (op.isNullConstant()) {
      name = op;
    } else if (op instanceof TrueGuardOperand) {
      name = op;
    } else if (op instanceof UnreachableOperand) {
      name = op;
    } else {
      throw new OptimizingCompilerException("ValueGraph.findOrCreateVertex: unexpected constant operand: " +
                                                op);
    }
    ValueGraphVertex v = getVertex(name);
    if (v == null) {
      v = new ValueGraphVertex(op);
      v.setLabel(op, 0);
      graph.addGraphNode(v);
      nameMap.put(name, v);
    }
    return v;
  }

  /**
   * Find or create an ValueGraphVertex corresponding to a
   * given type operand
   *
   * @param op the operand in question
   * @return a value graph vertex corresponding to this type
   */
  private ValueGraphVertex findOrCreateVertex(TypeOperand op) {
    Object name = op.getTypeRef();
    ValueGraphVertex v = getVertex(name);
    if (v == null) {
      v = new ValueGraphVertex(op);
      v.setLabel(op, 0);
      graph.addGraphNode(v);
      nameMap.put(name, v);
    }
    return v;
  }

  /**
   * Find or create an ValueGraphVertex corresponding to a
   * given method operand
   *
   * @param op the operand in question
   * @return a value graph vertex corresponding to this type
   */
  private ValueGraphVertex findOrCreateVertex(MethodOperand op) {
    Object name;
    if (op.hasTarget()) {
      name = op.getTarget();
    } else {
      name = op.getMemberRef();
    }
    ValueGraphVertex v = getVertex(name);
    if (v == null) {
      v = new ValueGraphVertex(op);
      v.setLabel(op, 0);
      graph.addGraphNode(v);
      nameMap.put(name, v);
    }
    return v;
  }

  /**
   * Find or create an ValueGraphVertex corresponding to a
   * given method operand
   *
   * @param op the operand in question
   * @return a value graph vertex corresponding to this type
   */
  private ValueGraphVertex findOrCreateVertex(ConditionOperand op) {
    Object name = op.value; // kludge.
    ValueGraphVertex v = getVertex(name);
    if (v == null) {
      v = new ValueGraphVertex(op);
      v.setLabel(op, 0);
      graph.addGraphNode(v);
      nameMap.put(name, v);
    }
    return v;
  }

  /**
   * Link two vertices in the value graph
   *
   * @param src the def
   * @param target the use
   * @param pos the position of target in the set of uses
   */
  private void link(ValueGraphVertex src, ValueGraphVertex target, int pos) {
    ValueGraphEdge e = new ValueGraphEdge(src, target);
    src.addTarget(target, pos);
    graph.addGraphEdge(e);
  }

  /**
   * Bypass MOVE instructions that def an operand: return the first def
   * in the chain that is not the result of a MOVE instruction.
   *
   * Note: treat PI instructions like MOVES
   *
   * @param op the RegisterOperand
   */
  private Operand bypassMoves(Operand op) {
    if (!op.isRegister()) return op;
    Register r = op.asRegister().getRegister();
    Instruction def = r.getFirstDef();
    if (def == null) {
      return op;
    }
    if (r.isPhysical()) {
      return op;
    }
    if (Move.conforms(def)) {
      //   In a perfect world, this shouldn't happen. Copy propagation
      //   in SSA form should remove all 'normal' moves.
      //   We can't simply bypass this move, since it may lead to
      //   infinite mutual recursion.
      return op;
    } else if (def.operator == PI) {
      return bypassMoves(GuardedUnary.getVal(def));
    } else {
      return op;
    }
  }
}
