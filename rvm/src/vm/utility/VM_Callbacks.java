/*
 * (C) Copyright IBM Corp. 2001
 */
//VM_Callbacks.java
//$Id$
package com.ibm.JikesRVM;

import com.ibm.JikesRVM.classloader.*;
import java.util.Enumeration;

/**
 * A class for managing various callbacks from the VM.
 * 
 * <p>Consumers should register an implementation of the needed interface with
 * a given callback method, and will get notified when the event happens.
 * 
 * <p>Note: callback consumers should not rely on any particular order of
 * callback invocation.
 * 
 * <p>TODO: allow limited control over callback order.
 *
 * <p>
 * The following events are currently implemented.  See code for exact
 * invocation syntax.
 * <ul>
 * <li> ClassLoaded       - called after a VM_Class is loaded
 * <li> ClassResolved     - called after a VM_Class is resolved
 * <li> ClassInstantiated - called after a VM_Class is instantiated
 * <li> ClassInitialized  - called after a VM_Class is initialized
 * <li> MethodOverride    - called when a method in a newly loaded class
 *                          overrides a method in an existing class
 * <li> MethodCompile     - called before a method is compiled
 * <li> ForName           - called when java.lang.Class.forName() is invoked
 * <li> BootImageWriting  - called when boot image writing is started
 * <li> Startup           - called when the VM has completed booting
 * <li> Exit              - called when the VM is about to exit
 * <li> AppStart          - called before the application starts executing
 *                          all runs -- needs application support)
 * <li> AppComplete       - called after the application completes executing
 *                          all runs --- needs application support)
 * <li> AppRunStart       - called before the application starts a run
 *                          (many applications have several runs -- needs
 *                          application support)
 * <li> AppRunComplete    - called after the application completes a run
 *                          (many applications have several runs --- needs
 *                          application support)
 * </ul>
 *
 * @author Igor Pechtchanski
 */
public final class VM_Callbacks {
  ///////////////
  // INTERFACE //
  ///////////////

  /**
   * Interface for monitoring class loading.
   */
  public static interface ClassLoadedMonitor {
    /**
     * Notify the monitor that a class has been loaded.
     * @param klass the class that was loaded
     */
    public void notifyClassLoaded(VM_Class klass);
  }

  /**
   * Class loading callback list.
   */
  private static CallbackList classLoadedCallbacks = null;
  private static Object classLoadedLock = new Object();
  private static boolean classLoadedEnabled = true;

  /**
   * Register a callback for class loading.
   * @param cb the object to notify when event happens
   */
  public static void addClassLoadedMonitor(ClassLoadedMonitor cb) {
    synchronized (classLoadedLock) {
      if (TRACE_ADDMONITOR || TRACE_CLASSLOADED) {
        VM.sysWrite("adding class loaded monitor: ");
        VM.sysWrite(getClass(cb));
        VM.sysWrite("\n");
      }
      classLoadedCallbacks = new CallbackList(cb, classLoadedCallbacks);
    }
  }

  /**
   * Notify the callback manager that a class has been loaded.
   * @param klass the class that was loaded
   */
  public static void notifyClassLoaded(VM_Class klass) {
    // NOTE: will need synchronization if allowing unregistering
    if (!classLoadedEnabled) return;
    classLoadedEnabled = false;
    if (TRACE_CLASSLOADED) {
      //VM.sysWrite(getThread(), false);
      //VM.sysWrite(": ");
      VM.sysWrite("invoking class loaded monitors: ");
      VM.sysWrite(klass.getDescriptor());
      VM.sysWrite("\n");
      //printStack("From: ");
    }
    for (CallbackList l = classLoadedCallbacks; l != null; l = l.next) {
      if (TRACE_CLASSLOADED) {
        VM.sysWrite("    ");
        VM.sysWrite(getClass(l.callback));
        VM.sysWrite("\n");
      }
      ((ClassLoadedMonitor) l.callback).notifyClassLoaded(klass);
    }
    classLoadedEnabled = true;
  }

  /**
   * Interface for monitoring class resolution.
   */
  public static interface ClassResolvedMonitor {
    /**
     * Notify the monitor that a class has been resolved.
     * @param klass the class that was resolved
     */
    public void notifyClassResolved(VM_Class klass);
  }

  /**
   * Class resolution callback list.
   */
  private static CallbackList classResolvedCallbacks = null;
  private static Object classResolvedLock = new Object();
  private static boolean classResolvedEnabled = true;

