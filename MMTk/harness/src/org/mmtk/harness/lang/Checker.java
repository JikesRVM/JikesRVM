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

import org.mmtk.harness.lang.Trace.Item;
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
import org.mmtk.harness.lang.ast.LoadField;
import org.mmtk.harness.lang.ast.Method;
import org.mmtk.harness.lang.ast.NormalMethod;
import org.mmtk.harness.lang.ast.Operator;
import org.mmtk.harness.lang.ast.PrintStatement;
import org.mmtk.harness.lang.ast.Return;
import org.mmtk.harness.lang.ast.Sequence;
import org.mmtk.harness.lang.ast.Spawn;
import org.mmtk.harness.lang.ast.Statement;
import org.mmtk.harness.lang.ast.StoreField;
import org.mmtk.harness.lang.ast.Type;
import org.mmtk.harness.lang.ast.UnaryExpression;
import org.mmtk.harness.lang.ast.Variable;
import org.mmtk.harness.lang.ast.WhileStatement;
import org.mmtk.harness.lang.parser.MethodTable;
import org.mmtk.harness.lang.runtime.Value;

/**
 * A type-checker visitor for MMTk scripts
 */
public class Checker extends Visitor {

  /**
   * Type check a script, represented by its method table.
   * @param methods
   */
  public static void typeCheck(MethodTable methods) {
    Checker checker = new Checker();
    for (Method m : methods.normalMethods()) {
      m.accept(checker);
    }
  }

  /**
   * The type of the most recently visited expression.
   */
  private Type type;

  /**
   * The type of the current method
   */
  private Type returnType;

  /**
   * Variable initialisation status
   */
  private boolean[] isInitialized;

  /**
   * @return The type of the most recently visited expression.
   */
  public Type getType() { return type; }

  /**
   * Visit an expression and return its type.
   * @param expr
   * @return
   */
  private Type getTypeOf(Expression expr) {
    expr.accept(this);
    Trace.trace(Item.CHECKER,"Type of %s is %s%n",PrettyPrinter.format(expr),type.toString());
    return type;
  }

  /**
   * Visit an expression, and return true if the result type is in the given
   * list of types.
   * @param expr
   * @param types
   * @return
   */
  private boolean checkType(Expression expr, Type...types) {
    getTypeOf(expr);
    for (Type t : types) {
      if (type == t) {
        return true;
      }
    }
    return false;
  }

  /**
   * Report an error and exit
   * @param ast
   * @param message
   */
  private static void fail(AST ast, String message) {
    System.err.printf("Error at line %d: %s%n",ast.getLine(),message);
    PrettyPrinter.print(System.err, ast); System.err.println();
    throw new RuntimeException(message);
  }

  private void checkParams(AST marker, List<Type> actualTypes, List<Type> formalTypes) {
    Iterator<Type> actualTypeIter = actualTypes.iterator();
    for (Type actualParamType : formalTypes) {
      Type formalParamType = actualTypeIter.next();
      if (!formalParamType.isCompatibleWith(actualParamType)) {
        fail(marker,"Actual parameter of type "+actualParamType+
            " is incompatible with formal param of type "+formalParamType);
      }
    }
  }

  /******************************************************************************
   *
   *                 Visitor methods
   *
   */


  @Override
  public void visit(Alloc alloc) {
    if (!checkType(alloc.getRefCount(),Type.INT)) {
      fail(alloc,"Allocation reference count must be integer");
    }
    if (!checkType(alloc.getDataCount(),Type.INT)) {
      fail(alloc,"Allocation data count must be integer");
    }
    if (!checkType(alloc.getDoubleAlign(),Type.BOOLEAN)) {
      fail(alloc,"Allocation double align must be boolean");
    }
    type = Type.OBJECT;
  }

  @Override
  public void visit(Assert ass) {
    checkType(ass.getPredicate(),Type.BOOLEAN);
    type = Type.VOID;
  }

  @Override
  public void visit(Assignment a) {
    isInitialized[a.getSlot()] = true;
    Type lhsType = a.getSymbol().getType();
    checkType(a.getRhs(),lhsType);
    type = Type.VOID;
  }

  @Override
  public void visit(BinaryExpression exp) {
    Type lhsType = getTypeOf(exp.getLhs());
    Type rhsType = getTypeOf(exp.getRhs());
    Operator op = exp.getOperator();
    boolean ok = true;
    if (lhsType != rhsType) {
      // Allow boolean/object comparisons
      if (op == Operator.EQ || op == Operator.NE) {
        if ((lhsType == Type.BOOLEAN && rhsType == Type.OBJECT) ||
            (lhsType == Type.OBJECT && rhsType == Type.BOOLEAN)) {
          ok = true;
        } else {
          ok = false;
        }
      } else {
        ok = false;
      }
      if (!ok) {
        fail(exp,"Type mismatch");
      }
    }
    if (Operator.booleanOperators.contains(op)) {
      type = Type.BOOLEAN;
    } else {
      type = lhsType;
    }
  }

