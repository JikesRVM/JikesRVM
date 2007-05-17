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
package org.mmtk.utility.deque;

import org.mmtk.plan.TraceStep;
import org.mmtk.utility.Constants;
import org.mmtk.utility.scan.Scan;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This class is a combination of a Deque and a TraceStep, designed to include
 * intelligent processing of child references as objects are scanned.
 * 
 * @see org.mmtk.plan.TraceStep
 */
@Uninterruptible public abstract class ObjectReferenceBuffer extends TraceStep implements Constants {
  /****************************************************************************
   * 
   * Instance variables
   */
  private final ObjectReferenceDeque values;

  /****************************************************************************
   * 
   * Initialization
   */

  /**
   * Constructor
   * 
   * @param name The name of the underlying deque.
   * @param queue The shared deque that is used.
   */
  public ObjectReferenceBuffer(String name, SharedDeque queue) {
    values = new ObjectReferenceDeque(name, queue);
    queue.newConsumer();
  }  
  
  /**
   * Transitive step.
   * 
   * @param loc The location containing the object reference to process.
   */
  @Inline
  public final void traceObjectLocation(Address loc) { 
    ObjectReference object = loc.loadObjectReference();
    process(object);
  }
  
  /**
   * This is the method that ensures 
   * 
   * @param object The object to process.
   */
  protected abstract void process(ObjectReference object);
  
  /**
   * Process each of the child objects for the passed object. 
   * 
   * @param object The object to process the children of.
   */
  @Inline
  public final void processChildren(ObjectReference object) { 
    Scan.scanObject(this, object);
  }
  
  /**
   * Pushes an object onto the queue, forcing an inlined sequence.
   *  
   * @param object The object to push.
   */
  @Inline
  public final void push(ObjectReference object) { 
    values.push(object);
  }

  /**
   * Pushes an object onto the queue, forcing an out of line sequence.
   *  
   * @param object The object to push.
   */
  @Inline
  public final void pushOOL(ObjectReference object) { 
    values.pushOOL(object);
  }
  
  /**
   * Retrives an object.
   * 
   * @return The object retrieved.
   */
  @Inline
  public final ObjectReference pop() { 
    return values.pop();
  }
  
  /**
   * Flushes all local state back to the shared queue.
   */
  public final void flushLocal() {
    values.flushLocal();
  }
}
