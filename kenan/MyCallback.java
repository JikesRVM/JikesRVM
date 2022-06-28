/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2005
 */
import dacapo.Callback;

public class MyCallback extends Callback {
  /* Immediatly prior to start of the benchmark */
  public void start(String benchmark) {
    System.err.println("my hook starting " + benchmark);
    super.start(benchmark);
  };
  public void startWarmup(String benchmark) {
    System.err.println("my hook starting warmup " + benchmark);
    super.startWarmup(benchmark);
  };
  /* Immediatly after the end of the benchmark */
  public void stop() {
    super.stop();
    System.err.println("my hook stopped ");
    System.err.flush();
  };
  public void stopWarmup() {
    super.stopWarmup();
    System.err.println("my hook stopped warmup");
    System.err.flush();
  };
  public void complete(String benchmark, boolean valid) {
    super.complete(benchmark, valid);
    System.err.println("my hook "+(valid ? "PASSED " : "FAILED ")+ benchmark);
    System.err.flush();
  };
  public void completeWarmup(String benchmark, boolean valid) {
    super.completeWarmup(benchmark, valid);
    System.err.println("my hook "+(valid ? "PASSED " : "FAILED ")+"warmup " + benchmark);
    System.err.flush();
  };
}
