#!/usr/bin/env python3
"""Build a static, token-free Pages site from canonical repository Markdown."""
from pathlib import Path
import html,re,shutil
import markdown
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'_site'
DOCS=[('README.md','readme','README / руководство'),('AUTO_GATEWAY.md','auto-gateway','Автошлюз и Host'),('docs/BOOT_AUTOSTART.md','boot','Автозапуск приставки'),('docs/INTERNET_YOUTUBE.md','youtube','Интернет и YouTube'),('docs/APP_UPDATES.md','updates','Обновление и установка APK'),('docs/ARCHITECTURE.md','architecture','Архитектура'),('docs/MINDMAP.md','mindmap-text','Карта проекта / текст'),('ROADMAP.md','roadmap','Roadmap / LAN discovery'),('docs/ANDROID_TV_REVIEW.md','qa','TV и тестирование'),('docs/RELEASE_SIGNING.md','signing','Подпись и релизы'),('docs/CHANGELOG.md','changelog','Changelog'),('privacy_policy.md','privacy','Конфиденциальность'),('docs/GITHUB_PAGES.md','pages','GitHub Pages'),('docs/SESSION-2026-09-30.md','archive','Архив 2026-09-30')]
PATHS={str((ROOT/src).resolve()): slug+'.html' for src,slug,_ in DOCS}
PATHS[str((ROOT/'docs/MINDMAP.html').resolve())]='mindmap.html'
if OUT.exists():shutil.rmtree(OUT)
OUT.mkdir()
for f in ('style.css','releases.js'):shutil.copyfile(ROOT/'site'/f,OUT/f)
(OUT/'.nojekyll').write_text('')
logo='<svg width="28" height="28" viewBox="0 0 28 28" aria-hidden="true"><rect x="2" y="2" width="24" height="24" rx="6" fill="none" stroke="currentColor" stroke-width="2"/><path d="M8 14h12m-5-5 5 5-5 5" fill="none" stroke="currentColor" stroke-width="2"/></svg>'
nav='<header><nav aria-label="Главная навигация"><a class="brand" href="index.html">'+logo+'ProxyDroid</a><a href="index.html#download">Скачать</a><a href="readme.html">Документация</a><a href="mindmap.html">Карта</a><a href="roadmap.html">Roadmap</a><a href="https://github.com/LeonidYasin/proxydroid">GitHub ↗</a></nav></header>'
footer='<footer><div class="wrap">ProxyDroid · Android TV / Phone · GPL-3.0-or-later · <a href="privacy.html">Конфиденциальность</a><br>Документация рабочей ветки fix/android-tv-dpad-default-port. RC ≠ stable; LAN discovery — план.</div></footer>'
def page(title,body,script=''):
 return '<!doctype html><html lang="ru"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><meta name="description" content="ProxyDroid: прокси VPN для Android TV, настройка пультом, автошлюз, signed APK и документация."><meta name="color-scheme" content="light dark"><title>'+html.escape(title)+' · ProxyDroid</title><link rel="stylesheet" href="style.css"></head><body><a class="skip" href="#main">К содержанию</a>'+nav+'<main id="main" class="wrap">'+body+'</main>'+footer+script+'</body></html>'
for src,slug,title in DOCS:
 text=(ROOT/src).read_text()
 rendered=markdown.markdown(text,extensions=['fenced_code','tables','sane_lists'])
 def link(match):
  original=html.unescape(match[1]); base,sep,anchor=original.partition('#')
  if not base or re.match(r'^[a-z]+:',base):return match[0]
  resolved=str((ROOT/src).parent.joinpath(base).resolve())
  if resolved in PATHS:return 'href="'+PATHS[resolved]+(('#'+anchor) if sep else '')+'"'
  return 'href="https://github.com/LeonidYasin/proxydroid/blob/fix/android-tv-dpad-default-port/'+html.escape(str(Path(resolved).relative_to(ROOT)))+(('#'+anchor) if sep else '')+'"'
 rendered=re.sub(r'href="([^"]+)"',link,rendered)
 rendered=re.sub(r'(<h[1-6]>)(.*?)(</h[1-6]>)',lambda m:m[1][:-1]+' id="'+re.sub(r'[^\w -]','',re.sub('<[^>]*>','',html.unescape(m[2]))).strip().lower().replace(' ','-')+'">'+m[2]+m[3],rendered)
 rendered=rendered.replace('<table>','<div class="tablewrap"><table>').replace('</table>','</table></div>')
 if slug=='mindmap-text':rendered='<p><a class="button" href="mindmap.html">Открыть интерактивную карту</a></p>'+rendered
 sidebar='<aside class="sidenav" aria-label="Разделы документации">'+''.join('<a href="'+s+'.html"'+(' aria-current="page"' if s==slug else '')+'>'+html.escape(t)+'</a>' for _,s,t in DOCS)+'</aside>'
 (OUT/(slug+'.html')).write_text(page(title,'<div class="doclayout">'+sidebar+'<article class="article">'+rendered+'</article></div>'))
