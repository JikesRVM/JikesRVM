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
package org.mmtk.harness.lang;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.mmtk.harness.Harness;
import org.mmtk.harness.lang.Trace.Item;
import org.mmtk.harness.lang.ast.*;
import org.mmtk.harness.lang.compiler.CompiledMethod;
import org.mmtk.harness.lang.compiler.CompiledMethodProxy;
import org.mmtk.harness.lang.compiler.CompiledMethodTable;
import org.mmtk.harness.lang.compiler.Register;
import org.mmtk.harness.lang.compiler.Temporary;
import org.mmtk.harness.lang.parser.MethodTable;
import org.mmtk.harness.lang.pcode.*;
import org.mmtk.harness.lang.runtime.ConstantPool;
import org.mmtk.harness.lang.type.Field;
import org.mmtk.harness.lang.type.Type;
import org.mmtk.harness.lang.type.UserType;

/**
 * Compile a script to pcode.
 */
public final class Compiler extends Visitor {

  static {
    //Trace.enable(Item.COMPILER);
    //Trace.enable(Item.EVAL);
  }

  private final CompiledMethod current;
  private final CompiledMethodTable methodTable;
  private final Temporary temps;

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
    Trace.trace(Item.COMPILER, "emitting %s", op);
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
      actuals.add(compile(exp));
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

  /**
   * Visit a node and return its result as a Register
   * @param ast
   * @return the result as register
   */
  private Register compile(AST ast) {
    return (Register)ast.accept(this);
  }

  @Override
  public Object visit(Alloc alloc) {
    Register result = temps.acquire();
    Register doubleAlign = ConstantPool.FALSE;
    if (alloc.isTyped()) {
      UserType type = alloc.getType();
      if (alloc.numArgs() >= 2) {
        doubleAlign = compile(alloc.getArg(1));
      }
      emit(new AllocUserOp(alloc,result,type,doubleAlign,alloc.getSite()));
      temps.release(doubleAlign);
    } else {
      Register refCount = compile(alloc.getArg(0));
      Register dataCount = compile(alloc.getArg(1));
      if (alloc.numArgs() >= 3) {
        doubleAlign = compile(alloc.getArg(2));
      }
      emit(new AllocOp(alloc,result,dataCount,refCount,doubleAlign,alloc.getSite()));
      temps.release(dataCount,refCount,doubleAlign);
    }
    return result;
  }

  @Override
  public Object visit(Assert ass) {
    Register predicate = compile(ass.getPredicate());
    Branch branch = new Branch(ass,predicate,true);
    emit(branch);
    temps.release(predicate);
    ArrayList<Register> actuals = new ArrayList<Register>(ass.getOutputs().size());
    for (Expression expr : ass.getOutputs()) {
      actuals.add(compile(expr));
    }
    emit(new PrintOp(ass,actuals.toArray(new Register[0])));
    freeTemps(actuals);
    emit(new ExitOp(ass,ConstantPool.ONE));
    branch.setBranchTarget(currentPc());
    return null;
  }

  @Override
  public Object visit(Assignment a) {
    Trace.trace(Item.COMPILER, "Compiling %s", PrettyPrinter.format(a));
    Register rhs = compile(a.getRhs());
    emit(new StoreLocal(a,Register.createLocal(a.getSlot()),rhs));
    temps.release(rhs);
    return null;
  }

  @Override
  public Object visit(BinaryExpression exp) {
    Register lhs = compile(exp.getLhs());
    Register rhs = compile(exp.getRhs());
    Register result = temps.acquire();
    emit(new BinaryOperation(exp,result,lhs,rhs,exp.getOperator()));
    temps.release(rhs,lhs);
    return result;
  }

  @Override
  public Object visit(Call call) {
    Method method = call.getMethod();
    List<Register> actuals = compileArgList(call.getParams());
    Register returnVal = method.getReturnType() == Type.VOID ?
        Register.NULL : temps.acquire();

    if (method instanceof IntrinsicMethod) {
      emit(new CallIntrinsicOp(call,returnVal,(IntrinsicMethod)method,actuals));
    } else if (method instanceof NormalMethod) {
      CompiledMethod compiledMethod = compiledMethodFor(method);
      emit(new CallNormalOp(call,returnVal,compiledMethod,actuals));
    } else {
      throw new RuntimeException("Unknown method class "+method.getClass().getCanonicalName());
    }
    freeTemps(actuals);
    return returnVal;
  }

