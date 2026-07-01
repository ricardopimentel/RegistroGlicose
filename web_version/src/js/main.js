import { db } from './db.js';
import { exportToCSV } from './export.js';
import { FOODS } from './foods.js';

let activeMeal = [];
let editingMealItem = null;

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
    const viewCarbCounter = document.getElementById('view-carb-counter');
    viewDashboard.style.display = view === 'dashboard' ? 'block' : 'none';
    viewHistory.style.display = view === 'history' ? 'block' : 'none';
    if (viewCarbCounter) viewCarbCounter.style.display = view === 'carb-counter' ? 'block' : 'none';
    viewSettings.style.display = view === 'settings' ? 'block' : 'none';

    // Update title
    const titles = { 
        dashboard: 'Dashboard', 
        history: 'Histórico', 
        'carb-counter': 'Contagem de Carboidratos',
        settings: 'Ajustes' 
    };
    viewTitle.textContent = titles[view];

    // Refresh view data
    if (view === 'dashboard') renderDashboard();
    if (view === 'history') renderHistory();
    if (view === 'carb-counter') renderCarbCounter();
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
    const glucoseReadings = readings.filter(r => r.value > 0);
    const latestValue = document.getElementById('latest-value');
    const latestTime = document.getElementById('latest-time');

    if (glucoseReadings.length > 0) {
        const last = glucoseReadings[glucoseReadings.length - 1];
        latestValue.textContent = last.value;
        latestTime.textContent = new Date(last.timestamp).toLocaleString();
        
        // Update Chart
        renderChart(glucoseReadings);
    } else {
        latestValue.textContent = '--';
        latestTime.textContent = 'Nenhum registro ainda';
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
    list.innerHTML = readings.map(r => {
        const headline = r.value > 0 
            ? `${r.value} <span style="font-size: 0.8rem; font-weight: 400; color: var(--text-secondary);">mg/dL</span>` 
            : `🥖 Refeição`;
            
        const carbBadge = r.carbs 
            ? `<span style="background: rgba(139, 92, 246, 0.2); color: var(--primary-light); padding: 3px 8px; border-radius: 8px; font-size: 0.75rem; font-weight: 700; margin-left: 8px; border: 1px solid rgba(139, 92, 246, 0.4);">🥖 ${r.carbs}g CHO</span>` 
            : '';
            
        return `
            <div class="glass-card" style="margin-bottom: 12px; display: flex; justify-content: space-between; align-items: center;">
                <div>
                    <p style="font-weight: 700; font-size: 1.1rem; display: flex; align-items: center; flex-wrap: wrap; gap: 4px;">
                        ${headline} ${carbBadge}
                    </p>
                    <p style="font-size: 0.75rem; color: var(--text-secondary);">${new Date(r.timestamp).toLocaleString()}</p>
                    ${r.note ? `<p style="font-size: 0.8rem; margin-top: 5px; opacity: 0.8;">${r.note}</p>` : ''}
                </div>
                <button onclick="window.deleteReading(${r.id})" style="background: none; border: none; color: var(--accent); opacity: 0.7;">
                    <i data-lucide="trash-2" style="width: 18px;"></i>
                </button>
            </div>
        `;
    }).join('');
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
    
    // Filter readings to only draw positive glucose values
    const glucoseReadings = readings.filter(r => r.value > 0);
    
    // Take last 7 readings
    const lastReadings = glucoseReadings.slice(-7);
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
    
    // Populate Carb Ratio Input
    const ratioInput = document.getElementById('web-carb-ratio-input');
    if (ratioInput) {
        ratioInput.value = settings.carbRatio > 0 ? settings.carbRatio : '';
    }

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

// Save Carb Ratio
const btnSaveRatio = document.getElementById('web-save-ratio-btn');
if (btnSaveRatio) {
    btnSaveRatio.addEventListener('click', () => {
        const ratio = parseFloat(document.getElementById('web-carb-ratio-input').value) || 0;
        const settings = db.getSettings();
        settings.carbRatio = ratio;
        db.saveSettings(settings);
        alert('Fator de carboidrato salvo com sucesso!');
    });
}

// ── Carb Counter Screen Functionality ──────────────────────────────────
function normalizeText(text) {
    return text.normalize("NFD").replace(/[\u0300-\u036f]/g, "").toLowerCase().trim();
}

function renderCarbCounter() {
    renderMealList();
    setupSearch();
    setupSaveMeal();
}

function setupSearch() {
    const searchInput = document.getElementById('web-search-input');
    const resultsContainer = document.getElementById('web-search-results');

    if (!searchInput || !resultsContainer) return;

    // Reset search
    searchInput.value = '';
    resultsContainer.style.display = 'none';

    searchInput.addEventListener('input', () => {
        const query = normalizeText(searchInput.value);
        if (query.length < 2) {
            resultsContainer.innerHTML = '';
            resultsContainer.style.display = 'none';
            return;
        }

        const filtered = FOODS.filter(food => 
            normalizeText(food.name).includes(query)
        ).slice(0, 40);

        if (filtered.length === 0) {
            resultsContainer.innerHTML = '<p style="padding: 10px; color: var(--text-secondary); text-align: center;">Nenhum alimento encontrado</p>';
        } else {
            resultsContainer.innerHTML = filtered.map(food => `
                <div class="search-result-item" onclick="window.addFoodToMeal(${food.id})" style="padding: 10px; border-bottom: 1px solid rgba(255,255,255,0.05); cursor: pointer; display: flex; justify-content: space-between; align-items: center;">
                    <div>
                        <p style="font-weight: 600; font-size: 0.95rem;">${food.name}</p>
                        <p style="font-size: 0.75rem; color: var(--text-secondary);">${food.measure} (${food.grams}g) • ${food.carbs}g CHO</p>
                    </div>
                    <i data-lucide="plus-circle" style="color: var(--primary); width: 18px;"></i>
                </div>
            `).join('');
            lucide.createIcons();
        }
        resultsContainer.style.display = 'block';
    });
}

window.addFoodToMeal = (id) => {
    const food = FOODS.find(f => f.id === id);
    if (!food) return;

    const existing = activeMeal.find(item => item.food.id === id);
    if (existing) {
        existing.multiplier += 1.0;
    } else {
        activeMeal.push({ food, multiplier: 1.0 });
    }

    // Clear search
    const searchInput = document.getElementById('web-search-input');
    const resultsContainer = document.getElementById('web-search-results');
    if (searchInput) searchInput.value = '';
    if (resultsContainer) resultsContainer.style.display = 'none';

    renderMealList();
};

function renderMealList() {
    const container = document.getElementById('web-meal-list');
    const summary = document.getElementById('web-meal-summary');
    const emptyMsg = document.getElementById('web-empty-meal');

    if (!container || !summary || !emptyMsg) return;

    if (activeMeal.length === 0) {
        container.innerHTML = '';
        emptyMsg.style.display = 'block';
        summary.style.display = 'none';
        return;
    }

    emptyMsg.style.display = 'none';
    summary.style.display = 'block';

    let totalCarbs = 0;
    let totalCalories = 0;

    container.innerHTML = activeMeal.map((item, index) => {
        const itemCarbs = Math.round(item.food.carbs * item.multiplier);
        const itemCalories = Math.round(item.food.calories * item.multiplier);
        const itemGrams = Math.round(item.food.grams * item.multiplier);

        totalCarbs += item.food.carbs * item.multiplier;
        totalCalories += item.food.calories * item.multiplier;

        return `
            <div class="glass-card" style="margin-bottom: 10px; padding: 12px; display: flex; justify-content: space-between; align-items: center;">
                <div style="flex: 1; min-width: 0; padding-right: 10px;">
                    <p style="font-weight: 700; font-size: 0.95rem; text-overflow: ellipsis; overflow: hidden; white-space: nowrap;">${item.food.name}</p>
                    <p style="font-size: 0.75rem; color: var(--text-secondary);">${item.food.measure} (${item.food.grams}g)</p>
                    <p style="font-size: 0.8rem; font-weight: 600; color: var(--primary); margin-top: 4px;">
                        ${itemCarbs}g CHO • ${itemCalories} kcal • ${itemGrams}g peso
                    </p>
                </div>
                
                <div style="display: flex; align-items: center; gap: 4px;">
                    <button onclick="window.changeMultiplier(${index}, -0.5)" style="background: none; border: none; color: var(--text-secondary); cursor: pointer;"><i data-lucide="minus-circle" style="width: 20px;"></i></button>
                    <span onclick="window.openPortionModal(${index})" style="font-weight: 700; font-size: 0.9rem; padding: 0 4px; cursor: pointer; text-decoration: underline;">${item.multiplier}x</span>
                    <button onclick="window.changeMultiplier(${index}, 0.5)" style="background: none; border: none; color: var(--text-secondary); cursor: pointer;"><i data-lucide="plus-circle" style="width: 20px;"></i></button>
                    <button onclick="window.removeFoodFromMeal(${index})" style="background: none; border: none; color: var(--accent); margin-left: 8px; cursor: pointer;"><i data-lucide="trash-2" style="width: 18px;"></i></button>
                </div>
            </div>
        `;
    }).join('');
    lucide.createIcons();

    totalCarbs = Math.round(totalCarbs);
    totalCalories = Math.round(totalCalories);

    document.getElementById('web-total-carbs').textContent = `${totalCarbs} g`;
    document.getElementById('web-total-calories').textContent = `${totalCalories} kcal`;

    // Suggested Insulin
    const settings = db.getSettings();
    const insulinAdvisory = document.getElementById('web-insulin-advisory');
    if (insulinAdvisory) {
        if (settings.carbRatio > 0) {
            const suggestion = (totalCarbs / settings.carbRatio).toFixed(1);
            document.getElementById('web-suggested-insulin').textContent = `${suggestion} U`;
            insulinAdvisory.style.display = 'block';
        } else {
            insulinAdvisory.style.display = 'none';
        }
    }
}

window.changeMultiplier = (index, delta) => {
    if (index < 0 || index >= activeMeal.length) return;
    
    activeMeal[index].multiplier += delta;
    if (activeMeal[index].multiplier <= 0) {
        activeMeal.splice(index, 1);
    }
    renderMealList();
};

window.removeFoodFromMeal = (index) => {
    activeMeal.splice(index, 1);
    renderMealList();
};

// Portion edit modal
const portionModal = document.getElementById('web-portion-modal');
const portionFoodName = document.getElementById('web-portion-food-name');
const portionStandardDesc = document.getElementById('web-portion-standard-desc');
const portionMultiplierInput = document.getElementById('web-portion-multiplier');

window.openPortionModal = (index) => {
    if (index < 0 || index >= activeMeal.length) return;
    editingMealItem = index;
    
    const item = activeMeal[index];
    portionFoodName.textContent = item.food.name;
    portionStandardDesc.textContent = `Medida usual: ${item.food.measure} (${item.food.grams}g/ml)`;
    portionMultiplierInput.value = item.multiplier;
    
    portionModal.style.display = 'flex';
    portionMultiplierInput.focus();
};

document.getElementById('web-cancel-portion').addEventListener('click', () => {
    portionModal.style.display = 'none';
    editingMealItem = null;
});

document.getElementById('web-save-portion').addEventListener('click', () => {
    if (editingMealItem === null) return;
    
    const mult = parseFloat(portionMultiplierInput.value) || 0;
    if (mult <= 0) {
        activeMeal.splice(editingMealItem, 1);
    } else {
        activeMeal[editingMealItem].multiplier = mult;
    }
    
    portionModal.style.display = 'none';
    editingMealItem = null;
    renderMealList();
});

// Confirm Meal Log Modal
const confirmModal = document.getElementById('web-meal-confirm-modal');

function setupSaveMeal() {
    const btnSaveMeal = document.getElementById('web-save-meal-btn');
    if (!btnSaveMeal) return;

    btnSaveMeal.addEventListener('click', () => {
        let totalCarbs = 0;
        let totalCalories = 0;
        activeMeal.forEach(item => {
            totalCarbs += item.food.carbs * item.multiplier;
            totalCalories += item.food.calories * item.multiplier;
        });

        totalCarbs = Math.round(totalCarbs);
        totalCalories = Math.round(totalCalories);

        document.getElementById('web-confirm-meal-summary').textContent = `${totalCarbs}g CHO • ${totalCalories} kcal`;
        document.getElementById('web-confirm-glucose').value = '';
        document.getElementById('web-confirm-note').value = '';
        
        confirmModal.style.display = 'flex';
    });
}

document.getElementById('web-cancel-confirm').addEventListener('click', () => {
    confirmModal.style.display = 'none';
});

document.getElementById('web-save-confirm').addEventListener('click', () => {
    let totalCarbs = 0;
    let totalCalories = 0;
    activeMeal.forEach(item => {
        totalCarbs += item.food.carbs * item.multiplier;
        totalCalories += item.food.calories * item.multiplier;
    });

    totalCarbs = Math.round(totalCarbs);
    totalCalories = Math.round(totalCalories);

    const mealType = document.getElementById('web-confirm-meal-type').value;
    const glucose = parseFloat(document.getElementById('web-confirm-glucose').value) || 0;
    const userNote = document.getElementById('web-confirm-note').value;
    
    // Auto-generate note if none provided
    const foodSummary = activeMeal.map(item => `${item.food.name} (${item.multiplier}x)`).join(', ');
    const note = userNote ? `${mealType}: ${userNote}` : `${mealType}: ${foodSummary}`;

    db.saveReading(glucose, note, totalCarbs, totalCalories);
    
    confirmModal.style.display = 'none';
    activeMeal = [];
    
    switchView('dashboard');
});
