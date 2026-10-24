import { useCallback, useEffect, useState } from 'react';
import { api, displayDate, initials, jsonBody } from '../api.js';

const statusClass = (status = '') => `status-pill status-${status.toLowerCase()}`;

function AppointmentRow({ appointment, role, refresh, setError }) {
  const [busy, setBusy] = useState(false);
  const patient = appointment.patient || {};
  const doctor = appointment.doctor || {};

  async function act(path, method = 'PATCH') {
    setBusy(true); setError('');
    try {
      await api(`/api/appointments/${appointment.id}${path}`, { method });
      await refresh();
    } catch (err) { setError(err.message); }
    finally { setBusy(false); }
  }

  const canManage = role === 'DOCTOR' || role === 'ADMIN';
  const canCancel = role === 'PATIENT' || role === 'ADMIN';
  return (
    <article className="appointment-row">
      <div className="appointment-date"><span>{new Date(appointment.appointmentDateTime).toLocaleDateString([], { month: 'short' })}</span><strong>{new Date(appointment.appointmentDateTime).toLocaleDateString([], { day: '2-digit' })}</strong><small>{new Date(appointment.appointmentDateTime).toLocaleDateString([], { weekday: 'short' })}</small></div>
      <div className="appointment-main">
        <div className="appointment-title-row"><h3>{role === 'PATIENT' ? (doctor.name || 'Doctor') : (patient.name || 'Patient')}</h3><span className={statusClass(appointment.status)}>{appointment.status || 'UNKNOWN'}</span></div>
        <p className="specialty">{role === 'PATIENT' ? (doctor.specialization || 'Consultation') : `Appointment with ${doctor.name || 'doctor'}`}</p>
        <p className="appointment-reason">{appointment.reason || 'No reason provided'}</p>
        <div className="appointment-meta"><span>◷ {displayDate(appointment.appointmentDateTime)}</span>{role !== 'PATIENT' && patient.email && <span>✉ {patient.email}</span>}</div>
      </div>
      <div className="appointment-actions">
        {canManage && appointment.status === 'BOOKED' && <button className="btn btn-outline btn-small" disabled={busy} onClick={() => act('/confirm')}>Confirm</button>}
        {canManage && appointment.status === 'CONFIRMED' && <button className="btn btn-primary btn-small" disabled={busy} onClick={() => act('/complete')}>Mark complete</button>}
        {canCancel && ['BOOKED', 'CONFIRMED'].includes(appointment.status) && <button className="btn btn-quiet btn-small" disabled={busy} onClick={() => { if (window.confirm('Cancel this appointment?')) act('/cancel'); }}>Cancel</button>}
      </div>
    </article>
  );
}

export default function AppointmentsPage({ role, onNotice }) {
  const [appointments, setAppointments] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [filter, setFilter] = useState('ALL');

  const refresh = useCallback(async () => {
    setLoading(true); setError('');
    try {
      const path = role === 'ADMIN' ? '/api/appointments' : role === 'DOCTOR' ? '/api/appointments/doctor' : '/api/appointments/mine';
      const data = await api(path);
      setAppointments(Array.isArray(data) ? data : []);
    } catch (err) { setError(err.message); }
    finally { setLoading(false); }
  }, [role]);

  useEffect(() => { refresh(); }, [refresh]);
  const shown = filter === 'ALL' ? appointments : appointments.filter((item) => item.status === filter);

  return (
    <section className="content-section">
      <div className="section-heading-row"><div><span className="eyebrow">YOUR VISITS, IN ONE PLACE</span><h2>{role === 'DOCTOR' ? 'Appointment schedule' : role === 'ADMIN' ? 'All appointments' : 'My appointments'}</h2><p className="muted">{role === 'DOCTOR' ? 'Review requests and update visit status.' : 'Review upcoming visits and check previous appointments.'}</p></div><button className="btn btn-outline" onClick={refresh}>↻ Refresh</button></div>
      <div className="stats-strip"><div><span>Total</span><strong>{appointments.length}</strong></div><div><span>Booked</span><strong>{appointments.filter((a) => a.status === 'BOOKED').length}</strong></div><div><span>Confirmed</span><strong>{appointments.filter((a) => a.status === 'CONFIRMED').length}</strong></div><div><span>Completed</span><strong>{appointments.filter((a) => a.status === 'COMPLETED').length}</strong></div></div>
      <div className="list-toolbar"><div className="filter-tabs">{['ALL', 'BOOKED', 'CONFIRMED', 'COMPLETED', 'CANCELLED'].map((value) => <button key={value} className={filter === value ? 'active' : ''} onClick={() => setFilter(value)}>{value === 'ALL' ? 'All visits' : value.charAt(0) + value.slice(1).toLowerCase()}</button>)}</div></div>
      {error && <div className="alert alert-error">{error}</div>}
      {loading ? <div className="empty-state"><span className="spinner" /> Loading appointments…</div> : shown.length ? <div className="appointment-list">{shown.map((item) => <AppointmentRow key={item.id} appointment={item} role={role} refresh={refresh} setError={setError} />)}</div> : <div className="empty-state"><span className="empty-icon">▦</span><strong>No appointments here yet</strong><p>{filter === 'ALL' ? 'Appointments you book or manage will appear here.' : `There are no ${filter.toLowerCase()} appointments.`}</p></div>}
    </section>
  );
}