  @Override
  public void visit(Call call) {
    Method m = call.getMethod();
    if (call.getParams().size() != m.getParamCount()) {
      fail(call,"Wrong number of parameters");
    }

    List<Type> actualTypes = new ArrayList<Type>();
    /* Type-check the actual parameter expressions */
    for (Expression param : call.getParams()) {
      param.accept(this);
      actualTypes.add(type);
    }
    checkParams(call, actualTypes, m.getParamTypes());
    if (call.isExpression()) {
      type = call.getMethod().getReturnType();
    } else {
      type = Type.VOID;
    }
  }

  @Override
  public void visit(Constant c) {
    type = c.value.type();
  }

  @Override
  public void visit(Empty e) {
    type = Type.VOID;
  }

  @Override
  public void visit(Expect exc) {
    type = Type.VOID;
  }

  @Override
  public void visit(IfStatement conditional) {
    for (Expression e : conditional.getConds()) {
      if (!checkType(e,Type.BOOLEAN)) {
        fail(e,"Conditional must have type BOOLEAN");
      }
    }
    for (Statement s : conditional.getStmts()) {
      s.accept(this);
    }
    type = Type.VOID;
  }

  @Override
  public void visit(LoadField load) {
    if (load.getObjectSymbol().getType() != Type.OBJECT) {
      fail(load,"Target of loadfield must be an Object");
    }
    load.getIndex().accept(this);
    if (type != Type.INT) {
      fail(load,"Loadfield index must have type INTEGER");
    }
    type = load.getFieldType();
  }

  @Override
  public void visit(NormalMethod method) {
    isInitialized = new boolean[method.getDecls().size()];
    for (Declaration decl : method.getParams()) {
      isInitialized[decl.getSlot()] = true;
    }
    returnType = method.getReturnType();
    method.getBody().accept(this);
    type = returnType;
  }

  @Override
  public void visit(PrintStatement print) {
    for (Expression exp : print.getArgs()) {
      exp.accept(this);
    }
    type = Type.VOID;
  }

  /**
   * Checks that
   * - The expression returned is internally consistent
   * - The type of the return value is compatible with the
   *   declared type of the method
   */
  @Override
  public void visit(Return ret) {
    if (ret.hasReturnValue()) {
      ret.getRhs().accept(this);
      if (!returnType.isCompatibleWith(type)) {
        fail(ret,"Returning a "+type+" in a method declared as "+returnType);
      }
    } else if (returnType != Type.VOID) {
      fail(ret,"Returning from a non-void method requires a return value");
    } else {
      type = Type.VOID;
    }
  }

  @Override
  public void visit(Sequence ass) {
    super.visit(ass);
  }

  /**
   * Check
   * - Actual parameter expressions
   * - Actual parameters against method formal parameters
   */
  @Override
  public void visit(Spawn sp) {
    List<Type> actualTypes = new ArrayList<Type>();
    for (Expression expr : sp.getArgs()) {
      expr.accept(this);
      actualTypes.add(type);
    }
    checkParams(sp, actualTypes, sp.getMethod().getParamTypes());
    type = Type.VOID;
  }

  @Override
  public void visit(StoreField store) {
    if (store.getObjectSymbol().getType() != Type.OBJECT) {
      fail(store,"Target of storefield must be an Object");
    }
    if (getTypeOf(store.getIndex()) != Type.INT) {
      fail(store,"Storefield index must have type INTEGER");
    }
    if (getTypeOf(store.getRhs()) != store.getFieldType()) {
      fail(store,"Storefield to a "+store.getFieldType()+" must have type "+store.getFieldType());
    }
    type = Type.VOID;
  }

  @Override
  public void visit(UnaryExpression exp) {
    /* Unary operators preserve type */
    exp.getOperand().accept(this);
    /* With this one exception ... */
    if (exp.getOperator() == Operator.NOT && type == Type.OBJECT) {
      type = Type.BOOLEAN;
    }
  }

  @Override
  public void visit(Value v) {
    type = v.type();
  }

  @Override
  public void visit(Variable var) {
    if (!isInitialized[var.getSlot()]) {
      fail(var,"Variable "+var.getSymbol().getName()+" is not initialized before use");
    }
    type = var.getSymbol().getType();
  }

  @Override
  public void visit(WhileStatement w) {
      if (!checkType(w.getCond(),Type.BOOLEAN,Type.OBJECT)) {
        fail(w,"While condition must have type BOOLEAN");
      }
    w.getBody().accept(this);
    type = Type.VOID;
  }

}
