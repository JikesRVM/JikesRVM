/*
 * (C) Copyright IBM Corp. 2002, 2005
 */
//$Id$
package org.vmmagic.pragma; 

import com.ibm.JikesRVM.classloader.*;
import java.lang.UnsupportedOperationException;

/**
 * Base class for dummy unchecked exceptions that will be used in method
 * throws clauses to associate method-granular properties/pragmas with methods.
 * 
 * @author Chapman Flack
 */
public abstract class PragmaException extends java.lang.RuntimeException {

  /**
   * Is the class represented by <CODE>eclass</CODE> in the throws clause of
   * <CODE>method</CODE>?
   * <P>
   * This method factors out the work common to
   * {@link #declaredBy(VM_Method)}, which must be implemented in each
   * extending class. Each class <CODE>foo</CODE> extending this class must
   * provide:
   * <PRE>
   * public static boolean {@link #declaredBy(VM_Method) declaredBy}(VM_Method m) {
   *   return declaredBy( myVMClass, m);
   * }
   * </PRE>
   * @param eclass A VM_TypeReference object representing any subclass of this class.
   * In the current implementation nothing bad happens if <CODE>eclass</CODE>
   * represents some other {@link java.lang.Throwable Throwable} (you'll find
   * out if the method can throw it), or even a non-Throwable object (you'll
   * find out that the method can't throw it) but this method isn't intended
   * for those purposes so that behavior is not defined.
   * @param method VM_Method object representing any method.
   * @return true iff <CODE>eclass</CODE> represents a Throwable declared in the
   * throws clause of the method represented by <CODE>method</CODE>.
   */
  protected final static boolean declaredBy(VM_TypeReference eclass, VM_Method method) {
    VM_TypeReference[] exceptions = method.getExceptionTypes();
    if (exceptions != null) {
      for (int i = 0; i<exceptions.length; i++) {
        VM_TypeReference e = exceptions[i];
        if (!e.isResolved()) {
          if (e.getName().equals(eclass.getName())) {
            try {
              e.resolve();
            } catch (NoClassDefFoundError e1) {
              // if we can't resolve it without error, then it doesn't match
            } catch (IllegalArgumentException e2) {
              // if we can't resolve it without error, then it doesn't match
            }
          }
        }
        if (exceptions[i].definitelySame(eclass)) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * @param name the descriptor of an exception type used as a pragma
   * @return the VM_TypeReference for said descriptor
   */
  protected final static VM_TypeReference getTypeRef(String name) {
    return VM_TypeReference.findOrCreate(VM_BootstrapClassLoader.getBootstrapClassLoader(),
                                         VM_Atom.findOrCreateAsciiAtom(name));
  }


  /**
   * Is this class declared in the throws clause of <CODE>method</CODE>?
   * <P>
   * Every extending class must override this, simply as a stub that calls
   * {@link #declaredBy(VM_TypeReference,VM_Method)} passing its own VM_Class
   * static. If this method is called directly, or on a subclass that has
   * neglected to override it, {@link UnsupportedOperationException}
   * is thrown (all because the JLS prohibited abstract static methods).
   * @param method VM_Method object representing any method.
   * @return true iff this class is declared in the
   * throws clause of the method represented by <CODE>method</CODE>.
   */
  public static boolean declaredBy(VM_Method method) {
    throw new UnsupportedOperationException("Subclass of PragmaException must implement declaredBy()");
  }
}
