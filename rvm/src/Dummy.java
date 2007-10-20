/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
import org.jikesrvm.compilers.common.VM_RecompilationManager;
import org.jikesrvm.runtime.VM_DynamicLinker;
import org.jikesrvm.runtime.VM_Math;
import org.jikesrvm.runtime.VM_Reflection;
import org.jikesrvm.scheduler.greenthreads.VM_Process;

/**
 * Dummy class containing enough references to force java compiler
 * to find every class comprising the vm, so everything gets recompiled
 * by just compiling "Dummy.java".
 * <p/>
 * The minimal set has to be discovered by trial and error. Sorry.
 */
class Dummy {
  static org.jikesrvm.VM a;
  static org.jikesrvm.classloader.VM_TableBasedDynamicLinker b;
  static VM_DynamicLinker c;
  static org.jikesrvm.jni.VM_JNIFunctions d;
  static VM_Reflection e;
  static VM_Process f;
  static org.vmmagic.pragma.SaveVolatile i;
  static org.jikesrvm.memorymanagers.mminterface.MM_Interface l;
  static VM_RecompilationManager o;
  static org.jikesrvm.ArchitectureSpecific.VM_MultianewarrayHelper r;
  static org.vmmagic.unboxed.Address s;
  static VM_Math t;
  static org.vmmagic.unboxed.WordArray x;
  static org.vmmagic.unboxed.OffsetArray y;
  static org.vmmagic.unboxed.ExtentArray z;
}
