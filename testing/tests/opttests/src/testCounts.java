/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
/**
 * @author unascribed
 */
class testCounts
{
    public static void main() {
        bar(100);
    }

  public static void bar(int n) {
        int a[] = new int[n];
        int length = a.length;

        for(int i = 0 ; i < length ; i++ )
        {
            a[i] = a[i] + 1;
        }
    }

    static int foo(int a[], int i) {
        return a[i];
    }
}
