/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
/**
 * Eratosthenes Sieve Prime-Number Program from BYTE
 * January 1983
 *
 * @author unascribed
 */
 
class sieve { 
  static final int size = 8190*2;
  static boolean[] flags = new boolean[size+1];
  public static void main(String args[]) {
    go();
  }
  static boolean run() {
    int i = go();
    System.out.println("Sieve returned: " + i);
    return true;
  }

  public static int go() {
    int i, prime, k, count = 0, iter;
    //    System.out.print("3000 iterations --> ");
    for (iter = 1; iter <= 100; iter++) { /* do program 100 times*/
      count = 0;                          /* prime counter */
      for (i = 0; i <= size; i++)         /* set all flags true */
        flags[i] = true;
      for (i = 0; i <= size; i++) {
        if (flags[i]) {                   /* found a prime */
          prime = i + i + 3;              /* twice index + 3 */
          for (k = i + prime; k <= size; k += prime)
            flags[k] = false;             /* kill all multiple */
          count++;
        }
      }
    }
    // System.out.println(count + " primes."); /* primes found on 100th pass */
    
    return count;
  }
}
