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
package org.jikesrvm.compilers.opt.regalloc;


/**
 * The following represents the intervals assigned to a particular spill
 * location
 */
class SpillLocationInterval extends CompoundInterval {
  /** Support for Set serialization */
  static final long serialVersionUID = 2854333172650538517L;
  /**
   * The spill location, an offset from the frame pointer
   */
  private final int frameOffset;
  /* type of the register that is being spilled */
  private final int type;

  int getOffset() {
    return frameOffset;
  }

  /**
   * The size of the spill location, in bytes.
   */
  private final int size;

  int getSize() {
    return size;
  }

  SpillLocationInterval(int frameOffset, int size, int type) {
    super(null);
    this.frameOffset = frameOffset;
    this.size = size;
    this.type = type;
  }

  public int getType() {
    return type;
  }

  @Override
  public String toString() {
    return super.toString() + "<Offset: " + frameOffset + ", size: " +
        size + ", type: " + type + ">";
  }

  /**
   * Redefine hash code for reproducibility.
   */
  @Override
  public int hashCode() {
    BasicInterval first = first();
    BasicInterval last = last();
    return frameOffset + (first.getBegin() << 4) + (last.getEnd() << 12);
  }
}
