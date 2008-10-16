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

import org.mmtk.harness.lang.ast.AST;
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
import org.mmtk.harness.lang.ast.MethodProxy;
import org.mmtk.harness.lang.ast.NormalMethod;
import org.mmtk.harness.lang.ast.Operator;
import org.mmtk.harness.lang.ast.PrintStatement;
import org.mmtk.harness.lang.ast.Return;
import org.mmtk.harness.lang.ast.Sequence;
import org.mmtk.harness.lang.ast.Spawn;
import org.mmtk.harness.lang.ast.Statement;
import org.mmtk.harness.lang.ast.StoreField;
import org.mmtk.harness.lang.ast.UnaryExpression;
import org.mmtk.harness.lang.ast.Variable;
import org.mmtk.harness.lang.ast.WhileStatement;

/**
 * Abstract visitor class for ASTs.  Provides default implementations
 * for all composite AST classes.
 */
public abstract class Visitor {

  public void visit(AST ast) { }
  public void visit(Alloc alloc) {
    alloc.getDataCount().accept(this);
    alloc.getRefCount().accept(this);
    alloc.getDoubleAlign().accept(this);
  }
  public void visit(Assert ass) {
    ass.getPredicate().accept(this);
    for (AST a : ass.getOutputs()) {
      a.accept(this);
    }
  }
  public void visit(Assignment a) {
    a.getRhs().accept(this);
  }
  public void visit(BinaryExpression exp) {
    exp.getLhs().accept(this);
    exp.getOperator().accept(this);
    exp.getRhs().accept(this);
  }
  public void visit(Call call) {
    for (Expression param : call.getParams()) {
      param.accept(this);
    }
    call.getMethod().accept(this);
  }
  public void visit(Constant c) { }
  public void visit(Declaration decl) { }
  public void visit(Empty e) { }
  public void visit(Expect exc) { }
  public void visit(IfStatement conditional) {
    for (Expression cond : conditional.getConds()) {
      cond.accept(this);
    }
    for (Statement stmt: conditional.getStmts()) {
      stmt.accept(this);
    }
  }
  public void visit(IntrinsicMethod method) {
  }
  public void visit(LoadField load) {
    load.getIndex().accept(this);
  }
  public void visit(Method method) {
    System.err.println("Fall-through to Method visitor");
  }
  public void visit(MethodProxy proxy) {
    proxy.getMethod().accept(this);
  }
  public void visit(NormalMethod method) {
    for (Declaration decl : method.getDecls()) {
      decl.accept(this);
    }
    method.getBody().accept(this);
  }
  public void visit(Operator op) {
    op.accept(this);
  }
  public void visit(PrintStatement print) {
    for (Expression e : print.getArgs()) {
      e.accept(this);
    }
  }
  public void visit(Return ret) {
    if (ret.hasReturnValue()) {
      ret.getRhs().accept(this);
    }
  }
  public void visit(Sequence ass) {
    for (Statement stmt : ass) {
      stmt.accept(this);
    }
  }
  public void visit(Spawn sp) {
    for (AST arg : sp.getArgs()) {
      arg.accept(this);
    }
    sp.getMethod().accept(this);
  }
  public void visit(StoreField store) {
    store.getIndex().accept(this);
    store.getRhs().accept(this);
  }
  public void visit(UnaryExpression exp) {
    exp.getOperator().accept(this);
    exp.getOperand().accept(this);
  }
  public void visit(Variable var) { }
  public void visit(WhileStatement w) {
    w.getCond().accept(this);
    w.getBody().accept(this);
  }
}
