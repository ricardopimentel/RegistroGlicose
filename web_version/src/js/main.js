import { db } from './db.js';
import { exportToCSV } from './export.js';

// Elements
const viewDashboard = document.getElementById('view-dashboard');
const viewHistory = document.getElementById('view-history');
const viewSettings = document.getElementById('view-settings');
const viewTitle = document.getElementById('view-title');
const navItems = document.querySelectorAll('.nav-item');
const fab = document.getElementById('fab');
const modal = document.getElementById('entry-modal');
const btnCancel = document.getElementById('cancel-entry');
const btnSave = document.getElementById('save-entry');
const btnExport = document.getElementById('export-btn');

let currentView = 'dashboard';
let chart = null;

// Initialize
window.addEventListener('DOMContentLoaded', () => {
    lucide.createIcons();
    renderDashboard();
    setupNavigation();
    setupModal();
    setupExport();
    startNotificationLoop();
});

function startNotificationLoop() {
    setInterval(checkReminders, 60000); // Every minute
    checkReminders();
}

function checkReminders() {
    const settings = db.getSettings();
    const now = new Date();
    const currentTime = `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`;

    settings.notifications.forEach(n => {
        if (n.enabled && n.time === currentTime) {
            showNotification(n.time);
        }
    });
}

function showNotification(time) {
    if (Notification.permission === 'granted') {
        new Notification('Lembrete de Glicose', {
            body: `Está na hora da sua medição das ${time}. Clique para registrar.`,
            icon: 'https://cdn-icons-png.flaticon.com/512/3063/3063822.png',
            vibrate: [200, 100, 200]
        });
    }
}

function setupNavigation() {
    navItems.forEach(item => {
        item.addEventListener('click', (e) => {
            e.preventDefault();
            const view = item.getAttribute('data-view');
            switchView(view);
        });
    });
}

function switchView(view) {
    currentView = view;
    
    // Update active state
    navItems.forEach(item => {
        item.classList.toggle('active', item.getAttribute('data-view') === view);
    });

    // Toggle views
    viewDashboard.style.display = view === 'dashboard' ? 'block' : 'none';
    viewHistory.style.display = view === 'history' ? 'block' : 'none';
    viewSettings.style.display = view === 'settings' ? 'block' : 'none';

    // Update title
    const titles = { dashboard: 'Dashboard', history: 'Histórico', settings: 'Ajustes' };
    viewTitle.textContent = titles[view];

    // Refresh view data
    if (view === 'dashboard') renderDashboard();
    if (view === 'history') renderHistory();
    if (view === 'settings') renderSettings();
    
    lucide.createIcons();
}

function setupModal() {
    fab.addEventListener('click', () => {
        modal.style.display = 'flex';
        document.getElementById('input-value').focus();
    });

    btnCancel.addEventListener('click', () => {
        modal.style.display = 'none';
        clearForm();
    });

    btnSave.addEventListener('click', () => {
        const val = document.getElementById('input-value').value;
        const note = document.getElementById('input-note').value;
        if (val) {
            db.saveReading(val, note);
            modal.style.display = 'none';
            clearForm();
            if (currentView === 'dashboard') renderDashboard();
            if (currentView === 'history') renderHistory();
        }
    });
}

function clearForm() {
    document.getElementById('input-value').value = '';
    document.getElementById('input-note').value = '';
}

function renderDashboard() {
    const readings = db.getReadings();
    const latestValue = document.getElementById('latest-value');
    const latestTime = document.getElementById('latest-time');

    if (readings.length > 0) {
        const last = readings[readings.length - 1];
        latestValue.textContent = last.value;
        latestTime.textContent = new Date(last.timestamp).toLocaleString();
        
        // Update Chart
        renderChart(readings);
    }
}

