/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

/**
 * Methods of a class that implements this interface are treated specially 
 * by the compilers:
 *  -They are only called from C or C++ program
 *  -The compiler will generate the necessary prolog to insert a glue stack
 *   frame to map from the AIX stack/register convention to RVM's convention
 *  -It is an error to call these methods from Java
 *
 * @author Bowen Alpern
 * @author Derek Lieber
 */ 
interface VM_NativeBridge
{

}
