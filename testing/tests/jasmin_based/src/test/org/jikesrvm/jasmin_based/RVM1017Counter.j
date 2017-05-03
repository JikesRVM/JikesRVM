;
;  This file is part of the Jikes RVM project (http://jikesrvm.org).
;
;  This file is licensed to You under the Eclipse Public License (EPL);
;  You may not use this file except in compliance with the License. You
;  may obtain a copy of the License at
;
;      http://www.opensource.org/licenses/eclipse-1.0.php
;
;  See the COPYRIGHT.txt file distributed with this work for information
;  regarding copyright ownership.
;

.bytecode 49.0
.source RVM1017Counter.j
.class public test/org/jikesrvm/jasmin_based/RVM1017Counter
.super test/org/jikesrvm/jasmin_based/RVM1017AbstractCounter

.method public <init>()V
  .limit stack 1
  .limit locals 1
  aload_0
  invokespecial test/org/jikesrvm/jasmin_based/RVM1017AbstractCounter/<init>()V
  return
.end method

; this should be ok
.method public synchronized addToCounter2(I)V
  .limit stack 40
  .limit locals 2
  aload_0
  aload_0
  getfield test/org/jikesrvm/jasmin_based/RVM1017AbstractCounter/count J
  iload_1
  i2l
  ladd
  putfield test/org/jikesrvm/jasmin_based/RVM1017AbstractCounter/count J
  return
.end method

; this should fail
.method public synchronized addToCounter(I)V
  .limit stack 6
  .limit locals 2
  aload_0 ; load for putfield invocation
  iload_1
  aload_0 ; load for getfield invocation
  getfield test/org/jikesrvm/jasmin_based/RVM1017AbstractCounter/count J
  lstore_0 ; overwrite "this" pointer - needed to trigger the bug
  i2l
  lload_0
  ladd
  putfield test/org/jikesrvm/jasmin_based/RVM1017AbstractCounter/count J
  return
.end method