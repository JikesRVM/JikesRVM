/*
 * (C) Copyright IBM Corp 2002
 */
//$Id$
package java.lang;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;

import java.net.URL;

import java.util.Properties;

import com.ibm.JikesRVM.classloader.VM_ClassLoader;
import com.ibm.JikesRVM.classloader.VM_SystemClassLoader;

import com.ibm.JikesRVM.FinalizerThread;
import com.ibm.JikesRVM.VM_Runtime;
import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_Process;
import com.ibm.JikesRVM.VM_UnimplementedError;

/**
 * Jikes RVM implementation of java.lang.Runtime.
 *
 * By convention, order methods in the same order
 * as they appear in the method summary list of Sun's 1.4 Javadoc API. 
 * 
 * @author Julian Dolby
 * @author Dave Grove
 */
public class Runtime {

  static SecurityManager securityManager;

  /**
   * single instance of Runtime class.
   */
  private static final Runtime runtime = new Runtime();

  /**
   * Prevent instantiation with private constructor
   */
  private Runtime() { }

  static Properties defaultProperties;

  static {
    defaultProperties = new Properties();

    defaultProperties.put("file.separator", "/");
    defaultProperties.put("path.separator", ":");
    defaultProperties.put("line.separator", "\n");
        
    defaultProperties.put("java.compiler", "JikesRVM");
    defaultProperties.put("java.vendor", "IBM");
    defaultProperties.put("java.version", "1.3.0");
    defaultProperties.put("java.vm.version", "1.3.0");
    defaultProperties.put("java.vm.name", "JikesRVM");
    defaultProperties.put("file.encoding", "8859_1");
    defaultProperties.put("java.io.tmpdir", "/tmp");

    defaultProperties.put("user.timezone", "America/New_York");
  }

  public void addShutdownHook(Thread hook) throws IllegalArgumentException, 
                                                  IllegalStateException, 
                                                  SecurityException {
    throw new VM_UnimplementedError();
  }

  public int availableProcessors() {
    throw new VM_UnimplementedError();
  }
    
  public Process exec(String prog) throws java.io.IOException, 
                                          SecurityException {
    return exec(prog, null);
  }
    
  public Process exec(String[] progArray) throws java.io.IOException,
                                                 SecurityException {
    return exec(progArray, null, null);
  }

  public Process exec(String[] progArray, String[] envp) throws java.io.IOException,
                                                                SecurityException {
    return exec(progArray, envp, null);
  }

  public Process exec(String[] progArray, String[] envp, java.io.File dir) throws java.io.IOException,
                                                                                  SecurityException {
    if (progArray != null && progArray.length > 0 && progArray[0] != null) {
      SecurityManager smngr = System.getSecurityManager();
      if (smngr != null)
        smngr.checkExec(progArray[0]);
      String dirPath = (dir != null) ? dir.getPath() : null;
      return new VM_Process(progArray[0], progArray, envp, dirPath);
    } else {
      throw new java.io.IOException();
    }
  }

  public Process exec(String prog, String[] envp) throws java.io.IOException,
                                                         SecurityException {
    //use a regular StringTokenizer to break the command_line into
    //small peaces. By convention the first argument is the name of the
    //command.
    int argsLenghPlusOne;
    int i=0;

    java.util.StringTokenizer slicer = new java.util.StringTokenizer(prog);

    String[] command = new String[argsLenghPlusOne=slicer.countTokens()];

    while (i<argsLenghPlusOne) {
      command[i++] = slicer.nextToken();
    }

    return exec(command, envp);
  }

  public Process exec(String command, String[] envp, java.io.File dir) throws java.io.IOException, 
                                                                              SecurityException {
    return exec(new String[]{command}, envp, dir);
  }
  
  public void exit (int code) throws SecurityException {
    SecurityManager smngr = System.getSecurityManager();
    if (smngr != null) {
      smngr.checkExit(code);
    }
    // TODO: run register shutdown hooks.
    VM.sysExit(code);
  }

  public long freeMemory() {
    return VM_Runtime.freeMemory();
  }

  public void gc() {
    VM_Runtime.gc();
  }

  public InputStream getLocalizedInputStream(InputStream stream) {
    return stream;
  }

  public OutputStream getLocalizedOutputStream(OutputStream stream) {
    return stream;
  }

  public static Runtime getRuntime() {
    return runtime;
  }

  public static void halt(int status) throws SecurityException {
    SecurityManager smngr = System.getSecurityManager();
    if (smngr != null) {
      smngr.checkExit(status);
    }
    throw new VM_UnimplementedError();
  }

  public synchronized void load(String pathName) throws SecurityException,
                                                        UnsatisfiedLinkError {
    SecurityManager smngr = System.getSecurityManager();
    if (smngr != null) {
      smngr.checkLink(pathName);
    }
    VM_ClassLoader.load(pathName);
  }
    
  public void loadLibrary(String libName) throws SecurityException, 
                                                 UnsatisfiedLinkError {
    SecurityManager smngr = System.getSecurityManager();
    if (smngr != null) {
      smngr.checkLink(libName);
    }
    Class[] classes = VMSecurityManager.getClassContext();
    ClassLoader loader = classes[1].getClassLoader();
    
    if (loader == null) loader = VM_SystemClassLoader.getVMClassLoader();

    String libPath = loader.findLibrary(libName);
    if (libPath != null) {
      VM_ClassLoader.load(libPath);
      return;
    } else {
      VM_ClassLoader.loadLibrary(libName);
    }
  }
    
  public long maxMemory() {
    return VM_Runtime.maxMemory();
  }

  public boolean removeShutdownHook(Thread hook) throws IllegalStateException,
                                                        SecurityException {
    throw new VM_UnimplementedError();
  }

  public void runFinalization() {
    synchronized (FinalizerThread.marker) {}
  }
    
  public static void runFinalizersOnExit(boolean run) throws SecurityException {
    SecurityManager smngr = System.getSecurityManager();
    if (smngr != null)
      smngr.checkExit(0);
    throw new VM_UnimplementedError();
  }
  
  public long totalMemory() {
    return VM_Runtime.totalMemory();
  }

  public void traceInstructions(boolean enable) {
    // VMs are free to ignore this...
  }

  public void traceMethodCalls(boolean enable) {
    // VMs are free to ignore this...
  }

  private static final String LIB_SUFFIX = (VM.BuildForLinux) ? ".so" :  ((VM.BuildForOsx) ? ".jnilib" : ".a");

  static String nativeGetLibname(String pathname, String libname) {
    if (pathname != null && !("".equals(pathname)))
      return pathname + File.separator + "lib" + libname + LIB_SUFFIX;
    else
      return "lib" + libname + LIB_SUFFIX;
  }
}
