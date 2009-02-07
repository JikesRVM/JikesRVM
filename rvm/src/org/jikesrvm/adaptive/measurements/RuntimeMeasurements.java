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
package org.jikesrvm.adaptive.measurements;

import java.util.Vector;
import org.jikesrvm.ArchitectureSpecific.StackframeLayoutConstants;
import org.jikesrvm.adaptive.controller.Controller;
import org.jikesrvm.adaptive.measurements.listeners.ContextListener;
import org.jikesrvm.adaptive.measurements.listeners.MethodListener;
import org.jikesrvm.adaptive.measurements.listeners.NullListener;
import org.jikesrvm.adaptive.util.AOSLogging;
import org.jikesrvm.compilers.common.CompiledMethod;
import org.jikesrvm.compilers.common.CompiledMethods;
import org.jikesrvm.runtime.Magic;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Address;

/**
 * RuntimeMeasurements manages listeners, decayable objects, and
 * reportable objects.
 *
 * A listener is installed by an organizer, and activated at thread
 * switch time by Thread.  Depending on the update method that the
 * listener supports, it can be either a method, context, or a null
 * listener.  Currently we have different registries for different
 * listeners.  An alternative design is to have one register with where
 * entries are tagged.
 *
 * A decayable object implements the Decayable interface.
 * Anyone can register a decayable object,
 * The DecayOrganizer periodically decays all objects that have
 * been registers.
 *
 * A reportable object implements the Reportable interface, and
 * is typically registered and used by the instrumentation subsystem.
 * A Reporable can be reset and reported.
 */
public abstract class RuntimeMeasurements {

  /////////////////////////////////////////////////////////////////////////
  // Support for gathering profile data on timer ticks
  /////////////////////////////////////////////////////////////////////////

  /**
   * listeners on timer ticks for methods
   */
  private static MethodListener[] timerMethodListeners = new MethodListener[0];

  /**
   * listeners on timer ticks for contexts
   */
  private static ContextListener[] timerContextListeners = new ContextListener[0];

  /**
   * listeners on timer ticks for nulls
   */
  private static NullListener[] timerNullListeners = new NullListener[0];

  /**
   * Install a method listener on timer ticks
   * @param s method listener to be installed
   */
  public static synchronized void installTimerMethodListener(MethodListener s) {
    int numListeners = timerMethodListeners.length;
    MethodListener[] tmp = new MethodListener[numListeners + 1];
    for (int i = 0; i < numListeners; i++) {
      tmp[i] = timerMethodListeners[i];
    }
    tmp[numListeners] = s;
    timerMethodListeners = tmp;
  }

  /**
   * Install a context listener on timer ticks
   * @param s context listener to be installed
   */
  public static synchronized void installTimerContextListener(ContextListener s) {
    int numListeners = timerContextListeners.length;
    ContextListener[] tmp = new ContextListener[numListeners + 1];
    for (int i = 0; i < numListeners; i++) {
      tmp[i] = timerContextListeners[i];
    }
    tmp[numListeners] = s;
    timerContextListeners = tmp;
  }

  /**
   * Install a null listener on timer ticks
   * @param s null listener to be installed
   */
  public static synchronized void installTimerNullListener(NullListener s) {
    int numListeners = timerNullListeners.length;
    NullListener[] tmp = new NullListener[numListeners + 1];
    for (int i = 0; i < numListeners; i++) {
      tmp[i] = timerNullListeners[i];
    }
    tmp[numListeners] = s;
    timerNullListeners = tmp;
  }