  /**
   * Register a callback for class resolution.
   * @param cb the object to notify when event happens
   */
  public static void addClassResolvedMonitor(ClassResolvedMonitor cb) {
    synchronized (classResolvedLock) {
      if (TRACE_ADDMONITOR || TRACE_CLASSRESOLVED) {
        VM.sysWrite("adding class resolved monitor: ");
        VM.sysWrite(getClass(cb));
        VM.sysWrite("\n");
      }
      classResolvedCallbacks = new CallbackList(cb, classResolvedCallbacks);
    }
  }

  /**
   * Notify the callback manager that a class has been resolved.
   * @param klass the class that was resolved
   */
  public static void notifyClassResolved(VM_Class klass) {
    // NOTE: will need synchronization if allowing unregistering
    if (!classResolvedEnabled) return;
    classResolvedEnabled = false;
    if (TRACE_CLASSRESOLVED) {
      //VM.sysWrite(getThread(), false);
      //VM.sysWrite(": ");
      VM.sysWrite("invoking class resolved monitors: ");
      VM.sysWrite(klass.getDescriptor());
      VM.sysWrite("\n");
      //printStack("From: ");
    }
    for (CallbackList l = classResolvedCallbacks; l != null; l = l.next) {
      if (TRACE_CLASSRESOLVED) {
        VM.sysWrite("    ");
        VM.sysWrite(getClass(l.callback));
        VM.sysWrite("\n");
      }
      ((ClassResolvedMonitor) l.callback).notifyClassResolved(klass);
    }
    classResolvedEnabled = true;
  }

  /**
   * Interface for monitoring class instantiation.
   */
  public static interface ClassInstantiatedMonitor {
    /**
     * Notify the monitor that a class has been instantiated.
     * @param klass the class that was instantiated
     */
    public void notifyClassInstantiated(VM_Class klass);
  }

  /**
   * Class instantiation callback list.
   */
  private static CallbackList classInstantiatedCallbacks = null;
  private static Object classInstantiatedLock = new Object();
  private static boolean classInstantiatedEnabled = true;

  /**
   * Register a callback for class instantiation.
   * @param cb the object to notify when event happens
   */
  public static void addClassInstantiatedMonitor(ClassInstantiatedMonitor cb) {
    synchronized (classInstantiatedLock) {
      if (TRACE_ADDMONITOR || TRACE_CLASSINSTANTIATED) {
        VM.sysWrite("adding class instantiated monitor: ");
        VM.sysWrite(getClass(cb));
        VM.sysWrite("\n");
      }
      classInstantiatedCallbacks = new CallbackList(cb,
                                                    classInstantiatedCallbacks);
    }
  }

  /**
   * Notify the callback manager that a class has been instantiated.
   * @param klass the class that was instantiated
   */
  public static void notifyClassInstantiated(VM_Class klass) {
    // NOTE: will need synchronization if allowing unregistering
    if (!classInstantiatedEnabled) return;
    classInstantiatedEnabled = false;
    if (TRACE_CLASSINSTANTIATED) {
      //VM.sysWrite(getThread(), false);
      //VM.sysWrite(": ");
      VM.sysWrite("invoking class instantiated monitors: ");
      VM.sysWrite(klass.getDescriptor());
      VM.sysWrite("\n");
      //printStack("From: ");
    }
    for (CallbackList l = classInstantiatedCallbacks; l != null; l = l.next) {
      if (TRACE_CLASSINSTANTIATED) {
        VM.sysWrite("    ");
        VM.sysWrite(getClass(l.callback));
        VM.sysWrite("\n");
      }
      ((ClassInstantiatedMonitor) l.callback).notifyClassInstantiated(klass);
    }
    classInstantiatedEnabled = true;
  }

  /**
   * Interface for monitoring class initialization.
   */
  public static interface ClassInitializedMonitor {
    /**
     * Notify the monitor that a class has been initialized.
     * @param klass the class that was initialized
     */
    public void notifyClassInitialized(VM_Class klass);
  }

  /**
   * Class initialization callback list.
   */
  private static CallbackList classInitializedCallbacks = null;
  private static Object classInitializedLock = new Object();
  private static boolean classInitializedEnabled = true;

  /**
   * Register a callback for class initialization.
   * @param cb the object to notify when event happens
   */
  public static void addClassInitializedMonitor(ClassInitializedMonitor cb) {
    synchronized (classInitializedLock) {
      if (TRACE_ADDMONITOR || TRACE_CLASSINITIALIZED) {
        VM.sysWrite("adding class initialized monitor: ");
        VM.sysWrite(getClass(cb));
        VM.sysWrite("\n");
      }
      classInitializedCallbacks = new CallbackList(cb,
                                                   classInitializedCallbacks);
    }
  }

