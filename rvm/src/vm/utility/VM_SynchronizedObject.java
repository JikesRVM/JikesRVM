/*
 * (C) Copyright IBM Corp. 2001
 */

// $Id$
package com.ibm.JikesRVM;

/**
 * This interface designates an object which should always be allocated a
 * thin lock, since it is likely to be synchronized.
 *
 * @author Stephen Fink
 */
public interface VM_SynchronizedObject {
}
