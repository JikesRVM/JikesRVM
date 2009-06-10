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

/*
 * Segregate the code to initialize the boot record,
 * because the machine generated cruft in InterfaceDeclarations.h
 * causes gcc 3.2 to complain bitterly...
 */

#define NEED_BOOT_RECORD_DECLARATIONS
#define NEED_BOOT_RECORD_INITIALIZATION
#include <InterfaceDeclarations.h>
