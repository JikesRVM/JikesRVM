/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */

/**
 * Dummy class containing enough references to force java compiler
 * to find every class comprising the vm, so everything gets recompiled
 * by just compiling "Dummy.java".
 * <p/>
 * The minimal set has to be discovered by trial and error. Sorry.
 *
 * @author Derek Lieber
 */
class Dummy {
  static org.jikesrvm.VM a;
  static org.jikesrvm.classloader.VM_TableBasedDynamicLinker b;
  static org.jikesrvm.VM_DynamicLinker c;
  static org.jikesrvm.VM_Reflection e;
  static org.jikesrvm.VM_Process f;
  static org.jikesrvm.jni.BuildJNIFunctionTable g;
  static org.vmmagic.pragma.SaveVolatile i;
  static org.jikesrvm.memorymanagers.mminterface.MM_Interface l;
  static org.jikesrvm.VM_RecompilationManager o;
  static org.jikesrvm.ArchitectureSpecific.VM_MultianewarrayHelper r;
  static org.vmmagic.unboxed.Address s;
  static org.jikesrvm.VM_Math t;
  static org.vmmagic.unboxed.WordArray x;
  static org.vmmagic.unboxed.OffsetArray y;
  static org.vmmagic.unboxed.ExtentArray z;
}
