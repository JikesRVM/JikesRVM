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
package org.jikesrvm.compilers.opt;

import org.jikesrvm.classloader.VM_NormalMethod;
import static org.jikesrvm.runtime.VM_EntrypointHelper.getMethod;

/**
 * Entrypoints that are valid when the build includes the opt compiler.
 */
public interface VM_OptEntrypoints {
  VM_NormalMethod optThreadSwitchFromOsrOptMethod = getMethod("Lorg/jikesrvm/compilers/opt/VM_OptSaveVolatile;", "OPT_yieldpointFromOsrOpt", "()V");
  VM_NormalMethod optThreadSwitchFromPrologueMethod = getMethod("Lorg/jikesrvm/compilers/opt/VM_OptSaveVolatile;", "OPT_yieldpointFromPrologue", "()V");
  VM_NormalMethod optThreadSwitchFromBackedgeMethod = getMethod("Lorg/jikesrvm/compilers/opt/VM_OptSaveVolatile;", "OPT_yieldpointFromBackedge", "()V");
  VM_NormalMethod optThreadSwitchFromEpilogueMethod = getMethod("Lorg/jikesrvm/compilers/opt/VM_OptSaveVolatile;", "OPT_yieldpointFromEpilogue", "()V");
  VM_NormalMethod yieldpointFromNativePrologueMethod = getMethod("Lorg/jikesrvm/compilers/opt/VM_OptSaveVolatile;", "OPT_yieldpointFromNativePrologue", "()V");
  VM_NormalMethod yieldpointFromNativeEpilogueMethod = getMethod("Lorg/jikesrvm/compilers/opt/VM_OptSaveVolatile;", "OPT_yieldpointFromNativeEpilogue", "()V");
  VM_NormalMethod optResolveMethod = getMethod("Lorg/jikesrvm/compilers/opt/VM_OptSaveVolatile;", "OPT_resolve", "()V");
  VM_NormalMethod optNewArrayArrayMethod = getMethod("Lorg/jikesrvm/compilers/opt/VM_OptLinker;", "newArrayArray", "(I[II)Ljava/lang/Object;");
  VM_NormalMethod sysArrayCopy = getMethod("Ljava/lang/VMSystem;", "arraycopy", "(Ljava/lang/Object;ILjava/lang/Object;II)V", false);
}
