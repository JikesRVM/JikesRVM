/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2004
 */
//$Id$

package com.ibm.jikesrvm.memorymanagers.mminterface;

import com.ibm.jikesrvm.VM_SizeConstants;

/**
 * This class merely exposes the MMTk constants into the Jikes RVM
 * package space so that they can be accessed by the VM in an
 * MM-neutral way.
 * 
 * @author Steve Blackburn
 * @version $Revision$
 * @date $Date$
 */  
public class MM_Constants implements VM_SizeConstants {
  public static final boolean NEEDS_LINEAR_SCAN = Selected.Constraints.get().needsLinearScan();
  public static final int GC_HEADER_BITS = Selected.Constraints.get().gcHeaderBits();
  public static final int GC_HEADER_BYTES = Selected.Constraints.get().gcHeaderWords() << LOG_BYTES_IN_WORD;  
  public static final boolean GENERATE_GC_TRACE = Selected.Constraints.get().generateGCTrace();
}

