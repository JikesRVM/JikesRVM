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
 * Provides classes that implement the transition from low-level IR to
 * machine-specific IR.
 * <p>
 * After the transition to machine-specific IR, almost all remaining
 * operators can be directly mapped to machine instructions. The actual
 * transition is done by
 * {@link org.jikesrvm.compilers.opt.lir2mir.ConvertLIRtoMIR ConvertLIRtoMIR}
 * which calls all the other classes. Some of these classes are
 * necessarily architecture-specific which is why they can be found in
 * the architecture-specific lir2mir packages.
 * <p>
 * A large part of the work of mapping of LIR instruction sequences to
 * MIR instruction sequences is done by the BURS classes.
 * We're using a
 * custom fork of <a href="https://code.google.com/p/iburg/">iburg</a>
 * that we call jburg. (NOTE: our jburg is not to be confused with
 * <a href="http://sourceforge.net/projects/jburg/">JBurg</a>).
 * You can find it in our source tree.
 * It is advised to take a look at it if you want to understand the BURS code.
 * You should also get familiar with the generation of BURS classes in the
 * build process.
 */
package org.jikesrvm.compilers.opt.lir2mir;
