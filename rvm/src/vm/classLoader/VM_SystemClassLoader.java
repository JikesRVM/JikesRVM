/*
 * (C) Copyright IBM Corp 2001,2002
 */
//$Id$
package com.ibm.JikesRVM;

import java.util.StringTokenizer;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Vector;
import java.util.zip.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.io.*;

/** 
 * VM_SystemClassLoader.java
 *
 * Implements an object that functions as a system class loader.
 * This class is a Singleton pattern.
 *
 * @author Bowen Alpern
 * @author Derek Lieber
 */
public final class VM_SystemClassLoader extends java.lang.ClassLoader {

  static void boot() {
      zipFileCache = new HashMap();
  }

  // prevent other classes from constructing
  private VM_SystemClassLoader() { super( null ); }

  /* Interface */
  private static VM_SystemClassLoader vmClassLoader =
      new VM_SystemClassLoader();

  public static VM_SystemClassLoader getVMClassLoader() { 
      return vmClassLoader;
  }

  static class Timer {
    private double startTime; 
    private double endTime;   

    public double elapsedTime() { return endTime - startTime; }
    
    public void start() {
      startTime = VM_Time.now();
    }

    public void finish() {
      endTime = VM_Time.now();
    }
  }


  public synchronized Class loadClass(String className, boolean resolveClass)
      throws ClassNotFoundException
  {

    Timer timer = null;
    if (VM.MeasureClassLoading()) {
      timer = new Timer();
      timer.start();
    }

    Class loadedClass = null;

    // Ask the VM to look in its cache.
    loadedClass = findLoadedClassInternal(className);

    if (loadedClass == null) loadedClass = findClass(className);

    // resolve if required
    if (resolveClass) resolveClass(loadedClass);

    if (VM.MeasureClassLoading()) {
      timer.finish();
      record(className, loadedClass, timer);
    }

    return loadedClass;
  }

  static class Report {
    final String name;
    final Class klass;
    final double time;
    Report next;
    Report(String name, Class klass, double time) {
      this.name = name;
      this.klass = klass;
      this.time = time;
    }
  }

  /** The head of the reports. */
  private Report firstReport;

  /** The current report. */
  private Report currentReport;

  /**
   * Records the time spent loading a class.
   *
   * @param className   Name of the class.
   * @param loadedClass Class that was loaded.
   * @param timer       Timer used to time loading.
   */
  private void record(String className, Class loadedClass, Timer timer) {
    if (VM.MeasureClassLoading == 1) {
      final Report report = new Report(className, loadedClass, timer.elapsedTime());
      if (firstReport == null) {
	firstReport = currentReport = report;
      } else {
	currentReport = currentReport.next = report;
      }
    } else {
      VM.sysWrite("[Loaded " + className + " in " + 
		  timer.elapsedTime() + " ms.]\n");
    }
  }

  /**
   * Search the system class loader's classpath for given class.
   *
   * @param className the name of the class to load
   * @return the class object, if it was found
   * @exception ClassNotFoundException if the class was not found, or was invalid
   */
  protected Class findClass (String className) throws ClassNotFoundException {

      // array types: recursively load element type
      if (className.startsWith("[")) {
	  Class eltClass = loadClass( className.substring(1), false );
	  VM_Type eltType = java.lang.JikesRVMSupport.getTypeForClass(eltClass);
	  VM_Atom descr = eltType.getDescriptor().arrayDescriptorFromElementDescriptor();
	  VM_Array type = (VM_Array)VM_ClassLoader.findOrCreateType(descr, this);
	  try {
	      type.load();
	  } catch (VM_ResolutionException e) {
	      throw new ClassNotFoundException( className );
	  }
	  return type.getClassForType();
      }

      // class types: try to find the class file
      else {	
	  try {	    
	      Class cls = null;

	      if (className.startsWith("L")&&className.endsWith(";"))
		  className = className.substring(1, className.length()-2);

	      // See if we can open a stream on the bytes which supposedly
	      // represent this class.
	      InputStream is = getResourceAsStream(className.replace('.','/') + ".class");

	      // Try to load the class.
	      DataInputStream dataInputStream = new DataInputStream(is);
	      try {
		  cls = VM_ClassLoader.defineClassInternal(className, dataInputStream, this);
	      }
	      finally {
		  // Make sure the input stream is closed.
		  try {
		      dataInputStream.close();
		  }
		  catch (IOException e) {
		  }
	      }
	      
	      return cls;

	  } catch (Throwable e) {
	      // We didn't find the class, or it wasn't valid, etc.
	      throw new ClassNotFoundException(className);
	  }
      }
  }
  
