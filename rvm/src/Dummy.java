/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
import org.jikesrvm.compilers.common.RecompilationManager;
import org.jikesrvm.runtime.DynamicLinker;
import org.jikesrvm.runtime.MathConstants;
import org.jikesrvm.runtime.Reflection;
import org.jikesrvm.scheduler.Lock;

/**
 * Dummy class containing enough references to force java compiler
 * to find every class comprising the VM, so everything gets recompiled
 * by just compiling "Dummy.java".
 * <p>
 * The minimal set has to be discovered by trial and error. Sorry.
 */
class Dummy {
  static org.jikesrvm.VM a;
  static org.jikesrvm.classloader.TableBasedDynamicLinker b;
  static DynamicLinker c;
  static org.jikesrvm.jni.JNIFunctions d;
  static Reflection e;
  static Lock f;
  static org.vmmagic.pragma.SaveVolatile i;
  static org.jikesrvm.mm.mminterface.MemoryManager l;
  static RecompilationManager o;
  static org.jikesrvm.ArchitectureSpecific.MultianewarrayHelper r;
  static org.vmmagic.unboxed.Address s;
  static MathConstants t;
  static org.vmmagic.unboxed.WordArray x;
  static org.vmmagic.unboxed.OffsetArray y;
  static org.vmmagic.unboxed.ExtentArray z;
  static org.jikesrvm.scheduler.LightMonitor zz;
}