  /**
   * Notify the callback manager that a class has been initialized.
   * @param klass the class that was initialized
   */
  public static void notifyClassInitialized(VM_Class klass) {
    // NOTE: will need synchronization if allowing unregistering
    if (!classInitializedEnabled) return;
    classInitializedEnabled = false;
    if (TRACE_CLASSINITIALIZED) {
      //VM.sysWrite(getThread(), false);
      //VM.sysWrite(": ");
      VM.sysWrite("invoking class initialized monitors: ");
      VM.sysWrite(klass.getDescriptor());
      VM.sysWrite("\n");
      //printStack("From: ");
    }
    for (CallbackList l = classInitializedCallbacks; l != null; l = l.next) {
      if (TRACE_CLASSINITIALIZED) {
        VM.sysWrite("    ");
        VM.sysWrite(getClass(l.callback));
        VM.sysWrite("\n");
      }
      ((ClassInitializedMonitor) l.callback).notifyClassInitialized(klass);
    }
    classInitializedEnabled = true;
  }

  /**
   * Interface for monitoring method override.
   */
  public static interface MethodOverrideMonitor {
    /**
     * Notify the monitor that a method has been overridden.
     * @param method the method that was loaded
     * @param parent the method that it overrides (null if none)
     */
    public void notifyMethodOverride(VM_Method method, VM_Method parent);
  }

  /**
   * Method override callback list.
   */
  private static CallbackList methodOverrideCallbacks = null;
  private static Object methodOverrideLock = new Object();
  private static boolean methodOverrideEnabled = true;

  /**
   * Register a callback for method override.
   * @param cb the object to notify when event happens
   */
  public static void addMethodOverrideMonitor(MethodOverrideMonitor cb) {
    synchronized (methodOverrideLock) {
      if (TRACE_ADDMONITOR || TRACE_METHODOVERRIDE) {
        VM.sysWrite("adding method override monitor: ");
        VM.sysWrite(getClass(cb));
        VM.sysWrite("\n");
      }
      methodOverrideCallbacks = new CallbackList(cb, methodOverrideCallbacks);
    }
  }

  /**
   * Notify the callback manager that a method has been overridden.
   * @param method the method that was loaded
   * @param parent the method that it overrides (null if none)
   */
  public static void notifyMethodOverride(VM_Method method, VM_Method parent) {
    // NOTE: will need synchronization if allowing unregistering
    if (!methodOverrideEnabled) return;
    methodOverrideEnabled = false;
    if (TRACE_METHODOVERRIDE) {
      //VM.sysWrite(getThread(), false);
      //VM.sysWrite(": ");
      VM.sysWrite("invoking method override monitors: ");
      VM.sysWrite(method);
      VM.sysWrite(":");
      if (parent != null)
        VM.sysWrite(parent);
      else
        VM.sysWrite("null");
      VM.sysWrite("\n");
      //printStack("From: ");
    }
    for (CallbackList l = methodOverrideCallbacks; l != null; l = l.next) {
      if (TRACE_METHODOVERRIDE) {
        VM.sysWrite("    ");
        VM.sysWrite(getClass(l.callback));
        VM.sysWrite("\n");
      }
      ((MethodOverrideMonitor) l.callback).notifyMethodOverride(method, parent);
    }
    methodOverrideEnabled = true;
  }

  /**
   * Interface for monitoring method compile.
   */
  public static interface MethodCompileMonitor {
    /**
     * Notify the monitor that a method is about to be compiled.
     * NOTE: use VM.runningVM and VM.writingBootImage to determine
     *       whether the VM is running
     * @param method the method that will be compiled
     * @param compiler the compiler that will be invoked.
     *        Values are constants in VM_CompiledMethod
     */
    public void notifyMethodCompile(VM_Method method, int compiler);
  }

  /**
   * Method compile callback list.
   */
  private static CallbackList methodCompileCallbacks = null;
  private static Object methodCompileLock = new Object();
  private static boolean methodCompileEnabled = true;

