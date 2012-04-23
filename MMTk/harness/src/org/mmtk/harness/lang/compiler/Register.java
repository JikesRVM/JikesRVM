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

public final class Register {

  public static final Register NULL = new Register(-1,false);

  static Register createTemporary(int index) {
    return new Register(index,true);
  }

  public static Register createLocal(int index) {
    return new Register(index,false);
  }

  public static Register createConstant(int index) {
    return new Register(index,false);
  }

  /*
   * Instance variables
   */

  private boolean isFree = false;
  private final boolean temporary;

  private final int index;

  private Register(int index, boolean temporary) {
    this.index = index;
    this.temporary = temporary;
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
}
