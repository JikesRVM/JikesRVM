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
import crypt.JGFCryptBench;
import jgfutil.JGFInstrumentor;
import lufact.JGFLUFactBench;
import moldyn.JGFMolDynBench;
import montecarlo.JGFMonteCarloBench;
import raytracer.JGFRayTracerBench;
import series.JGFSeriesBench;
import sor.JGFSORBench;
import sparsematmult.JGFSparseMatmultBench;

public class JGF_MultithreadedBenchmark {

  public static void main(String[] argv) {

    final int size = Integer.parseInt(argv[0]);
    final int threadCount = Integer.parseInt(argv[1]);

    JGFInstrumentor.printHeader(1,0,threadCount);

    final JGFBarrierBench b1 = new JGFBarrierBench(threadCount);
    b1.JGFrun();

    final JGFForkJoinBench b2 = new JGFForkJoinBench(threadCount);
    b2.JGFrun();

    final JGFSyncBench b3 = new JGFSyncBench(threadCount);
    b3.JGFrun();

    JGFInstrumentor.printHeader(2, size, threadCount);

    final JGFSeriesBench b4 = new JGFSeriesBench(threadCount);
    b4.JGFrun(size);

    final JGFLUFactBench b5 = new JGFLUFactBench(threadCount);
    b5.JGFrun(size);

    final JGFCryptBench b6 = new JGFCryptBench(threadCount);
    b6.JGFrun(size);

    final JGFSORBench b7 = new JGFSORBench(threadCount);
    b7.JGFrun(size);

    final JGFSparseMatmultBench b8 = new JGFSparseMatmultBench(threadCount);
    b8.JGFrun(size);

    JGFInstrumentor.printHeader(3,size,threadCount);

    final JGFMolDynBench b9 = new JGFMolDynBench(threadCount);
    b9.JGFrun(size);

    final JGFMonteCarloBench b10 = new JGFMonteCarloBench(threadCount);
    b10.JGFrun(size);

    final JGFRayTracerBench b11 = new JGFRayTracerBench(threadCount);
    b11.JGFrun(size);
  }
}
