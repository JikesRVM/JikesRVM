/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package java.lang;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.StringTokenizer;

import org.apache.harmony.luni.util.DeleteOnExit;
import org.apache.harmony.luni.internal.net.www.protocol.jar.JarURLConnection;
import org.apache.harmony.luni.internal.process.SystemProcess;
import org.apache.harmony.lang.RuntimePermissionCollection;
import org.apache.harmony.kernel.vm.VM;

import org.jikesrvm.classloader.RVMClass;
import org.jikesrvm.mm.mminterface.MemoryManager;
import org.jikesrvm.runtime.DynamicLibrary;
import org.jikesrvm.scheduler.RVMThread;

/**
 * This class, with the exception of the exec() APIs, must be implemented by the
 * VM vendor. The exec() APIs must first do any required security checks, and
 * then call org.apache.harmony.luni.internal.process.SystemProcess.create().
 * The Runtime interface.
 */
public class Runtime {

  private static final Runtime singleton = new Runtime();

  /** Shutdown hooks */
  private static ArrayList<Thread> hooksList = new ArrayList<Thread>();

  /**
   * 0 - normal work
   * 1 - being shutdown sequence running
   * 2 - being finalizing
   */
  private static int VMState = 0;

  static boolean finalizeOnExit = false;

  /**
   * Prevent this class from being instantiated
   */
  private Runtime(){
    //do nothing
  }

  /**
   * Execute progArray[0] in a separate platform process The new process
   * inherits the environment of the caller.
   *
   * @param progArray the array containing the program to execute as well as
   *        any arguments to the program.
   * @throws java.io.IOException if the program cannot be executed
   * @throws SecurityException if the current SecurityManager disallows
   *         program execution
   * @see SecurityManager#checkExec
   */
  public Process exec(String[] progArray) throws java.io.IOException {
    return exec(progArray, null, null);
  }

  /**
   * Execute progArray[0] in a separate platform process The new process uses
   * the environment provided in envp
   *
   * @param progArray the array containing the program to execute a well as
   *        any arguments to the program.
   * @param envp the array containing the environment to start the new process
   *        in.
   * @throws java.io.IOException if the program cannot be executed
   * @throws SecurityException if the current SecurityManager disallows
   *         program execution
   * @see SecurityManager#checkExec
   */
  public Process exec(String[] progArray, String[] envp) throws java.io.IOException {
    return exec(progArray, envp, null);
  }

  /**
   * Execute progArray[0] in a separate platform process. The new process uses
   * the environment provided in envp
   *
   * @param progArray the array containing the program to execute a well as
   *        any arguments to the program.
   * @param envp the array containing the environment to start the new process
   *        in.
   * @param directory the directory in which to execute progArray[0]. If null,
   *        execute in same directory as parent process.
   * @throws java.io.IOException if the program cannot be executed
   * @throws SecurityException if the current SecurityManager disallows
   *         program execution
   * @see SecurityManager#checkExec
   */
  public Process exec(String[] progArray, String[] envp, File directory)
  throws java.io.IOException {
    SecurityManager currentSecurity = System.getSecurityManager();
    if (currentSecurity != null) {
      currentSecurity.checkExec(progArray[0]);
    }
    if (progArray == null) {
      throw new NullPointerException("Command argument shouldn't be empty.");
    }
    if (progArray.length == 0) {
      throw new IndexOutOfBoundsException();
    }
    for (int i = 0; i < progArray.length; i++) {
      if (progArray[i] == null) {
        throw new NullPointerException("An element of progArray shouldn't be empty.");
      }
    }
    if (envp == null) {
      envp = new String[0];
    } else if (envp.length > 0) {
      for (int i = 0; i < envp.length; i++) {
        if (envp[i] == null) {
          throw new NullPointerException("An element of envp shouldn't be empty.");
        }
      }
    }
    return SystemProcess.create(progArray, envp, directory);
  }

