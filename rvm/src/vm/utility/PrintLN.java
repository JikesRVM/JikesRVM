/*
 * (C) Copyright IBM Corp. 2003
 */
//$Id$
package com.ibm.JikesRVM;
import com.ibm.JikesRVM.PrintContainer;	/* This import statement isn't
				     necessary, but is here for documentation
				     purposes. --S. Augart */ 
import com.ibm.JikesRVM.classloader.VM_Member;
import com.ibm.JikesRVM.classloader.VM_Atom;
import com.ibm.JikesRVM.classloader.VM_Class;

import java.io.PrintWriter;
import java.io.PrintStream;

/**
 * This interface is implemented by com.ibm.JikesRVM.PrintContainer.  The
 * interfaces is used by our java.lang.Throwable to print stack traces.
 *
 * @author Steven Augart (w/ brainstorming by David Grove)
 */
public abstract class PrintLN {
  //  PrintLN(PrintWriter out);
  //  PrintLN(PrintStream out);
  public abstract boolean isVMSysWriteln();
  public abstract boolean isSystemErr();
  public abstract void flush();
  
  public abstract void println();
  public abstract void print(String s);
  public abstract void println(String s);
  public abstract void print(int i);
  public abstract void printHex(int i);
  public abstract void print(char c);
  /* Code related to VM_Atom.classNameFromDescriptor() */
  public void print(VM_Class class_) {

    // getDescriptor() does no allocation.
    VM_Atom descriptor = class_.getDescriptor(); 
    byte[] val = descriptor.toByteArray();

    if (VM.VerifyAssertions) 
      VM._assert(val[0] == 'L' && val[val.length-1] == ';'); 
    for (int i = 1; i < val.length - 1; ++i) {
      char c = (char) val[i];
      if (c == '/')
	print('.');
      else
	print(c);
    }
    // We could do this in an emergency.  But we don't need to.
    // print(descriptor);
  }

    // A kludgy alternative:
//     public void print(VM_Class c) {
//       VM_Atom descriptor = c.getDescriptor();
//       try {
// 	print(descriptor.classNameFromDescriptor());
//       } catch(OutOfMemoryError e) {
// 	print(descriptor);
//       }
//     }

    // No such method:
    //public void print(VM_Class c) {
    //      VM.sysWrite(c);
    //    }


  public abstract void print(VM_Member m);
  public abstract void print(VM_Atom a);
}

