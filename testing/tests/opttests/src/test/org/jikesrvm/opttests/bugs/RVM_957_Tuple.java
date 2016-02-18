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
package test.org.jikesrvm.opttests.bugs;

import org.vmmagic.pragma.NoInline;

public class RVM_957_Tuple {
  private Object a;
  private Object b;

  public RVM_957_Tuple(Object a, Object b) {
    this.a = a;
    this.b = b;
  }

  // don't inline because this would create an inline guard patch point
  // which isn't supported by the escape transformations
  @NoInline
  public Object _1() {
    return a;
  }

  // don't inline because this would create an inline guard patch point
  // which isn't supported by the escape transformations
  @NoInline
  public Object _2() {
    return b;
  }
}
