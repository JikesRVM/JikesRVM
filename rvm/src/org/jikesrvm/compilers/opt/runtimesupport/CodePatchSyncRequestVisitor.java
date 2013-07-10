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
package org.jikesrvm.compilers.opt.runtimesupport;

import org.jikesrvm.scheduler.RVMThread;
import org.vmmagic.pragma.Uninterruptible;

/**
 * A visitor that is used to request synchronization of processor caches
 * after code patching has taken place.<p>
 */
@Uninterruptible
class CodePatchSyncRequestVisitor extends RVMThread.SoftHandshakeVisitor {

  @Override
  public boolean checkAndSignal(RVMThread t) {
    t.codePatchSyncRequested = true;
    return true; // handshake with everyone but ourselves.
  }

  @Override
  public boolean includeThread(RVMThread t) {
    // CollectorThreads will never be executing code that is subject to code patching.
    // (We don't allow speculative optimization of Uninterruptible code).  Therefore
    // it is safe to exempt collectors from the need to respond to the handshake.
    return !t.isCollectorThread();
  }

}
