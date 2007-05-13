/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package org.jikesrvm.adaptive.measurements;

import java.util.Vector;
import org.jikesrvm.ArchitectureSpecific.VM_StackframeLayoutConstants;
import org.jikesrvm.adaptive.controller.VM_Controller;
import org.jikesrvm.adaptive.measurements.listeners.VM_ContextListener;
import org.jikesrvm.adaptive.measurements.listeners.VM_MethodListener;
import org.jikesrvm.adaptive.measurements.listeners.VM_NullListener;
import org.jikesrvm.adaptive.util.VM_AOSLogging;
import org.jikesrvm.compilers.common.VM_CompiledMethod;
import org.jikesrvm.compilers.common.VM_CompiledMethods;
import org.jikesrvm.runtime.VM_Magic;
import org.jikesrvm.scheduler.VM_Scheduler;
import org.jikesrvm.scheduler.VM_Thread;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Address;

/**
 * RuntimeMeasurements manages listeners, decayable objects, and 
 * reportable objects.
 *
 * A listener is installed by an organizer, and activated at thread
 * switch time by VM_Thread.  Depending on the update method that the
 * listener supports, it can be either a method, context, or a null 
 * listener.  Currently we have different registries for different 
 * listeners.  An alternative design is to have one register with where 
 * entries are tagged.
 *
 * A decayable object implements the VM_Decayable interface.
 * Anyone can register a decayable object,
 * The VM_DecayOrganizer periodically decays all objects that have 
 * been registers.
 *
 * A reportable object implements the Reportable interface, and 
 * is typically registered and used by the instrumentation subsystem. 
 * A Reporable can be reset and reported.
 */
public abstract class VM_RuntimeMeasurements {

  /////////////////////////////////////////////////////////////////////////
  // Support for gathering profile data on timer ticks
  /////////////////////////////////////////////////////////////////////////

  /**
   * listeners on timer ticks for methods
   */
  private static VM_MethodListener[] timerMethodListeners = new VM_MethodListener[0];

  /**
   * listeners on timer ticks for contexts
   */
  private static VM_ContextListener[] timerContextListeners = new VM_ContextListener[0];

  /**
   * listeners on timer ticks for nulls
   */
  private static VM_NullListener[] timerNullListeners = new VM_NullListener[0];

