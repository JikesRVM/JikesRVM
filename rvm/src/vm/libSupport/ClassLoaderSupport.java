/*
 * (C) Copyright IBM Corp 2001,2002
 */
//$Id$
package com.ibm.JikesRVM.librarySupport;

import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_StackBrowser;
import com.ibm.JikesRVM.VM_UnimplementedError;
import com.ibm.JikesRVM.classloader.VM_Atom;
import com.ibm.JikesRVM.classloader.VM_Type;
import com.ibm.JikesRVM.classloader.VM_Array;
import com.ibm.JikesRVM.classloader.VM_Class;
import com.ibm.JikesRVM.classloader.VM_ClassLoader;
import com.ibm.JikesRVM.classloader.VM_ResolutionException;
import com.ibm.JikesRVM.classloader.VM_SystemClassLoader;
import java.security.ProtectionDomain;

/**
 * This class provides a set of static method entrypoints used in the
 * implementation of standard library classloader operations.
 *
 * @author Dick Attanasio 
 * @author Stephen Fink
 * @author Steve Smith
 */
public class ClassLoaderSupport {

  /**
   * Returns the context ClassLoader for the receiver.
   * TODO: implement this correctly
   *
   * @return		ClassLoader		The context ClassLoader
   */
  public static ClassLoader getContextClassLoader() {
    return VM_SystemClassLoader.getVMClassLoader();
  }

  /**
   * Get list of places currently being searched for application 
   * classes and resources.
   * @return names of directories, .zip files, and .jar files
   */ 
  public static String getApplicationRepositories() {
    return VM_ClassLoader.getApplicationRepositories();
  }

  /**
   * Loads and links the library specified by the argument.
   *
   * @param		pathName		the absolute (ie: platform dependent)
   *								path to the library to load
   *
   * @exception	UnsatisfiedLinkError if the library could not be loaded
   * @exception	SecurityException    if the library was not allowed to be loaded
   */
  public static void load(String pathName) {
    VM_ClassLoader.load(pathName);
  }

  /**
   * Loads and links the library specified by the argument.
   *
   * @param		libName		the name of the library to load
   *
   * @exception	UnsatisfiedLinkError	if the library could not be loaded
   * @exception	SecurityException       if the library was not allowed to be loaded
   */
  public static void loadLibrary(String libName) {
    VM_ClassLoader.loadLibrary(libName);
  }
  /**
   * Answers the classloader which was used to load the
   * class C. Answer null if the
   * class was loaded by the system class loader
   *
   * @param C the class in question
   * @return C's class loader or nil
   *
   * @see	java.lang.ClassLoader
   */
  public static ClassLoader getClassLoader(Class C) {
      return java.lang.JikesRVMSupport.getTypeForClass(C).asClass().getClassLoader();
  }
  /**
   * Constructs a new class from an array of bytes containing a
   * class definition in class file format and assigns the new
   * class to the specified protection domain.
   *
   * @param 		className java.lang.String the name of the new class.
   * @param 		classRep byte[] a memory image of a class file.
   * @param 		offset int the offset into the classRep.
   * @param 		length int the length of the class file.
   * @param 		protectionDomain the protection domain this class should
   *					belongs to.
   */
  public static Class defineClass(ClassLoader cl, String className, byte[] classRep, 
                                  int offset, int length, ProtectionDomain protectionDomain)
    throws java.lang.ClassFormatError
    {
      return VM_ClassLoader.defineClassInternal(className,
                                                classRep,
                                                offset,
                                                length,
						cl,
						protectionDomain);
    }
  /**
   * Constructs a new class from an array of bytes containing a
   * class definition in class file format.
   *
   * @author		SES, CRA
   * @version		initial: calls static method of VM_ClassLoader
   *
   * @param 		className the name of the new class
   * @param 		classRep a memory image of a class file
   * @param 		offset the offset into the classRep
   * @param 		length the length of the class file
   */
  public static Class defineClass(ClassLoader cl, String className, byte[] classRep, 
                                  int offset, int length) throws ClassFormatError {
    return VM_ClassLoader.defineClassInternal(className, classRep, offset, length, cl);
  }


