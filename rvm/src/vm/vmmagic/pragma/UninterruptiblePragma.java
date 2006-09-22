/*
 * This file is part of the Jikes RVM project (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2002, 2004
 */
//$Id$
package org.vmmagic.pragma; 

import com.ibm.JikesRVM.classloader.*;

/**
 * Any method that is declared capable of throwing this (pseudo-)exception
 * is treated specially by the machine code compiler:
 * (1) the normal thread switch test that would be
 *     emitted in the method prologue is omitted.
 * (2) the stack overflow test that would be emitted
 *     in the method prologue is omitted.
 *
 * <P>
 * If you want to declare most or all of the methods in a class to be 
 * <code>UninterruptiblePragma</code>, then see the 
 * {@link Uninterruptible} (pseudo-)interface.
 * <P>
 * This is the inverse of the {@link InterruptiblePragma} pseudo-exception.
 *
 * @author Chapman Flack
 */
public class UninterruptiblePragma extends PragmaException {
  private static final VM_TypeReference me = getTypeRef("Lorg/vmmagic/pragma/UninterruptiblePragma;");
  public static boolean declaredBy(VM_Method method) {
    return declaredBy(me, method);
  }
}
