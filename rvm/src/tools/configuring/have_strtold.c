#define _ISOC99_SOURCE          /* make it clear to slightly old versions of
                                   the GNU C library that we want C99
                                   features, specifically strtold() */
#define _ISOC9X_SOURCE          // For even older versions of GNU C library


extern long double dummy(void);
#include <stdlib.h>

long double
dummy(void)
{
    // We don't need to run this; just make sure it compiles.
    long double ld = strtold(NULL, NULL);
    return ld;
}

