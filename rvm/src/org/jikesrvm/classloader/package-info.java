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
 * Provides classes that implement the classloading system as well as
 * data types that represent Java entities like classes, fields, methods
 * and primitives.<p>
 *
 * Code in the classloading subsystem must be carefully written not to cause
 * dynamic classloading during the boot of the VM. This means that all necessary
 * classes must be contained in the bootimage.
 */
package org.jikesrvm.classloader;
