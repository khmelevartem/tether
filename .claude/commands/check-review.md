просмотри последние комментарии к ПР, над которым ты работаешь. оцени, какие из них валидны, исправь валидные. если есть невалидные, ответь на них, почему так считаешь. запушь изменения.

## Важно: ответы на комментарии

Если у комментария есть открытый тред (inline review comment или discussion thread) — отвечай **в самом треде через GitHub API**, а не в чате. Используй:

```bash
# Ответ на inline review comment (pulls/comments)
gh api repos/OWNER/REPO/pulls/PR/comments \
  -X POST \
  -F in_reply_to=COMMENT_ID \
  -F body="текст ответа"

# Ответ на общий PR comment (issues/comments)
gh api repos/OWNER/REPO/issues/PR/comments \
  -X POST \
  -F body="текст ответа"
```

Объяснения и обоснования пиши именно там — ревьюер получит нотификацию и увидит ответ в контексте кода.
