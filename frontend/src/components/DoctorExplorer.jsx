import { useEffect, useMemo, useState } from 'react';
import { api, displayDate, initials, jsonBody, money } from '../api.js';

function DoctorCard({ doctor, canBook, onBook, onSignIn }) {
  return (
    <article className="doctor-card">
      <div className="doctor-card-top">
        <div className="doctor-avatar">{initials(doctor.name)}</div>
        <div className="doctor-rating"><span>★</span> {Number(doctor.rating || 0).toFixed(1)}</div>
      </div>
      <div className="doctor-identity">
        <h3>{doctor.name || 'Doctor'}</h3>
        <p className="specialty">{doctor.specialization || 'General care'}</p>
        <p className="doctor-qualification">{doctor.qualification || 'Qualified medical professional'}</p>
      </div>
      <div className="doctor-metrics">
        <div><span className="metric-label">Experience</span><strong>{doctor.experienceYears ?? 0} yrs</strong></div>
        <div><span className="metric-label">Consultation</span><strong>{money(doctor.consultationFee)}</strong></div>
      </div>
      <button className="btn btn-primary btn-wide" onClick={() => canBook ? onBook(doctor) : onSignIn()}>{canBook ? 'View available times' : 'Sign in to book'} <span>→</span></button>
    </article>
  );
}

