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

public class RVM_957_Some {

  private Object x;

  public RVM_957_Some() {
    x = null;
  }

  public RVM_957_Some(Object x) {
    this.x = x;
  }

  public Object get() {
    return x;
  }

  @Override
  public String toString() {
    return "Some";
  }
}
