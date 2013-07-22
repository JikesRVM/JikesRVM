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
package org.mmtk.harness.lang.compiler;

import org.mmtk.harness.lang.type.Type;

/**
 * An abstract register.  Registers represent constant, program variables
 * or temporaries (sub-expression results).
 */
public final class Register {

  public static final Register NULL = new Register(-1,Type.NULL,false);

  static Register createTemporary(int index, Type type) {
    return new Register(index, type, true);
  }

  public static Register createLocal(int index, Type type) {
    return new Register(index, type, false);
  }

  public static Register createConstant(int index, Type type) {
    return new Register(index, type, false);
  }

  /*
   * Instance variables
   */

  private boolean isFree = false;
  private final boolean temporary;

  /** Equality is based purely on the index */
  private final int index;

  private Type type;

  private Register(int index, Type type, boolean temporary) {
    this.index = index;
    this.temporary = temporary;
    this.type = type;
  }

  public boolean isTemporary() {
    return temporary;
  }

  public void setUsed() {
    assert temporary : "Local variable registers don't change status";
    assert isFree : "Attempt to use a non-free temporary";
    isFree = false;
  }

  public void setFree() {
    assert temporary : "Local variable registers don't change status";
    assert !isFree : "Attempt to free a free temporary";
    isFree = true;
  }

  public int getIndex() {
    assert !isFree : "Attempt to get index of a free temporary";
    return index;
  }

  public Type getType() {
    return type;
  }

  public void setType(Type type) {
    this.type = type;
  }

  public static String nameOf(int index) {
    if (index >= 0) {
      return "t"+index;
    }
    return "c"+(-index-1);
  }

  @Override
  public String toString() {
    return nameOf(index);
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + index;
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    Register other = (Register) obj;
    if (index != other.index)
      return false;
    return true;
  }


}
