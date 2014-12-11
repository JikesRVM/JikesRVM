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
 * A basic interval contained in a CompoundInterval.
 */
class MappedBasicInterval extends BasicInterval {
  final CompoundInterval container;

  MappedBasicInterval(BasicInterval b, CompoundInterval c) {
    super(b.begin, b.end);
    this.container = c;
  }

  MappedBasicInterval(int begin, int end, CompoundInterval c) {
    super(begin, end);
    this.container = c;
  }

  @Override
  public boolean equals(Object o) {
    if (super.equals(o)) {
      MappedBasicInterval i = (MappedBasicInterval) o;
      return container == i.container;
    } else {
      return false;
    }
  }

  // Note that it is not necessary to overwrite hashCode() because
  // it need only be the case that equal objects have equal hash codes
  // which is already handled by BasicInterval.

  @Override
  public String toString() {
    return "<" + container.getRegister() + ">:" + super.toString();
  }

}
