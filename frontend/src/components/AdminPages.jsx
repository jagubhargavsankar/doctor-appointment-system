import { useCallback, useEffect, useState } from 'react';
import { api, displayDate, initials, jsonBody, money } from '../api.js';

const emptyDoctor = { name: '', email: '', password: '', specialization: '', qualification: '', experienceYears: '0', consultationFee: '' };

export function AdminDoctorsPage() {
  const [doctors, setDoctors] = useState([]);
  const [form, setForm] = useState(emptyDoctor);
  const [slotTimes, setSlotTimes] = useState({});
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');

  const refresh = useCallback(async () => {
    setLoading(true); setError('');
    try { const data = await api('/api/doctors?page=0&size=50'); setDoctors(data?.content || []); }
    catch (err) { setError(err.message); }
    finally { setLoading(false); }
  }, []);
  useEffect(() => { refresh(); }, [refresh]);

  function update(event) { setForm((old) => ({ ...old, [event.target.name]: event.target.value })); }

  async function createDoctor(event) {
    event.preventDefault(); setBusy(true); setError(''); setNotice('');
    try {
      await api('/api/doctors', { method: 'POST', body: jsonBody({ ...form, experienceYears: Number(form.experienceYears), consultationFee: Number(form.consultationFee) }) });
      setForm(emptyDoctor); setNotice('Doctor account created. Share the login email and password securely with the doctor.'); await refresh();
    } catch (err) { setError(err.message); }
    finally { setBusy(false); }
  }

  async function createSlot(doctorId) {
    const startTime = slotTimes[doctorId];
    if (!startTime) { setError('Choose a date and time before adding a slot.'); return; }
    setError(''); setNotice('');
    try {
      await api(`/api/doctor-slots/admin?doctorId=${doctorId}`, { method: 'POST', body: jsonBody({ startTime }) });
      setSlotTimes((old) => ({ ...old, [doctorId]: '' })); setNotice(`Availability added for doctor #${doctorId}.`);
    } catch (err) { setError(err.message); }
  }

  async function deleteDoctor(doctor) {
    if (!window.confirm(`Delete ${doctor.name}'s account? The backend will prevent deletion if appointments exist.`)) return;
    setError(''); setNotice('');
    try { await api(`/api/doctors/${doctor.id}`, { method: 'DELETE' }); setNotice('Doctor removed.'); await refresh(); }
    catch (err) { setError(err.message); }
  }

  const minLocal = (() => { const date = new Date(Date.now() + 60_000); date.setMinutes(date.getMinutes() - date.getTimezoneOffset()); return date.toISOString().slice(0, 16); })();
  return (
    <section className="content-section">
      <div className="section-heading-row"><div><span className="eyebrow">ADMINISTRATION</span><h2>Manage doctors</h2><p className="muted">Create doctor accounts and add their available appointment times.</p></div><button className="btn btn-outline" onClick={refresh}>↻ Refresh</button></div>
      {error && <div className="alert alert-error">{error}</div>}{notice && <div className="alert alert-success">{notice}</div>}
      <div className="admin-layout">
        <form className="panel-card admin-create-form" onSubmit={createDoctor}><span className="eyebrow">NEW DOCTOR</span><h3>Create a doctor account</h3><p className="muted">The backend creates both the doctor profile and login account.</p>
          <div className="form-grid"><label>Full name<input name="name" value={form.name} onChange={update} required maxLength={120} /></label><label>Email<input name="email" type="email" value={form.email} onChange={update} required /></label><label>Temporary password<input name="password" type="password" value={form.password} onChange={update} required minLength={8} maxLength={72} /></label><label>Specialization<input name="specialization" value={form.specialization} onChange={update} placeholder="e.g. Cardiologist" required /></label><label>Qualification<input name="qualification" value={form.qualification} onChange={update} placeholder="e.g. MBBS, MD" /></label><label>Experience (years)<input name="experienceYears" type="number" value={form.experienceYears} onChange={update} min={0} required /></label><label>Consultation fee (₹)<input name="consultationFee" type="number" value={form.consultationFee} onChange={update} min={0} step="0.01" required /></label></div>
          <button className="btn btn-primary btn-wide" disabled={busy}>{busy ? 'Creating…' : 'Create doctor account'} <span>→</span></button>
          <p className="form-footnote">Credentials are set by the admin. Do not send passwords in public channels.</p>
        </form>
        <div className="panel-card admin-doctor-list"><div className="panel-heading"><div><h3>Registered doctors</h3><p className="muted">{doctors.length} in the current listing</p></div><span className="count-badge">{doctors.length}</span></div>
          {loading ? <div className="loading-line">Loading doctors…</div> : doctors.length ? <div className="admin-doctors">{doctors.map((doctor) => <article className="admin-doctor-row" key={doctor.id}><div className="doctor-avatar small-avatar">{initials(doctor.name)}</div><div className="admin-doctor-info"><strong>{doctor.name}</strong><span>{doctor.specialization} · {doctor.experienceYears ?? 0} yrs · {money(doctor.consultationFee)}</span><small>ID #{doctor.id}</small></div><button className="icon-button danger" title="Delete doctor" aria-label={`Delete ${doctor.name}`} onClick={() => deleteDoctor(doctor)}>×</button><div className="admin-slot-add"><input aria-label={`Add slot time for ${doctor.name}`} type="datetime-local" min={minLocal} value={slotTimes[doctor.id] || ''} onChange={(e) => setSlotTimes((old) => ({ ...old, [doctor.id]: e.target.value }))} /><button className="btn btn-outline btn-small" onClick={() => createSlot(doctor.id)}>Add slot</button></div></article>)}</div> : <div className="empty-state compact"><strong>No doctors have been added</strong><p>Create the first doctor using the form.</p></div>}
        </div>
      </div>
    </section>
  );
}

export function AdminPatientsPage() {
  const [patients, setPatients] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const refresh = useCallback(async () => {
    setLoading(true); setError('');
    try { const data = await api('/api/patients'); setPatients(Array.isArray(data) ? data : []); }
    catch (err) { setError(err.message); }
    finally { setLoading(false); }
  }, []);
  useEffect(() => { refresh(); }, [refresh]);

  return <section className="content-section"><div className="section-heading-row"><div><span className="eyebrow">ADMINISTRATION</span><h2>Patient directory</h2><p className="muted">Patients registered in the system.</p></div><button className="btn btn-outline" onClick={refresh}>↻ Refresh</button></div>
    {error && <div className="alert alert-error">{error}</div>}
    <div className="panel-card directory-card"><div className="panel-heading"><div><h3>Registered patients</h3><p className="muted">{patients.length} records</p></div><span className="count-badge">{patients.length}</span></div>
      {loading ? <div className="loading-line">Loading patients…</div> : patients.length ? <div className="table-scroll"><table className="data-table"><thead><tr><th>Patient</th><th>Email</th><th>Phone</th><th>Patient ID</th></tr></thead><tbody>{patients.map((patient) => <tr key={patient.id}><td><div className="table-person"><span className="mini-avatar">{initials(patient.name)}</span><strong>{patient.name}</strong></div></td><td>{patient.email}</td><td>{patient.phone || '—'}</td><td>#{patient.id}</td></tr>)}</tbody></table></div> : <div className="empty-state compact"><strong>No patient records found</strong></div>}
    </div>
  </section>;
}