  /**
   * Constant value. Push an operand which fetches from the
   * global constant pool
   */
  @Override
  public Object visit(Constant c) {
    return ConstantPool.acquire(c.value);
  }

  @Override
  public Object visit(Empty e) {
    return null;
  }

  @Override
  public Object visit(Expect exp) {
    emit(new ExpectOp(exp,exp.getExpected()));
    return null;
  }

  @Override
  public Object visit(IfStatement conditional) {
    Iterator<Statement> stmtIter = conditional.getStmts().iterator();
    Branch branch = new Branch(conditional,Register.NULL,false);
    Goto gotoExit = new Goto(conditional);
    for (Expression cond : conditional.getConds()) {
      branch.setBranchTarget(currentPc());
      Register conditionReg = compile(cond);
      branch = new Branch(conditional,conditionReg,false);
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
    return null;
  }

  @Override
  public Object visit(IntrinsicMethod method) {
    throw new RuntimeException("You can't compile an intrinsic method!!!");
  }

  @Override
  public Object visit(LoadField load) {
    Register index = compile(load.getIndex());
    Register object = Register.createLocal(load.getSlot());
    Register result = temps.acquire();
    emit(new LoadFieldOp(load,result,object,index,load.getFieldType()));
    temps.release(index);
    return result;
  }

  @Override
  public Object visit(LoadNamedField load) {
    UserType type = (UserType)load.getObjectSymbol().getType();
    Field field = type.getField(load.getFieldName());
    Register object = Register.createLocal(load.getSlot());
    Register result = temps.acquire();
    emit(new LoadFixedFieldOp(load,result,object,field.getOffset(),
        load.getFieldName(),
        field.getType().isObject() ? Type.OBJECT : Type.INT));
    return result;
  }

  @Override
  public Object visit(NormalMethod method) {
    compile(method.getBody());
    emit(new ReturnOp(method));
    return null;
  }

  @Override
  public Object visit(PrintStatement print) {
    List<Expression> args = print.getArgs();
    List<Register> actuals = compileArgList(args);
    emit(new PrintOp(print,actuals));
    freeTemps(actuals);
    return null;
  }

  @Override
  public Object visit(Return ret) {
    if (ret.hasReturnValue()) {
      emit(new ReturnOp(ret,compile(ret.getRhs())));
    } else {
      emit(new ReturnOp(ret));
    }
    return null;
  }

  @Override
  public Object visit(Sequence ass) {
    for (Statement stmt : ass) {
      compile(stmt);
    }
    return null;
  }

  @Override
  public Object visit(Spawn sp) {
    List<Register> actuals = compileArgList(sp.getArgs());
    emit(new SpawnOp(sp,compiledMethodFor(sp.getMethod()),actuals));
    freeTemps(actuals);
    return null;
  }

  @Override
  public Object visit(StoreField store) {
    Register index = compile(store.getIndex());
    Register value = compile(store.getRhs());
    Register object = Register.createLocal(store.getSlot());
    emit(new StoreFieldOp(store,object,index,value,store.getFieldType()));
    temps.release(index,value);
    return null;
  }

  @Override
  public Object visit(StoreNamedField store) {
    Field field = store.getField();
    Register value = compile(store.getRhs());
    Register object = Register.createLocal(store.getSlot());
    emit(new StoreFixedFieldOp(store,object,field.getOffset(),store.getFieldName(),value,
        field.getType().isObject() ? Type.OBJECT : Type.INT));
    temps.release(value);
    return null;
  }

  @Override
  public Object visit(TypeLiteral type) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Object visit(UnaryExpression exp) {
    Register operand = compile(exp.getOperand());
    Register result = temps.acquire();
    emit(new UnaryOperation(exp,result,operand,exp.getOperator()));
    temps.release(operand);
    return result;
  }

  /**
   * Variable reference: push an operand which will fetch
   * the appropriate stack frame slot.
   */
  @Override
  public Object visit(Variable var) {
    return Register.createLocal(var.getSlot());
  }

  /**
   * While statement.  Compiled to:
   * <pre>
   *   top: condition
   *        if false goto b
   *        body
   *        goto top:
   *   b:
   * </pre>
   */
  @Override
  public Object visit(WhileStatement w) {
    int top = currentPc();
    Register cond = compile(w.getCond());
    Branch branchToExit = new Branch(w,cond,false);
    temps.release(cond);
    emit(branchToExit);
    compile(w.getBody());
    emit(new Goto(w,top));
    branchToExit.setBranchTarget(currentPc());
    return null;
  }

}
