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
package org.jikesrvm.adaptive.measurements.instrumentation;

/**
 * VM_CounterNameFunction.java
 *
 *
 * This interface defines a function that takes an integer and
 * returns a string corresponding to that integer.
 *
 **/

interface VM_CounterNameFunction {

  String getName(int key);
}