    public static Class loadArrayType(ClassLoader cl, String className, boolean resolveClass) throws ClassNotFoundException {

	VM_Atom d = VM_Atom.findOrCreateAsciiAtom(className.replace('.','/'));
	VM_Array cls = (VM_Array)VM_ClassLoader.findOrCreateType(d, cl);

	if (! cls.getElementType().isPrimitiveType()) {
	    Class k = cl.loadClass(cls.getElementType().getName());
	    if (resolveClass) try {
		VM_Type x = java.lang.JikesRVMSupport.getTypeForClass(k);
		x.resolve();
		x.instantiate();
		x.initialize();
	    } catch (VM_ResolutionException e) {
		throw new ClassNotFoundException( k.toString() );
	    }
	}
	
	try {
	    cls.load();
	} catch (VM_ResolutionException e) {
	    throw new ClassNotFoundException( className );
	}

	return cls.getClassForType();
    }

  /**
   * Forces a class to be linked (initialized).  If the class has
   * already been linked this operation has no effect.
   *
   * @param		clazz Class to link.
   * @exception	NullPointerException if clazz is null.
   *
   * @see			Class#getResource
   */
  public static void resolveClass(ClassLoader cl, Class clazz) {
    VM_ClassLoader.resolveClassInternal(clazz);
  }

  /**
   * Attempts to load a class using the system class loader.
   * Note that the class has already been been linked.
   *
   * @return 		java.lang.Class the class which was loaded.
   * @param 		className String the name of the class to search for.
   * @exception	ClassNotFoundException if the class can not be found.
   *
   * FOR NOW: call VM_Class.forName() - since there is only 1 namespace
   * EVENTUALLY: call VM_SystemClassLoader.findSystemClass : CRA 8/4/00
   *             
   */
  public static Class findSystemClass(String className) throws ClassNotFoundException {
    try { 
      return VM_Class.forName(className).getClassForType();
    }
    catch (VM_ResolutionException e) {
      throw new ClassNotFoundException(className + " not found ");
    }
  }

  /**
   * Attempts to find and return a class which has already
   * been loaded by the virtual machine. Note that the class
   * may not have been linked and the caller should call
   * resolveClass() on the result if necessary.
   *
   * @return 		java.lang.Class
   *					the class or null.
   * @param 		className String
   *					the name of the class to search for.
   */
    public static Class findLoadedClass(ClassLoader cl, String className) {
	// for now, just one name space
	return VM_SystemClassLoader.getVMClassLoader().findLoadedClassInternal(className);
    }


  /**
   * Returns the system class loader.  This is the parent
   * for new ClassLoader instances, and is typically the
   * class loader used to start the application.
   *
   * If a security manager is present, and the caller's
   * class loader is not null and the caller's class loader
   * is not the same as or an ancestor of the system class loader,
   * then this method calls the security manager's checkPermission
   * method with a RuntimePermission("getClassLoader") permission
   * to ensure it's ok to access the system class loader.
   * If not, a SecurityException will be thrown.
   *
   * @return 		the system classLoader.
   * @exception	SecurityException
   *					if a security manager exists and it does not
   *					allow access to the system class loader.
   */
    public static ClassLoader getSystemClassLoader () {
	return VM_SystemClassLoader.getVMClassLoader();
    }
    
    public static ClassLoader getClassLoaderFromStackFrame(int depth) {
	return VM_Class.getClassLoaderFromStackFrame(depth+1);
    }

    public static ClassLoader getNonSystemClassLoader() {
	ClassLoader cl = null;
	VM_StackBrowser sb = new VM_StackBrowser();
	VM.disableGC();
	sb.init();
	while ((cl=sb.getCurrentClass().getClassLoader())==VM_SystemClassLoader.getVMClassLoader() && sb.hasMoreFrames())
	    sb.up();
	VM.enableGC();
	if (cl!=VM_SystemClassLoader.getVMClassLoader())
	    return cl;
	else
	    return null;
    }
}
