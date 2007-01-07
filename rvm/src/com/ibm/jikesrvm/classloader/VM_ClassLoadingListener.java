/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001, 2006
 */
// $Id$
package com.ibm.jikesrvm.classloader;

/**
 * Interface for callbacks on classloading events. 
 * Just before a class is marked as INITIALIZED, VM_Class.initialize()
 * invokes listeners that implement this interface.
 *
 * @author Steve Fink
 * @author Dave Grove
 */
public interface VM_ClassLoadingListener {

  public void classInitialized(VM_Class c, boolean writingBootImage);

}
