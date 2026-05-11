#!/usr/bin/env bash
# PreToolUse hook: injects a short technical checklist before `git commit`.
# Reads tool input JSON from stdin, writes hookSpecificOutput JSON to stdout.

set -euo pipefail

input=$(cat)
cmd=$(printf '%s' "$input" | sed -n 's/.*"command"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')

case "$cmd" in
  *"git commit"*) ;;
  *) exit 0 ;;
esac

read -r -d '' CHECKLIST <<'EOF' || true
Pre-commit checklist (быстрая проверка перед `git commit`):

- [ ] commit message: `#<issue>: ...` (issue существует?)
- [ ] правки внутри worktree (`.claude/worktrees/<branch>/`), не в корне
- [ ] новый код в `commonMain`, если не нужен platform API → docs/engineering/architecture-principles.md
- [ ] правильный source set / hierarchy (`jvmMain` > android+desktop, `appleMain` > ios+macos) → docs/engineering/modules.md
- [ ] DI: компонент зарегистрирован и резолвится через граф, без ручных `new`/`object` → docs/engineering/dependency-injection.md
- [ ] UI/state — по слоям presentation, без бизнес-логики во вьюхах → docs/engineering/presentation-layer.md
- [ ] тесты добавлены/обновлены, гоняются `./gradlew allTests -q` → docs/engineering/testing.md
- [ ] комментарии минимальны (приватные методы вместо пояснений)
- [ ] KtLint руками НЕ запускался, форматирование не правил руками
- [ ] runtime-changing PR? — `/smoke-test` перед merge
- [ ] странное поведение платформы? — сначала загляни в `docs/knowledge/`

Сомневаешься в архитектурном решении — открой соответствующий файл из `docs/engineering/` прежде чем коммитить.
EOF

# Emit additionalContext for the model.
python3 - "$CHECKLIST" <<'PY'
import json, sys
ctx = sys.argv[1]
print(json.dumps({
    "hookSpecificOutput": {
        "hookEventName": "PreToolUse",
        "additionalContext": ctx,
    }
}))
PY
