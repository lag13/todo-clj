(ns todo-list-clj.handler
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.middleware.params :as params]
            [ring.middleware.json :as json]
            [clojure.java.jdbc :as jdbc]
            [taoensso.timbre :as timbre]))

(def db-spec
  {:dbtype "mysql"
   ;; https://dev.mysql.com/doc/connector-j/8.0/en/connector-j-api-changes.html
   :classname "com.mysql.cj.jdbc.Driver"
   :host "127.0.0.1"
   :dbname "todos"
   :user "todouser"
   :password "todopassword"
   ;; https://dev.mysql.com/doc/connector-j/8.0/en/connector-j-reference-configuration-properties.html
   :zeroDateTimeBehavior "CONVERT_TO_NULL"})

;; TODO: Make sure that I return UTC time for the dates. For that
;; matter, make sure that the database stores time in UTC.

;; TODO: Make sure to validate the json body and such.

;; TODO: I messed up some syntax (let [akj lkj] [(str "select *"])...
;; and when I made a request a big ring stack trace was returned as a
;; response. I thought my exception catching middleware would have
;; caught this but it did not. I don't like even the remote
;; possibility of this happening so how can we prevent it?

(defroutes app-routes
  ;; TODO: Add query parameter filtering like all todos created before
  ;; a certain date.
  (GET "/todos" [completed]
       (let [completed-query (if (nil? completed)
                               "WHERE completed = 0"
                               "WHERE completed = 1")]
         
         (jdbc/query db-spec [(str "SELECT * FROM todos " completed-query)])))
  (POST "/todos" {body :body}
        (let [result (jdbc/insert! db-spec :todos {:todo (get body "todo")})]
          {:body {:id (:generated_key (first result))}}))
  (PUT "/todos/:id" [id :as {body :body}]
       (let [update-col (if (nil? body)
                     {:completed true}
                     {:todo (get body "todo")})]
         (jdbc/update! db-spec :todos update-col ["id = ?" id])))
  (DELETE "/todos/:id" [id] (jdbc/delete! db-spec :todos ["id = ?" id])))

;; TODO: In general, what is idiomatic error handling in clojure? To
;; me (who is looking at things with new eyes) it seems like the wild
;; west where some people are fine with exceptions, others have taken
;; a haskell'y approach with the addition of some higher order types,
;; and others still might just return a tuple of (result error) to
;; indicate if something has gone wrong:
;; https://stackoverflow.com/questions/27742623/idiomatic-error-handling-in-clojure
;; https://adambard.com/blog/acceptable-error-handling-in-clojure/
;; https://8thlight.com/blog/mike-knepper/2015/05/19/handling-exceptions-with-middleware-in-clojure.html

;; TODO: How did I validate that the json body had "data" and stuff
;; like that in my go APIs?

;; TODO: Check if I can still slurp the body if a handler reads the
;; body and throws an excpetion.
(defn wrap-exception-catching [handler]
  (fn [req]
    (try
      (handler req)
      (catch Exception e
        (let [modified-req (-> req
                               (update-in [:headers] dissoc "authorization")
                               ;; TODO: Feels like I should read a
                               ;; limited portion of the body. I just
                               ;; don't like having code that deals
                               ;; with "infinites" I guess (i.e. slurp
                               ;; will read *all* of whatever you give
                               ;; it so will something bad happen if a
                               ;; ridiculously big body is sent in the
                               ;; request?)
                               (assoc :body (slurp (:body req))))]
          (timbre/error e "got exception:" (.getMessage e) "\non request (omitted authorization header for security purposes):" modified-req)
          {:status 500 :body "an unexpected error ocurred, it will try to be resolved shortly"})))))

(defmacro <-
  "Transforms a form like this:

      (<- wrap-fn1
          wrap-fn2
          wrap-fn3
          fn)

  to this:

     (wrap-fn1 (wrap-fn2 (wrap-fn3 fn)))

  While -> makes more readable a series of function calls on a value
  <- makes more readable a series of function calls that produce a new
  function because the left-to-right order of the function arguments
  reflect the order that each function gets invoked in the resulting
  function."
  ;; TODO: I did [x & forms] rather than just [& forms] because then
  ;; if you make a mistake and make this macro call (<-) the error
  ;; message will be something like "Wrong number (0) passed to: <-".
  ;; But if I just have [& forms] it would say something like "Wrong
  ;; number (0) passed to: core/->" which seems confusing. Is trying
  ;; to make the compilation error message less confusing the right
  ;; approach when making a macro?
  [x & forms]
  `(-> ~@(reverse forms) ~x))

(defroutes app
  (<- wrap-exception-catching
      json/wrap-json-response
      params/wrap-params
      json/wrap-json-body
      app-routes)
  (route/not-found "resource does not exist"))
