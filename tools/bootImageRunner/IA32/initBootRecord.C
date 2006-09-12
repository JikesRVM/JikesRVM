/*
 * (C) Copyright IBM Corp 2003
 */
//$Id$

/*
 * Segregate the code to initialize the boot record, 
 * because the machine generated cruft in InterfaceDeclarations.h
 * causes gcc 3.2 to complain bitterly...
 * @author Dave Grove
 */

#define NEED_BOOT_RECORD_DECLARATIONS
#define NEED_BOOT_RECORD_INITIALIZATION
#include <InterfaceDeclarations.h>