  /**
   * Register a callback for method compile.
   * @param cb the object to notify when event happens
   */
  public static void addMethodCompileMonitor(MethodCompileMonitor cb) {
    synchronized (methodCompileLock) {
      if (TRACE_ADDMONITOR || TRACE_METHODCOMPILE) {
        VM.sysWrite("adding method compile monitor: ");
        VM.sysWrite(getClass(cb));
        VM.sysWrite("\n");
      }
      methodCompileCallbacks = new CallbackList(cb, methodCompileCallbacks);
    }
  }

  /**
   * Notify the callback manager that a method is about to be compiled.
   * NOTE: use VM.runningVM and VM.writingBootImage to determine
   *       whether the VM is running
   * @param method the method that will be compiled
   * @param compiler the compiler that will be invoked
   *        Values are constants in VM_CompiledMethod
   */
  public static void notifyMethodCompile(VM_Method method, int compiler) {
    // NOTE: will need synchronization if allowing unregistering
    if (!methodCompileEnabled) return;
    methodCompileEnabled = false;
    if (TRACE_METHODCOMPILE) {
      //VM.sysWrite(getThread(), false);
      //VM.sysWrite(": ");
      VM.sysWrite("invoking method compile monitors: ");
      VM.sysWrite(method);
      VM.sysWrite(":");
      VM.sysWrite(compiler);
      VM.sysWrite("\n");
      //printStack("From: ");
    }
    for (CallbackList l = methodCompileCallbacks; l != null; l = l.next) {
      if (TRACE_METHODCOMPILE) {
        VM.sysWrite("    ");
        VM.sysWrite(getClass(l.callback));
        VM.sysWrite("\n");
      }
      ((MethodCompileMonitor) l.callback).notifyMethodCompile(method, compiler);
    }
    methodCompileEnabled = true;
  }

  /**
   * Interface for monitoring forName calls.
   */
  public static interface ForNameMonitor {
    /**
     * Notify the monitor that java.lang.Class.forName was called.
     * @param type the type that will be returned
     */
    public void notifyForName(VM_Type type);
  }

  /**
   * forName call callback list.
   */
  private static CallbackList forNameCallbacks = null;
  private static Object forNameLock = new Object();
  private static boolean forNameEnabled = true;

  /**
   * Register a callback for forName call.
   * @param cb the object to notify when event happens
   */
  public static void addForNameMonitor(ForNameMonitor cb) {
    synchronized (forNameLock) {
      if (TRACE_ADDMONITOR || TRACE_FORNAME) {
        VM.sysWrite("adding forName monitor: ");
        VM.sysWrite(getClass(cb));
        VM.sysWrite("\n");
      }
      forNameCallbacks = new CallbackList(cb, forNameCallbacks);
    }
  }

  /**
   * Notify the monitor that java.lang.Class.forName was called.
   * @param type the type that will be returned
   */
  public static void notifyForName(VM_Type type) {
    // NOTE: will need synchronization if allowing unregistering
    if (!forNameEnabled) return;
    forNameEnabled = false;
    if (TRACE_FORNAME) {
      //VM.sysWrite(getThread(), false);
      //VM.sysWrite(": ");
      VM.sysWrite("invoking forName monitors: ");
      VM.sysWrite(type.getDescriptor());
      VM.sysWrite("\n");
      //printStack("From: ");
    }
    for (CallbackList l = forNameCallbacks; l != null; l = l.next) {
      if (TRACE_FORNAME) {
        VM.sysWrite("    ");
        VM.sysWrite(getClass(l.callback));
        VM.sysWrite("\n");
      }
      ((ForNameMonitor) l.callback).notifyForName(type);
    }
    forNameEnabled = true;
  }


  /**
   * Interface for monitoring defineClass calls.
   */
  public static interface DefineClassMonitor {
    /**
     * Notify the monitor that java.lang.Class.defineclass was called.
     * @param type the type that will be returned
     */
    public void notifyDefineClass(VM_Type type);
  }

  /**
   * defineclass call callback list.
   */
  private static CallbackList defineClassCallbacks = null;
  private static Object defineClassLock = new Object();
  private static boolean defineClassEnabled = true;

  /**
   * Register a callback for defineClass call.
   * @param cb the object to notify when event happens
   */
  public static void addDefineClassMonitor(DefineClassMonitor cb) {
    synchronized (defineClassLock) {
      if (TRACE_ADDMONITOR || TRACE_DEFINECLASS) {
        VM.sysWrite("adding defineclass monitor: ");
        VM.sysWrite(getClass(cb));
        VM.sysWrite("\n");
      }
      defineClassCallbacks = new CallbackList(cb, defineClassCallbacks);
    }
  }

