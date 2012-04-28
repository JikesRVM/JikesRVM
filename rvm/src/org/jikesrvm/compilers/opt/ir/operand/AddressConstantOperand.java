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

import org.jikesrvm.VM;
import org.jikesrvm.SizeConstants;
import org.jikesrvm.classloader.TypeReference;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Extent;
import org.vmmagic.unboxed.Offset;
import org.vmmagic.unboxed.Word;

/**
 * Represents an address constant operand.
 *
 * @see Operand
 */
public final class AddressConstantOperand extends ConstantOperand {

  /**
   * Value of this operand.
   */
  public final Address value;

  /**
   * Constructs a new address constant operand with the specified value.
   *
   * @param v value
   */
  public AddressConstantOperand(Address v) {
    value = v;
  }

  /**
   * Constructs a new address constant operand with the specified offset value.
   *
   * @param v value
   * TODO: make a separte OffsetConstantOperand
   */
  public AddressConstantOperand(Offset v) {
    this(v.toWord().toAddress());
  }

  /**
   * Constructs a new address constant operand with the specified offset value.
   *
   * @param v value
   * TODO: make a separte OffsetConstantOperand
   */
  public AddressConstantOperand(Extent v) {
    this(v.toWord().toAddress());
  }

  /**
   * Constructs a new address constant operand with the specified offset value.
   *
   * @param v value
   * TODO: make a separte OffsetConstantOperand
   */
  public AddressConstantOperand(Word v) {
    this(v.toAddress());
  }

  @Override
  public Operand copy() {
    return new AddressConstantOperand(value);
  }

  /**
   * @return {@link TypeReference#Address}
   */
  @Override
  public TypeReference getType() {
    return TypeReference.Address;
  }

  /**
   * @return <code>true</code>
   */
  @Override
  public boolean isAddress() {
    return true;
  }

  @Override
  public boolean similar(Operand op) {
    return equals(op);
  }

  @Override
  public boolean equals(Object o) {
    return (o instanceof AddressConstantOperand) && (value.EQ(((AddressConstantOperand) o).value));
  }

  @Override
  public int hashCode() {
    return value.toWord().rshl(SizeConstants.LOG_BYTES_IN_ADDRESS).toInt();
  }

  /**
   * Returns the string representation of this operand.
   *
   * @return a string representation of this operand.
   */
  @Override
  public String toString() {
    return "Addr " + VM.addressAsHexString(value);
  }
}
