import { useCallback, useEffect, useState } from 'react';
import { api, displayDate, jsonBody } from '../api.js';

export default function DoctorSchedulePage() {
  const [slots, setSlots] = useState([]);
  const [startTime, setStartTime] = useState('');
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  const refresh = useCallback(async () => {
    setLoading(true); setError('');
    try { const data = await api('/api/doctor-slots/mine'); setSlots(Array.isArray(data) ? data : []); }
    catch (err) { setError(err.message); }
    finally { setLoading(false); }
  }, []);
  useEffect(() => { refresh(); }, [refresh]);

  async function createSlot(event) {
    event.preventDefault(); setBusy(true); setError(''); setSuccess('');
    try {
      await api('/api/doctor-slots', { method: 'POST', body: jsonBody({ startTime }) });
      setStartTime(''); setSuccess('Availability added. Patients can now book this time.'); await refresh();
    } catch (err) { setError(err.message); }
    finally { setBusy(false); }
  }

  async function deleteSlot(id) {
    if (!window.confirm('Remove this available time?')) return;
    setError('');
    try { await api(`/api/doctor-slots/${id}`, { method: 'DELETE' }); setSuccess('Availability removed.'); await refresh(); }
    catch (err) { setError(err.message); }
  }

  const minLocal = (() => { const date = new Date(Date.now() + 60_000); date.setMinutes(date.getMinutes() - date.getTimezoneOffset()); return date.toISOString().slice(0, 16); })();
  return (
    <section className="content-section">
      <div className="section-heading-row"><div><span className="eyebrow">MANAGE YOUR AVAILABILITY</span><h2>My schedule</h2><p className="muted">Add future appointment times. Patients can book only slots marked available.</p></div><button className="btn btn-outline" onClick={refresh}>↻ Refresh</button></div>
      <div className="schedule-layout">
        <form className="panel-card schedule-form" onSubmit={createSlot}><span className="panel-icon">＋</span><h3>Add availability</h3><p className="muted">Choose a date and start time for a new appointment slot.</p><label>Appointment date and time<input type="datetime-local" value={startTime} onChange={(e) => setStartTime(e.target.value)} min={minLocal} required /></label><button className="btn btn-primary btn-wide" disabled={busy}>{busy ? 'Saving…' : 'Add time slot'} <span>→</span></button></form>
        <div className="panel-card schedule-slots"><div className="panel-heading"><div><h3>Upcoming availability</h3><p className="muted">{slots.length} slot{slots.length === 1 ? '' : 's'} on your schedule</p></div><span className="count-badge">{slots.length}</span></div>
          {error && <div className="alert alert-error">{error}</div>}{success && <div className="alert alert-success">{success}</div>}
          {loading ? <div className="loading-line">Loading your schedule…</div> : slots.length ? <div className="slot-list">{slots.map((slot) => <div className="slot-list-row" key={slot.id}><div className="slot-calendar">◷</div><div className="slot-details"><strong>{displayDate(slot.startTime)}</strong><span>Slot #{slot.id} · {slot.status}</span></div><span className={`status-pill status-${String(slot.status).toLowerCase()}`}>{slot.status}</span>{slot.status === 'AVAILABLE' && <button className="icon-button danger" title="Remove slot" aria-label="Remove slot" onClick={() => deleteSlot(slot.id)}>×</button>}</div>)}</div> : <div className="empty-state compact"><span className="empty-icon">◷</span><strong>No upcoming times</strong><p>Add your first availability using the form.</p></div>}
        </div>
      </div>
    </section>
  );
}
