(ns swarmpit.api
  (:require [clojure.core.memoize :as memo]
            [clojure.set :refer [rename-keys]]
            [swarmpit.docker.client :as dc]
            [swarmpit.docker.mapper.inbound :as dmi]
            [swarmpit.docker.mapper.outbound :as dmo]
            [swarmpit.dockerauth.client :as dac]
            [swarmpit.dockerregistry.client :as drc]
            [swarmpit.dockerhub.client :as dhc]
            [swarmpit.dockerhub.mapper.inbound :as dhmi]
            [swarmpit.registry.client :as rc]
            [swarmpit.registry.mapper.inbound :as rmi]
            [swarmpit.couchdb.client :as cc]
            [swarmpit.couchdb.mapper.inbound :as cmi]
            [swarmpit.couchdb.mapper.outbound :as cmo]))

;;; User API

(defn users
  []
  (-> (cc/users)
      (cmi/->users)))

(defn user
  [user-id]
  (-> (cc/user user-id)
      (cmi/->user)))

(defn user-by-credentials
  [credentails]
  (cc/user-by-credentials (:username credentails)
                          (cmo/->password (:password credentails))))

(defn user-by-username
  [username]
  (cc/user-by-username username))

(defn user-exist?
  [user]
  (some? (user-by-username (:username user))))

(defn delete-user
  [user-id]
  (->> (user user-id)
       (cc/delete-user)))

(defn create-user
  [user]
  (if (not (user-exist? user))
    (->> (cmo/->user user)
         (cc/create-user))))

(defn change-password
  [user password]
  (->> (cmo/->password password)
       (cc/change-password user)))

;;; Registry API

(defn registries
  []
  (-> (cc/registries)
      (cmi/->registries)))

(defn registries-sum
  []
  (->> (registries)
       (map :name)
       (into [])))

(defn registry
  [registry-id]
  (-> (cc/registry registry-id)
      (cmi/->registry)))

(defn registry-by-name
  [registry-name]
  (cc/registry-by-name registry-name))

(defn registry-exist?
  [registry]
  (some? (registry-by-name (:name registry))))

(defn registry-valid?
  [registry]
  (some?
    (try
      (rc/info registry)
      (catch Exception _))))

(defn delete-registry
  [registry-id]
  (->> (registry registry-id)
       (cc/delete-registry)))

(defn create-registry
  [registry]
  (if (not (registry-exist? registry))
    (->> (cmo/->registry registry)
         (cc/create-registry))))

;;; Secret API

(defn secrets
  []
  (-> (dc/secrets)
      (dmi/->secrets)))

(defn secret
  [secret-id]
  (-> (dc/secret secret-id)
      (dmi/->secret)))

(defn delete-secret
  [secret-id]
  (dc/delete-secret secret-id))

(defn create-secret
  [secret]
  (-> (dmo/->secret secret)
      (dc/create-secret)
      (rename-keys {:ID :id})))

(defn update-secret
  [secret-id secret]
  (let [secret-version (:version secret)]
    (->> (dmo/->secret secret)
         (dc/update-secret secret-id secret-version))))

;;; Network API

(defn networks
  []
  (-> (dc/networks)
      (dmi/->networks)))

(defn network
  [network-id]
  (-> (dc/network network-id)
      (dmi/->network)))

(defn delete-network
  [network-id]
  (dc/delete-network network-id))

(defn create-network
  [network]
  (->> (dmo/->network network)
       (dc/create-network)))

;;; Volume API

(defn volumes
  []
  (-> (dc/volumes)
      (dmi/->volumes)))

(defn volume
  [volume-name]
  (-> (dc/volume volume-name)
      (dmi/->volume)))

(defn delete-volume
  [volume-name]
  (dc/delete-volume volume-name))

(defn create-volume
  [volume]
  (->> (dmo/->volume volume)
       (dc/create-volume)
       (dmi/->volume)))

;;; Node API

(defn nodes
  []
  (-> (dc/nodes)
      (dmi/->nodes)))

