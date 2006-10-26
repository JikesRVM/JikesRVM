/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp 2003
 */
//$Id$
package java.lang.ref;

import com.ibm.jikesrvm.memoryManagers.mmInterface.MM_Interface;

/**
 * Implementation of java.lang.ref.SoftReference for JikesRVM.
 * @author Chris Hoffmann
 */
public class SoftReference extends Reference {

  public SoftReference(Object referent) {
    super(referent);
    MM_Interface.addSoftReference(this);
  }

  public SoftReference(Object referent, ReferenceQueue q) {
    super(referent, q);
    MM_Interface.addSoftReference(this);
  }
  
}
