export const exportToCSV = (readings) => {
    if (readings.length === 0) return;

    // Group by date
    const days = {};
    const allTimes = new Set();

    readings.forEach(r => {
        const d = new Date(r.timestamp);
        const dateStr = d.toLocaleDateString();
        const timeStr = d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
        
        if (!days[dateStr]) days[dateStr] = {};
        days[dateStr][timeStr] = r.value;
        allTimes.add(timeStr);
    });

    // Sort times to keep columns consistent
    const sortedTimes = Array.from(allTimes).sort();
    const headers = ['Data', ...sortedTimes];

    const rows = Object.keys(days).sort((a, b) => {
        // Sort dates correctly (assuming DD/MM/YYYY or similar, might need better parsing)
        return new Date(a.split('/').reverse().join('-')) - new Date(b.split('/').reverse().join('-'));
    }).map(date => {
        const row = [date];
        sortedTimes.forEach(time => {
            row.push(days[date][time] || ''); // Empty if no measurement at that time
        });
        return row;
    });

    const csvContent = [headers, ...rows].map(e => e.join(",")).join("\n");
    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);

    // Always perform download
    const link = document.createElement("a");
    link.setAttribute("href", url);
    link.setAttribute("download", `glicose_${new Date().toISOString().split('T')[0]}.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);

    // Also trigger Share if available (optional for mobile convenience)
    if (navigator.share && navigator.canShare) {
        const file = new File([blob], 'registros_glicose.csv', { type: 'text/csv' });
        if (navigator.canShare({ files: [file] })) {
            navigator.share({
                title: 'Meus Registros de Glicose',
                text: 'Aqui estão meus registros de glicose.',
                files: [file]
            }).catch(err => console.log('Share canceled or failed', err));
        }
    }
};
