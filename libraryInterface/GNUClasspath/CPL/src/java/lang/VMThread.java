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
package java.lang;

import org.jikesrvm.scheduler.RVMThread;

/**
 * Wrapper for Jikes RVM thread class
 */
final class VMThread {
  /**
   * Corresponding Thread accessed by JikesRVMSupport.getThread()
   */
  final RVMThread vmdata;

  /**
   * Constructor, called by JikesRVMSupport.createThread and VMThread.create
   */
  VMThread(RVMThread vmdata) {
    this.vmdata = vmdata;
  }
  /**
   * Create the VM thread, set this in the parent Thread and start its execution
   */
  static void create(Thread parent, long stacksize) {
    RVMThread vmd = new RVMThread(parent, stacksize,  parent.name, parent.daemon, parent.priority);
    parent.vmThread = new VMThread(vmd);
    vmd.start();
  }

  /**
   * Sets the name of the thread
   * @param name the new name for the thread
   */
  void setName(String name) {
    vmdata.setName(name);
  }
  /**
   * Gets the name of the thread
   */
  String getName() {
    return vmdata.getName();
  }
  /**
   * The current executing thread
   * @return the current executing java.lang.Thread
   */
  static Thread currentThread() {
    return RVMThread.getCurrentThread().getJavaLangThread();
  }
  /**
   * Does the currently running Thread hold the lock on an obj?
   * @param obj the object to check
   * @return whether the thread holds the lock
   */
  static boolean holdsLock(Object obj) {
    return RVMThread.getCurrentThread().holdsLock(obj);
  }

  /**
   * Is this a daemon thread?
   * @return whether this thread is a daemon
   */
  boolean isDaemon() {
    return vmdata.isDaemonThread();
  }
  /**
   * Get the priority of the thread
   * @return the thread's priority
   */
  int getPriority() {
    return vmdata.getPriority();
  }
  /**
   * Set the priority of the thread
   * @param priority
   */
  void setPriority(int priority) {
    vmdata.setPriority(priority);
  }
  /**
   * Get the state of the thread
   * @return thread state
   */
  String getState() {
    return vmdata.getState().toString();
  }
  /**
   * Wait for the thread to die or for the timeout to occur
   * @param ms milliseconds to wait
   * @param ns nanoseconds to wait
   */
  void join(long ms, int ns) throws InterruptedException {
    vmdata.join(ms, ns);
  }
  /**
   * Yield control
   */
  static void yield() {
    RVMThread.yield();
  }
  /**
   * Put the current thread to sleep
   * @param ms milliseconds to sleep
   * @param ns nanoseconds to sleep
   */
  static void sleep(long ms, int ns) throws InterruptedException {
    RVMThread.sleep(ms, ns);
  }
  /**
   * Was the current thread interrupted and if it was clear the interrupted
   * status
   * @return whether the thread was interrupted
   */
  static boolean interrupted() {
    RVMThread current = RVMThread.getCurrentThread();
    if (current.isInterrupted()) {
      current.clearInterrupted();
      return true;
    }
    return false;
  }
  /**
   * Has this thread been interrupted?
   * @return whether the thread was interrupted
   */
  boolean isInterrupted() {
    return vmdata.isInterrupted();
  }
  /**
   * Interrupt this thread
   */
  void interrupt() {
    vmdata.interrupt();
  }
  /**
   * Suspend execution of this thread
   */
  void suspend() {
    vmdata.suspend();
  }
  /**
   * Resume execution of this thread
   */
  void resume() {
    vmdata.resume();
  }
  /**
   * Stop the thread abnormally and throw the given exception
   * @param t the throwable thrown when the thread dies
   */
  void stop(Throwable t) {
    vmdata.stop(t);
  }
  /**
   * Count the stack frames of this thread
   */
  int countStackFrames() {
    return vmdata.countStackFrames();
  }
}
