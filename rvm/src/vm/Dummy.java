/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.memoryManagers.mmInterface.MM_Interface;
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
  static com.ibm.JikesRVM.classloader.VM_TableBasedDynamicLinker b;
  static VM_DynamicLinker           c;
  static VM_Reflection              e;
  static VM_Process                 f;
  static com.ibm.JikesRVM.jni.BuildJNIFunctionTable g;
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
