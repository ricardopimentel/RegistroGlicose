const CACHE_NAME = 'glucose-tracker-v1';
const ASSETS = [
    './',
    './index.html',
    './src/css/index.css',
    './src/js/main.js',
    './src/js/db.js',
    './src/js/export.js'
];

self.addEventListener('install', (e) => {
    e.waitUntil(
        caches.open(CACHE_NAME).then(cache => cache.addAll(ASSETS))
    );
});

self.addEventListener('fetch', (e) => {
    e.respondWith(
        caches.match(e.request).then(res => res || fetch(e.request))
    );
});

// Handle Notification Click
self.addEventListener('notificationclick', (event) => {
    event.notification.close();
    event.waitUntil(
        clients.openWindow('/')
    );
});