maptext=(ROOT/'docs/MINDMAP.html').read_text();maptext=maptext.replace('</style>','</style><p style="margin:16px 24px"><a href="index.html">← Сайт ProxyDroid</a> · <a href="readme.html">Документация</a></p>',1)
maptext=maptext.replace('https://github.com/LeonidYasin/proxydroid/blob/fix/android-tv-dpad-default-port/docs/MINDMAP.md','mindmap-text.html').replace('https://github.com/LeonidYasin/proxydroid/blob/fix/android-tv-dpad-default-port/README.md','readme.html').replace('https://github.com/LeonidYasin/proxydroid/blob/fix/android-tv-dpad-default-port/ROADMAP.md','roadmap.html')
(OUT/'mindmap.html').write_text(maptext)
doclinks=''.join('<li><a href="'+slug+'.html">'+title+' →</a></li>' for _,slug,title in DOCS if slug!='archive')
body='''<section class="hero"><div><div class="eyebrow">Android TV / Phone · без root</div><h1>Ваш прокси.<br>Управление с пульта.</h1><p class="lead">IPv4/TCP и DNS через SOCKS или HTTP-прокси. Понятная настройка на приставке, выбор приложений и подписанные обновления.</p><div class="actions"><a class="button" href="#download">Скачать APK ↓</a><a class="button secondary" href="readme.html">Начать настройку →</a></div><p class="muted">Android 7.0+ · порт по умолчанию 10808 · ARM32 и universal</p></div><aside class="diagram" aria-label="Схема маршрутизации"><strong>Как идёт трафик</strong><div class="node">Приложения на приставке</div><div class="arrow" aria-hidden="true">↓</div><div class="node">ProxyDroid · Android VPN → Rust TUN</div><div class="arrow" aria-hidden="true">↓</div><div class="node">Ваш SOCKS / HTTP-прокси → интернет</div><p class="muted" style="font-size:14px;margin:16px 0 0">Прокси-сервер должен быть доступен в вашей сети. Шлюз сам по себе не обязательно является прокси.</p></aside></section><div class="notice"><p><strong>Статус проекта: 3.8.1 в рабочей ветке, релизы Test / RC.</strong> Изменения пока не в main. LAN discovery запланирован; холодная загрузка, YouTube и установщик конкретной ТВ-прошивки требуют проверки на устройстве.</p></div><section class="section"><h2>От установки к подключению</h2><div class="grid"><div class="step"><strong class="number">01 / УСТАНОВКА</strong><h3>Выберите свой APK</h3><p>ARM32 меньше по размеру. Если архитектура приставки неизвестна — скачайте universal. Подпись одна для всех release APK.</p><a href="signing.html">Версии и подпись →</a></div><div class="step"><strong class="number">02 / НАСТРОЙКА</strong><h3>Host → 10808 → Connect</h3><p>Auto gateway подставляет адрес шлюза в Host. Редактирование начинается по OK, а не при наведении пульта.</p><a href="auto-gateway.html">Автошлюз →</a></div><div class="step"><strong class="number">03 / ИСПОЛЬЗОВАНИЕ</strong><h3>Проверьте интернет</h3><p>После HTTPS-проверки через активный прокси появляется кнопка YouTube. Это не гарантия воспроизведения всех видео.</p><a href="youtube.html">YouTube и ограничения →</a></div></div></section><section id="download" class="section"><h2>Скачать подписанный APK</h2><p id="release-status" class="muted">Загружаем публичный каталог GitHub. Если он недоступен — используйте страницу Releases.</p><div id="release-list" class="downloads"></div><div class="actions"><a class="button secondary" href="https://github.com/LeonidYasin/proxydroid/releases">Все релизы на GitHub ↗</a><a href="updates.html" style="align-self:center">Обновление через меню →</a></div><p>В приложении: <strong>⋮ → Update app → Test → Download APK</strong>. После проверки открывается установщик Android. Полный путь файла в диалоге позволяет повторить установку.</p></section><section class="section"><h2>Возможности — и честные границы</h2><div class="grid"><div><h3>Профили и приложения</h3><p>SOCKS5, SOCKS4a, HTTP(S) CONNECT. Выбор профиля, allow-list или bypass-list приложений.</p></div><div><h3>Запуск приставки</h3><p>Start on boot ждёт сеть и прокси до 120 секунд. Нужны ранее выданное VPN-согласие и разрешённый OEM-автозапуск.</p><a href="boot.html">Диагностика автозапуска →</a></div><div><h3>Не реализовано</h3><p>Общий UDP/QUIC, полный IPv6-туннель и LAN discovery. Не обещаем полноценные legacy NTLM/PAC/SSID режимы.</p><a href="roadmap.html">Что дальше →</a></div></div></section><section class="section"><h2>Вся документация в одном месте</h2><p>Страницы сайта автоматически строятся из README и документов репозитория. Настройка, архитектура, безопасность, тесты и планы не расходятся по разным копиям.</p><ul class="doclinks">'''+doclinks+'''</ul><div class="actions"><a class="button secondary" href="mindmap.html">Интерактивная карта проекта →</a><a href="archive.html" style="align-self:center">Архив разработки</a></div></section>'''
props=dict(line.strip().split('=',1) for line in (ROOT/'version.properties').read_text().splitlines() if line.strip() and not line.lstrip().startswith('#'))
version='.'.join(props['VERSION_'+part] for part in ('MAJOR','MINOR','PATCH'))
body=body.replace('3.8.1 в рабочей ветке',version+' в рабочей ветке')
(OUT/'index.html').write_text(page('Прокси для Android TV',body,'<script src="releases.js" defer></script>'))
from html.parser import HTMLParser
class Links(HTMLParser):
 def handle_starttag(self,tag,attrs):
  for key,value in attrs:
   if key not in ('href','src') or not value or value.startswith(('#','http:','https:','data:','mailto:')):continue
   assert (OUT/value.split('#')[0]).exists(),f'Broken site link: {value}'
for p in OUT.glob('*.html'):Links().feed(p.read_text())
assert all(p.suffix in ('.html','.css','.js','') for p in OUT.iterdir())
print('PASS:',len(list(OUT.glob('*.html'))),'HTML routes, links checked; no private files')
