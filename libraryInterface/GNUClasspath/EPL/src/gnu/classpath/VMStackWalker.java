/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package gnu.classpath;

/** This is a cheap stack browser.  Better would be something like
 * the Jikes RVM {@link StackBrowser} class.
 * <p>
 * This is our interface to GNU Classpath.  We quote the official
 * Classpath Javadoc here, as part of clearly describing the interface.
 * Never the less, look at the source code of the GNU Class
 * (classpath/vm/reference/gnu/classpath/VMStackWalker.java) for the latest
 * description of what these methods should do.
 */

import org.jikesrvm.VM;
import org.jikesrvm.runtime.StackBrowser;
import org.jikesrvm.runtime.Entrypoints;

import org.jikesrvm.classloader.RVMType;


public final class VMStackWalker {

  /**
   * Walk up the stack and return the first non-{@code null} class loader.
   * If there aren't any non-{@code null} class loaders on the stack, return
   * {@code null}.
   *
   * @return the first non-{@code null} classloader on stack or {@code null}
   */
  public static ClassLoader firstNonNullClassLoader() {
    for (Class<?> type : getClassContext()) {
      ClassLoader loader = type.getClassLoader();
      if (loader != null)
        return loader;
    }
    return null;
  }

  /**
   * Classpath's Javadoc for this method says:
   * <blockquote>
   * Get a list of all the classes currently executing methods on the
   * Java stack. <code>getClassContext()[0]</code> is the class associated
   * with the currently executing method, i.e., the method that called
   * <code>VMStackWalker.getClassContext()</code> (possibly through
   * reflection). So you may need to pop off these stack frames from
   * the top of the stack:
   * <ul>
   * <li><code>VMStackWalker.getClassContext()</code>
   * <li><code>Method.invoke()</code>
   * </ul>
   *
   * @return an array of the declaring classes of each stack frame
   * </blockquote>
   */
  public static Class<?>[] getClassContext() {
    StackBrowser b = new StackBrowser();
    int frames = 0;
    VM.disableGC();

    b.init();
    b.up(); // skip VMStackWalker.getClassContext (this call)

    boolean reflected;  // Were we invoked by reflection?
    if (b.getMethod() == Entrypoints.java_lang_reflect_Method_invokeMethod){
      reflected = true;
      b.up();         // Skip Method.invoke, (if we were called by reflection)
    } else {
      reflected = false;
    }

    /* Count # of frames. */
    while(b.hasMoreFrames()) {
      frames++;
      b.up();
    }

    VM.enableGC();


    RVMType[] iclasses = new RVMType[ frames ];

    int i = 0;
    b = new StackBrowser();

    VM.disableGC();
    b.init();
    b.up(); // skip this method
    if (reflected)
      b.up();            // Skip Method.invoke if we were called by reflection

    while(b.hasMoreFrames()) {
      iclasses[i++] = b.getCurrentClass();
      b.up();
    }
    VM.enableGC();

    Class<?>[] classes = new Class[ frames ];
    for(int j = 0; j < iclasses.length; j++) {
      classes[j] = iclasses[j].getClassForType();
    }

    return classes;
  }

  /**
   * Classpath's Javadoc for this method is:
   * <blockquote>
   * Get the class associated with the method invoking the method
   * invoking this method, or <code>null</code> if the stack is not
   * that deep (e.g., invoked via JNI invocation API). This method
   * is an optimization for the expression <code>getClassContext()[1]</code>
   * and should return the same result.
   *
   * <p>
   * VM implementers are encouraged to provide a more efficient
   * version of this method.
   * </blockquote>
   */
  public static Class<?> getCallingClass() {
    return getCallingClass(1);  // Skip this method (getCallingClass())
  }

  public static Class<?> getCallingClass(int skip) {
    StackBrowser b = new StackBrowser();
    VM.disableGC();

    b.init();
    b.up(); // skip VMStackWalker.getCallingClass(int) (this call)
    while (skip-- > 0)          // Skip what the caller asked for.
      b.up();

    /* Skip Method.invoke, (if the caller was called by reflection) */
    if (b.getMethod() == Entrypoints.java_lang_reflect_Method_invokeMethod){
      b.up();
    }
    /* skip past another frame, whatever getClassContext()[0] would be. */
    if (!b.hasMoreFrames())
      return null;
    b.up();

    /* OK, we're there at getClassContext()[1] now.  Return it. */
    RVMType ret = b.getCurrentClass();
    VM.enableGC();

    return ret.getClassForType();
  }

  /**
   * Classpath's Javadoc for this method is:
   * <blockquote>
   * Get the class loader associated with the Class returned by
   * <code>getCallingClass()</code>, or <code>null</code> if no
   * such class exists or it is the boot loader. This method is an optimization
   * for the expression <code>getClassContext()[1].getClassLoader()</code>
   * and should return the same result.
   * </blockquote>
   */
  public static ClassLoader getCallingClassLoader() {
    Class<?> caller = getCallingClass(1); // skip getCallingClassLoader
    if (caller == null)
      return null;
    return caller.getClassLoader();
  }
}

