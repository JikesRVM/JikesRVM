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
package org.jikesrvm.adaptive.measurements.organizers;

import org.jikesrvm.VM;
import org.jikesrvm.adaptive.measurements.listeners.Listener;
import org.jikesrvm.scheduler.RVMThread;
import org.jikesrvm.scheduler.Latch;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.pragma.Unpreemptible;
import org.vmmagic.pragma.NonMoving;

/**
 * An Organizer acts an an intermediary between the low level
 * online measurements and the controller.  An organizer may perform
 * simple or complex tasks, but it is always simply following the
 * instructions given by the controller.
 */
@NonMoving
public abstract class Organizer extends RVMThread {

  /** Constructor */
  public Organizer() {
    super("Organizer");
  }

  /**
   * The listener associated with this organizer.
   * May be null if the organizer has no associated listener.
   */
  protected Listener listener;

  /** A latch used for activate/passivate. */
  private final Latch latch = new Latch(false);

  /**
   * Called when thread is scheduled.
   */
  @Override
  public void run() {
    initialize();
    while (true) {
      passivate(); // wait until externally scheduled to run
      try {
        thresholdReached();       // we've been scheduled; do our job!
        if (listener != null) listener.reset();
      } catch (Exception e) {
        e.printStackTrace();
        if (VM.ErrorsFatal) VM.sysFail("Exception in organizer " + this);
      }
    }
  }

  /**
   * Last opportunity to say something.
   */
  public void report() {}

  /**
   * Method that is called when the sampling threshold is reached
   */
  abstract void thresholdReached();

  /**
   * Organizer specific setup.
   * A good place to install and activate any listeners.
   */
  protected abstract void initialize();

  /*
   * Can access the thread queue without locking because
   * Only listener and organizer operate on the thread queue and the
   * listener uses its own protocol to ensure that exactly 1
   * thread will attempt to activate the organizer.
   */
  @Unpreemptible
  private void passivate() {
    if (listener != null) {
      if (VM.VerifyAssertions) VM._assert(!listener.isActive());
      listener.activate();
    }
    latch.waitAndClose();
  }

  /**
   * Called to activate the organizer thread (ie schedule it for execution).
   */
  @Uninterruptible
  public void activate() {
    if (listener != null) {
      if (VM.VerifyAssertions) VM._assert(listener.isActive());
      listener.passivate();
    }
    latch.openDangerously();
  }
}