  /**
   * Attempts to find and return a class which has already
   * been loaded by the virtual machine. Note that the class
   * may not have been linked and the caller should call
   * resolveClass() on the result if necessary.
   *
   * @return              java.lang.Class
   *                                      the class or null.
   * @param               className String
   *                                      the name of the class to search for.
   */
  public final Class findLoadedClassInternal (String className) {
    if (VM.VerifyAssertions) VM._assert(className != null);

    VM_Atom classDescriptor;
    if (className.startsWith("[")||(className.startsWith("L")&&className.endsWith(";")))
	classDescriptor = VM_Atom.findOrCreateAsciiAtom(className.replace('.','/'));
    else
	classDescriptor = VM_Atom.findOrCreateAsciiAtom(className.replace('.','/')).descriptorFromClassName();
    // make a descriptorfrom the class name string

    // check if the type dictionary has a loaded class
    int typeId = VM_TypeDictionary.findId(classDescriptor); 
    if (typeId == -1) return null;
    VM_Type t = VM_TypeDictionary.getValue(typeId);
    if (t == null) return null;
    if (!t.isLoaded()) return null;

    // found it. return the class
    return t.getClassForType();
  }

  public String toString() { return "System ClassLoader!"; }

  /**
   * Initialize for measuring class loading.
   */
  static void initializeMeasureClassLoading() {
    if (VM.MeasureClassLoading == 1) {
      VM_Callbacks.addExitMonitor(new Reporter(getVMClassLoader()));
    }
  }

  /**
   * Used for timing the class loading.
   */
  private final static class Reporter implements VM_Callbacks.ExitMonitor {
    private final VM_SystemClassLoader cl;
    Reporter(VM_SystemClassLoader cl) { this.cl = cl; }
    public void notifyExit(int value) {
      VM.sysWrite("Class Loader Report (" + value + ")");
      for (Report p = cl.firstReport; p != null; p = p.next) {
	VM.sysWrite("\t" + p.name + 
		    "\t" + p.time +
		    "\n");
      }
    }

  }
    
  private static HashMap zipFileCache;
    
  private interface Handler {
      void process(ZipFile zf, ZipEntry ze) throws Exception;
      void process(File f) throws Exception;
      Object getResult();
  }

  public InputStream getResourceAsStream(final String name) {
      Handler findStream = new Handler() {
	      InputStream stream;

	      public Object getResult() { return stream; }

	      public void process(ZipFile zf, ZipEntry ze) throws Exception {
		  stream = zf.getInputStream( ze );
	      }

	      public void process(File file) throws Exception {
		  stream = new FileInputStream( file );
	      }
      };

      return (InputStream) getResourceInternal(name, findStream, false);
  }

  public URL findResource(final String name) {
      Handler findURL = new Handler() {
	      URL url;

	      public Object getResult() { return url; }

	      public void process(ZipFile zf, ZipEntry ze) throws Exception {
		  url = new URL("jar", null, -1, "file:" + zf.getName() + "/!" +name);
	      }

	      public void process(File file) throws Exception {
		  url = new URL("file", null, -1, file.getName());
	      }
      };

      return (URL) getResourceInternal(name, findURL, false);
  }

  public Enumeration findResources(final String name) {
      Handler findURL = new Handler() {
	      Vector urls;

	      public Object getResult() { return urls.elements(); }

	      public void process(ZipFile zf, ZipEntry ze) throws Exception {
		  urls.addElement( new URL("jar", null, -1, "file:" + zf.getName() + "/!" +name) );
	      }

	      public void process(File file) throws Exception {
		  urls.addElement( new URL("file", null, -1, file.getName()) );
	      }
      };

      return (Enumeration) getResourceInternal(name, findURL, true);
  }

  private Object getResourceInternal(String name, Handler h, boolean multiple) {
      if (name.startsWith( File.separator ) )
	  name = name.substring( File.separator.length() );

      StringTokenizer tok = new StringTokenizer(VM_ClassLoader.getVmRepositories(), File.pathSeparator);

      while (tok.hasMoreElements()) {
	  try {
	      String path = tok.nextToken();
	      if (path.endsWith(".jar") || path.endsWith(".zip")) {
		  ZipFile zf = (ZipFile) zipFileCache.get( path );
		  if (zf == null) {
		      zf = new ZipFile( path );
		      if (zf == null) 
			  continue;
		      else
			  zipFileCache.put( path, zf );
		  }
		  
		  ZipEntry ze = zf.getEntry( name );
		  if (ze == null) continue;
		
		  h.process(zf, ze);
		  if (! multiple) return h.getResult();
	      }
	      
	      else if (path.endsWith( File.separator )) {
		  File file = new File(path + name);
		  if (file.exists()) {
		      h.process( file );
		      if (! multiple) return h.getResult();
		  } else
		      continue;
	      }
	      
	      else {
		  File file = new File(path + File.separator + name);
		  if (file.exists()) {
		      h.process( file );
		      if (! multiple) return h.getResult();
		  } else
		      continue;
	      }
	  } catch (Exception e) {
	      continue;
	  }
      }

      return (multiple)? h.getResult(): null;
  }
	      
}
