/*
 * (C) Copyright IBM Corp. 2001
 */

// $Id$
package com.ibm.JikesRVM;

/**
 * This class defines an object which should always be allocated a
 * thin lock, since it is likely to be synchronized.
 *
 * @author Stephen Fink
 */
public class VM_Synchronizer implements VM_SynchronizedObject {
}
