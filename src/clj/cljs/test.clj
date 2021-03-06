;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns cljs.test
  (:require [cljs.env :as env]
            [cljs.analyzer :as ana]))

;; =============================================================================
;; Utilities for assertions

(defn function?
  "Returns true if argument is a function or a symbol that resolves to
  a function (not a macro)."
  [menv x]
  (:fn-var (ana/resolve-var menv x)))

(defn assert-predicate
  "Returns generic assertion code for any functional predicate.  The
  'expected' argument to 'report' will contains the original form, the
  'actual' argument will contain the form with all its sub-forms
  evaluated.  If the predicate returns false, the 'actual' form will
  be wrapped in (not...)."
  [env msg form]
  (let [args (rest form)
        pred (first form)]
    `(let [values# (list ~@args)
           result# (apply ~pred values#)
           env'# (if result#
                   (cljs.test/do-report ~env
                     {:type :pass, :message ~msg,
                      :expected '~form, :actual (cons ~pred values#)})
                   (cljs.test/do-report ~env
                     {:type :fail, :message ~msg,
                      :expected '~form, :actual (list '~'not (cons '~pred values#))}))]
       (if (:return env'#)
         env'#
         result#))))

(defn assert-any
  "Returns generic assertion code for any test, including macros, Java
  method calls, or isolated symbols."
  [env msg form]
  `(let [value# ~form
         env'# (if value#
                 (cljs.test/do-report ~env
                   {:type :pass, :message ~msg,
                    :expected '~form, :actual value#})
                 (cljs.test/do-report ~env
                   {:type :fail, :message ~msg,
                    :expected '~form, :actual value#}))]
     (if (:return env'#)
       env'#
       value#)))

;; =============================================================================
;; Assertion Methods

;; You don't call these, but you can add methods to extend the 'is'
;; macro.  These define different kinds of tests, based on the first
;; symbol in the test expression.

(defmulti assert-expr 
  (fn [env menv msg form]
    (cond
      (nil? form) :always-fail
      (seq? form) (first form)
      :else :default)))

(defmethod assert-expr :always-fail [env menv msg form]
  ;; nil test: always fail
  `(cljs.test/do-report ~env {:type :fail, :message ~msg}))

(defmethod assert-expr :default [env menv msg form]
  (if (and (sequential? form)
           (function? menv (first form)))
    (assert-predicate env msg form)
    (assert-any env msg form)))

(defmethod assert-expr 'instance? [env menv msg form]
  ;; Test if x is an instance of y.
  `(let [klass# ~(nth form 1)
         object# ~(nth form 2)]
     (let [result# (instance? klass# object#)
           env'# (if result#
                   (cljs.test/do-report ~env
                     {:type :pass, :message ~msg,
                      :expected '~form, :actual (class object#)})
                   (cljs.test/do-report ~env
                     {:type :fail, :message ~msg,
                      :expected '~form, :actual (class object#)}))]
       (if (:return env'#)
         env'#
         result#))))

(defmethod assert-expr 'thrown? [env menv msg form]
  ;; (is (thrown? c expr))
  ;; Asserts that evaluating expr throws an exception of class c.
  ;; Returns the exception thrown.
  (let [klass (second form)
        body (nthnext form 2)]
    `(try
       ~@body
       (cljs.test/do-report ~env
         {:type :fail, :message ~msg,
          :expected '~form, :actual nil})
       (catch ~klass e#
         (cljs.test/do-report ~env
           {:type :pass, :message ~msg,
            :expected '~form, :actual e#})))))

(defmethod assert-expr 'thrown-with-msg? [env menv msg form]
  ;; (is (thrown-with-msg? c re expr))
  ;; Asserts that evaluating expr throws an exception of class c.
  ;; Also asserts that the message string of the exception matches
  ;; (with re-find) the regular expression re.
  (let [klass (nth form 1)
        re (nth form 2)
        body (nthnext form 3)]
    `(try
       ~@body
       (cljs.test/do-report {:type :fail, :message ~msg, :expected '~form, :actual nil})
       (catch ~klass e#
         (let [m# (.getMessage e#)
               env'# (if (re-find ~re m#)
                       (cljs.test/do-report ~env
                         {:type :pass, :message ~msg,
                          :expected '~form, :actual e#})
                       (cljs.test/do-report ~env
                         {:type :fail, :message ~msg,
                          :expected '~form, :actual e#}))]
           (if (:return env'#)
             env'#
             e#))))))

(defmacro try-expr
  "Used by the 'is' macro to catch unexpected exceptions.
  You don't call this."
  [env msg form]
  `(try
     ~(cljs.test/assert-expr env &env msg form)
     (catch :default t#
       (cljs.test/do-report ~env
         {:type :error, :message ~msg,
          :expected '~form, :actual t#}))))

;; =============================================================================
;; Assertion Macros

(defmacro is
  "Generic assertion macro.  'form' is any predicate test.
  'msg' is an optional message to attach to the assertion.
  
  Example: (is (= 4 (+ 2 2)) \"Two plus two should be 4\")

  Special forms:

  (is (thrown? c body)) checks that an instance of c is thrown from
  body, fails if not; then returns the thing thrown.

  (is (thrown-with-msg? c re body)) checks that an instance of c is
  thrown AND that the message on the exception matches (with
  re-find) the regular expression re."
  ([form] `(cljs.test/is ~form nil))
  ([form msg]
   (if (contains? (:locals &env) 'cljs$lang$test$body)
     `(fn [env#] (cljs.test/try-expr env# ~msg ~form))
     `(cljs.test/try-expr (cljs.test/empty-env) ~msg ~form))))

(defmacro testing
  "Adds a new string to the list of testing contexts.  May be nested,
  but must occur inside a test function (deftest)."
  [env string & body]
  `(fn [env#]
     (reduce #(%2 %1)
       (update-in env# [:testing-contexts] conj ~string)
       [~@body])))

;; =============================================================================
;; Defining Tests

(defmacro deftest
  "Defines a test function with no arguments.  Test functions may call
  other tests, so tests may be composed.  If you compose tests, you
  should also define a function named test-ns-hook; run-tests will
  call test-ns-hook instead of testing all vars.

  Note: Actually, the test body goes in the :test metadata on the var,
  and the real function (the value of the var) calls test-var on
  itself.

  When cljs.analyzer/*load-tests* is false, deftest is ignored."
  [name & body]
  (when ana/*load-tests*
    `(do
       (def ~(vary-meta name assoc :test
               `(fn self#
                  ([] (self# (cljs.test/empty-env)))
                  ([env#]
                   (let [~'cljs$lang$test$body nil
                         ret# (reduce #(%2 %1) env# [~@body])]
                     (when (:return env#)
                       ret#)))))
         (fn
           ([] (~name (cljs.test/empty-env)))
           ([env#]
            (cljs.test/test-var env# (.-cljs$lang$var ~name)))))
       (set! (.-cljs$lang$var ~name) (var ~name)))))

(defmacro test-all-vars
  "Calls test-vars on every var interned in the namespace, with fixtures."
  ([ns]
   `(cljs.test/test-vars (vals (ns-interns ~ns))))
  ([env ns]
   `(cljs.test/test-vars ~env (vals (ns-interns ~ns)))))

(defmacro test-ns
  "If the namespace defines a function named test-ns-hook, calls that.
  Otherwise, calls test-all-vars on the namespace.  'ns' is a
  namespace object or a symbol.

  Internally binds *report-counters* to a ref initialized to
  *initial-report-counters*.  Returns the final, dereferenced state of
  *report-counters*."
  ([ns] `(cljs.test/test-ns (cljs.test/empty-env) ~ns))
  ([env [quote ns :as form]]
   (assert (and (= quote 'quote) (symbol? ns)) "Argument to test-ns must be a quoted symbol")
   `(let [env# ~env
          return# (:return env#)
          env'# (-> (assoc env# :return true)
                  (cljs.test/do-report {:type :begin-test-ns, :ns ~form})
                  ;; If the namespace has a test-ns-hook function, call that:
                  ~(if-let [v (get-in @env/*compiler* [::ana/namespaces ns :defs 'test-ns-hook])]
                     `(~(symbol (name ns) "test-ns-hook"))
                     ;; Otherwise, just test every var in the namespace.
                     `(cljs.test/test-all-vars ~form))
                  (cljs.test/do-report {:type :end-test-ns, :ns ~form}))]
      (when return# env'#))))
