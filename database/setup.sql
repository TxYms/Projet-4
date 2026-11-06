-- DataShare : création du rôle et des bases PostgreSQL.
-- Exécuté par setup.sh avec un compte administrateur PostgreSQL.
-- Le schéma (tables, index) est ensuite créé par Flyway au démarrage de l'API :
-- backend/src/main/resources/db/migration/

\set ON_ERROR_STOP on

SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'db_user', :'db_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'db_user')
\gexec

SELECT format('CREATE DATABASE %I OWNER %I ENCODING ''UTF8''', :'db_name', :'db_user')
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = :'db_name')
\gexec

-- L'application n'a besoin d'aucun droit en dehors de sa propre base
REVOKE ALL ON DATABASE :"db_name" FROM PUBLIC;
GRANT CONNECT, TEMPORARY ON DATABASE :"db_name" TO :"db_user";
