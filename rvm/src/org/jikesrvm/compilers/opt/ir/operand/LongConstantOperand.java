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
package org.jikesrvm.compilers.opt.ir.operand;

import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.compilers.opt.util.Bits;
import org.jikesrvm.runtime.Statics;
import org.vmmagic.unboxed.Offset;

/**
 * Represents a constant long operand.
 *
 * @see Operand
 */
public final class LongConstantOperand extends ConstantOperand {

  /**
   * Constant 0, can be copied as convenient
   */
  public static final LongConstantOperand zero =
    new LongConstantOperand(0, Statics.slotAsOffset(Statics.findOrCreateLongSizeLiteral(0)));

  /**
   * Value of this operand.
   */
  public long value;

  /**
   * Offset in JTOC where this long constant lives. (0 for constants
   * obtained from constant folding)
   * //KV: is this field still necessary
   */
  public Offset offset;

  /**
   * Constructs a new long constant operand with the specified value.
   *
   * @param v value
   */
  public LongConstantOperand(long v) {
    value = v;
    offset = Offset.zero();
  }

  /**
   * Constructs a new long constant operand with the specified value and JTOC offset.
   * //KV: is this method still necessary
   * @param v value
   * @param i offset in the jtoc
   */
  public LongConstantOperand(long v, Offset i) {
    value = v;
    offset = i;
  }

  /**
   * @return {@link TypeReference#Long}
   */
  @Override
  public TypeReference getType() {
    return TypeReference.Long;
  }

  /**
   * @return <code>true</code>
   */
  @Override
  public boolean isLong() {
    return true;
  }

  /**
   * Return the lower 32 bits (as an int) of value
   */
  public int lower32() {
    return Bits.lower32(value);
  }

  /**
   * Return the upper 32 bits (as an int) of value
   */
  public int upper32() {
    return Bits.upper32(value);
  }

  @Override
  public Operand copy() {
    return new LongConstantOperand(value, offset);
  }

  @Override
  public boolean similar(Operand op) {
    return (op instanceof LongConstantOperand) && (value == ((LongConstantOperand) op).value);
  }

  /**
   * Returns the string representation of this operand.
   *
   * @return a string representation of this operand.
   */
  @Override
  public String toString() {
    return Long.toString(value) + "L";
  }

}
