/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

/**
 *
 * @author Bowen Alpern
 * @author David Grove
 */
public interface VM_TrapConstants
   {

       /** 
	*  This base is added to the numeric trap codes in VM_Runtime.java
	* to yield the intel trap number that is given to INT instructions
	*/
       public final static byte RVM_TRAP_BASE = (byte) 0x40;

   }
