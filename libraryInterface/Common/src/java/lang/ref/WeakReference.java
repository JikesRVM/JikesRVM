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
 * Implementation of java.lang.ref.WeakReference for JikesRVM.
 * 
 * @see java.util.WeakHashMap
 */
public class WeakReference<T> extends Reference<T> {

  public WeakReference(T referent) {
    super(referent);
    MM_Interface.addWeakReference(this);
  }

  public WeakReference(T referent, ReferenceQueue<T> q) {
    super(referent, q);
    MM_Interface.addWeakReference(this);
  }

}