  /**
   * Notify the monitor that java.lang.Class.defineclass was called.
   * @param type the type that will be returned
   */
  public static void notifyDefineClass(VM_Type type) {
    // NOTE: will need synchronization if allowing unregistering
    if (!defineClassEnabled) return;
    defineClassEnabled = false;
    if (TRACE_DEFINECLASS) {
      //VM.sysWrite(getThread(), false);
      //VM.sysWrite(": ");
      VM.sysWrite("invoking defineclass monitors: ");
      VM.sysWrite(type.getDescriptor());
      VM.sysWrite("\n");
      //printStack("From: ");
    }
    for (CallbackList l = defineClassCallbacks; l != null; l = l.next) {
      if (TRACE_DEFINECLASS) {
        VM.sysWrite("    ");
        VM.sysWrite(getClass(l.callback));
        VM.sysWrite("\n");
      }
      ((DefineClassMonitor) l.callback).notifyDefineClass(type);
    }
    defineClassEnabled = true;
  }



  /**
   * Interface for monitoring loadClass calls.
   */
  public static interface LoadClassMonitor {
    /**
     * Notify the monitor that java.lang.Class.loadclass was called.
     * @param type the type that will be returned
     */
    public void notifyLoadClass(VM_Type type);
  }

  /**
   * loadclass call callback list.
   */
  private static CallbackList loadClassCallbacks = null;
  private static Object loadClassLock = new Object();
  private static boolean loadClassEnabled = true;

  /**
   * Register a callback for loadClass call.
   * @param cb the object to notify when event happens
   */
  public static void addLoadClassMonitor(LoadClassMonitor cb) {
    synchronized (loadClassLock) {
      if (TRACE_ADDMONITOR || TRACE_LOADCLASS) {
        VM.sysWrite("adding loadclass monitor: ");
        VM.sysWrite(getClass(cb));
        VM.sysWrite("\n");
      }
      loadClassCallbacks = new CallbackList(cb, loadClassCallbacks);
    }
  }

  /**
   * Notify the monitor that java.lang.Class.loadclass was called.
   * @param type the type that will be returned
   */
  public static void notifyLoadClass(VM_Type type) {
    // NOTE: will need synchronization if allowing unregistering
    if (!loadClassEnabled) return;
    loadClassEnabled = false;
    if (TRACE_LOADCLASS) {
      //VM.sysWrite(getThread(), false);
      //VM.sysWrite(": ");
      VM.sysWrite("invoking loadclass monitors: ");
      VM.sysWrite(type.getDescriptor());
      VM.sysWrite("\n");
      //printStack("From: ");
    }
    for (CallbackList l = loadClassCallbacks; l != null; l = l.next) {
      if (TRACE_LOADCLASS) {
        VM.sysWrite("    ");
        VM.sysWrite(getClass(l.callback));
        VM.sysWrite("\n");
      }
      ((LoadClassMonitor) l.callback).notifyLoadClass(type);
    }
    loadClassEnabled = true;
  }


  /**
   * Interface for monitoring boot image writing.
   */
  public static interface BootImageMonitor {
    /**
     * Notify the monitor that boot image writing is in progress.
     * @param types the types that are included in the boot image
     */
    public void notifyBootImage(Enumeration types);
  }

  /**
   * Boot image writing callback list.
   */
  private static CallbackList bootImageCallbacks = null;
  private static Object bootImageLock = new Object();
  private static boolean bootImageEnabled = true;

  /**
   * Register a callback for boot image writing.
   * @param cb the object to notify when event happens
   */
  public static void addBootImageMonitor(BootImageMonitor cb) {
    synchronized (bootImageLock) {
      if (TRACE_ADDMONITOR || TRACE_BOOTIMAGE) {
        VM.sysWrite("adding boot image writing monitor: ");
        VM.sysWrite(getClass(cb));
        VM.sysWrite("\n");
      }
      bootImageCallbacks = new CallbackList(cb, bootImageCallbacks);
    }
  }

  /**
   * Notify the monitor that boot image writing is in progress.
   * @param types the types that are included in the boot image
   */
  public static void notifyBootImage(Enumeration types) {
    // NOTE: will need synchronization if allowing unregistering
    if (!bootImageEnabled) return;
    bootImageEnabled = false;
    if (TRACE_BOOTIMAGE) {
      //VM.sysWrite(getThread(), false);
      //VM.sysWrite(": ");
      VM.sysWrite("invoking boot image writing monitors\n");
    }
    for (CallbackList l = bootImageCallbacks; l != null; l = l.next) {
      if (TRACE_BOOTIMAGE) {
        VM.sysWrite("    ");
        VM.sysWrite(getClass(l.callback));
        VM.sysWrite("\n");
      }
      ((BootImageMonitor) l.callback).notifyBootImage(types);
    }
    bootImageEnabled = true;
  }

