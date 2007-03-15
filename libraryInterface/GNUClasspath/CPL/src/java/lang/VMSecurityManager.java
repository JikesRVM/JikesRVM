/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp 2002
 */
package java.lang;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.VM_Type;
import org.jikesrvm.VM_StackBrowser;
import org.jikesrvm.classloader.VM_BootstrapClassLoader;

/**
 * Library support interface of Jikes RVM.
 * This class is not used after GNU Classpath 0.13.  Many of its
 * methods have been transferred to VMStackWalker.  We are copying the
 * methods verbatim to there, rather than doing them by call, because of
 * the silly interface.
 *
 * @author Julian Dolby
 *
 */
final class VMSecurityManager
{
  static Class<?>[] getClassContext() {
      VM_StackBrowser b = new VM_StackBrowser();
      int frames = 0;
      VM.disableGC();

      b.init();
      b.up(); // skip this method
      b.up(); // skip the SecurityManager.getClassContext()

      while(b.hasMoreFrames()) {
          frames++;
          b.up();
      }

      VM.enableGC();
      VM_Type[] iclasses = new VM_Type[ frames ];

      int i = 0;
      b = new VM_StackBrowser();
      VM.disableGC();

      b.init();
      b.up(); // skip this method
      b.up(); // skip the SecurityManager.getClassContext()

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

  static ClassLoader currentClassLoader() {
      VM_StackBrowser b = new VM_StackBrowser();
      VM.disableGC();
      b.init();

      while(b.hasMoreFrames() && b.getClassLoader() == VM_BootstrapClassLoader.getBootstrapClassLoader())
          b.up();

      VM.enableGC();
      return b.hasMoreFrames()? b.getClassLoader(): null;
  }

}