  /**
   * Execute program in a separate platform process The new process inherits
   * the environment of the caller.
   *
   * @param prog the name of the program to execute
   * @throws java.io.IOException if the program cannot be executed
   * @throws SecurityException if the current SecurityManager disallows
   *         program execution
   * @see SecurityManager#checkExec
   */
  public Process exec(String prog) throws java.io.IOException {
    return exec(prog, null, null);
  }

  /**
   * Execute prog in a separate platform process The new process uses the
   * environment provided in envp
   *
   * @param prog the name of the program to execute
   * @param envp the array containing the environment to start the new process
   *        in.
   * @throws java.io.IOException if the program cannot be executed
   * @throws SecurityException if the current SecurityManager disallows
   *         program execution
   * @see SecurityManager#checkExec
   */
  public Process exec(String prog, String[] envp) throws java.io.IOException {
    return exec(prog, envp, null);
  }

  /**
   * Execute prog in a separate platform process The new process uses the
   * environment provided in envp
   *
   * @param prog the name of the program to execute
   * @param envp the array containing the environment to start the new process
   *        in.
   * @param directory the initial directory for the subprocess, or null to use
   *        the directory of the current process
   * @throws java.io.IOException if the program cannot be executed
   * @throws SecurityException if the current SecurityManager disallows
   *         program execution
   * @see SecurityManager#checkExec
   */
  public Process exec(String prog, String[] envp, File directory) throws java.io.IOException {
    if (prog == null) {
      throw new NullPointerException();
    }
    if (prog.length() == 0) {
      throw new IllegalArgumentException();
    }
    if (envp != null) {
      if (envp.length != 0) {
        for (int i = 0; i < envp.length; i++) {
          if (envp[i] == null) {
            throw new NullPointerException("An element of envp shouldn't be empty.");
          }
        }
      } else {
        envp = null;
      }
    }

    StringTokenizer st = new StringTokenizer(prog);
    String[] progArray = new String[st.countTokens()];
    int i = 0;

    while (st.hasMoreTokens()) {
      progArray[i++] = st.nextToken();

    }

    return exec(progArray, envp, directory);
  }

  void execShutdownSequence() {
    synchronized (hooksList) {
        if (VMState > 0) {
            return;
        }
        try {
            // Phase1: Execute all registered hooks.
            VMState = 1;
            for (Thread hook : hooksList) {
                hook.start();
            }

            for (Thread hook : hooksList) {
                while (true){
                    try {
                        hook.join();
                        break;
                    } catch (InterruptedException e) {
                        continue;
                    }
                }
            }
            // Phase2: Execute all finalizers if nessesary.
            VMState = 2;
            // TODO
            //FinalizerThread.shutdown(finalizeOnExit);

            // Close connections.
            if (VM.closeJars) {
                JarURLConnection.closeCachedFiles();
            }

            // Delete files.
            if (VM.deleteOnExit) {
                DeleteOnExit.deleteOnExit();
            }
        } catch (Throwable e) {
            // just catch all exceptions
        }
    }
  }

  /**
   * Causes the virtual machine to stop running, and the program to exit. If
   * runFinalizersOnExit(true) has been invoked, then all finalizers will be
   * run first.
   *
   * @param status the return code.
   * @throws SecurityException if the running thread is not allowed to cause
   *         the vm to exit.
   * @see SecurityManager#checkExit
   */
  public void exit(int status) {
    SecurityManager sm = System.getSecurityManager();
    if (sm != null) {
        sm.checkExit(status);
    }
    // Halt the VM if it is running finalizers.
    if (VMState == 2 && finalizeOnExit == true && status != 0) {
        halt(status);
    }

    execShutdownSequence();

    org.jikesrvm.VM.sysExit(status);
  }

  /**
   * Answers the amount of free memory resources which are available to the
   * running program.
   *
   */
  public long freeMemory() {
    return MemoryManager.freeMemory().toLong();
  }

  /**
   * Indicates to the virtual machine that it would be a good time to collect
   * available memory. Note that, this is a hint only.
   *
   */
  public void gc() {
    VMCommonLibrarySupport.gc();
  }

