/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
/**
 * @author Dmitry B. Yershov
 * @version $Revision: 1.1.2.1.4.3 $
 */

package java.lang.ref;

import org.vmmagic.pragma.UninterruptibleNoWarn;

/**
 * @com.intel.drl.spec_ref 
 */
public class ReferenceQueue<T> extends Object {

    private Reference<? extends T> firstReference;

    /**
     * @com.intel.drl.spec_ref 
     */
    public ReferenceQueue() {
    }

    /**
     * @com.intel.drl.spec_ref 
     */
    @SuppressWarnings("unchecked")
    public synchronized Reference<? extends T> poll() {
        if (firstReference == null)
            return null;
        Reference<? extends T> ref = firstReference;
        firstReference = (firstReference.nextOnQueue == firstReference ? null
                : firstReference.nextOnQueue);
        ref.nextOnQueue = null;
        return ref;
    }

    /**
     * @com.intel.drl.spec_ref 
     */
    @SuppressWarnings("unchecked")
    public synchronized Reference<? extends T> remove(long timeout)
            throws IllegalArgumentException, InterruptedException {
        if (firstReference == null)
            wait(timeout);
        if (firstReference == null)
            return null;
        Reference<? extends T> ref = firstReference;
        firstReference = (firstReference.nextOnQueue == firstReference ? null
                : firstReference.nextOnQueue);
        ref.nextOnQueue = null;
        return ref;
    }

    /**
     * @com.intel.drl.spec_ref 
     */
    public Reference<? extends T> remove() throws InterruptedException {
        return remove(0L);
    }

    synchronized boolean enqueue(Reference<? extends T> ref) {
        ref.nextOnQueue = (firstReference == null ? ref : firstReference);
        firstReference = ref;
        notify();
        return true;
    }
    
    @UninterruptibleNoWarn("This is a lie")
    boolean enqueueInternal(Reference<? extends T> ref) {
        return enqueue(ref);
    }
}
