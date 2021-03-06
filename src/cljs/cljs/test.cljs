;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns
^{:author "Stuart Sierra, with contributions and suggestions by 
  Chas Emerick, Allen Rohner, Stuart Halloway, and David Nolen",
     :doc "A unit testing framework.

   ASSERTIONS

   The core of the library is the \"is\" macro, which lets you make
   assertions of any arbitrary expression:

   (is (= 4 (+ 2 2)))
   (is (instance? Integer 256))
   (is (.startsWith \"abcde\" \"ab\"))

   You can type an \"is\" expression directly at the REPL, which will
   print a message if it fails.

       user> (is (= 5 (+ 2 2)))

       FAIL in  (:1)
       expected: (= 5 (+ 2 2))
         actual: (not (= 5 4))
       false

   The \"expected:\" line shows you the original expression, and the
   \"actual:\" shows you what actually happened.  In this case, it
   shows that (+ 2 2) returned 4, which is not = to 5.  Finally, the
   \"false\" on the last line is the value returned from the
   expression.  The \"is\" macro always returns the result of the
   inner expression.

   There are two special assertions for testing exceptions.  The
   \"(is (thrown? c ...))\" form tests if an exception of class c is
   thrown:

   (is (thrown? ArithmeticException (/ 1 0))) 

   \"(is (thrown-with-msg? c re ...))\" does the same thing and also
   tests that the message on the exception matches the regular
   expression re:

   (is (thrown-with-msg? ArithmeticException #\"Divide by zero\"
                         (/ 1 0)))

   DOCUMENTING TESTS

   \"is\" takes an optional second argument, a string describing the
   assertion.  This message will be included in the error report.

   (is (= 5 (+ 2 2)) \"Crazy arithmetic\")

   In addition, you can document groups of assertions with the
   \"testing\" macro, which takes a string followed by any number of
   assertions.  The string will be included in failure reports.
   Calls to \"testing\" may be nested, and all of the strings will be
   joined together with spaces in the final report, in a style
   similar to RSpec <http://rspec.info/>

   (testing \"Arithmetic\"
     (testing \"with positive integers\"
       (is (= 4 (+ 2 2)))
       (is (= 7 (+ 3 4))))
     (testing \"with negative integers\"
       (is (= -4 (+ -2 -2)))
       (is (= -1 (+ 3 -4)))))

   Note that, unlike RSpec, the \"testing\" macro may only be used
   INSIDE a \"deftest\" or \"with-test\" form (see below).


   DEFINING TESTS

   There are two ways to define tests.  The \"with-test\" macro takes
   a defn or def form as its first argument, followed by any number
   of assertions.  The tests will be stored as metadata on the
   definition.

   (with-test
       (defn my-function [x y]
         (+ x y))
     (is (= 4 (my-function 2 2)))
     (is (= 7 (my-function 3 4))))

   As of Clojure SVN rev. 1221, this does not work with defmacro.
   See http://code.google.com/p/clojure/issues/detail?id=51

   The other way lets you define tests separately from the rest of
   your code, even in a different namespace:

   (deftest addition
     (is (= 4 (+ 2 2)))
     (is (= 7 (+ 3 4))))

   (deftest subtraction
     (is (= 1 (- 4 3)))
     (is (= 3 (- 7 4))))

   This creates functions named \"addition\" and \"subtraction\", which
   can be called like any other function.  Therefore, tests can be
   grouped and composed, in a style similar to the test framework in
   Peter Seibel's \"Practical Common Lisp\"
   <http://www.gigamonkeys.com/book/practical-building-a-unit-test-framework.html>

   (deftest arithmetic
     (addition)
     (subtraction))

   The names of the nested tests will be joined in a list, like
   \"(arithmetic addition)\", in failure reports.  You can use nested
   tests to set up a context shared by several tests.


   RUNNING TESTS

   Run tests with the function \"(run-tests namespaces...)\":

   (run-tests 'your.namespace 'some.other.namespace)

   If you don't specify any namespaces, the current namespace is
   used.  To run all tests in all namespaces, use \"(run-all-tests)\".

   By default, these functions will search for all tests defined in
   a namespace and run them in an undefined order.  However, if you
   are composing tests, as in the \"arithmetic\" example above, you
   probably do not want the \"addition\" and \"subtraction\" tests run
   separately.  In that case, you must define a special function
   named \"test-ns-hook\" that runs your tests in the correct order:

   (defn test-ns-hook []
     (arithmetic))

   Note: test-ns-hook prevents execution of fixtures (see below).


   OMITTING TESTS FROM PRODUCTION CODE

   You can bind the variable \"*load-tests*\" to false when loading or
   compiling code in production.  This will prevent any tests from
   being created by \"with-test\" or \"deftest\".


   FIXTURES

   Fixtures allow you to run code before and after tests, to set up
   the context in which tests should be run.

   A fixture is just a function that calls another function passed as
   an argument.  It looks like this:

   (defn my-fixture [f]
      Perform setup, establish bindings, whatever.
     (f)  Then call the function we were passed.
      Tear-down / clean-up code here.
    )

   Fixtures are attached to namespaces in one of two ways.  \"each\"
   fixtures are run repeatedly, once for each test function created
   with \"deftest\" or \"with-test\".  \"each\" fixtures are useful for
   establishing a consistent before/after state for each test, like
   clearing out database tables.

   \"each\" fixtures can be attached to the current namespace like this:
   (use-fixtures :each fixture1 fixture2 ...)
   The fixture1, fixture2 are just functions like the example above.
   They can also be anonymous functions, like this:
   (use-fixtures :each (fn [f] setup... (f) cleanup...))

   The other kind of fixture, a \"once\" fixture, is only run once,
   around ALL the tests in the namespace.  \"once\" fixtures are useful
   for tasks that only need to be performed once, like establishing
   database connections, or for time-consuming tasks.

   Attach \"once\" fixtures to the current namespace like this:
   (use-fixtures :once fixture1 fixture2 ...)

   Note: Fixtures and test-ns-hook are mutually incompatible.  If you
   are using test-ns-hook, fixture functions will *never* be run.


   SAVING TEST OUTPUT TO A FILE

   All the test reporting functions write to the var *test-out*.  By
   default, this is the same as *out*, but you can rebind it to any
   PrintWriter.  For example, it could be a file opened with
   clojure.java.io/writer.


   EXTENDING TEST-IS (ADVANCED)

   You can extend the behavior of the \"is\" macro by defining new
   methods for the \"assert-expr\" multimethod.  These methods are
   called during expansion of the \"is\" macro, so they should return
   quoted forms to be evaluated.

   You can plug in your own test-reporting framework by rebinding
   the \"report\" function: (report event)

   The 'event' argument is a map.  It will always have a :type key,
   whose value will be a keyword signaling the type of event being
   reported.  Standard events with :type value of :pass, :fail, and
   :error are called when an assertion passes, fails, and throws an
   exception, respectively.  In that case, the event will also have
   the following keys:

     :expected   The form that was expected to be true
     :actual     A form representing what actually occurred
     :message    The string message given as an argument to 'is'

   The \"testing\" strings will be a list in \"*testing-contexts*\", and
   the vars being tested will be a list in \"*testing-vars*\".

   Your \"report\" function should wrap any printing calls in the
   \"with-test-out\" macro, which rebinds *out* to the current value
   of *test-out*.

   For additional event types, see the examples in the code.
"}
  cljs.test
  (:require-macros
   [clojure.template :as temp]))

;; =============================================================================
;; Protocols

(defprotocol ITestReporter
  (-do-report [_ env m]))

;; =============================================================================
;; Default Reporting

(defn testing-vars-str
  "Returns a string representation of the current test.  Renders names
  in *testing-vars* as a list, then the source file and line of
  current assertion."
  [env m]
  (let [{:keys [file line]} m]
    (str
      (reverse (map #(:name (meta %)) (:testing-vars env)))
      " (" file ":" line ")")))

(defn testing-contexts-str
  "Returns a string representation of the current test context. Joins
  strings in *testing-contexts* with spaces."
  [env]
  (apply str (interpose " " (reverse (:testing-contexts env)))))

(defn inc-report-counter
  "Increments the named counter in *report-counters*, a ref to a map.
  Does nothing if *report-counters* is nil."
  [env name]
  (if (:report-counters env)
    (update-in env [:report-counters name] (fnil inc 0))
    env))

(defmulti
  ^{:doc "Generic reporting function, may be overridden to plug in
   different report formats (e.g., TAP, JUnit).  Assertions such as
   'is' call 'report' to indicate results.  The argument given to
   'report' will be a map with a :type key."
     :dynamic true}
  report (fn [_ m] (:type m)))

(defmethod report :default [env m]
  (prn m))

(defmethod report :pass [env m]
  (inc-report-counter env :pass))

(defmethod report :fail [env m]
  (println "\nFAIL in" (testing-vars-str env m))
  (when (seq (:testing-contexts env))
    (println (testing-contexts-str env)))
  (when-let [message (:message m)] (println message))
  (println "expected:" (pr-str (:expected m)))
  (println "  actual:" (pr-str (:actual m)))
  (inc-report-counter env :fail))

(defmethod report :error [env m]
  (println "\nERROR in" (testing-vars-str env m))
  (when (seq (:testing-contexts env))
    (println (testing-contexts-str env)))
  (when-let [message (:message m)] (println message))
  (println "expected:" (pr-str (:expected m)))
  (print "  actual: ") (prn (:actual m))
  (inc-report-counter env :error))

(defmethod report :summary [env m]
  (println "\nRan" (:test m) "tests containing"
    (+ (:pass m) (:fail m) (:error m)) "assertions.")
  (println (:fail m) "failures," (:error m) "errors.")
  env)

(defmethod report :begin-test-ns [env m]
  (println "\nTesting" (name (:ns m)))
  env)

;; Ignore these message types:
(defmethod report :end-test-ns [env m] env)
(defmethod report :begin-test-var [env m] env)
(defmethod report :end-test-var [env m] env)

(deftype DefaultReporter []
  ITestReporter
  (-do-report [_ env m]
    (report env m)))

(defn default-reporter []
  (DefaultReporter.))

(defn file-and-line
  [exception depth]
  (if-let [stack (.-stack exception)]
    ;; TODO: flesh out
    {:file nil :line nil}
    {:file (.-fileName exception)
     :line (.-lineNumber exception)}))

(defn do-report [env m]
  {:post [(map? %)]}
  (let [m (case (:type m)
            :fail (merge (file-and-line (js/Error.) 1) m)
            :error (merge (file-and-line (:actual m) 0) m)
            m)]
   (-do-report (:reporter env) env m)))

(defn empty-env
  ([] (empty-env (default-reporter)))
  ([reporter]
   {:report-counters {:test 0 :pass 0 :fail 0 :error 0}
    :testing-vars ()
    :testing-contexts ()
    :reporter reporter}))

;; =============================================================================
;; Low-level functions

(defn test-var
  "If v has a function in its :test metadata, calls that function,
   add v to :testing-vars property of env."
  ([v]
   (test-var (empty-env) v))
  ([env v]
   {:pre [(map? env) (instance? Var v)]}
   (let [t (:test (meta v))
         env (-> env
               (update-in [:testing-vars] conj v)
               (update-in [:report-counters :test] inc))]
     (try
       (let [env' (t env)]
         (when (:return env') env'))
       (catch :default e
         (let [env' (do-report env
                      {:type :error
                       :message "Uncaught exception, not in assertion."
                       :expected nil
                       :actual e})]
           (when (:return env') env')))))))

(defn- default-fixture
  "The default, empty, fixture function.  Just calls its argument."
  [f]
  (f))

(defn compose-fixtures
  "Composes two fixture functions, creating a new fixture function
  that combines their behavior."
  [f1 f2]
  (fn [g] (f1 (fn [] (f2 g)))))

(defn join-fixtures
  "Composes a collection of fixtures, in order.  Always returns a valid
  fixture function, even if the collection is empty."
  [fixtures]
  (reduce compose-fixtures default-fixture fixtures))

(defn test-vars
  "Groups vars by their namespace and runs test-vars on them with
  appropriate fixtures applied."
  ([vars] (test-vars (empty-env) vars))
  ([env vars]
   (let [return (:return env)
         env' (reduce
                (fn [env [ns vars]]
                  (let [once-fixture-fn (join-fixtures (:once-fixtures env))
                        each-fixture-fn (join-fixtures (:each-fixtures env))]
                    (once-fixture-fn
                      (fn []
                        (reduce
                          (fn [env v]
                            (if (:test (meta v))
                              (each-fixture-fn (fn [] (test-var env v)))
                              env))
                          env vars)))))
                (assoc env :return true) (group-by (comp :ns meta) vars))]
     (when return env'))))

;; =============================================================================
;; Running Tests, high level functions

(defn successful?
  "Returns true if the given test summary indicates all tests
  were successful, false otherwise."
  [summary]
  (and (zero? (:fail summary 0))
       (zero? (:error summary 0))))
