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
import org.mmtk.harness.lang.ast.AllocUserType;
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
import org.mmtk.harness.lang.ast.LoadNamedField;
import org.mmtk.harness.lang.ast.Method;
import org.mmtk.harness.lang.ast.NormalMethod;
import org.mmtk.harness.lang.ast.Operator;
import org.mmtk.harness.lang.ast.PrintStatement;
import org.mmtk.harness.lang.ast.Return;
import org.mmtk.harness.lang.ast.Sequence;
import org.mmtk.harness.lang.ast.Spawn;
import org.mmtk.harness.lang.ast.Statement;
import org.mmtk.harness.lang.ast.StoreField;
import org.mmtk.harness.lang.ast.StoreNamedField;
import org.mmtk.harness.lang.ast.UnaryExpression;
import org.mmtk.harness.lang.ast.Variable;
import org.mmtk.harness.lang.ast.WhileStatement;
import org.mmtk.harness.lang.parser.MethodTable;
import org.mmtk.harness.lang.type.Field;
import org.mmtk.harness.lang.type.Type;
import org.mmtk.harness.lang.type.UserType;

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
   * The type of the current method
   */
  private Type returnType;

  /**
   * Variable initialisation status
   */
  private boolean[] isInitialized;

  /**
   * Visit an expression and return its type.
   * @param expr
   * @return
   */
  private Type getTypeOf(Expression expr) {
    Type type = (Type)expr.accept(this);
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
    Type type = getTypeOf(expr);
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
  private static void fail(AST ast, String message, Object...params) {
    String fullMessage = String.format(message,params);
    System.err.printf("Error at line %d: %s%n",ast.getLine(),fullMessage);
    PrettyPrinter.print(System.err, ast); System.err.println();
    throw new CheckerException(fullMessage);
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
  public Object visit(Alloc alloc) {
    if (!checkType(alloc.getRefCount(),Type.INT)) {
      fail(alloc,"Allocation reference count must be integer");
    }
    if (!checkType(alloc.getDataCount(),Type.INT)) {
      fail(alloc,"Allocation data count must be integer");
    }
    if (!checkType(alloc.getDoubleAlign(),Type.BOOLEAN)) {
      fail(alloc,"Allocation double align must be boolean");
    }
    return Type.OBJECT;
  }

  @Override
  public Object visit(AllocUserType alloc) {
    if (!alloc.getType().isObject() || alloc.getType() == Type.OBJECT) {
      fail(alloc,"Can't allocate a %s using alloc(type)",alloc.getType().toString());
    }
    return alloc.getType();
  }

  @Override
  public Object visit(Assert ass) {
    checkType(ass.getPredicate(),Type.BOOLEAN);
    return Type.VOID;
  }

  @Override
  public Object visit(Assignment a) {
    isInitialized[a.getSlot()] = true;
    Type lhsType = a.getSymbol().getType();
    checkType(a.getRhs(),lhsType);
    return Type.VOID;
  }

  @Override
  public Object visit(BinaryExpression exp) {
    Type lhsType = getTypeOf(exp.getLhs());
    Type rhsType = getTypeOf(exp.getRhs());
    Operator op = exp.getOperator();
    boolean ok = true;
    if (lhsType != rhsType) {
      // Allow boolean/object comparisons
      if (op == Operator.EQ || op == Operator.NE) {
        if ((lhsType == Type.BOOLEAN && rhsType.isObject()) ||
            (lhsType.isObject() && rhsType == Type.BOOLEAN)) {
          ok = true;
        } else if (lhsType.isObject() && rhsType.isObject()){
          ok = true;
        } else {
          ok = false;
        }
      } else {
        ok = false;
      }
      if (!ok) {
        fail(exp,"Type mismatch between "+lhsType+" and "+rhsType);
      }
    }
    if (Operator.booleanOperators.contains(op)) {
      return Type.BOOLEAN;
    } else {
      return lhsType;
    }
  }

  @Override
  public Object visit(Call call) {
    Method m = call.getMethod();
    if (call.getParams().size() != m.getParamCount()) {
      fail(call,"Wrong number of parameters");
    }

    List<Type> actualTypes = new ArrayList<Type>();
    /* Type-check the actual parameter expressions */
    for (Expression param : call.getParams()) {
      actualTypes.add(getTypeOf(param));
    }
    checkParams(call, actualTypes, m.getParamTypes());
    if (call.isExpression()) {
      return call.getMethod().getReturnType();
    }
    return Type.VOID;
  }

  @Override
  public Object visit(Constant c) {
    return c.value.type();
  }

  @Override
  public Object visit(Empty e) {
    return Type.VOID;
  }

  @Override
  public Object visit(Expect exc) {
    return Type.VOID;
  }

  @Override
  public Object visit(IfStatement conditional) {
    for (Expression e : conditional.getConds()) {
      if (!checkType(e,Type.BOOLEAN)) {
        fail(e,"Conditional must have type BOOLEAN");
      }
    }
    for (Statement s : conditional.getStmts()) {
      s.accept(this);
    }
    return Type.VOID;
  }

  @Override
  public Object visit(LoadField load) {
    if (load.getObjectSymbol().getType() != Type.OBJECT) {
      fail(load,"Target of loadfield must be an Object");
    }
    if (getTypeOf(load.getIndex()) != Type.INT) {
      fail(load,"Loadfield index must have type INTEGER");
    }
    return load.getFieldType();
  }

  @Override
  public Object visit(LoadNamedField load) {
    Type t = load.getObjectSymbol().getType();
    if (!t.isObject()) {
      fail(load,"Target of loadfield must be an Object type");
    }
    UserType objectType = (UserType)t;
    Field field = objectType.getField(load.getFieldName());
    if (field == null) {
      fail(load,"Type %s does not have a field called %s",t,load.getFieldName());
    }
    return field.getType();
  }

  @Override
  public Object visit(NormalMethod method) {
    isInitialized = new boolean[method.getDecls().size()];
    for (Declaration decl : method.getParams()) {
      isInitialized[decl.getSlot()] = true;
    }
    returnType = method.getReturnType();
    method.getBody().accept(this);
    return returnType;
  }

  @Override
  public Object visit(PrintStatement print) {
    for (Expression exp : print.getArgs()) {
      exp.accept(this);
    }
    return Type.VOID;
  }

  /**
   * Checks that
   * - The expression returned is internally consistent
   * - The type of the return value is compatible with the
   *   declared type of the method
   */
  @Override
  public Object visit(Return ret) {
    if (ret.hasReturnValue()) {
      Type type = getTypeOf(ret.getRhs());
      if (!returnType.isCompatibleWith(type)) {
        fail(ret,"Returning a "+type+" in a method declared as "+returnType);
      }
      return type;
    } else if (returnType != Type.VOID) {
      fail(ret,"Returning from a non-Object method requires a return value");
    }
    return Type.VOID;
  }

  @Override
  public Object visit(Sequence seq) {
    return super.visit(seq);
  }

  /**
   * Check
   * - Actual parameter expressions
   * - Actual parameters against method formal parameters
   */
  @Override
  public Object visit(Spawn sp) {
    List<Type> actualTypes = new ArrayList<Type>();
    for (Expression expr : sp.getArgs()) {
      actualTypes.add(getTypeOf(expr));
    }
    checkParams(sp, actualTypes, sp.getMethod().getParamTypes());
    return Type.VOID;
  }

  @Override
  public Object visit(StoreField store) {
    if (store.getObjectSymbol().getType() != Type.OBJECT) {
      fail(store,"Target of storefield must be an Object");
    }
    if (getTypeOf(store.getIndex()) != Type.INT) {
      fail(store,"Storefield index must have type INTEGER");
    }
    Type rhsType = getTypeOf(store.getRhs());
    Type fieldType = store.getFieldType();
    if (!fieldType.isCompatibleWith(rhsType)) {
      fail(store,"Storefield to a "+fieldType+" must have type "+fieldType+", not "+rhsType);
    }
    return Type.VOID;
  }

  @Override
  public Object visit(StoreNamedField store) {
    assert store.getObjectSymbol() != null;
    assert store.getObjectSymbol().getType() != null;
    Type t = store.getObjectSymbol().getType();
    if (!t.isObject()) {
      fail(store,"Target of storefield.name (%s) must be an object type, not %s",
          store.getObjectSymbol().getName(),t.getName());
    }
    UserType objectType = (UserType)t;
    Field field = objectType.getField(store.getFieldName());
    if (field == null) {
      fail(store,"The type "+objectType+" does not have a field called "+store.getFieldName());
    }
    Type fieldType = field.getType();
    if (!fieldType.isCompatibleWith(getTypeOf(store.getRhs()))) {
      fail(store,"Storefield to a "+fieldType+" must have type "+fieldType);
    }
    return Type.VOID;
  }

  @Override
  public Object visit(UnaryExpression exp) {
    /* Unary operators preserve type */
    Type type = getTypeOf(exp.getOperand());
    /* With this one exception ... */
    if (exp.getOperator() == Operator.NOT && type == Type.OBJECT) {
      return Type.BOOLEAN;
    }
    return type;
  }

  @Override
  public Object visit(Variable var) {
    if (!isInitialized[var.getSlot()]) {
      fail(var,"Variable "+var.getSymbol().getName()+" is not initialized before use");
    }
    return var.getSymbol().getType();
  }

  @Override
  public Object visit(WhileStatement w) {
      if (!checkType(w.getCond(),Type.BOOLEAN,Type.OBJECT)) {
        fail(w,"While condition must have type BOOLEAN");
      }
    w.getBody().accept(this);
    return Type.VOID;
  }

}
