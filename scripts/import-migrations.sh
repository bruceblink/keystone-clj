#!/usr/bin/env bash
set -euo pipefail

source_dir="${1:-}"
if [[ -z "$source_dir" ]]; then
  echo "Usage: bash scripts/import-migrations.sh /path/to/Keystone" >&2
  exit 2
fi

upstream="$source_dir/keystone-infrastructure/src/main/resources/db/migrate"
if [[ ! -d "$upstream/common" || ! -d "$upstream/mysql" ]]; then
  echo "Cannot find Keystone Flyway directories at $upstream" >&2
  exit 2
fi

cd "$(dirname "$0")/.."
mkdir -p resources/db/migrate/common resources/db/migrate/mysql
cp -a "$upstream/common/." resources/db/migrate/common/
cp -a "$upstream/mysql/." resources/db/migrate/mysql/
echo "Imported upstream Flyway files. Review before enabling APP_MIGRATE."