  /**
   * Install a method listener on timer ticks
   * @param s method listener to be installed
   */
  public static synchronized void installTimerMethodListener(VM_MethodListener s) {
    int numListeners = timerMethodListeners.length;
    VM_MethodListener[] tmp = new VM_MethodListener[numListeners + 1];
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
  public static synchronized void installTimerContextListener(VM_ContextListener s) {
    int numListeners = timerContextListeners.length;
    VM_ContextListener[] tmp = new VM_ContextListener[numListeners + 1];
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
  public static synchronized void installTimerNullListener(VM_NullListener s) {
    int numListeners = timerNullListeners.length;
    VM_NullListener[] tmp = new VM_NullListener[numListeners + 1];
    for (int i = 0; i < numListeners; i++) {
      tmp[i] = timerNullListeners[i];
    }
    tmp[numListeners] = s;
    timerNullListeners = tmp;
  }

  /**
   * Called from VM_Thread.yieldpoint every time it is invoked due to
   * a timer interrupt. When invoked, the callstack must be as follows:
   *   <..stuff..>
   *   <method that executed the taken yieldpoint>
   *   wrapper method
   *   VM_Thread.yieldpoint
   *   takeAOSTimerSample
   */
  @Uninterruptible
  public static void takeTimerSample(int whereFrom) {
    // We use threadswitches as a rough approximation of time. 
    // Every threadswitch is a clock tick.
    // TODO: kill controller clock in favor of VM_Processor.reportedTimerTicks
    VM_Controller.controllerClock++;

    //
    // "The idle thread is boring, and does not deserve to be sampled"
    //                           -- AOS Commandment Number 1
    if (!VM_Thread.getCurrentThread().isIdleThread()) {
      // Crawl stack to get to the frame in which the yieldpoint was taken
      // NB: depends on calling structure described in method comment!!!
      Address fp = VM_Magic.getCallerFramePointer(VM_Magic.getFramePointer()); // VM_Thread.yieldpoint
      fp = VM_Magic.getCallerFramePointer(fp); // wrapper routine
      Address ypTakenInFP = VM_Magic.getCallerFramePointer(fp); // method that took yieldpoint          

      // Get the cmid for the method in which the yieldpoint was taken.
      int ypTakenInCMID = VM_Magic.getCompiledMethodID(ypTakenInFP);

      // Get the cmid for that method's caller.
      Address ypTakenInCallerFP = VM_Magic.getCallerFramePointer(ypTakenInFP);
      int ypTakenInCallerCMID = VM_Magic.getCompiledMethodID(ypTakenInCallerFP);

      // Determine if ypTakenInCallerCMID corresponds to a real Java stackframe.
      // If one of the following conditions is detected, set ypTakenInCallerCMID to -1
      //    Caller is out-of-line assembly (no VM_Method object) or top-of-stack psuedo-frame
      //    Caller is a native method
      VM_CompiledMethod ypTakenInCM = VM_CompiledMethods.getCompiledMethod(ypTakenInCMID);
      if (ypTakenInCallerCMID == VM_StackframeLayoutConstants.INVISIBLE_METHOD_ID ||
          ypTakenInCM.getMethod().getDeclaringClass().hasBridgeFromNativeAnnotation()) {
        ypTakenInCallerCMID = -1;
      }

      // Notify all registered listeners
      for (VM_NullListener aNl : timerNullListeners) {
        if (aNl.isActive()) {
          aNl.update(whereFrom);
        }
      }
      for (VM_MethodListener aMl : timerMethodListeners) {
        if (aMl.isActive()) {
          aMl.update(ypTakenInCMID, ypTakenInCallerCMID, whereFrom);
        }
      }
      if (ypTakenInCallerCMID != -1) {
        for (VM_ContextListener aCl : timerContextListeners) {
          if (aCl.isActive()) {
            aCl.update(ypTakenInFP, whereFrom);
          }
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
  private static VM_MethodListener[] cbsMethodListeners = new VM_MethodListener[0];

  /**
   * context listeners that tigger on CBS call yieldpoints
   */
  private static VM_ContextListener[] cbsContextListeners = new VM_ContextListener[0];

  /**
   * Install a method listener on cbs ticks
   * @param s method listener to be installed
   */
  public static synchronized void installCBSMethodListener(VM_MethodListener s) {
    int numListeners = cbsMethodListeners.length;
    VM_MethodListener[] tmp = new VM_MethodListener[numListeners + 1];
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
  public static synchronized void installCBSContextListener(VM_ContextListener s) {
    int numListeners = cbsContextListeners.length;
    VM_ContextListener[] tmp = new VM_ContextListener[numListeners + 1];
    for (int i = 0; i < numListeners; i++) {
      tmp[i] = cbsContextListeners[i];
    }
    tmp[numListeners] = s;
    cbsContextListeners = tmp;
  }

  /**
   * Called from VM_Thread.yieldpoint when it is time to take a CBS method sample.
   * When invoked, the callstack must be as follows:
   *   <..stuff..>
   *   <method that executed the taken yieldpoint>
   *   wrapper method
   *   VM_Thread.yieldpoint
   *   takeCBSMethodSample
   */
  @Uninterruptible
  public static void takeCBSMethodSample(int whereFrom) {
    //
    // "The idle thread is boring, and does not deserve to be sampled"
    //                           -- AOS Commandment Number 1
    if (!VM_Thread.getCurrentThread().isIdleThread()) {
      // Crawl stack to get to the frame in which the yieldpoint was taken
      // NB: depends on calling structure described in method comment!!!
      Address fp = VM_Magic.getCallerFramePointer(VM_Magic.getFramePointer()); // VM_Thread.yieldpoint
      fp = VM_Magic.getCallerFramePointer(fp); // wrapper routine
      Address ypTakenInFP = VM_Magic.getCallerFramePointer(fp); // method that took yieldpoint          

      // Get the cmid for the method in which the yieldpoint was taken.
      int ypTakenInCMID = VM_Magic.getCompiledMethodID(ypTakenInFP);

      // Get the cmid for that method's caller.
      Address ypTakenInCallerFP = VM_Magic.getCallerFramePointer(ypTakenInFP);
      int ypTakenInCallerCMID = VM_Magic.getCompiledMethodID(ypTakenInCallerFP);

      // Determine if ypTakenInCallerCMID corresponds to a real Java stackframe.
      // If one of the following conditions is detected, set ypTakenInCallerCMID to -1
      //    Caller is out-of-line assembly (no VM_Method object) or top-of-stack psuedo-frame
      //    Caller is a native method
      VM_CompiledMethod ypTakenInCM = VM_CompiledMethods.getCompiledMethod(ypTakenInCMID);
      if (ypTakenInCallerCMID == VM_StackframeLayoutConstants.INVISIBLE_METHOD_ID ||
          ypTakenInCM.getMethod().getDeclaringClass().hasBridgeFromNativeAnnotation()) {
        ypTakenInCallerCMID = -1;
      }

      // Notify all registered listeners
      for (VM_MethodListener methodListener : cbsMethodListeners) {
        if (methodListener.isActive()) {
          methodListener.update(ypTakenInCMID, ypTakenInCallerCMID, whereFrom);
        }
      }
    }
  }

  /**
   * Called from VM_Thread.yieldpoint when it is time to take a CBS call sample.
   * When invoked, the callstack must be as follows:
   *   <..stuff..>
   *   <method that executed the taken yieldpoint>
   *   wrapper method
   *   VM_Thread.yieldpoint
   *   takeCBSCallSample
   */
  @Uninterruptible
  public static void takeCBSCallSample(int whereFrom) {
    //
    // "The idle thread is boring, and does not deserve to be sampled"
    //                           -- AOS Commandment Number 1
    if (!VM_Thread.getCurrentThread().isIdleThread()) {
      // Crawl stack to get to the frame in which the yieldpoint was taken
      // NB: depends on calling structure described in method comment!!!
      Address fp = VM_Magic.getCallerFramePointer(VM_Magic.getFramePointer()); // VM_Thread.yieldpoint
      fp = VM_Magic.getCallerFramePointer(fp); // wrapper routine
      Address ypTakenInFP = VM_Magic.getCallerFramePointer(fp); // method that took yieldpoint          

      // Get the cmid for the method in which the yieldpoint was taken.
      int ypTakenInCMID = VM_Magic.getCompiledMethodID(ypTakenInFP);

      // Get the cmid for that method's caller.
      Address ypTakenInCallerFP = VM_Magic.getCallerFramePointer(ypTakenInFP);
      int ypTakenInCallerCMID = VM_Magic.getCompiledMethodID(ypTakenInCallerFP);

      // Determine if ypTakenInCallerCMID corresponds to a real Java stackframe.
      // If one of the following conditions is detected, set ypTakenInCallerCMID to -1
      //    Caller is out-of-line assembly (no VM_Method object) or top-of-stack psuedo-frame
      //    Caller is a native method
      VM_CompiledMethod ypTakenInCM = VM_CompiledMethods.getCompiledMethod(ypTakenInCMID);
      if (ypTakenInCallerCMID == VM_StackframeLayoutConstants.INVISIBLE_METHOD_ID ||
          ypTakenInCM.getMethod().getDeclaringClass().hasBridgeFromNativeAnnotation()) {
        // drop sample
      } else {
        // Notify all registered listeners
        for (VM_ContextListener listener : cbsContextListeners) {
          if (listener.isActive()) {
            listener.update(ypTakenInFP, whereFrom);
          }
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
  static Vector<VM_Decayable> decayObjects = new Vector<VM_Decayable>();

  /**
   * Counts the number of decay events
   */
  static int decayEventCounter = 0;

  /**
   *  Register an object that should be decayed.
   *  The passed object will have its decay method called when the
   *  decaying thread decides it is time for the system to decay.
   */
  public static void registerDecayableObject(VM_Decayable obj) {
    decayObjects.add(obj);
  }

  /**
   * Decay all registered decayable objects.
   */
  public static void decayDecayableObjects() {
    decayEventCounter++;
    VM_AOSLogging.decayingCounters();

    for (VM_Decayable obj : decayObjects) {
      obj.decay();
    }
  }

  /////////////////////////////////////////////////////////////////////////
  // Support for reportable objects
  /////////////////////////////////////////////////////////////////////////

  /**
   * The currently registered reportable objects
   */
  static Vector<VM_Reportable> reportObjects = new Vector<VM_Reportable>();

  /**
   * Register an object that wants to have its report method called
   * whenever VM_RuntimeMeasurements.report is called
   */
  public static void registerReportableObject(VM_Reportable obj) {
    reportObjects.add(obj);
  }

  /**
   * Reset to all registered reportable objects
   */
  public static void resetReportableObjects() {
    for (VM_Reportable obj : reportObjects) {
      obj.reset();
    }
  }

  /**
   * Report to all registered reportable objects
   */
  private static void reportReportableObjects() {
    for (VM_Reportable obj : reportObjects) {
      obj.report();
    }
  }

  /**
   * Report the current state of runtime measurements
   */
  public static void report() {
    reportReportableObjects();

    VM_AOSLogging.decayStatistics(decayEventCounter);

    for (int i = 0, n = VM_Scheduler.threads.length; i < n; i++) {
      VM_Thread t = VM_Scheduler.threads[i];
      if (t != null) {
        VM_AOSLogging.threadExiting(t);
      }
    }
  }

  /**
   * Stop the runtime measurement subsystem
   */
  public static synchronized void stop() {
    timerMethodListeners = new VM_MethodListener[0];
    timerContextListeners = new VM_ContextListener[0];
    timerNullListeners = new VM_NullListener[0];

    cbsMethodListeners = new VM_MethodListener[0];
    cbsContextListeners = new VM_ContextListener[0];
  }

  /**
   * Called from VM_Thread.terminate.
   */
  public static void monitorThreadExit() {
    VM_AOSLogging.threadExiting(VM_Thread.getCurrentThread());
  }

  /**
   * Called when the VM is booting
   */
  public static void boot() { }
}

