/*
 * (C) Copyright IBM Corp 2001,2002
 */
//$Id$

package com.ibm.JikesRVM.OSR;
import com.ibm.JikesRVM.*;
/**
 * OSR_ObjectHolder helps the specialized prologue to load reference
 * get around of GC problem
 *
 * @author Feng Qian
 */

public class OSR_ObjectHolder implements VM_Uninterruptible {

  // initialize pool size
  private final static int POOLSIZE = 8;

  private static Object[][] refs; 

  public static void boot(){
    refs = new Object[POOLSIZE][];
    
    // exercise the method to avoid lazy compilation in the future
    Object[] objs = new Object[1];
    int p = handinRefs(objs);
    getRefAt(p,0);
    cleanRefs(p);
	
	if (VM.TraceOnStackReplacement) {
	  VM.sysWriteln("OSR_ObjectHolder booted...");
	}
  }

  /**
   * The JVM scope descriptor extractor can hand in an object here
   */
  public final static int handinRefs(Object[] objs) {    
    int n = refs.length;
    for (int i=0; i<n; i++) {
      if (refs[i] == null) {
	refs[i] = objs;
	return i;
      }
    }
    // grow the array
    Object[][] newRefs = new Object[2*n][];
    System.arraycopy(refs, 0, newRefs, 0, n);
    newRefs[n] = objs;
    refs = newRefs;
	
    return n;
  }

  /**
   * Get the object handed in before, only called by specialized code.
   */ 
  public final static Object getRefAt(int h, int i) 
    throws VM_PragmaInline {
	
	if (VM.TraceOnStackReplacement) {
	  VM.sysWriteln("OSR_ObjectHolder getRefAt");
	}
	Object obj = refs[h][i];
    return obj;
  }

  /**
   *  Clean objects. This method is called by specialized bytecode prologue
   */
  public final static void cleanRefs(int i) 
    throws VM_PragmaInline {
	if (VM.TraceOnStackReplacement) {
	  VM.sysWriteln("OSR_ObjectHolder cleanRefs");
	}
    refs[i] = null;
  }

}
