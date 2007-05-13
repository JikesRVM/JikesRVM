/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp 2003
 */

/*
 * Segregate the code to initialize the boot record, 
 * because the machine generated cruft in InterfaceDeclarations.h
 * causes gcc 3.2 to complain bitterly...
 */

#define NEED_BOOT_RECORD_DECLARATIONS
#define NEED_BOOT_RECORD_INITIALIZATION
#include <InterfaceDeclarations.h>
