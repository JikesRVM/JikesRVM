/*
 * (C) Copyright IBM Corp. 2001, 2004
 */
//$Id$
package com.ibm.JikesRVM.opt;
import com.ibm.JikesRVM.*;

import java.util.*;
import com.ibm.JikesRVM.opt.ir.*;

/**
 * This class implements the value graph used in global value numbering
 * a la Alpern, Wegman and Zadeck.  See Muchnick p.348 for a nice
 * discussion.
 *
 * @author Stephen Fink
 */
class OPT_ValueGraph implements OPT_Operators {

  /** 
   *  Construct a value graph from an IR. 
   *
   * <p> <b> PRECONDITION:</b> The IR <em> must </em> be in SSA form.
   * @param ir the IR
   */
  OPT_ValueGraph(OPT_IR ir) {
    // TODO!!: compute register lists incrementally
    // we need register lists in order to call OPT_Register.getFirstDef()
    OPT_DefUse.computeDU(ir);
    // add value graph nodes for each symbolic register
    addRegisterNodes(ir);
    // go through the IR and add nodes and edges to the value graph
    // for each instruction, as needed
    for (Enumeration e = ir.forwardInstrEnumerator(); e.hasMoreElements();) {
      OPT_Instruction s = (OPT_Instruction)e.nextElement();
      processInstruction(s);
    }

    computeClosure();
  }

