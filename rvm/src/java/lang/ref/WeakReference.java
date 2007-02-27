/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp 2003
 */
package java.lang.ref;

import com.ibm.jikesrvm.memorymanagers.mminterface.MM_Interface;

/**
 * Implementation of java.lang.ref.WeakReference for JikesRVM.
 * 
 * @author Chris Hoffmann
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