  /**
   * Interface for monitoring VM startup.
   */
  public static interface StartupMonitor {
    /**
     * Notify the monitor that the VM has started up.
     */
    public void notifyStartup();
  }

  /**
   * VM startup callback list.
   */
  private static CallbackList startupCallbacks = null;
  private static Object startupLock = new Object();
  private static boolean startupEnabled = true;

  /**
   * Register a callback for VM startup.
   * @param cb the object to notify when event happens
   */
  public static void addStartupMonitor(StartupMonitor cb) {
    synchronized (startupLock) {
      if (TRACE_ADDMONITOR || TRACE_STARTUP) {
        VM.sysWrite("adding startup monitor: ");
        VM.sysWrite(getClass(cb));
        VM.sysWrite("\n");
      }
      startupCallbacks = new CallbackList(cb, startupCallbacks);
    }
  }

  /**
   * Notify the callback manager that the VM has started up.
   * NOTE: Runs in the main thread!
   */
  public static void notifyStartup() {
    // NOTE: will need synchronization if allowing unregistering
    if (!startupEnabled) return;
    startupEnabled = false;
    if (TRACE_STARTUP) {
      //VM.sysWrite(getThread(), false);
      //VM.sysWrite(": ");
      VM.sysWrite("invoking startup monitors\n");
    }
    for (CallbackList l = startupCallbacks; l != null; l = l.next) {
      if (TRACE_STARTUP) {
        VM.sysWrite("    ");
        VM.sysWrite(getClass(l.callback));
        VM.sysWrite("\n");
      }
      ((StartupMonitor) l.callback).notifyStartup();
    }
    startupEnabled = true;
  }

  /**
   * Interface for monitoring VM exit.
   */
  public static interface ExitMonitor {
    /**
     * Notify the monitor that the VM is about to exit.
     * @param value the exit value
     */
    public void notifyExit(int value);
  }

  /**
   * VM exit callback list.
   */
  private static CallbackList exitCallbacks = null;
  private static Object exitLock = new VM_Synchronizer();
  private static boolean exitCallbacksStarted = false;

  /**
   * Register a callback for VM exit.
   * @param cb the object to notify when event happens
   */
  public static void addExitMonitor(ExitMonitor cb) {
    synchronized (exitLock) {
      if (TRACE_ADDMONITOR || TRACE_EXIT) {
        VM.sysWrite("adding exit monitor: ");
        VM.sysWrite(getClass(cb));
        VM.sysWrite("\n");
      }
      exitCallbacks = new CallbackList(cb, exitCallbacks);
    }
  }

  /**
   * Notify the callback manager that the VM is about to exit.
   * Will return once all the callbacks are invoked.
   * @param value the exit value
   */
  public static void notifyExit(final int value) {
    synchronized (exitLock) {
      if (exitCallbacksStarted) return;
      if (exitCallbacks == null) return;
      exitCallbacksStarted = true;
      if (TRACE_EXIT) {
        //VM.sysWrite(VM_Callbacks.getThread(), false);
        //VM.sysWrite(": ");
        VM.sysWrite("invoking exit monitors: ");
        VM.sysWriteln(value);
        //printStack("From: ");
      }
      for (CallbackList l = exitCallbacks; l != null; l = l.next) {
        if (TRACE_EXIT) {
          VM.sysWrite("    ");
          VM.sysWrite(VM_Callbacks.getClass(l.callback));
          VM.sysWrite("\n");
        }
        ((ExitMonitor) l.callback).notifyExit(value);
      }
    }
  }

  /**
   * Interface for monitoring when an application starts executing
   */
  public static interface AppStartMonitor {
    /**
     * Notify the monitor that the application has started executing
     * @param app application name
     */
    public void notifyAppStart(String app);
  }

  /**
   * Application Start executing callback list.
   */
  private static CallbackList appStartCallbacks = null;
  private static Object appStartLock = new Object();

  /**
   * Register a callback for when the application starts executing
   * @param cb the object to notify when event happens
   */
  public static void addAppStartMonitor(AppStartMonitor cb) {
    synchronized (appStartLock) {
      if (TRACE_ADDMONITOR || TRACE_APP_START) {
        VM.sysWrite("adding application start monitor: ");
        VM.sysWrite(getClass(cb));
        VM.sysWrite("\n");
      }
      appStartCallbacks = new CallbackList(cb, appStartCallbacks);
    }
  }

