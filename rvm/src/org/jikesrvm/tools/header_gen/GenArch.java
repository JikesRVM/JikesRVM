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
package org.jikesrvm.tools.header_gen;

/**
 * Emit the architecture-specific part of a header file containing declarations
 * required to access VM data structures from C++.
 * Posix version: AIX PPC, Linux PPC, Linux IA32
 */
abstract class GenArch extends GenerateInterfaceDeclarations {
  abstract void emitArchVirtualMachineDeclarations();

  abstract void emitArchAssemblerDeclarations();
}