function renderHistory() {
    const readings = [...db.getReadings()].reverse();
    const list = document.getElementById('history-list');
    const empty = document.getElementById('empty-history');

    if (readings.length === 0) {
        list.innerHTML = '';
        empty.style.display = 'block';
        return;
    }

    empty.style.display = 'none';
    list.innerHTML = readings.map(r => `
        <div class="glass-card" style="margin-bottom: 12px; display: flex; justify-content: space-between; align-items: center;">
            <div>
                <p style="font-weight: 700; font-size: 1.1rem;">${r.value} <span style="font-size: 0.8rem; font-weight: 400; color: var(--text-secondary);">mg/dL</span></p>
                <p style="font-size: 0.75rem; color: var(--text-secondary);">${new Date(r.timestamp).toLocaleString()}</p>
                ${r.note ? `<p style="font-size: 0.8rem; margin-top: 5px; opacity: 0.8;">${r.note}</p>` : ''}
            </div>
            <button onclick="window.deleteReading(${r.id})" style="background: none; border: none; color: var(--accent); opacity: 0.7;">
                <i data-lucide="trash-2" style="width: 18px;"></i>
            </button>
        </div>
    `).join('');
    lucide.createIcons();
}

window.deleteReading = (id) => {
    if (confirm('Excluir este registro?')) {
        db.deleteReading(id);
        renderHistory();
        renderDashboard();
    }
};

function renderChart(readings) {
    const ctx = document.getElementById('glucoseChart').getContext('2d');
    
    // Take last 7 readings
    const lastReadings = readings.slice(-7);
    const labels = lastReadings.map(r => new Date(r.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }));
    const data = lastReadings.map(r => r.value);

    if (chart) chart.destroy();

    chart = new Chart(ctx, {
        type: 'line',
        data: {
            labels: labels,
            datasets: [{
                label: 'mg/dL',
                data: data,
                borderColor: '#8b5cf6',
                backgroundColor: 'rgba(139, 92, 246, 0.1)',
                borderWidth: 3,
                tension: 0.4,
                fill: true,
                pointBackgroundColor: '#8b5cf6',
                pointRadius: 4
            }]
        },
        options: {
            responsive: true,
            plugins: { legend: { display: false } },
            scales: {
                y: { display: false },
                x: {
                    grid: { display: false },
                    ticks: { color: '#94a3b8', font: { size: 10 } }
                }
            }
        }
    });
}

function setupExport() {
    btnExport.addEventListener('click', () => {
        const readings = db.getReadings();
        exportToCSV(readings);
    });
}

function renderSettings() {
    const settings = db.getSettings();
    const list = document.getElementById('reminders-list');
    
    if (settings.notifications.length === 0) {
        list.innerHTML = '<p style="text-align: center; color: var(--text-secondary); padding: 10px;">Nenhum lembrete configurado</p>';
    } else {
        list.innerHTML = settings.notifications.map((n, i) => `
            <div style="display: flex; justify-content: space-between; align-items: center; padding: 10px 0; border-bottom: 1px solid var(--glass-border);">
                <span style="font-size: 1.1rem; font-weight: 500;">${n.time}</span>
                <div style="display: flex; gap: 15px; align-items: center;">
                    <input type="checkbox" ${n.enabled ? 'checked' : ''} onchange="window.toggleReminder(${i})" style="width: auto;">
                    <button onclick="window.removeReminder(${i})" style="background: none; border: none; color: var(--accent);"><i data-lucide="x" style="width: 18px;"></i></button>
                </div>
            </div>
        `).join('');
    }
    lucide.createIcons();
}

window.toggleReminder = (index) => {
    const settings = db.getSettings();
    settings.notifications[index].enabled = !settings.notifications[index].enabled;
    db.saveSettings(settings);
    // Logic to reschedule notifications would go here
};

window.removeReminder = (index) => {
    const settings = db.getSettings();
    settings.notifications.splice(index, 1);
    db.saveSettings(settings);
    renderSettings();
};

document.getElementById('add-reminder-btn').addEventListener('click', () => {
    const time = prompt('Horário (HH:MM):', '08:00');
    if (time && /^([01]?[0-9]|2[0-3]):[0-5][0-9]$/.test(time)) {
        const settings = db.getSettings();
        settings.notifications.push({ time, enabled: true });
        db.saveSettings(settings);
        renderSettings();
    }
});