  /**
   * Notify the callback manager that the application started executing
   * Will return once all the callbacks are invoked.
   * @param app name of application
   */
  public static void notifyAppStart(String app) {
    synchronized (appStartLock) {
      if (appStartCallbacks == null) return;
      if (TRACE_APP_START) {
        VM.sysWrite("invoking application start monitors\n");
      }
      for (CallbackList l = appStartCallbacks; l != null; l = l.next) {
        if (TRACE_APP_START) {
          VM.sysWrite("    ");
          VM.sysWrite(VM_Callbacks.getClass(l.callback));
          VM.sysWrite("\n");
        }
        ((AppStartMonitor) l.callback).notifyAppStart(app);
      }
    }
  }

  /**
   * Interface for monitoring when an application completes executing
   */
  public static interface AppCompleteMonitor {
    /**
     * Notify the monitor that the application has completed executing
     * @param app  name of application
     */
    public void notifyAppComplete(String app);
  }

  /**
   * Application Execution Complete callback list.
   */
  private static CallbackList appCompleteCallbacks = null;
  private static Object appCompleteLock = new Object();

  /**
   * Register a callback for when the application completes executing
   * @param cb the object to notify when event happens
   */
  public static void addAppCompleteMonitor(AppCompleteMonitor cb) {
    synchronized (appCompleteLock) {
      if (TRACE_ADDMONITOR || TRACE_APP_COMPLETE) {
        VM.sysWrite("adding application complete monitor: ");
        VM.sysWrite(getClass(cb));
        VM.sysWrite("\n");
      }
      appCompleteCallbacks = new CallbackList(cb, appCompleteCallbacks);
    }
  }

  /**
   * Notify the callback manager that the application completed executing
   * Will return once all the callbacks are invoked.
   */
  public static void notifyAppComplete(String app) {
    synchronized (appCompleteLock) {
      if (appCompleteCallbacks == null) return;
      if (TRACE_APP_COMPLETE) {
        VM.sysWrite("invoking application complete monitors for application ");VM.sysWrite(app);VM.sysWrite("\n");
      }
      for (CallbackList l = appCompleteCallbacks; l != null; l = l.next) {
        if (TRACE_APP_COMPLETE) {
          VM.sysWrite("    ");
          VM.sysWrite(VM_Callbacks.getClass(l.callback));
          VM.sysWrite("\n");
        }
        ((AppCompleteMonitor) l.callback).notifyAppComplete(app);
      }
    }
  }

  /**
   * Interface for monitoring when an application starts a run
   */
  public static interface AppRunStartMonitor {
    /**
     * Notify the monitor that the application has started a run
     * @param app application name
     * @param run run number
     */
    public void notifyAppRunStart(String app, int run);
  }

  /**
   * Application Run Start callback list.
   */
  private static CallbackList appRunStartCallbacks = null;
  private static Object appRunStartLock = new VM_Synchronizer();

  /**
   * Register a callback for when the application starts a run
   * @param cb the object to notify when event happens
   */
  public static void addAppRunStartMonitor(AppRunStartMonitor cb) {
    synchronized (appRunStartLock) {
      if (TRACE_ADDMONITOR || TRACE_APP_RUN_START) {
        VM.sysWrite("adding application run start monitor: ");
        VM.sysWrite(getClass(cb));
        VM.sysWrite("\n");
      }
      appRunStartCallbacks = new CallbackList(cb, appRunStartCallbacks);
    }
  }

  /**
   * Notify the callback manager that the application started a run
   * Will return once all the callbacks are invoked.
   */
  public static void notifyAppRunStart(String app, int run) {
    synchronized (appRunStartLock) {
      if (appRunStartCallbacks == null) return;
      if (TRACE_APP_RUN_START) {
        //VM.sysWrite(getThread(), false);
        //VM.sysWrite(": ");
        VM.sysWrite("invoking the start monitor for application ");VM.sysWrite(app);VM.sysWrite(" at run ");VM.sysWrite(run);VM.sysWrite("\n");
      }
      for (CallbackList l = appRunStartCallbacks; l != null; l = l.next) {
        if (TRACE_APP_RUN_START) {
          VM.sysWrite("    ");
          VM.sysWrite(VM_Callbacks.getClass(l.callback));
          VM.sysWrite("\n");
        }
        ((AppRunStartMonitor) l.callback).notifyAppRunStart(app, run);
      }
    }
  }