  /**
   * Called from Thread.yieldpoint every time it is invoked due to
   * a timer interrupt.
   */
  @Uninterruptible
  public static void takeTimerSample(int whereFrom, Address yieldpointServiceMethodFP) {
    // We use timer ticks as a rough approximation of time.
    // TODO: kill controller clock in favor of reportedTimerTicks
    // PNT: huh?
    Controller.controllerClock++;

    Address ypTakenInFP = Magic.getCallerFramePointer(yieldpointServiceMethodFP); // method that took yieldpoint

    // Get the cmid for the method in which the yieldpoint was taken.
    int ypTakenInCMID = Magic.getCompiledMethodID(ypTakenInFP);

    // Get the cmid for that method's caller.
    Address ypTakenInCallerFP = Magic.getCallerFramePointer(ypTakenInFP);
    int ypTakenInCallerCMID = Magic.getCompiledMethodID(ypTakenInCallerFP);

    // Determine if ypTakenInCallerCMID corresponds to a real Java stackframe.
    // If one of the following conditions is detected, set ypTakenInCallerCMID to -1
    //    Caller is out-of-line assembly (no RVMMethod object) or top-of-stack psuedo-frame
    //    Caller is a native method
    CompiledMethod ypTakenInCM = CompiledMethods.getCompiledMethod(ypTakenInCMID);
    if (ypTakenInCallerCMID == StackframeLayoutConstants.INVISIBLE_METHOD_ID ||
        ypTakenInCM.getMethod().getDeclaringClass().hasBridgeFromNativeAnnotation()) {
      ypTakenInCallerCMID = -1;
    }

    // Notify all registered listeners
    for (NullListener aNl : timerNullListeners) {
      if (aNl.isActive()) {
        aNl.update(whereFrom);
      }
    }
    for (MethodListener aMl : timerMethodListeners) {
      if (aMl.isActive()) {
        aMl.update(ypTakenInCMID, ypTakenInCallerCMID, whereFrom);
      }
    }
    if (ypTakenInCallerCMID != -1) {
      for (ContextListener aCl : timerContextListeners) {
        if (aCl.isActive()) {
          aCl.update(ypTakenInFP, whereFrom);
        }
      }
    }
  }

  /////////////////////////////////////////////////////////////////////////
  // Support for gathering profile data on CBS samples
  /////////////////////////////////////////////////////////////////////////

  /**
   * method listeners that trigger on CBS Method yieldpoints
   */
  private static MethodListener[] cbsMethodListeners = new MethodListener[0];

  /**
   * context listeners that tigger on CBS call yieldpoints
   */
  private static ContextListener[] cbsContextListeners = new ContextListener[0];

  /**
   * Install a method listener on cbs ticks
   * @param s method listener to be installed
   */
  public static synchronized void installCBSMethodListener(MethodListener s) {
    int numListeners = cbsMethodListeners.length;
    MethodListener[] tmp = new MethodListener[numListeners + 1];
    for (int i = 0; i < numListeners; i++) {
      tmp[i] = cbsMethodListeners[i];
    }
    tmp[numListeners] = s;
    cbsMethodListeners = tmp;
  }

  /**
   * Install a context listener on cbs ticks
   * @param s context listener to be installed
   */
  public static synchronized void installCBSContextListener(ContextListener s) {
    int numListeners = cbsContextListeners.length;
    ContextListener[] tmp = new ContextListener[numListeners + 1];
    for (int i = 0; i < numListeners; i++) {
      tmp[i] = cbsContextListeners[i];
    }
    tmp[numListeners] = s;
    cbsContextListeners = tmp;
  }

  /**
   * Called from Thread.yieldpoint when it is time to take a CBS method sample.
   */
  @Uninterruptible
  public static void takeCBSMethodSample(int whereFrom, Address yieldpointServiceMethodFP) {
    Address ypTakenInFP = Magic.getCallerFramePointer(yieldpointServiceMethodFP); // method that took yieldpoint

    // Get the cmid for the method in which the yieldpoint was taken.
    int ypTakenInCMID = Magic.getCompiledMethodID(ypTakenInFP);

    // Get the cmid for that method's caller.
    Address ypTakenInCallerFP = Magic.getCallerFramePointer(ypTakenInFP);
    int ypTakenInCallerCMID = Magic.getCompiledMethodID(ypTakenInCallerFP);

    // Determine if ypTakenInCallerCMID corresponds to a real Java stackframe.
    // If one of the following conditions is detected, set ypTakenInCallerCMID to -1
    //    Caller is out-of-line assembly (no RVMMethod object) or top-of-stack psuedo-frame
    //    Caller is a native method
    CompiledMethod ypTakenInCM = CompiledMethods.getCompiledMethod(ypTakenInCMID);
    if (ypTakenInCallerCMID == StackframeLayoutConstants.INVISIBLE_METHOD_ID ||
        ypTakenInCM.getMethod().getDeclaringClass().hasBridgeFromNativeAnnotation()) {
      ypTakenInCallerCMID = -1;
    }

    // Notify all registered listeners
    for (MethodListener methodListener : cbsMethodListeners) {
      if (methodListener.isActive()) {
        methodListener.update(ypTakenInCMID, ypTakenInCallerCMID, whereFrom);
      }
    }
  }

