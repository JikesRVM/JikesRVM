/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.mmtk.harness.lang;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.mmtk.harness.Harness;
import org.mmtk.harness.lang.ast.Alloc;
import org.mmtk.harness.lang.ast.Assert;
import org.mmtk.harness.lang.ast.Assignment;
import org.mmtk.harness.lang.ast.BinaryExpression;
import org.mmtk.harness.lang.ast.Call;
import org.mmtk.harness.lang.ast.Constant;
import org.mmtk.harness.lang.ast.Empty;
import org.mmtk.harness.lang.ast.Expect;
import org.mmtk.harness.lang.ast.Expression;
import org.mmtk.harness.lang.ast.IfStatement;
import org.mmtk.harness.lang.ast.IntrinsicMethod;
import org.mmtk.harness.lang.ast.LoadField;
import org.mmtk.harness.lang.ast.Method;
import org.mmtk.harness.lang.ast.NormalMethod;
import org.mmtk.harness.lang.ast.PrintStatement;
import org.mmtk.harness.lang.ast.Return;
import org.mmtk.harness.lang.ast.Sequence;
import org.mmtk.harness.lang.ast.Statement;
import org.mmtk.harness.lang.ast.Spawn;
import org.mmtk.harness.lang.ast.StoreField;
import org.mmtk.harness.lang.ast.Type;
import org.mmtk.harness.lang.ast.UnaryExpression;
import org.mmtk.harness.lang.ast.Variable;
import org.mmtk.harness.lang.ast.WhileStatement;
import org.mmtk.harness.lang.compiler.CompiledMethod;
import org.mmtk.harness.lang.compiler.CompiledMethodProxy;
import org.mmtk.harness.lang.compiler.CompiledMethodTable;
import org.mmtk.harness.lang.compiler.Register;
import org.mmtk.harness.lang.compiler.Temporary;
import org.mmtk.harness.lang.parser.MethodTable;
import org.mmtk.harness.lang.pcode.AllocOp;
import org.mmtk.harness.lang.pcode.BinaryOperation;
import org.mmtk.harness.lang.pcode.Branch;
import org.mmtk.harness.lang.pcode.CallIntrinsicOp;
import org.mmtk.harness.lang.pcode.CallNormalOp;
import org.mmtk.harness.lang.pcode.ExitOp;
import org.mmtk.harness.lang.pcode.ExpectOp;
import org.mmtk.harness.lang.pcode.Goto;
import org.mmtk.harness.lang.pcode.PrintOp;
import org.mmtk.harness.lang.pcode.PseudoOp;
import org.mmtk.harness.lang.pcode.ReturnOp;
import org.mmtk.harness.lang.pcode.SpawnOp;
import org.mmtk.harness.lang.pcode.StoreFieldOp;
import org.mmtk.harness.lang.pcode.StoreLocal;
import org.mmtk.harness.lang.pcode.UnaryOperation;
import org.mmtk.harness.lang.runtime.ConstantPool;

public final class Compiler extends Visitor {

  static {
    //Trace.enable(Item.COMPILER);
    //Trace.enable(Item.EVAL);
  }

  private final CompiledMethod current;
  private final CompiledMethodTable methodTable;
  private final Temporary temps;
  private final UnsyncStack<Register> operands = new UnsyncStack<Register>();

  private Compiler(NormalMethod method, CompiledMethodTable table) {
    this.current = new CompiledMethod(method);
    this.methodTable = table;
    this.temps = new Temporary(method.getDecls().size());
  }

  public static CompiledMethod compile(MethodTable methods) {
    CompiledMethodTable table = new CompiledMethodTable();
    for (NormalMethod m : methods.normalMethods()) {
      Compiler compiler = new Compiler(m,table);
      m.accept(compiler);
      table.put(compiler.yield());
    }
    for (CompiledMethod cm : table) {
      cm.resolveMethodReferences();
      if (Harness.dumpPcode.getValue()) {
        System.out.println(cm.getName()+"\n"+cm.toString());
      }
    }
    return table.get("main");
  }

  /*
   * Utility methods
   */

  public CompiledMethod yield() {
    current.setTemps(temps.size());
    return current;
  }

  private void emit(PseudoOp op) {
    current.append(op);
  }

  private int currentPc() {
    return current.currentIndex();
  }

  private void freeTemps(List<Register> actuals) {
    for (Register r : actuals) {
      temps.release(r);
    }
  }

  private List<Register> compileArgList(List<Expression> args) {
    ArrayList<Register> actuals = new ArrayList<Register>(args.size());
    for (Expression exp : args) {
      exp.accept(this);
      actuals.add(operands.pop());
    }
    return actuals;
  }

  private CompiledMethod compiledMethodFor(Method method) {
    CompiledMethod compiledMethod = methodTable.get(method.getName());
    if (compiledMethod == null) {
      compiledMethod = new CompiledMethodProxy((NormalMethod)method,methodTable);
    }
    return compiledMethod;
  }


  @Override
  public void visit(Alloc alloc) {
    alloc.getDataCount().accept(this);
    Register dataCount = operands.pop();
    alloc.getRefCount().accept(this);
    Register refCount = operands.pop();
    alloc.getDoubleAlign().accept(this);
    Register doubleAlign = operands.pop();
    Register result = temps.acquire();
    emit(new AllocOp(result,dataCount,refCount,doubleAlign,alloc.getSite()));
    operands.push(result);
    temps.release(dataCount,refCount,doubleAlign);
  }

