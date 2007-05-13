/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
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
