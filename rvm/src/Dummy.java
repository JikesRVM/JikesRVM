/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

import com.ibm.jikesrvm.*;
import com.ibm.jikesrvm.memorymanagers.mminterface.MM_Interface;
import com.ibm.jikesrvm.ArchitectureSpecific.VM_MultianewarrayHelper;
import org.vmmagic.unboxed.*;

/**
 * Dummy class containing enough references to force java compiler
 * to find every class comprising the vm, so everything gets recompiled
 * by just compiling "Dummy.java".
 *
 * The minimal set has to be discovered by trial and error. Sorry.
 *
 * @author Derek Lieber
 */
class Dummy {
  static VM                         a;
  static com.ibm.jikesrvm.classloader.VM_TableBasedDynamicLinker b;
  static VM_DynamicLinker           c;
  static VM_Reflection              e;
  static VM_Process                 f;
  static com.ibm.jikesrvm.jni.BuildJNIFunctionTable g;
  static VM_SaveVolatile            i;
  static MM_Interface               l;
  static VM_RecompilationManager    o;
  static VM_MultianewarrayHelper    r;
  static Address                 s;
  static VM_Math                    t;
  static WordArray               x;
  static OffsetArray             y;
  static ExtentArray             z;
}
	// Test of a TAB character.