  /**
   * Due to PI nodes and Moves, the initial label for a register may be
   * another register.  Fix up the value graph for cases where the initial
   * register label was not removed.
   */
  void computeClosure() {
    for (Enumeration e = enumerateVertices(); e.hasMoreElements(); ) {
      OPT_ValueGraphVertex v = (OPT_ValueGraphVertex)e.nextElement();
      if (v.name instanceof OPT_Register){
        if (v.label instanceof OPT_Register) {
          if (v.name != v.label) {
            OPT_ValueGraphVertex v2 = getVertex(v.label);
            if (VM.VerifyAssertions) {
              if (v2.name instanceof OPT_Register && 
                  v2.label instanceof OPT_Register &&
                  v2.label != v2.name) {
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
  public Enumeration enumerateVertices() {
    return graph.enumerateNodes();
  }

  /**
   * Return the vertex which has a given name. 
   *
   * @param name the name of the vertex
   * @return the vertex with the name.  null if none found.
   */
  public OPT_ValueGraphVertex getVertex(Object name) {
    if (name instanceof OPT_RegisterOperand) {
      name = ((OPT_RegisterOperand)name).asRegister().register;
    } else if (name instanceof OPT_IntConstantOperand) {
      name = new Integer(((OPT_IntConstantOperand)name).value);
    } else if (name instanceof OPT_FloatConstantOperand) {
      name = new Float(((OPT_FloatConstantOperand)name).value);
    } else if (name instanceof OPT_LongConstantOperand) {
      name = new Long(((OPT_LongConstantOperand)name).value);
    } else if (name instanceof OPT_DoubleConstantOperand) {
      name = new Double(((OPT_DoubleConstantOperand)name).value);
    } else if (name instanceof OPT_StringConstantOperand) {
      name = ((OPT_StringConstantOperand)name).value;
    }
    return (OPT_ValueGraphVertex)nameMap.get(name);
  }

  /**
   * Return a String representation of the value graph.
   *
   * @return a String representation of the value graph.
   */
  public String toString() {
    // print the nodes
    StringBuffer s = new StringBuffer("VALUE GRAPH: \n");
    for (Enumeration n = graph.enumerateNodes(); n.hasMoreElements();) {
      OPT_ValueGraphVertex node = (OPT_ValueGraphVertex)n.nextElement();
      s.append(node).append("\n");
    }
    return s.toString();
  }

  /**
   * Internal graph structure of the value graph.
   */
  private OPT_SpaceEffGraph graph = new OPT_SpaceEffGraph();
  /**
   * A mapping from name to value graph vertex.
   */
  private HashMap nameMap = new HashMap(); 

  /** 
   * Add a node to the value graph for every symbolic register.
   *
   * <p><b>PRECONDITION:</b> register lists are computed and valid
   *
   * @param ir the governing IR
   */
  private void addRegisterNodes(OPT_IR ir) {
    for (OPT_Register reg = ir.regpool.getFirstSymbolicRegister(); 
        reg != null; reg = reg.getNext())
      findOrCreateVertex(reg);
  }

  /** 
   * Update the value graph to account for a given instruction.
   *
   * @param s the instruction in question
   */
  private void processInstruction(OPT_Instruction s) {
    // TODO: support all necessary types of instructions
    if (s.isDynamicLinkingPoint())
      processCall(s); 
    else if (Move.conforms(s))
      processMove(s); 
    else if (s.operator == PI)
      processPi(s); 
    else if (New.conforms(s))
      processNew(s); 
    else if (NewArray.conforms(s))
      processNewArray(s); 
    else if (Unary.conforms(s))
      processUnary(s); 
    else if (GuardedUnary.conforms(s))
      processGuardedUnary(s); 
    else if (NullCheck.conforms(s))
      processNullCheck(s); 
    else if (ZeroCheck.conforms(s))
      processZeroCheck(s); 
    else if (Binary.conforms(s))
      processBinary(s); 
    else if (GuardedBinary.conforms(s))
      processGuardedBinary(s); 
    else if (InlineGuard.conforms(s))
      processInlineGuard(s);
    else if (IfCmp.conforms(s))
      processIfCmp(s);
    else if (Call.conforms(s))
      processCall(s); 
    else if (MonitorOp.conforms(s))
      processCall(s); 
    else if (Prepare.conforms(s))
      processCall(s); 
    else if (Attempt.conforms(s))
      processCall(s); 
    else if (CacheOp.conforms(s))
      processCall(s); 
    else if (ALoad.conforms(s))
      processALoad(s); 
    else if (PutField.conforms(s))
      processPutField(s); 
    else if (PutStatic.conforms(s))
      processPutStatic(s); 
    else if (AStore.conforms(s))
      processAStore(s); 
    else if (Phi.conforms(s))
      processPhi(s);
    else if (s.operator() == IR_PROLOGUE)
      processPrologue(s);
  }

  /** 
   * Update the value graph to account for a given MOVE instruction.
   *
   * <p><b>PRECONDITION:</b> <code> Move.conforms(s); </code>
   *
   * @param s the instruction in question
   */
  private void processMove(OPT_Instruction s) {
    // ignore instructions that define physical registers
    for (OPT_OperandEnumeration e = s.getDefs(); e.hasMoreElements(); ) {
      OPT_Operand current = e.next();
      if (current instanceof OPT_RegisterOperand && ((OPT_RegisterOperand) current).register.isPhysical()) return;
    }

    OPT_Register result = Move.getResult(s).register;
    OPT_ValueGraphVertex v = findOrCreateVertex(result);
    OPT_Operand val = Move.getVal(s);
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
  private void processPi(OPT_Instruction s) {
    OPT_Register result = GuardedUnary.getResult(s).register;
    OPT_ValueGraphVertex v = findOrCreateVertex(result);
    OPT_Operand val = GuardedUnary.getVal(s);
    // bypass Move instructions that define the right-hand side
    val = bypassMoves(val);
    v.copyVertex(findOrCreateVertex(val));
  }

  /** 
   * Update the value graph to account for a given NEW instruction.
   * 
   * <p><b>PRECONDITION:</b> <code> New.conforms(s); </code>
   *
   * <p>For a new instruction, we always create a new vertex.
   *
   * @param s the instruction in question
   */
  private void processNew(OPT_Instruction s) {
    OPT_RegisterOperand result = New.getResult(s);
    OPT_ValueGraphVertex v = findOrCreateVertex(result.register);
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
  private void processNewArray(OPT_Instruction s) {
    OPT_RegisterOperand result = NewArray.getResult(s);
    OPT_ValueGraphVertex v = findOrCreateVertex(result.register);
    // set the label for a NEW instruction to be the instruction itself
    // so that no two NEW results get the same value number
    v.setLabel(s, 0);
  }

  /** 
   * Update the value graph to account for a given PUTFIELD instruction.
   * 
   * <p><b>PRECONDITION:</b> <code> PutField.conforms(s); </code>
   *
   * <p> Make sure we have value graph nodes for a constant value
   *
   * @param s the instruction in question
   */
  private void processPutField(OPT_Instruction s) {
    OPT_Operand value = PutField.getValue(s);
    if (value.isConstant()) {
      findOrCreateVertex((OPT_ConstantOperand)value);
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
  private void processPutStatic(OPT_Instruction s) {
    OPT_Operand value = PutStatic.getValue(s);
    if (value.isConstant()) {
      findOrCreateVertex((OPT_ConstantOperand)value);
    }
  }

  /** 
   * Update the value graph to account for a given ASTORE instruction.
   *
   * <p><b>PRECONDITION:</b> <code> AStore.conforms(s); </code>
   *
   * <p>Make sure we have value graph nodes for a constant value
   *
   * @param s the instruction in question
   */
  private void processAStore(OPT_Instruction s) {
    OPT_Operand value = AStore.getValue(s);
    if (value.isConstant()) {
      findOrCreateVertex((OPT_ConstantOperand)value);
    }
    OPT_Operand index = AStore.getIndex(s);
    if (index.isConstant()) {
      findOrCreateVertex((OPT_ConstantOperand)index);
    }
  }

  /** 
   * Update the value graph to account for a given ALOAD instruction.
   * 
   * <p><b>PRECONDITION:</b> <code> ALoad.conforms(s); </code>
   *
   * <p>Make sure we have value graph nodes for a constant value
   *
   * @param s the instruction in question
   */
  private void processALoad(OPT_Instruction s) {
    OPT_Operand index = ALoad.getIndex(s);
    if (index.isConstant()) {
      findOrCreateVertex((OPT_ConstantOperand)index);
    }
  }

  /** 
   * Update the value graph to account for a given Unary instruction.
   * 
   * <p><b>PRECONDITION:</b> <code> Unary.conforms(s); </code>
   *
   * @param s the instruction in question
   */
  private void processUnary(OPT_Instruction s) {
    // label the vertex corresponding to the result with the operator
    OPT_RegisterOperand result = Unary.getResult(s);
    OPT_ValueGraphVertex v = findOrCreateVertex(result.register);
    v.setLabel(s.operator(), 1);
    // link node v to the operand it uses
    OPT_Operand val = Unary.getVal(s);
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
  private void processGuardedUnary(OPT_Instruction s) {
    // label the vertex corresponding to the result with the operator
    OPT_RegisterOperand result = GuardedUnary.getResult(s);
    OPT_ValueGraphVertex v = findOrCreateVertex(result.register);
    v.setLabel(s.operator(), 1);
    // link node v to the operand it uses
    OPT_Operand val = GuardedUnary.getVal(s);
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
  private void processNullCheck(OPT_Instruction s) {
    // label the vertex corresponding to the result with the operator
    OPT_RegisterOperand result = NullCheck.getGuardResult(s);
    OPT_ValueGraphVertex v = findOrCreateVertex(result.register);
    v.setLabel(s.operator(), 1);
    // link node v to the operand it uses
    OPT_Operand val = NullCheck.getRef(s);
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
  private void processZeroCheck(OPT_Instruction s) {
    // label the vertex corresponding to the result with the operator
    OPT_RegisterOperand result = ZeroCheck.getGuardResult(s);
    OPT_ValueGraphVertex v = findOrCreateVertex(result.register);
    v.setLabel(s.operator(), 1);
    // link node v to the operand it uses
    OPT_Operand val = ZeroCheck.getValue(s);
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
  private void processBinary(OPT_Instruction s) {
    // label the vertex corresponding to the result with the operator
    OPT_RegisterOperand result = Binary.getResult(s);
    OPT_ValueGraphVertex v = findOrCreateVertex(result.register);
    v.setLabel(s.operator(), 2);
    // link node v to the two operands it uses
    // first link the first val
    OPT_Operand val = Binary.getVal1(s);
    val = bypassMoves(val);
    link(v, findOrCreateVertex(val), 0);
    OPT_Operand val2 = Binary.getVal2(s);
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
  private void processGuardedBinary(OPT_Instruction s) {
    // label the vertex corresponding to the result with the operator
    OPT_RegisterOperand result = GuardedBinary.getResult(s);
    OPT_ValueGraphVertex v = findOrCreateVertex(result.register);
    v.setLabel(s.operator(), 2);
    // link node v to the two operands it uses
    // first link the first val
    OPT_Operand val = GuardedBinary.getVal1(s);
    val = bypassMoves(val);
    link(v, findOrCreateVertex(val), 0);
    OPT_Operand val2 = GuardedBinary.getVal2(s);
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
  private void processInlineGuard(OPT_Instruction s) {
    OPT_ValueGraphVertex v = new OPT_ValueGraphVertex(s);
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
  private void processIfCmp(OPT_Instruction s) {
    OPT_ValueGraphVertex v = new OPT_ValueGraphVertex(s);
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
  private void processPhi(OPT_Instruction s) {
    // the label for a PHI instruction is the basic block
    // in which it appears
    OPT_Register result = Phi.getResult(s).asRegister().register;
    OPT_ValueGraphVertex v = findOrCreateVertex(result);
    OPT_BasicBlock bb = s.getBasicBlock();
    v.setLabel(bb, bb.getNumberOfIn());
    // link node v to all operands it uses
    for (int i = 0; i < Phi.getNumberOfValues(s); i++) {
      OPT_Operand val = Phi.getValue(s, i);
      val = bypassMoves(val);
      OPT_ValueGraphVertex target = findOrCreateVertex(val);
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
  private void processPrologue(OPT_Instruction s) {
    int numArgs=0;
    for (OPT_OperandEnumeration e = s.getDefs(); e.hasMoreElements(); 
         numArgs++) {
      OPT_Register formal = ((OPT_RegisterOperand)e.next()).register;
      OPT_ValueGraphVertex v = findOrCreateVertex(formal);
      v.setLabel(new OPT_ValueGraphParamLabel(numArgs), 0);
    }
  }


  /** 
   * Update the value graph to account for a given Call instruction.
   * 
   * <p><b>PRECONDITION:</b> <code> Call.conforms(s); </code>
   *
   * @param s the instruction in question
   */
  private void processCall(OPT_Instruction s) {
  // do nothing.
  // TODO: someday, maybe exploit interprocedural information
  }

  /**
   * Find or create an OPT_ValueGraphVertex corresponding to a 
   * given variable.
   *
   * @param var   The variable
   * @return A value graph vertex corresponding to this variable
   */
  private OPT_ValueGraphVertex findOrCreateVertex(Object var) {
    if (var instanceof OPT_Register)
      return findOrCreateVertex((OPT_Register)var); 
    else if (var instanceof OPT_RegisterOperand)
      return findOrCreateVertex(((OPT_RegisterOperand)var).register); 
    else if (var instanceof OPT_ConstantOperand)
      return findOrCreateVertex((OPT_ConstantOperand)var); 
    else if (var instanceof OPT_TypeOperand)
      return findOrCreateVertex((OPT_TypeOperand)var); 
    else if (var instanceof OPT_MethodOperand)
      return findOrCreateVertex((OPT_MethodOperand)var); 
    else if (var instanceof OPT_ConditionOperand)
      return findOrCreateVertex((OPT_ConditionOperand)var); 
    else 
      throw  new OPT_OptimizingCompilerException(
          "OPT_ValueGraph.findOrCreateVertex: unexpected type "
          + var.getClass());
  }

  /**
   * Find or create an OPT_ValueGraphVertex corresponding to a 
   * given register
   *
   * @param r the register
   * @return a value graph vertex corresponding to this variable
   */
  private OPT_ValueGraphVertex findOrCreateVertex(OPT_Register r) {
    OPT_ValueGraphVertex v = getVertex(r);
    if (v == null) {
      v = new OPT_ValueGraphVertex(r);
      v.setLabel(r, 0);
      graph.addGraphNode(v);
      nameMap.put(r, v);
    }
    return v;
  }

  /**
   * Find or create an OPT_ValueGraphVertex corresponding to a 
   * given constant operand 
   *
   * @param op the constant operand
   * @return a value graph vertex corresponding to this variable
   */
  private OPT_ValueGraphVertex findOrCreateVertex(OPT_ConstantOperand op) {
    Object name;
    if (op.isAddressConstant()) {
      //-#if RVM_FOR_32_ADDR
      name = new Integer(op.asAddressConstant().value.toInt());
      //-#endif
      //-#if RVM_FOR_64_ADDR
      name = new Long(op.asAddressConstant().value.toLong());
      //-#endif
    } else if (op.isIntConstant()) {
      name = new Integer(op.asIntConstant().value);
    } else if (op.isFloatConstant()) {
      name = new Float(op.asFloatConstant().value);
    } else if (op.isLongConstant()) {
      name = new Long(op.asLongConstant().value);
    } else if (op.isDoubleConstant()) {
      name = new Double(op.asDoubleConstant().value);
    } else if (op.isStringConstant()) {
      name = op.asStringConstant().value;
    } else if (op.isNullConstant()) {
      name = op;
    } else if (op instanceof OPT_TrueGuardOperand) {
      name = op;
    } else if (op instanceof OPT_UnreachableOperand) {
      name = op;
    } else {
      throw  new OPT_OptimizingCompilerException(
          "OPT_ValueGraph.findOrCreateVertex: unexpected constant operand");
    }
    OPT_ValueGraphVertex v = getVertex(name);
    if (v == null) {
      v = new OPT_ValueGraphVertex(op);
      v.setLabel(op, 0);
      graph.addGraphNode(v);
      nameMap.put(name, v);
    }
    return v;
  }

  /**
   * Find or create an OPT_ValueGraphVertex corresponding to a 
   * given type operand 
   * 
   * @param op the operand in question
   * @return a value graph vertex corresponding to this type
   */
  private OPT_ValueGraphVertex findOrCreateVertex(OPT_TypeOperand op) {
    Object name = op.getTypeRef();
    OPT_ValueGraphVertex v = getVertex(name);
    if (v == null) {
      v = new OPT_ValueGraphVertex(op);
      v.setLabel(op, 0);
      graph.addGraphNode(v);
      nameMap.put(name, v);
    }
    return v;
  }

  /**
   * Find or create an OPT_ValueGraphVertex corresponding to a 
   * given method operand 
   * 
   * @param op the operand in question
   * @return a value graph vertex corresponding to this type
   */
  private OPT_ValueGraphVertex findOrCreateVertex(OPT_MethodOperand op) {
    Object name;
    if (op.hasTarget()) {
      name = op.getTarget();
    } else {
      name = op.getMemberRef();
    }
    OPT_ValueGraphVertex v = getVertex(name);
    if (v == null) {
      v = new OPT_ValueGraphVertex(op);
      v.setLabel(op, 0);
      graph.addGraphNode(v);
      nameMap.put(name, v);
    }
    return v;
  }

  /**
   * Find or create an OPT_ValueGraphVertex corresponding to a 
   * given method operand 
   * 
   * @param op the operand in question
   * @return a value graph vertex corresponding to this type
   */
  private OPT_ValueGraphVertex findOrCreateVertex(OPT_ConditionOperand op) {
    Object name = new Integer(op.value); // kludge.
    OPT_ValueGraphVertex v = getVertex(name);
    if (v == null) {
      v = new OPT_ValueGraphVertex(op);
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
  private void link(OPT_ValueGraphVertex src, OPT_ValueGraphVertex target, 
                    int pos) {
    OPT_ValueGraphEdge e = new OPT_ValueGraphEdge(src, target);
    src.addTarget(target, pos);
    graph.addGraphEdge(e);
  }

  /**
   * Bypass MOVE instructions that def an operand: return the first def
   * in the chain that is not the result of a MOVE instruction.
   *
   * <p>Note: treat PI instructions like MOVES
   *
   * @param op the OPT_RegisterOperand
   */
  private OPT_Operand bypassMoves(OPT_Operand op) {
    if (!op.isRegister()) return op;
    OPT_Register r = op.asRegister().register;
    OPT_Instruction def = r.getFirstDef();
    if (def == null)
      return op;
    if (r.isPhysical())
      return op;
    if (Move.conforms(def)) {
      //   In a perfect world, this shouldn't happen. Copy propagation
      //   in SSA form should remove all 'normal' moves.  
      //   We can't simply bypass this move, since it may lead to
      //   infinite mutual recursion.
      return op;
    } else if (def.operator == PI) {
      return bypassMoves(GuardedUnary.getVal(def));
    } else 
      return op;
  }
}
