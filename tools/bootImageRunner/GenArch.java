/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp 2001,2002, 2003
 */

/**
 * Emit the architecture-specific part of a header file containing declarations
 * required to access VM data structures from C++.
 * Posix version: AIX PPC, Linux PPC, Linux IA32
 *
 * @author Derek Lieber
 * @modified Steven Augart -- added the "-out" command-line argument.
 */
abstract class GenArch extends GenerateInterfaceDeclarations {
  abstract void emitArchVirtualMachineDeclarations();
  abstract void emitArchAssemblerDeclarations(); 
}
