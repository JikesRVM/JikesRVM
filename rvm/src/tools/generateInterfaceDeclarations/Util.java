/* -*-coding: iso-8859-1 -*-
 *
 * Copyright © IBM Corp 2004
 *
 * $Id$
 */

// import com.ibm.JikesRVM.classloader.VM_ClassLoader; // FILL ME IN 

import java.lang.reflect.Field;

/** Utility routines, shared by GenerateInterfaceDeclarations and
 * BootImageWriter.  
 *
 * @author Steven Augart
 * @date 11 March 2004 -- 12 March 2004
 */


public class Util {

  static int verbose = 2;

  /* Class Loading */
  public static ClassLoader altCL = null; // alternate reality class loader
  
  public static Class getClassNamed(String cname) {
    Class ret;

    if (verbose > 0)
      ep("getClassNamed(" + cname + ")");
    if (altCL == null) {
      if (verbose > 0)
      ep(" <default CL>");
      /* We're not worried about using Jikes RVM or another Classpath-based VM
       * to run GenerateInterfaceDeclarations.  The VM class is in the normal
       * CLASSPATH, as is gnu.classpath.Configuration.  */ 
      try {
        ret = Class.forName(cname);
      } catch (ClassNotFoundException e) {
        reportTrouble("Unable to load the class \"" + cname + "\""
                      + " with the default (application) class loader", e);
        return null;            // unreached
      }
    } else {
      if (verbose > 0 )
	ep(" <" + altCL + ">");

      /* using the alternate reality class loader */
      try {
        ret = Class.forName(cname, true, altCL);
      } catch (ClassNotFoundException e) {
        reportTrouble("Unable to load the class \"" + cname + "\"" 
                      + " with the Alternate Reality class loader", e);
        return null;            // unreached
      }
    }

    if (verbose > 0)
      epln();
    if (verbose > 1) {
      ClassLoader l = ret.getClassLoader();
      epln("     (actually loaded by "
	   + (l == null ? "{bootstrap}" : l.toString()) + ")");
    }
    
    return ret;
  }

  public static void setBoolField(Class c, final String fieldName, 
                                  boolean val) { 
    Field f;
    try {
      f = c.getField(fieldName);
    } catch (NoSuchFieldException e) {
      reportTrouble("Unable to find a field named " + fieldName
                    + "in " + c.toString() , e);
      f = null;                 // Unreachable
    } 
    try {
      f.setBoolean(null, val);
    } catch (IllegalAccessException e) {
      reportTrouble("Protection error while setting the boolean field named "
                    + fieldName + "in " + c, e);
    }
  }

  /* reportTrouble and its aux. data */

  /** Name of this program, for trouble reports */
  private static String preface = null; 
  
  public  static void setPreface(String preface_) {
    preface = preface_;
  }

  public static void reportTrouble(String msg) {
    reportTrouble(msg, (Exception) null);
  }

  public static void reportTrouble(String msg, Throwable e) {
    if (preface != null) {
      ep(preface);
    }
    ep(msg);
    if (e != null) {
      ep(": ");
      ep(e.toString());
    }
    epln();
    if (e != null) {
      e.printStackTrace();
    }
    printAfterword();
    System.exit(1);
  }

  /** Do-nothing method; inheritors might be able to override it. */
  static void printAfterword() {
  }


  /** System.err.print* */

  public static void ep(String s) {
    System.err.print(s);
  }

  public static void epln(String s) {
    System.err.println(s);
  }

  public static void epln() {
    System.err.println();
  }

  
}
