/*
 * (C) Copyright IBM Corp 2002
 */
//$Id$
package java.lang;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;

import java.util.Properties;

import com.ibm.JikesRVM.librarySupport.ClassLoaderSupport;
import com.ibm.JikesRVM.librarySupport.SystemSupport;
import com.ibm.JikesRVM.librarySupport.UnimplementedError;

/**
 * Library support interface of Jikes RVM
 *
 * @author Julian Dolby
 *
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
    private Runtime() {

    }

    static Properties defaultProperties;

    static {
	defaultProperties = new Properties();

	defaultProperties.put("file.separator", "/");
	defaultProperties.put("path.separator", ":");
	defaultProperties.put("line.separator", "\n");
	
	defaultProperties.put("java.compiler", "Jikes RVM");
	defaultProperties.put("java.vendor", "IBM");
	defaultProperties.put("java.version", "1.3.0");
	defaultProperties.put("java.vm.version", "1.3.0");
	defaultProperties.put("file.encoding", "8859_1");
	defaultProperties.put("java.io.tmpdir", "/tmp");

	defaultProperties.put("user.timezone", "America/New_York");
    }

    public Process exec(String[] progArray) throws java.io.IOException{
	return exec(progArray, null, null);
    }

    public Process exec(String[] progArray, String[] envp) throws java.io.IOException {
	return exec(progArray, envp, null);
    }

    public Process exec(String command, String[] envp, java.io.File dir) throws java.io.IOException {
	return exec(new String[]{command}, envp, dir);
    }

    public Process exec(String prog) throws java.io.IOException{
	return exec(prog, null);
    }
    
    public Process exec(String prog, String[] envp) throws java.io.IOException{
	//use a regular StringTokenizer to break the command_line into
	//small peaces. By convention the first argument is the name of the
	//command.
	int argsLenghPlusOne;
	int i=0;

	java.util.StringTokenizer slicer = new java.util.StringTokenizer(prog);

	String[] command = new String[argsLenghPlusOne=slicer.countTokens()];

	while (i<argsLenghPlusOne)
	    command[i++] = slicer.nextToken();

	return exec(command, envp);

    }

    public Process exec(String[] progArray, String[] envp, java.io.File dir) throws java.io.IOException {
	if (progArray != null && progArray.length > 0 && progArray[0] != null) {
	    SecurityManager smngr = System.getSecurityManager();
	    if (smngr != null)
		smngr.checkExec(progArray[0]);
	    return SystemSupport.createProcess(progArray[0], progArray, envp, dir);
	} else 
	    throw new java.io.IOException();
    }

    public void exit (int code) {
	SecurityManager smngr = System.getSecurityManager();
	if (smngr != null)
	    smngr.checkExit(code);
        SystemSupport.sysExit(code);
    }

    public long freeMemory() {
	return SystemSupport.freeMemory();
    }

    public void gc() {
	SystemSupport.gc();
    }

    public static Runtime getRuntime() {
	return runtime;
    }

    public synchronized void load(String pathName) {
	SecurityManager smngr = System.getSecurityManager();
	if (smngr != null)
	    smngr.checkLink(pathName);
        ClassLoaderSupport.load(pathName);
    }

    public void loadLibrary(String libName) {
	ClassLoaderSupport.loadLibrary(libName);
    }

    synchronized void loadLibraryWithClassLoader(String libName, ClassLoader loader) {
	SecurityManager smngr = System.getSecurityManager();
	if (smngr != null)
	    smngr.checkLink(libName);
	UnimplementedError.unimplemented("Runtime.loadLibraryWithClassLoader"); //!!TODO
    }

    public void runFinalization() {
	SystemSupport.runFinalization();
    }
    
    public static void runFinalizersOnExit(boolean run) {
	SecurityManager smngr = System.getSecurityManager();
	if (smngr != null)
	    smngr.checkExit(0);
        UnimplementedError.unimplemented("java.lang.Runtime.runFinalizersOnExit: not implemented\n"); //!!TODO
    }
    
    public long totalMemory() {
	return SystemSupport.totalMemory();
    }

    public void traceInstructions(boolean enable) {}

    public void traceMethodCalls(boolean enable) {}

    public InputStream getLocalizedInputStream(InputStream stream) {
	return stream;
    }

    public OutputStream getLocalizedOutputStream(OutputStream stream) {
	return stream;
    }

    static String nativeGetLibname(String pathname, String libname) {
    //-#if RVM_FOR_LINUX
	return pathname + File.separator + "lib" + libname + ".so";
    //-#else
	return pathname + File.separator + "lib" + libname + ".a";
    //-#endif
    }

}
