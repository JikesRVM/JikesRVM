#! /bin/bash
#
# (C) Copyright IBM Corp. 2003
#
# $Id$
#
# @author Julian Dolby
# @modiifed Steven Augart, to make it run on Bash and be fully compatible
# with the existing limited script.

basename="${0##*/}"

usage () {
    exit_status=${1-1};
    shift;
    ## Go to stderr unless exiting with status zero.
    (( $1 == 0 )) || exec >&2
    [ "$*" ] && echo "$basename: $*"
    cat <<- EOF
	usage: $basename [ -- ] <seconds> <command>
	   or: $basename kill <PID> ...
	   or: $basename [ -h | --help ]
	       The first form runs the given command for at most 'seconds' seconds.
	       The second form kills the children of the given PID(s)
	       recursively, i.e. it also kills all their sub-processes.
	       It does not kill the <PID> itself (for historical compat.)

	  The -- flag has the usual meaning of signaling the end of the flags..

EOF
    exit $exit_status
}
    
## Auxiliary fumnctions
function kill_children_of () {
    [[ "$*" ]] || usage 1 "No pids specified to kill the kids of"
    for pid; do
	kill -STOP $pid
	local children="$(ps -ef | awk '$3~/'$pid'/{print $2}')"
	if [ "$children" ]; then
	    kill_pids $children
	    kill -9 $children
	fi
    done
}


## Arg parsing


while :; do
    if [[ $1 = "-h" || $1 = "--help" ]]; then
	usage 0;
    elif [[ $1 = -- ]]; then
	shift
	break
    elif [[ $1 == -* ]]; then
	usage 1 "Unknown flag: \"$1\"";
    else
	break;
    fi
done

(( $# >= 2 )) || usage 1 "too few arguments"

if [[ $1 = "kill" ]]; then
    shift;
    kill_children_of "$@";
else
    # Run with a timeout.
    timeout="$1"
    shift
    ulimit -t $timeout
    exec "$@"
fi