  /**
   * Return the single Runtime instance
   *
   */
  public static Runtime getRuntime() {
    return singleton;
  }

  /**
   * Loads and links the library specified by the argument.
   *
   * @param pathName the absolute (ie: platform dependent) path to the library
   *        to load
   * @throws UnsatisfiedLinkError if the library could not be loaded
   * @throws SecurityException if the library was not allowed to be loaded
   */
  public void load(String pathName) {
    load0(pathName, RVMClass.getClassLoaderFromStackFrame(1), true);
  }

  void load0(String filename, ClassLoader cL, boolean check) throws SecurityException, UnsatisfiedLinkError {
    if (check) {
      if (filename == null) {
        throw new NullPointerException();
      }

      SecurityManager currentSecurity = System.getSecurityManager();

      if (currentSecurity != null) {
        currentSecurity.checkLink(filename);
      }
    }
    if (DynamicLibrary.load(filename) == 0) {
      // TODO: make use of cL
      throw new UnsatisfiedLinkError("Can not find the library: " +
          filename);
    }
  }

  /**
   * Loads and links the library specified by the argument.
   *
   * @param libName the name of the library to load
   * @throws UnsatisfiedLinkError if the library could not be loaded
   * @throws SecurityException if the library was not allowed to be loaded
   */
  public void loadLibrary(String libName) {
    loadLibrary0(libName, RVMClass.getClassLoaderFromStackFrame(1), true);
  }

  void loadLibrary0(String libname, ClassLoader cL, boolean check) throws SecurityException, UnsatisfiedLinkError {
    if (check) {
      if (libname == null) {
        throw new NullPointerException();
      }

      SecurityManager currentSecurity = System.getSecurityManager();

      if (currentSecurity != null) {
        currentSecurity.checkLink(libname);
      }
    }

    String libFullName = null;

    if (cL!=null) {
      libFullName = cL.findLibrary(libname);
    }
    if (libFullName == null) {
      String allPaths = null;

      //XXX: should we think hard about security policy for this block?:
      String jlp = System.getProperty("java.library.path");
      String vblp = System.getProperty("vm.boot.library.path");
      String udp = System.getProperty("user.dir");
      String pathSeparator = System.getProperty("path.separator");
      String fileSeparator = System.getProperty("file.separator");
      allPaths = (jlp!=null?jlp:"")+(vblp!=null?pathSeparator+vblp:"")+(udp!=null?pathSeparator+udp:"");

      if (allPaths.length()==0) {
        throw new UnsatisfiedLinkError("Can not find the library: " +
            libname);
      }

      //String[] paths = allPaths.split(pathSeparator);
      String[] paths;
      {
        ArrayList<String> res = new ArrayList<String>();
        int curPos = 0;
        int l = pathSeparator.length();
        int i = allPaths.indexOf(pathSeparator);
        int in = 0;
        while (i != -1) {
          String s = allPaths.substring(curPos, i);
          res.add(s);
          in++;
          curPos = i + l;
          i = allPaths.indexOf(pathSeparator, curPos);
        }

        if (curPos <= allPaths.length()) {
          String s = allPaths.substring(curPos, allPaths.length());
          in++;
          res.add(s);
        }

        paths = (String[]) res.toArray(new String[in]);
      }

      libname = System.mapLibraryName(libname);
      for (int i=0; i<paths.length; i++) {
        if (paths[i]==null) {
          continue;
        }
        libFullName = paths[i] + fileSeparator + libname;
        try {
          this.load0(libFullName, cL, false);
          return;
        } catch (UnsatisfiedLinkError e) {
        }
      }
    } else {
      this.load0(libFullName, cL, false);
      return;
    }
    throw new UnsatisfiedLinkError("Can not find the library: " +
        libname);
  }

  /**
   * Provides a hint to the virtual machine that it would be useful to attempt
   * to perform any outstanding object finalizations.
   *
   */
  public void runFinalization() {
    return;
  }

