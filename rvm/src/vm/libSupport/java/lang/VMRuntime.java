/*
 * (C) Copyright IBM Corp 2003
 */
//$Id$
package java.lang;

import java.io.File;
import java.util.Properties;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.classloader.VM_ClassLoader;
import com.ibm.JikesRVM.classloader.VM_SystemClassLoader;
import com.ibm.JikesRVM.memoryManagers.vmInterface.*;

/**
 * Jikes RVM implementation of GNU Classpath's java.lang.VMRuntime.
 * See reference implementation for javadoc.
 *
 * NOTE: Only some of these methods are actually used with classpath 0.07
 *       because we have out own copy of java.lang.Runtime.
 *       Once classpath 0.08 comes out, we can delete our implementation of
 *       java.lang.Runtime and use the classpath version + this class.
 *       
 * @author Julian Dolby
 * @author Dave Grove
 */
final class VMRuntime {

  private VMRuntime() { }

  static int availableProcessors() {
    throw new VM_UnimplementedError();
  }
    
  static long freeMemory() {
    return MM_Interface.freeMemory();
  }
    
  static long totalMemory() {
    return MM_Interface.totalMemory();
  }
    
  static long maxMemory() {
    return MM_Interface.maxMemory();
  }
    
  static void gc() {
    MM_Interface.gc();
  }
    
  static void runFinalization() {
    // a no-op for now
  }
    
  static void runFinalizationForExit() {
    throw new VM_UnimplementedError();
  }
    
  static void traceInstructions(boolean on) {
    // VMs are free to ignore this...
  }
    
  static void traceMethodCalls(boolean on) {
    // VMs are free to ignore this...
  }

  static void runFinalizersOnExit(boolean value) {
    throw new VM_UnimplementedError();
  }

  static void exit(int status) {
    VM.sysExit(status);
  }    

  static int nativeLoad(String filename) {
    throw new VM_UnimplementedError();
  }

  private static final String LIB_SUFFIX = (VM.BuildForLinux) ? ".so" :  ((VM.BuildForOsx) ? ".jnilib" : ".a");

  static String nativeGetLibname(String pathname, String libname) {
    if (pathname != null && !("".equals(pathname)))
      return pathname + File.separator + "lib" + libname + LIB_SUFFIX;
    else
      return "lib" + libname + LIB_SUFFIX;
  }

  static Process exec(String[] cmd, String[] env, File dir) {
    String dirPath = (dir != null) ? dir.getPath() : null;
    return new VM_Process(cmd[0], cmd, env, dirPath);
  }

  // TODO: There is a long list of properties in the
  //       GNU classpath reference implementation that we
  //       are not defining here.  We should define them all.
  //       See defect 3831.
  static void insertSystemProperties(Properties p) {
    p.put("java.version", "1.3.0"); // change to 1.4.0 ?
    p.put("java.vendor", "IBM");    // change to Jikes RVM??
    
    p.put("file.separator", "/");
    p.put("path.separator", ":");
    p.put("line.separator", "\n");
        
    p.put("java.compiler", "JikesRVM");
    p.put("java.vm.version", "1.3.0");
    p.put("java.vm.name", "JikesRVM");
    p.put("file.encoding", "8859_1");
    p.put("java.io.tmpdir", "/tmp");

    p.put("user.timezone", "America/New_York");
  }
    
}
