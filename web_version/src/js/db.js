const DB_NAME = 'glucose_tracker_db';

export const db = {
    getReadings() {
        const data = localStorage.getItem(`${DB_NAME}_readings`);
        return data ? JSON.parse(data) : [];
    },

    saveReading(value, note = '') {
        const readings = this.getReadings();
        const newReading = {
            id: Date.now(),
            value: parseFloat(value),
            note: note,
            timestamp: new Date().toISOString()
        };
        readings.push(newReading);
        localStorage.setItem(`${DB_NAME}_readings`, JSON.stringify(readings));
        return newReading;
    },

    deleteReading(id) {
        let readings = this.getReadings();
        readings = readings.filter(r => r.id !== id);
        localStorage.setItem(`${DB_NAME}_readings`, JSON.stringify(readings));
    },

    getSettings() {
        const data = localStorage.getItem(`${DB_NAME}_settings`);
        return data ? JSON.parse(data) : {
            notifications: [] // Array of { id, time, enabled }
        };
    },

    saveSettings(settings) {
        localStorage.setItem(`${DB_NAME}_settings`, JSON.stringify(settings));
    }
};