  @Override
  public void visit(Assert ass) {
    ass.getPredicate().accept(this);            // Compile the predicate
    Register predicate = operands.pop();
    Branch branch = new Branch(predicate,true);
    emit(branch);
    temps.release(predicate);
    ArrayList<Register> actuals = new ArrayList<Register>(ass.getOutputs().size());
    for (Expression expr : ass.getOutputs()) {
      expr.accept(this);
      actuals.add(operands.pop());
    }
    emit(new PrintOp(actuals.toArray(new Register[0])));
    freeTemps(actuals);
    emit(new ExitOp(ConstantPool.ONE));
    branch.setBranchTarget(currentPc());
  }

  @Override
  public void visit(Assignment a) {
    a.getRhs().accept(this);
    Register rhs = operands.pop();
    emit(new StoreLocal(Register.createLocal(a.getSlot()),rhs));
    temps.release(rhs);
  }

  @Override
  public void visit(BinaryExpression exp) {
    exp.getLhs().accept(this);
    Register lhs = operands.pop();
    exp.getRhs().accept(this);
    Register rhs = operands.pop();
    Register result = temps.acquire();
    emit(new BinaryOperation(result,lhs,rhs,exp.getOperator()));
    temps.release(rhs,lhs);
    operands.push(result);
  }

  @Override
  public void visit(Call call) {
    Method method = call.getMethod();


    List<Register> actuals = compileArgList(call.getParams());
    Register returnVal = method.getReturnType() == Type.VOID ?
        Register.NULL : temps.acquire();

    if (method instanceof IntrinsicMethod) {
      emit(new CallIntrinsicOp(returnVal,(IntrinsicMethod)method,actuals));
    } else if (method instanceof NormalMethod) {
      CompiledMethod compiledMethod = compiledMethodFor(method);
      emit(new CallNormalOp(returnVal,compiledMethod,actuals));
    } else {
      throw new RuntimeException("Unknown method class "+method.getClass().getCanonicalName());
    }
    freeTemps(actuals);
    if (returnVal != Register.NULL)
      operands.push(returnVal);
  }

  /**
   * Constant value.  push an operand which fetches from the
   * global constant pool
   */
  @Override
  public void visit(Constant c) {
    operands.push(ConstantPool.acquire(c.value));
  }

  @Override
  public void visit(Empty e) {
    // Do nothing
  }

  @Override
  public void visit(Expect exp) {
    emit(new ExpectOp(exp.getExpected()));
  }

  @Override
  public void visit(IfStatement conditional) {
    Iterator<Statement> stmtIter = conditional.getStmts().iterator();
    Branch branch = new Branch(Register.NULL,false);
    Goto gotoExit = new Goto();
    for (Expression cond : conditional.getConds()) {
      branch.setBranchTarget(currentPc());
      cond.accept(this);
      Register conditionReg = operands.pop();
      branch = new Branch(conditionReg,false);
      temps.release(conditionReg);
      emit(branch);
      stmtIter.next().accept(this);
      emit(gotoExit);
    }
    branch.setBranchTarget(currentPc());
    if (stmtIter.hasNext()) {
      stmtIter.next().accept(this);
    }
    gotoExit.setBranchTarget(currentPc());
  }

  @Override
  public void visit(IntrinsicMethod method) {
    throw new RuntimeException("You can't compile an intrinsic method!!!");
  }

  @Override
  public void visit(LoadField load) {
    load.getIndex().accept(this);
    Register index = operands.pop();
    Register object = Register.createLocal(load.getSlot());
    Register result = temps.acquire();
    emit(new org.mmtk.harness.lang.pcode.LoadFieldOp(result,object,index,load.getFieldType()));
    temps.release(index);
    operands.push(result);
  }

  @Override
  public void visit(NormalMethod method) {
    method.getBody().accept(this);
    emit(new ReturnOp());
  }

  @Override
  public void visit(PrintStatement print) {
    List<Expression> args = print.getArgs();
    List<Register> actuals = compileArgList(args);
    emit(new PrintOp(actuals));
    freeTemps(actuals);
  }

  @Override
  public void visit(Return ret) {
    if (ret.hasReturnValue()) {
      ret.getRhs().accept(this);
      emit(new ReturnOp(operands.pop()));
    } else {
      emit(new ReturnOp());
    }
  }

  @Override
  public void visit(Sequence ass) {
    for (Statement stmt : ass) {
      stmt.accept(this);
    }
  }

  @Override
  public void visit(Spawn sp) {
    List<Register> actuals = compileArgList(sp.getArgs());
    emit(new SpawnOp(compiledMethodFor(sp.getMethod()),actuals));
    freeTemps(actuals);
  }

  @Override
  public void visit(StoreField store) {
    store.getIndex().accept(this);
    Register index = operands.pop();
    store.getRhs().accept(this);
    Register value = operands.pop();
    Register object = Register.createLocal(store.getSlot());
    emit(new StoreFieldOp(object,index,value,store.getFieldType()));
    temps.release(index,value);
  }

  @Override
  public void visit(UnaryExpression exp) {
    exp.getOperand().accept(this);
    Register operand = operands.peek();
    emit(new UnaryOperation(operand,operand,exp.getOperator()));
  }

  /**
   * Variable reference: push an operand which will fetch
   * the appropriate stack frame slot.
   */
  @Override
  public void visit(Variable var) {
    operands.push(Register.createLocal(var.getSlot()));
  }

  /**
   * While statement.  Compiled to:
   *   top: condition
   *        if false goto b
   *        body
   *        goto top:
   *   b:
   */
  @Override
  public void visit(WhileStatement w) {
    int top = currentPc();
    w.getCond().accept(this);          // Compile the loop condition
    Register cond = operands.pop();
    Branch branchToExit = new Branch(cond,false);
    temps.release(cond);
    emit(branchToExit);
    w.getBody().accept(this);
    emit(new Goto(top));
    branchToExit.setBranchTarget(currentPc());
  }

}
