Создай issue для задачи: $ARGUMENTS

1. Напиши краткий черновик: заголовок + поле `**Тип:**` (`FEATURE | BUGFIX | REFACTOR | INFRA | DOCS | DEPENDENCY`) + 2-4 предложения контекста + DoD-чеклист + предполагаемый `size:` (S — до 4 часов, M — до одного дня, L — до трёх дней). Покажи пользователю, жди апрува.
2. Создай issue через `gh issue create --body-file /tmp/issue-body.md --label size:<S|M|L>`.
3. Если указан parent — привяжи через GraphQL `addSubIssue`.
4. Верни номер и URL созданного issue.

Не задавай уточняющих вопросов, если из описания всё понятно. Черновик — в свободной форме, без elaborate шаблонов.
