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
public class whet {


static final int ITERATIONS=3000; /* 10 Million Whetstone instructions */

static double           x1, x2, x3, x4, x, y, z, t, t1, t2;
static double[]         e1 = new double[4];
static int              i, j, k, l, n1, n2, n3, n4, n6, n7, n8, n9, n10, n11;

public static void main(String[] arg) {
    run();
}

public static void run() {
        System.out.println(" whetstone without MATH's native calls");
        /* initialize constants */

        t   =   0.499975;
        t1  =   0.50025;
        t2  =   2.0;

        /* set values of module weights */

        n1  =  10 * ITERATIONS;
        n2  =  12 * ITERATIONS;
        n3  =  14 * ITERATIONS;
        n4  = 345 * ITERATIONS;
        n6  = 210 * ITERATIONS;
        n7  =  32 * ITERATIONS;
        n8  = 899 * ITERATIONS;
        n9  = 616 * ITERATIONS;
        n10 =  10 * ITERATIONS;
        n11 =  93 * ITERATIONS;


/* MODULE 1:  simple identifiers */

        x1 =  1.0;
        x2 = x3 = x4 = -1.0;

        for(i = 1; i <= n1; i += 1) {
                x1 = (x1 + x2 + x3 - x4) * t;
                x2 = (x1 + x2 - x3 - x4) * t;
                x3 = (x1 - x2 + x3 + x4) * t;
                x4 = (-x1 + x2 + x3 + x4) * t;
        }
        pout(n1, n1, n1, x1, x2, x3, x4);


/* MODULE 2:  array elements */

        e1[0] =  1.0;
        e1[1] = e1[2] = e1[3] = -1.0;

        for (i = 1; i <= n2; i +=1) {
                e1[0] = (e1[0] + e1[1] + e1[2] - e1[3]) * t;
                e1[1] = (e1[0] + e1[1] - e1[2] + e1[3]) * t;
                e1[2] = (e1[0] - e1[1] + e1[2] + e1[3]) * t;
                e1[3] = (-e1[0] + e1[1] + e1[2] + e1[3]) * t;
        }
        pout(n2, n3, n2, e1[0], e1[1], e1[2], e1[3]);

/* MODULE 3:  array as parameter */

        for (i = 1; i <= n3; i += 1)
                pa(e1);
        pout(n3, n2, n2, e1[0], e1[1], e1[2], e1[3]);

/* MODULE 4:  conditional jumps */

        j = 1;
        for (i = 1; i <= n4; i += 1) {
                if (j == 1)
                        j = 2;
                else
                        j = 3;

                if (j > 2)
                        j = 0;
                else
                        j = 1;

                if (j < 1)
                        j = 1;
                else
                        j = 0;
        }
        pout(n4, j, j, x1, x2, x3, x4);

/* MODULE 5:  omitted */

/* MODULE 6:  integer arithmetic */

        j = 1;
        k = 2;
        l = 3;

        for (i = 1; i <= n6; i += 1) {
                j = j * (k - j) * (l -k);
                k = l * k - (l - j) * k;
                l = (l - k) * (k + j);

                e1[l - 2] = j + k + l;          /* C arrays are zero based */
                e1[k - 2] = j * k * l;
        }
        pout(n6, j, k, e1[0], e1[1], e1[2], e1[3]);

/* MODULE 7:  trig. functions */
        /* NOT YET SUPPORTED BY RVM
        x = y = 0.5;

        for(i = 1; i <= n7; i +=1) {
           x = t * Math.atan(t2*Math.sin(x)*Math.cos(x)/(Math.cos(x+y)+Math.cos(x-y)-1.0));
           y = t * Math.atan(t2*Math.sin(y)*Math.cos(y)/(Math.cos(x+y)+Math.cos(x-y)-1.0));
        }
        pout(n7, j, k, x, x, y, y);
        */

/* MODULE 8:  procedure calls */

        x = y = z = 1.0;

        for (i = 1; i <= n8; i +=1)
            p3(x, y, z);
        pout(n8, j, k, x, y, z, z);

/* MODULE9:  array references */

        j = 1;
        k = 2;
        l = 3;

        e1[0] = 1.0;
        e1[1] = 2.0;
        e1[2] = 3.0;
        e1[3] = 4.0;

        for(i = 1; i <= n9; i += 1)
                p0();
        pout(n9, j, k, e1[0], e1[1], e1[2], e1[3]);

/* MODULE10:  integer arithmetic */

        j = 2;
        k = 3;

        for(i = 1; i <= n10; i +=1) {
                j = j + k;
                k = j + k;
                j = k - j;
                k = k - j - j;
        }
        pout(n10, j, k, x1, x2, x3, x4);

/* MODULE11:  standard functions */
        /* NOT YET SUPPORTED BY RVM
        x = 0.75;
        for(i = 1; i <= n11; i +=1)
                x = Math.sqrt(Math.exp(Math.log(x) / t1));

        pout(n11, j, k, x, x, x, x);
        */
}

private static void pa(double[] e) {
        int j;

        for (j=0; j < 6; j++) {
        e[0] = (e[0] + e[1] + e[2] - e[3]) * t;
        e[1] = (e[0] + e[1] - e[2] + e[3]) * t;
        e[2] = (e[0] - e[1] + e[2] + e[3]) * t;
        e[3] = (-e[0] + e[1] + e[2] + e[3]) / t2;
        }
}


private static void p3(double x, double y, double w) {
        x  = t * (x + y);
        y  = t * (x + y);
        z = (x + y) /t2;
}


private static void p0() {
        e1[j] = e1[k];
        e1[k] = e1[l];
        e1[l] = e1[j];
}


private static void pout(int n, int j, int k, double x1, double x2, double x3, double x4)
{
   System.out.println(x1 + " " + x2 + " " + x3 + " " + x4 +
                      //" " + Double.doubleToLongBits(x1) +
                      //" " + Double.doubleToLongBits(x2) +
                      //" " + Double.doubleToLongBits(x3) +
                      //" " + Double.doubleToLongBits(x4) +
                      " " + n + " " +j + " " + k);
}

}
