#! /usr/bin/env bash
#
# (C) Copyright IBM Corp. 2003
#
# $Id$
#
# @author Julian Dolby
# @modiifed Steven Augart

ME="${0##*/}"
allkids_awk=$RVM_ROOT/rvm/regression/allkids.awk
mypath=$RVM_ROOT/rvm/regression/$ME

# This does not seem to change whether the "Killed" message is output:
# set +o notify
usage () {
    exit_status=${1-1};
    shift;
    ## Go to stderr unless exiting with status zero.
    (( $exit_status == 0 )) || exec >&2
    [ "$*" ] && echo "$ME: $*"
    cat <<- EOF
	usage: $ME [-v | --verbose] [ -- ] <seconds> <command>
	   or: $ME [-v | --verbose] kill <PID> ...
	   or: $ME [ -h | --help ]
	       The first form runs the given command for at most 'seconds' seconds.
	       The second form kills the children of the given PID(s)
	       recursively, i.e. it also kills all their sub-processes.
	       It does not kill the <PID> itself (for historical compat.)

	  The -- flag has the usual meaning of signaling the end of the flags..
	  The -v flag means to be verbose.

	For debugging, you can also use:
	    $ME showkids <PID>...
			List children of <PID>...
EOF
    exit $exit_status
}
    
## List all of the kids recursively, if you can.  If not, return unsuccessful
## exit status.
function list_recursive_kids () {
    if type pstree &> /dev/null; then
	# Extract just the numbers and make them into a newline-separated list.
	pstree -p "$@" | sed -e 's/[^0-9]\+/ /g' -e 's/  //g' -e 's/  //g' -e 's/^ //' -e 's/ $//' -e '/^$/d' | tr ' ' '\n'
    else
	show_process_table | awk -f $allkids_awk "$@"
    fi
}

function show_process_table () {
    if [[ $(uname) == Darwin ]]; then
	ps -wwajx
    else
	ps -ef
    fi
}


## Auxiliary functions
function kill_recursively () {
    [[ "$*" ]] || usage 1 "No pids specified to kill recursively"

    kill -STOP $*		# STOP them all from spawning
    all_kids="$(list_recursive_kids $*)" || echo >&2 "$ME: Trouble getting a list of the spawn of $*.  We'll try with what we have."
    if [[ "$all_kids" != *[0-9]* ]] ; then
	echo >&2 "$ME: Can't find any of $* and her/their spawn to kill (maybe they died of natural causes while they were on death row)?"
	return 1;
    fi
    (( verbose )) && echo "$ME: Killing" $all_kids
    (( verbose > 1 )) && ps l $all_kids
    kill -9 $all_kids
}


## Arg parsing


declare -i verbose=0

while :; do
    if [[ $1 = "-h" || $1 = "--help" ]]; then
	shift
	usage 0;
    elif [[ $1 = "-v" || $1 = "--verbose" ]]; then
	shift
	let ++verbose
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

(( verbose > 1 )) && echo "$ME: My PID is $$"

declare -i executioner_started=0

function executioner_alarm() {
    echo >&2 "$ME: OVERTIME ($timeout seconds have passed) executing ${worker} and spawn (command: $*)"
    let ++executioner_started
    kill_recursively $worker
}

function executioner_interrupt() {
    (( $verbose )) && echo "$ME: INTERRUPTED!  Executing ${worker} and spawn (command: $*)..."
    let ++executioner_started
    kill_recursively $worker
    (( $verbose )) && echo 'executed.'
}

if [[ $1 = "kill" ]]; then
    shift;
    kill_recursively "$@";
elif [[ $1 = "showkids" ]]; then
    shift;
    list_recursive_kids "$@"
else
    # Run with a timeout.
    timeout="$1"
    shift
    "$@" &
    worker=$!
    (( verbose )) && echo "$ME: Worker pid is $worker"
    trap "executioner_alarm '$*'" ALRM
    trap "executioner_interrupt '$*'" INT QUIT HUP TERM
    ( sleep $timeout ; kill -ALRM $$) &
    executioner=$!
    (( verbose )) && echo "$ME: Executioner pid is $executioner"
    wait $worker
## From the Bash 2.05b manual page:
#       When  bash  is
#       waiting  for an asynchronous command via the wait builtin,
#       the reception of a signal for which a trap  has  been  set
#       will  cause the wait builtin to return immediately with an
#       exit status greater than 128, immediately after which  the
#       trap is executed.
    worker_xitstatus=$?

    if (( ! executioner_started )); then
	(( verbose )) && echo "$ME: worker $worker (running $*) exited with status $worker_xitstatus; let's kill the executioner $executioner"
	kill_recursively $executioner
    fi
    # Report the worker's exit status!  RunSanityTests requires this.
    exit $worker_xitstatus
fi
