-- Datomic's bin/sql/postgres-table.sql without WITH (OIDS=FALSE), which Postgres 12+ rejects.
-- Two storage databases: "datomic" holds the original, "restored" starts empty for the restore.
CREATE TABLE datomic_kvs (id text NOT NULL, rev integer, map text, val bytea, CONSTRAINT pk_id PRIMARY KEY (id));
CREATE DATABASE restored OWNER datomic;
\connect restored
CREATE TABLE datomic_kvs (id text NOT NULL, rev integer, map text, val bytea, CONSTRAINT pk_id PRIMARY KEY (id));