  /**
   * Ensure that, when the virtual machine is about to exit, all objects are
   * finalized. Note that all finalization which occurs when the system is
   * exiting is performed after all running threads have been terminated.
   *
   * @param run true means finalize all on exit.
   * @deprecated This method is unsafe.
   */
  @Deprecated
  public static void runFinalizersOnExit(boolean run) {
    SecurityManager sm = System.getSecurityManager();
    if (sm != null) {
        sm.checkExit(0);
    }
    synchronized(hooksList) {
        finalizeOnExit = run;
    }
  }

  /**
   * Answers the total amount of memory resources which is available to (or in
   * use by) the running program.
   *
   */
  public long totalMemory() {
    return MemoryManager.totalMemory().toLong();
  }

  /**
   * Turns the output of debug information for instructions on or off.
   *
   * @param enable if true, turn trace on. false turns trace off.
   */
  public void traceInstructions(boolean enable) {
    return;
  }

  /**
   * Turns the output of debug information for methods on or off.
   *
   * @param enable if true, turn trace on. false turns trace off.
   */
  public void traceMethodCalls(boolean enable) {
    return;
  }

  /**
   * @deprecated Use InputStreamReader
   */
  @Deprecated
  public InputStream getLocalizedInputStream(InputStream stream) {
    throw new Error("TODO");
  }

  /**
   * @deprecated Use OutputStreamWriter
   */
  @Deprecated
  public OutputStream getLocalizedOutputStream(OutputStream stream) {
    throw new Error("TODO");
  }

  /**
   * Registers a new virtual-machine shutdown hook.
   *
   * @param hook the hook (a Thread) to register
   */
  public void addShutdownHook(Thread hook) {
    SecurityManager sm = System.getSecurityManager();
    if (sm != null) {
        sm.checkPermission(RuntimePermissionCollection.SHUTDOWN_HOOKS_PERMISSION);
    }
    // Check hook for null
    if (hook == null)
        throw new NullPointerException("null is not allowed here");

    if (hook.getState() != Thread.State.NEW) {
        throw new IllegalArgumentException();
    }
    if (VMState > 0) {
        throw new IllegalStateException();
    }
    synchronized (hooksList) {
        if (hooksList.contains(hook)) {
            throw new IllegalArgumentException();
        }
        hooksList.add(hook);
    }
  }

  /**
   * De-registers a previously-registered virtual-machine shutdown hook.
   *
   * @param hook the hook (a Thread) to de-register
   * @return true if the hook could be de-registered
   */
  public boolean removeShutdownHook(Thread hook) {
    SecurityManager sm = System.getSecurityManager();
    if (sm != null) {
        sm.checkPermission(RuntimePermissionCollection.SHUTDOWN_HOOKS_PERMISSION);
    }
    // Check hook for null
    if (hook == null)
        throw new NullPointerException("null is not allowed here");

    if (VMState > 0) {
        throw new IllegalStateException();
    }
    synchronized (hooksList) {
        return hooksList.remove(hook);
    }
  }

  /**
   * Causes the virtual machine to stop running, and the program to exit.
   * Finalizers will not be run first. Shutdown hooks will not be run.
   *
   * @param code
   *            the return code.
   * @throws SecurityException
   *                if the running thread is not allowed to cause the vm to
   *                exit.
   * @see SecurityManager#checkExit
   */
  public void halt(int code) {
    SecurityManager sm = System.getSecurityManager();

    if (sm != null) {
        sm.checkExit(code);
    }
    org.jikesrvm.VM.sysExit(code);
  }

  /**
   * Return the number of processors, always at least one.
   */
  public int availableProcessors() {
    return RVMThread.numProcessors;
  }

  /**
   * Return the maximum memory that will be used by the virtual machine, or
   * Long.MAX_VALUE.
   */
  public long maxMemory() {
    return MemoryManager.maxMemory().toLong();
  }
}
