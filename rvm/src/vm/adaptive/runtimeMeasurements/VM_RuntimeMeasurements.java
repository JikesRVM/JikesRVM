/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.adaptive;

import java.util.Vector;
import java.util.Enumeration;
import com.ibm.JikesRVM.VM;
import org.vmmagic.unboxed.*;
import com.ibm.JikesRVM.VM_Scheduler;
import com.ibm.JikesRVM.VM_Thread;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

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
 * A reportReporableObject can be reset, and reported.
 * 
 * @author Matthew Arnold
 * @author Stephen Fink
 * @modified Peter Sweeney
 */
public abstract class VM_RuntimeMeasurements {

  /**
   * listeners for methods
   */
  static VM_MethodListener[] methodListeners = new VM_MethodListener[0];
  /**
   * listeners for contexts
   */
  static VM_ContextListener[] contextListeners = new VM_ContextListener[0];
  /**
   * listeners for nulls
   */
  static VM_NullListener[] nullListeners = new VM_NullListener[0];

  private static int activateMethodListeners_count = 0;
  private static int activateContextListeners_count = 0;
  private static int activateNullListeners_count = 0;
  /**
   * Install a method listener
   * @param s method listener to be installed
   */
  static synchronized void installMethodListener(VM_MethodListener s) { 
    int numListeners = methodListeners.length;
    VM_MethodListener[] tmp = new VM_MethodListener[numListeners+1];
    for (int i=0; i<numListeners; i++) {
      tmp[i] = methodListeners[i];
    }
    tmp[numListeners] = s;
    methodListeners = tmp;
  }

  /**
   * Install a context listener
   * @param s context listener to be installed
   */
  static synchronized void installContextListener(VM_ContextListener s) { 
    int numListeners = contextListeners.length;
    VM_ContextListener[] tmp = new VM_ContextListener[numListeners+1];
    for (int i=0; i<numListeners; i++) {
      tmp[i] = contextListeners[i];
    }
    tmp[numListeners] = s;
    contextListeners = tmp;
  }

  /**
   * Install a null listener
   * @param s null listener to be installed
   */
  static synchronized void installNullListener(VM_NullListener s) { 
    int numListeners = nullListeners.length;
    VM_NullListener[] tmp = new VM_NullListener[numListeners+1];
    for (int i=0; i<numListeners; i++) {
      tmp[i] = nullListeners[i];
    }
    tmp[numListeners] = s;
    nullListeners = tmp;
  }

  /**
   * Determine if at least one active method listener exists
   * @return true if at least one active method listener
   */
  public static boolean hasMethodListener() throws UninterruptiblePragma { 
    VM_Listener[] tmp = methodListeners; // side-step dangerous race condition
    for (int i=0; i<tmp.length; i++) {
      if (tmp[i].isActive()) return true;
    }
    return false;
  }
  /**
   * Determine if at least one active context listener exists
   * @return true if at least one active context listener
   */
  public static boolean hasContextListener() throws UninterruptiblePragma { 
    VM_Listener[] tmp = contextListeners; // side-step dangerous race condition
    for (int i=0; i<tmp.length; i++) {
      if (tmp[i].isActive()) return true;
    }
    return false;
  }
  /**
   * Determine if at least one active null listener exists
   * @return true if at least one active null listener
   */
  public static boolean hasNullListener() throws UninterruptiblePragma { 
    VM_Listener[] tmp = nullListeners; // side-step dangerous race condition
    for (int i=0; i<tmp.length; i++) {
      if (tmp[i].isActive()) return true;
    }
    return false;
  }

  /**
   * Notify RuntimeMeasurements that method listeners should be activated
   *
   * @param cmid a compiled method id
   * @param callerCmid a compiled method id for the caller, -1 if none
   * @param whereFrom Was this a yieldpoint in a PROLOGUE, BACKEDGE, or
   *           EPILOGUE?
   */
  public static void activateMethodListeners(int cmid, int callerCmid, int whereFrom) throws UninterruptiblePragma {
    activateMethodListeners_count++;     
    VM_MethodListener[] tmp = methodListeners; // side-step dangerous race condition
    for (int i=0; i<tmp.length; i++) {
      if (tmp[i].isActive()) {
        tmp[i].update(cmid, callerCmid, whereFrom);
      }
    }
  }

