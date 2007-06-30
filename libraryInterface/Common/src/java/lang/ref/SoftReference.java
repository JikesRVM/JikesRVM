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
 * Implementation of java.lang.ref.SoftReference for JikesRVM.
 */
public class SoftReference<T> extends Reference<T> {

  public SoftReference(T referent) {
    super(referent);
    MM_Interface.addSoftReference(this);
  }

  public SoftReference(T referent, ReferenceQueue<T> q) {
    super(referent, q);
    MM_Interface.addSoftReference(this);
  }

}