(defn node
  [node-id]
  (-> (dc/node node-id)
      (dmi/->node)))

;;; Repository Dockerhub API

(defn dockerusers
  []
  (-> (cc/docker-users)
      (cmi/->dockerusers)))

(defn dockerusers-sum
  []
  (->> (dockerusers)
       (map :username)
       (into [])))

(defn dockeruser-info
  [dockeruser]
  (dhc/info dockeruser))

(defn dockeruser-login
  [dockeruser]
  (dhc/login dockeruser))

(defn dockeruser-by-username
  [docker-username]
  (cc/docker-user-by-name docker-username))

(defn dockeruser-exist?
  [dockeruser]
  (some? (dockeruser-by-username (:username dockeruser))))

(defn dockeruser
  [dockeruser-id]
  (-> (cc/docker-user dockeruser-id)
      (cmi/->dockeruser)))

(defn create-dockeruser
  [dockeruser dockeruser-info]
  (if (not (dockeruser-exist? dockeruser))
    (->> (cmo/->docker-user dockeruser dockeruser-info)
         (cc/create-docker-user))))

(defn delete-dockeruser
  [dockeruser-id]
  (-> (dockeruser dockeruser-id)
      (cc/delete-docker-user)))

(defn dockeruser-repositories
  [dockeruser]
  (let [user-token (:token (dhc/login dockeruser))]
    (->> (dhc/namespaces user-token)
         :namespaces
         (map #(:results (dhc/repositories-by-namespace user-token %)))
         (flatten)
         (dhmi/->user-repositories)
         (filter #(:private %)))))

(defn dockerhub-repositories
  [repository-query repository-page]
  (-> (dhc/repositories repository-query repository-page)
      (dhmi/->repositories repository-query repository-page)))

(defn dockerhub-tags
  [repository-name repository-user]
  (let [user (dockeruser-by-username repository-user)
        token (:token (dac/token user repository-name))]
    (->> (drc/tags token repository-name)
         :tags)))

;;; Repository V2 API

(defn repositories
  [registry]
  (->> (rc/repositories registry)
       (rmi/->repositories)))

(defn tags
  [registry repository-name]
  (->> (rc/tags registry repository-name)
       :tags))

;;; Service API

(defn services
  []
  (dmi/->services (dc/services)
                  (dc/tasks)
                  (dc/nodes)
                  (dc/networks)))

(defn service
  [service-id]
  (dmi/->service (dc/service service-id)
                 (dc/tasks)
                 (dc/nodes)
                 (dc/networks)))

(defn delete-service
  [service-id]
  (dc/delete-service service-id))

(defn service-registry
  [service]
  (get-in service [:repository :registry]))

(defn create-dockerhub-service
  [service secrets]
  (let [auth-config (->> (get-in service [:repository :user])
                         (dockeruser-by-username)
                         (dmo/->auth-config))]
    (->> (dmo/->service-image service)
         (dmo/->service service secrets)
         (dc/create-service auth-config))))

(defn create-registry-service
  [service registry secrets]
  (let [registry (registry-by-name registry)
        auth-config (dmo/->auth-config registry)]
    (->> (dmo/->service-image-registry service registry)
         (dmo/->service service secrets)
         (dc/create-service auth-config))))

(defn create-service
  [service]
  (let [registry (get-in service [:repository :registry])
        secrets (dmo/->service-secrets service (secrets))]
    (rename-keys
      (if (= "dockerhub" registry)
        (create-dockerhub-service service secrets)
        (create-registry-service service registry secrets)) {:ID :id})))

(defn update-service
  [service-id service]
  (let [secrets (dmo/->service-secrets service (secrets))]
    (->> (dmo/->service-image service)
         (dmo/->service service secrets)
         (dc/update-service service-id (:version service)))))

;;; Task API

(defn tasks
  []
  (->> (services)
       (map #(:tasks %))
       (flatten)))

(defn task
  [task-id]
  (->> (tasks)
       (filter #(= (:id %) task-id))
       (first)))