  /**
   * Notify RuntimeMeasurements that context listeners should be activated.
   *
   * @param sfp         a pointer to a stack frame
   * @param whereFrom Was this a yieldpoint in a PROLOGUE, BACKEDGE, or
   *         EPILOGUE?
   */
  public static void activateContextListeners(Address sfp, int whereFrom) throws UninterruptiblePragma {
    activateContextListeners_count++;     
    VM_ContextListener[] tmp = contextListeners; // side-step dangerous race condition
    for (int i=0; i<tmp.length; i++) {
      if (tmp[i].isActive()) {
        tmp[i].update(sfp, whereFrom);
      }
    }
  }

  /**
   * Notify RuntimeMeasurements that null listeners should be activated.
   * @param whereFrom Was this a yieldpoint in a PROLOGUE, BACKEDGE, or
   *         EPILOGUE?
   */
  public static void activateNullListeners(int whereFrom) throws UninterruptiblePragma {
    activateNullListeners_count++;     
    VM_NullListener[] tmp = nullListeners; // side-step dangerous race condition
    for (int i=0; i<tmp.length; i++) {
      if (tmp[i].isActive()) {
        tmp[i].update(whereFrom);
      }
    }
  }

  /**
   * The currently registered decayable objects
   */
  static Vector decayObjects = new Vector();

  /**
   * Counts the number of decay events
   */
  static int decayEventCounter = 0;

  /**
   *  Register an object that should be decayed.
   *  The passed object will have its decay method called when the
   *  decaying thread decides it is time for the system to decay.
   */
  static void registerDecayableObject(VM_Decayable obj) {
    decayObjects.add(obj);
  }

  /**
   * Decay all registered decayable objects.
   */
  static void decayDecayableObjects() {
    decayEventCounter++;
    if (VM.LogAOSEvents) VM_AOSLogging.decayingCounters();
    
    for (Enumeration e=decayObjects.elements(); e.hasMoreElements();) {
      VM_Decayable obj = (VM_Decayable) e.nextElement();
      obj.decay();
    }
  }

  /**
   * The currently registered reportable objects
   */
  static Vector reportObjects = new Vector();

  /** 
   * Register an object that wants to have its report method called
   * whenever VM_RuntimeMeasurements.report is called
   */
  static void registerReportableObject(VM_Reportable obj) {
    reportObjects.add(obj);
  }

  /**
   * Reset to all registered reportable objects
   */
  public static void resetReportableObjects() {
    for (Enumeration e=reportObjects.elements(); e.hasMoreElements();) {
      VM_Reportable obj = (VM_Reportable)e.nextElement();
      obj.reset();
    }
  }    
  /**
   * Report to all registered reportable objects
   */
  private static void reportReportableObjects() {
    for (Enumeration e=reportObjects.elements(); e.hasMoreElements();) {
      VM_Reportable obj = (VM_Reportable)e.nextElement();
      obj.report();
    }
  }    
  
  /**
   * Report the current state of runtime measurements
   */
  static void report() {
    reportReportableObjects();
    
    if (VM.LogAOSEvents) {
      VM_AOSLogging.listenerStatistics(activateMethodListeners_count,
                                       activateContextListeners_count,
                                       activateNullListeners_count);
      VM_AOSLogging.decayStatistics(decayEventCounter);

      for (int i = 0, n = VM_Scheduler.threads.length; i < n; i++) {
        VM_Thread t = VM_Scheduler.threads[i];
        if (t != null) {
          VM_AOSLogging.threadExiting(t);
        }
      }
    }
  }

  /**
   * Stop the runtime measurement subsystem
   */
  static synchronized void stop() {
    methodListeners = new VM_MethodListener[0];
    contextListeners = new VM_ContextListener[0];
    nullListeners = new VM_NullListener[0];
  }
    
  /**
   * Called from VM_Thread.terminate.
   */
  public static void monitorThreadExit() {
    if (VM.LogAOSEvents) VM_AOSLogging.threadExiting(VM_Thread.getCurrentThread());
  }
  
  /**
   * Called when the VM is booting
   */
  static void boot() { }
}

