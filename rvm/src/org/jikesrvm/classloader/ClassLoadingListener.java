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
package org.jikesrvm.classloader;

/**
 * Interface for callbacks on classloading events.
 * Just before a class is marked as INITIALIZED, RVMClass.initialize()
 * invokes listeners that implement this interface.
 */
public interface ClassLoadingListener {

  void classInitialized(RVMClass c, boolean writingBootImage);

}
