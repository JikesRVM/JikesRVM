/* -*-coding: iso-8859-1 -*-
 *
 * Copyright © IBM Corp 2001, 2002, 2003, 2004
 *
 * $Id$
 */
//$Id$

// package com.ibm.JikesRVM.GenerateInterfaceDeclarations;


import  java.io.*;
import  java.io.PrintStream;
import  java.util.*;
import java.lang.reflect.*;
import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.classloader.*;
// import com.ibm.JikesRVM.classloader.AlternateRealityClassLoader;

/**
 * Emit a header file containing declarations required to access VM 
 * data structures from C++.
 *
 * This code uses a lot of reflection because we have to deal with an
 * alternate reality when we're self-booting.  If we were running the old
 * non-reflection version on Jikes RVM, then we would print out the boot image
 * declarations for the running VM, not for the one being built.  So we have
 * to go through a lot of malarkey to load these for VM under construction.
 * 
 * Posix version: AIX PPC, Linux PPC, Linux IA32, Darwin (Mac OS X)
 *
 * @author Derek Lieber
 * @modified Steven Augart -- added the "-out" command-line argument.
 * @modified Steven Augart 2/2004, 3/2004: self-hosting
 */
class GenerateInterfaceDeclarations extends Shared {

  /** Never instantiate. */
  private GenerateInterfaceDeclarations() {
  }

  static int bootImageAddress = 0;

  /** We don't just use the -alternateRealityClasspath argument when
     self-hosting (with Jikes RVM as the VM running this program). 

     Consider, for example, building a Jikes RVM using classpath 0.07 with an
     older Kaffe running classpath 0.06.  We want the GNU Classpath version to
     be written out as 0.07, not as 0.06.  */
  static String alternateRealityClasspath;

  /** If we use the -alternateRealityNativeLibDir argument, then we might want
     to assume that the user is using Jikes RVM to perform self-booting.   (We
     don't need such an assumption, though.) */
  static String alternateRealityNativeLibDir;

  public static void main (String args[]) throws Exception {
    // Process command line directives.
    //
    for (int i = 0, n = args.length; i < n; ++i) {

      /* Verbosity! */
      if (args[i].equals("-v")){
        ++verbose;
        continue;
      }

      if (args[i].equals("-ia")) {              // image address
        if (++i == args.length) {
          epln("Error: The -ia flag requires an argument");
          System.exit(-1);
        }
        bootImageAddress = Integer.decode(args[i]).intValue();
        continue;
      }

      if (args[i].equals("-out")) {              // output file
        if (++i == args.length) {
          epln("Error: The -out flag requires an argument");
          System.exit(-1);
        }
        outFileName = args[i];
        continue;
      }

      if (args[i].equals("-alternateRealityClasspath")) {
        if (++i == args.length) {
          epln("Error: The -alternateRealityClasspath flag requires an argument");
          System.exit(-1);
        }
        alternateRealityClasspath = args[i];
        continue;
      }

      if (args[i].equals("-alternateRealityNativeLibDir")) {
        if (++i == args.length) {
          epln("Error: The -alternateRealityNativeLibDir flag requires an argument");
          System.exit(-1);
        }
        alternateRealityNativeLibDir = args[i];
        continue;
      }
      epln("Error: unrecognized command line argument: " + args[i]);
      System.exit(-1);
    }

    if (bootImageAddress == 0) {
      epln("Error: Must specify boot image load address.");
      System.exit(-1);
    }

    if (verbose > 0 ) {
      epln("Verbosity level: " + verbose);
    }

    /* 
     * There is an alternate reality class loader built into Jikes RVM.  We
     * can use that.
     *
     * EXCEPT that we cannot use it in the boot image.  So perhaps it is best
     * that we just write a general one.
     *
     * TODO: Include one in these sources? */
    if (verbose > 0)
      epln("**setting Shared.altCL...");
    
    if (alternateRealityClasspath == null 
        && alternateRealityNativeLibDir == null) {
      if (verbose > 0)
        epln("   No alternate reality classpath; use system CL for altCL");
      Shared.altCL = ClassLoader.getSystemClassLoader();
    } else {
      Shared.altCL 
        = AlternateRealityClassLoader.init(alternateRealityClasspath, 
                                           alternateRealityNativeLibDir);
    }
    if (verbose > 0)
      epln("**...set Shared.altCL, to: " + Shared.altCL);

    /* Load and initialize the VM class first.  That way we don't start
     * generating an output file until we're on the way towards booting. */

    if (verbose > 0)
      epln("**Calling initializeVM()...");
    Class vmClass = initializeVM();
    if (verbose > 0)
      epln("...called initializeVM()");

    if (outFileName == null) {
      out = System.out;
    } else {
      try {
        out = new PrintStream(new FileOutputStream(outFileName));
      } catch (IOException e) {
        reportTrouble("Caught an exception while opening" + outFileName +" for writing: " + e.toString());
      }
    }

    if (verbose > 0)
      epln("**Calling Emitters.emitStuff()...");
    new Emitters(vmClass).emitStuff(bootImageAddress);
    if (verbose > 0)
      epln("...called Emitters.emitStuff()");

    if (out.checkError()) {
      reportTrouble("an output error happened");
    }
    if (out != System.out) {
      out.close();
    }
    System.exit(0);
  }

    
  static Class initializeVM() 
    throws InvocationTargetException, IllegalAccessException
  {
    if (verbose > 0)
      epln("Entering initializeVM()...");
    
    Class vmClass = getClassNamed("com.ibm.JikesRVM.VM");

    if (verbose > 0) {
      ep("  Enabling verboseClassLoading...");
      setVerboseClassLoading(vmClass, true);
      epln("verbose class loading enabled.");
    
      ep("  Enabling TraceClassLoading...");
      setTraceClassLoading(vmClass, true);
      epln("trace class loading enabled.");
    }
    

    //-#if DebugForAlternateReality
//     epln("initializeVM(): The VM Class's class loader is: "
//          + vmClass.getClassLoader());
    //-#endif

    Method initVMForTool = null; // really dumb, but shuts up warnings.
    try {
      initVMForTool = vmClass.getMethod("initForTool", new Class[0]);
    } catch (NoSuchMethodException e) {
      reportTrouble("The VM Class doesn't have a zero-parameter initForTool() method?!: " + e.toString());
      // unreached
    }

    if (verbose > 1)
      ep("Calling initVMForTool...");

    initVMForTool.invoke(vmClass, new Object[0]);

    if (verbose > 1)
      epln("CALLED initVMForTool()");

    if (verbose > 0)
      epln("...completing initializeVM()");
    return vmClass;
  }

  /** VM_Options.TraceClassLoading is more verbose than -verbose:class */
  static void setTraceClassLoading(Class vmClass, boolean val) {
    final String fieldName = "TraceClassLoading";
    setBoolField(vmClass, fieldName, val);
  }
    
  /** This handles -verbose:class */
  static void setVerboseClassLoading(Class vmClass, boolean val) {
    final String fieldName = "verboseClassLoading";
    
    setBoolField(vmClass, fieldName, val);
    
//     Field f;
//     try {
//       f = vmClass.getField(fieldName);
//     } catch (NoSuchFieldException e) {
//       reportTrouble("Unable to find a field named " + fieldName
//                     + "in " + vmClass.toString() , e);
//     }
//     f.setBoolean(null, val);
  }
}