function BookingModal({ doctor, onClose, onBooked }) {
  const [slots, setSlots] = useState([]);
  const [slotId, setSlotId] = useState('');
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    let active = true;
    api(`/api/doctor-slots?doctorId=${encodeURIComponent(doctor.id)}`)
      .then((items) => { if (active) setSlots(Array.isArray(items) ? items : []); })
      .catch((err) => { if (active) setError(err.message); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [doctor.id]);

  async function submit(event) {
    event.preventDefault();
    if (!slotId) { setError('Choose an available time first.'); return; }
    setBusy(true); setError('');
    try {
      await api('/api/appointments', { method: 'POST', body: jsonBody({ slotId: Number(slotId), reason }) });
      onBooked();
      onClose();
    } catch (err) { setError(err.message); }
    finally { setBusy(false); }
  }

  return (
    <div className="modal-backdrop" onMouseDown={(e) => { if (e.target === e.currentTarget) onClose(); }}>
      <section className="modal-card" role="dialog" aria-modal="true" aria-labelledby="booking-title">
        <button className="modal-close" onClick={onClose} aria-label="Close booking dialog">×</button>
        <span className="eyebrow">APPOINTMENT REQUEST</span>
        <h2 id="booking-title">Book with {doctor.name}</h2>
        <p className="muted">{doctor.specialization} · {money(doctor.consultationFee)} consultation</p>
        {error && <div className="alert alert-error">{error}</div>}
        {loading ? <div className="loading-line">Loading available times…</div> : slots.length === 0 ? <div className="empty-state compact"><span className="empty-icon">◷</span><strong>No times available yet</strong><p>This doctor has not added any available slots. Please check again later.</p></div> : (
          <form onSubmit={submit} className="form-stack">
            <label>Available time</label>
            <div className="slot-grid">
              {slots.filter((slot) => slot.status === 'AVAILABLE').map((slot) => (
                <button type="button" key={slot.id} className={`slot-option ${String(slotId) === String(slot.id) ? 'selected' : ''}`} onClick={() => setSlotId(String(slot.id))}>
                  <span>{new Date(slot.startTime).toLocaleDateString([], { month: 'short', day: 'numeric' })}</span>
                  <strong>{new Date(slot.startTime).toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' })}</strong>
                </button>
              ))}
            </div>
            <label>Reason for visit<textarea value={reason} onChange={(e) => setReason(e.target.value)} placeholder="Briefly describe why you need an appointment" required maxLength={500} rows={3} /></label>
            <button className="btn btn-primary btn-wide" type="submit" disabled={busy || !slotId}>{busy ? 'Booking…' : 'Confirm appointment request'} <span>→</span></button>
          </form>
        )}
      </section>
    </div>
  );
}

export default function DoctorExplorer({ session, onRequireLogin, onNotice, refreshKey = 0 }) {
  const [doctors, setDoctors] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [search, setSearch] = useState('');
  const [problem, setProblem] = useState('');
  const [selectedDoctor, setSelectedDoctor] = useState(null);
  const [mode, setMode] = useState('all');
  const [searching, setSearching] = useState(false);
  const [pageCount, setPageCount] = useState(0);

  async function loadDoctors(page = 0) {
    setLoading(true); setError('');
    try {
      const result = await api(`/api/doctors?page=${page}&size=50`);
      setDoctors(Array.isArray(result) ? result : (result?.content || []));
      setPageCount(result?.totalPages || 0);
      setMode('all');
    } catch (err) { setError(err.message); }
    finally { setLoading(false); }
  }

  useEffect(() => { loadDoctors(); }, [refreshKey]);

  async function recommend(event) {
    event.preventDefault();
    if (!problem.trim()) { loadDoctors(); return; }
    setSearching(true); setError('');
    try {
      const result = await api(`/api/doctors/recommend?problem=${encodeURIComponent(problem.trim())}&page=0&size=50`);
      setDoctors(result?.content || []);
      setPageCount(result?.totalPages || 0);
      setMode('recommended');
    } catch (err) { setError(err.message); }
    finally { setSearching(false); }
  }

  const visibleDoctors = useMemo(() => {
    const q = search.trim().toLowerCase();
    if (!q) return doctors;
    return doctors.filter((d) => `${d.name || ''} ${d.specialization || ''} ${d.qualification || ''}`.toLowerCase().includes(q));
  }, [doctors, search]);

  const canBook = session?.role === 'PATIENT';
  return (
    <section className="content-section">
      <div className="section-heading-row">
        <div><span className="eyebrow">CARE, AT YOUR CONVENIENCE</span><h2>{session ? 'Find your doctor' : 'Meet your care team'}</h2><p className="muted">Explore specialists and find a time that works for you.</p></div>
        <div className="heading-stat"><strong>{doctors.length}{pageCount > 1 ? '+' : ''}</strong><span>doctors listed</span></div>
      </div>
      <div className="doctor-search-panel">
        <div className="search-field"><span className="search-symbol">⌕</span><input value={search} onChange={(e) => setSearch(e.target.value)} placeholder="Search by doctor, specialty or qualification" aria-label="Search doctors" /><button className="btn btn-outline" onClick={() => setSearch('')}>Clear</button></div>
        <form className="recommend-field" onSubmit={recommend}><div><span className="eyebrow">NOT SURE WHO TO SEE?</span><label htmlFor="problem-search">Find a specialist by health concern</label></div><div className="recommend-controls"><input id="problem-search" value={problem} onChange={(e) => setProblem(e.target.value)} placeholder="e.g. knee pain, skin rash, fever" /><button className="btn btn-dark" type="submit" disabled={searching}>{searching ? 'Finding…' : 'Recommend'}</button><button className="btn btn-text" type="button" onClick={() => { setProblem(''); loadDoctors(); }}>Reset</button></div></form>
      </div>
      {mode === 'recommended' && <p className="inline-note">Showing recommendations for <strong>{problem}</strong>. <button className="text-button" onClick={() => { setProblem(''); loadDoctors(); }}>View all doctors</button></p>}
      {error && <div className="alert alert-error">{error}</div>}
      {loading ? <div className="loading-grid">{[1, 2, 3].map((n) => <div className="skeleton-card" key={n} />)}</div> : visibleDoctors.length ? (
        <div className="doctor-grid">{visibleDoctors.map((doctor) => <DoctorCard key={doctor.id} doctor={doctor} canBook={canBook} onBook={setSelectedDoctor} onSignIn={onRequireLogin} />)}</div>
      ) : <div className="empty-state"><span className="empty-icon">⌕</span><strong>No doctors found</strong><p>Try another name or specialty. If you searched by a health concern, try a common term such as fever, skin, tooth or knee.</p></div>}
      {!session && <div className="sign-in-banner"><div><strong>Ready to take the next step?</strong><p>Create a patient account to view available slots and book your visit.</p></div><button className="btn btn-primary" onClick={onRequireLogin}>Sign in / Register <span>→</span></button></div>}
      {selectedDoctor && canBook && <BookingModal doctor={selectedDoctor} onClose={() => setSelectedDoctor(null)} onBooked={() => onNotice('Appointment request booked successfully. You can see it in My appointments.')} />}
    </section>
  );
}
