/*
 * (C) Copyright IBM Corp. 2001
 */
// $Id$

/**
 * VM_CounterNameFunction.java
 *
 * @author Stephen Fink
 *
 * This interface defines a function that takes an integer and
 * returns a string corresponding to that integer.
 *
 **/

interface VM_CounterNameFunction {

   String getName(int key);
}
