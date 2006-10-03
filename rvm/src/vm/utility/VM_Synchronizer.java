/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
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
