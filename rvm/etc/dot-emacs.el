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
    (add-to-list 'load-path (concat r "/rvm/etc"))
    (require 'jikes-rvm)))

;; I recommend adding the following to your c-mode-common-hook; they can make
;; it more pleasant to edit Jikes RVM code.
(add-hook 'c-mode-common-hook
	  #'(lambda ()
	      (turn-on-font-lock)
	      (which-function-mode 1)
	      ;; I like the auto-newline and hungry-delete-key features.
	      ;; You can learn about them with C-h f c-toggle-auto-state and
	      ;; C-h f c-toggle-hungry-state.  
	      ;;
	      ;; auto-newline is good for writing new code, but for some it
	      ;; will make it annoying to edit existing code.  Uncomment the
	      ;; line below if you're writing new code, or manually enable the
	      ;; auto-newline in that situation.

;	      (c-toggle-auto-state 1)

	      ;; If you find the hungry-delete-key feature annoying, then
	      ;; you'll want to leave the code below commented out too.
;	      (c-toggle-hungry-state 1)

	      ;; c-context-line-break is not part of GNU Emacs 20.7
	      (when (fboundp 'c-context-line-break)
		  (define-key c-mode-base-map "\r" 'c-context-line-break))))
