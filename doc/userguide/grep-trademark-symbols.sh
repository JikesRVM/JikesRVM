# /bin/bash
# grep-trademarks.sh
# Grep across the source files we use in order to list all of the
# \R and \TM we use.
egrep -n '\\(TM|R)(web)?(heading)?\>' $(cat inclusions.filenames)
