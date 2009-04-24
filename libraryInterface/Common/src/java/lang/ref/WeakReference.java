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
package java.lang.ref;

import org.jikesrvm.mm.mminterface.MemoryManager;

/**
 * Implementation of java.lang.ref.WeakReference for JikesRVM.
 *
 * @see java.util.WeakHashMap
 */
public class WeakReference<T> extends Reference<T> {

  public WeakReference(T referent) {
    super(referent);
    MemoryManager.addWeakReference(this,referent);
  }

  public WeakReference(T referent, ReferenceQueue<T> q) {
    super(referent, q);
    MemoryManager.addWeakReference(this,referent);
  }
}
