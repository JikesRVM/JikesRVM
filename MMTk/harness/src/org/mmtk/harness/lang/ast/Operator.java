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
package org.mmtk.harness.lang.ast;

import java.util.EnumSet;
import java.util.Set;

import org.mmtk.harness.lang.Visitor;
import org.mmtk.harness.lang.runtime.BoolValue;
import org.mmtk.harness.lang.runtime.IntValue;
import org.mmtk.harness.lang.runtime.Value;
import org.mmtk.harness.lang.type.Type;

/**
 * Arithmetic and logical operators
 * <p>
 * Operations themselves implemented in the appropriate Expression types
 */
public enum Operator {
  /* Equality */
  EQ("==") {
    @Override public Value operate(Value lhs, Value rhs) {
      return BoolValue.valueOf(lhs.equals(rhs));
    }
  }, NE("!=") {
    @Override public Value operate(Value lhs, Value rhs) {
      return BoolValue.valueOf(!lhs.equals(rhs));
    }
  },
  /* Integer comparison */
  GT(">") {
    @Override public Value operate(Value lhs, Value rhs) {
      return BoolValue.valueOf(lhs.getIntValue() > rhs.getIntValue());
    }
  }, LT("<") {
    @Override public Value operate(Value lhs, Value rhs) {
      return BoolValue.valueOf(lhs.getIntValue() < rhs.getIntValue());
    }
  }, LE("<=") {
    @Override public Value operate(Value lhs, Value rhs) {
      return BoolValue.valueOf(lhs.getIntValue() <= rhs.getIntValue());
    }
  }, GE(">=") {
    @Override public Value operate(Value lhs, Value rhs) {
      return BoolValue.valueOf(lhs.getIntValue() >= rhs.getIntValue());
    }
  },
  /* Logical */
  AND("&&") {
    @Override public Value operate(Value lhs, Value rhs) {
      return BoolValue.valueOf(lhs.getBoolValue() && rhs.getBoolValue());
    }
  }, OR("||") {
    @Override public Value operate(Value lhs, Value rhs) {
      return BoolValue.valueOf(lhs.getBoolValue() || rhs.getBoolValue());
    }
  },
  /* Unary */
  NOT("!") {
    @Override public Value operate(Value operand) {
      return BoolValue.valueOf(!operand.getBoolValue());
    }
  },
  /* Mathematical */
  PLUS("+") {
    @Override public Value operate(Value lhs, Value rhs) {
      return new IntValue(lhs.getIntValue() + rhs.getIntValue());
    }
  }, MINUS("-") {
    @Override public Value operate(Value lhs, Value rhs) {
      return new IntValue(lhs.getIntValue() - rhs.getIntValue());
    }
    @Override public Value operate(Value operand) {
      return new IntValue(- operand.getIntValue());
    }
  }, MULT("*") {
    @Override public Value operate(Value lhs, Value rhs) {
      return new IntValue(lhs.getIntValue() * rhs.getIntValue());
    }
  }, DIV("/") {
    @Override
    public Value operate(Value lhs, Value rhs) {
      return new IntValue(lhs.getIntValue() / rhs.getIntValue());
    }
  }, REM("%") {
    @Override public Value operate(Value lhs, Value rhs) {
      return new IntValue(lhs.getIntValue() % rhs.getIntValue());
    }
  }, LS("<<"){
    @Override public Value operate(Value lhs, Value rhs) {
      return new IntValue(lhs.getIntValue() << rhs.getIntValue());
    }
  }, RS(">>"){
    @Override public Value operate(Value lhs, Value rhs) {
      return new IntValue(lhs.getIntValue() >> rhs.getIntValue());
    }
  }, RSL(">>>"){
    @Override public Value operate(Value lhs, Value rhs) {
      return new IntValue(lhs.getIntValue() >>> rhs.getIntValue());
    }
  };

  /*
   * Families of operators
   */
  public static final Set<Operator> allOperators = EnumSet.allOf(Operator.class);
  public static final Set<Operator> unaryOperators = EnumSet.of(NOT, MINUS);
  public static final Set<Operator> binaryOperators = EnumSet.complementOf(EnumSet.of(NOT));
  public static final Set<Operator> booleanOperators = EnumSet.of(
      AND, OR, NOT, GT, LT, LE, GE, EQ, NE);
  public static final Set<Operator> arithmeticOperators = EnumSet.of(
      PLUS, MINUS, MULT, DIV, REM, LS, RS, RSL);

  /** Printable representation */
  private final String image;

  private Operator(String image) {
    this.image = image;
  }

  public final Type resultType(Type lhs, Type rhs) {
    if (booleanOperators.contains(this)) {
      return Type.BOOLEAN;
    }
    assert lhs == rhs;
    return lhs;
  }

  /**
   * @return {@code true} if this is a binary operation
   */
  public boolean isBinary() {
    return binaryOperators.contains(this);
  }

  /**
   * @return {@code true} if this is a unary operation
   */
  public boolean isUnary() {
    return unaryOperators.contains(this);
  }

  @Override
  public String toString() {
    return image;
  }

  public void accept(Visitor v) {
    v.visit(this);
  }

  public Value operate(Value operand) {
    throw new RuntimeException("Unsupported unary operation, "+this);
  }
  public Value operate(Value lhs, Value rhs) {
    throw new RuntimeException("Unsupported binary operation, "+this);
  }
}
