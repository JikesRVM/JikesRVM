/*
 * (C) Copyright IBM Corp 2002
 */
//$Id$
package java.lang;

import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.classloader.VM_Type;
import com.ibm.JikesRVM.VM_StackBrowser;
import com.ibm.JikesRVM.classloader.VM_BootstrapClassLoader;

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
  static Class[] getClassContext() {
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
      Class[] classes = new Class[ frames ];
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
