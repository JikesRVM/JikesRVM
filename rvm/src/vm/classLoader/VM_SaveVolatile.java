/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

/**
 * Methods of a class that implements this interface
 * are treated specially by the machine code compiler:
 * the method prologue saves all the volatile registers
 * and the method epilogue restores all the volatile registers
 *
 * @author Bowen Alpern
 * @author Derek Lieber
 */
interface VM_SaveVolatile  { }
