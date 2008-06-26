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

/**
 * A binary expression.
 */
public class BinaryExpression implements Expression {
  /** The left hand side of the expression. */
  private final Expression lhs;
  /** The right hand side of the expression */
  private final Expression rhs;
  /** The operator */
  private Operator op;

  /**
   * Create a binary expression.
   */
  public BinaryExpression(Expression lhs, Operator op, Expression rhs) {
    this.lhs = lhs;
    this.op = op;
    this.rhs = rhs;
  }

  /**
   * Evaluate the binary expression.
   */
  public Value eval(Env env) {
    Value lhsVal = lhs.eval(env);
    env.pushTemporary(lhsVal);
    env.gcSafePoint();

    Value rhsVal = rhs.eval(env);

    try {
      env.check(lhsVal.type() == rhsVal.type(), "Mismatched types in expression");

      /* Valid for all */
      switch (op) {
        case EQ: return new BoolValue(lhsVal.equals(rhsVal));
        case NE: return new BoolValue(!lhsVal.equals(rhsVal));
        default:
      }

      /* Only valid for boolean */
      if (lhsVal.type() == Type.BOOLEAN && rhsVal.type() == Type.BOOLEAN) {
        switch (op) {
          case AND: return new BoolValue(lhsVal.getBoolValue() && rhsVal.getBoolValue());
          case OR:  return new BoolValue(lhsVal.getBoolValue() || rhsVal.getBoolValue());
          default:
        }
      }

      /* Only valid for numeric */
      if (lhsVal.type() == Type.INT && rhsVal.type() == Type.INT) {
        switch (op) {
          case PLUS:  return new IntValue(lhsVal.getIntValue() + rhsVal.getIntValue());
          case MINUS: return new IntValue(lhsVal.getIntValue() - rhsVal.getIntValue());
          case MULT:  return new IntValue(lhsVal.getIntValue() * rhsVal.getIntValue());
          case DIV:   return new IntValue(lhsVal.getIntValue() / rhsVal.getIntValue());
          case REM:   return new IntValue(lhsVal.getIntValue() % rhsVal.getIntValue());
          case LS:    return new IntValue(lhsVal.getIntValue() << rhsVal.getIntValue());
          case RS:    return new IntValue(lhsVal.getIntValue() >> rhsVal.getIntValue());
          case RSL:   return new IntValue(lhsVal.getIntValue() >>> rhsVal.getIntValue());
          case GT:    return new BoolValue(lhsVal.getIntValue() > rhsVal.getIntValue());
          case LT:    return new BoolValue(lhsVal.getIntValue() < rhsVal.getIntValue());
          case GE:    return new BoolValue(lhsVal.getIntValue() >= rhsVal.getIntValue());
          case LE:    return new BoolValue(lhsVal.getIntValue() <= rhsVal.getIntValue());
          default:
        }
      }

      env.fail("Invalid binary expression " + op.name());
      return null;
    } finally {
      env.popTemporary(lhsVal);
    }
  }
}
