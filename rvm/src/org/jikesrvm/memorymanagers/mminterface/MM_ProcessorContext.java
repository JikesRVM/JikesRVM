/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.memorymanagers.mminterface;

import org.jikesrvm.scheduler.VM_Processor;

/**
 * VM_Processor must extend this class to associate appropriate context with processor.
 */
public abstract class MM_ProcessorContext extends Selected.Mutator {
  public final Selected.Collector collectorContext = new Selected.Collector((VM_Processor) this);
}
