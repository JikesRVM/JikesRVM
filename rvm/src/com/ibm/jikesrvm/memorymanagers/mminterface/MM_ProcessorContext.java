/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001, 2004
 * 
 * $Id: MM_Interface.java 11018 2006-11-19 23:39:48Z captain5050 $
 */
package com.ibm.jikesrvm.memorymanagers.mminterface;

import com.ibm.jikesrvm.VM_Processor;

/**
 * VM_Processor must extend this class to associate appropriate context with processor. 
 */
public abstract class MM_ProcessorContext extends Selected.Mutator {
  public final Selected.Collector collectorContext = new Selected.Collector((VM_Processor) this);
}
