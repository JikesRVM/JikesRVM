extern long double dummy(void);
#include <stdlib.h>

long double
dummy(void)
{
    // We don't need to run this; just make sure it compiles.
    long double ld = strtold(NULL, NULL);
    return ld;
}

