/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2002, 2003, 2004
 */
//$Id$
package org.vmmagic.pragma; 

import com.ibm.JikesRVM.classloader.*;

/**
 * A pragma that can be used to declare that a 
 * particular method is interruptible.  
 * Used to override the class-wide pragma
 * implied by implementing {@link Uninterruptible}.
 * 
 * @author Dave Grove
 */
public class InterruptiblePragma extends PragmaException {
  private static final VM_TypeReference me = getTypeRef("Lorg/vmmagic/pragma/InterruptiblePragma;");
  public static boolean declaredBy(VM_Method method) {
    return declaredBy(me, method);
  }
}
