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

/**
 * Provides classes that implement the transformation from bytecode to the
 * high-level intermediate representation (HIR).
 * <p>
 * {@link org.jikesrvm.compilers.opt.bc2ir.ConvertBCtoHIR} is the very first
 * compiler phase in the optimizing compiler. Its job is to call
 * {@link org.jikesrvm.compilers.opt.bc2ir.BC2IR} which generates the initial HIR.
 * BC2IR collaborates with {@link org.jikesrvm.compilers.opt.bc2ir.BBSet}
 * (for basic block management), {@link org.jikesrvm.compilers.opt.bc2ir.GenerationContext}
 * (for information about the generation process and the method
 * that is to be compiled) and {@link org.jikesrvm.compilers.opt.bc2ir.GenerateMagic}
 * (for non-machine specific magics) and one implementation of
 * GenerateMachineSpecificMagic.
 * BC2IR is also responsible for generating code to deal with on-stack-replacement.
 * <p>
 * Note that all inlining of application level methods in the optimizing compiler
 * happens in BC2IR. Inlining of runtime services (e.g. creation of new objects,
 * implementation of monitorexit and monitorenter, write barriers, ...)
 * happens during the transition from high-level IR to low-level IR.
 * <p>
 * Important classes from other packages that relate to the job of BC2IR are the
 * classes that form the IR and the classes that are related to inlining.
 * <p>
 * For debugging aids, see {@link org.jikesrvm.compilers.opt.bc2ir.IRGenOptions}
 * and the source code of BC2IR.
 * <p>
 * Please consult the User Guide and the "Recommended Reading" section on the
 * website for more information about the optimizing compiler and the intermediate
 * representations.
 *
 * @see org.jikesrvm.compilers.opt.ir.IR
 * @see org.jikesrvm.compilers.opt.inlining
 */
package org.jikesrvm.compilers.opt.bc2ir;

