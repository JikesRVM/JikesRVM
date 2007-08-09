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
package java.lang.ref;

import org.jikesrvm.memorymanagers.mminterface.MM_Interface;

/**
 * Implementation of java.lang.ref.PhantomReference for JikesRVM.
 */
public class PhantomReference<T> extends Reference<T> {

  public PhantomReference(T referent, ReferenceQueue<T> q) {
    super(referent, q);
    MM_Interface.addPhantomReference(this,referent);
  }

  /**
   * Returns the object this reference refers to. Phantom references
   * always return <code>null</code>.
   * @return <code>null</code>
   */
  public T get() {
    return null;
  }
}
