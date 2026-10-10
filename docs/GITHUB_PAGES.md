# Сайт проекта — GitHub Pages

Адрес: https://leonidyasin.github.io/proxydroid/

Сайт строится из **тех же Markdown-файлов**, что README и документация репозитория. `scripts/build_site.py` преобразует их в статические HTML-страницы, добавляет общую навигацию, локальные стили и интерактивный mind map. Отдельные копии инструкций вручную не поддерживаются. Mermaid/text карта доступна как текст; интерактивная карта — отдельная HTML-страница без внешних CDN.

Публикация: `.github/workflows/pages.yml` → build → configure-pages → upload-pages-artifact → deploy-pages. GitHub Pages настроен на **GitHub Actions**, HTTPS. Workflow выполняется для main и текущей рабочей ветки при изменении сайта/docs. До merge описание честно отмечает ветку и RC, не утверждает, что функции уже в main. Environment github-pages допускает только эти ветки; deployment concurrency не позволяет одновременное перезаписывание.

Локально: `python3 -m pip install -r site/requirements.txt && python3 scripts/build_site.py`. Результат `_site/`, его не надо коммитить. Проверки включают наличие всех ссылочных локальных страниц и отсутствие приватных файлов в output. Публикуются только HTML/CSS/JS/favicon и .nojekyll; APK скачиваются непосредственно из официальных Releases, ключи/кэш/логи в сайт не попадают.

Главная содержит назначение, быстрый старт, возможности/ограничения, Stable/Test, ссылки скачивания, changelog, полную документацию и roadmap. Список актуальных подписанных APK загружается из публичного GitHub API; при лимите/ошибке остаётся ссылка Releases. Никаких API токенов/аналитики/cookies, сторонних шрифтов или трекеров. Это не updater Android: сайт не устанавливает приложение.

UI: responsive desktop/mobile, keyboard focus, 44px controls, dark theme по настройкам системы, reduced motion. Изменение README/документации запускает rebuild автоматически.
