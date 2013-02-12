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
import org.mmtk.harness.lang.ast.LoadNamedField;
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
import org.mmtk.harness.lang.ast.StoreNamedField;
import org.mmtk.harness.lang.ast.TypeLiteral;
import org.mmtk.harness.lang.ast.UnaryExpression;
import org.mmtk.harness.lang.ast.Variable;
import org.mmtk.harness.lang.ast.WhileStatement;

/**
 * Abstract visitor class for ASTs. Provides default implementations
 * for all composite AST classes.
 */
public abstract class Visitor {

  public Object visit(AST ast) { return ast; }
  public Object visit(Alloc alloc) {
    for (Expression arg : alloc.getArgs()) {
      arg.accept(this);
    }
    return alloc;
  }
  public Object visit(Assert ass) {
    ass.getPredicate().accept(this);
    for (AST a : ass.getOutputs()) {
      a.accept(this);
    }
    return ass;
  }
  public Object visit(Assignment a) {
    a.getRhs().accept(this);
    return a;
  }
  public Object visit(BinaryExpression exp) {
    exp.getLhs().accept(this);
    exp.getOperator().accept(this);
    exp.getRhs().accept(this);
    return exp;
  }
  public Object visit(Call call) {
    for (Expression param : call.getParams()) {
      param.accept(this);
    }
    call.getMethod().accept(this);
    return call;
  }
  public Object visit(Constant c) { return c; }
  public Object visit(Declaration decl) { return decl; }
  public Object visit(Empty e) { return e; }
  public Object visit(Expect exc) { return exc; }
  public Object visit(IfStatement conditional) {
    for (Expression cond : conditional.getConds()) {
      cond.accept(this);
    }
    for (Statement stmt: conditional.getStmts()) {
      stmt.accept(this);
    }
    return conditional;
  }
  public Object visit(IntrinsicMethod method) {
    return method;
  }
  public Object visit(LoadField load) {
    load.getIndex().accept(this);
    return load;
  }
  public Object visit(LoadNamedField load) {
    return load;
  }
  public Object visit(Method method) {
    System.err.println("Fall-through to Method visitor");
    return method;
  }
  public Object visit(MethodProxy proxy) {
    proxy.getMethod().accept(this);
    return proxy;
  }
  public Object visit(NormalMethod method) {
    for (Declaration decl : method.getDecls()) {
      decl.accept(this);
    }
    method.getBody().accept(this);
    return method;
  }
  public Object visit(Operator op) {
    op.accept(this);
    return op;
  }
  public Object visit(PrintStatement print) {
    for (Expression e : print.getArgs()) {
      e.accept(this);
    }
    return print;
  }
  public Object visit(Return ret) {
    if (ret.hasReturnValue()) {
      ret.getRhs().accept(this);
    }
    return ret;
  }
  public Object visit(Sequence ass) {
    for (Statement stmt : ass) {
      stmt.accept(this);
    }
    return ass;
  }
  public Object visit(Spawn sp) {
    for (AST arg : sp.getArgs()) {
      arg.accept(this);
    }
    sp.getMethod().accept(this);
    return sp;
  }
  public Object visit(StoreField store) {
    store.getIndex().accept(this);
    store.getRhs().accept(this);
    return store;
  }
  public Object visit(StoreNamedField store) {
    store.getRhs().accept(this);
    return store;
  }
  public Object visit(TypeLiteral type) {
    return type;
  }
  public Object visit(UnaryExpression exp) {
    exp.getOperator().accept(this);
    exp.getOperand().accept(this);
    return exp;
  }
  public Object visit(Variable var) {
    return var;
  }
  public Object visit(WhileStatement w) {
    w.getCond().accept(this);
    w.getBody().accept(this);
    return w;
  }
}
