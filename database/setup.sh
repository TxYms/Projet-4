#!/usr/bin/env bash
# Installe la base PostgreSQL de DataShare.
#
# Usage :
#   ./database/setup.sh                       # valeurs par défaut (développement)
#   DB_NAME=datashare DB_USER=datashare DB_PASSWORD='***' PGHOST=db.example.org PGUSER=postgres ./database/setup.sh
#
# Variables :
#   DB_NAME, DB_USER, DB_PASSWORD  base et compte applicatif à créer
#   PGHOST, PGPORT, PGUSER         connexion administrateur (variables standard de psql)
#   PSQL                           commande psql à utiliser (ex. "sudo -u postgres psql" sous Linux)
set -euo pipefail

DB_NAME="${DB_NAME:-datashare}"
DB_USER="${DB_USER:-datashare}"
DB_PASSWORD="${DB_PASSWORD:-datashare}"
PSQL="${PSQL:-psql}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if [[ "$DB_PASSWORD" == "datashare" ]]; then
  echo "Attention : mot de passe par défaut, à réserver au développement local." >&2
fi

echo "Création du rôle « $DB_USER » et de la base « $DB_NAME »…"
$PSQL -v db_name="$DB_NAME" -v db_user="$DB_USER" -v db_password="$DB_PASSWORD" -d postgres -f - < "$SCRIPT_DIR/setup.sql"

echo "Base prête. Le schéma sera créé par Flyway au premier démarrage de l'API."
echo "Variables à fournir à l'API : DB_URL=jdbc:postgresql://${PGHOST:-localhost}:${PGPORT:-5432}/$DB_NAME DB_USER=$DB_USER DB_PASSWORD=…"