  /**
   * Interface for monitoring when an application completes a run
   */
  public static interface AppRunCompleteMonitor {
    /**
     * Notify the monitor that the application has completed a run
     * @param app name of application
     * @param run run number
     */
    public void notifyAppRunComplete(String app, int run);
  }

  /**
   * Application Run Complete callback list.
   */
  private static CallbackList appRunCompleteCallbacks = null;
  private static Object appRunCompleteLock = new VM_Synchronizer();

  /**
   * Register a callback for when the application completes a run
   * @param cb the object to notify when event happens
   */
  public static void addAppRunCompleteMonitor(AppRunCompleteMonitor cb) {
    synchronized (appRunCompleteLock) {
      if (TRACE_ADDMONITOR || TRACE_APP_RUN_COMPLETE) {
        VM.sysWrite("adding application run complete monitor: ");
        VM.sysWrite(getClass(cb));
        VM.sysWrite("\n");
      }
      appRunCompleteCallbacks = new CallbackList(cb, appRunCompleteCallbacks);
    }
  }

  /**
   * Notify the callback manager that the application completed a run
   * Will return once all the callbacks are invoked.
   * @param app name of application
   * @param run run number
   */
  public static void notifyAppRunComplete(String app, int run) {
    synchronized (appRunCompleteLock) {
      if (appRunCompleteCallbacks == null) return;
      if (TRACE_APP_RUN_COMPLETE) {
        //VM.sysWrite(getThread(), false);
        //VM.sysWrite(": ");
        VM.sysWrite("invoking the complete monitor for application ", app);
        VM.sysWriteln(" at run ", run);
      }
      for (CallbackList l = appRunCompleteCallbacks; l != null; l = l.next) {
        if (TRACE_APP_RUN_COMPLETE) {
          VM.sysWrite("    ");
          VM.sysWrite(VM_Callbacks.getClass(l.callback));
          VM.sysWrite("\n");
        }
        ((AppRunCompleteMonitor) l.callback).notifyAppRunComplete(app, run);
      }
    }
  }

  ////////////////////
  // IMPLEMENTATION //
  ////////////////////

  /**
   * Initialize callbacks.
   */
  public static void init() { }

  /**
   * Perform boot-time actions.
   */
  public static void boot() { }

  /**
   * Linked list of callbacks.
   */
  private static class CallbackList {
    public CallbackList(Object cb, CallbackList n) { callback = cb; next = n; }
    public final Object callback;
    public final CallbackList next;
  }

  private final static boolean TRACE_ADDMONITOR        = false;
  private final static boolean TRACE_CLASSLOADED       = false;
  private final static boolean TRACE_CLASSRESOLVED     = false;
  private final static boolean TRACE_CLASSINITIALIZED  = false;
  private final static boolean TRACE_CLASSINSTANTIATED = false;
  private final static boolean TRACE_METHODOVERRIDE    = false;
  private final static boolean TRACE_METHODCOMPILE     = false;
  private final static boolean TRACE_FORNAME           = false;
  private final static boolean TRACE_DEFINECLASS       = false;
  private final static boolean TRACE_LOADCLASS         = false;
  private final static boolean TRACE_BOOTIMAGE         = false;
  private final static boolean TRACE_STARTUP           = false;
  private final static boolean TRACE_EXIT              = false;
  private final static boolean TRACE_APP_RUN_START     = false;
  private final static boolean TRACE_APP_RUN_COMPLETE  = false;
  private final static boolean TRACE_APP_START         = false;
  private final static boolean TRACE_APP_COMPLETE      = false;

  /**
   * Return class name of the object.
   * @return class name of the object
   */
  private static VM_Atom getClass(Object o) {
    if (VM.runningVM)
        return java.lang.JikesRVMSupport.getTypeForClass(o.getClass()).getDescriptor();
    else
        return VM_Atom.findOrCreateAsciiAtom(o.getClass().getName());
  }

  /**
   * Return current thread id.
   * @return current thread id
   */
  private static int getThread() {
    if (VM.runningVM)
      return VM_Thread.getCurrentThread().getIndex();
    else
      return System.identityHashCode(Thread.currentThread());
  }

  /**
   * Print current stack trace.
   */
  private static void printStack(String message) {
    if (VM.runningVM)
      VM_Scheduler.traceback(message);
    else
      new Throwable(message).printStackTrace();
  }
}

