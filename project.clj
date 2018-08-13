(defproject todo-list-clj "0.1.0-SNAPSHOT"
  :description "A todo list HTTP API to demonstrate basic clojure knowledge"
  :url "http://github.com/lag13/todo-list-clj"
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [ring/ring-core "1.7.0-RC1"]
                 [compojure "1.6.1"]
                 [ring/ring-json "0.4.0"]
                 [org.clojure/java.jdbc "0.7.7"]
                 [mysql/mysql-connector-java "8.0.11"]
                 [com.taoensso/timbre "4.10.0"]]
  :plugins [[lein-ring "0.12.4"]]
  :ring {:handler todo-list-clj.handler/app
         :port 8080})
