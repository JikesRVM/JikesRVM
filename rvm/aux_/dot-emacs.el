;;; -*- coding: iso-8859-1; -*-
;;;
;;; Copyright © IBM Corp 2003
;;;
;;; $Id$

;;; @author Steven Augart

;; If you're going to use the Jikes RVM Emacs LISP code, I'd recommend 
;; adding something like the following to a file in your home directory named
;; ~/.emacs: 

(let ((r (getenv "RVM_ROOT")))
  (when r
    (add-to-list 'load-path (concat r "/rvm/aux"))
    (require 'jikes-rvm)))

;; I recommend adding the following to your c-mode hooks; they can make it
;; more pleasant to edit Jikes RVM code:
(add-hook 'c-mode-common-hook
	  #'(lambda ()
	      (turn-on-font-lock)
	      (which-function-mode 1)
	      (c-toggle-auto-hungry-state 1)
	      ;; Guard the use of c-context-line-break, since it is not present
	      ;; in GNU Emacs 20.7
	      (if (fboundp 'c-context-line-break)
		  (define-key c-mode-base-map "\r" 'c-context-line-break))))
