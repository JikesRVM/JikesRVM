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
import euler.JGFEulerBench;
import fft.JGFFFTBench;
import heapsort.JGFHeapSortBench;
import jgfutil.JGFInstrumentor;
import lufact.JGFLUFactBench;
import moldyn.JGFMolDynBench;
import montecarlo.JGFMonteCarloBench;
import raytracer.JGFRayTracerBench;
import search.JGFSearchBench;
import series.JGFSeriesBench;
import sor.JGFSORBench;
import sparsematmult.JGFSparseMatmultBench;

public class JGF_SerialBenchmark {
  public static void main(String[] argv) {
    final int size = Integer.parseInt(argv[0]);

    JGFInstrumentor.printHeader(1, size);

    final JGFArithBench ab = new JGFArithBench();
    ab.JGFrun();

    final JGFAssignBench asb = new JGFAssignBench();
    asb.JGFrun();

    final JGFCastBench cb = new JGFCastBench();
    cb.JGFrun();

    final JGFCreateBench crb = new JGFCreateBench();
    crb.JGFrun();

    final JGFExceptionBench eb = new JGFExceptionBench();
    eb.JGFrun();

    final JGFLoopBench lb = new JGFLoopBench();
    lb.JGFrun();

    final JGFMathBench mb = new JGFMathBench();
    mb.JGFrun();

    final JGFMethodBench meb = new JGFMethodBench();
    meb.JGFrun();

    final JGFSerialBench szb = new JGFSerialBench();
    szb.JGFrun();

    JGFInstrumentor.printHeader(2, size);

    final JGFSeriesBench se = new JGFSeriesBench();
    se.JGFrun(size);

    final JGFLUFactBench lub = new JGFLUFactBench();
    lub.JGFrun(size);

    final JGFHeapSortBench hb = new JGFHeapSortBench();
    hb.JGFrun(size);

    final JGFCryptBench cryb = new JGFCryptBench();
    cryb.JGFrun(size);

    final JGFFFTBench fft = new JGFFFTBench();
    fft.JGFrun(size);

    final JGFSORBench jb = new JGFSORBench();
    jb.JGFrun(size);

    final JGFSparseMatmultBench smm = new JGFSparseMatmultBench();
    smm.JGFrun(size);

    JGFInstrumentor.printHeader(3, size);

    final JGFEulerBench eub = new JGFEulerBench();
    eub.JGFrun(size);

    final JGFMolDynBench mdb = new JGFMolDynBench();
    mdb.JGFrun(size);

    final JGFMonteCarloBench mcb = new JGFMonteCarloBench();
    mcb.JGFrun(size);

    final JGFRayTracerBench rtb = new JGFRayTracerBench();
    rtb.JGFrun(size);

    final JGFSearchBench sb = new JGFSearchBench();
    sb.JGFrun(size);
  }
}
