"use strict";
(async () => {
 const root=document.getElementById("release-list"); if(!root)return;
 try {
  const response=await fetch("https://api.github.com/repos/LeonidYasin/proxydroid/releases?per_page=100",{headers:{Accept:"application/vnd.github+json"}});
  if(!response.ok)throw new Error("GitHub unavailable");
  const all=await response.json(); let count=0;
  for(const prerelease of [true,false]) {
   const release=all.find(r=>!r.draft&&r.prerelease===prerelease&&(r.assets||[]).some(a=>/^proxydroid-v[0-9.]+-android-tv-phone-(universal|armeabi-v7a)-release-signed-(stable|prerelease)-build-[0-9]+-a[0-9]+-g[0-9a-f]{12}\.apk$/.test(a.name)));
   if(!release)continue;
   const box=document.createElement("section");box.className="download";
   const heading=document.createElement("h3");heading.textContent=prerelease?"Test / RC":"Stable";box.append(heading);
   const title=document.createElement("p");title.textContent=release.tag_name;box.append(title);
   for(const asset of release.assets) {
    if(!/^proxydroid-v[0-9.]+-android-tv-phone-(universal|armeabi-v7a)-release-signed-(stable|prerelease)-build-[0-9]+-a[0-9]+-g[0-9a-f]{12}\.apk$/.test(asset.name))continue;
    const url=new URL(asset.browser_download_url);
    if(url.protocol!=="https:"||url.hostname!=="github.com"||!url.pathname.startsWith("/LeonidYasin/proxydroid/releases/download/"))continue;
    const p=document.createElement("p"),a=document.createElement("a");a.href=url.href;a.textContent=(asset.name.includes("-armeabi-v7a-")?"ARM32 / armeabi-v7a":"Universal")+" · "+(asset.size/1e6).toFixed(1)+" МБ";p.append(a);box.append(p);
   }
   root.append(box);count++;
  }
  document.getElementById("release-status").textContent=count?"Опубликованные подписанные APK из официального GitHub Releases. Stable и Test показаны раздельно.":"Подходящий подписанный релиз пока не опубликован. Откройте Releases.";
 } catch(e){document.getElementById("release-status").textContent="Каталог GitHub временно недоступен. Скачайте APK на странице Releases ниже.";}
})();