  /**
   * Called from Thread.yieldpoint when it is time to take a CBS call sample.
   */
  @Uninterruptible
  public static void takeCBSCallSample(int whereFrom, Address yieldpointServiceMethodFP) {
    Address ypTakenInFP = Magic.getCallerFramePointer(yieldpointServiceMethodFP); // method that took yieldpoint

    // Get the cmid for the method in which the yieldpoint was taken.
    int ypTakenInCMID = Magic.getCompiledMethodID(ypTakenInFP);

    // Get the cmid for that method's caller.
    Address ypTakenInCallerFP = Magic.getCallerFramePointer(ypTakenInFP);
    int ypTakenInCallerCMID = Magic.getCompiledMethodID(ypTakenInCallerFP);

    // Determine if ypTakenInCallerCMID corresponds to a real Java stackframe.
    // If one of the following conditions is detected, set ypTakenInCallerCMID to -1
    //    Caller is out-of-line assembly (no RVMMethod object) or top-of-stack psuedo-frame
    //    Caller is a native method
    CompiledMethod ypTakenInCM = CompiledMethods.getCompiledMethod(ypTakenInCMID);
    if (ypTakenInCallerCMID == StackframeLayoutConstants.INVISIBLE_METHOD_ID ||
        ypTakenInCM.getMethod().getDeclaringClass().hasBridgeFromNativeAnnotation()) {
      // drop sample
    } else {
      // Notify all registered listeners
      for (ContextListener listener : cbsContextListeners) {
        if (listener.isActive()) {
          listener.update(ypTakenInFP, whereFrom);
        }
      }
    }
  }

  /////////////////////////////////////////////////////////////////////////
  // Support for decay
  /////////////////////////////////////////////////////////////////////////

  /**
   * The currently registered decayable objects
   */
  static Vector<Decayable> decayObjects = new Vector<Decayable>();

  /**
   * Counts the number of decay events
   */
  static int decayEventCounter = 0;

  /**
   *  Register an object that should be decayed.
   *  The passed object will have its decay method called when the
   *  decaying thread decides it is time for the system to decay.
   */
  public static void registerDecayableObject(Decayable obj) {
    decayObjects.add(obj);
  }

  /**
   * Decay all registered decayable objects.
   */
  public static void decayDecayableObjects() {
    decayEventCounter++;
    AOSLogging.logger.decayingCounters();

    for (Decayable obj : decayObjects) {
      obj.decay();
    }
  }

  /////////////////////////////////////////////////////////////////////////
  // Support for reportable objects
  /////////////////////////////////////////////////////////////////////////

  /**
   * The currently registered reportable objects
   */
  static Vector<Reportable> reportObjects = new Vector<Reportable>();

  /**
   * Register an object that wants to have its report method called
   * whenever RuntimeMeasurements.report is called
   */
  public static void registerReportableObject(Reportable obj) {
    reportObjects.add(obj);
  }

  /**
   * Reset to all registered reportable objects
   */
  public static void resetReportableObjects() {
    for (Reportable obj : reportObjects) {
      obj.reset();
    }
  }

  /**
   * Report to all registered reportable objects
   */
  private static void reportReportableObjects() {
    for (Reportable obj : reportObjects) {
      obj.report();
    }
  }

  /**
   * Report the current state of runtime measurements
   */
  public static void report() {
    reportReportableObjects();

    AOSLogging.logger.decayStatistics(decayEventCounter);
  }

  /**
   * Stop the runtime measurement subsystem
   */
  public static synchronized void stop() {
    timerMethodListeners = new MethodListener[0];
    timerContextListeners = new ContextListener[0];
    timerNullListeners = new NullListener[0];

    cbsMethodListeners = new MethodListener[0];
    cbsContextListeners = new ContextListener[0];
  }

  /**
   * Called when the VM is booting
   */
  public static void boot() { }
}

