/*
 * (C) Copyright IBM Corp. 2002
 */
//$Id$
package com.ibm.JikesRVM;

import com.ibm.JikesRVM.classloader.*;
import java.lang.UnsupportedOperationException;

/**
 * Base class for dummy unchecked exceptions that will be used in method
 * throws clauses to associate method-granular properties/pragmas with methods.
 * 
 * @author Chapman Flack
 */
public abstract class VM_PragmaException extends java.lang.RuntimeException {
  /**
   * Is the class represented by <CODE>eclass</CODE> in the throws clause of
   * <CODE>method</CODE>?
   * <P>
   * This method factors out the work common to
   * {@link #declaredBy(VM_Method)}, which must be implemented in each
   * extending class. Each class <CODE>foo</CODE> extending this class must
   * provide:
   * <PRE>
   * private static final VM_Class myVMClass = getVMClass(foo.class);
   * public static boolean {@link #declaredBy(VM_Method) declaredBy}(VM_Method m) {
   *   return declaredBy( myVMClass, m);
   * }
   * </PRE>
   * @param eclass A VM_Class object representing any subclass of this class.
   * In the current implementation nothing bad happens if <CODE>eclass</CODE>
   * represents some other {@link java.lang.Throwable Throwable} (you'll find
   * out if the method can throw it), or even a non-Throwable object (you'll
   * find out that the method can't throw it) but this method isn't intended
   * for those purposes so that behavior is not defined.
   * @param method VM_Method object representing any method.
   * @return true iff <CODE>eclass</CODE> represents a Throwable declared in the
   * throws clause of the method represented by <CODE>method</CODE>.
   */
  protected final static boolean declaredBy(VM_Class eclass, VM_Method method) {
    VM_Type[] exceptions = method.getExceptionTypes();
    if (exceptions != null) {
      for (int i = 0 ; i < exceptions.length ; ++ i) {
        if (exceptions [ i ] == eclass) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Get the {@link VM_Class} object corresponding to a
   * {@link java.lang.Class Class} object. Cannot use the <CODE>getVMType</CODE>
   * method added to the <CODE>java.lang.Class</CODE> in <CODE>rvmrt.jar</CODE>
   * because it isn't there yet at boot-image-writing time, when these pragma
   * tests can expect to be heavily used. So, every extending class should have
   * a <CODE>private static final VM_Class</CODE> field that it initializes by
   * passing its class literal to this method. The contents of that field can
   * then be passed by that class's {@link #declaredBy(VM_Method) declaredBy}
   * method to the general {@link #declaredBy(VM_Class,VM_Method) declaredBy}
   * method above.
   * <P>
   * The extra static field winds up being redundant as the <CODE>rvmrt</CODE>
   * version of {@link java.lang.Class Class} already has an equivalent field,
   * but there won't be enough of these pragma classes for that waste to be
   * significant. In using a <CODE>static final</CODE> field, though, there is
   * an <STRONG>assumption</STRONG> that a {@link VM_Class} object found to
   * correspond to a class at image-writing time will be at run time a valid
   * {@link VM_Class} object and still the right one corresponding to that
   * class.
   * <P>
   * Can't just use {@link VM_Class#forName(java.lang.String) VM_Class.forName}
   * because it tries to load, resolve, instantiate, and initialize the class,
   * and instantiation can involve compiling, which can bring us back here to
   * (oops) check for pragmas. So all we try to get here is the VM_Class
   * object in its earliest state. We only need the reference to compare to
   * entries in an exceptions array, anyway. This then unavoidably duplicates
   * a smidge of code from
   * {@link VM_Class#forName(java.lang.String) VM_Class.forName}. But that
   * original code incorrectly assumes class names are restricted to ASCII.
   * Corrected here. (That doesn't mean arbitrary class names will work yet
   * in JikesRVM, only that <EM>this</EM> code won't have to be fixed to
   * support them.)
   * <P>
   * Oddly, using a class literal in the extending class doesn't lead to the
   * same regress, even though class literals are implemented as initializing
   * {@link java.lang.Class#forName(java.lang.String) forName} calls. I suspect
   * that is because they are resolved in the host VM (which doesn't care
   * about our pragmas) at image-writing time, and do not need to be resolved
   * again at rvm startup as long as the classes are in the bootimage.
   *
   * @param eclass a {@link java.lang.Class Class} object
   * @return The corresponding {@link VM_Class} object
   */
  protected final static VM_Class getVMClass(java.lang.Class eclass) {
    VM_Atom classDescriptor = VM_Atom.findOrCreateUnicodeAtom(eclass.getName().replace( '.', '/')).descriptorFromClassName();
    return VM_ClassLoader.findOrCreateType(classDescriptor,VM_SystemClassLoader.getVMClassLoader()).asClass();
  }

  /**
   * Is this class declared in the throws clause of <CODE>method</CODE>?
   * <P>
   * Every extending class must override this, simply as a stub that calls
   * {@link #declaredBy(VM_Class,VM_Method)} passing its own VM_Class
   * static. If this method is called directly, or on a subclass that has
   * neglected to override it, {@link UnsupportedOperationException}
   * is thrown (all because the JLS prohibited abstract static methods).
   * @param method VM_Method object representing any method.
   * @return true iff this class is declared in the
   * throws clause of the method represented by <CODE>method</CODE>.
   */
  public static boolean declaredBy(VM_Method method) {
    throw new UnsupportedOperationException("Subclass of VM_PragmaException must implement declaredBy()");
  }
}
