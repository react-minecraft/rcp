#!/usr/bin/env bash
# Prints release notes: the commits since the previous v* tag, grouped by their conventional prefix.
set -euo pipefail

previous=$(git describe --tags --abbrev=0 --match 'v*' HEAD^ 2>/dev/null || true)
range=${previous:+$previous..}HEAD

declare -A titles=(
  [feat]="✨ Новое"
  [fix]="🐛 Исправления"
  [refactor]="♻️ Рефакторинг"
  [other]="📦 Прочее"
)
declare -A groups=()
pattern='^(feat|fix|refactor)(\([^)]*\))?!?:[[:space:]]*(.*)$'

while IFS=$'\t' read -r hash subject; do
  subject=${subject//\[release\]/}
  subject=$(echo "$subject" | sed -E 's/[[:space:]]+$//')
  [ -z "$subject" ] && continue
  if [[ $subject =~ $pattern ]]; then
    type=${BASH_REMATCH[1]}
    scope=${BASH_REMATCH[2]}
    text=${BASH_REMATCH[3]}
    line="- ${scope:+**${scope:1:-1}**: }$text (\`$hash\`)"
  else
    type=other
    line="- $subject (\`$hash\`)"
  fi
  groups[$type]+="$line"$'\n'
done < <(git log --no-merges --format='%h%x09%s' "$range")

for type in feat fix refactor other; do
  if [ -n "${groups[$type]:-}" ]; then
    printf '### %s\n\n%s\n' "${titles[$type]}" "${groups[$type]}"
  fi
done

if [ -n "$previous" ]; then
  echo "**Все изменения**: https://github.com/${GITHUB_REPOSITORY}/compare/${previous}...v${VERSION}"
fi
